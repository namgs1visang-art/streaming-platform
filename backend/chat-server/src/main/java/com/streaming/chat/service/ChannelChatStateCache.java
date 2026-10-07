package com.streaming.chat.service;

import com.streaming.core.domain.channel.ChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 채널 얼리기 상태 캐시 (메시지마다 DB 조회하지 않도록 1초 TTL).
 * 관리자가 얼리기를 바꾸면 CHAT_FREEZE 이벤트를 받는 즉시 무효화된다.
 */
@Component
@RequiredArgsConstructor
public class ChannelChatStateCache {

    private static final long TTL_MS = 1000;

    public record State(boolean chatFrozen, boolean stickerFrozen) {
    }

    private record Entry(long at, Optional<State> state) {
    }

    private final ChannelRepository channelRepository;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    /** 채널이 없으면 empty */
    public Optional<State> get(String channelCode) {
        Entry e = cache.get(channelCode);
        if (e != null && System.currentTimeMillis() - e.at() < TTL_MS) return e.state();
        Optional<State> state = channelRepository.findByCode(channelCode)
                .map(c -> new State(c.isChatFrozen(), c.isStickerFrozen()));
        if (cache.size() > 5000) cache.clear();
        cache.put(channelCode, new Entry(System.currentTimeMillis(), state));
        return state;
    }

    public void invalidate(String channelCode) {
        cache.remove(channelCode);
    }
}
