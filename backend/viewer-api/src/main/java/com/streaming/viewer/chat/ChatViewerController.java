package com.streaming.viewer.chat;

import com.streaming.core.chat.ChatEvent;
import com.streaming.core.domain.broadcast.Broadcast;
import com.streaming.core.domain.channel.Channel;
import com.streaming.core.domain.chat.ChatMessage;
import com.streaming.core.domain.chat.ChatMessageRepository;
import com.streaming.core.domain.chat.ChatMessageStatus;
import com.streaming.core.service.BroadcastService;
import com.streaming.core.service.ChannelService;
import com.streaming.core.service.StickerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** 시청자 채팅 부가 API (메시지 전송 자체는 chat-server WebSocket) */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatViewerController {

    private final ChannelService channelService;
    private final BroadcastService broadcastService;
    private final ChatMessageRepository messageRepository;
    private final StickerService stickerService;

    public record ChatInit(boolean chatFrozen, boolean stickerFrozen, List<ChatEvent> messages) {
    }

    public record StickerView(Long id, String name, String emoji, String imageUrl) {
    }

    /**
     * 시청 화면 입장 시: 얼리기 상태 + 지금 방송의 최근 채팅.
     * 방송 중이면 이번 방송 시작 이후 메시지만 (이전 방송 채팅이 이어 보이지 않도록), 아니면 최근 30분.
     */
    @GetMapping("/channels/{code}/chat")
    public ChatInit chat(@PathVariable String code, @RequestParam(defaultValue = "50") int limit) {
        Channel channel = channelService.getByCode(code);
        LocalDateTime since = broadcastService.findLive(code)
                .map(Broadcast::getStartedAt)
                .orElse(LocalDateTime.now().minusMinutes(30));
        List<ChatMessage> rows = messageRepository.findRecent(code, EnumSet.of(ChatMessageStatus.VISIBLE), since,
                PageRequest.of(0, Math.min(200, Math.max(1, limit))));
        List<ChatEvent> messages = new ArrayList<>(rows.stream().map(ChatMessage::toEvent).toList());
        Collections.reverse(messages);
        return new ChatInit(channel.isChatFrozen(), channel.isStickerFrozen(), messages);
    }

    /** 스티커 선택창 */
    @GetMapping("/stickers")
    public List<StickerView> stickers() {
        return stickerService.findEnabled().stream()
                .map(s -> new StickerView(s.getId(), s.getName(), s.getEmoji(), s.getImageUrl()))
                .toList();
    }
}
