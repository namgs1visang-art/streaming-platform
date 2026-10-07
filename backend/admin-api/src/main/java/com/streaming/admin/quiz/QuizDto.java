package com.streaming.admin.quiz;

import com.streaming.core.domain.quiz.Quiz;
import com.streaming.core.domain.quiz.QuizPush;
import com.streaming.core.domain.quiz.ScheduleQuiz;
import com.streaming.core.domain.schedule.Schedule;
import com.streaming.core.service.QuizService;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public class QuizDto {

    /** answerIndex 가 null 이면 투표 */
    public record QuizRequest(
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 500) String question,
            @NotNull @Size(min = 2, max = 5) List<@NotBlank @Size(max = 100) String> options,
            Integer answerIndex,
            @Min(5) @Max(600) int timeLimitSec) {
    }

    public record QuizResponse(Long id, String title, String question, List<String> options,
                               Integer answerIndex, String mode, int timeLimitSec, LocalDateTime createdAt) {
        public static QuizResponse of(Quiz q) {
            return new QuizResponse(q.getId(), q.getTitle(), q.getQuestion(), q.getOptions(), q.getAnswerIndex(),
                    q.isVote() ? "VOTE" : "QUIZ", q.getTimeLimitSec(), q.getCreatedAt());
        }
    }

    public record ScheduleItem(Long id, String title, Long channelId, String channelCode, String channelName,
                               String channelStatus, LocalDateTime startAt, LocalDateTime endAt, long quizCount) {
        public static ScheduleItem of(Schedule s, long quizCount) {
            return new ScheduleItem(s.getId(), s.getTitle(), s.getChannel().getId(), s.getChannel().getCode(),
                    s.getChannel().getName(), s.getChannel().getStatus().name(), s.getStartAt(), s.getEndAt(), quizCount);
        }
    }

    public record Mapping(Long mappingId, int orderIndex, QuizResponse quiz) {
        public static Mapping of(ScheduleQuiz sq) {
            return new Mapping(sq.getId(), sq.getOrderIndex(), QuizResponse.of(sq.getQuiz()));
        }
    }

    public record PushView(Long id, Long quizId, String title, String question, List<String> options, Integer answerIndex,
                           String mode, LocalDateTime pushedAt, LocalDateTime closesAt, boolean open,
                           List<Long> counts, long total) {
        public static PushView of(QuizPush p, QuizService.Stats stats, boolean open) {
            return new PushView(p.getId(), p.getQuiz().getId(), p.getTitle(), p.getQuestion(), p.getOptions(), p.getAnswerIndex(),
                    p.isVote() ? "VOTE" : "QUIZ", p.getPushedAt(), p.getClosesAt(), open, stats.counts(), stats.total());
        }
    }

    public record ScheduleDetail(ScheduleItem schedule, List<Mapping> quizzes, List<PushView> pushes) {
    }

    public record AddQuizzesRequest(@NotEmpty List<Long> quizIds) {
    }

    public record ReorderRequest(@NotNull List<Long> mappingIds) {
    }

    public record PushRequest(@NotNull Long quizId) {
    }
}
