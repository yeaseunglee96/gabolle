import { apiRequest, ApiClientError } from '@/api/client';

export type RecommendationJobState = 'idle' | 'submitting' | 'accepted' | 'polling' | 'completed' | 'conflict' | 'failed' | 'cancelled' | 'unavailable';
export type RecommendationJobSnapshot = { state: RecommendationJobState; jobId: string | null; progress: number | null; stage: string | null; canCancel: boolean; errorMessage: string | null; resultRef: string | null };
export type RecommendationJobAcceptedDto = { jobId: string };
export type RecommendationJobPollDto = {
  jobId: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'EXPIRED';
  progress: { stage: string; percent: number };
  failure: { code: string; detail: string | null } | null;
  retryable: boolean;
  pollAfterSeconds: number | null;
};

export const unavailableJob = (message = '일정 생성 서버가 아직 준비되지 않았어요. 입력한 조건은 그대로 유지됩니다.'): RecommendationJobSnapshot => ({ state: 'unavailable', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: message, resultRef: null });
export function acceptJob(dto: RecommendationJobAcceptedDto): RecommendationJobSnapshot { return { state: 'accepted', jobId: dto.jobId, progress: 0, stage: '요청 접수', canCancel: false, errorMessage: null, resultRef: null }; }
export function adaptPolledJob(jobId: string, dto: RecommendationJobPollDto, previous?: RecommendationJobSnapshot): RecommendationJobSnapshot {
  const state: RecommendationJobState = ({ PENDING: 'accepted', RUNNING: 'polling', SUCCEEDED: 'completed', FAILED: 'failed', CANCELLED: 'cancelled', EXPIRED: 'failed' } as const)[dto.status];
  const reported = Math.max(0, Math.min(100, dto.progress.percent));
  const progress = reported === null ? previous?.progress ?? null : Math.max(previous?.progress ?? 0, reported);
  const errorMessage = dto.failure ? `${dto.failure.code}${dto.failure.detail ? ` · ${dto.failure.detail}` : ''}` : dto.status === 'EXPIRED' ? '일정 생성 작업이 만료됐어요. 다시 요청해 주세요.' : null;
  return { state, jobId, progress, stage: dto.progress.stage ?? previous?.stage ?? null, canCancel: false, errorMessage, resultRef: previous?.resultRef ?? null };
}
export interface RecommendationJobAdapter { submit(payload: unknown): Promise<RecommendationJobSnapshot>; poll(jobId: string, previous?: RecommendationJobSnapshot): Promise<RecommendationJobSnapshot>; }
function toFailure(error: unknown, jobId: string | null = null): RecommendationJobSnapshot {
  if (error instanceof ApiClientError && (error.status === 404 || error.status === 501 || error.code === 'NETWORK_ERROR')) return { ...unavailableJob(error.message), jobId };
  if (error instanceof ApiClientError && error.status === 409) return { state: 'conflict', jobId, progress: null, stage: null, canCancel: false, errorMessage: error.message, resultRef: null };
  return { state: 'failed', jobId, progress: null, stage: null, canCancel: false, errorMessage: error instanceof Error ? error.message : '일정을 만들지 못했어요. 잠시 후 다시 시도해 주세요.', resultRef: null };
}
export function createRecommendationJobAdapter(accessToken: string | null): RecommendationJobAdapter { return {
  async submit(_payload) { return unavailableJob('서버 여행 생성 계약에 이동수단과 정확한 활동 시간이 아직 없어 입력을 누락하지 않고 기다리고 있어요.'); },
  async poll(jobId, previous) { try { return adaptPolledJob(jobId, await apiRequest<RecommendationJobPollDto>(`/api/v1/jobs/${encodeURIComponent(jobId)}`, { accessToken }), previous); } catch (error) { return toFailure(error, jobId); } },
}; }
