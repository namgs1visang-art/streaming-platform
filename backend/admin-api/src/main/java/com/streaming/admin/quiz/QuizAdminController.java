package com.streaming.admin.quiz;

import com.streaming.admin.common.PageResponse;
import com.streaming.core.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 관리자 > 퀴즈 관리 > 퀴즈 관리 (문제 은행) */
@RestController
@RequestMapping("/api/admin/quizzes")
@RequiredArgsConstructor
public class QuizAdminController {

    private final QuizService quizService;

    @GetMapping
    public PageResponse<QuizDto.QuizResponse> list(@RequestParam(defaultValue = "") String keyword,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(quizService.search(keyword, page, size), QuizDto.QuizResponse::of);
    }

    @GetMapping("/{id}")
    public QuizDto.QuizResponse get(@PathVariable Long id) {
        return QuizDto.QuizResponse.of(quizService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDto.QuizResponse create(@Valid @RequestBody QuizDto.QuizRequest req) {
        return QuizDto.QuizResponse.of(quizService.create(req.title(), req.question(), req.options(), req.answerIndex(), req.timeLimitSec()));
    }

    @PutMapping("/{id}")
    public QuizDto.QuizResponse update(@PathVariable Long id, @Valid @RequestBody QuizDto.QuizRequest req) {
        return QuizDto.QuizResponse.of(quizService.update(id, req.title(), req.question(), req.options(), req.answerIndex(), req.timeLimitSec()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        quizService.delete(id);
    }
}
