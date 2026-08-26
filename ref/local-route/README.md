# LOCAL ROUTE

`LOCAL_ROUTE_기획서_v2`의 핵심 가설과 추가 기능을 실행 가능한 통합 MVP로 구현한 프로젝트입니다.

## 실행

```bash
npm install
cd server
npx prisma migrate deploy
npm run seed
npm run seed:translations
npm run seed:commerce
npm run seed:v2
npm run seed:discover-tags
cd ..
npm run dev
```

`npm run dev`는 루트에서 서버(`:4000`)와 웹(`:5173`)을 한 번에 띄웁니다(`scripts/dev.mjs`). 서버가 죽어도 웹은 계속 떠 있으므로 화면 작업 중에는 편합니다. 각 프로세스를 따로 보고 싶으면 두 터미널에서 `npm run dev:server` / `npm run dev:web`을 나눠 실행하세요.

- 웹: `http://localhost:5173`
- API: `http://localhost:4000`
- 화면이 안 뜨거나 API가 막히면 `npm run doctor`로 환경(포트 점유, `.env`, DB 마이그레이션 상태 등)을 먼저 점검하세요.
- 카카오 키가 없으면 지도 대신 안내를 표시하고 일정·목록 기능은 유지합니다.
- 외부 이동시간이나 날씨 원천이 없을 때는 반드시 `추정`으로 표시합니다.

### 장소 사진 검색 설정

관광지·식당에 저장된 사진이 없으면 서버가 네이버 이미지 검색을 우선 조회하고 Google Custom Search를 보조로 사용합니다. 검색 이미지는 원본 파일을 복제하지 않고 썸네일 URL과 출처 링크만 24시간 캐시합니다. 아래 환경변수 중 사용할 제공자의 키를 서버 환경에 설정하세요.

```bash
NAVER_SEARCH_CLIENT_ID=
NAVER_SEARCH_CLIENT_SECRET=
GOOGLE_CUSTOM_SEARCH_KEY=
GOOGLE_CUSTOM_SEARCH_CX=
```

키가 없거나 검색 결과가 없으면 기존 카테고리 플레이스홀더를 표시합니다.

## 구현 범위

- 여행 조건·취향·예산·동행·숙소·반려동물·외국인 편의 입력
- 규칙 기반 추천, 3개 추천 모드, OR-Tools 우선 일정 최적화와 휴리스틱 폴백
- 비동기 생성 job, SSE 진행률, 부분 재최적화, 대체 장소, 되돌리기
- 카카오맵 경로, 반려동물 안전 정보, 다국어 UI와 구조화 번역 출처
- 익명 세션, GPS 방문 인증, 리뷰, 로컬 등급·점수
- 이메일 회원가입·로그인, 프로필(이름·아바타), 트립 히스토리
- 여행 기록(스토리) 작성·수정·삭제와 개인 기록 보관함
- 처음 방문 시 보여주는 Welcome 온보딩(언어 선택, 기능 요약)
- 메뉴판 사진 번역, 양방향 음성 통역, 부산 사투리 카드 등 여행 중 실시간 보조 도구
- 로컬 탭: 축제·야시장·전통시장·액티비티·산책·자연·야경·기념품샵 8종 카테고리
- 이벤트 Outbox/Kafka 선택 연동, KPI, 광고·자연 추천 분리, 예약 제휴

### 여행 중 실시간 보조 도구

- **메뉴판 번역**: 브라우저에서 Tesseract.js로 사진 속 글자를 직접 인식하고, 수작업으로 정리한 한식 메뉴/알레르기 사전으로 번역·배지를 붙입니다. 외부 유료 OCR·번역 API를 쓰지 않으므로 네트워크 없이도 동작하고 비용이 들지 않습니다.
- **음성 통역**: 브라우저 내장 `SpeechRecognition`/`SpeechSynthesis`로 동작하는 한국어 ↔ 외국어 양방향 통역입니다. 자주 쓰는 문장은 프리셋 버튼으로 바로 재생할 수 있습니다.
- **부산 사투리 카드**: 여행 준비 탭에서 자주 쓰는 부산 사투리 표현을 카드로 익히고 확대 보기(플래시카드)로 복습할 수 있습니다.

세 기능 모두 클라이언트 로직 또는 무료 브라우저 API로 동작하는 데모 수준 구현이며, 실제 클라우드 OCR/번역/음성 서비스로 교체하지 않았다는 점을 정직하게 남겨둡니다.

### 로컬 탭 (부산 로컬 탐색)

`/trips/:tripId/discover`의 로컬 탭은 8개 카테고리를 아코디언으로 보여줍니다.

- 축제 · 야시장 · 전통시장(부산 전역 시장으로 확장) · 기념품샵은 기존 장소/이벤트 데이터를 그대로 씁니다.
- 액티비티 · 산책 · 자연 · 야경은 `Place.tasteTags`에 `prisma/ensure-discover-tags.ts`가 얹는 세부 태그(`adventure_activity` / `walk_trail` / `nature_spot` / `nightview`)로 분류합니다. 기존 `activity` 태그는 일정 추천 엔진이 그대로 쓰므로 건드리지 않습니다.
  - 액티비티: 요트·케이블카·서핑·사격장 등 예약형 체험
  - 산책: ~길·~로드·~코스·공원 등 도보 동선
  - 자연: 해수욕장·산 등 자연 경관
  - 야경: 밤에 봐야 의미 있는 명소만 별도 큐레이션. 같은 장소라도 자연·산책 탭에서는 낮 사진(`Place.imageUrl`)을 쓰고, 야경 탭은 전용 `Place.nightImageUrl`(Wikimedia Commons 등 라이선스가 명확한 야경 사진)만 사용합니다. 야경 사진이 없는 곳은 낮 사진을 대신 보여주지 않고 플레이스홀더로 남겨둡니다.
- 캠핑장류는 위 네 태그 어디에도 포함하지 않아 로컬 탭 어디에도 노출되지 않습니다.

### 카카오 식당 리뷰 추천

- 식당 후보에만 카카오 평점, 후기 수, 긍정 후기 비율, 집계 키워드를 반영합니다.
- 후기 수 30건과 평균 4.0점을 사전값으로 둔 베이지안 보정으로 소수 후기의 극단적 평점을 완화합니다.
- 카카오 로컬 API에는 평점·후기 응답 필드가 없으므로 임의 크롤링이나 가짜 점수를 사용하지 않습니다.
- 권한이 확인된 집계 JSON만 `npm run import:kakao-reviews -- <파일.json>`으로 가져옵니다. `source`는 `LICENSED_IMPORT` 또는 `MANUAL_VERIFIED`만 허용합니다.
- 필수 필드: `placeId`, `kakaoPlaceId`, `kakaoPlaceUrl`, `rating`(0~5), `reviewCount`, `collectedAt`, `source`. 선택 필드: `positiveReviewRate`(0~1), `keywords`.

### v2 추가 기능

- 지역축제: 여행 기간과 겹치는 축제만 조회하고 사용자가 선택해 일정에 추가
- 기념품샵: 지도 레이어·마커·간단 상세·마지막 동선용 거리 정보, 광고 순위 개입 금지
- 날씨: 날짜별 온도·강수 배지, 우천·반려동물 폭염 경고, 1시간 캐시와 추정값 표시
- 공동 편집: 소유자·편집자·열람자, 7일 초대 링크, 5초 폴링, 항목 버전 낙관적 락(409), 열람자 수정 차단(403)
- 공유: 30일 읽기 전용 링크, 출발지·연락처·정확 위치 제외, 복제 후 사용자 조건으로 재최적화 job 실행
- 소셜: 장소 기반 스토리, 여행 종료 후 지연 공개 기본값, 지역 단위 위치, JPEG EXIF 제거, 팔로우·시간순 피드
- UGC 운영: 신고 즉시 검토 큐 이동, 관리자 삭제·기각 처리
- 광고: 숙박·렌터카·여행보험·택시·공항이동·반려동물 이동만 등록·노출 허용. 음식점·카페·기념품샵은 API 단계에서 거부

### 페이스 러닝 (여행 중 자가보정 일정)

계획(`ItineraryItem.plannedArrival`/`stayMinutes`)과 실측(`actualArrival`/`actualDeparture`)을 같은 행에 두고, 그 차이로 세 가지를 제공합니다.

1. **지연 예보** — 현재 페이스로 하루가 몇 시에 끝날지, 영업 종료 전에 못 갈 장소가 있는지 계산해 상단에 상시 표시합니다.
2. **시간 앵커 재계산** — 지금 시각·현재 위치를 출발점으로 남은 일정만 다시 짭니다. 이미 다녀온 항목은 보존하고, 복귀 지점은 숙소로 유지합니다(`ScheduleInput.returnLat/returnLng`).
3. **개인 체류 리듬** — 카테고리별 `실측 ÷ 계획` 비율을 학습해 다음 계산의 체류시간에 반영합니다(`ScheduleInput.stayMinutesScale`).

리듬 계수는 카테고리당 최소 2건의 실측이 있을 때만 만들고 `[0.6, 2.0]`으로 가둡니다. 이동시간·체류시간 추정이 섞이므로 예보는 항상 `isEstimate: true`입니다.

## 주요 API

- `POST /api/auth/register` / `POST /api/auth/login` / `GET|PATCH /api/auth/me` / `POST /api/auth/logout`
- `POST /api/itineraries/:id/items/:itemId/progress` — 방문 도착·출발 실측 기록
- `GET /api/itineraries/:id/pace?dayIndex=&now=` — 지연 예보
- `POST /api/itineraries/:id/days/:dayIndex/replan` — 현재 시각·위치 기준 남은 일정 재계산
- `GET /api/trips/:tripId/rhythm` — 학습된 개인 체류 리듬
- `GET /api/festivals?from=&to=` / `POST /api/itineraries/:id/festivals/:placeId`
- `GET /api/shops/souvenir?lat=&lng=&radius=`
- `GET /api/activities|walk-trails|nature-spots|night-views?lat=&lng=&radius=` — 로컬 탭 세부 카테고리
- `GET /api/weather?region=BUSAN&date=`
- `POST /api/itineraries/:id/share` / `GET /api/s/:slug` / `POST /api/s/:slug/clone`
- `POST /api/trips/:tripId/members/invite` / `POST /api/collaboration/invites/:token/accept`
- `GET /api/itineraries/:id/collaboration` / `PATCH /api/itineraries/:id/items/:itemId/collaborate`
- `POST|GET /api/stories`, `PATCH|DELETE /api/stories/:id`, `POST /api/stories/:id/report`
- `POST|DELETE /api/users/:id/follow`
- `GET /api/moderation/stories` / `PATCH /api/moderation/reports/:id`

## 검증

```bash
npm test --workspace server
npm run build --workspace server
npm run build --workspace web
npm audit --audit-level=high
```

현재 날씨는 API 키 없이도 UX를 검증할 수 있는 캐시형 데모 예보이며 `isEstimate: true`로 정직하게 표시됩니다. 운영에서는 기상청 단기예보 수집 작업이 같은 응답 형식으로 캐시를 채워야 합니다. 스토리 이미지는 MVP에서 DB의 정제된 Data URL로 보관하므로, 운영 전 S3 호환 오브젝트 스토리지·리사이즈·CDN으로 교체해야 합니다.
