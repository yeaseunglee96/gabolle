export type RecommendationJobState =
  | 'idle'
  | 'submitting'
  | 'accepted'
  | 'polling'
  | 'completed'
  | 'conflict'
  | 'failed'
  | 'cancelled'
  | 'unavailable';

export type RecommendationJobSnapshot = {
  state: RecommendationJobState;
  jobId: string | null;
  progress: number | null;
  stage: string | null;
  canCancel: boolean;
  errorMessage: string | null;
  resultRef: string | null;
};

export type RecommendationJobAcceptedDto = { jobId: string };
export type RecommendationJobPollDto = {
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'CONFLICT';
  progress?: number | null;
  stage?: string | null;
  canCancel?: boolean;
  errorMessage?: string | null;
  resultRef?: string | null;
};

export const unavailableJob = (message = '일정 생성 기능을 준비하고 있어요. 입력한 조건은 그대로 유지됩니다.'): RecommendationJobSnapshot => ({ state: 'unavailable', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: message, resultRef: null });

export function acceptJob(dto: RecommendationJobAcceptedDto): RecommendationJobSnapshot {
  return { state: 'accepted', jobId: dto.jobId, progress: 0, stage: '요청 접수', canCancel: false, errorMessage: null, resultRef: null };
}

export function adaptPolledJob(jobId: string, dto: RecommendationJobPollDto): RecommendationJobSnapshot {
  const state: RecommendationJobState = ({ PENDING: 'accepted', RUNNING: 'polling', COMPLETED: 'completed', FAILED: 'failed', CANCELLED: 'cancelled', CONFLICT: 'conflict' } as const)[dto.status];
  return { state, jobId, progress: dto.progress == null ? null : Math.max(0, Math.min(100, dto.progress)), stage: dto.stage ?? null, canCancel: Boolean(dto.canCancel), errorMessage: dto.errorMessage ?? null, resultRef: dto.resultRef ?? null };
}

/** API 계약이 열리면 이 경계에서 POST(202)와 GET polling을 구현한다. */
export interface RecommendationJobAdapter {
  submit(): Promise<RecommendationJobSnapshot>;
  poll(jobId: string): Promise<RecommendationJobSnapshot>;
  cancel?(jobId: string): Promise<RecommendationJobSnapshot>;
}

export const unavailableRecommendationJobAdapter: RecommendationJobAdapter = {
  async submit() { return unavailableJob(); },
  async poll() { return unavailableJob('진행 상태를 확인할 서버 연결이 아직 준비되지 않았어요.'); },
};
