package com.streaming.core.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Redis "chat:{채널코드}" 로 이벤트 발행.
 * chat-server(여러 대)가 모두 구독하고 있다가 자기에게 붙은 시청자에게 STOMP 로 전달한다.
 * 트랜잭션 안에서 호출되면 커밋된 뒤에 발행한다 (롤백된 조치가 시청자에게 나가거나, DB 반영 전에 시청자가 응답하는 것 방지).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    public static final String TOPIC_PREFIX = "chat:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public void publish(ChatEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(ChatEvent event) {
        try {
            redis.convertAndSend(TOPIC_PREFIX + event.channelCode(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("채팅 이벤트 발행 실패 type={}, channel={}", event.type(), event.channelCode(), e);
        }
    }
}
