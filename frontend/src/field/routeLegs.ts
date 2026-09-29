/**
 * 경로 상세의 재료 — S15P21E201-1831.
 *
 * 서버 `/routes/directions` 는 수단마다 다른 것을 준다(2026-09-29 운영 실측, 해운대→자갈치).
 * - 대중교통: 노선 단계(「1003번」 — 어디서 타고 어디서 내리는지), 환승 횟수, 경로선. 시각표가 없어 늘 어림이다.
 * - 자동차: 택시 요금·통행료, 도로 경로선, 갈림길 안내.
 * - 도보: 우리 보행 길찾기의 경로선과 경사 조각. 단계 안내는 없다.
 * 화면은 이 차이를 지어내지 않고 그대로 옮긴다.
 */
import type { RouteDirections, RouteStep, TravelMode } from '@/map/routeDirections';
import type { MapPathPoint } from '@/map/types';

type Tx = (ko: string, en: string) => string;

/** 탭 순서 — 대중교통을 먼저 둔다. 이 앱의 여행자는 차가 없는 사람이 많다. */
export const ROUTE_MODES: readonly TravelMode[] = ['TRANSIT', 'CAR', 'WALK'];

export function parseTravelMode(raw: string | undefined): TravelMode | null {
  return raw === 'TRANSIT' || raw === 'CAR' || raw === 'WALK' ? raw : null;
}

/** 일정의 이동수단 코드(여행 만들 때 보낸 travelModes)를 경로 수단으로. 모르면 null. */
export function modeFromTravelModes(modes: readonly string[] | null | undefined): TravelMode | null {
  const first = modes?.[0];
  if (first === 'BUS' || first === 'SUBWAY') return 'TRANSIT';
  if (first === 'PRIVATE_CAR' || first === 'TAXI') return 'CAR';
  if (first === 'WALK') return 'WALK';
  return null;
}

/** 서버는 [경도, 위도] 로 준다(GeoJSON 순서). 지도는 {위도, 경도} 를 받는다 — S15P21E201-1567 에서 한 번 뒤바뀌었다. */
export function toMapPath(path: readonly [number, number][] | null | undefined): MapPathPoint[] {
  return (path ?? []).map(([lng, lat]) => ({ latitude: lat, longitude: lng }));
}

export type TransitStepKind = 'walk' | 'subway' | 'bus';

/** 대중교통 단계의 종류. 서버가 걷는 환승을 「도보」로, 지하철 노선을 「N호선」으로 부른다(TransitRouteAdapter.toLeg). */
export function transitStepKind(step: RouteStep): TransitStepKind {
  if (step.name === '도보') return 'walk';
  if (/호선$/.test(step.name)) return 'subway';
  return 'bus';
}

/**
 * 탭의 둘째 줄 — 택시비. 390 폭 탭 한 칸에 「42분 · 약 22,800원」이 안 들어가 잘렸다(S15P21E201-1831 실측).
 * 🔴 택시비는 자동차 탭에만 있다. 대중교통 요금은 서버가 아직 안 준다(transitFareKrw 가 null) — 지어내지 않는다.
 */
export function modeFareLine(directions: RouteDirections, tx: Tx): string | null {
  if (directions.mode !== 'CAR' || directions.taxiFareKrw == null) return null;
  const fare = directions.taxiFareKrw.toLocaleString('en-US');
  return tx(`택시 약 ${fare}원`, `Taxi ~₩${fare}`);
}

/**
 * 서버의 어림 사유는 한국어 고정 문장이다(RouteQueryService·TransitRouteAdapter). 아는 것만 화면 언어로 바꾸고
 * 모르는 것은 그대로 둔다 — 새 문장이 생겨도 빈 칸이 되지는 않는다.
 */
export function estimateReasonText(reason: string | null | undefined, tx: Tx): string | null {
  if (!reason) return null;
  switch (reason) {
    case '시각표가 없어 노선의 평균 배차간격과 정거장 수로 계산한 값입니다.':
      return tx('버스·지하철 시각표가 없어 평균 배차간격과 정거장 수로 계산했어요. 실제로는 기다리는 시간에 따라 달라요.', 'There is no timetable, so this uses the average interval and number of stops. The real time depends on how long you wait.');
    case '출발 시각을 몰라 하루의 여러 시각을 재서 가운데 값으로 답했습니다.':
      return tx('출발 시각을 몰라 하루 여러 시각의 가운데 값으로 계산했어요.', 'We do not know when you leave, so this is the middle value across the day.');
    case '이 이동수단의 경로를 물어볼 곳이 아직 없습니다.':
      return tx('이 수단의 경로는 아직 찾을 수 없어 직선거리로 어림했어요.', 'We cannot search this kind of route yet, so this is estimated from the straight-line distance.');
    case '경로 서비스에서 경로를 받지 못했습니다.':
      return tx('경로 서비스가 답하지 않아 직선거리로 어림했어요.', 'The route service did not answer, so this is estimated from the straight-line distance.');
    case '걸어가는 편이 대중교통보다 빨라 도보로 계산했습니다.':
      return tx('걸어가는 편이 대중교통보다 빨라요.', 'Walking is faster than public transit here.');
    default:
      return reason;
  }
}

/** 걸리는 시간 — 60분이 넘으면 「4시간 7분」. 「247분」은 한눈에 안 읽힌다. */
export function formatDuration(minutes: number, tx: Tx): string {
  const total = Math.max(0, Math.round(minutes));
  if (total < 60) return tx(`${total}분`, `${total} min`);
  const h = Math.floor(total / 60);
  const m = total % 60;
  return m === 0 ? tx(`${h}시간`, `${h} h`) : tx(`${h}시간 ${m}분`, `${h} h ${m} min`);
}

/**
 * 서버의 대중교통 안내 문장에서 타는 곳·내리는 곳을 뽑는다(TransitRouteAdapter.toLeg 의 두 문장 모양).
 * 문장 그대로 두면 「1003번을(를)」 같은 조사가 보이고, 영어 화면에도 한국어 문장이 통째로 나간다.
 * 모양이 다르면 null — 화면은 원문을 그대로 쓴다.
 */
export function parseTransitGuidance(guidance: string): { kind: 'ride' | 'walk'; from: string; to: string } | null {
  const ride = /^(.+?)에서 .+?을\(를\) 타고 (.+)에서 내립니다\.?$/.exec(guidance);
  if (ride) return { kind: 'ride', from: ride[1], to: ride[2] };
  const walk = /^(.+?)에서 (.+)까지 걸어서 갈아탑니다\.?$/.exec(guidance);
  if (walk) return { kind: 'walk', from: walk[1], to: walk[2] };
  return null;
}
