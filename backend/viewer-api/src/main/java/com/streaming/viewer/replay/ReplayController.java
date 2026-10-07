package com.streaming.viewer.replay;

import com.streaming.core.common.ConflictException;
import com.streaming.core.domain.broadcast.Broadcast;
import com.streaming.core.domain.broadcast.BroadcastRepository;
import com.streaming.core.domain.broadcast.RecordingStatus;
import com.streaming.core.domain.chat.ChatMessage;
import com.streaming.core.domain.chat.ChatMessageRepository;
import com.streaming.core.domain.chat.ChatMessageStatus;
import com.streaming.core.domain.quiz.QuizPushRepository;
import com.streaming.core.service.BroadcastService;
import com.streaming.core.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 다시보기 = 녹화 영상(mp4) + 그 방송 시간대의 채팅/퀴즈를 같은 시간축으로 재생.
 * 영상 0초 = Broadcast.startedAt, 채팅 오프셋(ms) = 채팅 시각 - startedAt.
 * 관리자가 숨김/삭제한 메시지는 다시보기에서도 보이지 않는다.
 */
@RestController
@RequestMapping("/api/replays")
@RequiredArgsConstructor
public class ReplayController {

    private static final int MAX_CHAT_PER_WINDOW = 3000;

    private final BroadcastRepository broadcastRepository;
    private final BroadcastService broadcastService;
    private final ChatMessageRepository chatMessageRepository;
    private final QuizPushRepository quizPushRepository;
    private final QuizService quizService;

    @Value("${app.media.http-base-url}")
    private String httpBaseUrl;

    public record ReplayItem(Long id, String title, String channelCode, String channelName,
                             LocalDateTime startedAt, Integer durationSec) {
    }

    public record ReplayQuiz(Long pushId, long offsetSec, long durationSec, String title, String question,
                             List<String> options, Integer answerIndex, String mode, List<Long> counts, long total) {
    }

    public record ReplayDetail(Long id, String title, String channelCode, String channelName,
                               LocalDateTime startedAt, LocalDateTime endedAt, Integer durationSec,
                               String videoUrl, long chatCount, List<ReplayQuiz> quizzes) {
    }

    public record ReplayChat(String id, String type, String sender, String content,
                             Map<String, Object> payload, long offsetMs) {
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "24") int size) {
        Page<Broadcast> p = broadcastRepository.findReplays(PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        List<ReplayItem> items = p.getContent().stream()
                .map(b -> new ReplayItem(b.getId(), b.getTitle(), b.getChannel().getCode(), b.getChannel().getName(),
                        b.getStartedAt(), b.getDurationSec()))
                .toList();
        return Map.of("items", items, "total", p.getTotalElements());
    }

    @GetMapping("/{id}")
    public ReplayDetail detail(@PathVariable Long id) {
        Broadcast b = ready(id);
        LocalDateTime end = b.windowEnd();
        long chatCount = chatMessageRepository.countByChannelCodeAndStatusAndCreatedAtBetween(
                b.getChannel().getCode(), ChatMessageStatus.VISIBLE, b.getStartedAt(), end);
        List<ReplayQuiz> quizzes = quizPushRepository.findInWindow(b.getChannel().getId(), b.getStartedAt(), end).stream()
                .map(p -> {
                    QuizService.Stats s = quizService.finalStats(p);
                    return new ReplayQuiz(p.getId(),
                            Math.max(0, Duration.between(b.getStartedAt(), p.getPushedAt()).toSeconds()),
                            Math.max(3, Duration.between(p.getPushedAt(), p.getClosesAt()).toSeconds()),
                            p.getTitle(), p.getQuestion(), p.getOptions(), p.getAnswerIndex(),
                            p.isVote() ? "VOTE" : "QUIZ", s.counts(), s.total());
                })
                .toList();
        return new ReplayDetail(b.getId(), b.getTitle(), b.getChannel().getCode(), b.getChannel().getName(),
                b.getStartedAt(), b.getEndedAt(), b.getDurationSec(), httpBaseUrl + "/" + b.getVideoPath(),
                chatCount, quizzes);
    }

    /** 채팅 구간 조회 [from, to) 초 — 영상 위치에 맞춰 구간 단위로 가져간다 */
    @GetMapping("/{id}/chat")
    public Map<String, Object> chat(@PathVariable Long id,
                                    @RequestParam(defaultValue = "0") long from,
                                    @RequestParam(defaultValue = "120") long to) {
        Broadcast b = ready(id);
        LocalDateTime base = b.getStartedAt();
        List<ChatMessage> rows = chatMessageRepository.findInWindow(b.getChannel().getCode(), ChatMessageStatus.VISIBLE,
                base.plusSeconds(Math.max(0, from)), base.plusSeconds(Math.max(from, to)), PageRequest.of(0, MAX_CHAT_PER_WINDOW));
        List<ReplayChat> messages = rows.stream()
                .map(m -> new ReplayChat(m.getId(), m.getType().name(), m.getSender(), m.getContent(), m.getPayload(),
                        Duration.between(base, m.getCreatedAt()).toMillis()))
                .toList();
        return Map.of("from", from, "to", to, "truncated", rows.size() == MAX_CHAT_PER_WINDOW, "messages", messages);
    }

    private Broadcast ready(Long id) {
        Broadcast b = broadcastService.get(id);
        if (b.getRecordingStatus() != RecordingStatus.READY || b.getVideoPath() == null) {
            String why = switch (b.getRecordingStatus()) {
                case NONE -> "녹화하지 않은 방송입니다.";
                case RECORDING -> "아직 녹화 중이거나 저장 처리 중입니다.";
                case FAILED -> "녹화에 실패했습니다." + (b.getRecordingError() != null ? " (" + b.getRecordingError() + ")" : "");
                case READY -> "녹화 정보가 올바르지 않습니다.";
            };
            throw new ConflictException("다시보기를 사용할 수 없습니다. " + why);
        }
        return b;
    }
}
