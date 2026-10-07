package com.streaming.chat.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streaming.chat.service.ChannelChatStateCache;
import com.streaming.core.chat.ChatEvent;
import com.streaming.core.chat.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 서버 여러 대 간 채팅 중계.
 *  발행: 어느 서버/서비스든 ChatEventPublisher 로 Redis "chat:{채널코드}" 에 발행
 *  수신: 모든 chat-server 가 구독하고 있다가 자기에게 붙은 사용자에게 STOMP 로 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatRelay implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelChatStateCache stateCache;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            ChatEvent event = objectMapper.readValue(json, ChatEvent.class);
            if (event.type() == MessageType.CHAT_FREEZE) {
                stateCache.invalidate(event.channelCode()); // 얼리기 즉시 반영
            }
            messagingTemplate.convertAndSend("/topic/chat/" + event.channelCode(), event);
        } catch (Exception e) {
            log.error("Redis 수신 메시지 처리 실패", e);
        }
    }
}
