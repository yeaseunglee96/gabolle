import { apiRequest, ApiClientError, isServerError } from '@/api/client';

// jaehyeon 님 계약(S15P21E201-896, 2026-09-13 axmap): GET /api/v1/places/categories.
// place.category 값별 장소 수다. facets(place_feature 표식)와는 다른 값이고, 추천 후보를
// 좁힐 때 실제로 비교되는 쪽이 이거다 — 그래서 취향 화면의 갈래 노출 여부를 이걸로 정한다.
// 서버가 place.category 에 실제로 있는 값만 낸다(목록을 서버가 정하지 않는다) — 그래서
// 적재가 새로 돌면 코드 변경 없이 응답에 갈래가 늘어난다.
export type PlaceCategoryItem = { code: string; placeCount: number };
export type PlaceCategoriesDto = { categories: PlaceCategoryItem[]; generatedAt: string };

type PlaceCategoriesFailure = { state: 'unavailable' | 'offline' | 'error'; message: string };

// 🔴 S15P21E201-1081 — localExplore.ts 의 toFailure 와 같은 이유로 상태 코드를 본다.
// INVALID_RESPONSE 로 가르면 배포 중 nginx 의 502(본문이 HTML)까지 "아직 준비되지 않았어요"
// 가 되어, 잠깐 끊긴 것을 사용자가 "없는 기능" 으로 읽는다.
function toFailure(error: unknown): PlaceCategoriesFailure {
  if (error instanceof ApiClientError && error.code === 'NETWORK_ERROR') return { state: 'offline', message: error.message };
  if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return { state: 'unavailable', message: '갈래 조회 API가 아직 준비되지 않았어요.' };
  if (isServerError(error)) return { state: 'error', message: (error as ApiClientError).message };
  return { state: 'error', message: error instanceof Error ? error.message : '요청을 처리하지 못했어요.' };
}

export type PlaceCategoriesLoadResult = { state: 'success'; categories: PlaceCategoryItem[] } | PlaceCategoriesFailure;

export async function getPlaceCategories(signal?: AbortSignal): Promise<PlaceCategoriesLoadResult> {
  try {
    const dto = await apiRequest<PlaceCategoriesDto>('/api/v1/places/categories', { signal });
    return { state: 'success', categories: dto.categories };
  } catch (error) {
    return toFailure(error);
  }
}
