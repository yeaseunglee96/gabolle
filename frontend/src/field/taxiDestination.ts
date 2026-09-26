// 택시 목적지 고르기 — S15P21E201-1141.
//
// 🔴 우리 DB 만 보면 안 된다(S15P21E201-1742). 2026-09-26 실측으로 부산역(호텔만 나옴)·BIFF광장·
// 부산타워·더베이101·부산시민공원·동백섬이 0건이었고, 「해운대 해수욕장」처럼 띄어 쓰면 있는 장소도
// 0건이었다. 카카오 검색(/api/v1/origins)은 그 전부를 주소와 함께 찾는다. 그래서 둘에 동시에 묻고
// 합친다 — 글쓰기 지역 검색(searchRegions)과 같은 방식이다.
import { ApiClientError } from '@/api/client';
import { searchPlacesByName, type PlaceSearchItem } from '@/discovery/places';
import { searchOrigins, type OriginCandidate } from '@/plan/origins';

/** 목록 한 줄. 우리 DB 장소면 placeId 가 있고, 카카오 결과면 없다(이름·주소로 카드를 그린다). */
export type TaxiDestination = {
  key: string;
  name: string;
  address: string | null;
  placeId: string | null;
};

export type TaxiDestinationOutcome =
  | { state: 'idle' }
  | { state: 'ready'; items: TaxiDestination[] }
  | { state: 'empty' }
  | { state: 'blocked'; reason: 'signed-out' | 'not-built' | 'server' | 'error' };

const MAX_ITEMS = 10;

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

const clean = (value: string | null | undefined) => (value ?? '').trim();
const sameKey = (name: string, address: string | null) => `${name.replace(/\s/g, '')}|${clean(address).replace(/\s/g, '')}`;

/**
 * 한 목록으로 합친다. 순서는 ① 우리 장소 중 주소가 있는 것 ② 카카오 결과 ③ 우리 장소 중 주소가 없는 것.
 * 🔴 주소 없는 우리 장소를 뒤로 미는 이유 — 같은 곳을 카카오가 주소와 함께 주는 일이 흔하다(모모스 → 「모모스커피 부산본점」).
 * 기사에게 보여줄 것이 많은 줄이 위에 와야 한다.
 */
export function mergeTaxiDestinations(
  ours: PlaceSearchItem[],
  kakao: Pick<OriginCandidate, 'name' | 'address' | 'externalId'>[],
): TaxiDestination[] {
  const usable = ours.filter((item) => item.placeId && clean(item.nameKo) && (clean(item.address) || Number.isFinite(item.lat)));
  const toOurs = (item: PlaceSearchItem): TaxiDestination => ({ key: `p:${item.placeId}`, name: clean(item.nameKo), address: clean(item.address) || null, placeId: item.placeId });
  const withAddress = usable.filter((item) => clean(item.address)).map(toOurs);
  const withoutAddress = usable.filter((item) => !clean(item.address)).map(toOurs);
  const external = kakao
    .filter((item) => clean(item.name) && clean(item.address))
    .map((item): TaxiDestination => ({ key: `k:${item.externalId ?? `${item.name}|${item.address}`}`, name: clean(item.name), address: clean(item.address), placeId: null }));

  const seen = new Set<string>();
  const out: TaxiDestination[] = [];
  for (const item of [...withAddress, ...external, ...withoutAddress]) {
    const key = sameKey(item.name, item.address);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(item);
  }
  return out.slice(0, MAX_ITEMS);
}

/** 목적지 후보를 찾는다. 두 곳 중 한 곳만 답해도 그것으로 목록을 만든다 — 둘 다 안 될 때만 막힌다. */
export async function searchTaxiDestinations(raw: string, signal?: AbortSignal, accessToken: string | null = null): Promise<TaxiDestinationOutcome> {
  const query = normalizeDestinationQuery(raw);
  if (query.length < MIN_DESTINATION_QUERY_LENGTH) return { state: 'idle' };
  let oursError: unknown = null;
  const [ours, kakao] = await Promise.all([
    searchPlacesByName(query, signal).catch((error: unknown) => { oursError = error; return null; }),
    searchOrigins(query, accessToken, signal).then((result) => (result.state === 'success' ? result.items : null)).catch(() => null),
  ]);
  if (ours === null && kakao === null) return { state: 'blocked', reason: blockedReason(oursError) };
  const items = mergeTaxiDestinations(ours ?? [], kakao ?? []);
  if (!items.length) return { state: 'empty' };
  return { state: 'ready', items };
}

/** 목록의 둘째 줄에 적을 말. */
export function destinationSubtitle(item: { address?: string | null }): string | null {
  const address = item.address?.trim();
  return address ? address : null;
}

/** 고른 줄로 여는 택시 카드 주소. 카카오 결과는 우리 id 가 없어 이름·주소를 그대로 싣는다. */
export function taxiCardHref(item: TaxiDestination): string {
  if (item.placeId) return `/taxi-card/${encodeURIComponent(item.placeId)}`;
  return `/taxi-card/external?name=${encodeURIComponent(item.name)}&address=${encodeURIComponent(item.address ?? '')}`;
}
