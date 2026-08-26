import type { ActivitySpot, BookingOption, CourseCategory, CreateTripRequest, EmbeddedRoute, Festival, ItineraryJob, ItineraryOutput, LocationSearchResult, PaceForecast, PlaceAlternative, PlaceImageMatch, PlaceRecord, ReplanResult, RhythmProfile, SharedItinerary, SouvenirShop, SponsoredPlacement, StoryRecord, TaxiCard, WeatherForecast } from "../types";

/**
 * 백엔드가 떠 있지 않으면 fetch 는 TypeError("Failed to fetch") 로 실패한다.
 * 그대로 두면 화면은 정상인데 기능만 조용히 죽어 원인을 찾기 어렵다.
 * 이 모듈 안의 모든 fetch 호출을 감싸 원인을 명확한 에러로 바꾼다.
 */
export class ApiUnavailableError extends Error {
  constructor() {
    super("백엔드 서버(http://localhost:4000)에 연결할 수 없습니다. 프로젝트 루트에서 npm run dev 로 서버와 웹을 함께 실행해 주세요.");
    this.name = "ApiUnavailableError";
  }
}

export class ApiResponseError extends Error {
  constructor(message: string, public readonly status: number) { super(message); this.name = "ApiResponseError"; }
}

const rawFetch = globalThis.fetch.bind(globalThis);
// 아래 fetch 는 이 모듈 스코프에서 전역 fetch 를 가린다(호출부 수정 불필요).
async function fetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  try {
    return await rawFetch(input, init);
  } catch {
    throw new ApiUnavailableError();
  }
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiResponseError(body.message ?? `요청 실패 (${res.status})`, res.status);
  }
  return res.json() as Promise<T>;
}

export async function createTrip(payload: CreateTripRequest): Promise<{ tripId: string }> {
  const auth = await authHeaders(payload.language ?? "KO");
  const res = await fetch("/api/trips", {
    method: "POST",
    headers: { "Content-Type": "application/json", ...auth },
    body: JSON.stringify(payload),
  });
  return handle(res);
}

export interface MyTripSummary { id: string; origin: string; startDate: string; endDate: string; partySize: number; pace: "RELAXED" | "NORMAL" | "PACKED"; status: string; itineraryId: string | null; placeCount: number; storyCount: number; coverImage: string | null; highlights: string[]; createdAt: string }
export async function getMyTrips() { return (await handle<{ trips: MyTripSummary[] }>(await fetch("/api/trips", { headers: await authHeaders() }))).trips; }

export async function generateItinerary(tripId: string, onProgress?: (job: ItineraryJob) => void, mode?: string): Promise<ItineraryOutput> {
  const res = await fetch(`/api/trips/${tripId}/itineraries:generate`, {
    method: "POST",
    headers: { "Idempotency-Key": crypto.randomUUID(), ...(await authHeaders()) },
  });
  const created = await handle<{ jobId: string; streamUrl: string; statusUrl: string }>(res);
  await waitForJob(created, onProgress);
  return getItinerary(tripId, mode);
}

function waitForJob(created: { jobId: string; streamUrl: string; statusUrl: string }, onProgress?: (job: ItineraryJob) => void) {
  return new Promise<void>((resolve, reject) => {
    let settled = false;
    let polling = false;
    const finish = (job: ItineraryJob) => {
      onProgress?.(job);
      if (job.status === "DONE") { settled = true; resolve(); return true; }
      if (job.status === "FAILED") { settled = true; reject(new Error(job.errorMessage ?? "일정 생성에 실패했습니다.")); return true; }
      return false;
    };
    const source = new EventSource(created.streamUrl);
    source.addEventListener("progress", (event) => {
      const job = JSON.parse((event as MessageEvent).data) as ItineraryJob;
      if (finish(job)) source.close();
    });
    source.onerror = () => {
      source.close();
      if (settled || polling) return;
      polling = true;
      const poll = async () => {
        try {
          const job = await handle<ItineraryJob>(await fetch(created.statusUrl));
          if (!finish(job)) window.setTimeout(poll, 2_000);
        } catch (error) { reject(error); }
      };
      void poll();
    };
  });
}

export async function getItinerary(tripId: string, mode?: string): Promise<ItineraryOutput> {
  const res = await fetch(`/api/trips/${tripId}/itinerary${mode ? `?mode=${encodeURIComponent(mode)}` : ""}`, { headers: await authHeaders() });
  return handle(res);
}

export async function updateTripPreferences(
  tripId: string,
  payload: { mustVisitPlaceIds: string[]; excludedPlaceIds: string[] }
): Promise<void> {
  const res = await fetch(`/api/trips/${tripId}/preferences`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", ...(await authHeaders()) },
    body: JSON.stringify(payload),
  });
  await handle(res);
}

export async function getPlaceCount(): Promise<number> {
  const res = await fetch("/api/places");
  const places = await handle<PlaceRecord[]>(res);
  // 서버가 배열이 아닌 형태(에러 객체, 페이지네이션 래퍼 등)를 돌려줘도
  // 화면이 깨지지 않도록 숫자를 보장한다.
  return Array.isArray(places) ? places.length : 0;
}

export async function getCourseCategories(): Promise<CourseCategory[]> {
  return (await handle<{ categories: CourseCategory[] }>(await fetch("/api/course-categories?catalog=full-v2"))).categories;
}

// 위저드의 "가고 싶은 곳" 검색용 - 카카오 전체 검색이 아니라 이 앱의 시드 장소 카탈로그 안에서만 찾는다.
export async function searchPlaces(query: string): Promise<PlaceRecord[]> {
  if (query.trim().length < 2) return [];
  const res = await fetch(`/api/places?search=${encodeURIComponent(query.trim())}`);
  return handle<PlaceRecord[]>(res);
}

export async function getLodgings(): Promise<PlaceRecord[]> {
  const res = await fetch("/api/places?category=LODGING");
  return handle<PlaceRecord[]>(res);
}

export async function reoptimizeDay(itineraryId: string, dayIndex: number, payload: { action: "REMOVE" | "PIN" | "UNPIN" | "REPLACE"; itemId?: string; replacementPlaceId?: string }) {
  return handle(await fetch(`/api/itineraries/${itineraryId}/days/${dayIndex}/reoptimize`, { method: "POST", headers: { "Content-Type": "application/json", ...(await authHeaders()) }, body: JSON.stringify(payload) }));
}

export async function reorderDay(itineraryId: string, dayIndex: number, itemIds: string[]): Promise<{ changedDayIndex: number; warnings: string[] }> {
  return handle(await fetch(`/api/itineraries/${itineraryId}/days/${dayIndex}/reorder`, { method: "PATCH", headers: { "Content-Type": "application/json", ...(await authHeaders()) }, body: JSON.stringify({ itemIds }) }));
}

export async function undoItineraryChange(itineraryId: string) {
  return handle(await fetch(`/api/itineraries/${itineraryId}/undo`, { method: "POST", headers: await authHeaders() }));
}

// ── 페이스 러닝 ────────────────────────────────────────────────────────────────

/** 방문 도착/출발을 기록한다. 이 실측값이 지연 예보와 리듬 학습의 유일한 재료다. */
export async function recordItemProgress(itineraryId: string, itemId: string, payload: { arrivedAt?: string | null; departedAt?: string | null }) {
  return handle<{ id: string; seqOrder: number; plannedArrival: string; stayMinutes: number; actualArrival: string | null; actualDeparture: string | null; actualStayMinutes: number | null }>(
    await fetch(`/api/itineraries/${itineraryId}/items/${itemId}/progress`, { method: "POST", headers: { "Content-Type": "application/json", ...(await authHeaders()) }, body: JSON.stringify(payload) })
  );
}

/** 지연 예보를 가져온다. now 를 생략하면 서버 시각 기준으로 계산한다. */
export async function getPaceForecast(itineraryId: string, dayIndex: number, now?: string): Promise<PaceForecast> {
  const query = new URLSearchParams({ dayIndex: String(dayIndex) });
  if (now) query.set("now", now);
  return handle(await fetch(`/api/itineraries/${itineraryId}/pace?${query}`, { headers: await authHeaders() }));
}

/** 지금 이 시각·이 자리를 출발점으로 남은 일정만 다시 짠다. */
export async function replanRemainingDay(itineraryId: string, dayIndex: number, payload: { currentTime: string; lat: number; lng: number; strategy?: "KEEP_ALL" | "DROP_ONE" | "DEFER_LAST"; useRhythm?: boolean }): Promise<ReplanResult> {
  return handle(await fetch(`/api/itineraries/${itineraryId}/days/${dayIndex}/replan`, { method: "POST", headers: { "Content-Type": "application/json", ...(await authHeaders()) }, body: JSON.stringify(payload) }));
}

/** 이 여행에서 학습된 개인 체류 리듬. */
export async function getRhythmProfile(tripId: string): Promise<RhythmProfile> {
  return handle(await fetch(`/api/trips/${tripId}/rhythm`, { headers: await authHeaders() }));
}

export async function getAlternatives(itineraryId: string, itemId: string): Promise<PlaceAlternative[]> {
  return handle(await fetch(`/api/itineraries/${itineraryId}/items/${itemId}/alternatives`));
}

export async function searchLocations(query: string): Promise<LocationSearchResult[]> {
  return (await handle<{ locations: LocationSearchResult[] }>(await fetch(`/api/locations/search?query=${encodeURIComponent(query)}`))).locations;
}

export async function getPlaceImage(placeId: string): Promise<PlaceImageMatch> {
  return handle(await fetch(`/api/places/${encodeURIComponent(placeId)}/image`));
}

export async function getEmbeddedRoute(params: { startLat: number; startLng: number; endLat: number; endLng: number; mode: "TRANSIT" | "CAR"; lang?: "KO" | "EN" }): Promise<EmbeddedRoute> {
  const query = new URLSearchParams(Object.fromEntries(Object.entries(params).map(([key, value]) => [key, String(value)])));
  return handle(await fetch(`/api/routes/directions?${query}`));
}

// F-NAV-02(v4 6.3장): 한국어를 못 읽는 외국인이 택시 기사에게 보여줄 목적지 카드.
export async function getTaxiCard(placeId: string): Promise<TaxiCard> {
  return handle(await fetch(`/api/routes/taxi-card?placeId=${encodeURIComponent(placeId)}`));
}

function clientSessionId() {
  const key = "local-route-client-session";
  const existing = localStorage.getItem(key);
  if (existing) return existing;
  const created = crypto.randomUUID();
  localStorage.setItem(key, created);
  return created;
}

const tokenKey = "local-route-auth-token";
const userKey = "local-route-auth-user";
const accountKey = "local-route-account";
const obsoleteDemoAccountKey = "local-route-demo-account";

export interface AccountUser {
  id: string; email: string; name: string; emailVerified: boolean;
  locale: "KO" | "EN"; nationality: string | null;
  dietType: "NONE" | "VEGETARIAN" | "VEGAN" | "HALAL"; allergies: string[];
  travelStyle: "RELAXED" | "BALANCED" | "PACKED"; defaultTransport: "TRANSIT" | "CAR" | "WALK";
  avatarImage: string | null; avatarColor: "LAVENDER" | "SKY" | "MINT" | "PEACH" | "CHARCOAL";
}

function saveAccount(token: string, user: AccountUser) {
  localStorage.setItem(tokenKey, token);
  localStorage.setItem(accountKey, JSON.stringify(user));
  localStorage.removeItem(obsoleteDemoAccountKey);
  window.dispatchEvent(new Event("local-route-account-changed"));
}

export function getStoredAccount(): AccountUser | null {
  localStorage.removeItem(obsoleteDemoAccountKey);
  try { const value = localStorage.getItem(accountKey); return value ? JSON.parse(value) as AccountUser : null; } catch { return null; }
}

export async function registerAccount(payload: { name: string; email: string; password: string; locale: "KO" | "EN" }) {
  const res = await fetch("/api/auth/register", { method: "POST", headers: { "Content-Type": "application/json", ...(await authHeaders(payload.locale)) }, body: JSON.stringify(payload) });
  const result = await handle<{ token: string; user: AccountUser }>(res);
  saveAccount(result.token, result.user);
  return result.user;
}

export async function loginAccount(payload: { email: string; password: string }) {
  const res = await fetch("/api/auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
  const result = await handle<{ token: string; user: AccountUser }>(res);
  saveAccount(result.token, result.user);
  return result.user;
}

export async function logoutAccount() {
  const token = localStorage.getItem(tokenKey);
  if (token) await fetch("/api/auth/logout", { method: "POST", headers: { Authorization: `Bearer ${token}` } });
  localStorage.removeItem(tokenKey);
  localStorage.removeItem(userKey);
  localStorage.removeItem(accountKey);
  localStorage.removeItem(obsoleteDemoAccountKey);
  window.dispatchEvent(new Event("local-route-account-changed"));
}

export async function updateAccountProfile(payload: Pick<AccountUser, "name" | "locale" | "nationality" | "dietType" | "allergies" | "travelStyle" | "defaultTransport" | "avatarImage" | "avatarColor">) {
  const token = localStorage.getItem(tokenKey);
  if (!token) throw new Error("로그인이 필요합니다.");
  const result = await handle<{ user: AccountUser }>(await fetch("/api/auth/me", { method: "PATCH", headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` }, body: JSON.stringify(payload) }));
  localStorage.setItem(accountKey, JSON.stringify(result.user));
  window.dispatchEvent(new Event("local-route-account-changed"));
  return result.user;
}

export async function authHeaders(locale: "KO" | "EN" = "KO"): Promise<Record<string, string>> {
  let token = localStorage.getItem(tokenKey);
  if (!token) {
    const created = await handle<{ token: string; id: string }>(await fetch("/api/auth/anonymous", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ locale }) }));
    token = created.token; localStorage.setItem(tokenKey, token); localStorage.setItem(userKey, created.id);
  }
  return { Authorization: `Bearer ${token}` };
}
export function currentUserId() { return localStorage.getItem(userKey); }

export async function getFestivals(from: string, to: string): Promise<Festival[]> { return (await handle<{ festivals: Festival[] }>(await fetch(`/api/festivals?from=${from}&to=${to}&region=BUSAN`))).festivals; }
export async function getEvents(from: string, to: string): Promise<Festival[]> { return (await handle<{ events: Festival[] }>(await fetch(`/api/events?from=${from}&to=${to}&region=BUSAN`))).events; }
export async function addFestival(itineraryId: string, placeId: string) { return handle(await fetch(`/api/itineraries/${itineraryId}/festivals/${placeId}`, { method: "POST", headers: await authHeaders() })); }
export async function getSouvenirShops(lat: number, lng: number): Promise<SouvenirShop[]> { return handle(await fetch(`/api/shops/souvenir?lat=${lat}&lng=${lng}&radius=12000`)); }
export async function getActivities(lat: number, lng: number): Promise<ActivitySpot[]> { return handle(await fetch(`/api/activities?lat=${lat}&lng=${lng}&radius=15000`)); }
export async function getWalkTrails(lat: number, lng: number): Promise<ActivitySpot[]> { return handle(await fetch(`/api/walk-trails?lat=${lat}&lng=${lng}&radius=15000`)); }
export async function getNatureSpots(lat: number, lng: number): Promise<ActivitySpot[]> { return handle(await fetch(`/api/nature-spots?lat=${lat}&lng=${lng}&radius=15000`)); }
export async function getNightViews(lat: number, lng: number): Promise<ActivitySpot[]> { return handle(await fetch(`/api/night-views?lat=${lat}&lng=${lng}&radius=15000`)); }
export async function getWeather(date: string): Promise<WeatherForecast> { return handle(await fetch(`/api/weather?region=BUSAN&date=${date}`)); }
export async function createShare(itineraryId: string) { return handle<{ shareSlug: string; url: string; expiresAt: string }>(await fetch(`/api/itineraries/${itineraryId}/share`, { method: "POST", headers: { "Content-Type": "application/json", ...(await authHeaders()) }, body: JSON.stringify({ visibility: "LINK", expiresInDays: 30 }) })); }
export async function getSharedItinerary(slug: string): Promise<SharedItinerary> { return handle(await fetch(`/api/s/${slug}`)); }
export async function inviteCompanion(tripId: string, role: "EDITOR" | "VIEWER") { return handle<{ inviteUrl: string; expiresAt: string }>(await fetch(`/api/trips/${tripId}/members/invite`, { method: "POST", headers: { "Content-Type": "application/json", ...(await authHeaders()) }, body: JSON.stringify({ role, expiresInDays: 7 }) })); }
export async function acceptInvite(inviteToken: string) { return handle<{ tripId: string; role: string }>(await fetch(`/api/collaboration/invites/${inviteToken}/accept`, { method: "POST", headers: await authHeaders() })); }
export async function getCollaboration(itineraryId: string) { return handle<{ version: number; myRole: string; members: unknown[] }>(await fetch(`/api/itineraries/${itineraryId}/collaboration`, { headers: await authHeaders() })); }
export async function createStory(payload: { placeId: string; itineraryItemId?: string; content: string; images: string[]; visibility: string; publishMode: "NOW" | "AFTER_TRIP" }) { return handle<{ storyId: string; delayed: boolean; exifRemoved: boolean }>(await fetch("/api/stories", { method: "POST", headers: { "Content-Type": "application/json", ...(await authHeaders()) }, body: JSON.stringify(payload) })); }
export async function getStories(options: boolean | { mine?: boolean; following?: boolean } = false): Promise<StoryRecord[]> { const params = new URLSearchParams(); if (typeof options === "boolean" ? options : options.mine) params.set("mine", "true"); if (typeof options !== "boolean" && options.following) params.set("following", "true"); return handle(await fetch(`/api/stories${params.size ? `?${params}` : ""}`, { headers: await authHeaders() })); }
export async function updateStory(id: string, payload: { content: string; images: string[]; visibility: string; publishNow?: boolean }) { return handle(await fetch(`/api/stories/${id}`, { method: "PATCH", headers: { "Content-Type": "application/json", ...(await authHeaders()) }, body: JSON.stringify(payload) })); }
export async function deleteStory(id: string) { const response = await fetch(`/api/stories/${id}`, { method: "DELETE", headers: await authHeaders() }); if (!response.ok) await handle(response); }
export async function reportStory(id: string, reason = "PRIVACY") { return handle(await fetch(`/api/stories/${id}/report`, { method: "POST", headers: { "Content-Type": "application/json", ...(await authHeaders()) }, body: JSON.stringify({ reason }) })); }
export async function followUser(id: string, following: boolean) { return handle(await fetch(`/api/users/${id}/follow`, { method: following ? "DELETE" : "POST", headers: await authHeaders() })); }

export async function getSponsoredPlacements(context: { mode: string; language: string }): Promise<SponsoredPlacement[]> {
  const query = new URLSearchParams({ mode: context.mode, language: context.language });
  return handle(await fetch(`/api/ads?${query}`));
}

export async function trackAd(campaignId: string, eventType: "impressions" | "clicks") {
  return handle(await fetch(`/api/ads/${campaignId}/${eventType}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ eventId: crypto.randomUUID(), clientSessionId: clientSessionId() }) }));
}

export async function getBookingOptions(placeId: string): Promise<BookingOption[]> {
  return handle(await fetch(`/api/places/${placeId}/booking-options`));
}

export async function startBooking(partnerId: string, tripId: string) {
  return handle<{ bookingId: string; bookingUrl: string; disclosure: string }>(await fetch("/api/bookings/start", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ partnerId, tripId, eventId: crypto.randomUUID(), clientSessionId: clientSessionId() }) }));
}
