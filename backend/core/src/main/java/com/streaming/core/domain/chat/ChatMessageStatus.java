package com.streaming.core.domain.chat;

public enum ChatMessageStatus {
    VISIBLE,  // 정상 노출
    HIDDEN,   // 관리자 숨김 (복구 가능, 시청자/다시보기에서 미노출)
    DELETED   // 관리자 삭제 (복구 불가)
}
