package com.streaming.chat.service;

import com.streaming.core.domain.chat.Sticker;
import com.streaming.core.service.StickerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 사용 중인 스티커 목록 캐시 (10초) */
@Component
@RequiredArgsConstructor
public class StickerCache {

    private static final long TTL_MS = 10_000;

    public record StickerInfo(Long id, String name, String emoji, String imageUrl) {
    }

    private final StickerService stickerService;
    private volatile Map<Long, StickerInfo> stickers = Map.of();
    private volatile long loadedAt;

    public Optional<StickerInfo> get(Long id) {
        if (System.currentTimeMillis() - loadedAt > TTL_MS) reload();
        return Optional.ofNullable(stickers.get(id));
    }

    private synchronized void reload() {
        if (System.currentTimeMillis() - loadedAt <= TTL_MS) return;
        stickers = stickerService.findEnabled().stream()
                .map((Sticker s) -> new StickerInfo(s.getId(), s.getName(), s.getEmoji(), s.getImageUrl()))
                .collect(Collectors.toUnmodifiableMap(StickerInfo::id, Function.identity()));
        loadedAt = System.currentTimeMillis();
    }
}
