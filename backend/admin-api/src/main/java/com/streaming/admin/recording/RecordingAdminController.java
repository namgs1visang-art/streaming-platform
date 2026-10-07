package com.streaming.admin.recording;

import com.streaming.admin.common.PageResponse;
import com.streaming.core.domain.broadcast.Broadcast;
import com.streaming.core.domain.broadcast.BroadcastRepository;
import com.streaming.core.domain.chat.ChatMessageRepository;
import com.streaming.core.domain.chat.ChatMessageStatus;
import com.streaming.core.domain.quiz.QuizPushRepository;
import com.streaming.core.service.BroadcastService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/** 관리자 > VOD > 방송 녹화 관리 (방송 회차 + 녹화 파일 + 채팅/퀴즈 수) */
@RestController
@RequestMapping("/api/admin/broadcasts")
@RequiredArgsConstructor
public class RecordingAdminController {

    private final BroadcastRepository broadcastRepository;
    private final BroadcastService broadcastService;
    private final ChatMessageRepository chatMessageRepository;
    private final QuizPushRepository quizPushRepository;

    @Value("${app.media.http-base-url}")
    private String httpBaseUrl;

    public record Item(Long id, Long channelId, String channelCode, String channelName, String scheduleTitle,
                       String title, LocalDateTime startedAt, LocalDateTime endedAt, Integer durationSec,
                       String recordingStatus, String recordingError, String videoUrl, Long fileSizeBytes,
                       long chatCount, long quizCount) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<Item> list(@RequestParam(required = false) Long channelId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        Page<Broadcast> result = channelId == null
                ? broadcastRepository.findPage(pr)
                : broadcastRepository.findPageByChannel(channelId, pr);
        return PageResponse.of(result, this::toItem);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        broadcastService.delete(id); // 이력만 삭제 (SRS 녹화 파일은 그대로)
    }

    private Item toItem(Broadcast b) {
        LocalDateTime end = b.windowEnd();
        long chat = chatMessageRepository.countByChannelCodeAndStatusAndCreatedAtBetween(
                b.getChannel().getCode(), ChatMessageStatus.VISIBLE, b.getStartedAt(), end);
        long quiz = quizPushRepository.countByChannelIdAndPushedAtBetween(b.getChannel().getId(), b.getStartedAt(), end);
        return new Item(b.getId(), b.getChannel().getId(), b.getChannel().getCode(), b.getChannel().getName(),
                b.getSchedule() != null ? b.getSchedule().getTitle() : null,
                b.getTitle(), b.getStartedAt(), b.getEndedAt(), b.getDurationSec(),
                b.getRecordingStatus().name(), b.getRecordingError(),
                b.getVideoPath() != null ? httpBaseUrl + "/" + b.getVideoPath() : null,
                b.getFileSizeBytes(), chat, quiz);
    }
}
