package com.streaming.core.service;

import com.streaming.core.common.NotFoundException;
import com.streaming.core.domain.broadcast.Broadcast;
import com.streaming.core.domain.broadcast.BroadcastRepository;
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
import java.util.List;
import java.util.Optional;

/**
 * 방송 회차 + 녹화 생명주기 (SRS 훅에서 호출)
 *
 *  on_publish   ─▶ start()           방송 회차 생성 (편성의 녹화 옵션 → RECORDING / NONE)
 *  on_unpublish ─▶ end()             종료 시각 기록
 *  on_dvr       ─▶ attachRecording() SRS 가 만든 mp4 를 회차에 연결 → READY (다시보기 가능)
 *  (주기)        ─▶ failStuck()       종료 후 일정 시간 on_dvr 가 안 오면 FAILED
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

    @Transactional
    public void end(String channelCode) {
        LocalDateTime now = LocalDateTime.now();
        broadcastRepository.findOpenByChannelCode(channelCode).forEach(b -> {
            b.end(now);
            log.info("[broadcast] 종료 id={}, channel={}, {}초", b.getId(), channelCode, b.getDurationSec());
        });
    }

    /**
     * on_dvr 로 받은 녹화 파일을 방송 회차에 연결.
     * @return 연결됐으면 true. false 면 녹화 대상이 아닌 송출(녹화 OFF)의 파일
     */
    @Transactional
    public boolean attachRecording(String channelCode, String videoPath, Long fileSizeBytes) {
        List<Broadcast> waiting = broadcastRepository.findWaitingRecording(channelCode);
        if (waiting.isEmpty()) {
            log.info("[broadcast] 녹화 대상 방송 없음 (녹화 OFF) channel={}, file={}", channelCode, videoPath);
            return false;
        }
        Broadcast b = waiting.get(0);
        b.attachRecording(videoPath, fileSizeBytes);
        log.info("[broadcast] 녹화 저장 id={}, file={}", b.getId(), videoPath);
        return true;
    }

    /** 종료 후 graceMinutes 가 지나도 녹화 파일이 없는 방송 → FAILED */
    @Transactional
    public int failStuck(int graceMinutes) {
        List<Broadcast> stuck = broadcastRepository.findStuckRecordings(LocalDateTime.now().minusMinutes(graceMinutes));
        stuck.forEach(b -> b.failRecording("SRS 녹화 파일(on_dvr)을 받지 못했습니다."));
        return stuck.size();
    }

    @Transactional
    public void delete(Long id) {
        broadcastRepository.delete(get(id));
    }
}
