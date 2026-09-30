// 언어 다섯을 다 받는다. 이 함수가 'ko' | 'en' 만 받으면 부르는 쪽
// 열다섯 곳이 각자 떨어뜨려야 하고, 한 곳만 빠뜨리면 일본어 사용자가 한국어 이름을 본다.
// 떨어뜨리는 일은 여기 한 자리에서 한다.
import { coarseCoordinate } from '@/personalization/locationConsent';
import { resolveTextLanguage, type LanguageCode } from '@/i18n/languages';
import type { PhotoLicense, PhotoSubject } from '@/discovery/places';
import { apiRequest, ApiClientError, isServerError } from '@/api/client';
import { UNAVAILABLE_MESSAGE } from '@/api/errorText';
import { localNameFor, type LocalNames } from '@/discovery/localNames';
import type { LocalAddresses } from '@/discovery/localAddress';

export type FacetKeyEntry = { featureKey: string; placeCount: number; labelKo: string; labelEn?: string | null };
// 서버 목록과 순서는 그대로 유지한다. 이 사전은 영문 표기가 없는 기존 응답의 번역만 맡는다.
const ENGLISH_FACET_LABELS: Record<string, string> = {
  FESTIVAL: 'Festivals', NIGHT_MARKET: 'Night markets', TRADITIONAL_MARKET: 'Traditional markets',
  ACTIVITY: 'Activities', WALK: 'Walks', NATURE: 'Nature', NIGHT_VIEW: 'Night views', SOUVENIR_SHOP: 'Souvenir shops',
};
/**
 * 갈래 이름 — 서버는 한국어·영어 이름만 준다.
 * 🔴 일본어·중국어 화면에서 「Festivals」「Traditional markets」가 영어로 떴다(5개 언어 점검 2026-09-29).
 *    tx 를 주면 한국어 이름을 번역표에서 찾는다 — 표에 없으면 tx 가 영어로 떨어진다.
 */
export function localFacetLabel(entry: FacetKeyEntry, language: LanguageCode, tx?: (ko: string, en: string) => string): string {
  const text = resolveTextLanguage(language);
  if (text === 'ko') return entry.labelKo;
  const english = entry.labelEn?.trim() || ENGLISH_FACET_LABELS[entry.featureKey] || entry.labelKo;
  // resolveTextLanguage 는 한국어가 아니면 전부 'en' 이다 — 일본어·중국어는 원래 코드로 가른다.
  const tableLanguage = language === 'ja' || language === 'zh-Hans' || language === 'zh-Hant';
  return tableLanguage && tx ? tx(entry.labelKo, english) : english;
}
export function localPlaceName(place: { nameKo: string; nameEn: string | null; localNames?: LocalNames }, language: LanguageCode): string {
  // 일본어·중국어는 관광공사 번역 이름이 먼저(S15P21E201-1860). 없으면 예전처럼 영어 → 한국어.
  const local = localNameFor(place.localNames, language);
  if (local) return local;
  return resolveTextLanguage(language) === 'en' ? place.nameEn?.trim() || place.nameKo : place.nameKo;
}
export type LocalFacetEntry = FacetKeyEntry & { placeFeatureType: string };
export type FacetGroup = { userInputCode: string; placeFeatureType: string; matchKind: string; placeCount: number; keys: FacetKeyEntry[] };
export type FacetsDto = { facets: FacetGroup[]; generatedAt: string };

type FacetsFailure = { state: 'unavailable' | 'offline' | 'error'; message: string };

// — "아직 준비되지 않았어요" 는 404·501 일 때만 말한다.
function toFailure(error: unknown): FacetsFailure {
  if (error instanceof ApiClientError && error.code === 'NETWORK_ERROR') return { state: 'offline', message: error.message };
  if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return { state: 'unavailable', message: UNAVAILABLE_MESSAGE };
  // 5xx 는 'offline' 도 아니다 — 사용자의 인터넷은 멀쩡하므로 "연결을 확인해 주세요" 는 거짓말이다.
  if (isServerError(error)) return { state: 'error', message: (error as ApiClientError).message };
  return { state: 'error', message: error instanceof Error ? error.message : '요청을 처리하지 못했어요.' };
}

export type FacetsLoadResult = { state: 'success'; facets: FacetGroup[] } | FacetsFailure;

export async function getFacets(signal?: AbortSignal): Promise<FacetsLoadResult> {
  try {
    const dto = await apiRequest<FacetsDto>('/api/v1/places/facets', { signal });
    return { state: 'success', facets: dto.facets };
  } catch (error) {
    return toFailure(error);
  }
}

export function flattenLocalFacets(result: FacetsLoadResult, knownKeys: ReadonlySet<string>): LocalFacetEntry[] | null {
  if (result.state !== 'success') return null;
  const flat = result.facets.flatMap((group) => group.keys.map((entry) => ({ ...entry, placeFeatureType: group.placeFeatureType })));
  const local = flat.filter((entry) => knownKeys.has(entry.featureKey));
  return (local.length ? local : flat).filter((entry) => entry.placeCount > 0 && entry.labelKo);
}

// 근처 장소 조회 — NearbyPlaceController#nearby와 필드 단위로 맞춘 실제 계약.
// facetKey 를 주면 여덟 갈래 표식으로 좁힌다(purpose 는 gabolle.place.purposes 설정이 아직
// 비어 있어 이 화면에서는 안 쓴다 — 컨트롤러 javadoc 참고).
export type NearbyPlaceItem = {
  placeId: string;
  nameKo: string;
  nameEn: string | null;
  /** 일본어·중국어 이름 — 관광공사가 번역해 둔 곳만(S15P21E201-1859). 없으면 칸째 빠져 온다. */
  localNames?: LocalNames;
  category: string | null;
  address: string | null;
  addressEn?: string;
  /** 일본어·중국어 주소 — 관광공사가 번역해 둔 곳만(S15P21E201-1876). 화면은 addressForLanguage 로 고른다. */
  localAddresses?: LocalAddresses;
  lat: number;
  lng: number;
  distanceM: number;
  // — 목록 응답에 사진이 실려 온다(백엔드 MR !992). 값이 없으면 칸 자체가
  // 안 오므로 optional 이다. photoUrl 을 쓰면 photoSource 도 반드시 같이 그린다
  // 관광공사 공공누리라 출처 표기가 이용 조건이다.
  photoUrl?: string | null;
  photoSource?: string | null;
  // — 이 사진이 그 장소를 찍은 것인지, 그 장소가 든 건물을 찍은 것인지.
  // 값이 없으면 칸 자체가 안 온다. 없으면 화면은 아무 말도 안 한다(모르는 것을 아는 척 안 한다).
  photoSubject?: PhotoSubject | null;
  // 위키미디어 사진의 라이선스 — 뜻은 places.ts 의 PhotoLicense(S15P21E201-1610).
  photoLicense?: PhotoLicense | null;
};
export type NearbyPlacesDto = {
  items: NearbyPlaceItem[];
  requestedRadiusM: number;
  effectiveRadiusM: number;
  radiusExpanded: boolean;
  expansionSteps: number;
  scanTruncated: boolean;
  limit: number;
  purposeApplied: boolean;
  facetKeyApplied: boolean;
};

export type NearbyPlacesLoadResult = { state: 'success' } & NearbyPlacesDto | FacetsFailure;

export async function getNearbyPlaces(
  params: { lat: number; lng: number; facetKey?: string; radiusMeters?: number; limit?: number },
  signal?: AbortSignal,
): Promise<NearbyPlacesLoadResult> {
  // 🔴 좌표는 약 100m 로 줄여 보낸다 — 주소창에 실려 서버 접속 기록에 남는다(S15P21E201-1691).
  const query = new URLSearchParams({ lat: String(coarseCoordinate(params.lat)), lng: String(coarseCoordinate(params.lng)) });
  if (params.facetKey) query.set('facetKey', params.facetKey);
  if (params.radiusMeters) query.set('radiusMeters', String(params.radiusMeters));
  if (params.limit) query.set('limit', String(params.limit));
  try {
    const dto = await apiRequest<NearbyPlacesDto>(`/api/v1/places/nearby?${query.toString()}`, { signal });
    return { state: 'success', ...dto };
  } catch (error) {
    return toFailure(error);
  }
}
