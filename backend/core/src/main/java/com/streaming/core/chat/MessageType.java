package com.streaming.core.chat;

/**
 * 채팅 채널(Redis "chat:{채널코드}" → STOMP /topic/chat/{채널코드})로 흐르는 모든 이벤트 종류.
 * 채팅·관리자 조치·퀴즈가 모두 같은 통로를 쓴다.
 */
public enum MessageType {
    // 저장되는 메시지
    CHAT,           // 시청자 텍스트
    STICKER,        // 시청자 스티커 (payload: stickerId, name, emoji, imageUrl)
    ADMIN,          // 관리자 메시지
    SYSTEM,         // 공지 (저장 안 함)

    // 관리자 조치
    CHAT_FREEZE,    // payload: chatFrozen, stickerFrozen
    MESSAGE_DELETE, // payload: ids
    MESSAGE_HIDE,   // payload: ids
    MESSAGE_UNHIDE, // payload: message (복구된 메시지 전체)

    // 퀴즈/투표
    QUIZ_START,     // payload: pushId, title, question, options, mode, timeLimitSec, closesAt
    QUIZ_STATS,     // payload: pushId, counts, total (1초 단위 실시간 집계)
    QUIZ_RESULT,    // payload: pushId, counts, total, answerIndex
    QUIZ_END;       // 퀴즈 카드 닫기

    /** 채팅 로그(chat_message)로 남기는 타입 */
    public boolean isPersistent() {
        return this == CHAT || this == STICKER || this == ADMIN;
    }
}
