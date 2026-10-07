package com.streaming.viewer.hook;

import com.streaming.core.domain.channel.Channel;
import com.streaming.core.domain.channel.ChannelRepository;
import com.streaming.core.domain.schedule.Schedule;
import com.streaming.core.service.BroadcastService;
import com.streaming.core.service.ChannelService;
import com.streaming.core.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * SRS → Spring Boot 콜백.
 * 응답 code 가 0 이면 허용, 그 외(또는 HTTP 200 이 아니면) SRS가 송출을 끊는다.
 *
 *  on_publish   스트림키 인증 → 채널 LIVE → 방송 회차(Broadcast) 시작 (편성의 녹화 옵션 반영)
 *  on_unpublish 채널 OFFLINE → 방송 회차 종료
 *  on_dvr       녹화 mp4 완료 → 방송 회차에 연결 → 다시보기 가능
 */
@Slf4j
@RestController
@RequestMapping("/api/hooks/srs")
@RequiredArgsConstructor
public class SrsHookController {

    private static final Map<String, Integer> OK = Map.of("code", 0);
    /** SRS http_server 의 dir (srs.conf: ./objs/nginx/html) — 이 뒤가 재생 URL 경로 */
    private static final String SRS_HTML_DIR = "objs/nginx/html/";

    private final ChannelRepository channelRepository;
    private final ChannelService channelService;
    private final ScheduleService scheduleService;
    private final BroadcastService broadcastService;

    /** true 이면 편성된 시간에만 송출 허용 */
    @Value("${app.publish.require-schedule:false}")
    private boolean requireSchedule;

    @Value("${app.publish.early-minutes:10}")
    private int earlyMinutes;

    @Value("${app.recording.default-enabled:true}")
    private boolean recordWithoutSchedule;

    @Value("${app.recording.dvr-host-dir:}")
    private String dvrHostDir;

    @PostMapping("/on_publish")
    public ResponseEntity<Map<String, Integer>> onPublish(@RequestBody SrsHookRequest req) {
        log.info("[on_publish] app={}, stream={}, ip={}", req.app(), req.stream(), req.ip());

        Optional<Channel> found = channelRepository.findByCode(req.stream());
        if (found.isEmpty()) {
            return reject("존재하지 않는 채널", req);
        }
        Channel channel = found.get();
        if (!channel.matchesKey(req.streamKey())) {
            return reject("스트림키 불일치", req);
        }
        Optional<Schedule> onAir = scheduleService.findOnAir(channel.getId(), earlyMinutes);
        if (requireSchedule && onAir.isEmpty()) {
            return reject("편성 시간이 아님", req);
        }

        channelService.markLive(channel);
        broadcastService.start(channel.getId(), onAir.map(Schedule::getId).orElse(null), recordWithoutSchedule);
        log.info("[on_publish] 송출 허용 channel={}", channel.getCode());
        return ResponseEntity.ok(OK);
    }

    @PostMapping("/on_unpublish")
    public Map<String, Integer> onUnpublish(@RequestBody SrsHookRequest req) {
        log.info("[on_unpublish] stream={}", req.stream());
        channelService.markOffline(req.stream());
        broadcastService.end(req.stream());
        return OK;
    }

    /** 녹화 완료 (dvr_plan session → 송출 1회당 mp4 1개) */
    @PostMapping("/on_dvr")
    public Map<String, Integer> onDvr(@RequestBody SrsHookRequest req) {
        log.info("[on_dvr] stream={}, file={}", req.stream(), req.file());
        String videoPath = toHttpPath(req.file());
        if (videoPath == null) {
            log.warn("[on_dvr] 녹화 파일 경로를 해석할 수 없음: {}", req.file());
            return OK;
        }
        Path hostFile = hostFile(videoPath);
        Long size = sizeOf(hostFile);
        boolean attached = broadcastService.attachRecording(req.stream(), videoPath, size);
        if (!attached && hostFile != null) {
            // 녹화 OFF 편성 → SRS 는 모든 송출을 녹화하므로 파일 정리
            try {
                Files.deleteIfExists(hostFile);
                log.info("[on_dvr] 녹화 OFF 방송 파일 삭제 {}", hostFile);
            } catch (IOException e) {
                log.warn("[on_dvr] 파일 삭제 실패 {}", hostFile, e);
            }
        }
        return OK;
    }

    /** "./objs/nginx/html/dvr/live/ch1/123.mp4" → "dvr/live/ch1/123.mp4" (SRS http_server 경로) */
    static String toHttpPath(String file) {
        if (file == null || file.isBlank()) return null;
        String f = file.replace('\\', '/');
        int idx = f.indexOf(SRS_HTML_DIR);
        if (idx >= 0) return f.substring(idx + SRS_HTML_DIR.length());
        int dvr = f.indexOf("dvr/");
        return dvr >= 0 ? f.substring(dvr) : null;
    }

    /** app.recording.dvr-host-dir(= infra/srs/dvr) 가 설정돼 있으면 PC 상의 실제 파일 */
    private Path hostFile(String videoPath) {
        if (dvrHostDir == null || dvrHostDir.isBlank() || !videoPath.startsWith("dvr/")) return null;
        return Path.of(dvrHostDir).resolve(videoPath.substring("dvr/".length())).normalize();
    }

    private static Long sizeOf(Path file) {
        try {
            return file != null && Files.exists(file) ? Files.size(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private ResponseEntity<Map<String, Integer>> reject(String reason, SrsHookRequest req) {
        log.warn("[on_publish] 송출 거부 - {} (stream={}, ip={})", reason, req.stream(), req.ip());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", 403));
    }
}
