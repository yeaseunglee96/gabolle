import { apiRequest, ApiClientError } from '@/api/client';

// jaehyeon 님 계약(2026-09-08 axmap): GET /api/v1/places/facets.
// 여덟 갈래(축제·야시장·전통시장·액티비티·산책·자연·야경·기념품샵)는 서버 코드에 고정돼 있고
// 서버가 항상 전부 돌려준다(건수 0인 갈래도 옴). 🔴 목록을 화면에 박지 않는다 — 갈래가 늘거나
// 이름이 바뀌어도 앱을 다시 배포하지 않게 하려는 것이 서버가 labelKo까지 함께 주는 이유다.
export type FacetKeyEntry = { featureKey: string; placeCount: number; labelKo: string };
export type LocalFacetEntry = FacetKeyEntry & { placeFeatureType: string };
export type FacetGroup = { userInputCode: string; placeFeatureType: string; matchKind: string; placeCount: number; keys: FacetKeyEntry[] };
export type FacetsDto = { facets: FacetGroup[]; generatedAt: string };

type FacetsFailure = { state: 'unavailable' | 'offline' | 'error'; message: string };

function toFailure(error: unknown): FacetsFailure {
  if (error instanceof ApiClientError && error.code === 'NETWORK_ERROR') return { state: 'offline', message: error.message };
  if (error instanceof ApiClientError && error.code === 'INVALID_RESPONSE') return { state: 'unavailable', message: '로컬 탐색 API가 아직 준비되지 않았어요.' };
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

// 근처 장소 조회 — NearbyPlaceController#nearby(S15P21E201-469)와 필드 단위로 맞춘 실제 계약.
// facetKey 를 주면 여덟 갈래 표식으로 좁힌다(purpose 는 gabolle.place.purposes 설정이 아직
// 비어 있어 이 화면에서는 안 쓴다 — 컨트롤러 javadoc 참고).
export type NearbyPlaceItem = {
  placeId: string;
  nameKo: string;
  nameEn: string | null;
  category: string | null;
  address: string | null;
  lat: number;
  lng: number;
  distanceM: number;
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
  const query = new URLSearchParams({ lat: String(params.lat), lng: String(params.lng) });
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
