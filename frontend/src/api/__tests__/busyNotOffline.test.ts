// 서버가 바쁜데 「인터넷 연결을 확인해 주세요」가 뜨던 것 — S15P21E201-2060 (2026-10-16 웹 점검: 서버 과부하 503·429).
//
// 🔴 이 시험이 지키는 것
//    1. 서버가 5xx·429 로 답하면 «서버가 잠시 바빠요»다. 「서버가 응답하지 못했어요 (HTTP 503)」·「예상하지 못한 서버 응답이에요 (HTTP 429)」가 아니다.
//    2. 웹에서 fetch 가 던졌어도 서버가 대답은 했으면(nginx 의 503·429 에는 CORS 머리가 없어 브라우저가 응답을 못 넘긴다) 인터넷 탓이 아니다.
//       진짜로 못 닿았을 때만 NETWORK_ERROR — 그래야 화면이 「인터넷 연결을 확인해 주세요」를 말한다.
//    3. 그 말은 여섯 언어로 나간다(localizeMessage).
import { Platform } from 'react-native';

import {
  __resetApiAvailabilityForTests,
  apiRequest,
  ApiClientError,
  ApiUnavailableError,
  RATE_LIMIT_RETRY_DELAYS_MS,
  SERVER_ERROR_CODE,
  setApiLanguage,
  subscribeApiAvailability,
} from '../client';
import { localizeMessage } from '@/i18n/messages';
import { pickLanguage } from '@/i18n/pick';

const BUSY_KO = '서버가 잠시 바빠요. 잠시 후 다시 시도해 주세요.';
const BUSY_EN = 'The server is busy right now. Please try again shortly.';

const envelopeReply = (status: number, error: { code: string; message: string } | null, data: unknown = null) => ({
  status,
  headers: { get: () => 'application/json' },
  ok: status >= 200 && status < 300,
  json: async () => ({ data, error, meta: { requestId: 'r' } }),
});

/** nginx 가 주는 안내 페이지 — 우리 JSON 봉투가 아니다. */
const htmlReply = (status: number) => ({
  status,
  headers: { get: () => 'text/html' },
  ok: false,
  json: async () => { throw new Error('not json'); },
});

type Handler = (url: string, init?: RequestInit) => Promise<unknown>;

/** 익명 출입증은 늘 내주고, 나머지는 주어진 대로. 부른 요청을 모두 적어 둔다. */
function fakeFetch(handler: Handler) {
  const calls: { url: string; init?: RequestInit }[] = [];
  globalThis.fetch = jest.fn(async (url: string, init?: RequestInit) => {
    if (String(url).includes('/api/v1/auth/anonymous')) return envelopeReply(201, null, { sessionId: 's', sessionToken: 't', issuedAt: 'now' });
    calls.push({ url: String(url), init });
    return handler(String(url), init);
  }) as never;
  return calls;
}

const flush = async () => { for (let i = 0; i < 12; i += 1) await Promise.resolve(); };

describe('서버가 바쁘다는 말', () => {
  const realFetch = globalThis.fetch;
  const originalPlatform = Platform.OS;
  // RN 시험 환경의 navigator 에는 onLine 이 없다 — 있으면 되돌리고, 없으면 지운다.
  const nav = ((globalThis as { navigator?: object }).navigator ??= {}) as object;
  const onLineDescriptor = Object.getOwnPropertyDescriptor(nav, 'onLine');
  let seen: boolean[];
  let unsubscribe: () => void;

  const asWeb = () => Object.defineProperty(Platform, 'OS', { get: () => 'web', configurable: true });
  const setOnLine = (value: boolean) => Object.defineProperty(nav, 'onLine', { get: () => value, configurable: true });

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
    Object.defineProperty(Platform, 'OS', { get: () => originalPlatform, configurable: true });
    if (onLineDescriptor) Object.defineProperty(nav, 'onLine', onLineDescriptor);
    else delete (nav as { onLine?: boolean }).onLine;
  });

  describe('1. 서버가 읽히게 5xx·429 로 답했을 때', () => {
    it('🔴 503 안내 페이지(HTML)는 「서버가 잠시 바빠요」다 — HTTP 번호가 사람 앞에 나오지 않는다', async () => {
      fakeFetch(async () => htmlReply(503));

      const pending = apiRequest('/api/v1/places/facets', { method: 'POST' }); // POST 는 다시 보내지 않는다
      const error = await pending.catch((e) => e);

      expect(error).toBeInstanceOf(ApiClientError);
      expect((error as ApiClientError).code).toBe(SERVER_ERROR_CODE);
      expect((error as ApiClientError).status).toBe(503);
      expect((error as ApiClientError).message).toBe(BUSY_KO);
      expect((error as ApiClientError).message).not.toMatch(/HTTP/);
    });

    it('🔴 GET 이 계속 503 이어도 끝에는 같은 말이다(재시도는 전과 같이 두 번)', async () => {
      const calls = fakeFetch(async () => htmlReply(503));

      const settled = apiRequest('/api/v1/trips').then(() => null, (e) => e);
      await jest.advanceTimersByTimeAsync(RATE_LIMIT_RETRY_DELAYS_MS[0] + RATE_LIMIT_RETRY_DELAYS_MS[1] + 50);
      const error = await settled;

      expect(calls).toHaveLength(3);
      expect((error as ApiClientError).message).toBe(BUSY_KO);
    });

    it('🔴 429 안내 페이지(HTML)도 «바쁨»이다 — 전에는 「예상하지 못한 서버 응답이에요. (HTTP 429)」였다', async () => {
      fakeFetch(async () => htmlReply(429));

      const error = await apiRequest('/api/v1/places/facets').catch((e) => e);

      expect((error as ApiClientError).code).toBe(SERVER_ERROR_CODE);
      expect((error as ApiClientError).status).toBe(429);
      expect((error as ApiClientError).message).toBe(BUSY_KO);
    });

    it('우리 앱이 봉투로 준 429(예: 로그인 시도 한도)는 서버 문장 그대로다 — 이유를 아는 말을 덮지 않는다', async () => {
      fakeFetch(async () => envelopeReply(429, { code: 'ANONYMOUS_LIMIT_REACHED', message: '비회원은 여행을 5개까지 만들 수 있어요.' }));

      const error = await apiRequest('/api/v1/trips', { method: 'POST', body: {} }).catch((e) => e);

      expect((error as ApiClientError).code).toBe('ANONYMOUS_LIMIT_REACHED');
      expect((error as ApiClientError).message).toBe('비회원은 여행을 5개까지 만들 수 있어요.');
    });

    it('영어 화면이면 영어 문장이다', async () => {
      setApiLanguage('en');
      try {
        fakeFetch(async () => htmlReply(429));
        const error = await apiRequest('/api/v1/places/facets').catch((e) => e);
        expect((error as ApiClientError).message).toBe(BUSY_EN);
      } finally {
        setApiLanguage('ko');
      }
    });
  });

  describe('2. 웹에서 fetch 가 던졌을 때 — 서버가 대답은 했는가', () => {
    const rejectMain = () => { throw new TypeError('Failed to fetch'); };

    it('🔴 서버가 대답했으면(CORS 머리 없는 503·429) 인터넷 탓이 아니다 — 「서버가 잠시 바빠요」, 끊김 띠도 안 켠다', async () => {
      asWeb();
      const calls = fakeFetch(async (_url, init) => {
        // 두 번째 요청(no-cors 로 물은 것)은 «불투명 응답»으로 끝난다 — 대답은 왔다는 뜻이다.
        if (init?.mode === 'no-cors') return { type: 'opaque', status: 0 };
        return rejectMain();
      });

      const error = await apiRequest('/api/v1/places/facets').catch((e) => e);
      await flush();

      expect(error).toBeInstanceOf(ApiClientError);
      expect(error).not.toBeInstanceOf(ApiUnavailableError);
      expect((error as ApiClientError).code).toBe(SERVER_ERROR_CODE);
      expect((error as ApiClientError).message).toBe(BUSY_KO);
      expect(seen).toEqual([]); // 서버는 살아 있다 — 「서버 연결을 확인하고 있어요」 띠를 올리지 않았다
      const probe = calls.find((call) => call.init?.mode === 'no-cors');
      expect(probe?.init?.method).toBe('GET');
      expect(probe?.init?.cache).toBe('no-store');
    });

    it('🔴 진짜로 못 닿았으면(확인 요청도 던진다) 전처럼 NETWORK_ERROR — 그때만 인터넷 문구가 나온다', async () => {
      asWeb();
      fakeFetch(async () => rejectMain());

      const error = await apiRequest('/api/v1/places/facets').catch((e) => e);
      await flush();

      expect(error).toBeInstanceOf(ApiUnavailableError);
      expect((error as ApiClientError).code).toBe('NETWORK_ERROR');
      expect(seen).toEqual([true]);
    });

    it('브라우저가 오프라인이라고 알면 서버를 다시 두드리지 않고 바로 NETWORK_ERROR 다', async () => {
      asWeb();
      setOnLine(false);
      const calls = fakeFetch(async () => rejectMain());

      const error = await apiRequest('/api/v1/places/facets').catch((e) => e);

      expect((error as ApiClientError).code).toBe('NETWORK_ERROR');
      expect(calls.filter((call) => call.init?.mode === 'no-cors')).toHaveLength(0);
    });

    it('같은 순간에 여러 요청이 실패해도 확인 요청은 한 번이다 — 몰린 서버를 더 두드리지 않는다', async () => {
      asWeb();
      const calls = fakeFetch(async (_url, init) => (init?.mode === 'no-cors' ? { type: 'opaque', status: 0 } : rejectMain()));

      const errors = await Promise.all([
        apiRequest('/api/v1/places/facets').catch((e) => e),
        apiRequest('/api/v1/places/categories').catch((e) => e),
        apiRequest('/api/v1/trips').catch((e) => e),
      ]);

      for (const error of errors) expect((error as ApiClientError).message).toBe(BUSY_KO);
      expect(calls.filter((call) => call.init?.mode === 'no-cors')).toHaveLength(1);
    });

    it('🔴 앱(iOS·안드로이드)은 이 길을 안 탄다 — CORS 가 없어 5xx 가 그대로 읽히고, 던졌다면 진짜 통신 실패다', async () => {
      // Platform.OS 를 web 으로 바꾸지 않았다(기본 ios).
      const calls = fakeFetch(async () => rejectMain());

      const error = await apiRequest('/api/v1/places/facets').catch((e) => e);
      await flush();

      expect((error as ApiClientError).code).toBe('NETWORK_ERROR');
      expect(calls.filter((call) => call.init?.mode === 'no-cors')).toHaveLength(0);
    });

    it('취소는 여전히 취소다 — 확인 요청도 안 나간다', async () => {
      asWeb();
      const controller = new AbortController();
      const calls = fakeFetch(async () => { throw new Error('Aborted'); });
      controller.abort();

      const error = await apiRequest('/api/v1/places/facets', { signal: controller.signal }).catch((e) => e);

      expect((error as ApiClientError).code).toBe('REQUEST_CANCELLED');
      expect(calls.filter((call) => call.init?.mode === 'no-cors')).toHaveLength(0);
    });
  });

  describe('3. 여섯 언어로 나간다', () => {
    const tx = (language: 'ko' | 'en' | 'ja' | 'zh-Hans' | 'zh-Hant' | 'vi') => (ko: string, en: string) => pickLanguage(language, { ko, en });

    it('🔴 한국어 문장은 번역표를 거쳐 일본어·중국어(간체·번체)·베트남어로, 영어는 영어 문장으로 나간다', () => {
      expect(localizeMessage(tx('ko'), BUSY_KO)).toBe(BUSY_KO);
      expect(localizeMessage(tx('en'), BUSY_KO)).toBe(BUSY_EN);
      expect(localizeMessage(tx('ja'), BUSY_KO)).toBe('サーバーが混み合っています。しばらくしてからもう一度お試しください。');
      expect(localizeMessage(tx('zh-Hans'), BUSY_KO)).toBe('服务器暂时繁忙。请稍后再试。');
      expect(localizeMessage(tx('zh-Hant'), BUSY_KO)).toBe('伺服器暫時繁忙。請稍後再試。');
      expect(localizeMessage(tx('vi'), BUSY_KO)).toBe('Máy chủ đang bận. Vui lòng thử lại sau.');
    });

    it('🔴 일본어·중국어·베트남어 화면의 API 언어는 en 이라 영어로 던져진다 — 그것도 같은 번역을 찾는다', () => {
      expect(localizeMessage(tx('ja'), BUSY_EN)).toBe('サーバーが混み合っています。しばらくしてからもう一度お試しください。');
      expect(localizeMessage(tx('vi'), BUSY_EN)).toBe('Máy chủ đang bận. Vui lòng thử lại sau.');
      expect(localizeMessage(tx('en'), BUSY_EN)).toBe(BUSY_EN);
      expect(localizeMessage(tx('ko'), BUSY_EN)).toBe(BUSY_KO);
    });
  });
});
