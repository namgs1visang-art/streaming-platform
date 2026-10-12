package com.streaming.admin.monitor;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 관리자 > LIVE > 모니터링 — 방송 상태 확인 + 한 화면에서 통제
 * 채팅 얼리기: PUT /api/admin/chat/rooms/{channelId}/freeze
 * 메시지 숨김/삭제/관리자 메시지: /api/admin/chat/messages/**
 * 퀴즈 조기 마감: POST /api/admin/quiz-schedules/pushes/{pushId}/close
 */
@RestController
@RequestMapping("/api/admin/monitor/channels")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    @GetMapping
    public List<MonitorDto.ChannelStatus> channels() {
        return monitorService.channels();
    }

    @GetMapping("/{id}")
    public MonitorDto.Detail detail(@PathVariable Long id) {
        return monitorService.detail(id);
    }

    /** 방송 중지 (송출 잠금 + 강제 종료) */
    @PostMapping("/{id}/stop")
    public MonitorDto.StopResult stop(@PathVariable Long id, @Valid @RequestBody(required = false) MonitorDto.StopRequest req) {
        return monitorService.stop(id, req == null ? null : req.message());
    }

    /** 송출 허용 (잠금 해제) */
    @PostMapping("/{id}/allow-publish")
    public MonitorDto.ChannelStatus allowPublish(@PathVariable Long id) {
        return monitorService.allowPublish(id);
    }

    @PostMapping("/{id}/notice")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void notice(@PathVariable Long id, @Valid @RequestBody MonitorDto.NoticeRequest req) {
        monitorService.notice(id, req.content());
    }

    /** 퀴즈/투표 바로 출제 */
    @PostMapping("/{id}/quiz-pushes")
    public Map<String, Long> push(@PathVariable Long id, @Valid @RequestBody MonitorDto.PushRequest req) {
        return Map.of("pushId", monitorService.pushQuiz(id, req.quizId()));
    }
}
