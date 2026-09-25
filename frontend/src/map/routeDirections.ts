import { UNAVAILABLE_MESSAGE } from '@/api/errorText';
import { apiRequest, ApiClientError } from '@/api/client';
import type { SlopePiece } from '@/map/slopeGrades';

// 계약: backend/src/main/java/com/gabolle/backend/route/presentation/RouteController.java
// (-184). 좌표 두 개만 있으면 되는 일이라 여행·일정에 매달지 않는다 — 컨트롤러
// 자체 주석 참고. 대중교통(TRANSIT)은 지하철·버스 경로를 주는 공개 API가 아직 없어서
// (RouteQueryService 클래스 주석,대기) transferCount 는 항상 null 이고
// steps 는 항상 빈 배열이다 — 이 화면은 그 사실을 지어내지 않고 그대로 보여준다.
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
  params: { originLat: number; originLng: number; destLat: number; destLng: number; mode?: TravelMode },
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
  try {
    const directions = await apiRequest<RouteDirections>(`/api/v1/routes/directions?${query.toString()}`, { accessToken, signal });
    return { state: 'success', directions };
  } catch (error) {
    if (error instanceof ApiClientError && error.code === 'NETWORK_ERROR') return { state: 'offline', message: error.message };
    if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return { state: 'unavailable', message: UNAVAILABLE_MESSAGE };
    return { state: 'error', message: error instanceof Error ? error.message : '경로를 불러오지 못했어요.' };
  }
}
