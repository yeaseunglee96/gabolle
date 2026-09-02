# GABOLLE 백엔드 인증 인수인계

이 문서는 회원 인증·소셜 로그인 백엔드와 배포 담당자가 같은 계약을 사용하기 위한 문서다. 실제 비밀값은 문서나 저장소에 기록하지 않는다.

## 현재 구현 범위

- 이메일 회원가입, 이메일 인증, 로그인, 비밀번호 재설정
- access token·refresh token 발급, rotation, 재사용 감지, 로그아웃
- OAuth challenge 발급과 1회성 소비
- OAuth challenge에 `state`, `nonce`, PKCE `codeChallenge(S256)`, redirect URI, device ID 바인딩
- Google authorization code 교환과 ID Token의 서명/JWKS, issuer, audience, nonce, `email_verified` 검증
- 로컬/OAuth 가입 공통 필수 약관 검증 및 동의 이력 저장
- access token에 session ID를 포함하고 사용자·세션 상태를 요청마다 확인
- Google/Naver/Kakao provider adapter의 기본 code 교환 및 프로필 조회
- Flyway 인증 스키마
- 모바일 access/refresh 응답과 웹 HttpOnly refresh cookie 응답을 같은 인증 서비스에서 제공
- 웹 origin에 대한 CORS 및 브라우저 refresh/logout 엔드포인트

실제 PostgreSQL, SMTP, Google 네트워크, EC2 환경에서는 아직 통합 검증이 필요하다.

## 모바일 OAuth 흐름

서버 callback 방식으로 Google code를 직접 받지 않는다. React Native 앱이 시스템 브라우저로 인증을 시작하고, HTTPS App Link/Universal Link로 code를 받은 뒤 백엔드에 전달한다.

```text
앱: PKCE verifier/challenge 생성
  -> POST /api/v1/auth/oauth/google/challenge
  <- state, nonce, expiresAt
앱: Google authorization endpoint를 시스템 브라우저로 호출
Google -> 앱 HTTPS redirect URI: code, state
앱 -> POST /api/v1/auth/oauth/google: code, verifier, state, nonce
백엔드: challenge/PKCE 검증 -> Google token 교환 -> ID Token 검증
       -> 사용자 연결/생성 -> GABOLLE access·refresh token 발급
```

현재 개발 기준 redirect URI는 다음 값으로 맞춘다. 앱의 App Link 경로가 확정되면 Google Console과 `GABOLLE_OAUTH_ALLOWED_REDIRECT_URIS`를 같은 값으로 변경한다.

```text
https://j15e201.p.ssafy.io/oauth/google/callback
```

`/api/v1/auth/oauth/google/callback`은 현재 서버 callback 엔드포인트가 아니므로 모바일 흐름의 redirect URI로 사용하지 않는다.

## 웹 인증 흐름

웹은 refresh token을 JavaScript나 `localStorage`에 저장하지 않는다. 로그인 요청에 `X-Client-Platform: WEB`을 보내면 서버가 `HttpOnly` refresh cookie를 설정하고 응답 JSON에는 access token만 넣는다.

```text
웹: POST /api/v1/auth/login 또는 /api/v1/auth/oauth/{provider}
    Header: X-Client-Platform: WEB
    Body: 기존 모바일 계약과 동일
서버: gabolle_refresh_token cookie(Set-Cookie) + access token JSON
웹: access token은 메모리에 보관
웹: POST /api/v1/auth/web/refresh (credentials: include)
서버: cookie 검증·rotation 후 새 cookie + access token JSON
웹: POST /api/v1/auth/web/logout?allDevices=false (credentials: include)
서버: cookie 삭제 및 현재 refresh token 폐기
```

웹이 별도 origin에서 API를 호출하면 fetch/axios에 `credentials: "include"`를 지정해야 한다. 서버는 `GABOLLE_CORS_ALLOWED_ORIGINS`에 등록된 정확한 origin만 허용하며 `*`와 credentials를 함께 사용하지 않는다. 운영 cookie는 `Secure=true`, `SameSite=Strict`를 기본으로 한다. 로컬 HTTP 개발에서만 `.env`의 `GABOLLE_WEB_REFRESH_COOKIE_SECURE=false`를 사용한다.

프론트와 API가 서로 다른 site에 배치되어 `SameSite=None`을 사용해야 하는 경우에는 현재 전역 CSRF 비활성 설정만으로 운영하지 않는다. 이 배치에서는 CSRF token 또는 별도 same-site BFF를 추가한 뒤 배포한다.

웹 로그인·OAuth 요청에도 `deviceId`를 보내면 refresh token을 브라우저 단위로 묶을 수 있다. 이 경우 `/web/refresh`에도 같은 값을 `X-Device-Id` 헤더로 보내야 한다. 값은 브라우저에서 임의 생성한 식별자이며 이메일·개인정보를 넣지 않는다.

## API 계약

### Challenge

```http
POST /api/v1/auth/oauth/google/challenge
Content-Type: application/json
```

```json
{
  "redirectUri": "https://j15e201.p.ssafy.io/oauth/google/callback",
  "codeChallenge": "<base64url(SHA-256(codeVerifier))>",
  "codeChallengeMethod": "S256",
  "deviceId": "<device-id>"
}
```

### Login

```http
POST /api/v1/auth/oauth/google
Content-Type: application/json
```

```json
{
  "authorizationCode": "<google-code>",
  "redirectUri": "https://j15e201.p.ssafy.io/oauth/google/callback",
  "codeVerifier": "<pkce-verifier>",
  "state": "<challenge-state>",
  "nonce": "<challenge-nonce>",
  "ageGateAccepted": true,
  "deviceId": "<device-id>",
  "consents": {
    "TERMS_OF_SERVICE": true,
    "PRIVACY_POLICY": true
  },
  "behaviorPersonalizationEnabled": false
}
```

`state`, `nonce`, `redirectUri`, `deviceId`, PKCE가 하나라도 다르면 요청을 거부한다. challenge는 성공적으로 소비되면 재사용할 수 없다.
최초 소셜 가입자는 로컬 가입과 동일하게 필수 약관 동의가 필요하며, 기존 계정에 동일 이메일이 있으면 자동 연결하지 않고 계정 연결 절차를 안내한다.

## 환경변수 계약

Spring의 `dev` 프로필에 주입한다.

```text
SPRING_PROFILES_ACTIVE=dev
GABOLLE_DB_URL=jdbc:postgresql://...
GABOLLE_DB_USERNAME=...
GABOLLE_DB_PASSWORD=...
GABOLLE_JWT_SECRET=<32자 이상 랜덤값>
GABOLLE_GOOGLE_CLIENT_ID=...
GABOLLE_GOOGLE_CLIENT_SECRET=...
GABOLLE_GOOGLE_ALLOWED_CLIENT_IDS=...
GABOLLE_OAUTH_ALLOWED_REDIRECT_URIS=https://j15e201.p.ssafy.io/oauth/google/callback
GABOLLE_CORS_ALLOWED_ORIGINS=https://j15e201.p.ssafy.io
GABOLLE_WEB_REFRESH_COOKIE_NAME=gabolle_refresh_token
GABOLLE_WEB_REFRESH_COOKIE_SECURE=true
GABOLLE_WEB_REFRESH_COOKIE_SAME_SITE=Strict
```

Google client secret은 모바일 앱에 넣지 않는다. 여러 플랫폼 client ID를 사용하게 되면 `GABOLLE_GOOGLE_ALLOWED_CLIENT_IDS`에 ID Token audience를 추가하고, token 교환에 사용할 client ID 매핑을 플랫폼 결정 이후 확정한다.

## Jenkins 인수인계

실제 배포가 Jenkins라면 Jenkins Credentials 또는 연동된 Secret Store에서 다음 값을 주입한다. Jenkinsfile과 로그에 실제 값이 나타나지 않아야 한다.

백엔드는 `backend/Dockerfile`과 `backend/docker-compose.yml`을 기준으로 이미지와 PostgreSQL을 구성할 수 있다. Jenkins에서는 저장소의 `.env`를 생성하지 말고 환경변수를 주입한 뒤 다음 순서로 실행한다.

```text
docker compose -f backend/docker-compose.yml build backend
docker compose -f backend/docker-compose.yml up -d db backend
```

Compose 파일 추가만으로 Google 로그인이 완료되지는 않는다. 이미지 실행과 DB migration은 확인할 수 있지만, 실제 로그인은 Google Console redirect URI, provider client 설정, Nginx HTTPS/App Link, 모바일 앱의 code/state 전달까지 맞아야 한다.

```text
GABOLLE_GOOGLE_CLIENT_ID
GABOLLE_GOOGLE_CLIENT_SECRET
GABOLLE_JWT_SECRET
GABOLLE_DB_URL
GABOLLE_DB_USERNAME
GABOLLE_DB_PASSWORD
GABOLLE_MAIL_USERNAME
GABOLLE_MAIL_PASSWORD
```

기본 배포 검증 순서:

1. `backend/gradlew clean test bootJar`
2. 산출물 또는 컨테이너를 EC2에 배포
3. `SPRING_PROFILES_ACTIVE=dev`와 필수 환경변수 확인
4. Spring health endpoint 확인
5. Nginx의 `/api/` 프록시가 Spring으로 전달되는지 확인
6. 웹 정적 파일의 SPA fallback이 `/oauth/{provider}/callback` 경로에서도 동작하는지 확인
7. 웹에서 `credentials: include`로 login → web/refresh → web/logout 순서를 테스트
8. 모바일 앱에서 challenge → Google → login 순서로 테스트

GitLab CI/CD 변수는 Jenkins로 자동 전달되지 않는다. 배포 주체가 Jenkins로 확정되면 Jenkins Credentials를 기준으로 관리한다.

## Nginx 인수인계

- `https://j15e201.p.ssafy.io` TLS 종료
- `/api/`를 Spring 포트로 reverse proxy
- `/oauth/{provider}/callback` 등 웹 SPA 경로는 정적 파일의 `index.html`로 fallback
- `Host`, `X-Forwarded-Proto`, `X-Forwarded-For` 전달
- CORS는 Spring에서 처리하므로 `GABOLLE_CORS_ALLOWED_ORIGINS`에 실제 웹 origin만 허용
- `Set-Cookie`와 `Cookie` 헤더를 삭제하거나 캐시하지 않도록 설정
- App Link/Universal Link를 사용할 경우 도메인에 association 파일을 HTTPS로 제공

Nginx는 Google Client Secret을 알 필요가 없다.

## 검증 경계

현재 `backend` 단위 테스트는 통과했지만 다음은 미검증이다.

- 실제 PostgreSQL에 Flyway migration 적용
- 실제 SMTP 인증 메일 발송
- Google JWKS 네트워크 조회와 실제 authorization code 교환
- refresh device binding, access token 폐기, 계정 상태 변경 시 실제 앱 흐름
- Android/iOS App Link 설정
- 웹 브라우저의 실제 OAuth callback 및 cookie 정책(SameSite/CORS)
- Jenkins 배포 및 EC2/Nginx 운영 확인
