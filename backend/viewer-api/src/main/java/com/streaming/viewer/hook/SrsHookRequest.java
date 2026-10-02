package com.streaming.viewer.hook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * SRS HTTP 콜백 요청 본문 (필요한 필드만).
 * 예) {"action":"on_publish","app":"live","stream":"channel1","param":"?key=abc", ...}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SrsHookRequest(
        String action,
        String client_id,
        String ip,
        String vhost,
        String app,
        String stream,
        String param,
        String file,   // on_dvr: 녹화 파일 경로
        String cwd) {

    /** param("?key=xxx&a=b")에서 key 값 추출 */
    public String streamKey() {
        if (param == null || param.isBlank()) return null;
        String q = param.startsWith("?") ? param.substring(1) : param;
        for (String pair : q.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && pair.substring(0, idx).equals("key")) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }
}
