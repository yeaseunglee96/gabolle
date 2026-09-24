// nginx 요청 제한(limit_req)이 넘친 요청을 503 으로 돌려줄 때 GET 을 다시 보낸다 — S15P21E201-1574.
//
// 🔴 운영 웹에서 로그인 뒤 홈을 열면 API 19개 중 9개가 503 이었고(5초 간격으로 세 번 모두 같았다),
//    앱은 재시도 없이 바로 실패로 처리해서 「내 여행」 카드가 빈 상자로 남았다.
import {
  __resetApiAvailabilityForTests,
  apiRequest,
  ApiClientError,
  RATE_LIMIT_RETRY_DELAYS_MS,
  subscribeApiAvailability,
} from '../client';

const reply = (status: number, data: unknown = {}) => ({
  status,
  headers: { get: () => 'application/json' },
  ok: status >= 200 && status < 300,
  json: async () => ({ data, error: null, meta: { requestId: 'r' } }),
});

/** 익명 출입증은 항상 내주고, 그 밖의 경로는 순서대로 준비한 응답을 준다. */
function scriptedFetch(statuses: number[]) {
  const calls: { url: string; method: string }[] = [];
  const queue = [...statuses];
  const fn = jest.fn(async (url: string, init?: { method?: string }) => {
    if (String(url).includes('/api/v1/auth/anonymous')) {
      return reply(201, { sessionId: 's', sessionToken: 't', issuedAt: 'now' });
    }
    calls.push({ url: String(url), method: init?.method ?? 'GET' });
    const status = queue.shift() ?? 200;
    return reply(status, status === 200 ? { ok: true } : {});
  });
  return { fn: fn as never, calls };
}

describe('S15P21E201-1574 503 재시도', () => {
  const realFetch = globalThis.fetch;
  let seen: boolean[];
  let unsubscribe: () => void;

  beforeEach(() => {
    jest.useFakeTimers();
    jest.spyOn(Math, 'random').mockReturnValue(0);
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
    jest.restoreAllMocks();
    globalThis.fetch = realFetch;
  });

  it('🔴 GET 이 503 두 번 뒤에 200 이면 성공으로 끝난다 — 사용자는 실패를 못 본다', async () => {
    const { fn, calls } = scriptedFetch([503, 503, 200]);
    globalThis.fetch = fn;

    const pending = apiRequest<{ ok: boolean }>('/api/v1/trips');
    await jest.advanceTimersByTimeAsync(RATE_LIMIT_RETRY_DELAYS_MS[0] + RATE_LIMIT_RETRY_DELAYS_MS[1] + 50);

    await expect(pending).resolves.toEqual({ ok: true });
    expect(calls).toHaveLength(3);
    // 잠깐 몰린 503 이 「서버 연결 불가」 배너를 켜지 않는다.
    expect(seen).not.toContain(true);
  });

  it('계속 503 이면 두 번만 다시 하고 실패를 돌려준다 — 죽은 서버를 끝없이 두드리지 않는다', async () => {
    const { fn, calls } = scriptedFetch([503, 503, 503, 503, 503]);
    globalThis.fetch = fn;

    const pending = apiRequest('/api/v1/trips');
    const settled = pending.then(() => 'resolved', (error) => error);
    await jest.advanceTimersByTimeAsync(RATE_LIMIT_RETRY_DELAYS_MS[0] + RATE_LIMIT_RETRY_DELAYS_MS[1] + 50);

    const result = await settled;
    expect(result).toBeInstanceOf(ApiClientError);
    expect((result as ApiClientError).status).toBe(503);
    expect(calls).toHaveLength(1 + RATE_LIMIT_RETRY_DELAYS_MS.length);
    expect(seen).toContain(true);
  });

  it('🔴 POST 는 재시도하지 않는다 — 두 번 실행되면 안 되는 요청이다', async () => {
    const { fn, calls } = scriptedFetch([503, 200]);
    globalThis.fetch = fn;

    const pending = apiRequest('/api/v1/stories', { method: 'POST', body: { body: 'x' } });
    const settled = pending.then(() => 'resolved', (error) => error);
    await jest.advanceTimersByTimeAsync(5000);

    expect(await settled).toBeInstanceOf(ApiClientError);
    // 되묻기(RECOVERY_PROBE)의 GET 은 세지 않는다 — 셀 것은 같은 POST 가 몇 번 나갔는가다.
    expect(calls.filter((call) => call.url.includes('/api/v1/stories'))).toHaveLength(1);
  });

  it('기다리는 동안 호출자가 끊으면 다시 보내지 않는다', async () => {
    const { fn, calls } = scriptedFetch([503, 200]);
    globalThis.fetch = fn;
    const controller = new AbortController();

    const pending = apiRequest('/api/v1/trips', { signal: controller.signal });
    const settled = pending.then(() => 'resolved', (error) => error);
    await jest.advanceTimersByTimeAsync(10);
    controller.abort();
    await jest.advanceTimersByTimeAsync(5000);

    expect(await settled).toBeInstanceOf(ApiClientError);
    expect(calls).toHaveLength(1);
  });
});
