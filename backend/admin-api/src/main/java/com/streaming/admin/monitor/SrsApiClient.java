package com.streaming.admin.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * SRS HTTP API(1985) 클라이언트
 *  GET    /api/v1/streams        스트림 목록 (비트레이트, 해상도, 코덱, 접속 수, 송출 클라이언트 ID)
 *  DELETE /api/v1/clients/{cid}  클라이언트(OBS 송출) 강제 종료 → SRS 가 on_unpublish 훅 호출
 */
@Slf4j
@Component
public class SrsApiClient {

    public record StreamStat(String name, String publisherId, boolean publishing, int clients,
                             int kbpsIn, String videoCodec, Integer width, Integer height,
                             String audioCodec) {
    }

    private final RestClient client;

    public SrsApiClient(@Value("${app.srs.api-url:http://localhost:1985}") String apiUrl) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(1500);
        f.setReadTimeout(3000);
        this.client = RestClient.builder().baseUrl(apiUrl).requestFactory(f).build();
    }

    /** stream 이름(=채널 코드) → 상태. SRS 에 연결할 수 없으면 빈 Map */
    public Map<String, StreamStat> streams() {
        Map<String, StreamStat> result = new HashMap<>();
        try {
            JsonNode root = client.get().uri("/api/v1/streams?count=200").retrieve().body(JsonNode.class);
            if (root == null) return result;
            for (JsonNode s : root.path("streams")) {
                JsonNode video = s.path("video");
                JsonNode publish = s.path("publish");
                StreamStat stat = new StreamStat(
                        s.path("name").asText(),
                        publish.path("cid").asText(null),
                        publish.path("active").asBoolean(false),
                        s.path("clients").asInt(0),
                        s.path("kbps").path("recv_30s").asInt(0),
                        video.isMissingNode() || video.isNull() ? null : video.path("codec").asText(null),
                        video.has("width") ? video.path("width").asInt() : null,
                        video.has("height") ? video.path("height").asInt() : null,
                        s.path("audio").isMissingNode() || s.path("audio").isNull() ? null : s.path("audio").path("codec").asText(null));
                result.put(stat.name(), stat);
            }
        } catch (Exception e) {
            log.debug("SRS 스트림 조회 실패: {}", e.getMessage());
        }
        return result;
    }

    /** 송출 강제 종료. 성공하면 true */
    public boolean kick(String streamName) {
        StreamStat stat = streams().get(streamName);
        if (stat == null || stat.publisherId() == null) return false;
        try {
            client.delete().uri("/api/v1/clients/{cid}", stat.publisherId()).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("SRS 송출 종료 실패 stream={}: {}", streamName, e.getMessage());
            return false;
        }
    }
}
