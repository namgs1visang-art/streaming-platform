package com.streaming.core.chat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 채팅 채널로 흐르는 이벤트 1건 (Redis/STOMP 공통 JSON) */
public record ChatEvent(
        String id,
        MessageType type,
        String channelCode,
        String sender,
        String clientId,               // 익명 시청자 식별값 (로그인 연동 전까지 브라우저별 UUID)
        String content,
        Map<String, Object> payload,   // 스티커/퀴즈/관리자 조치 부가 데이터
        Instant sentAt) {

    public static ChatEvent of(MessageType type, String channelCode, String sender, String clientId,
                               String content, Map<String, Object> payload) {
        return new ChatEvent(UUID.randomUUID().toString(), type, channelCode, sender, clientId, content, payload, Instant.now());
    }

    /** 관리자/시스템 이벤트 */
    public static ChatEvent system(MessageType type, String channelCode, String content, Map<String, Object> payload) {
        return of(type, channelCode, "SYSTEM", null, content, payload);
    }
}
