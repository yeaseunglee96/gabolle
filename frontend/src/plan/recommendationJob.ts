import { apiRequest, ApiClientError } from '@/api/client';
import { PLAN_UNAVAILABLE_MESSAGE, readableApiError } from '@/api/errorText';
import { getCurrentLanguage } from '@/i18n/languages';
import { localizeMessage } from '@/i18n/messages';
import { pickLanguage } from '@/i18n/pick';
import { cloneSharedTripAndJob, createTripAndRecommendationJob } from '@/api/tripApi';
import type { PlanDraft } from '@/plan/PlanProvider';
import { describeBlockedBy, readBlockedBy } from '@/plan/blockedByMessage';
import type { RecommendationJobStreamSnapshot } from '@/plan/recommendationJobStream';

export type RecommendationJobState = 'idle' | 'submitting' | 'accepted' | 'polling' | 'completed' | 'conflict' | 'consent-required' | 'failed' | 'cancelled' | 'unavailable';
export type RecommendationJobSnapshot = { state: RecommendationJobState; jobId: string | null; progress: number | null; stage: string | null; canCancel: boolean; errorMessage: string | null; resultRef: string | null; requiredConsent?: 'HEALTH_CONSTRAINTS';
  /** 서버 실패 코드 — 화면이 코드에 따라 다음 할 일을 다르게 말할 때 쓴다(S15P21E201-1739). 실패가 아니면 없다. */
  failureCode?: string | null };
export type RecommendationJobAcceptedDto = { jobId: string };
export type RecommendationJobPollDto = {
  jobId: string;
  status: 'QUEUED' | 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED' | 'CANCELLED' | 'EXPIRED';
  progress: { stage: string; percent: number };
  /**
   * `blockedBy` — 어느 조건이 후보를 다 걷어냈나(S15P21E201-1514). 서버가 모양을 바꿔도
   * 화면이 안 죽게 `unknown` 으로 받아 blockedByMessage.readBlockedBy 가 확인한다.
   * 설명할 수 없는 실패면 빈 목록이 온다.
   */
  failure: { code: string; detail: string | null; blockedBy?: unknown } | null;
  retryable: boolean;
  pollAfterSeconds: number | null;
};

// : 백엔드 RecommendationCodes.java의 실패 코드를 사람이 읽는 말로 옮긴다
// 여기 없으면 "ENGINE_NO_CANDIDATES" 같은 원문이 그대로 화면에 나갔었다. 코드가 뜻하는
// 실제 원인만 옮기고, 실패 단계(detail)는 로그용이라 사용자 문구에는 안 보여준다.
const JOB_FAILURE_MESSAGE: Record<string, [string, string]> = {
  ENGINE_NO_CANDIDATES: ['조건에 맞는 장소를 찾지 못했어요. 날짜·예산·취향 조건을 조금 넓혀서 다시 시도해 주세요.', "We couldn't find places matching your conditions. Try widening your dates, budget, or preferences."],
  RECOMMENDATION_NO_FEASIBLE_RESULT: ['조건을 모두 만족하는 일정을 만들지 못했어요. 조건을 조정해서 다시 시도해 주세요.', 'Could not build an itinerary that satisfies every condition. Try adjusting them.'],
  ENGINE_ORIGIN_MISSING: ['출발지 정보가 없어요. 여행 조건에서 출발지를 다시 확인해 주세요.', 'Missing starting point. Please check it in your trip conditions.'],
  ENGINE_UNAVAILABLE: ['추천 엔진에 일시적인 문제가 있어요. 잠시 후 다시 시도해 주세요.', 'The recommendation engine is temporarily unavailable. Please try again shortly.'],
  ENGINE_NOT_CONFIGURED: ['추천 엔진에 일시적인 문제가 있어요. 잠시 후 다시 시도해 주세요.', 'The recommendation engine is temporarily unavailable. Please try again shortly.'],
  ITINERARY_VERSION_CONFLICT: ['다른 곳에서 먼저 일정이 바뀌었어요. 새로고침 후 다시 시도해 주세요.', 'The itinerary changed elsewhere first. Please refresh and try again.'],
  // 추천 실행기가 꽉 차서 서버가 이 작업을 받지 못했다(S15P21E201-1688). 다시 누르면 새 작업으로 간다.
  SERVER_BUSY: ['지금 요청이 많아요. 잠시 뒤 다시 시도해 주세요.', 'We are handling a lot of requests right now. Please try again in a moment.'],
  // 오늘 출발 당일치기를 부산 20:31 뒤에 만들면 남은 시간이 없다(백엔드 !1734, 다시 시도 불가 — 다시 해도 같은 답). S15P21E201-1739.
  ITINERARY_NO_TIME_LEFT_TODAY: ['오늘은 남은 시간이 없어요. 여행을 내일부터로 바꿔 주세요.', "There's no time left today. Try starting your trip tomorrow."],
};
/**
 * 같은 실패 코드라도 «어느 단계에서» 멈췄는지에 따라 할 말이 다르다.
 *
 * 🔴 `CONSTRAINT_EVALUATION` 에서 멈춘 «맞는 일정 없음» 은 **꼭 지켜야 하는 조건**
 *    (알레르기 · 식단 · 이동)이 후보를 전부 걷어낸 것이다. 그런데 화면은 「조건을
 *    조정해 주세요」 라고만 말해서, 사용자는 **어느 조건인지 모른 채** 날짜나 예산을
 *    넓혀 보고 또 실패한다 — 그 둘은 이 실패와 아무 상관이 없다.
 *
 * 🔴 「맞는 곳이 없다」 가 아니라 **「확인하지 못했다」** 라고 적는다. 운영 자료에
 *    알레르기·식단 표식이 아직 0건이라(S15P21E201-1468), 서버는 «위험한 곳을 골라낸» 것이
 *    아니라 «안전한 곳인지 확인할 수 없어» 전부 뺀 것이다. 둘은 다른 말이고, 앞의 말로
 *    적으면 사용자는 부산에 자기가 먹을 것이 없다고 읽는다.
 */
const STAGE_FAILURE_MESSAGE: Record<string, Record<string, [string, string]>> = {
  RECOMMENDATION_NO_FEASIBLE_RESULT: {
    CONSTRAINT_EVALUATION: [
      '알레르기 · 식단 · 이동처럼 «꼭 지켜야 하는» 조건에 맞는 곳을 확인하지 못했어요. 그 조건을 빼거나 줄이고 다시 만들어 주세요.',
      "We could not verify places that meet your must-have conditions (allergies, diet, mobility). Try removing or easing those and building again.",
    ],
  },
};

const DEFAULT_JOB_FAILURE_MESSAGE = ['일정을 만드는 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.', 'Something went wrong while building your itinerary. Please try again shortly.'] as const;

// 🔴 화면 언어로 고른다 — S15P21E201-1776. 전에는 서버용 언어(ko|en 뿐)로 골라서 일본어·중국어 화면에 실패 문구가
//    영어로 나갔고, 한국어로 박힌 문구(엔진 없음 · 만료)와 서버 원문(409 · 403 · 그 밖의 오류)은 영어 화면에도 한국어로
//    나갔다. 화면(generating.tsx)은 errorMessage 를 그대로 그리므로 여기서 한 번에 옮긴다.
const jobTx = (ko: string, en: string) => pickLanguage(getCurrentLanguage(), { ko, en });
const jobText = (pair: readonly [string, string]) => jobTx(pair[0], pair[1]);
const localizeServer = (message: string | null | undefined) => (message ? localizeMessage(jobTx, message) : null);
const EXPIRED_MESSAGE = ['일정 생성 작업이 만료됐어요. 다시 요청해 주세요.', 'This itinerary request expired. Please request it again.'] as const;

export const unavailableJob = (message = PLAN_UNAVAILABLE_MESSAGE): RecommendationJobSnapshot => ({ state: 'unavailable', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: localizeServer(message), resultRef: null });
export function acceptJob(dto: RecommendationJobAcceptedDto): RecommendationJobSnapshot { return { state: 'accepted', jobId: dto.jobId, progress: 0, stage: '요청 접수', canCancel: false, errorMessage: null, resultRef: null }; }
export function adaptPolledJob(jobId: string, dto: RecommendationJobPollDto, previous?: RecommendationJobSnapshot): RecommendationJobSnapshot {
  const state: RecommendationJobState = ({ QUEUED: 'accepted', PENDING: 'accepted', RUNNING: 'polling', SUCCEEDED: 'completed', FAILED: 'failed', CANCELED: 'cancelled', CANCELLED: 'cancelled', EXPIRED: 'failed' } as const)[dto.status];
  const reported = Math.max(0, Math.min(100, dto.progress.percent));
  const progress = reported === null ? previous?.progress ?? null : Math.max(previous?.progress ?? 0, reported);
  // 🔴 서버가 «어느 조건이» 막았는지 알려 주면 그것이 먼저다 (S15P21E201-1514).
  //    못 알려 주면(빈 목록 · 모르는 갈래뿐) 예전처럼 단계·코드별 문구로 떨어진다.
  const failureMessage = dto.failure
    ? describeBlockedBy(readBlockedBy(dto.failure.blockedBy))
      ?? jobText(STAGE_FAILURE_MESSAGE[dto.failure.code]?.[dto.failure.detail ?? '']
        ?? JOB_FAILURE_MESSAGE[dto.failure.code]
        ?? DEFAULT_JOB_FAILURE_MESSAGE)
    : null;
  const errorMessage = dto.failure ? failureMessage : dto.status === 'EXPIRED' ? jobText(EXPIRED_MESSAGE) : null;
  return { state, jobId, progress, stage: dto.progress.stage ?? previous?.stage ?? null, canCancel: false, errorMessage, resultRef: previous?.resultRef ?? null, failureCode: dto.failure?.code ?? null };
}
// — SSE(GET /api/v1/jobs/{jobId}/progress)가 보내는 건 폴링과 모양이 다르다
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
  if (error instanceof ApiClientError && (error.status === 404 || error.status === 501 || error.code === 'NETWORK_ERROR')) return { ...unavailableJob(), jobId };
  if (error instanceof ApiClientError && error.status === 409) return { state: 'conflict', jobId, progress: null, stage: null, canCancel: false, errorMessage: localizeServer(error.message), resultRef: null };
  if (error instanceof ApiClientError && error.status === 403 && error.code === 'HEALTH_CONSENT_REQUIRED') return { state: 'consent-required', jobId, progress: null, stage: null, canCancel: false, errorMessage: localizeServer(error.message), resultRef: null, requiredConsent: 'HEALTH_CONSTRAINTS' };
  return { state: 'failed', jobId, progress: null, stage: null, canCancel: false, errorMessage: localizeServer(readableApiError(error, getCurrentLanguage() === 'ko')),
    resultRef: null };
}
export function createRecommendationJobAdapter(accessToken: string | null): RecommendationJobAdapter { return {
  async submit(draft) { try { return acceptJob(draft.cloneShareToken ? await cloneSharedTripAndJob(draft.cloneShareToken, draft, accessToken) : await createTripAndRecommendationJob(draft, accessToken)); } catch (error) { return toFailure(error); } },
  async poll(jobId, previous) { try { return adaptPolledJob(jobId, await apiRequest<RecommendationJobPollDto>(`/api/v1/jobs/${encodeURIComponent(jobId)}`, { accessToken }), previous); } catch (error) { return toFailure(error, jobId); } },
}; }
