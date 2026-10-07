package com.streaming.core.domain.channel;

import com.streaming.core.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 방송 채널. 채널 1개 = OBS 송출 경로 1개.
 * OBS 스트림 키 형식: {code}?key={streamKey}
 */
@Getter
@Entity
@Table(name = "channel")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Channel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SRS stream 이름으로 사용 (영문/숫자/하이픈) */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 64)
    private String streamKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChannelStatus status;

    private LocalDateTime liveStartedAt;

    /** 채팅 얼리기: true 면 시청자 채팅/스티커 전송 불가 (읽기만 가능) */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean chatFrozen;

    /** 스티커 얼리기: true 면 스티커 전송만 불가 (텍스트 채팅은 가능) */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean stickerFrozen;

    public static Channel create(String code, String name, String description) {
        Channel channel = new Channel();
        channel.code = code;
        channel.name = name;
        channel.description = description;
        channel.streamKey = generateKey();
        channel.status = ChannelStatus.OFFLINE;
        return channel;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void regenerateStreamKey() {
        this.streamKey = generateKey();
    }

    public boolean matchesKey(String key) {
        return key != null && this.streamKey.equals(key);
    }

    public void goLive() {
        this.status = ChannelStatus.LIVE;
        this.liveStartedAt = LocalDateTime.now();
    }

    public void goOffline() {
        this.status = ChannelStatus.OFFLINE;
        this.liveStartedAt = null;
    }

    public void freezeChat(boolean frozen) {
        this.chatFrozen = frozen;
    }

    public void freezeSticker(boolean frozen) {
        this.stickerFrozen = frozen;
    }

    private static String generateKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
