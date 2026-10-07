package com.streaming.core.domain.broadcast;

import com.streaming.core.domain.channel.Channel;
import com.streaming.core.domain.schedule.Schedule;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 방송 1회 = OBS 송출 시작(on_publish) ~ 종료(on_unpublish).
 * SRS DVR(dvr_plan session)이 송출 1회를 mp4 1개로 녹화하므로 녹화 단위와 같다.
 * 다시보기에서 영상 0초 = startedAt, 채팅 오프셋 = 채팅 시각 - startedAt.
 */
@Getter
@Entity
@Table(name = "broadcast", indexes = {
        @Index(name = "idx_broadcast_channel_started", columnList = "channel_id, startedAt")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Broadcast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    /** 송출 시작 시점에 걸려 있던 편성 (없을 수 있음) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Schedule schedule;

    @Column(nullable = false, length = 250)
    private String title;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordingStatus recordingStatus;

    /** SRS http_server 기준 상대 경로 (예: dvr/live/channel1/1759800000000.mp4) */
    @Column(length = 500)
    private String videoPath;

    private Long fileSizeBytes;

    private Integer durationSec;

    @Column(length = 500)
    private String recordingError;

    public static Broadcast start(Channel channel, Schedule schedule, String title, LocalDateTime now, boolean record) {
        Broadcast b = new Broadcast();
        b.channel = channel;
        b.schedule = schedule;
        b.title = title;
        b.startedAt = now;
        b.recordingStatus = record ? RecordingStatus.RECORDING : RecordingStatus.NONE;
        return b;
    }

    public boolean isLive() {
        return endedAt == null;
    }

    public void end(LocalDateTime now) {
        if (endedAt != null) return;
        this.endedAt = now;
        this.durationSec = (int) Math.max(1, Duration.between(startedAt, now).toSeconds());
    }

    public void attachRecording(String videoPath, Long fileSizeBytes) {
        this.videoPath = videoPath;
        this.fileSizeBytes = fileSizeBytes;
        this.recordingStatus = RecordingStatus.READY;
        this.recordingError = null;
    }

    public void failRecording(String reason) {
        this.recordingStatus = RecordingStatus.FAILED;
        this.recordingError = reason;
    }

    /** 다시보기 구간 끝 (방송 중이면 현재) */
    public LocalDateTime windowEnd() {
        return endedAt != null ? endedAt : LocalDateTime.now();
    }
}
