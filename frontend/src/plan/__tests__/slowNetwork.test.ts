// 느린 네트워크에서 데모가 깨지던 네 자리 — S15P21E201-1823.
declare const require: (id: string) => any; declare const __dirname: string; const { readFileSync } = require('fs');
const mockApiRequest = jest.fn();
jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: (...args: unknown[]) => mockApiRequest(...args) }));

import { ApiClientError } from '@/api/client';
import { askAssistant } from '@/assistant/assistantApi';
import { createRecommendationJobAdapter, type RecommendationJobSnapshot } from '@/plan/recommendationJob';
import { adaptCourse, ensureCourseItinerary } from '@/plan/tripCourses';

const running: RecommendationJobSnapshot = { state: 'polling', jobId: 'job-1', progress: 40, stage: '동선 계산', canCancel: false, errorMessage: null, resultRef: null };
const networkError = () => new ApiClientError('offline', 'NETWORK_ERROR', 0);

beforeEach(() => mockApiRequest.mockReset());

describe('추천 작업 폴링', () => {
  it('🔴 네트워크 오류 한 번으로 끝나지 않고, 다음 조회에서 완료를 받는다', async () => {
    const adapter = createRecommendationJobAdapter('token');
    mockApiRequest.mockRejectedValueOnce(networkError());
    const afterError = await adapter.poll('job-1', running);
    expect(afterError.state).toBe('polling');
    expect(afterError.progress).toBe(40);

    mockApiRequest.mockResolvedValueOnce({ jobId: 'job-1', status: 'SUCCEEDED', progress: { stage: '완료', percent: 100 }, failure: null, retryable: false, pollAfterSeconds: null });
    const done = await adapter.poll('job-1', afterError);
    expect(done.state).toBe('completed');
    expect(done.transientFailures).toBeUndefined();
  });

  it('시간 초과도 한 번은 넘긴다', async () => {
    mockApiRequest.mockRejectedValueOnce(new ApiClientError('late', 'REQUEST_TIMEOUT', 0));
    expect((await createRecommendationJobAdapter('token').poll('job-1', running)).state).toBe('polling');
  });

  it('연달아 세 번 끊기면 그때 끝낸다', async () => {
    const adapter = createRecommendationJobAdapter('token');
    mockApiRequest.mockRejectedValue(networkError());
    let snap = running;
    for (let i = 0; i < 3; i += 1) snap = await adapter.poll('job-1', snap);
    expect(snap.state).toBe('unavailable');
  });

  it('서버가 준 오류(상태 코드 있음)는 바로 끝낸다', async () => {
    mockApiRequest.mockRejectedValueOnce(new ApiClientError('boom', 'INTERNAL', 500));
    expect((await createRecommendationJobAdapter('token').poll('job-1', running)).state).toBe('failed');
  });
});

describe('긴 요청의 제한 시간', () => {
  it('🔴 동백이 요청은 45초를 기다린다', async () => {
    mockApiRequest.mockResolvedValue({ type: 'MESSAGE', message: 'hi' });
    await askAssistant('안녕', 'token').catch(() => undefined);
    expect(mockApiRequest).toHaveBeenCalledWith('/api/v1/assistant/messages', expect.objectContaining({ timeoutMs: 45000 }));
  });

  it('🔴 2안·3안 확정은 40초를 기다린다', async () => {
    mockApiRequest.mockResolvedValue({ itineraryId: 'it-b' });
    await ensureCourseItinerary('trip-1', adaptCourse({ id: 'req:1' }), 'token');
    expect(mockApiRequest).toHaveBeenCalledWith('/api/v1/trips/trip-1/course', expect.objectContaining({ timeoutMs: 40000 }));
  });

  it('🔴 확정이 끊겨도 서버가 일정을 만들어 두었으면 성공으로 연다 — 두 번 누르게 하지 않는다', async () => {
    mockApiRequest.mockImplementation((path: string) => path.endsWith('/course')
      ? Promise.reject(new ApiClientError('late', 'REQUEST_TIMEOUT', 0))
      : Promise.resolve({ courses: [{ id: 'req:1', itineraryId: 'it-b', days: [{ date: '2026-10-04', items: [{ title: '해운대' }] }] }] }));
    await expect(ensureCourseItinerary('trip-1', adaptCourse({ id: 'req:1' }), 'token')).resolves.toEqual({ state: 'success', itineraryId: 'it-b' });
  });
});

describe('질문 마지막 제출', () => {
  it('🔴 그리기 상태가 아니라 즉시 잠그는 ref 로 두 번 제출을 막고, 실패하면 푼다', () => {
    const src: string = readFileSync(`${__dirname}/../../../app/(plan)/questions.tsx`, 'utf8');
    const body = src.slice(src.indexOf('const submitPlan = async'), src.indexOf('const grantHealthConsentAndRetry'));
    expect(src).toMatch(/const submittingNow = useRef\(false\)/);
    expect(body).toMatch(/if \(submittingNow\.current\) return;/);
    expect(body.indexOf('submittingNow.current = true')).toBeLessThan(body.indexOf('.submit(draft)'));
    expect(body).toMatch(/else submittingNow\.current = false/);
  });
});
