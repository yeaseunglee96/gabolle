import { apiRequest, ApiClientError } from '@/api/client';

// jaehyeon 님 계약(2026-09-08 axmap): GET /api/v1/places/facets.
// 여덟 갈래(축제·야시장·전통시장·액티비티·산책·자연·야경·기념품샵)는 서버 코드에 고정돼 있고
// 서버가 항상 전부 돌려준다(건수 0인 갈래도 옴). 🔴 목록을 화면에 박지 않는다 — 갈래가 늘거나
// 이름이 바뀌어도 앱을 다시 배포하지 않게 하려는 것이 서버가 labelKo까지 함께 주는 이유다.
export type FacetKeyEntry = { featureKey: string; placeCount: number; labelKo: string };
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

// 🔴 jaehyeon 님이 요청 칸(lat·lng·radiusMeters·facetKey·purpose·limit)은 확인해 줬지만 응답
// 모양은 아직 못 받았다 — 물어봐 둔 상태다. 그때까지는 이 함수를 만들지 않는다. 지어낸 타입으로
// 화면을 만들면 실제 응답이 오는 순간 다시 고쳐야 하고, 그 사이에 잘못된 필드를 가정하고 만든
// 화면이 조용히 깨진다.
