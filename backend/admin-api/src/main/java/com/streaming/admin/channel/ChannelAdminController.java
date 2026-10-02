package com.streaming.admin.channel;

import com.streaming.core.service.ChannelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 관리자 > LIVE > 채널 관리 */
@RestController
@RequestMapping("/api/admin/channels")
@RequiredArgsConstructor
public class ChannelAdminController {

    private final ChannelService channelService;

    @Value("${app.media.rtmp-base-url}")
    private String rtmpBaseUrl;

    @Value("${app.media.hls-base-url}")
    private String hlsBaseUrl;

    @GetMapping
    public List<ChannelDto.Response> list() {
        return channelService.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ChannelDto.Response get(@PathVariable Long id) {
        return toResponse(channelService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelDto.Response create(@Valid @RequestBody ChannelDto.CreateRequest req) {
        return toResponse(channelService.create(req.code(), req.name(), req.description()));
    }

    @PutMapping("/{id}")
    public ChannelDto.Response update(@PathVariable Long id, @Valid @RequestBody ChannelDto.UpdateRequest req) {
        return toResponse(channelService.update(id, req.name(), req.description()));
    }

    /** 스트림키 유출 시 재발급 */
    @PostMapping("/{id}/stream-key")
    public ChannelDto.Response regenerateKey(@PathVariable Long id) {
        return toResponse(channelService.regenerateKey(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        channelService.delete(id);
    }

    private ChannelDto.Response toResponse(com.streaming.core.domain.channel.Channel c) {
        return ChannelDto.Response.of(c, rtmpBaseUrl, hlsBaseUrl);
    }
}
