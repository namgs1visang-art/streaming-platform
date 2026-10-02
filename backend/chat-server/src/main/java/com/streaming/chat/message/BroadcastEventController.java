package com.streaming.chat.message;

import com.streaming.chat.redis.RedisChatRelay;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 관리자/서버 → 채널 전체로 이벤트 방송 (공지, 퀴즈 출제/종료/결과).
 * TODO: 내부망 전용 또는 관리자 토큰 인증 추가
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class BroadcastEventController {

    private final RedisChatRelay relay;

    public record EventRequest(MessageType type, String content, Map<String, Object> payload) {
    }

    @PostMapping("/{channelCode}/events")
    public ChatMessage broadcast(@PathVariable String channelCode, @RequestBody EventRequest req) {
        if (req.type() == null || req.type() == MessageType.CHAT) {
            throw new IllegalArgumentException("type 은 SYSTEM / QUIZ_* 중 하나여야 합니다.");
        }
        ChatMessage msg = ChatMessage.of(req.type(), channelCode, "SYSTEM", req.content(), req.payload());
        relay.publish(msg);
        return msg;
    }
}
