import { apiRequest, ApiClientError } from '@/api/client';
import { getCurrentLanguage } from '@/i18n/languages';
import { pickLanguage } from '@/i18n/pick';
import { koreanSubject } from '@/i18n/korean';

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
// tripId 는에서 늘어난 칸이다. 그 판이 아직 안 올라간 서버도 있으므로
// 없을 수 있다 — 없으면 담아두기·빼기가 기기에만 남는다(recommendationActions.ts 머리말).
export type RecommendationJobResultDto = { status: 'COMPLETED' | 'PARTIAL' | 'FAILED'; items?: RecommendationCourseDto[]; itineraryId?: string | null; fallbackMode?: FallbackMode; conflicts?: string[]; errorMessage?: string | null; placeCount?: number | null; estimatedTravelMinutes?: number | null; tripId?: string | null };

export type RecommendationCourse = RecommendationCourseDto & { reasons: string[]; actionState: CourseActionState };
export type RecommendationViewModel = { state: RecommendationViewState; courses: RecommendationCourse[]; conflicts: string[]; message: string; itineraryId: string | null; placeCount: number | null; estimatedTravelMinutes: number | null; tripId: string | null };

// 🔴 화면 문구는 고른 언어의 번역표로 — S15P21E201-1767. 전에는 서버용 언어(ko|en 뿐)로 골라서 일본어·중국어
//    화면에 추천 이유가 「Matches your interests」처럼 영어로 떴다(Play 35 실기기). 표에 줄은 이미 있었다.
//    intent.ts(S15P21E201-1517)와 같은 방식이다. 표에 없는 문구는 전과 같이 영어로 떨어진다.
const t = (ko: string, en: string) => pickLanguage(getCurrentLanguage(), { ko, en });

// : 이 사전은 한때 BEACH_PREFERENCE 등 다섯 개였는데 백엔드 계약이 통째로
// 갈아엎어진 뒤에도(BaselineCandidateScorer·RecommendationCodes, back/dev) 안 따라가서
// 실제로 오는 코드와 하나도 안 겹쳐 모든 카드가 안전장치 문구만 중복 표시하고 있었다.
// 지금 실제로 오는 코드로 교체한다.
const REASON: Record<string, [string, string]> = {
  MUST_VISIT_PLACE: ['내가 꼭 가고 싶다고 적은 곳', 'A place you marked as must-visit'],
  NEAR_ORIGIN: ['출발지에서 가까움', 'Close to your starting point'],
  TAG_MATCH_INTEREST: ['관심 카테고리와 일치', 'Matches your interests'],
  TAG_MATCH_ATMOSPHERE: ['선호 분위기와 일치', 'Matches your preferred mood'],
  TAG_MATCH_CUISINE: ['음식 취향과 일치', 'Matches your food preferences'],
  POPULAR: ['인기 있는 곳', 'A popular spot'],
  EDITORIAL_PICK: ['에디터 추천', "Editor's pick"],
  DIVERSITY_RERANKED: ['다양성을 위해 순서 조정됨', 'Reordered for variety'],
  // 「걷기만」 고른 여행 — 고른 범위 밖이지만 출발지에서 걸어서 30분 안이라 첫날에 넣은 곳(백엔드 !1630).
  WALK_ONLY_FIRST_DAY: ['출발지에서 걸어갈 수 있어요', 'Within walking distance of your starting point'],
  // 일정 장소 카드에 이유 한 줄을 달면서 채운 것들(S15P21E201-1645). 서버 코드에서 뽑은 전부 — 백엔드가 목록을 줬다.
  USER_ADDED: ['내가 직접 넣은 곳', 'A place you added'],
  SEED_FROM_SHARED_ITINERARY: ['공유받은 일정에서 가져온 곳', 'From a shared itinerary'],
  TASTE_VECTOR_MATCH: ['내가 좋아한 곳들과 비슷함', 'Similar to places you liked'],
  PREF_ALIGNED_LOCALITY: ['현지 분위기 취향과 맞음', 'Matches your local-vibe preference'],
  PREF_ALIGNED_QUIETNESS: ['조용한 곳 취향과 맞음', 'Matches your preference for quiet'],
  PREF_ALIGNED_TOURIST_PREFERENCE: ['관광지 취향과 맞음', 'Matches your sightseeing preference'],
  PREF_ALIGNED_SHADE_PREFERENCE: ['그늘 취향과 맞음', 'Matches your shade preference'],
  PREF_ALIGNED_SLOPE_PREFERENCE: ['완만한 길 취향과 맞음', 'Matches your preference for gentle slopes'],
  WITHIN_BUDGET: ['예산 안', 'Within your budget'],
};

// TOP_CONTRIBUTOR_<축 이름> — 축 이름은 score_components 맵의 키를 대소문자까지 그대로
// 붙인 것이라 고정된 목록이 아니다(RecommendationCodes.java 주석: 대문자로 바꾸면 코드와
// 어긋난다). 아는 축은 문구를 달고, 모르는 축이 와도 최소한 서로 다른 텍스트가 보이도록
// 축 이름을 그대로 보여준다 — 전부 같은 안전장치 문구로 뭉개지 않는다.
const TOP_CONTRIBUTOR_PREFIX = 'TOP_CONTRIBUTOR_';
const AXIS_LABEL: Record<string, [string, string, string, string]> = {
  distance: ['거리', 'distance', '다른 곳보다 거리가 돋보임', 'Stands out for distance'],
  interest: ['관심 카테고리', 'your interests', '다른 곳보다 관심 카테고리가 돋보임', 'Stands out for your interests'],
  atmosphere: ['분위기', 'mood', '다른 곳보다 분위기가 돋보임', 'Stands out for mood'],
  cuisine: ['음식 취향', 'food preferences', '다른 곳보다 음식 취향이 돋보임', 'Stands out for food preferences'],
  preferenceAlignment: ['취향 일치도', 'preference match', '다른 곳보다 취향 일치도가 돋보임', 'Stands out for preference match'],
  popularity: ['인기도', 'popularity', '다른 곳보다 인기도가 돋보임', 'Stands out for popularity'],
};

export const reasonLabel = (code: string): string => {
  if (REASON[code]) return t(...REASON[code]);
  if (code.startsWith(TOP_CONTRIBUTOR_PREFIX)) {
    const axis = code.slice(TOP_CONTRIBUTOR_PREFIX.length);
    const label = AXIS_LABEL[axis];
    // 🔴 「점수가 가장 높은 축」이 아니라 «같은 결과 안에서 다른 곳보다 가장 두드러진 축»이다(백엔드 !1634 — 전에는 89% 가
    //    「거리」였다). 그래서 「다른 곳보다 ○○이 돋보임」이라 말한다(S15P21E201-1640).
    // 🔴 아는 축은 «완성된 문구»로 번역표를 찾는다(S15P21E201-1767). 값을 끼운 틀은 번역표에 없어서
    //    Play 37 실기기 일본어 화면에 「Stands out for distance」가 그대로 떴다. 모르는 축만 틀로 만든다.
    if (label) return t(label[2], label[3]);
    return t(`다른 곳보다 ${axis}${koreanSubject(axis)} 돋보임`, `Stands out for ${axis}`);
  }
  return t('추천 조건 반영', 'Reflects your conditions');
};

/**
 * 일정 장소 카드에 달 이유 «한 줄» — 여럿이면 하나만(S15P21E201-1645). 보일 이유가 없으면 null 이고, 그때는 줄을 안 그린다.
 *
 * 🔴 순서는 사용자가 정했다(백엔드 제안 순서): 내가 고른 것 → 테마·취향 → 설문 → 그 장소만의 특징 → 출발지 → 인기.
 *    옛 일정은 거의 모든 곳에 NEAR_ORIGIN·TOP_CONTRIBUTOR_distance 가 저장돼 있어(옛 규칙, 476곳 중 476·470),
 *    그 둘이 앞이면 모든 카드가 같은 말을 한다. 「왜 여기 있나」를 말하는 걷기 첫날·공유 일정은 내가 고른 것 바로 뒤,
 *    예산 안·에디터 추천은 맨 뒤다. DIVERSITY_RERANKED 는 순서를 섞었다는 내부 표시라 사람에게 안 보인다.
 */
const REASON_RANK: ReadonlyArray<(code: string) => boolean> = [
  (code) => code === 'MUST_VISIT_PLACE' || code === 'USER_ADDED',
  (code) => code === 'WALK_ONLY_FIRST_DAY' || code === 'SEED_FROM_SHARED_ITINERARY',
  (code) => code.startsWith('TAG_MATCH_') || code === 'TASTE_VECTOR_MATCH',
  (code) => code.startsWith('PREF_ALIGNED_'),
  (code) => code.startsWith(TOP_CONTRIBUTOR_PREFIX),
  (code) => code === 'NEAR_ORIGIN',
  (code) => code === 'POPULAR',
  (code) => code === 'WITHIN_BUDGET' || code === 'EDITORIAL_PICK',
];

export function pickReasonLine(codes: readonly string[] | null | undefined): string | null {
  if (!codes?.length) return null;
  for (const matches of REASON_RANK) {
    // 모르는 코드는 「추천 조건 반영」으로 뭉개지 말고 건너뛴다 — 한 줄은 뜻이 있을 때만 단다.
    const code = codes.find((entry) => matches(entry) && (REASON[entry] || entry.startsWith(TOP_CONTRIBUTOR_PREFIX)));
    if (code) return reasonLabel(code);
  }
  return null;
}

export function adaptRecommendationResult(dto: RecommendationJobResultDto): RecommendationViewModel {
  const placeCount = dto.placeCount ?? null;
  const estimatedTravelMinutes = dto.estimatedTravelMinutes ?? null;
  if (dto.status === 'FAILED') return { state: 'error', courses: [], conflicts: dto.conflicts ?? [], message: dto.errorMessage ?? t('추천 결과를 불러오지 못했어요.', 'Could not load the recommendation result.'), itineraryId: null, placeCount, estimatedTravelMinutes, tripId: dto.tripId ?? null };
  const courses = (dto.items ?? []).map((item) => ({ ...item, reasons: item.reasonCodes.map(reasonLabel), actionState: 'idle' as const }));
  if (!courses.length) return { state: 'empty-conflict', courses: [], conflicts: dto.conflicts ?? [], message: t('조건을 만족하는 추천을 찾지 못했어요.', 'No recommendations matched your conditions.'), itineraryId: null, placeCount, estimatedTravelMinutes, tripId: dto.tripId ?? null };
  const state: RecommendationViewState = dto.status === 'PARTIAL' ? 'partial' : (dto.fallbackMode && dto.fallbackMode !== 'MODEL' ? 'fallback' : 'success');
  return { state, courses, conflicts: dto.conflicts ?? [], message: state === 'partial' ? t('일부 정보가 확인되지 않은 결과예요.', 'Some details in this result are unconfirmed.') : state === 'fallback' ? t('기본 추천 방식으로 구성했어요.', 'We used the baseline recommendation method.') : t('조건에 맞는 코스를 찾았어요.', 'We found courses that match your conditions.'), itineraryId: dto.itineraryId ?? courses.find((item) => item.itineraryId)?.itineraryId ?? null, placeCount, estimatedTravelMinutes, tripId: dto.tripId ?? null };
}

export const unavailableRecommendations = (): RecommendationViewModel => ({ state: 'unavailable', courses: [], conflicts: [], message: t('아직 생성된 추천이 없어요. 여행 조건을 확인하고 생성을 시작해 주세요.', "No recommendations have been created yet. Check your trip conditions and start generating."), itineraryId: null, placeCount: null, estimatedTravelMinutes: null, tripId: null });

// — 여행 ID 로 그 여행의 추천 작업 목록을 받아, 다시 볼 수 있는 결과를 고른다
// (서버 경로는. 하나가 아니라 목록으로 오는 이유는 맨 앞이 답이 아니기
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
    // 404 는 빈 목록과 다르다. 빈 목록은 "내 여행인데 아직 안 만들었다" 라 생성으로 이어
    // 주면 되고, 404 는 "그런 여행이 없다(또는 남의 여행이다)" 라 생성을 권하면 안 된다.
    // 서버는 그 404 에 TRIP_NOT_FOUND 를 반드시 싣는다 — 코드가 그 값일 때만 그렇게 읽는다.
    if (error instanceof ApiClientError && error.status === 404 && error.code === 'TRIP_NOT_FOUND') return { state: 'trip-not-found' };
    if (error instanceof ApiClientError && (error.status === 405 || error.status === 404)) return { state: 'none' };
    return { state: 'error', message: t('추천 결과를 불러오지 못했어요.', 'Could not load the recommendation result.') };
  }
}

export async function loadRecommendationResult(jobId: string, accessToken: string | null): Promise<RecommendationViewModel> {
  try {
    return adaptRecommendationResult(await apiRequest<RecommendationJobResultDto>(`/api/v1/recommendation-jobs/${encodeURIComponent(jobId)}`, { accessToken }));
  } catch (error) {
    if (error instanceof ApiClientError && (error.status === 0 || error.code === 'NETWORK_ERROR')) {
      return { state: 'offline', courses: [], conflicts: [], message: error.message, itineraryId: null, placeCount: null, estimatedTravelMinutes: null, tripId: null };
    }
    if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return unavailableRecommendations();
    return { state: 'error', courses: [], conflicts: [], message: error instanceof Error ? error.message : t('추천 결과를 불러오지 못했어요.', 'Could not load the recommendation result.'), itineraryId: null, placeCount: null, estimatedTravelMinutes: null, tripId: null };
  }
}
