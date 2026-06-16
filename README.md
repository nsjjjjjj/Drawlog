# Drawlog

친구 그룹 안에서 매일 하나의 주제를 정하고, 각자 웹 캔버스에 그림을 그려 날짜별 피드로 공유하는 MVP입니다.

## 기술 구조

- Frontend: React + Vite + HTML Canvas
- Backend: Spring Boot 3, Spring Security, JWT, Spring Data JPA
- DB: MariaDB
- Storage: 로컬 Docker volume `uploads`
- Reverse Proxy: Nginx
- Deploy: Docker Compose

## 실행

```bash
cp .env.example .env
docker compose up --build
```

브라우저에서 `http://localhost:8081`로 접속합니다.

같은 와이파이의 휴대폰에서 열 때는 PC의 내부 IP를 사용합니다.

```bash
ipconfig getifaddr en0
```

예를 들어 IP가 `192.168.0.12`라면 휴대폰 브라우저에서 `http://192.168.0.12:8081`로 접속합니다. 다른 포트나 터널 도메인을 붙이면 `.env`의 `APP_CORS_ALLOWED_ORIGIN_PATTERNS`에 해당 Origin을 추가한 뒤 `docker compose up -d --build backend`를 실행합니다.

처음 사용 흐름:

1. 회원가입
2. 그룹 생성 또는 초대코드로 입장
3. 오늘의 주제 확인
4. 캔버스에 그림을 그리고 제출
5. 날짜별 피드에서 같은 그룹 그림 확인

## 환경변수

`.env.example`을 복사해서 사용합니다.

| 변수 | 설명 | 기본값 |
| --- | --- | --- |
| `MARIADB_DATABASE` | MariaDB 데이터베이스 | `drawlog` |
| `MARIADB_USER` | MariaDB 앱 사용자 | `drawlog` |
| `MARIADB_PASSWORD` | MariaDB 앱 비밀번호 | `drawlog` |
| `MARIADB_ROOT_PASSWORD` | MariaDB root 비밀번호 | `drawlog-root` |
| `APP_JWT_SECRET` | JWT 서명 키, 32바이트 이상 권장 | 로컬 개발용 값 |
| `APP_TIME_ZONE` | 주제 선정 기준 시간대 | `Asia/Seoul` |
| `APP_CORS_ALLOWED_ORIGIN_PATTERNS` | 브라우저 API 요청을 허용할 Origin 패턴, 모바일 로컬 접속용 사설 IP 포함 | `localhost`, `127.0.0.1`, `192.168.*:8081`, `10.*:8081`, `172.*:8081` |

Spring Boot에서 추가로 사용하는 값:

- `APP_UPLOAD_DIR`: 업로드 저장 경로, Compose에서는 `/app/uploads`
- `APP_MAX_FILE_SIZE`: multipart 파일 제한, 기본 `5MB`
- `APP_MAX_REQUEST_SIZE`: multipart 요청 제한, 기본 `6MB`
- `APP_MAX_IMAGE_BYTES`: 서비스 레벨 이미지 크기 제한, 기본 `5242880`

## API

인증이 필요한 API는 `Authorization: Bearer <token>` 헤더를 사용합니다.

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/auth/signup` | 회원가입 |
| `POST` | `/api/auth/login` | 로그인 및 JWT 발급 |
| `GET` | `/api/groups` | 내가 속한 그룹 목록과 멤버 조회 |
| `POST` | `/api/groups` | 그룹 생성, 생성자는 자동 멤버/그룹장 등록, 첫 주제 선택 가능 |
| `POST` | `/api/groups/join` | 초대코드로 그룹 입장 |
| `PATCH` | `/api/groups/{id}` | 그룹장 전용 그룹명 변경 |
| `POST` | `/api/groups/{id}/invite-code` | 그룹장 전용 초대코드 재발급 |
| `DELETE` | `/api/groups/{id}/members/{userId}` | 그룹장 전용 멤버 내보내기 |
| `DELETE` | `/api/groups/{id}/membership` | 그룹 나가기, 마지막 멤버가 나가면 그룹 데이터 삭제 |
| `GET` | `/api/topics/today?groupId={id}` | 선택 그룹의 오늘 주제 조회 |
| `POST` | `/api/topics/suggestions` | 내일 주제 제안 |
| `GET` | `/api/topics/suggestions/mine?groupId={id}` | 내 내일 주제 제안 조회 |
| `PUT` | `/api/topics/suggestions/{id}` | 내 주제 제안 수정 |
| `DELETE` | `/api/topics/suggestions/{id}` | 내 주제 제안 삭제 |
| `POST` | `/api/drawings` | `multipart/form-data`의 `groupId`, `file` 필드로 PNG/WebP 업로드 |
| `DELETE` | `/api/drawings/{id}` | 내 그림 삭제 |
| `GET` | `/api/feed?groupId={id}&date=YYYY-MM-DD` | 같은 그룹의 날짜별 그림 피드 조회 |
| `GET` | `/api/groups/{id}/messages` | 같은 그룹의 최근 채팅 메시지 조회 |
| `POST` | `/api/groups/{id}/messages` | 일반 채팅, 그림 인용 채팅, 메시지 답장 작성 |

요청 예시:

```bash
curl -X POST http://localhost:8081/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"username":"jun","email":"jun@example.com","password":"password1"}'
```

## 서비스 동작

- 사용자는 여러 그룹에 가입할 수 있고 로그인 후 그룹 선택 화면에서 작업할 그룹을 고릅니다.
- 그룹 생성자는 그룹장이 되며 그룹명 변경, 초대코드 재발급, 멤버 내보내기를 할 수 있습니다.
- 그룹 생성 시 첫 주제를 직접 입력할 수 있고, 비우면 시스템 기본 주제가 자동 선택됩니다.
- 그룹장이 나가면 남은 멤버 중 가장 먼저 들어온 멤버에게 그룹장이 넘어갑니다.
- 마지막 멤버가 그룹을 나가면 그룹의 주제, 제안, 그림 DB 데이터와 로컬 이미지 파일이 삭제됩니다.
- 매일 00시(`APP_TIME_ZONE` 기준)에 그룹별 주제를 선정합니다.
- 자정 스케줄이 실행되지 못해도 `GET /api/topics/today` 또는 피드 조회 시 해당 날짜 주제를 자동 생성합니다.
- 주제 제안은 항상 다음 날 날짜로 저장됩니다.
- 그룹에 해당 날짜 제안이 있으면 그중 랜덤 선택, 없으면 시스템 기본 주제 목록에서 랜덤 선택합니다.
- 피드는 인증된 사용자가 속한 기본 그룹의 그림만 반환합니다.
- 그룹 채팅은 같은 그룹 멤버만 조회/작성할 수 있습니다.
- 그림 아래의 인용 버튼으로 특정 그림을 채팅에 붙일 수 있고, 메시지의 답하기 버튼으로 대화 문맥을 이어갈 수 있습니다.

## 저장소 구조

```text
.
├── backend
│   └── src/main/java/com/drawlog
│       ├── auth
│       ├── chat
│       ├── config
│       ├── drawing
│       ├── group
│       ├── storage
│       ├── topic
│       └── user
├── frontend
│   └── src
├── nginx
├── docs
└── docker-compose.yml
```

## StorageService 교체

업로드 저장은 `backend/src/main/java/com/drawlog/storage/StorageService.java` 인터페이스 뒤에 있습니다. 현재 구현은 `LocalStorageService`이며 로컬 Docker volume에 파일을 저장하고 `/uploads/...` URL을 반환합니다.

Google Cloud Storage로 교체할 때는 새 구현체를 만들고 `storeImage(MultipartFile file)`에서 다음만 유지하면 됩니다.

- PNG/WebP 및 크기 검증
- 객체 저장
- DB에 저장할 공개 또는 서명 이미지 URL 반환

## 보안

- `/api/auth/signup`, `/api/auth/login`, `/uploads/**`를 제외한 모든 API는 JWT가 필요합니다.
- 업로드는 MIME type `image/png`, `image/webp`와 확장자 `.png`, `.webp`를 모두 검증합니다.
- Nginx `client_max_body_size`와 Spring multipart 제한이 업로드 크기를 제한합니다.
- 운영에서는 반드시 강한 `APP_JWT_SECRET`과 별도 DB 비밀번호를 사용하세요.

## 테스트

백엔드 통합 테스트는 그룹 생성, 첫 주제 지정, 그룹장 권한, 멤버 내보내기, 방장 승계, 마지막 멤버 퇴장 시 그룹 삭제, 그룹 채팅의 인용/답장/권한 흐름을 검증합니다. Docker 백엔드 이미지 빌드 중에도 테스트가 실행됩니다.

```bash
docker compose build backend
```

JDK/Gradle 환경에서 직접 실행할 수도 있습니다.

```bash
cd backend
gradle test
```

## cloudflared

MVP Compose에는 포함하지 않았습니다. 추가 방법은 [docs/cloudflared.md](docs/cloudflared.md)를 참고하세요.
