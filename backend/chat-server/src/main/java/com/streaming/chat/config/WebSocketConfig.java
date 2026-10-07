package com.streaming.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 클라이언트 → /app/chat/{channelCode}       (메시지/스티커 보내기)
 * 클라이언트 ← /topic/chat/{channelCode}     (채널 전체 이벤트)
 * 클라이언트 ← /user/queue/errors            (내 전송이 거절된 사유: 얼리기, 도배 등)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        // 서버 내부 브로커는 "같은 서버에 붙은 사용자"에게만 전달.
        // 서버 간 전달은 Redis Pub/Sub(RedisChatRelay)이 담당.
        registry.enableSimpleBroker("/topic", "/queue");
    }
}
