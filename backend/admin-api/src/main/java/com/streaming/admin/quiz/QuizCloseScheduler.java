package com.streaming.admin.quiz;

import com.streaming.core.service.QuizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 제한 시간이 끝난 퀴즈 자동 마감 + 결과 발표 (viewer-api 에도 같은 스케줄러가 있으며, 조건부 update 로 한 번만 처리됨) */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuizCloseScheduler {

    private final QuizService quizService;

    @Scheduled(fixedDelay = 1000)
    public void closeExpired() {
        try {
            quizService.closeExpired();
        } catch (Exception e) {
            log.warn("퀴즈 자동 마감 실패: {}", e.getMessage());
        }
    }
}
