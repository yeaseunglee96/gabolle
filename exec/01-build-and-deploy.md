# 1. 클론 후 빌드 및 배포

## 제품 및 버전

| 구분 | 저장소에서 확인한 값 | 근거 |
|---|---|---|
| 서버 OS | Ubuntu 24.04 LTS로 구축한 기록. 현재 설치 상태는 확인 필요 | `docs/SERVER-SETUP.md` 1절 |
| 백엔드 언어·빌드 | Java 17, Gradle Wrapper 9.7.1, Spring Boot 4.0.8 | `backend/build.gradle`, `backend/gradle/wrapper/gradle-wrapper.properties` |
| WAS (Java 웹 애플리케이션 실행 서버) | Spring Boot 내장 Tomcat 11.0.25 | `backend/build.gradle`의 `tomcat.version` |
| 백엔드 이미지 | `eclipse-temurin:17-jdk`로 빌드, `eclipse-temurin:17-jre`로 실행. 경로 최적화에는 별도 Python 3.13 포함 | `backend/Dockerfile` |
| 웹 빌드·서빙 | `node:20-alpine`에서 Expo 웹 export, `nginx:alpine` 컨테이너에서 정적 파일 제공. Nginx 이미지 세부 버전은 고정되지 않음 | `frontend/Dockerfile` |
| 모바일 앱 | Expo `~57.0.22`, React Native `0.86.3`; 앱 빌드는 EAS Build(Expo 클라우드 빌드) 설정 사용 | `frontend/package.json`, `frontend/eas.json` |
| DB·선택 서비스 | 로컬 Compose는 PostgreSQL `16-alpine`; Kafka `3.8.0`은 `events` 프로필을 명시할 때만 실행 | `backend/docker-compose.yml` |
| IDE | 버전 지정 없음. Java 17과 Node 20을 지원하는 편집기 사용 | 저장소에 IDE 버전 고정 파일 없음 |

`backend/README.md`에는 Spring Boot 4.0.0이라는 오래된 표기가 있다. 실제 빌드 선언인 `backend/build.gradle`의 4.0.8을 따른다.

## 클론과 로컬 빌드

```bash
git clone https://lab.ssafy.com/s15-bigdata-dist-sub1/S15P21E201.git
cd S15P21E201
git checkout main

cd backend
./gradlew bootJar
docker build -t gabolle-backend:local .

cd ../frontend
npm ci
npm run build:web
docker build -t gabolle-frontend:local .
```

Windows에서는 `backend/gradlew.bat`을 사용한다. 웹 빌드 전에 아래 `EXPO_PUBLIC_*` 값을 빌드 환경에 설정해야 실제 API와 로그인·지도 기능을 연결할 수 있다. 값이 빠져도 일부 검사는 경고만 내므로 생성된 번들의 기능을 확인한다. 앱은 `frontend/eas.json`과 `frontend/app.config.js`를 확인해 별도로 빌드한다. 위 명령의 전체 실행 성공 여부는 이 문서 작성 중 검증하지 않았다.

로컬 백엔드와 PostgreSQL을 함께 띄울 때는 `backend/.env.example`을 `backend/.env`로 복사하고 실제 비밀값을 입력한 다음 `docker compose up -d --build`를 실행한다. `POSTGRES_PASSWORD`와 `GABOLLE_JWT_SECRET`은 필수다. Compose는 `backend/docker-compose.yml`에 **나열된 변수만** 컨테이너로 전달한다. `.env`에 값을 추가하는 것만으로는 전달되지 않는다.

## 환경 변수와 설정 파일

| 위치 | 채워야 할 내용 |
|---|---|
| `backend/.env.example`, `backend/docker-compose.yml` | 로컬 DB 이름·사용자·비밀번호(`POSTGRES_*`), 앱 포트, JWT 서명 비밀값, 메일, 소셜 로그인, 외부 API 키. 실제 `.env`는 Git에 넣지 않는다. |
| `backend/src/main/resources/application.properties`, `application-dev.properties` | 서버 프로필, DB/Flyway(기동 시 DB 변경 이력을 적용하는 도구), 인증, 외부 서비스 설정의 변수 읽기와 기본값. |
| `backend/Jenkinsfile` | 운영 배포 시 Jenkins Credentials(배포 서버의 비밀값 저장소) ID와 컨테이너 전달값의 실제 대응표. `.env.example`보다 항목이 많다. |
| `frontend/.env.example`, `frontend/Dockerfile`, `frontend/Jenkinsfile` | `EXPO_PUBLIC_API_BASE_URL`, `EXPO_PUBLIC_OAUTH_CALLBACK_BASE_URL`, Google·Naver·Kakao·Apple 공개 client ID, `EXPO_PUBLIC_KAKAO_MAP_JS_KEY`, 빌드 식별 정보. `EXPO_PUBLIC_*`는 **웹 빌드 때** 번들에 들어가며 컨테이너 실행 때 넣어도 바뀌지 않는다. |
| `frontend/app.json`, `frontend/app.config.js`, `frontend/eas.json` | 앱 식별자와 EAS 빌드 프로필, 앱 빌드 시 필수 URL·OAuth 식별자. |
| `bigData/.env.example`, `bigData/deploy/.env.example`, `bigData/config/sources.json` | 공공데이터·Mapillary 인증, BIMS(부산 버스 위치 데이터) 수집기별 키와 호출 대상. |
| `infra/personalization/.env.example`, `compose.yaml`, `Jenkinsfile` | PostgreSQL·Redis·MinIO·MLflow·Airflow 인프라의 접속 및 배포 설정. |
| `backend/src/main/resources/db/migration/` | Flyway SQL 파일. DB의 현재 데이터 덤프는 아니다. |

운영 백엔드에서 Jenkins가 주입하는 값은 `GABOLLE_DB_URL`, `GABOLLE_DB_USERNAME`, `GABOLLE_DB_PASSWORD`, `GABOLLE_DB_SCHEMA`, `GABOLLE_JWT_SECRET`, 메일(`GABOLLE_MAIL_*`), 소셜 로그인(`GABOLLE_{GOOGLE,NAVER,KAKAO,APPLE}_*`), 사진 저장소(`GABOLLE_S3_*`), 외부 API 키(`GABOLLE_MENU_SCAN_API_KEY`, `GABOLLE_KMA_SERVICE_KEY`, `GABOLLE_TRANSIT_SERVICE_KEY`, `GABOLLE_EXCHANGE_RATE_AUTH_KEY`, `GABOLLE_INTERNAL_API_TOKEN`) 등이다. 정확한 ID와 기본값은 `backend/Jenkinsfile`의 `withCredentials`와 `docker run -e` 목록을 함께 확인한다. 실제 값은 이 문서에 적지 않는다.

## 배포 흐름과 특이사항

1. 운영 Jenkins의 `backend/Jenkinsfile`은 **`back/dev`**, `frontend/Jenkinsfile`은 **`front/dev`**를 체크아웃한다. `main`에 문서를 push해도 이 두 Job이 새 코드를 자동 배포하지는 않는다.
2. 백엔드 배포는 실제 DB에 적용된 Flyway 버전과 새 SQL의 순서를 배포 전에 검사한다. 이미지 `local-route-backend:candidate`를 만들고 컨테이너 상태 URL `http://backend:8080/actuator/health` 확인 후 `latest`로 승격한다. 프론트도 `local-route-frontend:candidate`를 확인한 뒤 `latest`로 승격한다.
3. Jenkins와 서비스 컨테이너는 `local-route-personalization_data_net` Docker 네트워크를 공유한다. 호스트 Nginx는 `/api/`를 백엔드 8080으로, `/`를 프론트엔드 3000으로 전달한다. `/api/`의 `proxy_pass` 끝에 `/`를 붙이면 경로 접두사가 제거되므로 `docs/SERVER-SETUP.md`의 설정을 따른다.
4. Nginx 서버 설정 위치는 `/etc/nginx/sites-available/default`, 업로드 크기 설정 위치는 `/etc/nginx/conf.d/upload-size.conf`이다. 서버 기록의 설정 경로이며 현재 서버 파일 자체는 확인하지 않았다.
5. DB 주소는 운영 Jenkins 자격증명으로 주입된다. 2026-09-28 실행 중인 백엔드의 연결 대상 DB명이 `app_db`임을 실제 컨테이너 환경에서 확인했다. 로컬 Compose 기본 DB명 `gabolle`과 다르다. 백업·복원 검증 범위는 [03-db-dump.md](03-db-dump.md)에 기록했다.

운영 서버, Jenkins, EAS 계정에 로그인해 새 환경에서 실제 빌드·배포·상태 확인을 수행한 결과는 이 문서에 포함되지 않는다.
