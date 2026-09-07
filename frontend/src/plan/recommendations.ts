import { apiRequest, ApiClientError, getApiLanguage } from '@/api/client';

export type RecommendationViewState = 'loading' | 'success' | 'partial' | 'fallback' | 'empty-conflict' | 'error' | 'offline' | 'unavailable';
export type DataStatus = 'VERIFIED' | 'ESTIMATED' | 'UNKNOWN';
export type FallbackMode = 'MODEL' | 'RULE' | 'BASELINE';
export type CourseActionState = 'idle' | 'saving' | 'saved' | 'excluding' | 'excluded' | 'failed';

export type RecommendationCourseDto = {
  id: string; title: string; imageUrl?: string | null; reasonCodes: string[];
  estimatedCostKrw?: number | null; crowdLevel?: 'LOW' | 'MEDIUM' | 'HIGH' | null;
  mobilityWarnings?: string[] | null; dataStatus: DataStatus; fallbackMode: FallbackMode;
  itineraryId?: string | null;
};
export type RecommendationJobResultDto = { status: 'COMPLETED' | 'PARTIAL' | 'FAILED'; items?: RecommendationCourseDto[]; itineraryId?: string | null; fallbackMode?: FallbackMode; conflicts?: string[]; errorMessage?: string | null; placeCount?: number | null; estimatedTravelMinutes?: number | null };

export type RecommendationCourse = RecommendationCourseDto & { reasons: string[]; actionState: CourseActionState };
export type RecommendationViewModel = { state: RecommendationViewState; courses: RecommendationCourse[]; conflicts: string[]; message: string; itineraryId: string | null; placeCount: number | null; estimatedTravelMinutes: number | null };

const t = (ko: string, en: string) => (getApiLanguage() === 'en' ? en : ko);

const REASON: Record<string, [string, string]> = {
  BEACH_PREFERENCE: ['바다 취향 반영', 'Matches your beach preference'],
  LOCAL_FOOD: ['로컬 음식 선호', 'Local food you like'],
  LOW_WALKING: ['보행 부담 고려', 'Considers walking limits'],
  QUIET_PLACE: ['조용한 장소 선호', 'Quiet place preference'],
  ACCESSIBLE_ROUTE: ['이동 제약 고려', 'Considers mobility needs'],
};
export const reasonLabel = (code: string) => t(...(REASON[code] ?? ['추천 조건 반영', 'Reflects your conditions']));

export function adaptRecommendationResult(dto: RecommendationJobResultDto): RecommendationViewModel {
  const placeCount = dto.placeCount ?? null;
  const estimatedTravelMinutes = dto.estimatedTravelMinutes ?? null;
  if (dto.status === 'FAILED') return { state: 'error', courses: [], conflicts: dto.conflicts ?? [], message: dto.errorMessage ?? t('추천 결과를 불러오지 못했어요.', 'Could not load the recommendation result.'), itineraryId: null, placeCount, estimatedTravelMinutes };
  const courses = (dto.items ?? []).map((item) => ({ ...item, reasons: item.reasonCodes.map(reasonLabel), actionState: 'idle' as const }));
  if (!courses.length) return { state: 'empty-conflict', courses: [], conflicts: dto.conflicts ?? [], message: t('조건을 만족하는 추천을 찾지 못했어요.', 'No recommendations matched your conditions.'), itineraryId: null, placeCount, estimatedTravelMinutes };
  const state: RecommendationViewState = dto.status === 'PARTIAL' ? 'partial' : (dto.fallbackMode && dto.fallbackMode !== 'MODEL' ? 'fallback' : 'success');
  return { state, courses, conflicts: dto.conflicts ?? [], message: state === 'partial' ? t('일부 정보가 확인되지 않은 결과예요.', 'Some details in this result are unconfirmed.') : state === 'fallback' ? t('기본 추천 방식으로 구성했어요.', 'We used the baseline recommendation method.') : t('조건에 맞는 코스를 찾았어요.', 'We found courses that match your conditions.'), itineraryId: dto.itineraryId ?? courses.find((item) => item.itineraryId)?.itineraryId ?? null, placeCount, estimatedTravelMinutes };
}

export const unavailableRecommendations = (): RecommendationViewModel => ({ state: 'unavailable', courses: [], conflicts: [], message: t('아직 생성된 추천이 없어요. 여행 조건을 확인하고 생성을 시작해 주세요.', "No recommendations have been created yet. Check your trip conditions and start generating."), itineraryId: null, placeCount: null, estimatedTravelMinutes: null });

export async function loadRecommendationResult(jobId: string, accessToken: string | null): Promise<RecommendationViewModel> {
  try {
    return adaptRecommendationResult(await apiRequest<RecommendationJobResultDto>(`/api/v1/recommendation-jobs/${encodeURIComponent(jobId)}`, { accessToken }));
  } catch (error) {
    if (error instanceof ApiClientError && (error.status === 0 || error.code === 'NETWORK_ERROR')) {
      return { state: 'offline', courses: [], conflicts: [], message: error.message, itineraryId: null, placeCount: null, estimatedTravelMinutes: null };
    }
    if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return unavailableRecommendations();
    return { state: 'error', courses: [], conflicts: [], message: error instanceof Error ? error.message : t('추천 결과를 불러오지 못했어요.', 'Could not load the recommendation result.'), itineraryId: null, placeCount: null, estimatedTravelMinutes: null };
  }
}
