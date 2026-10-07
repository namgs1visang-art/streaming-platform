package com.streaming.admin.quiz;

import com.streaming.core.domain.quiz.QuizPush;
import com.streaming.core.domain.schedule.Schedule;
import com.streaming.core.service.QuizService;
import com.streaming.core.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 관리자 > 퀴즈 관리 > 편성 퀴즈 관리
 * 스케줄(방송)마다 출제할 퀴즈를 담아 두고, 방송 중에 [출제] → 시청자 투표/응답 → 자동/조기 마감.
 */
@RestController
@RequestMapping("/api/admin/quiz-schedules")
@RequiredArgsConstructor
public class QuizScheduleController {

    private final ScheduleService scheduleService;
    private final QuizService quizService;

    /** 기간 내 스케줄 + 담긴 퀴즈 수 */
    @GetMapping
    public List<QuizDto.ScheduleItem> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleService.findInRange(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream()
                .map(s -> QuizDto.ScheduleItem.of(s, quizService.countScheduleQuizzes(s.getId())))
                .toList();
    }

    /** 편성 퀴즈 목록 + 출제 이력(집계 포함) */
    @GetMapping("/{scheduleId}")
    public QuizDto.ScheduleDetail detail(@PathVariable Long scheduleId) {
        Schedule s = scheduleService.get(scheduleId);
        List<QuizDto.Mapping> quizzes = quizService.findScheduleQuizzes(scheduleId).stream().map(QuizDto.Mapping::of).toList();
        LocalDateTime now = LocalDateTime.now();
        List<QuizDto.PushView> pushes = quizService.findPushes(scheduleId).stream().map(p -> {
            boolean open = p.isOpen(now);
            QuizService.Stats stats = open ? quizService.liveStats(p.getId(), p.getOptions().size()) : quizService.finalStats(p);
            return QuizDto.PushView.of(p, stats, open);
        }).toList();
        return new QuizDto.ScheduleDetail(QuizDto.ScheduleItem.of(s, quizzes.size()), quizzes, pushes);
    }

    @PostMapping("/{scheduleId}/quizzes")
    public Map<String, Integer> add(@PathVariable Long scheduleId, @Valid @RequestBody QuizDto.AddQuizzesRequest req) {
        return Map.of("added", quizService.addToSchedule(scheduleId, req.quizIds()));
    }

    @DeleteMapping("/{scheduleId}/quizzes/{mappingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long scheduleId, @PathVariable Long mappingId) {
        quizService.removeFromSchedule(scheduleId, mappingId);
    }

    @PutMapping("/{scheduleId}/quizzes/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@PathVariable Long scheduleId, @Valid @RequestBody QuizDto.ReorderRequest req) {
        quizService.reorder(scheduleId, req.mappingIds());
    }

    /** 출제 — 채널이 LIVE 일 때만 */
    @PostMapping("/{scheduleId}/pushes")
    public Map<String, Object> push(@PathVariable Long scheduleId, @Valid @RequestBody QuizDto.PushRequest req) {
        QuizPush p = quizService.push(scheduleId, req.quizId());
        return Map.of("pushId", p.getId(), "closesAt", p.getClosesAt());
    }

    /** 조기 마감 → 즉시 결과 발표 */
    @PostMapping("/pushes/{pushId}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable Long pushId) {
        quizService.closeEarly(pushId);
    }
}
