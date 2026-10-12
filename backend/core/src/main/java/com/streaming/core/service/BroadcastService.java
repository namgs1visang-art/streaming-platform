package com.streaming.core.service;

import com.streaming.core.common.NotFoundException;
import com.streaming.core.domain.broadcast.Broadcast;
import com.streaming.core.domain.broadcast.BroadcastRepository;
import com.streaming.core.domain.broadcast.RecordingStatus;
import com.streaming.core.domain.channel.Channel;
import com.streaming.core.domain.channel.ChannelRepository;
import com.streaming.core.domain.schedule.Schedule;
import com.streaming.core.domain.schedule.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 방송 회차 + 녹화 생명주기 (OME AdmissionWebhooks 에서 호출, 녹화 자체는 OmeRecorder)
 *
 *  opening ─▶ start()           방송 회차 생성 (편성의 녹화 옵션 → RECORDING / NONE)
 *  closing ─▶ end()             종료 시각 기록 → 녹화 중이던 회차 id 반환 (녹화 종료 요청용)
 *  (주기)   ─▶ attachRecording() OME 녹화 완료(info xml 생성) 확인 → mp4 연결 → READY (다시보기 가능)
 *  (주기)   ─▶ failStuck()       종료 후 일정 시간 녹화가 완료되지 않으면 FAILED
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BroadcastService {

    private static final DateTimeFormatter TITLE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BroadcastRepository broadcastRepository;
    private final ChannelRepository channelRepository;
    private final ScheduleRepository scheduleRepository;

    public Broadcast get(Long id) {
        return broadcastRepository.findWithChannel(id)
                .orElseThrow(() -> new NotFoundException("방송 이력이 없습니다. id=" + id));
    }

    /** 채널에서 지금 진행 중인 방송 (없으면 empty) */
    public Optional<Broadcast> findLive(String channelCode) {
        return broadcastRepository.findOpenByChannelCode(channelCode).stream().findFirst();
    }

    @Transactional
    public Broadcast start(Long channelId, Long scheduleId, boolean defaultRecord) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("채널이 없습니다. id=" + channelId));
        LocalDateTime now = LocalDateTime.now();

        // 이전 방송이 on_unpublish 없이 끊긴 경우 정리
        broadcastRepository.findByChannelIdAndEndedAtIsNull(channelId).forEach(b -> b.end(now));

        Schedule schedule = scheduleId == null ? null : scheduleRepository.findById(scheduleId).orElse(null);
        boolean record = schedule != null ? schedule.isRecordEnabled() : defaultRecord;
        String title = (schedule != null ? schedule.getTitle() : channel.getName()) + " " + now.format(TITLE_TIME);

        Broadcast b = broadcastRepository.save(Broadcast.start(channel, schedule, title, now, record));
        log.info("[broadcast] 시작 id={}, channel={}, schedule={}, record={}", b.getId(), channel.getCode(), scheduleId, record);
        return b;
    }

    /** @return 이번에 종료된 방송 중 녹화 중이던 방송 id (OME 녹화 종료 요청 대상) */
    @Transactional
    public List<Long> end(String channelCode) {
        LocalDateTime now = LocalDateTime.now();
        List<Long> recording = new ArrayList<>();
        broadcastRepository.findOpenByChannelCode(channelCode).forEach(b -> {
            b.end(now);
            if (b.getRecordingStatus() == RecordingStatus.RECORDING) recording.add(b.getId());
            log.info("[broadcast] 종료 id={}, channel={}, {}초", b.getId(), channelCode, b.getDurationSec());
        });
        return recording;
    }

    /** 녹화 완료 확인 대상 (종료됐고 아직 파일이 연결되지 않은 녹화 방송) */
    public List<Broadcast> findPendingRecordings() {
        return broadcastRepository.findPendingRecordings();
    }

    /** OME 녹화가 끝난 mp4 를 방송 회차에 연결 → 다시보기 가능 */
    @Transactional
    public void attachRecording(Long broadcastId, String videoPath, Long fileSizeBytes) {
        broadcastRepository.findById(broadcastId).ifPresent(b -> {
            b.attachRecording(videoPath, fileSizeBytes);
            log.info("[broadcast] 녹화 저장 id={}, file={}, {} bytes", b.getId(), videoPath, fileSizeBytes);
        });
    }

    @Transactional
    public void failRecording(Long broadcastId, String reason) {
        broadcastRepository.findById(broadcastId).ifPresent(b -> {
            b.failRecording(reason);
            log.warn("[broadcast] 녹화 실패 id={}: {}", b.getId(), reason);
        });
    }

    /** 종료 후 graceMinutes 가 지나도 녹화 파일이 없는 방송 → FAILED */
    @Transactional
    public int failStuck(int graceMinutes) {
        List<Broadcast> stuck = broadcastRepository.findStuckRecordings(LocalDateTime.now().minusMinutes(graceMinutes));
        stuck.forEach(b -> b.failRecording("OME 녹화 파일이 완료되지 않았습니다."));
        return stuck.size();
    }

    @Transactional
    public void delete(Long id) {
        broadcastRepository.delete(get(id));
    }
}
