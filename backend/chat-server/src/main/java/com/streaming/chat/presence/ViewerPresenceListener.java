package com.streaming.chat.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 시청자 수 집계: /topic/chat/{채널코드} 구독 = 입장, 연결 끊김 = 퇴장.
 * Redis 키 viewers:{채널코드} 를 viewer-api 가 읽어서 화면에 보여준다.
 * 관리자 화면(구독 헤더 role=admin)은 시청자 수에서 제외한다.
 */
@Component
@RequiredArgsConstructor
public class ViewerPresenceListener {

    private static final String DEST_PREFIX = "/topic/chat/";

    private final StringRedisTemplate redis;

    /** 이 서버에 붙은 세션 → 채널코드 */
    private final Map<String, String> sessionChannel = new ConcurrentHashMap<>();

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String dest = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        if (dest == null || sessionId == null || !dest.startsWith(DEST_PREFIX)) return;
        if ("admin".equals(accessor.getFirstNativeHeader("role"))) return;

        String channelCode = dest.substring(DEST_PREFIX.length());
        if (sessionChannel.putIfAbsent(sessionId, channelCode) == null) {
            redis.opsForValue().increment(key(channelCode));
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String channelCode = sessionChannel.remove(event.getSessionId());
        if (channelCode != null) {
            redis.opsForValue().decrement(key(channelCode));
        }
    }

    private String key(String channelCode) {
        return "viewers:" + channelCode;
    }
}
