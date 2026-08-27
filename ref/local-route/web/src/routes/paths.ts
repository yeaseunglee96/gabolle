/**
 * 타입 안전 경로 빌더.
 * 화면 이동은 문자열을 직접 쓰지 말고 반드시 이 헬퍼를 거친다.
 * 경로가 바뀌어도 이 파일만 고치면 되고, 앱(Expo Router)도 같은 이름을 재사용한다.
 */
export const paths = {
  home: () => "/",
  welcome: () => "/welcome",
  onboarding: () => "/onboarding",
  login: () => "/login",
  signup: () => "/signup",

  plan: (step: "basic" | "taste" | "confirm" = "basic") => `/plan/${step}`,
  generating: (tripId: string) => `/generating/${tripId}`,

  trip: (tripId: string) => `/trips/${tripId}`,
  tripOverview: (tripId: string) => `/trips/${tripId}/overview`,
  tripSchedule: (tripId: string) => `/trips/${tripId}/schedule`,
  tripScheduleDay: (tripId: string, dayIndex: number) => `/trips/${tripId}/schedule/${dayIndex}`,
  tripScheduleNavigate: (tripId: string, dayIndex: number) => `/trips/${tripId}/schedule/${dayIndex}/navigate`,
  tripMap: (tripId: string) => `/trips/${tripId}/map`,
  tripDiscover: (tripId: string) => `/trips/${tripId}/discover`,
  tripFestivals: (tripId: string) => `/trips/${tripId}/discover/festivals`,
  tripSouvenirs: (tripId: string) => `/trips/${tripId}/discover/souvenirs`,
  tripTogether: (tripId: string) => `/trips/${tripId}/together`,
  tripPrep: (tripId: string) => `/trips/${tripId}/prep`,
  tripCollaborate: (tripId: string) => `/trips/${tripId}/collaborate`,

  place: (placeId: string) => `/places/${placeId}`,
  placeReviews: (placeId: string) => `/places/${placeId}/reviews`,

  stories: () => "/stories",
  storyNew: () => "/stories/new",
  story: (storyId: string) => `/stories/${storyId}`,
  user: (userId: string) => `/users/${userId}`,

  me: () => "/me",
  myTrips: () => "/me/trips",
  myLocal: () => "/me/local",
  settings: () => "/me/settings",

  share: (slug: string) => `/s/${slug}`,
  invite: (token: string) => `/invite/${token}`,

  adminModeration: () => "/admin/moderation",
  adminKpis: () => "/admin/kpis",
  adminAds: () => "/admin/ads",

  terms: () => "/legal/terms",
  privacy: () => "/legal/privacy",
  openSource: () => "/legal/open-source",
} as const;

/** 사이드바 탭 id ↔ 경로 매핑. Sidebar 는 이 표만 알면 된다. */
export const TAB_TO_PATH = {
  home: paths.tripOverview,
  schedule: paths.tripSchedule,
  discover: paths.tripDiscover,
  together: paths.tripTogether,
  prep: paths.tripPrep,
} as const;

/** 현재 경로에서 활성 탭을 역산한다. */
export function tabFromPathname(pathname: string): keyof typeof TAB_TO_PATH {
  if (pathname.includes("/schedule") || pathname.includes("/map")) return "schedule";
  if (pathname.includes("/discover")) return "discover";
  if (pathname.includes("/together")) return "together";
  if (pathname.includes("/prep") || pathname.includes("/collaborate")) return "prep";
  return "home";
}
