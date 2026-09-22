import { adaptPolledJob, adaptStreamedJob } from './recommendationJob';

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

/**
 * 실패 문구가 «어느 단계에서 멈췄는지» 까지 보는가 — S15P21E201-1468.
 *
 * 🔴 운영 실측(2026-09-22): 알레르기나 식단을 하나라도 고르면 추천이 반드시 실패한다.
 *    장소 자료에 알레르기 표식이 0건이라 모든 후보가 「확인 불가」로 걸러지기 때문이다.
 *    그런데 화면은 「조건을 조정해 주세요」 라고만 말해서, 사용자는 날짜와 예산을 넓혀
 *    보고 또 실패한다 — 그 둘은 이 실패와 아무 상관이 없다.
 */
describe('꼭 지켜야 하는 조건이 후보를 다 걷어냈을 때', () => {
  const jobId = '02ae7ea5-0688-4d8c-a310-b42031ff47a2';
  const failed = (detail: string) => adaptPolledJob(jobId, {
    jobId, status: 'FAILED',
    progress: { stage: detail, percent: 70 },
    failure: { code: 'RECOMMENDATION_NO_FEASIBLE_RESULT', detail },
    retryable: false, pollAfterSeconds: null,
  });

  it('제약 평가에서 멈추면 어느 조건인지 짚어 준다', () => {
    const message = failed('CONSTRAINT_EVALUATION').errorMessage ?? '';
    expect(message).toContain('알레르기');
    expect(message).toContain('식단');
  });

  it('🔴 「맞는 곳이 없다」가 아니라 「확인하지 못했다」라고 적는다', () => {
    // 서버는 위험한 곳을 골라낸 것이 아니라, 안전한지 «확인할 수 없어» 전부 뺐다.
    // 앞의 말로 적으면 사용자는 부산에 자기가 먹을 것이 없다고 읽는다.
    expect(failed('CONSTRAINT_EVALUATION').errorMessage).toContain('확인하지 못했어요');
  });

  it('다른 단계에서 멈춘 같은 코드는 원래 문구를 그대로 쓴다', () => {
    const message = failed('RANKING').errorMessage ?? '';
    expect(message).toContain('조건을 조정해서');
    expect(message).not.toContain('알레르기');
  });
});
