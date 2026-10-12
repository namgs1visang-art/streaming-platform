package com.streaming.viewer.hook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streaming.core.domain.broadcast.Broadcast;
import com.streaming.core.domain.broadcast.RecordingStatus;
import com.streaming.core.domain.channel.Channel;
import com.streaming.core.domain.channel.ChannelRepository;
import com.streaming.core.domain.schedule.Schedule;
import com.streaming.core.media.OmeApiClient;
import com.streaming.core.media.OmeRecorder;
import com.streaming.core.service.BroadcastService;
import com.streaming.core.service.ChannelService;
import com.streaming.core.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OME AdmissionWebhooks → Spring Boot.
 * 응답 {"allowed": true} 면 허용, false 면 OME 가 송출을 끊는다. closing 에는 {} 응답.
 *
 *  incoming opening  스트림키 인증 → 채널 LIVE → 방송 회차(Broadcast) 시작 → (녹화 ON) OME 녹화 시작
 *  incoming closing  (허용했던 연결만) 채널 OFFLINE → 방송 회차 종료 → OME 녹화 종료
 *  (녹화 완료 확인은 LiveJobs.completeRecordings)
 *
 * X-OME-Signature = base64url(HMAC-SHA1(본문, SecretKey)) 로 OME 가 보낸 요청인지 검증한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/hooks/ome")
@RequiredArgsConstructor
public class OmeWebhookController {

    private final ObjectMapper objectMapper;
    private final ChannelRepository channelRepository;
    private final ChannelService channelService;
    private final ScheduleService scheduleService;
    private final BroadcastService broadcastService;
    private final OmeRecorder recorder;
    private final OmeApiClient ome;

    /** 채널코드 → 허용한 송출 연결(주소:포트) */
    private final Map<String, String> publishers = new ConcurrentHashMap<>();

    /** infra/ome/Server.xml AdmissionWebhooks SecretKey (비우면 서명 검증 생략) */
    @Value("${app.ome.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.ome.app:live}")
    private String omeApp;

    /** true 이면 편성된 시간에만 송출 허용 */
    @Value("${app.publish.require-schedule:false}")
    private boolean requireSchedule;

    @Value("${app.publish.early-minutes:10}")
    private int earlyMinutes;

    @Value("${app.recording.default-enabled:true}")
    private boolean recordWithoutSchedule;

    @PostMapping("/admission")
    public ResponseEntity<Map<String, Object>> admission(@RequestBody String body,
                                                         @RequestHeader(value = "X-OME-Signature", required = false) String signature) {
        if (!validSignature(body, signature)) {
            log.warn("[ome] 서명 불일치 — OME 가 아닌 요청 거부");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("allowed", false, "reason", "invalid signature"));
        }
        OmeAdmissionRequest req;
        try {
            req = objectMapper.readValue(body, OmeAdmissionRequest.class);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("allowed", false, "reason", "invalid body"));
        }

        if (!req.incoming()) {
            // 재생(outgoing) 은 webhook 대상이 아니지만, 켜져 있어도 막지 않는다
            return ResponseEntity.ok(req.opening() ? Map.of("allowed", true) : Map.of());
        }
        if (req.opening()) {
            return ResponseEntity.ok(onPublish(req));
        }
        onUnpublish(req);
        return ResponseEntity.ok(Map.of());
    }

    private Map<String, Object> onPublish(OmeAdmissionRequest req) {
        log.info("[ome] 송출 요청 app={}, stream={}, ip={}", req.app(), req.stream(), req.ip());

        if (!omeApp.equals(req.app())) {
            return reject("잘못된 앱 이름 (OBS 서버 주소는 rtmp://{호스트}/" + omeApp + ")", req);
        }
        Optional<Channel> found = channelRepository.findByCode(req.stream());
        if (found.isEmpty()) {
            return reject("존재하지 않는 채널", req);
        }
        Channel channel = found.get();
        if (!channel.matchesKey(req.streamKey())) {
            return reject("스트림키 불일치", req);
        }
        if (channel.isPublishLocked()) {
            return reject("관리자가 방송을 중지함 (모니터링에서 [송출 허용] 필요)", req);
        }
        Optional<Schedule> onAir = scheduleService.findOnAir(channel.getId(), earlyMinutes);
        if (requireSchedule && onAir.isEmpty()) {
            return reject("편성 시간이 아님", req);
        }

        // OBS 재접속 등으로 closing 없이 다시 열린 경우: 이전 회차와 그 녹화를 먼저 정리
        broadcastService.end(channel.getCode()).forEach(recorder::stop);

        channelService.markLive(channel);
        Broadcast b = broadcastService.start(channel.getId(), onAir.map(Schedule::getId).orElse(null), recordWithoutSchedule);
        if (b.getRecordingStatus() == RecordingStatus.RECORDING) {
            Long id = b.getId();
            recorder.start(id, channel.getCode()).thenAccept(ok -> {
                if (!ok) broadcastService.failRecording(id, "OME 녹화 시작 실패 (REST API 연결 확인)");
            });
        }
        publishers.put(channel.getCode(), req.session());
        log.info("[ome] 송출 허용 channel={}, broadcast={}", channel.getCode(), b.getId());
        return Map.of("allowed", true);
    }

    private void onUnpublish(OmeAdmissionRequest req) {
        if (req.stream() == null || !isAcceptedSession(req)) {
            // OME 는 거부한 송출 시도에도 closing 을 보낸다 → 방송 중인 채널을 끄지 않도록 무시
            log.info("[ome] 거부됐던 송출 연결 종료 (무시) stream={}, client={}", req.stream(), req.session());
            return;
        }
        log.info("[ome] 송출 종료 stream={}", req.stream());
        publishers.remove(req.stream());
        channelService.markOffline(req.stream());
        List<Long> recording = broadcastService.end(req.stream());
        recording.forEach(recorder::stop);
    }

    /**
     * closing 이 허용했던 송출 연결의 것인지.
     * 기록이 있으면 연결(주소:포트)로 비교하고, 서버 재시작 등으로 기록이 없으면 OME 에 스트림이 남아 있는지로 판단한다.
     */
    private boolean isAcceptedSession(OmeAdmissionRequest req) {
        String accepted = publishers.get(req.stream());
        if (accepted != null) return accepted.equals(req.session());
        return ome.streamNames().map(names -> !names.contains(req.stream())).orElse(true);
    }

    private Map<String, Object> reject(String reason, OmeAdmissionRequest req) {
        log.warn("[ome] 송출 거부 - {} (stream={}, ip={})", reason, req.stream(), req.ip());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("allowed", false);
        res.put("reason", reason);
        return res;
    }

    private boolean validSignature(String body, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) return true;
        if (signature == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] expected = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            byte[] actual = Base64.getUrlDecoder().decode(signature.strip());
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }
}
