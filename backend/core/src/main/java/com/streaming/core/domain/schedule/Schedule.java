package com.streaming.core.domain.schedule;

import com.streaming.core.domain.BaseTimeEntity;
import com.streaming.core.domain.channel.Channel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 방송 스케줄 (편성표의 한 칸). 채널마다 여러 개, 채널 간에는 같은 시간대 중복 가능.
 */
@Getter
@Entity
@Table(name = "schedule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    /** 녹화해서 VOD로 남길지 */
    @Column(nullable = false)
    private boolean recordEnabled;

    public static Schedule create(Channel channel, String title, LocalDateTime startAt, LocalDateTime endAt, boolean recordEnabled) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("종료 시간은 시작 시간 이후여야 합니다.");
        }
        Schedule s = new Schedule();
        s.channel = channel;
        s.title = title;
        s.startAt = startAt;
        s.endAt = endAt;
        s.recordEnabled = recordEnabled;
        return s;
    }

    public void update(String title, LocalDateTime startAt, LocalDateTime endAt, boolean recordEnabled) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("종료 시간은 시작 시간 이후여야 합니다.");
        }
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.recordEnabled = recordEnabled;
    }
}
