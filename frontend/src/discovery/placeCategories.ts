import { apiRequest, ApiClientError, isServerError } from '@/api/client';
import { UNAVAILABLE_MESSAGE } from '@/api/errorText';

export type PlaceCategoryItem = { code: string; placeCount: number };
export type PlaceCategoriesDto = { categories: PlaceCategoryItem[]; generatedAt: string };

type PlaceCategoriesFailure = { state: 'unavailable' | 'offline' | 'error'; message: string };

// — localExplore.ts 의 toFailure 와 같은 이유로 상태 코드를 본다.
// INVALID_RESPONSE 로 가르면 배포 중 nginx 의 502(본문이 HTML)까지 "아직 준비되지 않았어요"
// 가 되어, 잠깐 끊긴 것을 사용자가 "없는 기능" 으로 읽는다.
function toFailure(error: unknown): PlaceCategoriesFailure {
  if (error instanceof ApiClientError && error.code === 'NETWORK_ERROR') return { state: 'offline', message: error.message };
  if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return { state: 'unavailable', message: UNAVAILABLE_MESSAGE };
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
