package com.streaming.viewer.channel;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** chat-server 가 Redis 에 기록한 실시간 시청자 수를 읽는다. 키: viewers:{채널코드} */
@Component
@RequiredArgsConstructor
public class ViewerCountReader {

    private final StringRedisTemplate redis;

    public long get(String channelCode) {
        try {
            String v = redis.opsForValue().get("viewers:" + channelCode);
            return v == null ? 0 : Math.max(0, Long.parseLong(v));
        } catch (Exception e) {
            return 0; // Redis 장애가 시청 자체를 막지 않도록
        }
    }
}
