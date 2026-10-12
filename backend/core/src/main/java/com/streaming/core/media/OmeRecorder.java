package com.streaming.core.media;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 방송 회차 녹화 (OME REST API)
 *  방송 1회 = 녹화 작업 1개 (id: broadcast-{방송ID})
 *  OME 컨테이너: /opt/ovenmediaengine/records/live/{채널코드}/{방송ID}.mp4 (+ .xml: 녹화가 끝나면 생성)
 *  PC:          infra/ome/records/live/...  → recordings(nginx) http://localhost:8080/dvr/live/...
 * 녹화 OFF 방송은 아예 녹화를 시작하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OmeRecorder {

    /** Server.xml OutputProfile 의 원본 영상 + AAC 음성 (mp4 는 Opus 미지원, 화질별 변환본은 제외) */
    private static final List<String> VARIANTS = List.of("bypass_video", "aac_audio");
    private static final String CONTAINER_ROOT = "/opt/ovenmediaengine/records/";
    /** recordings(nginx) 에서의 경로 = Broadcast.videoPath */
    private static final String HTTP_ROOT = "dvr/";

    private final OmeApiClient ome;

    @Value("${app.ome.app:live}")
    private String app;

    public static String recordId(Long broadcastId) {
        return "broadcast-" + broadcastId;
    }

    /** "dvr/live/channel1/12.mp4" */
    public String videoPath(Long broadcastId, String channelCode) {
        return HTTP_ROOT + relative(broadcastId, channelCode) + ".mp4";
    }

    /** 녹화 완료 표시 파일 "dvr/live/channel1/12.xml" */
    public String infoPath(Long broadcastId, String channelCode) {
        return HTTP_ROOT + relative(broadcastId, channelCode) + ".xml";
    }

    /** 비동기 녹화 시작 (AdmissionWebhooks 응답을 늦추지 않도록). 결과: 성공 여부 */
    public CompletableFuture<Boolean> start(Long broadcastId, String channelCode) {
        String base = CONTAINER_ROOT + relative(broadcastId, channelCode);
        return CompletableFuture.supplyAsync(() ->
                ome.startRecord(recordId(broadcastId), channelCode, VARIANTS, base + ".mp4", base + ".xml"));
    }

    public void stop(Long broadcastId) {
        CompletableFuture.runAsync(() -> ome.stopRecord(recordId(broadcastId)));
    }

    private String relative(Long broadcastId, String channelCode) {
        return app + "/" + channelCode + "/" + broadcastId;
    }
}
