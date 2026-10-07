package com.streaming.chat.message;

import com.streaming.chat.service.ChannelChatStateCache;
import com.streaming.chat.service.ChatMessageWriter;
import com.streaming.chat.service.StickerCache;
import com.streaming.core.chat.ChatEvent;
import com.streaming.core.chat.ChatEventPublisher;
import com.streaming.core.chat.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 시청자 채팅/스티커 전송: STOMP SEND /app/chat/{channelCode}
 *  { sender, clientId, content }            텍스트
 *  { sender, clientId, stickerId }          스티커
 *
 * 검증: 채널 존재 → 채팅 얼리기 → 도배 → (스티커) 스티커 얼리기/사용 여부
 * 통과하면 DB 저장 큐에 넣고 Redis 로 발행 (모든 chat-server 가 시청자에게 전달)
 * TODO(로그인 연동): sender/clientId 를 토큰의 사용자 정보로 대체, 사용자 제재 체크
 */
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatEventPublisher publisher;
    private final ChatMessageWriter writer;
    private final ChannelChatStateCache stateCache;
    private final StickerCache stickerCache;

    @Value("${app.chat.max-length:200}")
    private int maxLength;

    @Value("${app.chat.min-interval-ms:500}")
    private long minIntervalMs;

    /** 세션별 마지막 전송 시각 (도배 방지) */
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();

    public record SendRequest(String sender, String clientId, String content, Long stickerId) {
    }

    @MessageMapping("/chat/{channelCode}")
    public void send(@DestinationVariable String channelCode, @Payload SendRequest req,
                     SimpMessageHeaderAccessor accessor) {
        ChannelChatStateCache.State state = stateCache.get(channelCode)
                .orElseThrow(() -> new ChatRejectedException("NO_CHANNEL", "존재하지 않는 채널입니다."));
        if (state.chatFrozen()) {
            throw new ChatRejectedException("CHAT_FROZEN", "관리자가 채팅을 얼렸습니다. 잠시 후 다시 시도해 주세요.");
        }

        boolean sticker = req.stickerId() != null;
        String content = req.content() == null ? "" : req.content().strip();
        if (!sticker && content.isEmpty()) return;

        checkRate(accessor.getSessionId());

        String sender = (req.sender() == null || req.sender().isBlank()) ? "익명" : truncate(req.sender().strip(), 50);
        String clientId = req.clientId() == null ? null : truncate(req.clientId(), 64);

        ChatEvent event;
        if (sticker) {
            if (state.stickerFrozen()) {
                throw new ChatRejectedException("STICKER_FROZEN", "관리자가 스티커 사용을 중지했습니다.");
            }
            StickerCache.StickerInfo s = stickerCache.get(req.stickerId())
                    .orElseThrow(() -> new ChatRejectedException("NO_STICKER", "사용할 수 없는 스티커입니다."));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("stickerId", s.id());
            payload.put("name", s.name());
            payload.put("emoji", s.emoji());
            payload.put("imageUrl", s.imageUrl());
            event = ChatEvent.of(MessageType.STICKER, channelCode, sender, clientId, "[스티커] " + s.name(), payload);
        } else {
            event = ChatEvent.of(MessageType.CHAT, channelCode, sender, clientId, truncate(content, maxLength), null);
        }

        writer.enqueue(event);
        publisher.publish(event);
    }

    @MessageExceptionHandler(ChatRejectedException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Map<String, String> rejected(ChatRejectedException e) {
        return Map.of("code", e.getCode(), "message", e.getMessage());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        lastSent.remove(event.getSessionId());
    }

    private void checkRate(String sessionId) {
        if (sessionId == null) return;
        long now = System.currentTimeMillis();
        Long prev = lastSent.put(sessionId, now);
        if (prev != null && now - prev < minIntervalMs) {
            throw new ChatRejectedException("RATE_LIMITED", "메시지를 너무 빠르게 보내고 있습니다.");
        }
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
