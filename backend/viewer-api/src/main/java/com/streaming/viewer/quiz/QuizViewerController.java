package com.streaming.viewer.quiz;

import com.streaming.core.domain.quiz.QuizPush;
import com.streaming.core.service.QuizService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/** 시청자 퀴즈/투표 응답 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class QuizViewerController {

    private final QuizService quizService;

    public record AnswerRequest(@NotBlank String clientId, String nickname, @Min(0) int answerIndex) {
    }

    /** 입장/재접속 시 진행 중인 퀴즈 (없으면 quiz: null). 이미 응답했으면 myAnswered: true */
    @GetMapping("/channels/{code}/quiz/active")
    public Map<String, Object> active(@PathVariable String code, @RequestParam(required = false) String clientId) {
        Map<String, Object> res = new LinkedHashMap<>();
        QuizPush p = quizService.findOpen(code).orElse(null);
        if (p == null) {
            res.put("quiz", null);
            return res;
        }
        Map<String, Object> quiz = new LinkedHashMap<>();
        quiz.put("pushId", p.getId());
        quiz.put("title", p.getTitle());
        quiz.put("question", p.getQuestion());
        quiz.put("options", p.getOptions());
        quiz.put("mode", p.isVote() ? "VOTE" : "QUIZ");
        quiz.put("timeLimitSec", p.getTimeLimitSec());
        quiz.put("closesAt", p.getClosesAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        QuizService.Stats stats = quizService.liveStats(p.getId(), p.getOptions().size());
        res.put("quiz", quiz);
        res.put("stats", Map.of("counts", stats.counts(), "total", stats.total()));
        res.put("myAnswered", quizService.hasAnswered(p.getId(), clientId));
        return res;
    }

    /** 응답 (1인 1회) → 현재 집계 반환 */
    @PostMapping("/quiz/{pushId}/answers")
    public Map<String, Object> answer(@PathVariable Long pushId, @Valid @RequestBody AnswerRequest req) {
        QuizService.Stats s = quizService.submitAnswer(pushId, req.clientId(), req.nickname(), req.answerIndex());
        return Map.of("accepted", true, "counts", s.counts(), "total", s.total());
    }
}
