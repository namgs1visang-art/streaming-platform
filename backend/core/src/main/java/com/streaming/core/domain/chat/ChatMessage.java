package com.streaming.core.domain.chat;

import com.streaming.core.chat.ChatEvent;
import com.streaming.core.chat.MessageType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 채팅 로그. 시청자 메시지는 chat-server 가 JDBC 배치로 일괄 저장한다 (ChatMessageWriter).
 * 다시보기는 이 테이블을 방송 시간대(Broadcast.startedAt ~ endedAt)로 잘라서 재생한다.
 */
@Getter
@Entity
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_chat_message_channel_created", columnList = "channelCode, createdAt"),
        @Index(name = "idx_chat_message_client", columnList = "clientId")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 50)
    private String channelCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type;

    @Column(nullable = false, length = 50)
    private String sender;

    @Column(length = 64)
    private String clientId;

    @Column(nullable = false, length = 500)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatMessageStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 50)
    private String moderatedBy;

    private LocalDateTime moderatedAt;

    public static ChatMessage from(ChatEvent e) {
        ChatMessage m = new ChatMessage();
        m.id = e.id();
        m.channelCode = e.channelCode();
        m.type = e.type();
        m.sender = e.sender();
        m.clientId = e.clientId();
        m.content = e.content();
        m.payload = e.payload();
        m.status = ChatMessageStatus.VISIBLE;
        m.createdAt = toLocal(e.sentAt());
        return m;
    }

    public void hide(String actor) {
        this.status = ChatMessageStatus.HIDDEN;
        this.moderatedBy = actor;
        this.moderatedAt = LocalDateTime.now();
    }

    public void unhide(String actor) {
        this.status = ChatMessageStatus.VISIBLE;
        this.moderatedBy = actor;
        this.moderatedAt = LocalDateTime.now();
    }

    public void delete(String actor) {
        this.status = ChatMessageStatus.DELETED;
        this.moderatedBy = actor;
        this.moderatedAt = LocalDateTime.now();
    }

    /** 화면/STOMP 로 내보낼 이벤트 형태 */
    public ChatEvent toEvent() {
        return new ChatEvent(id, type, channelCode, sender, clientId, content, payload,
                createdAt.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
