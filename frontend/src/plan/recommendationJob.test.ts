import { adaptStreamedJob } from './recommendationJob';

/**
 * — SSE(`GET /api/v1/jobs/{jobId}/progress`)가 보내는 실제 모양
 * ({@code JobProgressBroker.JobProgressSnapshot}: jobId·status·stage·percent·code, 중첩 없음)을
 * adaptPolledJob과 같은 판정(상태 매핑·진행률 역행 방지·실패 문구)으로 옮기는지 본다.
 */
describe('adaptStreamedJob', () => {
  const jobId = '02ae7ea5-0688-4d8c-a310-b42031ff47a2';

  it('진행 중 스냅샷을 polling 상태와 같은 모양으로 옮긴다', () => {
    const next = adaptStreamedJob(jobId, { jobId, status: 'RUNNING', stage: 'RANKING', percent: 40, code: null });
    expect(next.state).toBe('polling');
    expect(next.stage).toBe('RANKING');
    expect(next.progress).toBe(40);
  });

  it('완료 스냅샷은 completed로 옮긴다', () => {
    const next = adaptStreamedJob(jobId, { jobId, status: 'SUCCEEDED', stage: 'COMPLETED', percent: 100, code: null });
    expect(next.state).toBe('completed');
    expect(next.progress).toBe(100);
  });

  it('실패 스냅샷의 code를 사람이 읽는 문구로 옮긴다 — polling 경로와 같은 사전을 쓴다', () => {
    const next = adaptStreamedJob(jobId, { jobId, status: 'FAILED', stage: 'CANDIDATE_GENERATION', percent: 0, code: 'ENGINE_NO_CANDIDATES' });
    expect(next.state).toBe('failed');
    expect(next.errorMessage).toContain('조건에 맞는 장소를 찾지 못했어요');
  });

  it('🔴 진행률이 뒤로 가지 않는다 — 늦게 도착한 스냅샷이 앞선 값보다 낮아도 무시한다', () => {
    const previous = adaptStreamedJob(jobId, { jobId, status: 'RUNNING', stage: 'RANKING', percent: 60, code: null });
    const stale = adaptStreamedJob(jobId, { jobId, status: 'RUNNING', stage: 'CANDIDATE_GENERATION', percent: 10, code: null }, previous);
    expect(stale.progress).toBe(60);
  });
});
