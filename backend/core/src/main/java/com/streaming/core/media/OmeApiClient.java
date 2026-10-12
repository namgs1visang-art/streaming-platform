package com.streaming.core.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * OvenMediaEngine REST API(8081) 클라이언트 — Basic 인증(AccessToken)
 *  GET    /v1/vhosts/{vh}/apps/{app}/streams              송출 중인 스트림 목록
 *  GET    /v1/vhosts/{vh}/apps/{app}/streams/{s}          입력 트랙 (코덱, 해상도, B-프레임)
 *  GET    /v1/stats/current/vhosts/{vh}/apps/{app}/streams/{s}  수신 비트레이트, 접속 수
 *  DELETE /v1/vhosts/{vh}/apps/{app}/streams/{s}          송출 강제 종료 → AdmissionWebhooks closing
 *  POST   /v1/vhosts/{vh}/apps/{app}:startRecord / :stopRecord   녹화
 */
@Slf4j
@Component
public class OmeApiClient {

    /** 모니터링용 스트림 상태 */
    public record StreamStat(String name, int kbpsIn, String videoCodec, Integer width, Integer height,
                             Double framerate, boolean bframes, String audioCodec, int connections) {
    }

    private final RestClient client;
    private final String appPath;
    private final String statsPath;

    public OmeApiClient(@Value("${app.ome.api-url:http://localhost:8081}") String apiUrl,
                        @Value("${app.ome.access-token:ome-access-token}") String accessToken,
                        @Value("${app.ome.vhost:default}") String vhost,
                        @Value("${app.ome.app:live}") String app) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(1500);
        f.setReadTimeout(3000);
        String basic = Base64.getEncoder().encodeToString(accessToken.getBytes(StandardCharsets.UTF_8));
        this.client = RestClient.builder()
                .baseUrl(apiUrl)
                // OME API 서버는 chunked 본문을 읽지 못한다 → 버퍼링해서 Content-Length 로 전송
                .requestFactory(new BufferingClientHttpRequestFactory(f))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
        this.appPath = "/v1/vhosts/" + vhost + "/apps/" + app;
        this.statsPath = "/v1/stats/current/vhosts/" + vhost + "/apps/" + app;
    }

    /** 지금 송출 중인 스트림 이름. OME 에 연결할 수 없으면 empty */
    public Optional<Set<String>> streamNames() {
        try {
            Set<String> names = new HashSet<>();
            get(appPath + "/streams").forEach(n -> names.add(n.asText()));
            return Optional.of(names);
        } catch (Exception e) {
            log.debug("OME 스트림 목록 조회 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** stream 이름(=채널 코드) → 상태. OME 에 연결할 수 없으면 빈 Map */
    public Map<String, StreamStat> streams() {
        Map<String, StreamStat> result = new HashMap<>();
        try {
            JsonNode list = get(appPath + "/streams");
            for (JsonNode n : list) {
                String name = n.asText();
                try {
                    result.put(name, stat(name));
                } catch (Exception e) {
                    log.debug("OME 스트림 상태 조회 실패 {}: {}", name, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("OME 스트림 목록 조회 실패: {}", e.getMessage());
        }
        return result;
    }

    private StreamStat stat(String name) {
        JsonNode info = get(appPath + "/streams/" + name);
        String videoCodec = null, audioCodec = null;
        Integer width = null, height = null;
        Double framerate = null;
        boolean bframes = false;
        long bps = 0;
        for (JsonNode t : info.path("input").path("tracks")) {
            if (t.has("video") && videoCodec == null) {
                JsonNode v = t.path("video");
                videoCodec = v.path("codec").asText(null);
                width = v.has("width") ? v.path("width").asInt() : null;
                height = v.has("height") ? v.path("height").asInt() : null;
                framerate = v.has("framerate") ? v.path("framerate").asDouble() : null;
                bframes = v.path("hasBframes").asBoolean(false);
                bps += trackBitrate(v);
            } else if (t.has("audio") && audioCodec == null) {
                audioCodec = t.path("audio").path("codec").asText(null);
                bps += trackBitrate(t.path("audio"));
            }
        }
        JsonNode stats = get(statsPath + "/streams/" + name);
        // 입력 트랙의 최근 비트레이트 합 (없으면 평균 수신량). lastThroughputIn 은 순간값이라 출렁인다
        if (bps == 0) bps = stats.path("avgThroughputIn").asLong(0);
        int kbps = (int) (bps / 1000);
        int connections = stats.path("totalConnections").asInt(0);
        return new StreamStat(name, kbps, videoCodec, width, height, framerate, bframes, audioCodec, connections);
    }

    private static long trackBitrate(JsonNode track) {
        long latest = track.path("bitrateLatest").asLong(0);
        return latest > 0 ? latest : track.path("bitrate").asLong(0);
    }

    /** 송출 강제 종료 (스트림 삭제). 성공하면 true */
    public boolean kick(String streamName) {
        try {
            client.delete().uri(appPath + "/streams/{s}", streamName).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("OME 송출 종료 실패 stream={}: {}", streamName, e.getMessage());
            return false;
        }
    }

    /**
     * 녹화 시작. 스트림이 아직 없으면 OME 가 예약해 두었다가 스트림이 생기면 시작한다.
     * @param filePath/infoPath OME 컨테이너 안의 경로
     */
    public boolean startRecord(String id, String streamName, List<String> variantNames, String filePath, String infoPath) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("stream", Map.of("name", streamName, "variantNames", variantNames));
        body.put("filePath", filePath);
        body.put("infoPath", infoPath);
        try {
            client.post().uri(appPath + ":startRecord").contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("OME 녹화 시작 실패 id={}, stream={}: {}", id, streamName, e.getMessage());
            return false;
        }
    }

    /** 녹화 종료 (이미 끝났으면 무시) */
    public void stopRecord(String id) {
        try {
            client.post().uri(appPath + ":stopRecord").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("id", id)).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            // 스트림 종료와 함께 이미 끝난 녹화
        } catch (Exception e) {
            log.warn("OME 녹화 종료 실패 id={}: {}", id, e.getMessage());
        }
    }

    private JsonNode get(String uri) {
        JsonNode body = client.get().uri(uri).retrieve().body(JsonNode.class);
        return body == null ? MissingNode.getInstance() : body.path("response");
    }
}
