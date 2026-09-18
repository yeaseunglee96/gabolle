import { apiRequest, ApiClientError, setRefreshHandler, setUnauthorizedHandler } from '../client';

// 서버는 익명 출입증으로 못 쓰는 경로에 401 을 준다(AuthenticatedUsers.requireId).
// 그것을 세션 만료로 읽으면 로그인한 적 없는 사람이 로그인 화면으로 튕긴다 — S15P21E201-997.
const unauthorizedBody = JSON.stringify({ data: null, error: { code: 'AUTHENTICATION_REQUIRED', message: '로그인이 필요합니다.' }, meta: { requestId: 'r1' } });

function mockFetch() {
  return jest.fn(async (input: RequestInfo | URL) => {
    if (String(input).includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 'anon-token', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      });
    }
    return new Response(unauthorizedBody, { status: 401, headers: { 'content-type': 'application/json' } });
  });
}

describe('401 을 세션 만료와 구분한다', () => {
  let onUnauthorized: jest.Mock;

  beforeEach(() => {
    onUnauthorized = jest.fn();
    setUnauthorizedHandler(onUnauthorized);
    setRefreshHandler(async () => null);
    globalThis.fetch = mockFetch() as unknown as typeof fetch;
  });

  afterEach(() => {
    setUnauthorizedHandler(null);
    setRefreshHandler(null);
  });

  it('회원 토큰 없이 부른 요청은 로그아웃시키지 않는다', async () => {
    await expect(apiRequest('/api/v1/trips')).rejects.toBeInstanceOf(ApiClientError);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('회원 토큰을 들고 갔는데도 거절당하면 세션 만료로 다룬다', async () => {
    await expect(apiRequest('/api/v1/trips', { accessToken: 'member-token' })).rejects.toBeInstanceOf(ApiClientError);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });
});
