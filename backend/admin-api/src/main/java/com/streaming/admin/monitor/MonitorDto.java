package com.streaming.admin.monitor;

import com.streaming.admin.quiz.QuizDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class MonitorDto {

    public record ScheduleInfo(Long id, String title, LocalDateTime startAt, LocalDateTime endAt) {
    }

    public record BroadcastInfo(Long id, LocalDateTime startedAt, String recordingStatus) {
    }

    /** OME 입력 스트림 상태. bframes=true 면 WebRTC 재생이 끊길 수 있음 (OBS B-프레임 0 필요) */
    public record StreamInfo(int kbpsIn, String videoCodec, Integer width, Integer height, Double framerate,
                             boolean bframes, String audioCodec, int mediaConnections) {
    }

    public record QuizInfo(Long pushId, String title, String question, String mode, List<String> options,
                           Integer answerIndex, LocalDateTime closesAt, List<Long> counts, long total) {
    }

    /** 모니터링 카드 1개 = 채널 1개의 현재 상태 */
    public record ChannelStatus(Long id, String code, String name, String status, LocalDateTime liveStartedAt,
                                String playbackUrl, String webrtcUrl, long viewerCount,
                                boolean chatFrozen, boolean stickerFrozen, boolean publishLocked,
                                ScheduleInfo schedule, BroadcastInfo broadcast, StreamInfo stream, QuizInfo quiz) {
    }

    /** 컨트롤 화면: 상태 + 지금 편성에 담긴 퀴즈 */
    public record Detail(ChannelStatus channel, List<QuizDto.Mapping> scheduleQuizzes) {
    }

    public record StopRequest(@Size(max = 200) String message) {
    }

    public record NoticeRequest(@NotBlank @Size(max = 200) String content) {
    }

    public record PushRequest(@NotNull Long quizId) {
    }

    public record StopResult(boolean kicked, ChannelStatus channel) {
    }
}
