// 「서버 연결을 확인하고 있어요」 배너가 안 사라지던 것 — S15P21E201-1281.
import {
  __resetApiAvailabilityForTests,
  apiRequest,
  ApiClientError,
  RECOVERY_PROBE_DELAYS_MS,
  REQUEST_CANCELLED_CODE,
  subscribeApiAvailability,
} from '../client';

const flush = async () => { for (let i = 0; i < 8; i += 1) await Promise.resolve(); };

/** 상태 코드만 있으면 되는 가짜 응답. */
const reply = (status: number, data: unknown = {}) => ({
  status,
  headers: { get: () => 'application/json' },
  ok: status >= 200 && status < 300,
  json: async () => ({ data, error: null, meta: { requestId: 'r' } }),
});

/**
 * 🔴 모든 요청 앞에는 익명 출입증 발급이 한 번 나간다. 그것까지 가짜 fetch 로 막으면
 * 정작 보려던 것(취소 처리) 대신 출입증 실패가 깃발을 켠다. 출입증은 항상 내주고,
 * 그 밖의 경로만 주어진 대로 처리한다.
 */
function fetchExceptAnonymous(handler: () => Promise<unknown>) {
  return jest.fn(async (url: string) => {
    if (String(url).includes('/api/v1/auth/anonymous')) {
      return reply(201, { sessionId: 's', sessionToken: 't', issuedAt: 'now' });
    }
    return handler();
  }) as never;
}

describe('S15P21E201-1281 서버 연결 배너', () => {
  const realFetch = globalThis.fetch;
  let seen: boolean[];
  let unsubscribe: () => void;

  beforeEach(() => {
    jest.useFakeTimers();
    __resetApiAvailabilityForTests();
    seen = [];
    unsubscribe = subscribeApiAvailability((v) => seen.push(v));
    seen.length = 0; // 구독 즉시 오는 현재값은 빼고 본다
  });

  afterEach(() => {
    unsubscribe();
    __resetApiAvailabilityForTests();
    jest.clearAllTimers();
    jest.useRealTimers();
    globalThis.fetch = realFetch;
  });

  describe('🔴 호출자가 끊은 요청은 끊김이 아니다', () => {
    it('화면을 떠나며 취소해도 「서버가 죽었다」로 세지 않는다', async () => {
      const controller = new AbortController();
      // fetch 가 취소로 실패하는 상황을 그대로 흉내 낸다.
      globalThis.fetch = fetchExceptAnonymous(async () => { throw new Error('Aborted'); });
      controller.abort();

      await expect(apiRequest('/api/v1/places/x', { signal: controller.signal })).rejects.toThrow(ApiClientError);
      await flush();

      expect(seen).toEqual([]); // 배너 쪽에 참이 한 번도 안 갔다
    });

    it('취소는 취소라고 말한다 — 화면이 오류로 그리지 않게', async () => {
      const controller = new AbortController();
      globalThis.fetch = fetchExceptAnonymous(async () => { throw new Error('Aborted'); });
      controller.abort();

      const error = await apiRequest('/api/v1/places/x', { signal: controller.signal }).catch((e) => e);
      expect((error as ApiClientError).code).toBe(REQUEST_CANCELLED_CODE);
    });

    it('취소가 아닌 진짜 네트워크 실패는 그대로 끊김으로 센다', async () => {
      globalThis.fetch = fetchExceptAnonymous(async () => { throw new Error('Network request failed'); });

      await expect(apiRequest('/api/v1/places/x')).rejects.toThrow();
      await flush();

      expect(seen).toEqual([true]);
    });
  });

  describe('🔴 끊긴 뒤에 스스로 되묻는다', () => {
    it('요청을 하나도 안 하는 화면에 있어도, 서버가 돌아오면 꺼진다', async () => {
      // 이게 이 티켓의 핵심이다. 예전에는 되묻는 코드가 없어서 배너가 영영 남았다.
      globalThis.fetch = fetchExceptAnonymous(async () => { throw new Error('Network request failed'); });
      await expect(apiRequest('/api/v1/trips')).rejects.toThrow();
      await flush();
      expect(seen).toEqual([true]);

      // 이제 화면은 아무 요청도 안 한다. 서버만 되살아난다.
      globalThis.fetch = jest.fn(async () => reply(401)) as never;
      jest.advanceTimersByTime(RECOVERY_PROBE_DELAYS_MS[0]);
      await flush();

      expect(seen).toEqual([true, false]);
    });

    it('아직 5xx 면 계속 켜 둔 채 다시 묻는다', async () => {
      globalThis.fetch = fetchExceptAnonymous(async () => { throw new Error('Network request failed'); });
      await expect(apiRequest('/api/v1/trips')).rejects.toThrow();
      await flush();

      globalThis.fetch = jest.fn(async () => reply(503)) as never;
      jest.advanceTimersByTime(RECOVERY_PROBE_DELAYS_MS[0]);
      await flush();
      expect(seen).toEqual([true]);          // 아직 안 꺼졌다

      // 두 번째 되묻기에서 살아난다
      globalThis.fetch = jest.fn(async () => reply(200)) as never;
      jest.advanceTimersByTime(RECOVERY_PROBE_DELAYS_MS[1]);
      await flush();
      expect(seen).toEqual([true, false]);
    });

    it('간격은 점점 늘어난다 — 죽은 서버를 3초마다 영원히 두드리지 않는다', () => {
      for (let i = 1; i < RECOVERY_PROBE_DELAYS_MS.length; i += 1) {
        expect(RECOVERY_PROBE_DELAYS_MS[i]).toBeGreaterThan(RECOVERY_PROBE_DELAYS_MS[i - 1]);
      }
    });
  });
});
