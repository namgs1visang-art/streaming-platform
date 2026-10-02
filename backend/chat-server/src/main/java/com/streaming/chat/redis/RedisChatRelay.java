package com.streaming.chat.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streaming.chat.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 서버 여러 대 간 채팅 중계.
 *  publish(): 어느 서버로 들어온 메시지든 Redis "chat:{채널코드}" 로 발행
 *  onMessage(): 모든 서버가 구독하고 있다가 자기에게 붙은 사용자에게 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatRelay implements MessageListener {

    public static final String TOPIC_PREFIX = "chat:";

    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public void publish(ChatMessage message) {
        try {
            redis.convertAndSend(TOPIC_PREFIX + message.channelCode(), objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            log.error("Redis 발행 실패", e);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            ChatMessage chat = objectMapper.readValue(json, ChatMessage.class);
            messagingTemplate.convertAndSend("/topic/chat/" + chat.channelCode(), chat);
        } catch (Exception e) {
            log.error("Redis 수신 메시지 처리 실패", e);
        }
    }
}
