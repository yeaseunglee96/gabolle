# GABOLLE Backend

GABOLLE(가볼래) Spring Boot 백엔드입니다.

## 현재 생성 기준

- Spring Boot 4.0.0
- Java 17
- Gradle Wrapper
- **PostgreSQL 16** + Spring Data JPA — MySQL·SQLite는 쓰지 않습니다 ([docs/DB-STANDARD.md](../docs/DB-STANDARD.md))
- Flyway migration — 백엔드 표는 `public`이 아니라 **`gabolle` schema**에 만들어집니다
- Spring Security + OAuth2 Client/Resource Server
- Actuator health

팀 백엔드는 Java 17을 기준으로 개발합니다. 팀 PC와 CI도 Java 17로 맞춥니다.

## 실행

```powershell
cd backend
./gradlew.bat bootRun
```

기본 상태 확인 주소:

```text
http://localhost:8080/actuator/health
```

기본 profile(`no-db`)은 PostgreSQL 없이 서버와 health endpoint만 실행합니다 — 컴파일 확인용입니다.

DB를 쓰는 기능은 `db` 또는 `dev` profile로 전환해야 돕니다. 그 profile에는 이미 PostgreSQL 연결과 Flyway가 붙어 있고, 운영에는 migration 6개가 적용된 상태입니다(2026-09-03). 🔴 `@Profile({"db","dev"})`가 붙은 빈은 기본 profile에서 **만들어지지 않습니다** — DB를 쓰는 기능이 안 뜨면 profile을 먼저 확인하십시오.

DB 타입 기준·schema 분리·마이그레이션 규칙은 [docs/DB-STANDARD.md](../docs/DB-STANDARD.md)에 모아 두었습니다. 🔴 특히 **raw SQL을 쓸 때 `gabolle` schema를 못 찾는 함정**이 있어서, `JdbcTemplate`을 쓰기 전에 그 문서 2절을 보십시오.

## Docker Compose 실행

`docker compose`는 PostgreSQL과 Spring Boot를 함께 실행하고, 컨테이너 시작 시 Flyway migration을 적용합니다.

```powershell
cd backend
Copy-Item .env.example .env
# .env에서 POSTGRES_PASSWORD와 GABOLLE_JWT_SECRET을 실제 값으로 변경
docker compose up --build -d
docker compose logs -f backend
```

health 확인:

```text
http://localhost:8080/actuator/health
```

웹 로컬 개발에서는 `.env`의 `GABOLLE_CORS_ALLOWED_ORIGINS`에 Vite origin(예: `http://localhost:5173`)을 추가하고, HTTP이므로 `GABOLLE_WEB_REFRESH_COOKIE_SECURE=false`로 둡니다. 웹 로그인/OAuth 요청에는 `X-Client-Platform: WEB`을 보내고 fetch/axios에 `credentials: "include"`를 지정합니다. refresh token은 브라우저 저장소에 저장하지 않고 다음 API를 사용합니다.

로그인 요청에 `deviceId`를 넣어 기기 바인딩을 활성화한 경우 `/web/refresh`에도 같은 값을 `X-Device-Id` 헤더로 전송합니다.

```text
POST /api/v1/auth/web/refresh  # HttpOnly cookie를 보내 access token 재발급
POST /api/v1/auth/web/logout   # cookie 삭제 및 refresh session 폐기
```

실제 배포에서는 `.env`를 저장소에 넣지 않고 Jenkins Credentials 또는 Secret Store에서 주입합니다. Google OAuth가 동작하려면 Google Console의 redirect URI, `GABOLLE_GOOGLE_CLIENT_ID`, `GABOLLE_GOOGLE_CLIENT_SECRET`, Nginx HTTPS/SPA fallback/App Link 설정이 모두 같은 값이어야 합니다.

## 기능 단위 패키지

패키지 간 의존성은 `common`으로만 우회하지 말고 각 기능의 application/domain 경계를 유지합니다. Controller에서 다른 기능의 Repository를 직접 호출하지 않습니다.

```text
src/main/java/com/gabolle/backend/
├── common/          # 공통 응답, 오류, 요청 ID, 설정
├── auth/            # Google/Naver/Kakao OAuth, JWT, 세션
├── user/            # 회원 프로필, 동의, 취향, 제약
├── place/           # 내부 placeId, 다국어 장소, 외부 데이터 매핑
├── editorial/       # Editor's Pick과 반응
├── trip/            # 여행, 멤버, 초대, 여행 조건 snapshot
├── itinerary/       # 불변 일정 버전, 일자, 항목, 구간
├── recommendation/  # 추천 Job, baseline, 추천 서비스 adapter
└── event/           # PostgreSQL Outbox와 이벤트 발행
```

이번 작업에서는 디렉터리와 프로젝트 뼈대만 만들었습니다. 초기 담당 경계는 다음과 같습니다.

- 본인: `auth`, `user` — 소셜 로그인, JWT/세션, 회원·동의·취향·제약
- 팀원: `place`, `editorial`, `trip`, `itinerary`, `recommendation`, `event`
- 공동: `common` — API 응답/오류 형식, 인증 주체 규칙, 요청 ID 등 계약만 합의

각 기능은 API·entity·service·repository·test를 같은 기능 패키지 안에 두고, 다른 기능의 Repository를 직접 호출하지 않습니다. 공통 계약이나 DB migration을 바꿀 때는 두 담당자가 함께 확인합니다.

## MR 빌드 검사 (S15P21E201-266)

`backend/`가 바뀐 MR에서는 `.gitlab-ci.yml`의 `backend:build` 잡이 자동으로
`./gradlew build`(테스트 포함)를 돌립니다. 로컬에서 같은 걸 미리 확인하려면:

```bash
cd backend
./gradlew build
```

## 참고 기준

- 공개 API: 저장소 밖 개인 컨텍스트의 GABOLLE API 명세
- 데이터 모델: 저장소 밖 개인 컨텍스트의 GABOLLE ERD 초안
- 기준 브랜치: `back/dev`
- 초기 Jira: `S15P21E201-243` — Spring Boot 프로젝트 뼈대와 상태 확인 주소
