package com.streaming.viewer.channel;

import com.streaming.core.domain.channel.Channel;
import com.streaming.core.domain.schedule.Schedule;
import com.streaming.core.service.ChannelService;
import com.streaming.core.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 시청자용 API (스트림키 등 민감정보는 절대 내려주지 않음) */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChannelViewerController {

    private final ChannelService channelService;
    private final ScheduleService scheduleService;
    private final ViewerCountReader viewerCountReader;

    @Value("${app.media.hls-base-url}")
    private String hlsBaseUrl;

    public record ChannelView(Long id, String code, String name, String description,
                              String status, LocalDateTime liveStartedAt,
                              String playbackUrl, long viewerCount) {
    }

    public record ScheduleView(Long id, String channelCode, String channelName, String title,
                               LocalDateTime startAt, LocalDateTime endAt) {
        static ScheduleView of(Schedule s) {
            return new ScheduleView(s.getId(), s.getChannel().getCode(), s.getChannel().getName(),
                    s.getTitle(), s.getStartAt(), s.getEndAt());
        }
    }

    /** 전체 채널 (LIVE 먼저) */
    @GetMapping("/channels")
    public List<ChannelView> channels(@RequestParam(defaultValue = "false") boolean liveOnly) {
        List<Channel> list = liveOnly ? channelService.findLive() : channelService.findAll();
        return list.stream()
                .sorted((a, b) -> b.getStatus().compareTo(a.getStatus()))
                .map(this::toView).toList();
    }

    @GetMapping("/channels/{code}")
    public ChannelView channel(@PathVariable String code) {
        return toView(channelService.getByCode(code));
    }

    /** 편성표: /api/schedules?date=2026-10-02 */
    @GetMapping("/schedules")
    public List<ScheduleView> schedules(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        return scheduleService.findInRange(d.atStartOfDay(), d.plusDays(1).atStartOfDay())
                .stream().map(ScheduleView::of).toList();
    }

    private ChannelView toView(Channel c) {
        return new ChannelView(c.getId(), c.getCode(), c.getName(), c.getDescription(),
                c.getStatus().name(), c.getLiveStartedAt(),
                hlsBaseUrl + "/" + c.getCode() + ".m3u8",
                viewerCountReader.get(c.getCode()));
    }
}
