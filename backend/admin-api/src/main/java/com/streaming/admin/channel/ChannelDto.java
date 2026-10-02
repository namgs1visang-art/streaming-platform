package com.streaming.admin.channel;

import com.streaming.core.domain.channel.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class ChannelDto {

    public record CreateRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9-]{3,50}$", message = "영문 소문자/숫자/하이픈 3~50자") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description) {
    }

    /** OBS 설정에 바로 넣을 수 있는 값까지 포함 */
    public record Response(
            Long id,
            String code,
            String name,
            String description,
            String status,
            LocalDateTime liveStartedAt,
            String obsServer,
            String obsStreamKey,
            String playbackUrl,
            LocalDateTime createdAt) {

        public static Response of(Channel c, String rtmpBaseUrl, String hlsBaseUrl) {
            return new Response(
                    c.getId(), c.getCode(), c.getName(), c.getDescription(),
                    c.getStatus().name(), c.getLiveStartedAt(),
                    rtmpBaseUrl,
                    c.getCode() + "?key=" + c.getStreamKey(),
                    hlsBaseUrl + "/" + c.getCode() + ".m3u8",
                    c.getCreatedAt());
        }
    }
}
