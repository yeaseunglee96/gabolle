// 로컬 탐색의 「맛집」 갈래 — 공공기관이 인증한 식당만 (S15P21E201-2051).
//
// 맛집은 사람마다 기준이 달라 자의적으로 고르면 믿음이 안 간다. 그래서 부산시 맛집정보 · 구군 모범음식점 · 택슐랭(택시기사가
// 추천한 식당)에 오른 곳만 보여 준다. 사용자는 구 → 동 두 단계로 고른 뒤 그 동의 맛집 목록을 본다.
//
// 서버
//   GET /api/v1/places/restaurants/areas        구 → 동 목록과 곳마다의 맛집 수
//   GET /api/v1/places/restaurants?gu=&dong=    구·동으로 거른 목록. 기존 목록 카드 칸에 gu · dong · certifiedSources 를 더했다
// 갈래 자체는 /facets 의 탐색 갈래 끝에 LOCAL_RESTAURANT 로 온다. 서버에 자료가 아직 없으면 그 갈래가 안 와서 칩이 안 그려진다 —
// 장소가 0곳인 갈래를 거르는 기존 규칙(flattenLocalFacets)이 그대로 맡는다. 앱을 서버보다 먼저 배포해도 된다.
import { apiRequest } from '@/api/client';
import { romanizeKorean } from '@/discovery/romanize';
import { toPlaceSearchItem, type PlaceSearchItem, type PlaceSearchItemDto } from '@/discovery/places';
import { resolveTextLanguage, type LanguageCode } from '@/i18n/languages';

/** 서버 갈래 코드 — `/facets` 의 INTEREST_TAG 끝에 붙는다. */
export const LOCAL_RESTAURANT_KEY = 'LOCAL_RESTAURANT';

export function isLocalRestaurant(entry: { featureKey: string } | null | undefined): boolean {
  return entry?.featureKey === LOCAL_RESTAURANT_KEY;
}

/** 인증한 목록의 서버 낱말. 서버가 새 낱말을 주면 이름표를 못 붙이므로 그린 쪽에서 건너뛴다. */
export const RESTAURANT_SOURCES = ['BUSAN_FOOD_INFO', 'MODEL_RESTAURANT', 'TAXI_DRIVER_PICK'] as const;
export type RestaurantSource = (typeof RESTAURANT_SOURCES)[number];

export type RestaurantDong = { dong: string; placeCount: number };
export type RestaurantGu = { gu: string; placeCount: number; dongs: RestaurantDong[] };
type RestaurantAreasDto = { areas: RestaurantGu[]; generatedAt: string };

/** 목록 한 줄 — 기존 장소 카드가 쓰는 칸 그대로에 구·동·인증 목록이 더해진다. */
export type RestaurantItem = PlaceSearchItem & { gu: string | null; dong: string | null; certifiedSources: string[] };
type RestaurantItemDto = PlaceSearchItemDto & { gu?: string | null; dong?: string | null; certifiedSources?: string[] | null };
type RestaurantPageDto = { items: RestaurantItemDto[]; limit: number; hasNext: boolean };

/** 고른 곳. dong 이 null 이면 그 구 전체다. */
export type RestaurantArea = { gu: string; dong: string | null };

type Tx = (ko: string, en: string) => string;

/** 구 → 동 목록. 서버가 식당이 많은 곳부터 준 순서 그대로다. */
export async function getRestaurantAreas(signal?: AbortSignal): Promise<RestaurantGu[]> {
  const dto = await apiRequest<RestaurantAreasDto>('/api/v1/places/restaurants/areas', { signal });
  return Array.isArray(dto.areas) ? dto.areas : [];
}

export function toRestaurantItem(dto: RestaurantItemDto): RestaurantItem {
  return {
    ...toPlaceSearchItem(dto),
    gu: dto.gu ?? null,
    dong: dto.dong ?? null,
    certifiedSources: Array.isArray(dto.certifiedSources) ? dto.certifiedSources : [],
  };
}

/** 구·동으로 거른 맛집. 순서는 서버가 정했다(인증한 목록이 많은 집이 먼저). */
export async function getRestaurants(area: RestaurantArea, signal?: AbortSignal): Promise<{ items: RestaurantItem[]; hasNext: boolean }> {
  const query = new URLSearchParams({ gu: area.gu });
  if (area.dong) query.set('dong', area.dong);
  const dto = await apiRequest<RestaurantPageDto>(`/api/v1/places/restaurants?${query.toString()}`, { signal });
  return { items: dto.items.map(toRestaurantItem), hasNext: dto.hasNext === true };
}

/**
 * 구·동 이름 — 한국어 화면은 그대로, 그 밖의 언어는 읽는 법을 곁들인다(장소 이름과 같은 romanizeKorean).
 * 이름을 번역하지 않는다: 길을 묻거나 택시를 탈 때 쓰는 말이 한국어 이름이라 그대로 남아야 한다.
 */
export function areaLabel(name: string, language: LanguageCode): string {
  if (resolveTextLanguage(language) === 'ko') return name;
  const reading = romanizeKorean(name);
  return reading ? `${name} (${reading})` : name;
}

/**
 * 카드에 달 출처 배지의 글. 서버 순서가 아니라 고정 순서(부산시 맛집 → 모범음식점 → 택슐랭)이고, 모르는 낱말은 건너뛴다.
 * 문구는 글자 그대로 tx 에 넣는다 — 번역 검사(check:translations)가 보도록.
 */
export function sourceBadgeLabels(sources: readonly string[], tx: Tx): string[] {
  const labels: string[] = [];
  for (const source of RESTAURANT_SOURCES) {
    if (!sources.includes(source)) continue;
    if (source === 'BUSAN_FOOD_INFO') labels.push(tx('부산시 맛집', 'Busan Food Guide'));
    else if (source === 'MODEL_RESTAURANT') labels.push(tx('모범음식점', 'Model Restaurant'));
    else labels.push(tx('택슐랭', 'Taxi drivers’ pick'));
  }
  return labels;
}
