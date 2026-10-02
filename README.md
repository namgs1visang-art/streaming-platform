# Streaming Platform

OBS 송출 → SRS(미디어 서버) → HLS 시청 + 실시간 채팅/퀴즈 + 녹화(VOD)

```
[OBS] ─RTMP─▶ [SRS] ─HLS─▶ [viewer-web]
                │ on_publish / on_unpublish / on_dvr
                ▼
          [viewer-api] ── PostgreSQL ── [admin-api] ◀── [admin-web]
                │
             Redis ◀── [chat-server] ◀─WebSocket─▶ [viewer-web 채팅]
```

## 구성

| 경로 | 역할 | 포트 |
|---|---|---|
| `infra/` | PostgreSQL, Redis, MinIO, SRS (docker-compose) | 5432, 6379, 9000/9001, 1935/1985/8080 |
| `backend/core` | 공통 엔티티·Repository·서비스 (Channel, Schedule) | - |
| `backend/admin-api` | 관리자 API (채널/스케줄 관리) | 8081 |
| `backend/viewer-api` | 시청자 API + SRS 훅(스트림키 인증, LIVE 상태) | 8082 |
| `backend/chat-server` | WebSocket(STOMP) 채팅 + Redis Pub/Sub + 시청자 수 | 8083 |
| `frontend/admin-web` | 관리자 화면 (React) | 5173 |
| `frontend/viewer-web` | 시청자 화면 (React, hls.js) | 5174 |

## 실행 순서

### 1. 인프라 (Docker Desktop 필요)
```bash
cd infra
docker compose up -d
```

### 2. 백엔드 (JDK 17 이상)
IntelliJ에서 `backend` 폴더를 Gradle 프로젝트로 열고 각 Application 실행, 또는:
```bash
cd backend
./gradlew :admin-api:bootRun
./gradlew :viewer-api:bootRun
./gradlew :chat-server:bootRun
```
> 테이블은 `ddl-auto: update` 로 자동 생성됩니다 (운영 전 Flyway 전환 예정).

### 3. 프론트
```bash
cd frontend/admin-web  && npm install && npm run dev   # http://localhost:5173
cd frontend/viewer-web && npm install && npm run dev   # http://localhost:5174
```

### 4. OBS 송출 테스트
1. 관리자(5173) → 채널 관리 → 채널 생성 (예: 코드 `channel1`)
2. OBS → 설정 → 방송
   - 서비스: **사용자 지정**
   - 서버: `rtmp://localhost/live`
   - 스트림 키: 표의 `channel1?key=...` 값 (클릭하면 복사)
3. 방송 시작 → 관리자 화면 상태가 `LIVE` 로 변경
4. 시청자(5174) → 채널 클릭 → 재생 + 채팅

> 키가 틀리면 SRS가 송출을 끊고 viewer-api 로그에 `송출 거부` 가 찍힙니다.
> 편성 시간에만 송출을 허용하려면 viewer-api `app.publish.require-schedule: true`.

### 5. 퀴즈/공지 이벤트 테스트
```bash
curl -X POST http://localhost:8083/api/chat/channel1/events \
  -H "Content-Type: application/json" \
  -d '{"type":"QUIZ_START","content":"1+1은?","payload":{"options":["1","2","3"]}}'
```
시청 화면 채팅 위에 퀴즈 카드가 표시됩니다. (`QUIZ_END` 로 닫힘)

## 단계별 로드맵

- [x] 1. OBS → SRS → HLS 재생
- [x] 2. 채널 / 스트림키 인증 (on_publish)
- [x] 3. 스케줄 + 편성표 + 시청 페이지 (기본)
- [x] 4. 실시간 채팅 기본 (Redis Pub/Sub, 시청자 수)
- [ ] 4-1. 채팅 고도화: 로그인 연동, 차단/도배 방지, 메시지 DB 저장, 관리자 메시지/사용자 관리
- [ ] 5. 녹화 → VOD: on_dvr → MinIO 업로드 → 콘텐츠 등록, 카테고리
- [ ] 6. 퀴즈: 문제은행, 편성 퀴즈, 답안 제출, Redis 실시간 집계
- [ ] 7. 모니터링: SRS HTTP API(1985) 기반 대시보드
- [ ] 8. 트랜스코딩 프로파일(FFmpeg), 패키징, CDN, 관리자 계정/권한(Spring Security)

## 참고
- 현재 DVR은 모든 송출을 녹화합니다. 스케줄의 `recordEnabled` 반영은 5단계에서 처리합니다.
- SRS 관리 콘솔: http://localhost:8080/console/ , API: http://localhost:1985/api/v1/streams/
- MinIO 콘솔: http://localhost:9001 (minioadmin / minioadmin)
