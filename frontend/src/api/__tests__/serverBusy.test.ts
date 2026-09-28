// 추천 서버가 꽉 찼을 때(SERVER_BUSY 503) — S15P21E201-1688, 백엔드 !1681 에 맞춘 것.
//
// 🔴 이 시험이 지키는 것:
//    1. 서버가 봉투로 「지금 요청이 많아요」라고 답한 503 은 서버가 살아 있다는 뜻이다. 「끊김」으로 세지 않는다 —
//       세면 복구 확인 요청이 헛돌고, 확인이 1초 넘게 걸리면(붐빌 때) 「서버 연결을 확인하고 있어요」 띠가 뜬다.
//    2. 작업 상태가 FAILED · SERVER_BUSY 이면 그 뜻대로 말한다 — 일반 문장 「일정을 만드는 중 문제가 생겼어요」가 아니라.
import {
  __resetApiAvailabilityForTests,
  apiRequest,
  ApiClientError,
  RECOVERY_PROBE_DELAYS_MS,
  subscribeApiAvailability,
} from '../client';
import { adaptPolledJob } from '@/plan/recommendationJob';

const flush = async () => { for (let i = 0; i < 8; i += 1) await Promise.resolve(); };

const envelope = (status: number, error: { code: string; message: string } | null, data: unknown = null) => ({
  status,
  headers: { get: () => 'application/json' },
  ok: status >= 200 && status < 300,
  json: async () => ({ data, error, meta: { requestId: 'r' } }),
});

/** 익명 출입증은 늘 내주고, 나머지 경로만 주어진 대로. 부른 경로를 적어 둔다. */
function fakeFetch(handler: (url: string) => unknown) {
  const calls: string[] = [];
  globalThis.fetch = jest.fn(async (url: string) => {
    if (String(url).includes('/api/v1/auth/anonymous')) {
      return envelope(201, null, { sessionId: 's', sessionToken: 't', issuedAt: 'now' });
    }
    calls.push(String(url));
    return handler(String(url));
  }) as never;
  return calls;
}

describe('1. SERVER_BUSY 503 은 끊김이 아니다', () => {
  const realFetch = globalThis.fetch;
  let seen: boolean[];
  let unsubscribe: () => void;

  beforeEach(() => {
    jest.useFakeTimers();
    __resetApiAvailabilityForTests();
    seen = [];
    unsubscribe = subscribeApiAvailability((v) => seen.push(v));
    seen.length = 0;
  });
  afterEach(() => {
    unsubscribe();
    __resetApiAvailabilityForTests();
    jest.clearAllTimers();
    jest.useRealTimers();
    globalThis.fetch = realFetch;
  });

  it('🔴 서버 문장을 그대로 싣고, 끊김 표시는 꺼진 채로 끝나며, 복구 확인 요청이 나가지 않는다', async () => {
    const calls = fakeFetch(() => envelope(503, { code: 'SERVER_BUSY', message: '지금 요청이 많아요. 잠시 뒤 다시 시도해 주세요.' }));
    const pending = apiRequest('/api/v1/trips/t1/recommendation-jobs', { method: 'POST', accessToken: 'tok', body: {} });
    await expect(pending).rejects.toMatchObject({ code: 'SERVER_BUSY', status: 503, message: '지금 요청이 많아요. 잠시 뒤 다시 시도해 주세요.' });
    expect(seen[seen.length - 1] ?? false).toBe(false);

    const before = calls.length;
    jest.advanceTimersByTime(RECOVERY_PROBE_DELAYS_MS[0] + 100);
    await flush();
    expect(calls.length).toBe(before);
  });

  it('POST 라 저절로 다시 보내지 않는다 — 사람이 다시 누른다', async () => {
    const calls = fakeFetch(() => envelope(503, { code: 'SERVER_BUSY', message: '지금 요청이 많아요. 잠시 뒤 다시 시도해 주세요.' }));
    await expect(apiRequest('/api/v1/trips/t1/recommendation-jobs', { method: 'POST', accessToken: 'tok', body: {} })).rejects.toBeInstanceOf(ApiClientError);
    expect(calls.filter((url) => url.includes('recommendation-jobs'))).toHaveLength(1);
  });

  it('다른 5xx 는 전처럼 끊김으로 센다 — 이 예외는 SERVER_BUSY 하나만', async () => {
    fakeFetch(() => envelope(500, { code: 'INTERNAL_ERROR', message: '서버 오류' }));
    await expect(apiRequest('/api/v1/trips/t1/recommendation-jobs', { method: 'POST', accessToken: 'tok', body: {} })).rejects.toBeInstanceOf(ApiClientError);
    expect(seen[seen.length - 1]).toBe(true);
  });
});

describe('2. 작업 상태 FAILED · SERVER_BUSY', () => {
  const poll = (code: string) => adaptPolledJob('job-1', {
    status: 'FAILED',
    progress: { percent: 10, stage: null },
    failure: { code, detail: null, retryable: true },
  } as never);

  it('🔴 「지금 요청이 많아요」로 말한다 — 일반 문장이 아니라', () => {
    expect(poll('SERVER_BUSY').errorMessage).toBe('지금 요청이 많아요. 잠시 뒤 다시 시도해 주세요.');
  });

  it('모르는 코드는 전처럼 일반 문장', () => {
    expect(poll('SOMETHING_NEW').errorMessage).toBe('일정을 만드는 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.');
  });
});
