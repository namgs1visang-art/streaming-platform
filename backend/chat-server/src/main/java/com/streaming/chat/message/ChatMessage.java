package com.streaming.chat.message;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ChatMessage(
        String id,
        MessageType type,
        String channelCode,
        String sender,
        String content,
        Map<String, Object> payload,   // 퀴즈 문제/보기 등 부가 데이터
        Instant sentAt) {

    public static ChatMessage of(MessageType type, String channelCode, String sender,
                                 String content, Map<String, Object> payload) {
        return new ChatMessage(UUID.randomUUID().toString(), type, channelCode, sender, content, payload, Instant.now());
    }
}
