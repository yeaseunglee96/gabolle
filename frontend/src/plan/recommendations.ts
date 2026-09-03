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
export type RecommendationJobResultDto = { status: 'COMPLETED' | 'PARTIAL' | 'FAILED'; items?: RecommendationCourseDto[]; itineraryId?: string | null; fallbackMode?: FallbackMode; conflicts?: string[]; errorMessage?: string | null };

export type RecommendationCourse = RecommendationCourseDto & { reasons: string[]; actionState: CourseActionState };
export type RecommendationViewModel = { state: RecommendationViewState; courses: RecommendationCourse[]; conflicts: string[]; message: string; itineraryId: string | null };

const REASON: Record<string, string> = { BEACH_PREFERENCE: '바다 취향 반영', LOCAL_FOOD: '로컬 음식 선호', LOW_WALKING: '보행 부담 고려', QUIET_PLACE: '조용한 장소 선호', ACCESSIBLE_ROUTE: '이동 제약 고려' };
export const reasonLabel = (code: string) => REASON[code] ?? '추천 조건 반영';

export function adaptRecommendationResult(dto: RecommendationJobResultDto): RecommendationViewModel {
  if (dto.status === 'FAILED') return { state: 'error', courses: [], conflicts: dto.conflicts ?? [], message: dto.errorMessage ?? '추천 결과를 불러오지 못했어요.', itineraryId: null };
  const courses = (dto.items ?? []).map((item) => ({ ...item, reasons: item.reasonCodes.map(reasonLabel), actionState: 'idle' as const }));
  if (!courses.length) return { state: 'empty-conflict', courses: [], conflicts: dto.conflicts ?? [], message: '조건을 만족하는 추천을 찾지 못했어요.', itineraryId: null };
  const state: RecommendationViewState = dto.status === 'PARTIAL' ? 'partial' : (dto.fallbackMode && dto.fallbackMode !== 'MODEL' ? 'fallback' : 'success');
  return { state, courses, conflicts: dto.conflicts ?? [], message: state === 'partial' ? '일부 정보가 확인되지 않은 결과예요.' : state === 'fallback' ? '기본 추천 방식으로 구성했어요.' : '조건에 맞는 코스를 찾았어요.', itineraryId: dto.itineraryId ?? courses.find((item) => item.itineraryId)?.itineraryId ?? null };
}

export const unavailableRecommendations = (): RecommendationViewModel => ({ state: 'unavailable', courses: [], conflicts: [], message: '아직 생성된 추천이 없어요. 여행 조건을 확인하고 생성을 시작해 주세요.', itineraryId: null });
