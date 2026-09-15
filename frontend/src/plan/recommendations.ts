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

// S15P21E201-910: 이 사전은 한때 BEACH_PREFERENCE 등 다섯 개였는데 백엔드 계약이 통째로
// 갈아엎어진 뒤에도(BaselineCandidateScorer·RecommendationCodes, back/dev) 안 따라가서,
// 실제로 오는 코드와 하나도 안 겹쳐 모든 카드가 안전장치 문구만 중복 표시하고 있었다.
// 지금 실제로 오는 코드로 교체한다.
const REASON: Record<string, [string, string]> = {
  NEAR_ORIGIN: ['출발지에서 가까움', 'Close to your starting point'],
  TAG_MATCH_INTEREST: ['관심 카테고리와 일치', 'Matches your interests'],
  TAG_MATCH_ATMOSPHERE: ['선호 분위기와 일치', 'Matches your preferred mood'],
  TAG_MATCH_CUISINE: ['음식 취향과 일치', 'Matches your food preferences'],
  POPULAR: ['인기 있는 곳', 'A popular spot'],
  EDITORIAL_PICK: ['에디터 추천', "Editor's pick"],
  DIVERSITY_RERANKED: ['다양성을 위해 순서 조정됨', 'Reordered for variety'],
};

// TOP_CONTRIBUTOR_<축 이름> — 축 이름은 score_components 맵의 키를 대소문자까지 그대로
// 붙인 것이라 고정된 목록이 아니다(RecommendationCodes.java 주석: 대문자로 바꾸면 코드와
// 어긋난다). 아는 축은 문구를 달고, 모르는 축이 와도 최소한 서로 다른 텍스트가 보이도록
// 축 이름을 그대로 보여준다 — 전부 같은 안전장치 문구로 뭉개지 않는다.
const TOP_CONTRIBUTOR_PREFIX = 'TOP_CONTRIBUTOR_';
const AXIS_LABEL: Record<string, [string, string]> = {
  distance: ['거리', 'distance'],
  interest: ['관심 카테고리', 'your interests'],
  atmosphere: ['분위기', 'mood'],
  cuisine: ['음식 취향', 'food preferences'],
  preferenceAlignment: ['취향 일치도', 'preference match'],
  popularity: ['인기도', 'popularity'],
};

export const reasonLabel = (code: string): string => {
  if (REASON[code]) return t(...REASON[code]);
  if (code.startsWith(TOP_CONTRIBUTOR_PREFIX)) {
    const axis = code.slice(TOP_CONTRIBUTOR_PREFIX.length);
    const label = AXIS_LABEL[axis];
    return label ? t(`${label[0]} 점수가 가장 높음`, `Highest score in ${label[1]}`) : t(`${axis} 점수가 가장 높음`, `Highest score in ${axis}`);
  }
  return t('추천 조건 반영', 'Reflects your conditions');
};

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

// S15P21E201-1002 — 여행 ID 로 그 여행의 추천 작업 목록을 받아, 다시 볼 수 있는 결과를 고른다
// (서버 경로는 S15P21E201-1001). 하나가 아니라 목록으로 오는 이유는 맨 앞이 답이 아니기
// 때문이다 — 가장 최근 작업이 실패했으면 그 앞의 성공한 추천을 써야 한다. 맨 앞만 집으면
// 재시도가 한 번 실패했다는 이유로 멀쩡히 있던 추천을 잃는다.
type TripRecommendationJobDto = {
  jobId: string;
  type?: string | null;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'CANCELED' | 'EXPIRED';
};

export type TripRecommendationLookup =
  | { state: 'found'; jobId: string }
  | { state: 'in-progress'; jobId: string }
  | { state: 'none' }
  | { state: 'trip-not-found' }
  | { state: 'offline'; message: string }
  | { state: 'error'; message: string };

// 일정 편집 작업(ITEM_REMOVE 등)도 같은 여행에 붙어 목록에 섞여 온다. 서버가 일부러 안 걸러
// 주므로 여기서 거른다 — 이 화면이 볼 것은 일정 생성 작업뿐이다.
const GENERATION_JOB_TYPE = 'ITINERARY_GENERATION';

export async function findLatestRecommendationJob(tripId: string, accessToken: string | null): Promise<TripRecommendationLookup> {
  try {
    const jobs = await apiRequest<TripRecommendationJobDto[]>(`/api/v1/trips/${encodeURIComponent(tripId)}/recommendation-jobs`, { accessToken });
    const generations = (Array.isArray(jobs) ? jobs : []).filter((job) => !job.type || job.type === GENERATION_JOB_TYPE);
    const succeeded = generations.find((job) => job.status === 'SUCCEEDED');
    if (succeeded) return { state: 'found', jobId: succeeded.jobId };
    const running = generations.find((job) => job.status === 'PENDING' || job.status === 'RUNNING');
    if (running) return { state: 'in-progress', jobId: running.jobId };
    // 빈 목록도, 실패·취소·만료만 남은 것도 "지금 볼 수 있는 추천이 없다" 로 같다.
    return { state: 'none' };
  } catch (error) {
    if (error instanceof ApiClientError && (error.status === 0 || error.code === 'NETWORK_ERROR')) return { state: 'offline', message: error.message };
    // 🔴 404 는 빈 목록과 다르다. 빈 목록은 "내 여행인데 아직 안 만들었다" 라 생성으로 이어
    // 주면 되고, 404 는 "그런 여행이 없다(또는 남의 여행이다)" 라 생성을 권하면 안 된다.
    if (error instanceof ApiClientError && error.status === 404) return { state: 'trip-not-found' };
    // 이 경로가 아직 배포되지 않은 서버는 404 가 아니라 501 을 낸다 — 그때 "그런 여행이 없다"
    // 고 말하면 거짓말이 된다.
    if (error instanceof ApiClientError && error.status === 501) return { state: 'none' };
    return { state: 'error', message: t('추천 결과를 불러오지 못했어요.', 'Could not load the recommendation result.') };
  }
}

export const tripNotFoundRecommendations = (): RecommendationViewModel => ({
  state: 'error', courses: [], conflicts: [],
  message: t('그 여행을 찾을 수 없어요. 내 여행에서 다시 골라 주세요.', 'We could not find that trip. Please pick it again from your trips.'),
  itineraryId: null, placeCount: null, estimatedTravelMinutes: null,
});

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
