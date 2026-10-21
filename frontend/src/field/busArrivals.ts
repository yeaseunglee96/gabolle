// 주변 버스 도착 — 현장 도구가 쓴다.
import { isVendorNotReady } from '@/api/vendorReady';
import { apiRequest, ApiClientError } from '@/api/client';
import { coarseCoordinate } from '@/personalization/locationConsent';

export type BusArrival = {
  routeNo: string;
  /** 도착까지 남은 초. 정보가 없으면 null 이다 — 서버 DTO 가 그렇게 적어 뒀다. */
  arrivalSeconds: number | null;
  /** 몇 정류장 전인지. 없으면 null. */
  remainingStops: number | null;
  vehicleType: string | null;
};

export type BusStop = {
  nodeId: string;
  nodeName: string;
  lat: number;
  lng: number;
  arrivals: BusArrival[];
};

export type NearbyBusDto = { stops: BusStop[] };

// 'not-ready' — 바깥 업체 열쇠가 안 꽂혔다. 다시 시도해도 매한가지다.
export type BusBlockedReason = 'signed-out' | 'not-built' | 'not-ready' | 'vendor' | 'error';

export type NearbyBusOutcome =
  | { state: 'ready'; stops: BusStop[] }
  | { state: 'blocked'; reason: BusBlockedReason };

/** 남은 시간을 어떻게 말할 것인가. */
export type ArrivalLabel =
  | { kind: 'unknown' }
  | { kind: 'imminent' }
  | { kind: 'minutes'; minutes: number };

export function arrivalLabel(arrivalSeconds: number | null | undefined): ArrivalLabel {
  if (arrivalSeconds == null || !Number.isFinite(arrivalSeconds)) return { kind: 'unknown' };
  if (arrivalSeconds < 0) return { kind: 'unknown' };
  if (arrivalSeconds < 60) return { kind: 'imminent' };
  return { kind: 'minutes', minutes: Math.floor(arrivalSeconds / 60) };
}

/** 빠른 것부터. */
export function sortArrivals(arrivals: BusArrival[]): BusArrival[] {
  return [...arrivals].sort((a, b) => {
    const as = a.arrivalSeconds;
    const bs = b.arrivalSeconds;
    if (as == null && bs == null) return a.routeNo.localeCompare(b.routeNo, 'ko', { numeric: true });
    if (as == null) return 1;
    if (bs == null) return -1;
    if (as !== bs) return as - bs;
    return a.routeNo.localeCompare(b.routeNo, 'ko', { numeric: true });
  });
}

/** 정류소도 "가장 빨리 오는 버스" 가 이른 곳부터. 아무것도 안 오는 곳은 뒤로. */
export function sortStops(stops: BusStop[]): BusStop[] {
  const soonest = (s: BusStop) => {
    const times = s.arrivals.map((a) => a.arrivalSeconds).filter((v): v is number => v != null);
    return times.length ? Math.min(...times) : Number.POSITIVE_INFINITY;
  };
  return [...stops].sort((a, b) => soonest(a) - soonest(b));
}

function blockedReason(error: unknown): BusBlockedReason {
  // 상태 숫자보다 먼저 본다. 열쇠가 안 꽂힌 것도 5xx 로 오므로
  // 숫자만 보면 「잠시 뒤면 될 수도 있다」과 구분되지 않는다.
  if (isVendorNotReady(error)) return 'not-ready';
  if (!(error instanceof ApiClientError)) return 'error';
  if (error.status === 401 || error.status === 403) return 'signed-out';
  if (error.status === 404 || error.status === 501) return 'not-built';
  if (error.status >= 500) return 'vendor';
  return 'error';
}

export async function loadNearbyBusArrivals(
  coords: { latitude: number; longitude: number },
  accessToken: string | null,
  signal?: AbortSignal,
): Promise<NearbyBusOutcome> {
  // 로그인 없이 부르면 401 이다. 갔다 와서 알기보다 여기서 바로 말해 준다.
  if (!accessToken) return { state: 'blocked', reason: 'signed-out' };
  try {
    // 🔴 좌표는 약 100m 로 줄여 보낸다 — 주소창에 실려 서버 접속 기록에 남는다(S15P21E201-1691).
    const query = new URLSearchParams({ lat: String(coarseCoordinate(coords.latitude)), lng: String(coarseCoordinate(coords.longitude)) });
    const dto = await apiRequest<NearbyBusDto>(`/api/v1/transit/nearby-bus-arrivals?${query.toString()}`, {
      accessToken,
      signal,
    });
    const stops = (dto?.stops ?? [])
      .filter((s) => s?.nodeId && s?.nodeName)
      .map((s) => ({ ...s, arrivals: sortArrivals((s.arrivals ?? []).filter((a) => a?.routeNo)) }));
    // 빈 목록은 실패가 아니다. 정말로 근처에 정류소가 없을 수 있다 — 화면이 "없어요" 라고
    // 말하면 되고, 그것은 사실이다. 여기서 blocked 로 바꾸면 없는 고장을 만들어 낸다.
    return { state: 'ready', stops: sortStops(stops) };
  } catch (error) {
    return { state: 'blocked', reason: blockedReason(error) };
  }
}

/**
 * 걸어가서 탈 수 있나 — 도착까지 남은 시간과 정류장까지 걷는 시간을 견준다(UI 캔버스 ⑬).
 * 「곧 도착」이 목록 맨 위에 있어도 걸어서 7분 걸리는 정류장이면 못 탄다 — 그 차이를 사람이 매번 계산하지 않게.
 * 2분은 여유다. 도착 시각은 몇 분씩 흔들리고 횡단보도 신호도 있다.
 *
 * @returns catch 걸어가면 탄다 · tight 빠듯하다 · miss 걸어가면 이미 떠난다 · null 모른다(도착 시간이 없다)
 */
export function catchVerdict(arrivalSeconds: number | null, walkMin: number): 'catch' | 'tight' | 'miss' | null {
  if (arrivalSeconds == null) return null;
  const arriveMin = arrivalSeconds / 60;
  if (arriveMin >= walkMin + 2) return 'catch';
  if (arriveMin >= walkMin) return 'tight';
  return 'miss';
}

