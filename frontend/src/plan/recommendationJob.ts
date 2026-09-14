import { apiRequest, ApiClientError, getApiLanguage } from '@/api/client';
import { cloneSharedTripAndJob, createTripAndRecommendationJob } from '@/api/tripApi';
import type { PlanDraft } from '@/plan/PlanProvider';
import type { RecommendationJobStreamSnapshot } from '@/plan/recommendationJobStream';

export type RecommendationJobState = 'idle' | 'submitting' | 'accepted' | 'polling' | 'completed' | 'conflict' | 'consent-required' | 'failed' | 'cancelled' | 'unavailable';
// 'consent-required'의 requiredConsent: S15P21E201-549(백엔드, 2026-09-11)가 새로 건 403 둘.
// HEALTH_CONSENT_REQUIRED — 요청에 알레르기나 필수 식단 제약이 있는데 HEALTH_CONSTRAINTS
// 동의가 없다. PRECISE_LOCATION_CONSENT_REQUIRED는 지금 이 화면(일정 생성)에서는 안 난다 —
// 방문 인증 쪽 계약이라 여기 타입에는 안 넣는다(코드가 없는 경로를 지어내지 않는다).
export type RecommendationJobSnapshot = { state: RecommendationJobState; jobId: string | null; progress: number | null; stage: string | null; canCancel: boolean; errorMessage: string | null; resultRef: string | null; requiredConsent?: 'HEALTH_CONSTRAINTS' };
export type RecommendationJobAcceptedDto = { jobId: string };
export type RecommendationJobPollDto = {
  jobId: string;
  status: 'QUEUED' | 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED' | 'CANCELLED' | 'EXPIRED';
  progress: { stage: string; percent: number };
  failure: { code: string; detail: string | null } | null;
  retryable: boolean;
  pollAfterSeconds: number | null;
};

// S15P21E201-919: 백엔드 RecommendationCodes.java의 실패 코드를 사람이 읽는 말로 옮긴다 —
// 여기 없으면 "ENGINE_NO_CANDIDATES" 같은 원문이 그대로 화면에 나갔었다. 코드가 뜻하는
// 실제 원인만 옮기고, 실패 단계(detail)는 로그용이라 사용자 문구에는 안 보여준다.
const JOB_FAILURE_MESSAGE: Record<string, [string, string]> = {
  ENGINE_NO_CANDIDATES: ['조건에 맞는 장소를 찾지 못했어요. 날짜·예산·취향 조건을 조금 넓혀서 다시 시도해 주세요.', "We couldn't find places matching your conditions. Try widening your dates, budget, or preferences."],
  RECOMMENDATION_NO_FEASIBLE_RESULT: ['조건을 모두 만족하는 일정을 만들지 못했어요. 조건을 조정해서 다시 시도해 주세요.', 'Could not build an itinerary that satisfies every condition. Try adjusting them.'],
  ENGINE_ORIGIN_MISSING: ['출발지 정보가 없어요. 여행 조건에서 출발지를 다시 확인해 주세요.', 'Missing starting point. Please check it in your trip conditions.'],
  ENGINE_UNAVAILABLE: ['추천 엔진에 일시적인 문제가 있어요. 잠시 후 다시 시도해 주세요.', 'The recommendation engine is temporarily unavailable. Please try again shortly.'],
  ENGINE_NOT_CONFIGURED: ['추천 엔진에 일시적인 문제가 있어요. 잠시 후 다시 시도해 주세요.', 'The recommendation engine is temporarily unavailable. Please try again shortly.'],
  ITINERARY_VERSION_CONFLICT: ['다른 곳에서 먼저 일정이 바뀌었어요. 새로고침 후 다시 시도해 주세요.', 'The itinerary changed elsewhere first. Please refresh and try again.'],
};
const DEFAULT_JOB_FAILURE_MESSAGE = ['일정을 만드는 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.', 'Something went wrong while building your itinerary. Please try again shortly.'] as const;

export const unavailableJob = (message = '일정 생성 서버가 아직 준비되지 않았어요. 입력한 조건은 그대로 유지됩니다.'): RecommendationJobSnapshot => ({ state: 'unavailable', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: message, resultRef: null });
export function acceptJob(dto: RecommendationJobAcceptedDto): RecommendationJobSnapshot { return { state: 'accepted', jobId: dto.jobId, progress: 0, stage: '요청 접수', canCancel: false, errorMessage: null, resultRef: null }; }
export function adaptPolledJob(jobId: string, dto: RecommendationJobPollDto, previous?: RecommendationJobSnapshot): RecommendationJobSnapshot {
  const state: RecommendationJobState = ({ QUEUED: 'accepted', PENDING: 'accepted', RUNNING: 'polling', SUCCEEDED: 'completed', FAILED: 'failed', CANCELED: 'cancelled', CANCELLED: 'cancelled', EXPIRED: 'failed' } as const)[dto.status];
  const reported = Math.max(0, Math.min(100, dto.progress.percent));
  const progress = reported === null ? previous?.progress ?? null : Math.max(previous?.progress ?? 0, reported);
  const isKo = getApiLanguage() !== 'en';
  const errorMessage = dto.failure ? (JOB_FAILURE_MESSAGE[dto.failure.code] ?? DEFAULT_JOB_FAILURE_MESSAGE)[isKo ? 0 : 1] : dto.status === 'EXPIRED' ? '일정 생성 작업이 만료됐어요. 다시 요청해 주세요.' : null;
  return { state, jobId, progress, stage: dto.progress.stage ?? previous?.stage ?? null, canCancel: false, errorMessage, resultRef: previous?.resultRef ?? null };
}
// S15P21E201-69 — SSE(GET /api/v1/jobs/{jobId}/progress)가 보내는 건 폴링과 모양이 다르다
// ({jobId, status, stage, percent, code} — 중첩된 progress 객체가 아니다). 판정 로직은
// adaptPolledJob 하나만 있으면 되므로, 여기서는 모양만 그 입력(RecommendationJobPollDto)으로
// 바꿔 그대로 넘긴다 — 상태 매핑·진행률 역행 방지·실패 문구를 두 번 쓰지 않는다.
export function adaptStreamedJob(
  jobId: string,
  snapshot: RecommendationJobStreamSnapshot,
  previous?: RecommendationJobSnapshot,
): RecommendationJobSnapshot {
  return adaptPolledJob(jobId, {
    jobId: snapshot.jobId,
    status: snapshot.status,
    progress: { stage: snapshot.stage ?? '', percent: snapshot.percent },
    failure: snapshot.code ? { code: snapshot.code, detail: null } : null,
    retryable: false,
    pollAfterSeconds: null,
  }, previous);
}

export interface RecommendationJobAdapter { submit(draft: PlanDraft): Promise<RecommendationJobSnapshot>; poll(jobId: string, previous?: RecommendationJobSnapshot): Promise<RecommendationJobSnapshot>; }
function toFailure(error: unknown, jobId: string | null = null): RecommendationJobSnapshot {
  if (error instanceof ApiClientError && (error.status === 404 || error.status === 501 || error.code === 'NETWORK_ERROR')) return { ...unavailableJob(error.message), jobId };
  if (error instanceof ApiClientError && error.status === 409) return { state: 'conflict', jobId, progress: null, stage: null, canCancel: false, errorMessage: error.message, resultRef: null };
  if (error instanceof ApiClientError && error.status === 403 && error.code === 'HEALTH_CONSENT_REQUIRED') return { state: 'consent-required', jobId, progress: null, stage: null, canCancel: false, errorMessage: error.message, resultRef: null, requiredConsent: 'HEALTH_CONSTRAINTS' };
  return { state: 'failed', jobId, progress: null, stage: null, canCancel: false, errorMessage: error instanceof Error ? error.message : '일정을 만들지 못했어요. 잠시 후 다시 시도해 주세요.', resultRef: null };
}
export function createRecommendationJobAdapter(accessToken: string | null): RecommendationJobAdapter { return {
  async submit(draft) { try { return acceptJob(draft.cloneShareToken ? await cloneSharedTripAndJob(draft.cloneShareToken, draft, accessToken) : await createTripAndRecommendationJob(draft, accessToken)); } catch (error) { return toFailure(error); } },
  async poll(jobId, previous) { try { return adaptPolledJob(jobId, await apiRequest<RecommendationJobPollDto>(`/api/v1/jobs/${encodeURIComponent(jobId)}`, { accessToken }), previous); } catch (error) { return toFailure(error, jobId); } },
}; }
