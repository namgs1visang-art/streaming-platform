# Streaming Platform

OBS 송출 → OvenMediaEngine(미디어 서버) → WebRTC / LL-HLS 시청(화질 자동 전환) + 실시간 채팅/퀴즈 + 녹화(VOD)

```
[OBS] ─RTMP─▶ [OME] ─WebRTC(1초 미만) / LL-HLS(2~5초), 원본·720p·480p─▶ [viewer-web (OvenPlayer)]
                │ AdmissionWebhooks(송출 시작/종료)      ▲ REST API :8081 (상태·강제 종료·녹화)
                ▼                                         │
          [viewer-api] ── PostgreSQL ── [admin-api] ◀── [admin-web]
                │  녹화 mp4: infra/ome/records ─▶ [recordings(nginx) :8080] ─▶ 다시보기
                │
             Redis ◀── [chat-server] ◀─WebSocket─▶ [viewer-web 채팅]
```

## 구성

| 경로 | 역할 | 포트 |
|---|---|---|
| `infra/` | PostgreSQL, Redis, OvenMediaEngine, 녹화 파일 서버(nginx), MinIO(선택) (docker-compose) | 5432, 6379, 1935/3333/3478/8081 + 10000-10004/udp, 8080, 9000/9001 |
| `backend/core` | 공통 엔티티·Repository·서비스 (채널, 스케줄, 방송/녹화, 채팅 로그·스티커, 퀴즈) + 채팅 이벤트 발행 | - |
| `backend/admin-api` | 관리자 API (채널/스케줄, 채팅·스티커, 퀴즈·편성 퀴즈, 방송 녹화) | 8091 |
| `backend/viewer-api` | 시청자 API(채널, 편성표, 채팅 초기값, 스티커, 퀴즈 응답, 다시보기) + OME AdmissionWebhooks(스트림키 인증, 방송 회차) + OME 녹화 시작/완료 확인 | 8092 |
| `backend/chat-server` | WebSocket(STOMP) 채팅·스티커 + 얼리기/도배 검사 + 채팅 로그 일괄 저장 + Redis Pub/Sub + 시청자 수 | 8093 |
| `frontend/admin-web` | 관리자 화면 (React) | 5173 |
| `frontend/viewer-web` | 시청자 화면 (React, OvenPlayer) | 5174 |

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
cd frontend/admin-web  && npm install && npm run dev   # http://localhost:5173 (@stomp/stompjs 추가됨 → npm install 필요)
cd frontend/viewer-web && npm install && npm run dev   # http://localhost:5174
```

### 4. OBS 송출 테스트
1. 관리자(5173) → 채널 관리 → 채널 생성 (예: 코드 `channel1`)
2. OBS → 설정 → 방송
   - 서비스: **사용자 지정**
   - 서버: `rtmp://localhost/live`
   - 스트림 키: 표의 `channel1?key=...` 값 (클릭하면 복사)
   - 설정 → 출력 → 출력 방식 **고급** → 스트리밍: 데이터율 제어 `CBR`, **키프레임 간격 `1`초**, CPU 사용량 `veryfast`, 튜닝 `zerolatency`
   - **B-프레임 0 (필수)** — x264 는 프로파일 `baseline`, NVENC 는 최대 B-프레임 `0`. B-프레임이 있으면 WebRTC 원본 화질이 끊깁니다 (모니터링에 ⚠ 표시).
     > 라이브 재생은 OvenPlayer 가 WebRTC(UDP 10000-10004, 지연 1초 미만) 우선, 실패하면 LL-HLS 로 자동 전환합니다. 시청 URL 에 `?hls` 를 붙이면 LL-HLS 로만 재생.
     > 화질은 원본/720p/480p 중 회선에 따라 자동 전환되고, 플레이어 톱니바퀴에서 고를 수 있습니다. 720p·480p 변환은 CPU 를 쓰므로 PC 가 버거우면 `infra/ome/Server.xml` 의 해당 항목을 지우세요.
     > 다른 기기(폰 등)에서 WebRTC 로 보려면 `OME_HOST_IP=<PC의 LAN IP> docker compose up -d ome`.
3. 방송 시작 → 관리자 화면 상태가 `LIVE` 로 변경
4. 시청자(5174) → 채널 클릭 → 재생 + 채팅

> 키가 틀리면 OME 가 송출을 끊고 viewer-api 로그에 `송출 거부` 가 찍힙니다.
> 편성 시간에만 송출을 허용하려면 viewer-api `app.publish.require-schedule: true`.

### 5. 채팅 고도화 테스트
- 관리자(5173) → 채팅 관리 → **채팅 메시지 관리**
  - 채팅방(채널) 선택 → [채팅 얼리기] / [스티커 얼리기] → 시청 화면 입력창이 즉시 잠김 (서버에서도 거절)
  - 메시지에 마우스를 올려 **숨김 / 숨김 해제 / 삭제** → 시청 화면·다시보기에서 즉시 사라짐
  - 하단 입력창으로 **관리자 메시지** 전송 (시청 화면에 빨간색으로 강조)
  - [메시지 이력] 탭: 기간·상태·닉네임/내용 검색
- 채팅 관리 → **스티커 관리**: 이모지 또는 이미지 URL 로 스티커 등록 (처음 조회 시 기본 스티커 10개 자동 등록)
- 시청 화면 채팅 입력창 왼쪽 😊 → 스티커 전송
- 채팅 로그는 chat-server 가 0.3초마다 `chat_message` 테이블에 일괄 저장 (로그인 연동 전이라 시청자는 브라우저별 clientId + 랜덤 닉네임)

### 6. 녹화 → 다시보기 테스트
1. 스케줄 관리에서 지금 시간대 스케줄을 **녹화 체크**해서 등록 (편성 없이 송출하면 `app.recording.default-enabled` 따름)
2. OBS 송출 → 채팅 몇 개 → 송출 종료
3. 송출 시작 시 viewer-api 가 OME 에 녹화를 요청 → 종료되면 `infra/ome/records/live/{채널코드}/{방송ID}.mp4` (+ 완료 표시 .xml) 생성 → viewer-api 가 5초마다 확인해 방송 회차에 연결
4. 관리자 → VOD → **방송 녹화 관리** 에서 `다시보기 가능` 확인 → [다시보기]
5. 시청자(5174) → **다시보기** 메뉴: 영상 재생 위치에 맞춰 그 방송 시간대의 채팅/퀴즈가 다시 흐름

> 녹화 OFF 방송은 아예 녹화하지 않습니다. 녹화 파일은 recordings(nginx) `http://localhost:8080/dvr/...` 로 재생됩니다.

### 8. 모니터링 (방송 통합 통제)
관리자 → LIVE → **모니터링**: 전체 채널 상태 카드(LIVE, 시청자 수, 방송 시간, 송출 비트레이트, 얼리기/퀴즈 진행 표시)를 3초마다 갱신하고,
카드를 선택하면 한 화면에서 그 방송을 통제합니다.
- **방송**: 미리보기, 송출 품질(OME REST API 8081: kbps·해상도·fps·코덱·B-프레임 경고), 재생 방식 표시(WebRTC/LL-HLS), 편성, 녹화 상태, **방송 중지**(진행 중 퀴즈 마감 + 송출 잠금 + 시청자 안내 + OME 강제 종료), **송출 허용**, 공지
- **퀴즈/투표**: 진행 중 퀴즈 실시간 집계 + 지금 마감, 이 방송 편성 퀴즈 출제, 문제 은행에서 바로 출제
- **채팅**: 채팅/스티커 얼리기, 실시간 피드에서 숨김·삭제, 관리자 메시지

> 방송 중지 후에는 송출이 잠겨 OBS 가 자동 재접속해도 거부됩니다. 다시 방송하려면 [송출 허용] → OBS [방송 시작].

### 7. 퀴즈/투표 테스트
1. 퀴즈 관리 → **퀴즈 관리**: 퀴즈(정답 있음) 또는 투표(정답 없음) 등록, 보기 2~5개, 제한 시간
2. 퀴즈 관리 → **편성 퀴즈 관리**: 스케줄 선택 → 문제 은행에서 추가 → 순서 정리
3. 채널이 LIVE 일 때 [출제] → 시청 화면 채팅 위에 퀴즈 카드
4. 시청자가 보기 선택 (1인 1회) → 1초마다 응답 분포 실시간 갱신 (관리자 화면도 동일)
5. 제한 시간 종료 또는 [지금 마감] → 정답/결과 발표, 출제 이력에 참여 수·정답률
6. **방송 화면 표시**: OBS → 소스 추가 → 브라우저 → `http://localhost:5174/overlay/{채널코드}/quiz` (1920×1080, 배경 투명). 출제하면 방송 화면에 투표 카드가 뜨고 실시간 득표율·결과가 표시됨 (`?pos=tl|tr|bl|br`, `&scale=1.3`)

수동 이벤트(공지 등)는 기존처럼 chat-server 로 보낼 수 있습니다.
```bash
curl -X POST http://localhost:8093/api/chat/channel1/events \
  -H "Content-Type: application/json" \
  -d '{"type":"SYSTEM","content":"10분 뒤 퀴즈가 시작됩니다!"}'
```

## 이벤트 흐름 (채팅·관리자 조치·퀴즈 공통)

```
[viewer-web] ─STOMP SEND /app/chat/{code}─▶ [chat-server] ─ 얼리기/도배 검사 ─▶ chat_message 일괄 저장
                                                  │
[admin-api]  얼리기·숨김·삭제·관리자 메시지·퀴즈 출제/마감 ─┐
[viewer-api] 퀴즈 실시간 집계(1초)                      ├─▶ Redis "chat:{code}" ─▶ 모든 chat-server ─▶ /topic/chat/{code}
[chat-server] 시청자 메시지                         ─┘
```
메시지 타입: `CHAT, STICKER, ADMIN, SYSTEM, CHAT_FREEZE, MESSAGE_DELETE, MESSAGE_HIDE, MESSAGE_UNHIDE, QUIZ_START, QUIZ_STATS, QUIZ_RESULT, QUIZ_END`
(전송 거절 사유는 보낸 사람에게만 `/user/queue/errors` 로 전달)

## 단계별 로드맵

- [x] 1. OBS → OME → WebRTC / LL-HLS 재생 (SRS 에서 전환)
- [x] 2. 채널 / 스트림키 인증 (OME AdmissionWebhooks, 서명 검증)
- [x] 3. 스케줄 + 편성표 + 시청 페이지 (기본)
- [x] 4. 실시간 채팅 기본 (Redis Pub/Sub, 시청자 수)
- [x] 4-1. 채팅 고도화: 채팅/스티커 얼리기, 스티커 전송, 도배 방지, 메시지 DB 저장, 관리자 메시지 관리(숨김/삭제/관리자 메시지/이력 검색)
- [ ] 4-2. 로그인 연동, 사용자 제재(채팅 사용자 관리), 금칙어
- [x] 5. 녹화 → 다시보기: 송출 시작/종료 방송 회차, OME 녹화(REST API) 연결, 방송 시간대 채팅·퀴즈 동기 재생
- [ ] 5-1. 녹화 파일 MinIO 업로드 → VOD 콘텐츠 등록, 카테고리
- [x] 6. 퀴즈/투표: 문제은행, 편성 퀴즈, 출제/마감, 1인 1회 응답, Redis 실시간 집계
- [x] 7. 모니터링: OME REST API(8081) 기반 상태 + 방송 통합 통제(방송 중지, 공지, 퀴즈, 채팅)
- [x] 8-1. 화질별 변환(원본/720p/480p ABR, OME 내장 인코더)
- [ ] 8. CDN, 관리자 계정/권한(Spring Security)

## 참고
- 미디어 서버 설정: `infra/ome/Server.xml` (OvenMediaEngine v0.20.5, AGPL-3.0). 바꾼 뒤 `docker restart sp-ome`
- 재생 주소: WebRTC `ws://localhost:3333/live/{채널코드}/master`, LL-HLS `http://localhost:3333/live/{채널코드}/master.m3u8`
- OME REST API: `http://localhost:8081/v1/vhosts/default/apps/live/streams` (헤더 `Authorization: Basic base64(ome-access-token)`)
- 비밀값(`ome-access-token`, `ome-webhook-secret`)은 docker-compose 와 admin-api / viewer-api `application.yml` 의 `app.ome.*` 가 같아야 합니다.
- MinIO 콘솔: http://localhost:9001 (minioadmin / minioadmin, `docker compose --profile storage up -d`)
