import { API_BASE_URL } from '@/api/client';

import { getUserProfile } from '../stories';

// 남의 프로필 배경 사진 — S15P21E201-1842(화면) / -1841(서버).
// 사용자가 고른 배경 사진이 남의 프로필에서는 늘 기본 사진이었다. 서버가 칸을 싣고, 앱은
// 프로필 사진과 같은 저장소 주소라 같은 방식으로 풀어 쓴다.
function respondWith(payload: unknown) {
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL) => {
    if (String(input).includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    return new Response(JSON.stringify(payload), { status: 200, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

const profile = (extra: Record<string, unknown>) => ({
  data: { userId: 'u2', displayName: '상대', followerCount: 0, followingCount: 0, storyCount: 3, following: false, ...extra },
  error: null,
  meta: { requestId: 'r1' },
});

describe('남의 프로필 배경 사진', () => {
  it('🔴 서버가 준 저장소 주소를 화면용 주소로 풀어 쓴다 — 프로필 사진과 같은 규칙', async () => {
    respondWith(profile({ avatarUrl: '/api/v1/uploads/images/a.webp', coverUrl: '/api/v1/uploads/images/c.webp' }));
    const result = await getUserProfile('u2', 'token');
    if (result.state !== 'success') throw new Error('성공을 기대했다');
    expect(result.profile.coverUrl).toBe(`${API_BASE_URL}/api/v1/uploads/images/c.webp`);
    expect(result.profile.avatarUrl).toBe(`${API_BASE_URL}/api/v1/uploads/images/a.webp`);
  });

  it('이미 완전한 주소면 그대로 둔다', async () => {
    respondWith(profile({ coverUrl: 'https://cdn.example.test/c.webp' }));
    const result = await getUserProfile('u2', 'token');
    if (result.state !== 'success') throw new Error('성공을 기대했다');
    expect(result.profile.coverUrl).toBe('https://cdn.example.test/c.webp');
  });

  it('안 골랐으면(null) 또는 칸이 없는 옛 서버면 비워 둔다 — 화면이 기본 사진을 깐다', async () => {
    respondWith(profile({ coverUrl: null }));
    const chosenNone = await getUserProfile('u2', 'token');
    if (chosenNone.state !== 'success') throw new Error('성공을 기대했다');
    expect(chosenNone.profile.coverUrl ?? null).toBeNull();

    respondWith(profile({}));
    const oldServer = await getUserProfile('u2', 'token');
    if (oldServer.state !== 'success') throw new Error('성공을 기대했다');
    expect(oldServer.profile.coverUrl).toBeUndefined();
  });
});
