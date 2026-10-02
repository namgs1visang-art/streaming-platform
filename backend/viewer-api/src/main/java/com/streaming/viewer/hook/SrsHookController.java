package com.streaming.viewer.hook;

import com.streaming.core.domain.channel.Channel;
import com.streaming.core.domain.channel.ChannelRepository;
import com.streaming.core.service.ChannelService;
import com.streaming.core.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * SRS → Spring Boot 콜백.
 * 응답 code 가 0 이면 허용, 그 외(또는 HTTP 200 이 아니면) SRS가 송출을 끊는다.
 */
@Slf4j
@RestController
@RequestMapping("/api/hooks/srs")
@RequiredArgsConstructor
public class SrsHookController {

    private static final Map<String, Integer> OK = Map.of("code", 0);

    private final ChannelRepository channelRepository;
    private final ChannelService channelService;
    private final ScheduleService scheduleService;

    /** true 이면 편성된 시간에만 송출 허용 */
    @Value("${app.publish.require-schedule:false}")
    private boolean requireSchedule;

    @Value("${app.publish.early-minutes:10}")
    private int earlyMinutes;

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
        if (requireSchedule && !scheduleService.isOnAir(channel.getId(), earlyMinutes)) {
            return reject("편성 시간이 아님", req);
        }

        channelService.markLive(channel);
        log.info("[on_publish] 송출 허용 channel={}", channel.getCode());
        return ResponseEntity.ok(OK);
    }

    @PostMapping("/on_unpublish")
    public Map<String, Integer> onUnpublish(@RequestBody SrsHookRequest req) {
        log.info("[on_unpublish] stream={}", req.stream());
        channelService.markOffline(req.stream());
        return OK;
    }

    /** 녹화 완료 → 다음 단계: 스토리지(MinIO) 업로드 + VOD 콘텐츠 등록 */
    @PostMapping("/on_dvr")
    public Map<String, Integer> onDvr(@RequestBody SrsHookRequest req) {
        log.info("[on_dvr] stream={}, file={}", req.stream(), req.file());
        // TODO(5단계): VodService.registerRecording(req.stream(), req.file())
        return OK;
    }

    private ResponseEntity<Map<String, Integer>> reject(String reason, SrsHookRequest req) {
        log.warn("[on_publish] 송출 거부 - {} (stream={}, ip={})", reason, req.stream(), req.ip());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", 403));
    }
}
