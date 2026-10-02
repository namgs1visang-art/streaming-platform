package com.streaming.chat.message;

import com.streaming.chat.redis.RedisChatRelay;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private static final int MAX_LENGTH = 200;

    private final RedisChatRelay relay;

    public record SendRequest(String sender, String content) {
    }

    /** 시청자 채팅: STOMP SEND /app/chat/{channelCode} */
    @MessageMapping("/chat/{channelCode}")
    public void send(@DestinationVariable String channelCode, @Payload SendRequest req) {
        if (req.content() == null || req.content().isBlank()) return;
        String content = req.content().length() > MAX_LENGTH ? req.content().substring(0, MAX_LENGTH) : req.content();
        String sender = (req.sender() == null || req.sender().isBlank()) ? "익명" : req.sender();

        // TODO(4단계): 로그인 사용자 확인, 차단 사용자/도배 체크, 메시지 DB 비동기 저장
        relay.publish(ChatMessage.of(MessageType.CHAT, channelCode, sender, content, null));
    }
}
