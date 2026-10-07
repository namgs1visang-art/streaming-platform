package com.streaming.viewer.job;

import com.streaming.core.service.BroadcastService;
import com.streaming.core.service.QuizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 라이브 중 주기 작업 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveJobs {

    private final QuizService quizService;
    private final BroadcastService broadcastService;

    @Value("${app.recording.stuck-minutes:3}")
    private int stuckMinutes;

    /** 퀴즈 실시간 집계 발행 (응답이 들어온 퀴즈만) */
    @Scheduled(fixedDelay = 1000)
    public void quizStats() {
        try {
            quizService.flushLiveStats();
        } catch (Exception e) {
            log.warn("퀴즈 집계 발행 실패: {}", e.getMessage());
        }
    }

    /** 제한 시간 끝난 퀴즈 마감 + 결과 발표 (admin-api 와 중복 실행돼도 한 번만 처리) */
    @Scheduled(fixedDelay = 1000)
    public void quizClose() {
        try {
            quizService.closeExpired();
        } catch (Exception e) {
            log.warn("퀴즈 자동 마감 실패: {}", e.getMessage());
        }
    }

    /** on_dvr 를 받지 못한 녹화 → FAILED */
    @Scheduled(fixedDelay = 60_000)
    public void stuckRecordings() {
        int n = broadcastService.failStuck(stuckMinutes);
        if (n > 0) log.warn("[broadcast] 녹화 파일 미수신 {}건 실패 처리", n);
    }
}
