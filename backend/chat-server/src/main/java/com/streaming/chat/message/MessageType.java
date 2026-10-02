package com.streaming.chat.message;

/** 채팅 채널로 흐르는 모든 이벤트 종류. 퀴즈도 같은 통로로 보낸다. */
public enum MessageType {
    CHAT,
    SYSTEM,
    QUIZ_START,
    QUIZ_END,
    QUIZ_RESULT
}
