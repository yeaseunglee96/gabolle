// 루트 포스트 — 내 여행 경로(하루 또는 전체)를 피드 글로 올리고, 남의 루트를 따라 여행을 만든다 (S15P21E201-2027).
// 「이 장소가 들어간 루트」(S15P21E201-2028)도 여기서 부른다.
//
// 🔴 숙소는 서버가 빼고 저장한다(숙소 정류장·숙소로 드나드는 선·출발점). 앱은 아무것도 거르지 않는다 —
//    거르는 곳이 둘이면 한쪽만 고쳐지는 날이 온다.
import { ApiClientError, apiRequest } from '@/api/client';
import { createTripForSelfPlan } from '@/api/tripApi';
import { addItineraryItem } from '@/plan/itinerary';
import { EMPTY_PLAN } from '@/plan/PlanProvider';

export type RoutePostStop = {
  seq: number;
  dayIndex: number;
  placeId: string;
  name: string;
  nameEn?: string | null;
  lat: number;
  lng: number;
  photoUrl?: string | null;
  stayMinutes?: number | null;
};

export type RoutePostDto = {
  routePostId: string;
  author: { userId: string; displayName: string; avatarUrl?: string | null };
  title: string;
  body: string;
  /** 0부터 센 하루. null 이면 일정 전체. */
  dayIndex: number | null;
  dayCount: number;
  durationMin: number | null;
  distanceM: number | null;
  /** [lat, lng] 점들 — 지도 썸네일 선. */
  path: Array<[number, number]>;
  stops: RoutePostStop[];
  likeCount: number;
  saveCount: number;
  copyCount: number;
  likedByMe: boolean;
  savedByMe: boolean;
  mine: boolean;
  createdAt: string;
};

export type RouteSort = 'POPULAR' | 'RECENT';

type Failure = { state: 'error' | 'offline' | 'not-found'; message: string };

function failure(error: unknown): Failure {
  if (error instanceof ApiClientError && error.code === 'NETWORK_ERROR') return { state: 'offline', message: error.message };
  if (error instanceof ApiClientError && error.status === 404) return { state: 'not-found', message: error.message };
  return { state: 'error', message: error instanceof Error ? error.message : '요청을 처리하지 못했어요.' };
}

export const ROUTE_POSTS_KEY = ['route-posts'] as const;
export const routeFeedKey = (sort: RouteSort, signedIn: boolean) => [...ROUTE_POSTS_KEY, 'feed', sort, signedIn] as const;
export const placeRoutesKey = (placeId: string, signedIn: boolean) => [...ROUTE_POSTS_KEY, 'place', placeId, signedIn] as const;
export const routePostKey = (id: string, signedIn: boolean) => [...ROUTE_POSTS_KEY, 'one', id, signedIn] as const;

export type RouteFeedResult = { state: 'success'; items: RoutePostDto[]; nextCursor: string | null } | Failure;

export async function loadRouteFeed(input: { sort: RouteSort; cursor?: string | null; limit?: number; accessToken: string | null }): Promise<RouteFeedResult> {
  try {
    const params = new URLSearchParams({ sort: input.sort, limit: String(input.limit ?? 20) });
    if (input.cursor) params.set('cursor', input.cursor);
    const dto = await apiRequest<{ items: RoutePostDto[]; nextCursor: string | null }>(`/api/v1/route-posts?${params.toString()}`, { accessToken: input.accessToken });
    return { state: 'success', items: dto.items, nextCursor: dto.nextCursor };
  } catch (error) {
    return failure(error);
  }
}

export type RouteListResult = { state: 'success'; items: RoutePostDto[] } | Failure;

/** 이 장소가 들어간 공개 루트 — 인기순, 기본 5개(S15P21E201-2028). */
export async function loadPlaceRoutes(placeId: string, accessToken: string | null, limit = 5): Promise<RouteListResult> {
  try {
    const items = await apiRequest<RoutePostDto[]>(`/api/v1/places/${encodeURIComponent(placeId)}/route-posts?limit=${limit}`, { accessToken });
    return { state: 'success', items };
  } catch (error) {
    return failure(error);
  }
}

export type RouteOneResult = { state: 'success'; post: RoutePostDto } | Failure;

export async function getRoutePost(id: string, accessToken: string | null): Promise<RouteOneResult> {
  try {
    return { state: 'success', post: await apiRequest<RoutePostDto>(`/api/v1/route-posts/${encodeURIComponent(id)}`, { accessToken }) };
  } catch (error) {
    return failure(error);
  }
}

export async function publishRoutePost(input: { itineraryId: string; dayIndex: number | null; title: string; body: string; accessToken: string | null }): Promise<RouteOneResult> {
  try {
    const post = await apiRequest<RoutePostDto>('/api/v1/route-posts', {
      method: 'POST',
      accessToken: input.accessToken,
      body: { itineraryId: input.itineraryId, dayIndex: input.dayIndex, title: input.title, body: input.body },
    });
    return { state: 'success', post };
  } catch (error) {
    if (error instanceof ApiClientError && error.code === 'TOO_FEW_STOPS') return { state: 'error', message: 'TOO_FEW_STOPS' };
    return failure(error);
  }
}

export async function deleteRoutePost(id: string, accessToken: string | null): Promise<{ state: 'success' } | Failure> {
  try {
    await apiRequest<void>(`/api/v1/route-posts/${encodeURIComponent(id)}`, { method: 'DELETE', accessToken });
    return { state: 'success' };
  } catch (error) {
    return failure(error);
  }
}

export async function setRouteReaction(id: string, kind: 'like' | 'save', on: boolean, accessToken: string | null): Promise<RouteOneResult> {
  try {
    const post = await apiRequest<RoutePostDto>(`/api/v1/route-posts/${encodeURIComponent(id)}/${kind}`, { method: on ? 'PUT' : 'DELETE', accessToken });
    return { state: 'success', post };
  } catch (error) {
    return failure(error);
  }
}

/** 날짜 하나를 'YYYY-MM-DD' 로. 기기 시간대 기준 — 여행 날짜 칸과 같은 방식. */
function ymd(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** 이 루트를 몇 날로 옮기나 — 루트의 날 순서를 0, 1, 2… 로 다시 매긴다(하루짜리 루트의 dayIndex 가 2 여도 첫날로). */
export function copyPlan(post: Pick<RoutePostDto, 'stops'>): Array<{ dayIndex: number; placeIds: string[] }> {
  const days = [...new Set(post.stops.map((stop) => stop.dayIndex))].sort((a, b) => a - b);
  return days.map((day, index) => {
    const seen = new Set<string>();
    // 같은 날 같은 곳이 두 번이면 서버가 409(이미 담긴 장소)로 막는다 — 처음 것만 넣는다.
    const placeIds = post.stops.filter((stop) => stop.dayIndex === day).sort((a, b) => a.seq - b.seq)
      .map((stop) => stop.placeId).filter((id) => (seen.has(id) ? false : (seen.add(id), true)));
    return { dayIndex: index, placeIds };
  });
}

export type CopyResult = { state: 'success'; itineraryId: string; added: number; skipped: number } | Failure;

/**
 * 「이 루트로 여행 만들기」 — 직접 짜기와 같은 길(S15P21E201-2018): 여행 + 빈 일정을 만들고, 들른 곳을 그날 그 순서대로
 * 하나씩 더한다(자리 지정 — S15P21E201-2017 이 구간·시각까지 매긴다). 날짜는 내일부터 루트의 날 수만큼.
 *
 * 한 곳이 실패해도 멈추지 않는다 — 그 곳만 건너뛰고 몇 곳이 빠졌는지 돌려준다(지금 없는 장소 등).
 */
export async function copyRouteIntoNewTrip(post: RoutePostDto, accessToken: string | null, today = new Date()): Promise<CopyResult> {
  const plan = copyPlan(post);
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1);
  const end = new Date(start.getFullYear(), start.getMonth(), start.getDate() + Math.max(plan.length, 1) - 1);
  try {
    const { itineraryId } = await createTripForSelfPlan({
      ...EMPTY_PLAN,
      startDate: ymd(start),
      endDate: ymd(end),
      // 같은 루트를 두 번 눌러도 새 여행이 되게 — 멱등 키에 시각을 섞는다.
      attemptId: `route-${post.routePostId}-${Date.now()}`,
    }, accessToken);
    const loaded = await apiRequest<{ version: number }>(`/api/v1/itineraries/${encodeURIComponent(itineraryId)}`, { accessToken });
    let version = loaded.version;
    let added = 0;
    let skipped = 0;
    for (const day of plan) {
      let position = 1;
      for (const placeId of day.placeIds) {
        const result = await addItineraryItem({ itineraryId, placeId, dayIndex: day.dayIndex, baseVersion: version, position, accessToken });
        if (result.state === 'success') { version = result.itinerary.version; position += 1; added += 1; } else { skipped += 1; }
      }
    }
    // 따라 했다고 센다 — 인기 점수에 들어간다. 실패해도 여행은 이미 만들어졌으니 묻지 않는다.
    void apiRequest(`/api/v1/route-posts/${encodeURIComponent(post.routePostId)}/copies`, { method: 'POST', accessToken }).catch(() => undefined);
    return { state: 'success', itineraryId, added, skipped };
  } catch (error) {
    return failure(error);
  }
}

/** 「2시간 30분」 · 「3.2km」 — 모르면 null(지어내지 않는다). */
export function durationParts(min: number | null | undefined): { h: number; m: number } | null {
  if (min == null || min <= 0) return null;
  return { h: Math.floor(min / 60), m: min % 60 };
}

export function distanceKm(meters: number | null | undefined): string | null {
  if (meters == null || meters <= 0) return null;
  return (meters / 1000).toFixed(meters < 10_000 ? 1 : 0);
}
