package com.streaming.core.domain.broadcast;

public enum RecordingStatus {
    NONE,       // 녹화 안 함 (편성의 녹화 옵션 OFF)
    RECORDING,  // 녹화 중 / OME 녹화 파일 완료 대기
    READY,      // 다시보기 가능
    FAILED      // 녹화 파일을 받지 못함
}
