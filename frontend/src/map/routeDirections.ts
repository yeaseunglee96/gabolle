import { UNAVAILABLE_MESSAGE } from '@/api/errorText';
import { apiRequest, ApiClientError } from '@/api/client';
import type { SlopePiece } from '@/map/slopeGrades';

// 계약: backend/src/main/java/com/gabolle/backend/route/presentation/RouteController.java
// (-184). 좌표 두 개만 있으면 되는 일이라 여행·일정에 매달지 않는다 — 컨트롤러
// 자체 주석 참고. 대중교통(TRANSIT)은 이제 서버가 노선망(TRANSIT_NETWORK)으로 실제 단계(steps)를 답한다 —
// 전에 「steps 는 항상 빈 배열」이라 적혀 있었는데 더는 맞지 않는다(2026-09-29, 진미리 제보). 그래도 노선망이
// 못 이은 구간은 빈 배열·null 로 올 수 있고, 이 화면은 없는 단계를 지어내지 않는다.
export type TravelMode = 'CAR' | 'TRANSIT' | 'WALK';

export type RouteStep = { name: string; guidance: string; distanceM: number; durationMin: number };

export type RouteDirections = {
  mode: TravelMode;
  distanceM: number;
  durationMin: number;
  taxiFareKrw: number | null;
  tollFareKrw: number | null;
  transferCount: number | null;
  estimated: boolean;
  estimateReason: string | null;
  provider: string;
  path: [number, number][];
  steps: RouteStep[];
  /**
   * 걷는 길의 경사 조각(백엔드 !1626, S15P21E201-1630). 우리 보행 길찾기가 찾은 걷기(provider OSM_WALK_GRAPH ·
   * estimated false)에만 차고 나머지는 빈 배열이다. 그 전의 서버는 칸이 없다.
   */
  pieces?: SlopePiece[];
};

export type RouteDirectionsResult =
  | { state: 'success'; directions: RouteDirections }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

export async function getRouteDirections(
  /**
   * stepFree = 계단·급경사를 피하는 길로 물을까 — 휠체어·유아차·계단 피하기를 고른 여행(일정 응답의 stepFree).
   * 🔴 true 일 때만 붙인다. false·없음이면 전과 같은 주소라, 이 칸을 모르는 옛 서버도 그대로 답한다.
   */
  params: { originLat: number; originLng: number; destLat: number; destLng: number; mode?: TravelMode; stepFree?: boolean },
  accessToken: string | null,
  signal?: AbortSignal,
): Promise<RouteDirectionsResult> {
  const query = new URLSearchParams({
    originLat: String(params.originLat),
    originLng: String(params.originLng),
    destLat: String(params.destLat),
    destLng: String(params.destLng),
  });
  if (params.mode) query.set('mode', params.mode);
  if (params.stepFree === true) query.set('stepFree', 'true');
  try {
    const directions = await apiRequest<RouteDirections>(`/api/v1/routes/directions?${query.toString()}`, { accessToken, signal });
    return { state: 'success', directions };
  } catch (error) {
    if (error instanceof ApiClientError && error.code === 'NETWORK_ERROR') return { state: 'offline', message: error.message };
    if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return { state: 'unavailable', message: UNAVAILABLE_MESSAGE };
    return { state: 'error', message: error instanceof Error ? error.message : '경로를 불러오지 못했어요.' };
  }
}
