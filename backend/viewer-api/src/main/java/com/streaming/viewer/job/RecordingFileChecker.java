package com.streaming.viewer.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * 녹화 파일 서버(recordings, nginx)에서 OME 녹화 완료 여부 확인.
 * OME 는 녹화가 끝나야 info xml 을 만든다 → xml 이 있으면 mp4 완료, 크기는 HEAD Content-Length.
 */
@Slf4j
@Component
public class RecordingFileChecker {

    private final RestClient client;

    public RecordingFileChecker(@Value("${app.media.http-base-url}") String httpBaseUrl) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(1500);
        f.setReadTimeout(3000);
        this.client = RestClient.builder().baseUrl(httpBaseUrl).requestFactory(f).build();
    }

    /** 완료됐으면 mp4 크기(bytes, 모르면 -1), 아직이면 empty */
    public Optional<Long> completedSize(String infoPath, String videoPath) {
        if (!exists(infoPath)) return Optional.empty();
        try {
            ResponseEntity<Void> res = client.head().uri("/" + videoPath).retrieve().toBodilessEntity();
            return Optional.of(res.getHeaders().getContentLength());
        } catch (Exception e) {
            log.warn("녹화 info 는 있는데 mp4 를 찾을 수 없음: {}", videoPath);
            return Optional.empty();
        }
    }

    private boolean exists(String path) {
        try {
            client.head().uri("/" + path).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
