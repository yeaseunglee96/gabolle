import { getUserProfile, setBlocked } from '../stories';

// 차단 — S15P21E201-991(화면) / -990(서버).
// 🔴 차단은 「내가 이 사람을 안 본다」가 아니라 「이 사람에게 내 것을 안 보여준다」다.
// 그래서 blocked(내가 차단했다)와 blockedByUser(이 사람이 나를 차단했다)는 서로 다른 값이고,
// 화면이 갈리는 지점도 다르다.
type Call = { url: string; method: string };
let calls: Call[] = [];

function respondWith(payload: unknown, status = 200) {
  calls = [];
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    calls.push({ url, method: init?.method ?? 'GET' });
    return new Response(JSON.stringify(payload), { status, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

const profile = (extra: Record<string, unknown>) => ({
  data: { userId: 'u2', displayName: '상대', followerCount: 0, followingCount: 0, storyCount: 3, following: false, ...extra },
  error: null,
  meta: { requestId: 'r1' },
});

describe('사용자 차단', () => {
  it('차단은 PUT, 해제는 DELETE 로 같은 주소에 간다', async () => {
    respondWith({ data: { userId: 'u2', blocked: true }, error: null, meta: { requestId: 'r1' } });
    await expect(setBlocked('u2', true, 'token')).resolves.toEqual({ state: 'success', blocked: true });
    expect(calls[0]).toEqual({ url: expect.stringContaining('/api/v1/users/u2/block'), method: 'PUT' });

    respondWith({ data: { userId: 'u2', blocked: false }, error: null, meta: { requestId: 'r1' } });
    await expect(setBlocked('u2', false, 'token')).resolves.toEqual({ state: 'success', blocked: false });
    expect(calls[0]).toEqual({ url: expect.stringContaining('/api/v1/users/u2/block'), method: 'DELETE' });
  });

  it('내가 차단한 상대의 프로필은 blocked 로 온다 — 버튼이 「차단 해제」가 되는 자리', async () => {
    respondWith(profile({ blocked: true, blockedByUser: false }));
    const result = await getUserProfile('u2', 'token');
    if (result.state !== 'success') throw new Error('성공을 기대했다');
    expect(result.profile.blocked).toBe(true);
    expect(result.profile.blockedByUser).toBe(false);
  });

  it('🔴 나를 차단한 상대의 프로필은 blockedByUser 로 온다 — 화면이 「차단되어 볼 수 없습니다」가 되는 자리', async () => {
    respondWith(profile({ blocked: false, blockedByUser: true }));
    const result = await getUserProfile('u2', 'token');
    if (result.state !== 'success') throw new Error('성공을 기대했다');
    expect(result.profile.blocked).toBe(false);
    expect(result.profile.blockedByUser).toBe(true);
  });

  it('차단 값이 없는 옛 서버 응답도 읽는다 — 둘 다 차단 아님으로 다룬다', async () => {
    respondWith(profile({}));
    const result = await getUserProfile('u2', 'token');
    if (result.state !== 'success') throw new Error('성공을 기대했다');
    expect(result.profile.blocked).toBeUndefined();
    expect(result.profile.blockedByUser).toBeUndefined();
  });
});
