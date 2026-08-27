/**
 * LOCAL ROUTE 페이지 트리 — 단일 진실 공급원(Single Source of Truth)
 *
 * 이 파일 하나가 다음 4가지를 동시에 정의한다.
 *   1) 웹 라우팅        : routes/AppRouter.tsx 가 이 트리를 그대로 사용
 *   2) 앱 라우팅        : expoPath 가 Expo Router 파일 경로와 1:1 대응
 *   3) 담당자 분배      : owners 필드로 4인(DB/INFRA/FE/BE) 작업 분해
 *   4) 마일스톤 관리    : milestone 으로 9/1 웹 MVP · 9/9 앱 범위를 고정
 *
 * 화면을 추가할 때는 반드시 이 트리에 먼저 노드를 추가한 뒤 페이지 파일을 만든다.
 */

/** 노출 플랫폼 */
export type Platform = "WEB" | "APP" | "BOTH";

/** 담당 역할 */
export type Owner = "FE" | "BE" | "DB" | "INFRA";

/** 접근 권한 */
export type AccessLevel =
  | "PUBLIC"        // 누구나
  | "SESSION"       // 익명 세션 토큰 필요
  | "TRIP_VIEWER"   // 해당 여행의 열람자 이상
  | "TRIP_EDITOR"   // 해당 여행의 편집자 이상
  | "TRIP_OWNER"    // 해당 여행의 소유자
  | "ADMIN";        // 운영자 토큰

/** 릴리스 마일스톤 */
export type Milestone =
  | "M1_WEB"        // 2026-09-01 웹 MVP 릴리스 대상
  | "M2_APP"        // 2026-09-09 앱 빌드 대상
  | "M3_REVIEW"     // 스토어 심사 통과에 필요한 필수 화면
  | "M4_LATER";     // 심사 이후 후속

/** 구현 상태 */
export type ImplStatus = "DONE" | "PARTIAL" | "TODO";

export interface RouteNode {
  /** 트리 내 고유 식별자. 분석 이벤트의 screen_id 로도 사용한다. */
  id: string;
  /** react-router 경로 조각(부모 기준 상대 경로). index 라우트는 "" */
  path: string;
  /** Expo Router 기준 파일 경로. 앱 구현 시 이 경로에 파일을 만든다. */
  expoPath: string;
  titleKo: string;
  titleEn: string;
  /** 이 화면이 사용자에게 무엇을 해주는지 한 문장 */
  purpose: string;
  platform: Platform;
  access: AccessLevel;
  /** 이 화면이 호출하는 서버 엔드포인트 */
  apis: string[];
  owners: Owner[];
  milestone: Milestone;
  status: ImplStatus;
  children?: RouteNode[];
}

export const ROUTE_TREE: RouteNode[] = [
  {
    id: "home",
    path: "/",
    expoPath: "app/index.tsx",
    titleKo: "시작 화면",
    titleEn: "Home",
    purpose: "서비스 가치를 한 화면에 보여주고 여행 계획 시작으로 연결한다.",
    platform: "BOTH",
    access: "PUBLIC",
    apis: ["GET /api/places?limit=1", "GET /api/course-categories"],
    owners: ["FE"],
    milestone: "M1_WEB",
    status: "TODO",
  },
  {
    id: "welcome",
    path: "/welcome",
    expoPath: "app/welcome.tsx",
    titleKo: "첫 방문 소개·언어 선택",
    titleEn: "Welcome",
    purpose: "웹 첫 방문 시 서비스 목적·핵심 기능을 소개하고 화면 언어를 먼저 고르게 한다. 진행 중인 일정이 있는 재방문 사용자는 이 화면과 홈을 건너뛰고 자신의 일정으로 바로 들어간다.",
    platform: "WEB",
    access: "PUBLIC",
    apis: [],
    owners: ["FE"],
    milestone: "M1_WEB",
    status: "DONE",
  },
  {
    id: "onboarding",
    path: "/onboarding",
    expoPath: "app/onboarding.tsx",
    titleKo: "앱 온보딩·권한 요청",
    titleEn: "Onboarding",
    purpose: "앱 최초 실행 시 위치·알림 권한을 목적과 함께 설명하고 요청한다. 스토어 심사에서 권한 사유 노출이 필수다.",
    platform: "APP",
    access: "PUBLIC",
    apis: ["POST /api/auth/anonymous"],
    owners: ["FE"],
    milestone: "M3_REVIEW",
    status: "TODO",
  },
  {
    id: "auth.login",
    path: "/login",
    expoPath: "app/login.tsx",
    titleKo: "로그인",
    titleEn: "Sign in",
    purpose: "저장된 여행과 기록을 이어서 이용하기 위한 로그인 화면을 제공한다.",
    platform: "BOTH",
    access: "PUBLIC",
    apis: ["POST /api/auth/login", "GET /api/auth/me", "POST /api/auth/logout"],
    owners: ["FE"],
    milestone: "M1_WEB",
    status: "DONE",
  },
  {
    id: "auth.signup",
    path: "/signup",
    expoPath: "app/signup.tsx",
    titleKo: "회원가입",
    titleEn: "Create account",
    purpose: "여행 일정과 기록을 여러 기기에서 이어가기 위한 계정 생성 화면을 제공한다.",
    platform: "BOTH",
    access: "PUBLIC",
    apis: ["POST /api/auth/register"],
    owners: ["FE"],
    milestone: "M1_WEB",
    status: "DONE",
  },
  {
    id: "plan",
    path: "/plan",
    expoPath: "app/plan/_layout.tsx",
    titleKo: "여행 조건 입력",
    titleEn: "Plan a trip",
    purpose: "4단계 위저드로 일정 계산에 필요한 조건을 모두 수집한다.",
    platform: "BOTH",
    access: "PUBLIC",
    apis: ["GET /api/locations/search", "GET /api/places?category=LODGING", "GET /api/course-categories", "POST /api/trips"],
    owners: ["FE", "BE"],
    milestone: "M1_WEB",
    status: "DONE",
    children: [
      { id: "plan.basic", path: "basic", expoPath: "app/plan/basic.tsx", titleKo: "1단계 · 기본 정보", titleEn: "Basics", purpose: "출발지·기간·인원·예산·이동 시간대를 입력한다.", platform: "BOTH", access: "PUBLIC", apis: ["GET /api/locations/search"], owners: ["FE"], milestone: "M1_WEB", status: "DONE" },
      { id: "plan.taste", path: "taste", expoPath: "app/plan/taste.tsx", titleKo: "2단계 · 취향·코스", titleEn: "Taste & course", purpose: "추천 모드 3종과 10개 코스 카테고리, 취향 태그를 선택한다.", platform: "BOTH", access: "PUBLIC", apis: ["GET /api/course-categories"], owners: ["FE", "BE"], milestone: "M1_WEB", status: "DONE" },
      { id: "plan.conditions", path: "conditions", expoPath: "app/plan/conditions.tsx", titleKo: "3단계 · 이용 조건", titleEn: "Conditions", purpose: "외국인 편의·알레르기·식단·숙소를 입력한다.", platform: "BOTH", access: "PUBLIC", apis: ["GET /api/places?category=LODGING"], owners: ["FE"], milestone: "M1_WEB", status: "DONE" },
      { id: "plan.confirm", path: "confirm", expoPath: "app/plan/confirm.tsx", titleKo: "4단계 · 최종 확인", titleEn: "Confirm", purpose: "입력값을 요약해 보여주고 일정 계산을 실행한다.", platform: "BOTH", access: "PUBLIC", apis: ["POST /api/trips", "POST /api/trips/:id/itineraries:generate"], owners: ["FE", "BE"], milestone: "M1_WEB", status: "DONE" },
    ],
  },
  {
    id: "generating",
    path: "/generating/:tripId",
    expoPath: "app/generating/[tripId].tsx",
    titleKo: "일정 생성 진행",
    titleEn: "Generating",
    purpose: "추천·최적화 job 진행률을 SSE 로 실시간 표시하고 완료 시 결과로 이동한다.",
    platform: "BOTH",
    access: "PUBLIC",
    apis: ["POST /api/trips/:id/itineraries:generate", "GET /api/itinerary-jobs/:jobId", "GET /api/itinerary-jobs/:jobId/events (SSE)"],
    owners: ["FE", "BE"],
    milestone: "M1_WEB",
    status: "DONE",
  },
  {
    id: "trip",
    path: "/trips/:tripId",
    expoPath: "app/trips/[tripId]/_layout.tsx",
    titleKo: "여행 대시보드",
    titleEn: "Trip dashboard",
    purpose: "생성된 일정을 중심으로 5개 서비스 탭을 제공하는 공통 레이아웃.",
    platform: "BOTH",
    access: "TRIP_VIEWER",
    apis: ["GET /api/trips/:id/itinerary", "GET /api/itineraries/:id/collaboration"],
    owners: ["FE", "BE"],
    milestone: "M1_WEB",
    status: "DONE",
    children: [
      { id: "trip.overview", path: "overview", expoPath: "app/trips/[tripId]/overview.tsx", titleKo: "홈 · 여행 요약", titleEn: "Overview", purpose: "총 예산·일정 요약·다음 목적지·조건 배지를 한눈에 보여준다.", platform: "BOTH", access: "TRIP_VIEWER", apis: ["GET /api/trips/:id/itinerary", "GET /api/weather"], owners: ["FE"], milestone: "M1_WEB", status: "DONE" },
      { id: "trip.schedule", path: "schedule", expoPath: "app/trips/[tripId]/schedule/index.tsx", titleKo: "일정 · 동선 편집", titleEn: "Schedule", purpose: "날짜별 타임라인과 지도를 함께 보며 장소를 고정·제외·교체하고 부분 재최적화한다.", platform: "BOTH", access: "TRIP_VIEWER", apis: ["POST /api/itineraries/:id/days/:dayIndex/reoptimize", "GET /api/itineraries/:id/items/:itemId/alternatives", "POST /api/itineraries/:id/undo", "GET /api/routes/directions"], owners: ["FE", "BE"], milestone: "M1_WEB", status: "DONE" },
      { id: "trip.scheduleDay", path: "schedule/:dayIndex", expoPath: "app/trips/[tripId]/schedule/[dayIndex].tsx", titleKo: "일정 · 특정 날짜", titleEn: "Day detail", purpose: "특정 날짜만 확대해 시간대별 방문지와 이동 구간을 본다. 딥링크 대상.", platform: "BOTH", access: "TRIP_VIEWER", apis: ["GET /api/trips/:id/itinerary", "GET /api/routes/directions"], owners: ["FE"], milestone: "M1_WEB", status: "DONE" },
      { id: "trip.scheduleNavigate", path: "schedule/:dayIndex/navigate", expoPath: "app/trips/[tripId]/schedule/[dayIndex]/navigate.tsx", titleKo: "경로 상세 · 네비게이션", titleEn: "Navigation", purpose: "구글맵이 한국에서 지원하지 못하는 영어 턴바이턴 안내와 택시 기사용 한글 목적지 카드를 보여준다(v2 13장, F-NAV-01~03).", platform: "BOTH", access: "TRIP_VIEWER", apis: ["GET /api/routes/directions", "GET /api/routes/taxi-card"], owners: ["FE", "BE"], milestone: "M2_APP", status: "DONE" },
      { id: "trip.map", path: "map", expoPath: "app/trips/[tripId]/map.tsx", titleKo: "지도 전체보기", titleEn: "Map", purpose: "지도를 전체 화면으로 확대해 전체 동선·기념품샵 레이어를 본다.", platform: "BOTH", access: "TRIP_VIEWER", apis: ["GET /api/routes/directions", "GET /api/shops/souvenir"], owners: ["FE"], milestone: "M1_WEB", status: "PARTIAL" },
      { id: "trip.discover", path: "discover", expoPath: "app/trips/[tripId]/discover/index.tsx", titleKo: "로컬 · 축제와 기념품", titleEn: "Discover", purpose: "여행 기간과 겹치는 지역 축제·행사와 근처 기념품샵을 찾아 일정에 추가한다.", platform: "BOTH", access: "TRIP_VIEWER", apis: ["GET /api/festivals", "GET /api/events", "GET /api/shops/souvenir", "POST /api/itineraries/:id/festivals/:placeId"], owners: ["FE", "BE"], milestone: "M1_WEB", status: "DONE" },
      { id: "trip.festivals", path: "discover/festivals", expoPath: "app/trips/[tripId]/discover/festivals.tsx", titleKo: "축제 목록", titleEn: "Festivals", purpose: "기간이 겹치는 축제만 필터링해 상세 정보와 함께 보여준다.", platform: "BOTH", access: "TRIP_VIEWER", apis: ["GET /api/festivals"], owners: ["FE", "BE"], milestone: "M2_APP", status: "PARTIAL" },
      { id: "trip.souvenirs", path: "discover/souvenirs", expoPath: "app/trips/[tripId]/discover/souvenirs.tsx", titleKo: "기념품샵 지도", titleEn: "Souvenir shops", purpose: "마지막 동선 근처 기념품샵을 지도 레이어로 표시한다. 광고가 순위에 개입하지 않는다.", platform: "BOTH", access: "TRIP_VIEWER", apis: ["GET /api/shops/souvenir"], owners: ["FE", "BE"], milestone: "M2_APP", status: "PARTIAL" },
      { id: "trip.together", path: "together", expoPath: "app/trips/[tripId]/together.tsx", titleKo: "함께 · 여행 기록", titleEn: "Together", purpose: "여행자들의 스토리 피드를 보고 팔로우한 기록만 모아 본다.", platform: "BOTH", access: "TRIP_VIEWER", apis: ["GET /api/stories", "POST /api/users/:id/follow", "POST /api/stories/:id/report"], owners: ["FE", "BE"], milestone: "M2_APP", status: "DONE" },
      { id: "trip.prep", path: "prep", expoPath: "app/trips/[tripId]/prep.tsx", titleKo: "여행 준비", titleEn: "Prepare", purpose: "예상 경비 상세, 지역 여행 팁, 필수 서비스 광고, 숙소 예약 제휴, 공유·초대를 모은다.", platform: "BOTH", access: "TRIP_VIEWER", apis: ["GET /api/ads", "POST /api/ads/:id/impressions", "POST /api/ads/:id/clicks", "GET /api/places/:id/booking-options", "POST /api/bookings/start", "POST /api/itineraries/:id/share"], owners: ["FE", "BE"], milestone: "M1_WEB", status: "DONE" },
      { id: "trip.collaborate", path: "collaborate", expoPath: "app/trips/[tripId]/collaborate.tsx", titleKo: "동행자 공동 편집", titleEn: "Collaborate", purpose: "동행자를 편집자·열람자로 초대하고 항목 단위로 함께 편집한다. 낙관적 락으로 충돌을 막는다.", platform: "BOTH", access: "TRIP_EDITOR", apis: ["POST /api/trips/:tripId/members/invite", "GET /api/itineraries/:id/collaboration", "PATCH /api/itineraries/:id/items/:itemId/collaborate"], owners: ["FE", "BE"], milestone: "M2_APP", status: "PARTIAL" },
    ],
  },
  {
    id: "place",
    path: "/places/:placeId",
    expoPath: "app/places/[placeId]/_layout.tsx",
    titleKo: "장소 상세",
    titleEn: "Place detail",
    purpose: "장소의 사진·운영시간·비용·로컬 점수 근거·편의 정보를 보여준다.",
    platform: "BOTH",
    access: "PUBLIC",
    apis: ["GET /api/places/:id", "GET /api/places/:id/image"],
    owners: ["FE", "BE"],
    milestone: "M2_APP",
    status: "TODO",
    children: [
      { id: "place.reviews", path: "reviews", expoPath: "app/places/[placeId]/reviews.tsx", titleKo: "리뷰와 방문 인증", titleEn: "Reviews", purpose: "GPS 방문 인증을 거친 리뷰만 보여주고 새 리뷰를 작성한다.", platform: "BOTH", access: "SESSION", apis: ["GET /api/places/:id/reviews", "POST /api/places/:id/reviews", "POST /api/places/:id/visits/verify"], owners: ["FE", "BE"], milestone: "M2_APP", status: "TODO" },
    ],
  },
  {
    id: "stories",
    path: "/stories",
    expoPath: "app/stories/index.tsx",
    titleKo: "스토리 피드",
    titleEn: "Stories",
    purpose: "여행 종료 후 지연 공개된 장소 기반 스토리를 시간순으로 본다.",
    platform: "BOTH",
    access: "PUBLIC",
    apis: ["GET /api/stories"],
    owners: ["FE", "BE"],
    milestone: "M2_APP",
    status: "DONE",
    children: [
      { id: "stories.new", path: "new", expoPath: "app/stories/new.tsx", titleKo: "스토리 작성", titleEn: "New story", purpose: "일정 장소에 사진과 글을 남기고 나중에도 수정·삭제한다. 위치는 지역 단위로만 공개한다.", platform: "BOTH", access: "SESSION", apis: ["POST /api/stories", "PATCH /api/stories/:id", "DELETE /api/stories/:id"], owners: ["FE", "BE"], milestone: "M2_APP", status: "DONE" },
      { id: "stories.detail", path: ":storyId", expoPath: "app/stories/[storyId].tsx", titleKo: "스토리 상세", titleEn: "Story detail", purpose: "스토리 한 건과 연결된 장소를 보고, 신고하거나 작성자를 팔로우한다.", platform: "BOTH", access: "PUBLIC", apis: ["GET /api/stories", "POST /api/stories/:id/report", "POST /api/users/:id/follow"], owners: ["FE"], milestone: "M2_APP", status: "TODO" },
    ],
  },
  {
    id: "user",
    path: "/users/:userId",
    expoPath: "app/users/[userId].tsx",
    titleKo: "사용자 프로필",
    titleEn: "User profile",
    purpose: "다른 여행자의 로컬 등급과 공개 스토리를 보고 팔로우한다.",
    platform: "BOTH",
    access: "PUBLIC",
    apis: ["GET /api/local-profile", "GET /api/stories", "POST /api/users/:id/follow", "DELETE /api/users/:id/follow"],
    owners: ["FE", "BE"],
    milestone: "M2_APP",
    status: "TODO",
  },
  {
    id: "me",
    path: "/me",
    expoPath: "app/me/_layout.tsx",
    titleKo: "내 정보",
    titleEn: "Me",
    purpose: "익명 세션 기준 내 여행·로컬 등급·설정을 모은다.",
    platform: "BOTH",
    access: "SESSION",
    apis: ["POST /api/auth/anonymous", "GET /api/local-profile"],
    owners: ["FE", "BE"],
    milestone: "M2_APP",
    status: "TODO",
    children: [
      { id: "me.trips", path: "trips", expoPath: "app/me/trips.tsx", titleKo: "내 여행 목록", titleEn: "My trips", purpose: "계정에 저장된 여행을 날짜순으로 보고 다시 열거나 새 여행을 만든다.", platform: "BOTH", access: "SESSION", apis: ["GET /api/trips"], owners: ["FE", "BE"], milestone: "M2_APP", status: "DONE" },
      { id: "me.local", path: "local", expoPath: "app/me/local.tsx", titleKo: "내 로컬 등급", titleEn: "Local status", purpose: "방문 인증·리뷰로 쌓인 로컬 점수와 등급, 다음 등급 조건을 보여준다.", platform: "BOTH", access: "SESSION", apis: ["GET /api/local-profile"], owners: ["FE", "BE"], milestone: "M4_LATER", status: "TODO" },
      { id: "me.settings", path: "settings", expoPath: "app/me/settings.tsx", titleKo: "프로필·여행 설정", titleEn: "Profile & travel settings", purpose: "이름·언어와 일정 생성에 재사용할 식단·알레르기·여행 스타일·이동수단을 관리한다.", platform: "BOTH", access: "SESSION", apis: ["GET /api/auth/me", "PATCH /api/auth/me"], owners: ["FE", "BE"], milestone: "M2_APP", status: "DONE" },
    ],
  },
  {
    id: "share",
    path: "/s/:slug",
    expoPath: "app/s/[slug].tsx",
    titleKo: "공유된 일정 (읽기 전용)",
    titleEn: "Shared itinerary",
    purpose: "30일 유효한 읽기 전용 링크. 출발지·연락처·정확 위치는 제외하고 보여주며, 복제해 내 조건으로 다시 계산할 수 있다.",
    platform: "BOTH",
    access: "PUBLIC",
    apis: ["GET /api/s/:slug", "POST /api/s/:slug/clone"],
    owners: ["FE", "BE"],
    milestone: "M1_WEB",
    status: "DONE",
  },
  {
    id: "invite",
    path: "/invite/:inviteToken",
    expoPath: "app/invite/[inviteToken].tsx",
    titleKo: "동행 초대 수락",
    titleEn: "Accept invite",
    purpose: "7일 유효한 초대 링크를 수락해 편집자·열람자로 여행에 합류한다.",
    platform: "BOTH",
    access: "PUBLIC",
    apis: ["POST /api/collaboration/invites/:inviteToken/accept"],
    owners: ["FE", "BE"],
    milestone: "M1_WEB",
    status: "DONE",
  },
  {
    id: "admin",
    path: "/admin",
    expoPath: "(웹 전용)",
    titleKo: "운영 콘솔",
    titleEn: "Admin",
    purpose: "운영자 토큰으로 UGC 신고 검토, KPI, 광고 캠페인을 관리한다.",
    platform: "WEB",
    access: "ADMIN",
    apis: ["GET /api/moderation/stories"],
    owners: ["FE", "BE", "INFRA"],
    milestone: "M3_REVIEW",
    status: "TODO",
    children: [
      { id: "admin.moderation", path: "moderation", expoPath: "(웹 전용)", titleKo: "신고 검토 큐", titleEn: "Moderation", purpose: "신고된 스토리를 24시간 내 처리한다. UGC 앱의 스토어 심사 필수 요건이다.", platform: "WEB", access: "ADMIN", apis: ["GET /api/moderation/stories", "PATCH /api/moderation/reports/:id"], owners: ["FE", "BE"], milestone: "M3_REVIEW", status: "TODO" },
      { id: "admin.kpis", path: "kpis", expoPath: "(웹 전용)", titleKo: "KPI 대시보드", titleEn: "KPIs", purpose: "이벤트 파이프라인에 쌓인 핵심 지표를 본다.", platform: "WEB", access: "ADMIN", apis: ["GET /api/analytics/kpis", "GET /api/events/catalog"], owners: ["FE", "BE", "DB"], milestone: "M4_LATER", status: "TODO" },
      { id: "admin.ads", path: "ads", expoPath: "(웹 전용)", titleKo: "광고 캠페인 관리", titleEn: "Ad campaigns", purpose: "여행 필수 서비스 업종만 캠페인을 등록한다. 음식점·카페·기념품샵은 API 단계에서 거부된다.", platform: "WEB", access: "ADMIN", apis: ["POST /api/businesses", "POST /api/businesses/:id/campaigns", "GET /api/ads"], owners: ["FE", "BE"], milestone: "M4_LATER", status: "TODO" },
    ],
  },
  {
    id: "legal",
    path: "/legal",
    expoPath: "app/legal/_layout.tsx",
    titleKo: "약관·정책",
    titleEn: "Legal",
    purpose: "스토어 심사와 개인정보 고지에 필요한 문서를 앱·웹에서 동일하게 노출한다.",
    platform: "BOTH",
    access: "PUBLIC",
    apis: [],
    owners: ["FE", "INFRA"],
    milestone: "M3_REVIEW",
    status: "TODO",
    children: [
      { id: "legal.terms", path: "terms", expoPath: "app/legal/terms.tsx", titleKo: "이용약관", titleEn: "Terms", purpose: "서비스 이용 조건. UGC 정책과 금지행위를 포함한다.", platform: "BOTH", access: "PUBLIC", apis: [], owners: ["FE"], milestone: "M3_REVIEW", status: "TODO" },
      { id: "legal.privacy", path: "privacy", expoPath: "app/legal/privacy.tsx", titleKo: "개인정보 처리방침", titleEn: "Privacy policy", purpose: "수집 항목·보존기간·위치 처리·삭제 절차. 스토어 등록 시 공개 URL 로 제출해야 한다.", platform: "BOTH", access: "PUBLIC", apis: [], owners: ["FE", "INFRA"], milestone: "M3_REVIEW", status: "TODO" },
      { id: "legal.openSource", path: "open-source", expoPath: "app/legal/open-source.tsx", titleKo: "오픈소스 고지", titleEn: "Open source", purpose: "사용 중인 오픈소스 라이선스를 고지한다.", platform: "BOTH", access: "PUBLIC", apis: [], owners: ["INFRA"], milestone: "M3_REVIEW", status: "TODO" },
    ],
  },
  {
    id: "notFound",
    path: "*",
    expoPath: "app/+not-found.tsx",
    titleKo: "페이지를 찾을 수 없음",
    titleEn: "Not found",
    purpose: "잘못된 경로에서 홈으로 안전하게 돌아간다.",
    platform: "BOTH",
    access: "PUBLIC",
    apis: [],
    owners: ["FE"],
    milestone: "M1_WEB",
    status: "TODO",
  },
];

/** 트리를 1차원 배열로 펼친다. 문서 생성·이벤트 스키마 검증에 사용한다. */
export function flattenRoutes(nodes: RouteNode[] = ROUTE_TREE, parentPath = ""): (RouteNode & { fullPath: string; depth: number })[] {
  const out: (RouteNode & { fullPath: string; depth: number })[] = [];
  const walk = (list: RouteNode[], base: string, depth: number) => {
    for (const node of list) {
      const fullPath = node.path.startsWith("/") ? node.path : `${base.replace(/\/$/, "")}/${node.path}`;
      out.push({ ...node, fullPath, depth });
      if (node.children) walk(node.children, fullPath, depth + 1);
    }
  };
  walk(nodes, parentPath, 0);
  return out;
}

/** 특정 마일스톤까지 포함되는 화면 수를 센다. 범위 협의용. */
export function countByMilestone() {
  const counts: Record<Milestone, number> = { M1_WEB: 0, M2_APP: 0, M3_REVIEW: 0, M4_LATER: 0 };
  for (const node of flattenRoutes()) counts[node.milestone] += 1;
  return counts;
}
