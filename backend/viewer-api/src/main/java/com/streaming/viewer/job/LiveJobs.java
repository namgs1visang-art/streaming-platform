package com.streaming.viewer.job;

import com.streaming.core.domain.broadcast.Broadcast;
import com.streaming.core.media.OmeRecorder;
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
    private final OmeRecorder recorder;
    private final RecordingFileChecker fileChecker;

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

    /** 종료된 방송의 OME 녹화가 끝났으면(info xml 생성) mp4 를 연결 → 다시보기 가능 */
    @Scheduled(fixedDelay = 5000)
    public void completeRecordings() {
        try {
            for (Broadcast b : broadcastService.findPendingRecordings()) {
                String code = b.getChannel().getCode();
                String video = recorder.videoPath(b.getId(), code);
                fileChecker.completedSize(recorder.infoPath(b.getId(), code), video)
                        .ifPresent(size -> broadcastService.attachRecording(b.getId(), video, size >= 0 ? size : null));
            }
        } catch (Exception e) {
            log.warn("녹화 완료 확인 실패: {}", e.getMessage());
        }
    }

    /** 종료 후 일정 시간 녹화가 완료되지 않은 방송 → FAILED */
    @Scheduled(fixedDelay = 60_000)
    public void stuckRecordings() {
        int n = broadcastService.failStuck(stuckMinutes);
        if (n > 0) log.warn("[broadcast] 녹화 파일 미완료 {}건 실패 처리", n);
    }
}
