package com.streaming.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streaming.core.chat.ChatEvent;
import com.streaming.core.domain.chat.ChatMessage;
import com.streaming.core.domain.chat.ChatMessageStatus;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 채팅 로그 write-behind 저장.
 * 메시지 1건마다 INSERT 하면 채팅이 몰릴 때 DB 가 병목이 되므로, 큐에 쌓아 두고
 * app.chat.write-interval-ms 마다 JDBC batch 로 한 번에 저장한다.
 * (프로세스가 비정상 종료되면 최대 한 주기 분량이 유실될 수 있음 → 운영에서는 Redis Streams/Kafka 로 교체)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageWriter {

    private static final int CHUNK = 1000;
    private static final int MAX_QUEUE = 200_000; // DB 장애 시 메모리 보호

    private static final String SQL = "insert into chat_message " +
            "(id, channel_code, type, sender, client_id, content, payload, status, created_at) " +
            "values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?) on conflict (id) do nothing";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    /** JPA(hibernate.jdbc.time_zone)와 같은 기준으로 시각을 저장해야 방송 시간대 조회가 어긋나지 않는다 */
    @Value("${spring.jpa.properties.hibernate.jdbc.time_zone:}")
    private String jdbcTimeZone;

    private final ConcurrentLinkedQueue<ChatEvent> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();

    public void enqueue(ChatEvent event) {
        if (size.get() >= MAX_QUEUE) {
            queue.poll();
            size.decrementAndGet();
        }
        queue.add(event);
        size.incrementAndGet();
    }

    @Scheduled(fixedDelayString = "${app.chat.write-interval-ms:300}")
    public synchronized void flush() {
        while (!queue.isEmpty()) {
            List<ChatEvent> chunk = new ArrayList<>(CHUNK);
            ChatEvent e;
            while (chunk.size() < CHUNK && (e = queue.poll()) != null) {
                chunk.add(e);
                size.decrementAndGet();
            }
            Calendar cal = jdbcTimeZone == null || jdbcTimeZone.isBlank()
                    ? Calendar.getInstance()
                    : Calendar.getInstance(TimeZone.getTimeZone(jdbcTimeZone));
            try {
                jdbcTemplate.batchUpdate(SQL, chunk, chunk.size(), (ps, m) -> {
                    ps.setString(1, m.id());
                    ps.setString(2, m.channelCode());
                    ps.setString(3, m.type().name());
                    ps.setString(4, m.sender());
                    ps.setString(5, m.clientId());
                    ps.setString(6, m.content());
                    ps.setString(7, toJson(m));
                    ps.setString(8, ChatMessageStatus.VISIBLE.name());
                    ps.setTimestamp(9, Timestamp.valueOf(ChatMessage.toLocal(m.sentAt())), cal);
                });
            } catch (Exception ex) {
                log.error("채팅 로그 저장 실패 ({}건) — 다음 주기에 재시도", chunk.size(), ex);
                chunk.forEach(this::enqueue);
                return;
            }
        }
    }

    private String toJson(ChatEvent m) {
        if (m.payload() == null) return null;
        try {
            return objectMapper.writeValueAsString(m.payload());
        } catch (Exception e) {
            return null;
        }
    }

    @PreDestroy
    public void shutdown() {
        flush();
    }
}
