package com.streaming.core.service;

import com.streaming.core.common.NotFoundException;
import com.streaming.core.domain.channel.Channel;
import com.streaming.core.domain.schedule.Schedule;
import com.streaming.core.domain.schedule.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ChannelService channelService;

    public List<Schedule> findInRange(LocalDateTime from, LocalDateTime to) {
        return scheduleRepository.findInRange(from, to);
    }

    public Schedule get(Long id) {
        return scheduleRepository.findWithChannel(id)
                .orElseThrow(() -> new NotFoundException("스케줄이 없습니다. id=" + id));
    }

    /** 지금(시작 earlyMinutes 분 전 포함) 송출 가능한 스케줄이 있는지 */
    public boolean isOnAir(Long channelId, int earlyMinutes) {
        LocalDateTime now = LocalDateTime.now();
        return !scheduleRepository.findOnAir(channelId, now, now.plusMinutes(earlyMinutes)).isEmpty();
    }

    @Transactional
    public Schedule create(Long channelId, String title, LocalDateTime startAt, LocalDateTime endAt, boolean recordEnabled) {
        Channel channel = channelService.get(channelId);
        checkOverlap(channelId, startAt, endAt, -1L);
        return scheduleRepository.save(Schedule.create(channel, title, startAt, endAt, recordEnabled));
    }

    @Transactional
    public Schedule update(Long id, String title, LocalDateTime startAt, LocalDateTime endAt, boolean recordEnabled) {
        Schedule schedule = get(id);
        checkOverlap(schedule.getChannel().getId(), startAt, endAt, id);
        schedule.update(title, startAt, endAt, recordEnabled);
        return schedule;
    }

    @Transactional
    public void delete(Long id) {
        scheduleRepository.delete(get(id));
    }

    private void checkOverlap(Long channelId, LocalDateTime startAt, LocalDateTime endAt, Long excludeId) {
        if (scheduleRepository.existsOverlap(channelId, startAt, endAt, excludeId)) {
            throw new IllegalArgumentException("같은 채널에 겹치는 스케줄이 있습니다.");
        }
    }
}
