// 로컬 탐색의 「맛집」·「카페」 갈래 — 구 → 동으로 골라 본다 (S15P21E201-2051 맛집 · S15P21E201-2059 카페·내 위치).
//
// 맛집은 사람마다 기준이 달라 자의적으로 고르면 믿음이 안 간다. 그래서 공공기관 목록(부산시 맛집정보 · 구군 모범음식점 ·
// 택슐랭)에 오르거나 현지인이 추천한 식당만 보여 준다. 카페도 같은 틀이다 — 현지인이 추천했거나 한국관광공사·레드테이블이
// 소개한 카페. 사용자는 구 → 동 두 단계로 고른 뒤 그 동의 목록을 본다.
//
// 서버 (두 끝점을 맛집과 카페가 함께 쓴다 — kind=RESTAURANT(기본) | CAFE)
//   GET /api/v1/places/restaurants/areas[?kind=][&lat=&lng=]   구 → 동 목록과 곳마다의 수. 좌표를 주면 nearestGu 를 더한다
//   GET /api/v1/places/restaurants?[kind=&]gu=&dong=           구·동으로 거른 목록. 기존 목록 카드 칸에 gu · dong · certifiedSources 를 더했다
// 갈래 자체는 /facets 의 탐색 갈래 끝에 LOCAL_RESTAURANT · LOCAL_CAFE 로 온다. 서버에 자료가 아직 없으면 그 갈래가 안 와서 칩이 안 그려진다 —
// 장소가 0곳인 갈래를 거르는 기존 규칙(flattenLocalFacets)이 그대로 맡는다. 앱을 서버보다 먼저 배포해도 된다.
import { apiRequest } from '@/api/client';
import { romanizeKorean } from '@/discovery/romanize';
import { toPlaceSearchItem, type PlaceSearchItem, type PlaceSearchItemDto } from '@/discovery/places';
import { resolveTextLanguage, type LanguageCode } from '@/i18n/languages';
import { coarseCoordinate } from '@/personalization/locationConsent';

/** 서버 갈래 코드 — `/facets` 의 INTEREST_TAG 끝에 붙는다. */
export const LOCAL_RESTAURANT_KEY = 'LOCAL_RESTAURANT';
export const LOCAL_CAFE_KEY = 'LOCAL_CAFE';

/** 구 → 동으로 골라 보는 갈래 둘 — 서버 `kind` 와 같은 낱말이다. */
export type LocalKind = 'RESTAURANT' | 'CAFE';

/** 갈래 열쇠(`LOCAL_RESTAURANT` · `LOCAL_CAFE`)가 어느 종류인지. 둘 다 아니면 null — 일반 갈래다. */
export function localKindOf(entry: { featureKey: string } | null | undefined): LocalKind | null {
  if (entry?.featureKey === LOCAL_RESTAURANT_KEY) return 'RESTAURANT';
  if (entry?.featureKey === LOCAL_CAFE_KEY) return 'CAFE';
  return null;
}

export function isLocalRestaurant(entry: { featureKey: string } | null | undefined): boolean {
  return localKindOf(entry) === 'RESTAURANT';
}

export function isLocalCafe(entry: { featureKey: string } | null | undefined): boolean {
  return localKindOf(entry) === 'CAFE';
}

/**
 * 카페를 맛집 바로 뒤에 둔다. 서버는 선언 순서(맛집 → 카페)로 주지만, 낱말 글자순으로 주던 응답이 와도(「LOCAL_CAFE」가 앞) 화면의
 * 순서는 같아야 한다. 둘 중 하나가 없거나 이미 그 순서면 받은 배열을 그대로 돌려준다 — 다른 갈래의 순서는 안 건드린다.
 */
export function placeCafeAfterRestaurant<T extends { featureKey: string }>(facets: T[]): T[] {
  const cafeAt = facets.findIndex((entry) => entry.featureKey === LOCAL_CAFE_KEY);
  const restaurantAt = facets.findIndex((entry) => entry.featureKey === LOCAL_RESTAURANT_KEY);
  if (cafeAt < 0 || restaurantAt < 0 || cafeAt === restaurantAt + 1) return facets;
  const rest = facets.filter((entry) => entry.featureKey !== LOCAL_CAFE_KEY);
  rest.splice(rest.findIndex((entry) => entry.featureKey === LOCAL_RESTAURANT_KEY) + 1, 0, facets[cafeAt]);
  return rest;
}

/**
 * 소개·인증한 목록의 서버 낱말 — 배지 순서이기도 하다(공공기관 셋 → 카페 쪽 둘 → 현지인 추천).
 * 서버가 새 낱말을 주면 이름표를 못 붙이므로 그린 쪽에서 건너뛴다.
 */
export const RESTAURANT_SOURCES = ['BUSAN_FOOD_INFO', 'MODEL_RESTAURANT', 'TAXI_DRIVER_PICK', 'KTO_CAFE', 'REDTABLE_FEATURED', 'LOCAL_SURVEY'] as const;
export type RestaurantSource = (typeof RESTAURANT_SOURCES)[number];

export type RestaurantDong = { dong: string; placeCount: number };
export type RestaurantGu = { gu: string; placeCount: number; dongs: RestaurantDong[] };
type RestaurantAreasDto = { areas: RestaurantGu[]; nearestGu?: string | null; generatedAt: string };

/** 구 → 동 목록 + (내 위치를 줬다면) 내 위치에서 가장 가까운 곳의 구. */
export type LocalAreas = { areas: RestaurantGu[]; nearestGu: string | null };

/** 목록 한 줄 — 기존 장소 카드가 쓰는 칸 그대로에 구·동·소개한 목록이 더해진다. */
export type RestaurantItem = PlaceSearchItem & {
  gu: string | null;
  dong: string | null;
  certifiedSources: string[];
  /** 레드테이블이 방송·수상 이력도 적어 둔 곳. 서버는 참일 때만 이 칸을 준다 — 없으면 아니다. */
  broadcastOrAward?: boolean;
};
type RestaurantItemDto = PlaceSearchItemDto & {
  gu?: string | null;
  dong?: string | null;
  certifiedSources?: string[] | null;
  broadcastOrAward?: boolean | null;
};
type RestaurantPageDto = { items: RestaurantItemDto[]; limit: number; hasNext: boolean };

/** 고른 곳. dong 이 null 이면 그 구 전체다. */
export type RestaurantArea = { gu: string; dong: string | null };

/** 내 위치. 화면이 읽은 값 그대로 — 서버로 보낼 때 여기서 약 100m 로 줄인다. */
export type LocalCoords = { latitude: number; longitude: number };

type Tx = (ko: string, en: string) => string;

/**
 * 구 → 동 목록. 서버가 곳이 많은 쪽부터 준 순서 그대로다.
 *
 * 🔴 좌표를 주면 `nearestGu` 가 온다 — 그 갈래의 장소 가운데 가장 가까운 곳의 구, 5km 가 넘으면 null. 좌표는 **약 100m 로 줄여** 보낸다
 *    (주소창에 실려 서버 접속 기록에 남는다 — S15P21E201-1691). 맛집(기본)은 `kind` 를 안 보내 예전 요청과 똑같다.
 */
export async function getLocalAreas(
  kind: LocalKind,
  options: { coords?: LocalCoords | null; signal?: AbortSignal } = {},
): Promise<LocalAreas> {
  const query = new URLSearchParams();
  if (kind !== 'RESTAURANT') query.set('kind', kind);
  if (options.coords) {
    query.set('lat', String(coarseCoordinate(options.coords.latitude)));
    query.set('lng', String(coarseCoordinate(options.coords.longitude)));
  }
  const queryText = query.toString();
  const dto = await apiRequest<RestaurantAreasDto>(`/api/v1/places/restaurants/areas${queryText ? `?${queryText}` : ''}`, { signal: options.signal });
  return {
    areas: Array.isArray(dto.areas) ? dto.areas : [],
    nearestGu: typeof dto.nearestGu === 'string' && dto.nearestGu.trim() ? dto.nearestGu : null,
  };
}

export function toRestaurantItem(dto: RestaurantItemDto): RestaurantItem {
  const item: RestaurantItem = {
    ...toPlaceSearchItem(dto),
    gu: dto.gu ?? null,
    dong: dto.dong ?? null,
    certifiedSources: Array.isArray(dto.certifiedSources) ? dto.certifiedSources : [],
  };
  if (dto.broadcastOrAward === true) item.broadcastOrAward = true;
  return item;
}

/** 구·동으로 거른 맛집·카페. 순서는 서버가 정했다(소개·인증한 목록이 많은 집이 먼저). */
export async function getLocalList(kind: LocalKind, area: RestaurantArea, signal?: AbortSignal): Promise<{ items: RestaurantItem[]; hasNext: boolean }> {
  const query = new URLSearchParams();
  if (kind !== 'RESTAURANT') query.set('kind', kind);
  query.set('gu', area.gu);
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
 * 카드에 달 출처 배지의 글. 서버 순서가 아니라 고정 순서(부산시 맛집 → 모범음식점 → 택슐랭 → 관광공사 → 레드테이블 → 현지인 추천)이고,
 * 모르는 낱말은 건너뛴다. 문구는 글자 그대로 tx 에 넣는다 — 번역 검사(check:translations)가 보도록.
 *
 * 🔴 출처를 사실대로 적는다 — 「현지인 추천」은 공공기관 인증이 아니라 설문에서 사람들이 추천한 것이고, 「레드테이블 · 방송·수상」은 레드테이블이
 *    방송 출연이나 수상 이력을 적어 둔 곳일 때만 붙는다.
 */
export function sourceBadgeLabels(sources: readonly string[], tx: Tx, options: { broadcastOrAward?: boolean } = {}): string[] {
  const labels: string[] = [];
  for (const source of RESTAURANT_SOURCES) {
    if (!sources.includes(source)) continue;
    if (source === 'BUSAN_FOOD_INFO') labels.push(tx('부산시 맛집', 'Busan Food Guide'));
    else if (source === 'MODEL_RESTAURANT') labels.push(tx('모범음식점', 'Model Restaurant'));
    else if (source === 'TAXI_DRIVER_PICK') labels.push(tx('택슐랭', 'Taxi drivers’ pick'));
    else if (source === 'KTO_CAFE') labels.push(tx('관광공사 추천', 'Korea Tourism pick'));
    else if (source === 'REDTABLE_FEATURED') labels.push(options.broadcastOrAward ? tx('레드테이블 · 방송·수상', 'Red Table · TV & awards') : tx('레드테이블', 'Red Table'));
    else labels.push(tx('현지인 추천', 'Local pick'));
  }
  return labels;
}
