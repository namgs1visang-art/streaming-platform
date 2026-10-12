package com.streaming.viewer.hook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.net.URI;

/**
 * OME AdmissionWebhooks 요청 본문.
 * 예) {"client":{"address":"172.17.0.1",...},
 *      "request":{"direction":"incoming","protocol":"rtmp","status":"opening",
 *                 "url":"rtmp://localhost:1935/live/channel1?key=abc","time":"..."}}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OmeAdmissionRequest(Client client, Request request) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Client(String address, Integer port, String real_ip, String user_agent) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Request(String direction, String protocol, String status, String url, String new_url, String time) {
    }

    public boolean incoming() {
        return request != null && "incoming".equals(request.direction());
    }

    public boolean opening() {
        return request != null && "opening".equals(request.status());
    }

    /** 송출 연결 식별 (같은 연결의 opening ↔ closing 짝 맞추기) */
    public String session() {
        return client == null ? null : client.address() + ":" + client.port();
    }

    public String ip() {
        return client == null ? null : (client.real_ip() != null ? client.real_ip() : client.address());
    }

    /** url 경로 "/live/channel1" → app=live, stream=channel1 */
    public String app() {
        String[] p = pathParts();
        return p.length > 0 ? p[0] : null;
    }

    public String stream() {
        String[] p = pathParts();
        return p.length > 1 ? p[1] : null;
    }

    /** url 쿼리 "?key=xxx&a=b" 에서 key 값 (OBS 스트림 키: {채널코드}?key={스트림키}) */
    public String streamKey() {
        String q = uri() == null ? null : uri().getRawQuery();
        if (q == null || q.isBlank()) return null;
        for (String pair : q.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && pair.substring(0, idx).equals("key")) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }

    private String[] pathParts() {
        String path = uri() == null ? null : uri().getPath();
        if (path == null) return new String[0];
        String trimmed = path.replaceAll("^/+|/+$", "");
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/");
    }

    private URI uri() {
        if (request == null || request.url() == null) return null;
        try {
            return URI.create(request.url());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
