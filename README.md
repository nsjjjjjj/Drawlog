# Drawlog

Drawlog는 친구 그룹 안에서 매일 하나의 그림 주제를 정하고, 각자 웹 캔버스에 그림을 그려 날짜별 피드와 그룹 채팅으로 공유하는 PWA 지향 MVP입니다.

## 기술 구조

- Frontend: React + Vite + HTML Canvas
- Backend: Spring Boot 3, Spring Security, Spring Data JPA
- Auth: JWT Access Token + Refresh Token
- DB: PostgreSQL
- Storage: 로컬 Docker volume `uploads`
- Reverse Proxy: Nginx
- Deploy: Docker Compose
- External Access: Cloudflare Tunnel 연결 문서 제공
- Architecture: 도메인별 패키지를 나눈 DDD-lite 구조

## 실행

```bash
cp .env.example .env
docker compose up --build
```

브라우저에서 `http://localhost:8081`로 접속합니다.

같은 와이파이의 휴대폰에서는 PC의 내부 IP를 사용합니다.

```bash
ipconfig getifaddr en0
```

예를 들어 IP가 `192.168.0.12`라면 휴대폰 브라우저에서 `http://192.168.0.12:8081`로 접속합니다. CORS 기본값은 `localhost`, `127.0.0.1`, `192.168.*`, `10.*`, `172.*`, `*.trycloudflare.com` 패턴을 포함합니다.

MariaDB 시절의 Docker volume이 남아 있다면 PostgreSQL과 호환되지 않습니다. 기존 데이터를 버리고 새로 시작할 때만 아래 명령을 사용하세요.

```bash
docker compose down -v
docker compose up --build
```

## 환경변수

| 변수 | 설명 | 기본값 |
| --- | --- | --- |
| `POSTGRES_DB` | PostgreSQL 데이터베이스 | `drawlog` |
| `POSTGRES_USER` | PostgreSQL 앱 사용자 | `drawlog` |
| `POSTGRES_PASSWORD` | PostgreSQL 비밀번호 | `drawlog` |
| `APP_JWT_SECRET` | JWT 서명 키, 32바이트 이상 권장 | 로컬 개발용 값 |
| `APP_ACCESS_TOKEN_EXPIRATION_MS` | Access Token 만료 시간 | `1800000` |
| `APP_REFRESH_TOKEN_EXPIRATION_MS` | Refresh Token 만료 시간 | `1209600000` |
| `APP_COOKIE_SECURE` | Refresh Token 쿠키 Secure 여부 | `false` |
| `APP_TIME_ZONE` | 스케줄 기준 시간대 | `Asia/Seoul` |
| `APP_CORS_ALLOWED_ORIGIN_PATTERNS` | 허용 Origin 패턴 | `.env.example` 참고 |

## 인증 구조

- Access Token은 30분 동안 유효하고 JSON 응답 body로 내려갑니다.
- Refresh Token은 14일 동안 유효하고 HttpOnly 쿠키로만 저장됩니다.
- `POST /api/auth/refresh`는 Refresh Token Rotation 방식으로 동작합니다.
  - 기존 Refresh Token을 검증한 뒤 `revoked_at`으로 폐기합니다.
  - 새 Refresh Token을 HttpOnly 쿠키로 다시 내려줍니다.
  - 새 Access Token을 JSON 응답 body로 내려줍니다.
  - 이미 폐기된 Refresh Token이 다시 사용되면 401을 반환하고 해당 사용자의 활성 Refresh Token도 폐기합니다.
- `POST /api/auth/logout`은 Refresh Token을 무효화하고 쿠키를 삭제합니다.
- 앱을 계속 사용하면 refresh 시점마다 Refresh Token 만료가 다시 14일로 연장됩니다.
- 14일 이상 앱을 사용하지 않으면 Refresh Token이 만료되어 다시 로그인해야 합니다.
- 운영 HTTPS에서는 `APP_COOKIE_SECURE=true`로 설정하세요.

## 그림 저장 구조

- MVP 원본 그림은 브라우저 Canvas에서 WebP 또는 PNG 이미지로 변환해 `uploads` volume에 저장합니다.
- 현재 DB에서는 기존 호환 컬럼인 `drawings.thumbnail_path`를 이미지 경로로 사용하고, 응답에는 `imageUrl`을 내려줍니다.
- 그림 제출/수정 API는 `multipart/form-data`를 사용합니다.
  - `image`: Canvas에서 만든 WebP 또는 PNG 파일
- `drawings.stroke_data` JSONB 컬럼은 V2 호환을 위해 남겨두지만, 현재 MVP에서는 읽지 않습니다. 기존 DB의 NOT NULL 제약과 호환되도록 이미지 저장 placeholder JSON이 들어갈 수 있습니다.
- Stroke JSON, 레이어, 타임랩스, 트레이싱 이미지는 V2 기능으로 미뤘습니다.
- `StorageService` 인터페이스는 유지되어 있어 추후 Google Cloud Storage 구현체로 교체할 수 있습니다.
- 프로필 사진 변경/삭제, 그림 수정, 회원탈퇴 시 기존 로컬 파일을 정리합니다. 파일 삭제 실패는 사용자 요청을 막지 않지만 백엔드 WARN 로그와 실패 목록에 남겨 운영자가 확인할 수 있습니다.

## 주요 정책

- 사용자는 여러 그룹에 가입할 수 있습니다.
- 그룹 최대 인원은 2~12명이며 서버에서도 검증합니다.
- 그룹장은 `memberships.role = OWNER`로 판단하며 `friend_groups.owner_id`는 사용하지 않습니다.
- 그룹명 변경, 멤버 추방, 방장 위임은 OWNER만 가능합니다.
- 방장은 다른 멤버에게 위임해야 탈퇴할 수 있습니다.
- 일반 그룹 나가기에서는 방장 자동 승계/그룹 자동 삭제를 하지 않습니다.
- 회원탈퇴 시에는 모든 그룹 membership이 삭제됩니다. 탈퇴자가 OWNER이면 가장 먼저 가입한 남은 멤버에게 OWNER를 자동 위임하고, OWNER 혼자 있던 그룹은 관련 데이터를 정리한 뒤 그룹을 삭제합니다.
- 초대코드와 `/invite/{inviteCode}` 초대링크를 모두 지원합니다.
- 초대코드 재발급 API는 제거했습니다.
- 주제 제안은 하루 1인 1개입니다.
- 주제는 등록 즉시 투표 가능하고, 1표라도 받으면 수정/삭제할 수 없습니다.
- 투표는 1인 1표이며 재투표 시 기존 vote row가 다른 후보를 가리키도록 바뀝니다.
- 매일 00:00에 최다 득표 주제를 선정하고, 동률 또는 0표 후보는 랜덤으로 고릅니다.
- 후보가 없으면 시스템 기본 주제 중 랜덤으로 선정합니다.
- 스케줄러가 실패해도 오늘 주제 조회 시 없으면 자동 생성합니다.
- 하루 1인 1그림이며 여러 번 수정 가능하지만 최종본 1개만 유지합니다.
- 그림은 당일만 수정 가능하고 다음날 00:00 이후 잠깁니다.
- 그림 삭제 API는 제거했습니다.
- 미제출자는 당일 피드가 잠기고, 제출자는 당일 피드를 바로 볼 수 있습니다.
- 다음날이 되면 피드가 공개됩니다.
- 피드 날짜 조회는 DailyTopic을 자동 생성하지 않습니다.
- 그림이 없는 날짜는 랜덤 주제를 만들지 않고 “아직 아무도 그림을 올리지 않았어요.” 상태로 표시합니다.
- 미래 날짜 피드 조회는 허용하지 않습니다.
- 날짜 달력에서는 실제 그림 기록이 존재하는 날짜만 선택할 수 있고, 좌우 화살표는 하루 단위로 이동합니다.
- 채팅은 REST API + polling 방식입니다.
- 그림 인용 댓글은 별도 댓글 테이블 없이 `DRAWING_QUOTE` 타입의 채팅 메시지로 처리합니다.
- 채팅 삭제는 하드 삭제가 아니라 `deleted_at` 기록입니다.
- 계정 삭제는 사용자 row 소프트 삭제이며 이메일은 `deleted_user_{id}` 형태로 익명화됩니다. 탈퇴 사용자의 membership과 그림 row는 삭제하고, 연결된 프로필/그림 파일도 로컬 Storage에서 정리합니다. 채팅은 유지되며 보낸 사람은 “탈퇴한 사용자”로 표시됩니다.

## API

성공 응답은 데이터만 반환합니다. 에러 응답은 아래 형태입니다.

```json
{
  "code": "GROUP_FULL",
  "message": "그룹 최대 인원을 초과했습니다."
}
```

### Auth

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/auth/signup` | 회원가입 |
| `POST` | `/api/auth/login` | 로그인 |
| `POST` | `/api/auth/refresh` | Refresh Cookie 회전 + Access Token 재발급 |
| `POST` | `/api/auth/logout` | Refresh Token 무효화 |
| `GET` | `/api/auth/me` | 내 정보 |

### User

| Method | Path | 설명 |
| --- | --- | --- |
| `PATCH` | `/api/users/me/nickname` | 닉네임 변경 |
| `PATCH` | `/api/users/me/profile-image` | 프로필 이미지 변경 |
| `DELETE` | `/api/users/me` | 계정 소프트 삭제 |

### Group

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/groups` | 그룹 생성, 첫 주제 선택 가능 |
| `GET` | `/api/groups` | 내 그룹 목록 |
| `GET` | `/api/groups/{groupId}` | 그룹 상세 |
| `PATCH` | `/api/groups/{groupId}` | OWNER 그룹명 변경 |
| `POST` | `/api/groups/join` | 초대코드 입장 |
| `POST` | `/api/groups/{groupId}/leave` | 그룹 탈퇴 |
| `POST` | `/api/groups/{groupId}/transfer-owner` | 방장 위임 |
| `DELETE` | `/api/groups/{groupId}/members/{userId}` | 멤버 추방 |

### Topic

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/api/groups/{groupId}/topics/today` | 오늘 주제 |
| `GET` | `/api/groups/{groupId}/topics/suggestions?targetDate=YYYY-MM-DD` | 주제 후보 |
| `POST` | `/api/groups/{groupId}/topics/suggestions` | 주제 제안 |
| `PATCH` | `/api/groups/{groupId}/topics/suggestions/{suggestionId}` | 주제 수정 |
| `DELETE` | `/api/groups/{groupId}/topics/suggestions/{suggestionId}` | 주제 삭제 |
| `POST` | `/api/groups/{groupId}/topics/suggestions/{suggestionId}/vote` | 투표/재투표 |
| `GET` | `/api/groups/{groupId}/topics/my-vote?targetDate=YYYY-MM-DD` | 내 투표 |

### Drawing / Feed / Chat / Notification

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/api/groups/{groupId}/drawings/today` | 내 오늘 그림 |
| `POST` | `/api/groups/{groupId}/drawings/today` | 오늘 그림 제출, multipart `image` |
| `PUT` | `/api/groups/{groupId}/drawings/today` | 오늘 그림 수정, multipart `image` |
| `GET` | `/api/groups/{groupId}/drawings/{drawingId}` | 그림 상세 |
| `GET` | `/api/groups/{groupId}/feed?date=YYYY-MM-DD` | 날짜별 피드 |
| `GET` | `/api/groups/{groupId}/feed/dates` | 그림 기록이 존재하는 날짜 목록 |
| `GET` | `/api/groups/{groupId}/chats?cursor=...&size=30` | 채팅 조회 |
| `POST` | `/api/groups/{groupId}/chats` | 채팅 작성 |
| `DELETE` | `/api/groups/{groupId}/chats/{messageId}` | 채팅 소프트 삭제 |
| `GET` | `/api/notifications` | 알림 목록 |
| `PATCH` | `/api/notifications/read-all` | 전체 읽음 |
| `GET` | `/api/notification-settings` | 알림 설정 |
| `PATCH` | `/api/notification-settings` | 전체 알림 ON/OFF |
| `PATCH` | `/api/groups/{groupId}/notification-setting` | 그룹 알림 ON/OFF |

## Scheduler

`APP_TIME_ZONE` 기준으로 동작합니다.

- 23:50: 투표 종료 시점
- 00:00: DailyTopic 선정 및 지난 그림 잠금
- 12:00: 오늘 그림을 안 그린 사람에게 알림 생성
- 23:00: 내일 주제 투표를 안 한 사람에게 알림 생성

## 저장소 구조

```text
backend/src/main/java/com/drawlog
├── auth
├── user
├── group
├── topic
├── drawing
├── feed
├── chat
├── notification
├── storage
├── common
└── config
```

## 테스트

Docker 백엔드 이미지 빌드 중 `gradle clean test bootJar`가 실행됩니다.

```bash
docker compose build backend
```

프론트 빌드는 아래처럼 확인합니다.

```bash
cd frontend
npm ci
npm run build
```

현재 테스트는 그룹 최대 12명, 방장 위임 없이 탈퇴 불가, 1인 1주제, 투표 잠금, 재투표, DailyTopic 선정, 그림 잠금, 피드 잠금, 채팅 소프트 삭제, 계정 소프트 삭제, 프로필/그림 파일 교체 시 이전 파일 삭제, 프로필 삭제 시 파일 삭제, 회원탈퇴 시 membership 삭제, OWNER 자동 위임, 단독 OWNER 그룹 삭제, 탈퇴자 그림/파일 삭제, 피드 제외, 채팅 유지, Refresh Token 무효화를 검증합니다.

## Cloudflare Tunnel

MVP Compose에는 `cloudflared`를 포함하지 않았습니다. 외부 접속이 필요하면 [docs/cloudflared.md](docs/cloudflared.md)를 참고해 `nginx:80`으로 터널을 연결하세요.
