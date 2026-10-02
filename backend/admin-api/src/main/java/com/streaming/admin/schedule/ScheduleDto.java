package com.streaming.admin.schedule;

import com.streaming.core.domain.schedule.Schedule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class ScheduleDto {

    public record CreateRequest(
            @NotNull Long channelId,
            @NotBlank @Size(max = 200) String title,
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime endAt,
            boolean recordEnabled) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime endAt,
            boolean recordEnabled) {
    }

    public record Response(
            Long id,
            Long channelId,
            String channelName,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean recordEnabled) {

        public static Response of(Schedule s) {
            return new Response(s.getId(), s.getChannel().getId(), s.getChannel().getName(),
                    s.getTitle(), s.getStartAt(), s.getEndAt(), s.isRecordEnabled());
        }
    }
}
