/**
 * 「어디로 가세요?」 — 주변 버스를 목적지부터 시작하게 하는 재료. S15P21E201-1834.
 *
 * 🔴 버스는 목적지가 있어야 탄다. 주변 버스 화면은 「근처에 어떤 버스가 몇 분 뒤 오는지」만 보여줘서, 어느 버스가 내가 갈
 *    곳으로 가는지는 사용자가 알아내야 했다. 여기서 목적지(오늘 일정의 다음 장소·숙소·검색)를 정하고, 서버 길찾기가 준
 *    대중교통 첫 탈것과 주변 정류장의 실시간 도착을 맞물린다.
 *
 * 화면(app/field/transit.tsx)이 부르는 순수 함수와 불러오기 함수만 둔다.
 */
import { searchPlacesByName } from '@/discovery/places';
import { transitStepKind, parseTransitGuidance } from '@/field/routeLegs';
import type { BusStop } from '@/field/busArrivals';
import type { RouteDirections } from '@/map/routeDirections';
import { loadItinerary, type ItineraryDto } from '@/plan/itinerary';
import { searchOrigins } from '@/plan/origins';
import { localDateKey } from '@/plan/tripProgress';
import { loadTrips, resolveTripItinerary, type TripSummaryDto } from '@/trip/trips';

export type DestinationKind = 'next' | 'lodging' | 'search';

export type Destination = {
  key: string;
  kind: DestinationKind;
  /** 화면에 적을 이름. 숙소 이름을 모르면 null — 화면이 「숙소」라고 적는다. */
  name: string | null;
  address: string | null;
  latitude: number;
  longitude: number;
  placeId: string | null;
  /** 다음 일정이면 그 장소의 시작 시각(「14:41」) — 늦을지 가늠하게. */
  startsAt?: string;
};

export type TodayTargets = { next: Destination | null; lodging: Destination | null };

const finite = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value);

/** 오늘 날짜가 여행 기간 안에 드는 여행. 여럿이면 먼저 시작한 것. */
export function pickTodayTrip(trips: readonly TripSummaryDto[], today: string): TripSummaryDto | null {
  const hits = trips.filter((trip) => trip.startDate && trip.endDate && trip.startDate <= today && today <= trip.endDate);
  hits.sort((a, b) => (a.startDate ?? '').localeCompare(b.startDate ?? ''));
  return hits[0] ?? null;
}

/**
 * 오늘 일정에서 「다음 장소」와 「숙소」.
 * 다음 장소 = 아직 시작 시각이 안 된 첫 곳. 🔴 다 지났으면 null — 지난 곳을 「다음」이라고 부르지 않는다.
 * 숙소 = 그날 끝에 돌아가는 곳이 숙소일 때만(returnLeg.kind === 'LODGING'). 마지막 날의 출발지는 숙소가 아니다.
 */
export function todayTargets(itinerary: Pick<ItineraryDto, 'days'>, today: string, now: Date): TodayTargets {
  const day = itinerary.days.find((each) => each.date === today);
  if (!day) return { next: null, lodging: null };
  const upcoming = day.items.find((item) => finite(item.lat) && finite(item.lng) && Date.parse(item.startsAt) > now.getTime());
  const next: Destination | null = upcoming && finite(upcoming.lat) && finite(upcoming.lng)
    ? { key: `next:${upcoming.id}`, kind: 'next', name: upcoming.title, address: null, latitude: upcoming.lat, longitude: upcoming.lng, placeId: upcoming.placeId, startsAt: upcoming.startsAt }
    : null;
  const back = day.returnLeg;
  const lodging: Destination | null = back && back.kind === 'LODGING' && finite(back.lat) && finite(back.lng)
    ? { key: 'lodging', kind: 'lodging', name: back.label, address: null, latitude: back.lat, longitude: back.lng, placeId: null }
    : null;
  return { next, lodging };
}

/**
 * 오늘 여행이 있으면 다음 장소·숙소를 불러온다. 로그인 전이거나 오늘 여행이 없거나 못 불러오면 null —
 * 화면은 칩 없이 검색만 보여준다. 실패를 오류로 띄우지 않는다: 칩은 지름길일 뿐이다.
 */
export async function loadTodayTargets(accessToken: string | null, now: Date = new Date()): Promise<TodayTargets | null> {
  if (!accessToken) return null;
  const trips = await loadTrips(accessToken);
  if (trips.state !== 'success') return null;
  const today = localDateKey(now);
  const trip = pickTodayTrip(trips.trips, today);
  if (!trip) return null;
  const choice = await resolveTripItinerary(trip, accessToken);
  if (choice.state !== 'open') return null;
  const loaded = await loadItinerary(choice.itineraryId, accessToken);
  if (loaded.state !== 'success') return null;
  const targets = todayTargets(loaded.itinerary, today, now);
  return targets.next || targets.lodging ? targets : null;
}

const squash = (value: string) => value.replace(/[\s.·]/g, '');

/**
 * 장소 이름으로 목적지를 찾는다 — 우리 장소 먼저, 그다음 카카오 장소 검색(좌표가 있는 것만). 같은 이름은 한 번만.
 * 둘 중 하나가 실패해도 나머지로 답한다. 둘 다 실패하면 빈 목록.
 */
export async function searchDestinations(query: string, accessToken: string | null, signal?: AbortSignal): Promise<Destination[]> {
  const [ours, kakao] = await Promise.allSettled([searchPlacesByName(query, signal), searchOrigins(query, accessToken, signal)]);
  const out: Destination[] = [];
  const seen = new Set<string>();
  const push = (item: Destination) => {
    const id = squash(item.name ?? '');
    if (!id || seen.has(id)) return;
    seen.add(id);
    out.push(item);
  };
  if (ours.status === 'fulfilled') {
    for (const place of ours.value) {
      if (!finite(place.lat) || !finite(place.lng)) continue;
      push({ key: `p:${place.placeId}`, kind: 'search', name: place.nameKo, address: place.address || null, latitude: place.lat, longitude: place.lng, placeId: place.placeId });
    }
  }
  if (kakao.status === 'fulfilled' && kakao.value.state === 'success') {
    for (const place of kakao.value.items) {
      if (!finite(place.lat) || !finite(place.lng)) continue;
      push({ key: `k:${place.externalId}`, kind: 'search', name: place.name, address: place.address || null, latitude: place.lat, longitude: place.lng, placeId: null });
    }
  }
  return out.slice(0, 8);
}

export type RideSummary = {
  kind: 'bus' | 'subway';
  /** 「1003」「88(A)」「2호선」 — 버스는 끝의 「번」을 뗀다(실시간 도착의 routeNo 와 맞추려고). */
  line: string;
  board: string | null;
  alight: string | null;
  /** 갈아타는 횟수. 모르면 null. */
  transfers: number | null;
  /** 타는 정류장에 그 버스가 오기까지 남은 초. 주변 정류장 도착 정보와 맞물릴 때만 — 못 맞물리면 null(지어내지 않는다). */
  liveSeconds: number | null;
};

const lineOf = (name: string) => name.replace(/번$/, '').trim();

/** 대중교통 길에서 처음 타는 것. 걷기만 있으면 null. */
export function rideSummary(directions: RouteDirections, stops: readonly BusStop[]): RideSummary | null {
  if (directions.mode !== 'TRANSIT') return null;
  const first = directions.steps.find((step) => transitStepKind(step) !== 'walk');
  if (!first) return null;
  const kind = transitStepKind(first) === 'subway' ? 'subway' : 'bus';
  const parsed = parseTransitGuidance(first.guidance);
  const board = parsed?.kind === 'ride' ? parsed.from : null;
  const alight = parsed?.kind === 'ride' ? parsed.to : null;
  const line = lineOf(first.name);
  let liveSeconds: number | null = null;
  if (kind === 'bus' && board) {
    const want = squash(board);
    for (const stop of stops) {
      if (squash(stop.nodeName) !== want) continue;
      for (const arrival of stop.arrivals) {
        if (arrival.routeNo !== line || arrival.arrivalSeconds == null) continue;
        if (liveSeconds === null || arrival.arrivalSeconds < liveSeconds) liveSeconds = arrival.arrivalSeconds;
      }
    }
  }
  return { kind, line, board, alight, transfers: directions.transferCount, liveSeconds };
}

/** 이 길에서 타는 버스 번호들 — 주변 정류장 목록에서 강조한다. */
export function busLinesOf(directions: RouteDirections | null): Set<string> {
  const lines = new Set<string>();
  if (!directions || directions.mode !== 'TRANSIT') return lines;
  for (const step of directions.steps) if (transitStepKind(step) === 'bus') lines.add(lineOf(step.name));
  return lines;
}
