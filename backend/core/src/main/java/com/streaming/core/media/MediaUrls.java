package com.streaming.core.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OME 재생/송출 주소 (채널코드 = OME stream 이름)
 *  WebRTC  ws://localhost:3333/live/{code}/master         지연 1초 미만, 화질 자동 전환
 *  LL-HLS  http://localhost:3333/live/{code}/master.m3u8  WebRTC 가 안 될 때 (2~5초)
 * "master" 는 infra/ome/Server.xml 의 Playlist FileName (원본/720p/480p)
 */
@Component
public class MediaUrls {

    private static final String PLAYLIST = "master";

    @Value("${app.media.rtmp-base-url:rtmp://localhost/live}")
    private String rtmpBaseUrl;

    @Value("${app.media.webrtc-base-url:ws://localhost:3333/live}")
    private String webrtcBaseUrl;

    @Value("${app.media.llhls-base-url:http://localhost:3333/live}")
    private String llhlsBaseUrl;

    /** OBS "서버" 칸 */
    public String rtmp() {
        return rtmpBaseUrl;
    }

    public String webrtc(String channelCode) {
        return webrtcBaseUrl + "/" + channelCode + "/" + PLAYLIST;
    }

    public String llhls(String channelCode) {
        return llhlsBaseUrl + "/" + channelCode + "/" + PLAYLIST + ".m3u8";
    }
}
