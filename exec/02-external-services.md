# 2. 외부 서비스 등록과 활용

아래는 `main`의 코드와 설정에서 **실제로 참조하는 서비스**다. 발급한 키의 값이나 콘솔 계정은 포함하지 않는다. 제공자 콘솔의 실제 등록 상태, 할당량과 키 유효성은 별도 확인이 필요하다.

| 서비스 | 포팅 시 준비할 것 | 코드/설정 근거 |
|---|---|---|
| Google, Naver, Kakao 로그인 | 각 개발자 콘솔에서 앱을 등록하고 웹 콜백 URI를 허용한다. 공개 client ID는 `EXPO_PUBLIC_{GOOGLE,NAVER,KAKAO}_CLIENT_ID`, 서버의 ID와 secret은 `GABOLLE_{GOOGLE,NAVER,KAKAO}_CLIENT_ID`와 `..._CLIENT_SECRET`으로 등록한다. 서버의 `GABOLLE_OAUTH_ALLOWED_REDIRECT_URIS`와 정확히 맞춘다. | `frontend/.env.example`, `backend/src/main/resources/application-dev.properties`의 OAuth 설정, `docs/GABOLLE_BACKEND_AUTH_HANDOFF.md` |
| Apple 로그인 | Apple 개발자 계정에서 Service ID, Team ID, Key ID, `.p8` 개인키를 준비한다. 서버 변수는 `GABOLLE_APPLE_CLIENT_ID`, `GABOLLE_APPLE_TEAM_ID`, `GABOLLE_APPLE_KEY_ID`, `GABOLLE_APPLE_PRIVATE_KEY`, 앱 공개 ID는 `EXPO_PUBLIC_APPLE_CLIENT_ID`다. 웹과 iOS 앱의 허용 client ID 범위도 확인한다. | `backend/src/main/resources/application-dev.properties`의 Apple 단락, `backend/Jenkinsfile`, `frontend/Dockerfile` |
| Kakao 지도·장소·경로 | Kakao Developers에서 웹 도메인과 앱 사용 환경을 등록한다. 웹·앱 WebView 지도용 JavaScript 키는 `EXPO_PUBLIC_KAKAO_MAP_JS_KEY`; 서버의 길찾기·출발지 검색은 `GABOLLE_KAKAO_REST_API_KEY` 또는 로그인용 Kakao REST ID를 사용한다. 웹 빌드 환경과 EAS 앱 빌드 환경 각각에 공개 지도 키를 넣는다. | `frontend/docs/MAP-RECOVERY.md`, `frontend/.env.example`, `backend/src/main/resources/application-dev.properties`의 경로 설정 |
| SSAFY GMS 중계 | 서비스 이용 권한과 키를 발급받아 `GABOLLE_MENU_SCAN_API_KEY`로 배포 자격증명에 등록한다. 메뉴판 읽기와 여행 이름·번역이 같은 키를 참조하며 자연어 도우미도 별도 `GABOLLE_ASSISTANT_API_KEY`가 없으면 이 키를 사용한다. 제공되는 모델과 기본 URL 조합을 함께 확인한다. | `backend/src/main/resources/application-dev.properties`의 번역·도우미·메뉴판 단락, `backend/Jenkinsfile` |
| 공공데이터포털 | [data.go.kr](https://www.data.go.kr) 가입 후 부산 버스 실시간 위치(BIMS), TourAPI 관광정보, 무장애 여행정보, TAGO 정류소·도착정보의 해당 오퍼레이션을 활용신청한다. 일반 인증키와 **각 오퍼레이션의 요청주소**를 확인한다. 수집은 `DATA_GO_KR_KEY`, 서버 TAGO 기능은 `GABOLLE_TRANSIT_SERVICE_KEY`를 사용한다. 수집기별 한도와 호출 주기를 확인한다. | `bigData/docs/SOURCES.md`, `bigData/config/sources.json`, `bigData/.env.example`, `backend/src/main/resources/application-dev.properties` |
| 기상청 단기예보 | [기상청 API 허브](https://apihub.kma.go.kr)에서 별도 인증키를 발급받아 `GABOLLE_KMA_SERVICE_KEY`에 등록한다. **data.go.kr 키와 호환되지 않는다.** | `backend/src/main/resources/application-dev.properties`의 날씨 설정, `backend/docker-compose.yml` |
| 한국수출입은행 환율 | 환율 API 인증키를 발급받아 `GABOLLE_EXCHANGE_RATE_AUTH_KEY`로 등록한다. | `backend/src/main/resources/application-dev.properties`의 환율 설정 |
| Mapillary 거리 이미지 | [Mapillary 개발자 페이지](https://www.mapillary.com/developer)에서 앱을 등록하고 `MAPILLARY_TOKEN`을 발급받는다. 수집 전 부산 지역 이미지 범위를 확인한다. | `bigData/docs/SOURCES.md`, `bigData/.env.example` |
| Expo EAS 앱 빌드 | Expo 프로젝트 접근 권한과 앱 식별자를 확인하고 `frontend/eas.json`, `frontend/app.json`, `frontend/app.config.js`의 프로필·필수 URL·공개 ID를 EAS 환경에 맞춘다. | 해당 설정 파일, `frontend/Dockerfile` 상단 설명 |
| Gmail SMTP | 메일 발송을 쓰는 환경에서 발송 계정과 앱 비밀번호, 발신 주소를 `GABOLLE_MAIL_*`에 등록한다. 기능 비활성 환경은 `GABOLLE_MAIL_ENABLED=false`다. | `backend/.env.example`, `backend/Jenkinsfile` |

사진 저장은 배포 인프라의 **MinIO**(S3 호환 객체 저장소)를 사용한다. 별도 AWS S3 계정을 전제로 만들지 않는다. 접속·버킷·접근 키는 `infra/personalization/compose.yaml`과 `backend/Jenkinsfile`의 `GABOLLE_S3_*` 전달을 확인한다.

## 계정과 키를 등록할 때 확인할 순서

1. 새 환경의 도메인, 앱 식별자와 소셜 로그인 콜백 URI를 먼저 확정한다.
2. 각 제공자 콘솔에 위 값을 등록하고 승인 여부를 확인한다. 발급 키는 팀의 비밀 저장소 또는 Jenkins Credentials에 넣는다.
3. 웹 공개 변수는 **Docker 빌드 시점**, 앱 공개 변수는 **EAS 빌드 시점**, 서버 비밀값은 **컨테이너 실행 시점**에 주입한다.
4. 데이터 제공 API는 승인된 오퍼레이션과 요청주소를 확인한다. 서비스마다 키 형식과 호출 한도가 다르다.
5. 실제 로그인, 지도, 날씨, 데이터 수집, 메일 발송이 새 환경에서 되는지는 기능별로 확인한다. 이 문서는 그 실행 결과를 주장하지 않는다.

`backend/Jenkinsfile`의 기상청 키 출처 주석은 오래된 내용이다. 현재 서버 설정의 `GABOLLE_KMA_SERVICE_KEY` 설명에 따라 기상청 API 허브에서 발급한다.
