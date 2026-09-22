// 일정 진행을 **서버에** 남긴다 (S15P21E201-1325).
//
// 🔴 전에는 이 기기에만 남았다. 폰에서 출발해 놓고 태블릿으로 보면 아무 일도 안 일어난
//    것처럼 보이고, 앱을 지우면 사라졌다.
//
// 🔴 보내는 것은 **상태가 아니라 일어난 일**이다. 「지금 RUNNING 이다」를 통째로 보내면
//    두 기기가 서로의 상태를 덮어쓴다. 사건을 보내면 서버가 순서를 정한다.
import { ApiClientError, apiRequest } from '@/api/client';
import type { StopOutcome, TripProgress } from '@/plan/tripProgress';

type StopDto = {
  itemKey?: string;
  index?: number;
  arrivedAt?: string | null;
  arrivedHow?: string | null;
  skipped?: boolean;
};

type ProgressDto = {
  status?: string;
  currentStopIndex?: number;
  startedAt?: string | null;
  stops?: StopDto[];
};

export type ProgressResult =
  | { state: 'success'; progress: TripProgress }
  /** 서버에 이 자리가 아직 없다(404·501). 화면은 기기에 남은 것으로 간다. */
  | { state: 'unavailable' }
  | { state: 'error'; message: string };

/**
 * 서버가 준 것을 화면 모양으로.
 *
 * 🔴 「다녀옴」과 「건너뜀」을 한 칸으로 합치지 않는다. 건너뛴 곳은 안 간 곳이다 —
 * 합치면 나중에 「거기 갔었나?」를 기억으로만 풀어야 한다.
 */
export function adaptProgress(dto: ProgressDto): TripProgress {
  const status = dto?.status === 'RUNNING' || dto?.status === 'PAUSED' || dto?.status === 'DONE'
    ? dto.status
    : 'PLANNED';
  const outcomes: Record<string, StopOutcome> = {};
  for (const stop of dto?.stops ?? []) {
    if (typeof stop?.itemKey !== 'string' || stop.itemKey === '') continue;
    if (stop.skipped) {
      outcomes[stop.itemKey] = { kind: 'SKIPPED', at: stop.arrivedAt ?? '' };
    }
    else if (typeof stop.arrivedAt === 'string' && stop.arrivedAt !== '') {
      outcomes[stop.itemKey] = {
        kind: 'ARRIVED',
        at: stop.arrivedAt,
        // 🔴 모르면 「손으로 찍었다」로 떨어뜨리지 않는다. 기본 경로가 GPS 라서, 모르는 것을
        //    손으로 떨어뜨리면 「GPS 가 얼마나 맞히나」를 재는 값이 조용히 나빠진다.
        how: stop.arrivedHow === 'manual' ? 'manual' : 'auto',
      };
    }
  }
  return {
    status,
    currentStopIndex: typeof dto?.currentStopIndex === 'number' && dto.currentStopIndex >= 0 ? dto.currentStopIndex : 0,
    outcomes,
  };
}

async function call(path: string, accessToken: string | null, method: 'GET' | 'POST', body?: unknown): Promise<ProgressResult> {
  try {
    const dto = await apiRequest<ProgressDto>(path, { method, accessToken, body });
    return { state: 'success', progress: adaptProgress(dto) };
  }
  catch (error) {
    // 🔴 아직 없는 자리(404·501)는 실패가 아니다. 화면이 기기에 남은 것으로 이어 간다.
    if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) {
      return { state: 'unavailable' };
    }
    return { state: 'error', message: error instanceof Error ? error.message : '진행 상태를 저장하지 못했어요.' };
  }
}

const base = (itineraryId: string) => `/api/v1/itineraries/${encodeURIComponent(itineraryId)}/progress`;

export function fetchProgress(itineraryId: string, accessToken: string | null) {
  return call(base(itineraryId), accessToken, 'GET');
}

export function startProgress(itineraryId: string, accessToken: string | null) {
  return call(`${base(itineraryId)}/start`, accessToken, 'POST');
}

export function pauseProgress(itineraryId: string, accessToken: string | null) {
  return call(`${base(itineraryId)}/pause`, accessToken, 'POST');
}

export function arriveProgress(itineraryId: string, itemKey: string, how: 'auto' | 'manual', accessToken: string | null) {
  return call(`${base(itineraryId)}/stops/${encodeURIComponent(itemKey)}/arrive`, accessToken, 'POST', { how });
}

export function skipProgress(itineraryId: string, itemKey: string, accessToken: string | null) {
  return call(`${base(itineraryId)}/stops/${encodeURIComponent(itemKey)}/skip`, accessToken, 'POST');
}
