import { apiRequest, ApiClientError, resetAnonymousSession, setRefreshHandler, setUnauthorizedHandler } from '../client';

// 비회원 출입증을 언제 버리고 새로 받는가 — S15P21E201-317.
//
// 서버는 오래 안 쓴 출입증을 지우고(정리 배치), 로그인하며 넘긴 출입증도 지운다. 그 값을 계속 실으면 이 기기는
// 비회원 여행을 영영 못 만든다. 반대로 살아 있는 출입증으로 회원 전용 경로를 부른 401 에 출입증을 버리면,
// 그 출입증에 묶인 여행을 잃는다. 둘은 오류 코드로 갈린다.
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
const failure = (code: string) => ({ data: null, error: { code, message: '로그인이 필요합니다.' }, meta: { requestId: 'r' } });

let issued: string[];
let tripsResponder: (token: string | null) => Response;

function headerOf(init: RequestInit | undefined, name: string): string | null {
  const headers = (init?.headers ?? {}) as Record<string, string>;
  return headers[name] ?? null;
}

beforeEach(async () => {
  issued = [];
  setUnauthorizedHandler(jest.fn());
  setRefreshHandler(async () => null);
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input).includes('/auth/anonymous')) {
      const token = issued.length === 0 ? 'stale-token' : `fresh-token-${issued.length}`;
      issued.push(token);
      return json(201, { data: { sessionId: 's', sessionToken: token, issuedAt: '' }, error: null, meta: { requestId: 'r0' } });
    }
    return tripsResponder(headerOf(init, 'X-Session-Token'));
  }) as unknown as typeof fetch;
  await resetAnonymousSession();
});

afterEach(() => {
  setUnauthorizedHandler(null);
  setRefreshHandler(null);
});

describe('비회원 출입증 교체', () => {
  it('서버가 모르는 출입증이면 버리고 새로 받아 한 번 다시 보낸다', async () => {
    tripsResponder = (token) => (token === 'stale-token'
      ? json(401, failure('AUTHENTICATION_REQUIRED'))
      : json(200, { data: [], error: null, meta: { requestId: 'r1' } }));

    await expect(apiRequest('/api/v1/trips')).resolves.toEqual([]);
    expect(issued).toEqual(['stale-token', 'fresh-token-1']);
  });

  it('살아 있는 출입증으로 회원 전용 경로를 부른 401 에는 출입증을 버리지 않는다', async () => {
    tripsResponder = () => json(401, failure('INVALID_AUTHENTICATION'));

    await expect(apiRequest('/api/v1/trips/t/share-links', { method: 'POST' })).rejects.toBeInstanceOf(ApiClientError);
    expect(issued).toEqual(['stale-token']);
  });

  it('로그인 토큰을 실은 요청의 401 은 출입증과 상관없다', async () => {
    tripsResponder = () => json(401, failure('AUTHENTICATION_REQUIRED'));

    await expect(apiRequest('/api/v1/trips', { accessToken: 'member-token' })).rejects.toBeInstanceOf(ApiClientError);
    expect(issued).toEqual(['stale-token']);
  });

  it('resetAnonymousSession 뒤의 요청은 새 출입증을 싣는다', async () => {
    const seen: (string | null)[] = [];
    tripsResponder = (token) => { seen.push(token); return json(200, { data: [], error: null, meta: { requestId: 'r1' } }); };

    await apiRequest('/api/v1/trips');
    await resetAnonymousSession();
    await apiRequest('/api/v1/trips');

    expect(seen).toEqual(['stale-token', 'fresh-token-1']);
  });
});
