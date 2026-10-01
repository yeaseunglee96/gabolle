/**
 * 탑승 중 — 타고 가는 동안 남은 정류장과 내릴 곳을 센다. S15P21E201-1837.
 *
 * 외국인 여행자는 한국어 안내 방송을 못 알아들어 어디서 내릴지 몰라 불안하다. 내 위치를 받을 때마다 「남은 정류장」·
 * 「내릴 곳까지 거리」를 다시 세고, 내릴 곳이 다가오면(정류장 1개 전, 또는 ALERT_M 안) 알릴 때라고 답한다.
 *
 * 🔴 정류장 목록은 서버가 단계마다 `stops` 로 준다(S15P21E201-1836). 옛 서버는 칸이 없다 — 그때는 이름을 모르니
 *    「남은 정류장」은 null 로 두고 거리로만 센다. 내릴 곳 좌표는 경로선에서 얻는다(한 번만 타는 길일 때).
 */
import { parseTransitGuidance, transitStepKind } from '@/field/routeLegs';
import { straightDistanceM } from '@/field/subwayStations';
import type { RouteDirections, RouteStep } from '@/map/routeDirections';

export type StopPoint = { name: string; lat: number; lng: number };
type LatLng = { latitude: number; longitude: number };

/** 서버가 새로 싣는 칸 — 옛 서버는 없다. routeDirections.ts 의 타입은 건드리지 않고 여기서 선택 칸으로 받는다. */
type StepWithStops = RouteStep & { stops?: StopPoint[] | null };

/** 이만큼 가까워지면 「다음 정류장에서 내리세요」. 부산 시내버스 정류장 간격이 대개 300~600m 라 한 정거장 전쯤이다. */
export const ALERT_M = 500;
/** 이만큼 가까우면 내린 것으로 본다 — 갈아타는 길이면 다음 구간으로 넘어간다. */
export const ARRIVED_M = 80;
/**
 * 이보다 멀면 «이 노선 위에 있지 않다» 로 본다 — 지금 정류장을 정하지 않는다(S15P21E201-1903).
 * 🔴 전에는 노선에서 몇 km 떨어져 있어도 가장 가까운 정류장을 「지금 여기」로 찍었고, 그 정류장이 끝에서 둘째면
 *    타지도 않았는데 「다음 정류장에서 내리세요」로 울렸다(실기기: 강서구에서 연 1003번 안내가 부산역을 「지금 여기」로).
 */
export const OFF_ROUTE_M = 400;

export type RideLeg = {
  kind: 'bus' | 'subway';
  /** 「1003」「88(A)」「2호선」. */
  line: string;
  board: string | null;
  alightName: string | null;
  /** 내릴 곳 좌표. 모르면 null — 그 구간은 거리로 셀 수 없다. */
  alight: LatLng | null;
  /** 지나는 정류장(타는 곳~내리는 곳). 옛 서버면 빈 목록. */
  stops: StopPoint[];
};

const finite = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value);

/** 대중교통 길에서 타는 구간들. 걷기만 있으면 빈 목록. */
export function rideLegs(directions: RouteDirections): RideLeg[] {
  if (directions.mode !== 'TRANSIT') return [];
  const rides = (directions.steps as StepWithStops[]).filter((step) => transitStepKind(step) !== 'walk');
  return rides.map((step) => {
    const stops = (step.stops ?? []).filter((stop) => stop && typeof stop.name === 'string' && finite(stop.lat) && finite(stop.lng));
    const parsed = parseTransitGuidance(step.guidance);
    const last = stops[stops.length - 1];
    let alight: LatLng | null = last ? { latitude: last.lat, longitude: last.lng } : null;
    // 옛 서버: 경로선은 [출발, 탄 구간의 정류장들…, 도착] 이라 한 번만 타면 끝에서 두 번째 점이 내릴 곳이다.
    if (!alight && rides.length === 1 && directions.path.length >= 3) {
      const [lng, lat] = directions.path[directions.path.length - 2];
      if (finite(lat) && finite(lng)) alight = { latitude: lat, longitude: lng };
    }
    return {
      kind: transitStepKind(step) === 'subway' ? 'subway' : 'bus',
      line: step.name.replace(/번$/, '').trim(),
      board: parsed?.kind === 'ride' ? parsed.from : stops[0]?.name ?? null,
      alightName: parsed?.kind === 'ride' ? parsed.to : last?.name ?? null,
      alight,
      stops,
    };
  });
}

export type RideProgress = {
  /** 내릴 곳까지 남은 정류장 수. 정류장 목록이 없으면 null. */
  remainingStops: number | null;
  /** 지금 가장 가까운 정류장의 순번(stops 안). 정류장 목록이 없으면 null — 사다리를 그릴 때 쓴다. */
  currentIndex: number | null;
  /** 다음 정류장 이름. 모르거나 이미 내릴 곳이면 null. */
  nextStop: string | null;
  distanceToAlightM: number | null;
  /** 지금 「다음 정류장에서 내리세요」라고 알릴 때인가. */
  alertNow: boolean;
  /** 내릴 곳에 닿았나. */
  arrived: boolean;
};

/**
 * 내 위치 하나로 이 구간의 진행을 센다.
 * 🔴 남은 정류장은 «가장 가까운 정류장»의 순번으로 센다. 버스가 두 정류장 사이에 있으면 가까운 쪽으로 붙는데,
 *    알림이 한 정거장 일찍 올 수는 있어도 늦지는 않는다 — 늦는 것이 더 나쁘다.
 */
export function rideProgress(leg: RideLeg, at: LatLng): RideProgress {
  const distanceToAlightM = leg.alight ? straightDistanceM(at, leg.alight) : null;
  let remainingStops: number | null = null;
  let nextStop: string | null = null;
  let currentIndex: number | null = null;
  if (leg.stops.length >= 2) {
    let nearest = 0;
    let best = Number.POSITIVE_INFINITY;
    leg.stops.forEach((stop, index) => {
      const d = straightDistanceM(at, { latitude: stop.lat, longitude: stop.lng });
      if (d < best) { best = d; nearest = index; }
    });
    if (best <= OFF_ROUTE_M) {
      currentIndex = nearest;
      remainingStops = leg.stops.length - 1 - nearest;
      nextStop = remainingStops > 0 ? leg.stops[nearest + 1].name : null;
    }
  }
  const arrived = distanceToAlightM !== null && distanceToAlightM <= ARRIVED_M;
  const alertNow = !arrived && ((remainingStops !== null && remainingStops <= 1) || (distanceToAlightM !== null && distanceToAlightM <= ALERT_M));
  return { remainingStops, currentIndex, nextStop, distanceToAlightM, alertNow, arrived };
}
