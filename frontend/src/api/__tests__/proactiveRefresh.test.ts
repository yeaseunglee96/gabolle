import {
  ACCESS_TOKEN_REFRESH_MARGIN_MS,
  ApiClientError,
  ApiUnavailableError,
  apiRequest,
  refreshIfExpiring,
  setRefreshHandler,
  setUnauthorizedHandler,
  trackAccessToken,
} from '../client';

// 재현 — 서버는 끝난 접속 표를 401 로 거절하지 않고 익명 출입증으로 받아 준다. 그래서 공개 화면은
// 표가 끝난 뒤에도 200 이고, 401 을 기다리는 갱신은 영영 안 일어나 로그인 유지 시간이 안 밀린다.
// 이 시험의 서버 흉내는 그 모양 그대로 — 어떤 표를 들고 와도 200 을 준다.
const okBody = JSON.stringify({ data: { ok: true }, error: null, meta: { requestId: 'r1' } });
const LIFETIME_S = 1800;

let now = 1_000_000;
let sentTokens: (string | null)[];

function mockFetch() {
  return jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input).includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 'anon-token', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      });
    }
    const authorization = (init?.headers as Record<string, string> | undefined)?.Authorization ?? null;
    sentTokens.push(authorization ? authorization.replace('Bearer ', '') : null);
    return new Response(okBody, { status: 200, headers: { 'content-type': 'application/json' } });
  });
}

/** AuthProvider 가 하는 것처럼, 갱신 응답이 오면(한 박자 뒤) 새 표의 수명을 알린다. */
function refreshingTo(token: string) {
  return jest.fn(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
    trackAccessToken(token, LIFETIME_S);
    return token;
  });
}

describe('접속 표가 끝나기 전에 미리 갱신한다', () => {
  let onUnauthorized: jest.Mock;

  beforeEach(() => {
    now = 1_000_000;
    jest.spyOn(Date, 'now').mockImplementation(() => now);
    sentTokens = [];
    onUnauthorized = jest.fn();
    setUnauthorizedHandler(onUnauthorized);
    globalThis.fetch = mockFetch() as unknown as typeof fetch;
  });

  afterEach(() => {
    trackAccessToken(null);
    setUnauthorizedHandler(null);
    setRefreshHandler(null);
    jest.restoreAllMocks();
  });

  it('넉넉히 남은 표는 그대로 들고 간다', async () => {
    const handler = refreshingTo('new-token');
    setRefreshHandler(handler);
    trackAccessToken('old-token', LIFETIME_S);
    now += (LIFETIME_S * 1000) - ACCESS_TOKEN_REFRESH_MARGIN_MS - 1;

    await apiRequest('/api/v1/places', { accessToken: 'old-token' });

    expect(handler).not.toHaveBeenCalled();
    expect(sentTokens).toEqual(['old-token']);
  });

  it('끝난 표로 공개 화면을 불러도 — 서버가 200 을 줄 자리여도 — 먼저 갱신하고 새 표로 보낸다', async () => {
    const handler = refreshingTo('new-token');
    setRefreshHandler(handler);
    trackAccessToken('old-token', LIFETIME_S);
    now += LIFETIME_S * 1000 + 5 * 60_000;

    await apiRequest('/api/v1/places', { accessToken: 'old-token' });

    expect(handler).toHaveBeenCalledTimes(1);
    expect(sentTokens).toEqual(['new-token']);
  });

  it('홈처럼 한꺼번에 여러 요청이 나가도 갱신은 한 번이다', async () => {
    const handler = refreshingTo('new-token');
    setRefreshHandler(handler);
    trackAccessToken('old-token', LIFETIME_S);
    now += LIFETIME_S * 1000 - ACCESS_TOKEN_REFRESH_MARGIN_MS;

    await Promise.all([
      apiRequest('/api/v1/places', { accessToken: 'old-token' }),
      apiRequest('/api/v1/stories', { accessToken: 'old-token' }),
      apiRequest('/api/v1/trips', { accessToken: 'old-token' }),
    ]);

    expect(handler).toHaveBeenCalledTimes(1);
    expect(sentTokens).toEqual(['new-token', 'new-token', 'new-token']);
  });

  it('갱신 직후 화면이 아직 옛 표를 들고 와도 새 표로 보낸다 — 끝난 표가 익명으로 새지 않게', async () => {
    const handler = refreshingTo('new-token');
    setRefreshHandler(handler);
    trackAccessToken('old-token', LIFETIME_S);
    now += LIFETIME_S * 1000;

    await apiRequest('/api/v1/places', { accessToken: 'old-token' });
    await apiRequest('/api/v1/stories', { accessToken: 'old-token' });

    expect(handler).toHaveBeenCalledTimes(1);
    expect(sentTokens).toEqual(['new-token', 'new-token']);
  });

  it('로그아웃한 뒤에는 옛 표를 새 표로 바꿔 주지 않는다', async () => {
    const handler = refreshingTo('next-token');
    setRefreshHandler(handler);
    trackAccessToken('old-token', LIFETIME_S);
    trackAccessToken('new-token', LIFETIME_S);
    trackAccessToken(null);

    await apiRequest('/api/v1/places', { accessToken: 'old-token' });

    expect(handler).not.toHaveBeenCalled();
    expect(sentTokens).toEqual(['old-token']);
  });

  it('서버가 갱신을 거절하면(401) 로그아웃을 한 번만 일으키고, 요청은 보내지 않는다', async () => {
    setRefreshHandler(async () => { throw new ApiClientError('만료되었거나 폐기된 refresh token입니다.', 'INVALID_REFRESH_TOKEN', 401); });
    trackAccessToken('old-token', LIFETIME_S);
    now += LIFETIME_S * 1000;

    const results = await Promise.allSettled([
      apiRequest('/api/v1/places', { accessToken: 'old-token' }),
      apiRequest('/api/v1/stories', { accessToken: 'old-token' }),
    ]);

    expect(results.map((r) => r.status)).toEqual(['rejected', 'rejected']);
    expect((results[0] as PromiseRejectedResult).reason).toMatchObject({ code: 'SESSION_EXPIRED', status: 401 });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(sentTokens).toEqual([]);
  });

  it.each([
    ['nginx 의 503', new ApiClientError('서버가 잠시 응답하지 못했어요.', 'SERVER_ERROR', 503)],
    ['배포 중 502', new ApiClientError('서버가 잠시 응답하지 못했어요.', 'SERVER_ERROR', 502)],
    ['망 끊김', new ApiUnavailableError()],
  ])('갱신이 %s 로 실패하면 로그아웃하지 않고 지금 표로 보낸다', async (_label, error) => {
    setRefreshHandler(async () => { throw error; });
    trackAccessToken('old-token', LIFETIME_S);
    now += LIFETIME_S * 1000 - 30_000;

    await apiRequest('/api/v1/places', { accessToken: 'old-token' });

    expect(onUnauthorized).not.toHaveBeenCalled();
    expect(sentTokens).toEqual(['old-token']);
  });

  it('수명을 모르는 표는 미리 갱신하지 않는다 — 401 뒤 갱신만 남는다', async () => {
    const handler = refreshingTo('new-token');
    setRefreshHandler(handler);
    trackAccessToken('other-token', LIFETIME_S);
    now += LIFETIME_S * 1000;

    await apiRequest('/api/v1/places', { accessToken: 'untracked-token' });

    expect(handler).not.toHaveBeenCalled();
    expect(sentTokens).toEqual(['untracked-token']);
  });
});

describe('앱이 다시 앞에 오면 표를 본다', () => {
  let onUnauthorized: jest.Mock;

  beforeEach(() => {
    now = 1_000_000;
    jest.spyOn(Date, 'now').mockImplementation(() => now);
    onUnauthorized = jest.fn();
    setUnauthorizedHandler(onUnauthorized);
  });

  afterEach(() => {
    trackAccessToken(null);
    setUnauthorizedHandler(null);
    setRefreshHandler(null);
    jest.restoreAllMocks();
  });

  it('표가 넉넉하면 아무것도 안 한다', async () => {
    const handler = refreshingTo('new-token');
    setRefreshHandler(handler);
    trackAccessToken('old-token', LIFETIME_S);

    await refreshIfExpiring();

    expect(handler).not.toHaveBeenCalled();
  });

  it('뒤에 있는 동안 표가 끝났으면 갱신한다', async () => {
    const handler = refreshingTo('new-token');
    setRefreshHandler(handler);
    trackAccessToken('old-token', LIFETIME_S);
    now += 3 * 60 * 60_000;

    await refreshIfExpiring();

    expect(handler).toHaveBeenCalledTimes(1);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('그사이 세션이 끝났으면(401) 돌아온 자리에서 로그아웃시킨다', async () => {
    setRefreshHandler(async () => { throw new ApiClientError('만료', 'INVALID_REFRESH_TOKEN', 401); });
    trackAccessToken('old-token', LIFETIME_S);
    now += 3 * 60 * 60_000;

    await refreshIfExpiring();

    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('막 깨어나 망이 아직 안 붙었으면 로그아웃시키지 않는다', async () => {
    setRefreshHandler(async () => { throw new ApiUnavailableError(); });
    trackAccessToken('old-token', LIFETIME_S);
    now += 3 * 60 * 60_000;

    await refreshIfExpiring();

    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('로그인 안 했으면 아무것도 안 한다', async () => {
    const handler = refreshingTo('new-token');
    setRefreshHandler(handler);

    await refreshIfExpiring();

    expect(handler).not.toHaveBeenCalled();
  });
});
