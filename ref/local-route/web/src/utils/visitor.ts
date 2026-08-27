const WELCOME_SEEN_KEY = "local-route-onboarding-seen";
const LAST_TRIP_KEY = "local-route-last-trip-id";

export function hasSeenWelcome(): boolean {
  try {
    return localStorage.getItem(WELCOME_SEEN_KEY) === "1";
  } catch {
    return false;
  }
}

export function markWelcomeSeen() {
  try {
    localStorage.setItem(WELCOME_SEEN_KEY, "1");
  } catch {
    /* 프라이빗 모드 등에서는 세션 동안만 유지 — 매번 웰컴 화면이 다시 보이는 정도의 저하는 허용 */
  }
}

export function getLastVisitedTripId(): string | null {
  try {
    return localStorage.getItem(LAST_TRIP_KEY);
  } catch {
    return null;
  }
}

export function setLastVisitedTripId(tripId: string) {
  try {
    localStorage.setItem(LAST_TRIP_KEY, tripId);
  } catch {
    /* 위와 동일한 이유로 무시 */
  }
}
