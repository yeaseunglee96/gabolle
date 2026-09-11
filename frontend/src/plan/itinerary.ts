import { apiRequest, ApiClientError } from '@/api/client';

export type ItineraryItemDto = {
  id: string;
  startsAt: string;
  title: string;
  description?: string | null;
  estimatedCostKrw?: number | null;
  walkingMeters?: number | null;
  locked: boolean;
  dataStatus?: 'VERIFIED' | 'ESTIMATED' | 'UNKNOWN';
};

export type ItineraryDto = {
  id: string;
  title: string;
  version: number;
  days: Array<{ date: string; items: ItineraryItemDto[] }>;
  totalEstimatedCostKrw?: number | null;
  totalWalkingMeters?: number | null;
  fallbackMode?: 'MODEL' | 'RULE' | 'BASELINE' | null;
  myRole?: 'OWNER' | 'EDITOR' | 'VIEWER';
  canEdit?: boolean;
};

export type ItineraryVersionEntryDto = {
  version: number;
  baseVersion: number;
  operation: string;
  createdBy: string;
  createdAt: string;
  requestId: string;
  warningCodes?: string[];
  revertedFromVersion?: number | null;
};

export type ItineraryLoadResult =
  | { state: 'success'; itinerary: ItineraryDto }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

export type ItineraryMutationResult =
  | { state: 'success'; itinerary: ItineraryDto }
  | { state: 'conflict'; latestVersion: number; message: string }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

function failure(error: unknown): Exclude<ItineraryMutationResult, { state: 'success' }> {
  if (error instanceof ApiClientError && error.status === 409) {
    const latest = Number(error.fields.find((field) => /^latestVersion=/.test(field))?.split('=')[1]);
    return { state: 'conflict', latestVersion: Number.isFinite(latest) ? latest : 0, message: '다른 변경이 먼저 반영됐어요. 최신 일정을 불러와 다시 시도해 주세요.' };
  }
  if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return { state: 'unavailable', message: '일정 API가 아직 준비되지 않았어요.' };
  if (error instanceof ApiClientError && (error.status === 0 || error.code === 'NETWORK_ERROR')) return { state: 'offline', message: error.message };
  return { state: 'error', message: error instanceof Error ? error.message : '일정을 처리하지 못했어요.' };
}

export async function loadItinerary(id: string, accessToken: string | null): Promise<ItineraryLoadResult> {
  try {
    return { state: 'success', itinerary: await apiRequest<ItineraryDto>(`/api/v1/itineraries/${encodeURIComponent(id)}`, { accessToken }) };
  } catch (error) {
    const result = failure(error);
    return result.state === 'conflict' ? { state: 'error', message: result.message } : result;
  }
}

export async function setItineraryItemLocked(input: { itineraryId: string; itemId: string; locked: boolean; baseVersion: number; accessToken: string | null }): Promise<ItineraryMutationResult> {
  try {
    return { state: 'success', itinerary: await apiRequest<ItineraryDto>(`/api/v1/itineraries/${encodeURIComponent(input.itineraryId)}/items/${encodeURIComponent(input.itemId)}/lock`, { method: 'POST', accessToken: input.accessToken, body: { locked: input.locked, baseVersion: input.baseVersion } }) };
  } catch (error) {
    return failure(error);
  }
}

export type ItineraryJobAcceptedResult =
  | { state: 'accepted'; jobId: string }
  | { state: 'conflict'; latestVersion: number; message: string }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

export async function removeItineraryItem(input: { itineraryId: string; itemId: string; baseVersion: number; operationalReason?: string; accessToken: string | null }): Promise<ItineraryJobAcceptedResult> {
  try {
    const dto = await apiRequest<{ jobId: string }>(`/api/v1/itineraries/${encodeURIComponent(input.itineraryId)}/items/${encodeURIComponent(input.itemId)}/remove`, { method: 'POST', accessToken: input.accessToken, body: { baseVersion: input.baseVersion, operationalReason: input.operationalReason } });
    return { state: 'accepted', jobId: dto.jobId };
  } catch (error) {
    return failure(error);
  }
}

export async function recalculateItineraryDay(input: { itineraryId: string; baseVersion: number; dayIndex?: number; fromItemId?: string; accessToken: string | null }): Promise<ItineraryJobAcceptedResult> {
  try {
    const body = input.fromItemId ? { baseVersion: input.baseVersion, fromItemId: input.fromItemId } : { baseVersion: input.baseVersion, dayIndex: input.dayIndex ?? 0 };
    const dto = await apiRequest<{ jobId: string }>(`/api/v1/itineraries/${encodeURIComponent(input.itineraryId)}/recalculate`, { method: 'POST', accessToken: input.accessToken, body });
    return { state: 'accepted', jobId: dto.jobId };
  } catch (error) {
    return failure(error);
  }
}

export type ItineraryJobPollDto = {
  jobId: string;
  status: 'QUEUED' | 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED' | 'CANCELLED' | 'EXPIRED';
  itineraryVersion?: number | null;
  failure?: { code: string; detail?: string | null } | null;
};

export type ItineraryJobResult =
  | { state: 'pending' }
  | { state: 'succeeded'; version: number }
  | { state: 'conflict'; message: string }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

export async function pollItineraryJob(jobId: string, accessToken: string | null): Promise<ItineraryJobResult> {
  try {
    const dto = await apiRequest<ItineraryJobPollDto>(`/api/v1/jobs/${encodeURIComponent(jobId)}`, { accessToken });
    if (dto.status === 'SUCCEEDED') return { state: 'succeeded', version: dto.itineraryVersion ?? 0 };
    if (dto.status === 'FAILED' || dto.status === 'EXPIRED') {
      if (dto.failure?.code === 'ITINERARY_VERSION_CONFLICT') return { state: 'conflict', message: '다른 변경이 먼저 반영됐어요. 최신 일정을 불러와 다시 시도해 주세요.' };
      return { state: 'error', message: dto.failure?.detail ?? '요청을 처리하지 못했어요.' };
    }
    if (dto.status === 'CANCELED' || dto.status === 'CANCELLED') return { state: 'error', message: '요청이 취소됐어요.' };
    return { state: 'pending' };
  } catch (error) {
    const result = failure(error);
    return result.state === 'conflict' ? { state: 'conflict', message: result.message } : result;
  }
}

export type ItineraryReorderResult =
  | { state: 'success'; itinerary: ItineraryDto }
  | { state: 'conflict'; latestVersion: number; message: string }
  | { state: 'mismatch'; message: string }
  | { state: 'lockedItemMoved'; message: string }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

export async function reorderItineraryDay(input: { itineraryId: string; dayIndex: number; itemKeys: string[]; baseVersion: number; accessToken: string | null }): Promise<ItineraryReorderResult> {
  try {
    return {
      state: 'success',
      itinerary: await apiRequest<ItineraryDto>(`/api/v1/itineraries/${encodeURIComponent(input.itineraryId)}/days/${input.dayIndex}/reorder`, {
        method: 'POST',
        accessToken: input.accessToken,
        body: { itemKeys: input.itemKeys, baseVersion: input.baseVersion },
      }),
    };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 400 && error.code === 'ITINERARY_DAY_ORDER_MISMATCH') {
      return { state: 'mismatch', message: '순서 목록이 이 날짜의 장소와 맞지 않아요. 새로고침 후 다시 시도해 주세요.' };
    }
    if (error instanceof ApiClientError && error.status === 409 && error.code === 'ITINERARY_LOCKED_ITEM_MOVED') {
      return { state: 'lockedItemMoved', message: '고정된 장소는 자리를 옮길 수 없어요. 고정을 먼저 풀어 주세요.' };
    }
    return failure(error);
  }
}

export type ItineraryRevertResult = ItineraryMutationResult | { state: 'noOp'; message: string };

export async function revertItinerary(input: { itineraryId: string; baseVersion: number; toVersion?: number; accessToken: string | null }): Promise<ItineraryRevertResult> {
  try {
    const body = input.toVersion == null ? { baseVersion: input.baseVersion } : { baseVersion: input.baseVersion, toVersion: input.toVersion };
    return { state: 'success', itinerary: await apiRequest<ItineraryDto>(`/api/v1/itineraries/${encodeURIComponent(input.itineraryId)}/revert`, { method: 'POST', accessToken: input.accessToken, body }) };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 422 && error.code === 'ITINERARY_NOTHING_TO_REVERT') return { state: 'noOp', message: '되돌릴 변경 사항이 없어요.' };
    return failure(error);
  }
}

export async function loadItineraryVersions(itineraryId: string, accessToken: string | null): Promise<{ state: 'success'; versions: ItineraryVersionEntryDto[] } | { state: 'unavailable' | 'offline' | 'error'; message: string }> {
  try {
    return { state: 'success', versions: await apiRequest<ItineraryVersionEntryDto[]>(`/api/v1/itineraries/${encodeURIComponent(itineraryId)}/versions`, { accessToken }) };
  } catch (error) {
    const result = failure(error);
    return result.state === 'conflict' ? { state: 'error', message: result.message } : result;
  }
}
