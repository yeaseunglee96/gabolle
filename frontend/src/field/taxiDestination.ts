// 택시 목적지 고르기 — S15P21E201-1141.
import { ApiClientError } from '@/api/client';
import { searchPlacesByName, type PlaceSearchItem } from '@/discovery/places';

export type TaxiDestinationOutcome =
  | { state: 'idle' }
  | { state: 'ready'; items: PlaceSearchItem[] }
  | { state: 'empty' }
  | { state: 'blocked'; reason: 'signed-out' | 'not-built' | 'server' | 'error' };

/** 입력한 글자를 검색어로 다듬는다. */
export function normalizeDestinationQuery(raw: string): string {
  return raw.trim().replace(/\s+/g, ' ');
}

/** 지금 검색을 쏠 것인가. */
export const MIN_DESTINATION_QUERY_LENGTH = 2;

export function canSearchDestination(raw: string): boolean {
  return normalizeDestinationQuery(raw).length >= MIN_DESTINATION_QUERY_LENGTH;
}

function blockedReason(error: unknown): Extract<TaxiDestinationOutcome, { state: 'blocked' }>['reason'] {
  if (!(error instanceof ApiClientError)) return 'error';
  if (error.status === 401 || error.status === 403) return 'signed-out';
  if (error.status === 404 || error.status === 501) return 'not-built';
  if (error.status >= 500) return 'server';
  return 'error';
}

/** 목적지 후보를 찾는다. */
export async function searchTaxiDestinations(raw: string, signal?: AbortSignal): Promise<TaxiDestinationOutcome> {
  const query = normalizeDestinationQuery(raw);
  if (query.length < MIN_DESTINATION_QUERY_LENGTH) return { state: 'idle' };
  try {
    const found = await searchPlacesByName(query, signal);
    const items = found.filter((item) => item.placeId && item.nameKo && (item.address || Number.isFinite(item.lat)));
    if (!items.length) return { state: 'empty' };
    return { state: 'ready', items };
  } catch (error) {
    return { state: 'blocked', reason: blockedReason(error) };
  }
}

/** 목록의 둘째 줄에 적을 말. */
export function destinationSubtitle(item: PlaceSearchItem): string | null {
  const address = item.address?.trim();
  return address ? address : null;
}
