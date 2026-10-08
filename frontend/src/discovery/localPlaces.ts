// 「로컬 장소」 묶음 — 로컬 탐색의 첫 칸(S15P21E201-2024).
//
// 전에는 서버가 준 갈래 순서 그대로 첫 칸이 「전통시장」이었다. 사용자 피드백은
// 「로컬 탐색을 전통시장이란 이름이 아닌 로컬 장소로 시작」. 서버에는 「로컬 장소 전체」를
// 한 번에 주는 길이 없다 — 근처 조회(/places/nearby)에 갈래를 안 주면 편의점까지 섞인
// 모든 장소가 온다. 그래서 화면이 여덟 갈래를 각각 조금씩 불러 섞는다. 서버는 안 바꾼다.
import { getNearbyPlaces, type LocalFacetEntry, type NearbyPlaceItem, type NearbyPlacesLoadResult } from '@/discovery/localExplore';
import { getPlacesByFacet, type PlaceSearchItem } from '@/discovery/places';
import type { LanguageCode } from '@/i18n/languages';

/** 서버 갈래 코드와 겹치지 않는 화면 전용 열쇠. 서버에 보내지 않는다. */
export const LOCAL_PLACES_KEY = 'LOCAL_PLACES';

// 🔴 번역표(translations.ts)가 아니라 여기 둔다 — 이 문구는 갈래 이름이라 localFacetLabel 을
//    거치지 않고 이 함수로만 그린다. 한 자리에서 다섯 언어를 다 본다.
const LABELS: Record<LanguageCode, string> = {
  ko: '로컬 장소', en: 'Local places', ja: 'ローカルスポット', 'zh-Hans': '本地好去处', 'zh-Hant': '在地好去處',
} as Record<LanguageCode, string>;

export function localPlacesLabel(language: LanguageCode): string {
  return LABELS[language] ?? LABELS.en;
}

export function isLocalPlaces(entry: { featureKey: string } | null | undefined): boolean {
  return entry?.featureKey === LOCAL_PLACES_KEY;
}

/**
 * 맨 앞에 「로컬 장소」를 붙인다. 개수(placeCount)는 갈래끼리 겹칠 수 있어 더하면 틀린다 —
 * 그래서 0 으로 두고, 화면은 이 칸에서 개수를 그리지 않는다(모르는 것을 아는 척 안 한다).
 */
export function withLocalPlacesFirst(facets: LocalFacetEntry[]): LocalFacetEntry[] {
  if (!facets.length) return facets;
  return [{ featureKey: LOCAL_PLACES_KEY, labelKo: LABELS.ko, labelEn: LABELS.en, placeCount: 0, placeFeatureType: '' }, ...facets];
}

/** 갈래별 목록을 번갈아 섞는다 — 한 갈래가 첫 화면을 다 차지하지 않게. 같은 장소는 한 번만. */
export function mergeLocalPlaces<T extends { placeId: string }>(lists: T[][], limit: number): T[] {
  const seen = new Set<string>();
  const out: T[] = [];
  const longest = Math.max(0, ...lists.map((list) => list.length));
  for (let i = 0; i < longest && out.length < limit; i += 1) {
    for (const list of lists) {
      const item = list[i];
      if (!item || seen.has(item.placeId)) continue;
      seen.add(item.placeId);
      out.push(item);
      if (out.length >= limit) break;
    }
  }
  return out;
}

const PER_FACET = 6;

/**
 * 묶음에 섞지 않는 갈래. 축제는 장소 검색이 날짜를 몰라 이미 끝난 축제가 섞인다(홈 축제 줄이
 * 날짜 조회로 바꾼 이유와 같다 — S15P21E201-1594). 축제는 자기 칸·자기 줄에서 본다.
 */
const EXCLUDED_FROM_MIX = new Set(['FESTIVAL']);
const mixable = <T extends { featureKey: string }>(facets: T[]): T[] => facets.filter((f) => !EXCLUDED_FROM_MIX.has(f.featureKey) && f.featureKey !== LOCAL_PLACES_KEY);
const TOTAL = 24;

/** 부산 전체 — 갈래마다 몇 곳씩. 전부 실패할 때만 실패로 본다. */
export async function getAllLocalPlaces(
  facets: Pick<LocalFacetEntry, 'featureKey' | 'placeFeatureType'>[], signal?: AbortSignal, total = TOTAL,
): Promise<PlaceSearchItem[]> {
  const settled = await Promise.allSettled(mixable(facets).map((f) => getPlacesByFacet(f.placeFeatureType, f.featureKey, PER_FACET, signal)));
  const ok = settled.filter((r): r is PromiseFulfilledResult<PlaceSearchItem[]> => r.status === 'fulfilled');
  if (!ok.length && settled.length) throw (settled[0] as PromiseRejectedResult).reason;
  return mergeLocalPlaces(ok.map((r) => r.value), total);
}

/** 내 근처 — 갈래마다 근처 조회를 하고 가까운 순으로 합친다. 반경은 가장 넓힌 값을 밝힌다. */
export async function getNearbyLocalPlaces(
  facets: LocalFacetEntry[], center: { lat: number; lng: number }, signal?: AbortSignal,
): Promise<NearbyPlacesLoadResult> {
  const results = await Promise.all(mixable(facets).map((f) => getNearbyPlaces({ lat: center.lat, lng: center.lng, facetKey: f.featureKey, limit: PER_FACET }, signal)));
  const ok = results.filter((r): r is Extract<NearbyPlacesLoadResult, { state: 'success' }> => r.state === 'success');
  if (!ok.length) return results[0] ?? { state: 'error', message: '요청을 처리하지 못했어요.' };
  const items: NearbyPlaceItem[] = mergeLocalPlaces(ok.map((r) => r.items), TOTAL).sort((a, b) => a.distanceM - b.distanceM);
  const widest = ok.reduce((a, b) => (b.effectiveRadiusM > a.effectiveRadiusM ? b : a));
  return {
    ...widest,
    items,
    limit: TOTAL,
    radiusExpanded: ok.some((r) => r.radiusExpanded),
    scanTruncated: ok.some((r) => r.scanTruncated),
  };
}
