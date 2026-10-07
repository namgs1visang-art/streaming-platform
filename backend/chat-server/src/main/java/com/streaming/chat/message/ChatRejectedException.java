package com.streaming.chat.message;

/** 시청자 전송 거절 (얼리기, 도배 등) — 보낸 사람에게만 /user/queue/errors 로 알린다 */
public class ChatRejectedException extends RuntimeException {

    private final String code;

    public ChatRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
