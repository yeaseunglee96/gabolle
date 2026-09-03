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
