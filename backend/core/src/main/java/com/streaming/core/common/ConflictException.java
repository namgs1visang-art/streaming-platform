package com.streaming.core.common;

/** 현재 상태와 충돌하는 요청 (HTTP 409) — 예: 이미 응답한 퀴즈, 진행 중인 퀴즈가 있음 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
