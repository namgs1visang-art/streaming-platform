package com.streaming.chat.message;

import com.streaming.core.chat.ChatEvent;
import com.streaming.core.chat.ChatEventPublisher;
import com.streaming.core.chat.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 서버 → 채널 전체로 이벤트 방송 (공지 등 테스트용).
 * 퀴즈 출제/채팅 관리는 admin-api 가 같은 Redis 채널로 직접 발행한다.
 * TODO: 내부망 전용 또는 관리자 토큰 인증 추가
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class BroadcastEventController {

    private final ChatEventPublisher publisher;

    public record EventRequest(MessageType type, String content, Map<String, Object> payload) {
    }

    @PostMapping("/{channelCode}/events")
    public ChatEvent broadcast(@PathVariable String channelCode, @RequestBody EventRequest req) {
        if (req.type() == null || req.type().isPersistent()) {
            throw new IllegalArgumentException("type 은 SYSTEM / QUIZ_* 등 시스템 이벤트여야 합니다.");
        }
        ChatEvent event = ChatEvent.system(req.type(), channelCode, req.content(), req.payload());
        publisher.publish(event);
        return event;
    }
}
