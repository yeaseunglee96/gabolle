import { apiRequest, ApiClientError } from '@/api/client';

export type RecommendationJobState = 'idle' | 'submitting' | 'accepted' | 'polling' | 'completed' | 'conflict' | 'failed' | 'cancelled' | 'unavailable';
export type RecommendationJobSnapshot = { state: RecommendationJobState; jobId: string | null; progress: number | null; stage: string | null; canCancel: boolean; errorMessage: string | null; resultRef: string | null };
export type RecommendationJobAcceptedDto = { jobId: string };
export type RecommendationJobPollDto = { status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'CONFLICT'; progress?: number | null; stage?: string | null; canCancel?: boolean; errorMessage?: string | null; resultRef?: string | null };

export const unavailableJob = (message = '일정 생성 서버가 아직 준비되지 않았어요. 입력한 조건은 그대로 유지됩니다.'): RecommendationJobSnapshot => ({ state: 'unavailable', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: message, resultRef: null });
export function acceptJob(dto: RecommendationJobAcceptedDto): RecommendationJobSnapshot { return { state: 'accepted', jobId: dto.jobId, progress: 0, stage: '요청 접수', canCancel: false, errorMessage: null, resultRef: null }; }
export function adaptPolledJob(jobId: string, dto: RecommendationJobPollDto, previous?: RecommendationJobSnapshot): RecommendationJobSnapshot {
  const state: RecommendationJobState = ({ PENDING: 'accepted', RUNNING: 'polling', COMPLETED: 'completed', FAILED: 'failed', CANCELLED: 'cancelled', CONFLICT: 'conflict' } as const)[dto.status];
  const reported = dto.progress == null ? null : Math.max(0, Math.min(100, dto.progress));
  const progress = reported === null ? previous?.progress ?? null : Math.max(previous?.progress ?? 0, reported);
  return { state, jobId, progress, stage: dto.stage ?? previous?.stage ?? null, canCancel: Boolean(dto.canCancel), errorMessage: dto.errorMessage ?? null, resultRef: dto.resultRef ?? previous?.resultRef ?? null };
}
export interface RecommendationJobAdapter { submit(payload: unknown): Promise<RecommendationJobSnapshot>; poll(jobId: string, previous?: RecommendationJobSnapshot): Promise<RecommendationJobSnapshot>; }
function toFailure(error: unknown, jobId: string | null = null): RecommendationJobSnapshot {
  if (error instanceof ApiClientError && (error.status === 404 || error.status === 501 || error.code === 'NETWORK_ERROR')) return { ...unavailableJob(error.message), jobId };
  if (error instanceof ApiClientError && error.status === 409) return { state: 'conflict', jobId, progress: null, stage: null, canCancel: false, errorMessage: error.message, resultRef: null };
  return { state: 'failed', jobId, progress: null, stage: null, canCancel: false, errorMessage: error instanceof Error ? error.message : '일정을 만들지 못했어요. 잠시 후 다시 시도해 주세요.', resultRef: null };
}
export function createRecommendationJobAdapter(accessToken: string | null): RecommendationJobAdapter { return {
  async submit(payload) { try { return acceptJob(await apiRequest<RecommendationJobAcceptedDto>('/api/v1/recommendation-jobs', { method: 'POST', body: payload, accessToken })); } catch (error) { return toFailure(error); } },
  async poll(jobId, previous) { try { return adaptPolledJob(jobId, await apiRequest<RecommendationJobPollDto>(`/api/v1/recommendation-jobs/${encodeURIComponent(jobId)}`, { accessToken }), previous); } catch (error) { return toFailure(error, jobId); } },
}; }
