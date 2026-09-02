# GABOLLE Backend

GABOLLE(가볼래) Spring Boot 백엔드입니다.

## 현재 생성 기준

- Spring Boot 4.0.0
- Java 17
- Gradle Wrapper
- PostgreSQL + Spring Data JPA
- Flyway migration
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

현재 기본 profile은 PostgreSQL 없이 서버와 health endpoint를 실행할 수 있는 뼈대 상태입니다. DB를 사용하는 기능을 붙일 때 로컬·배포 profile에 PostgreSQL 연결과 migration 실행 조건을 추가합니다.

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

## 참고 기준

- 공개 API: 저장소 밖 개인 컨텍스트의 GABOLLE API 명세
- 데이터 모델: 저장소 밖 개인 컨텍스트의 GABOLLE ERD 초안
- 기준 브랜치: `back/dev`
- 초기 Jira: `S15P21E201-243` — Spring Boot 프로젝트 뼈대와 상태 확인 주소
