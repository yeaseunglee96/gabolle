// 사진 칸을 「뗀다」고 말하는 값 — S15P21E201-1308.
//
// 🔴 이 시험이 지키는 것은 「기능이 도는가」가 아니라 **서버와 뜻이 같은가**이다.
//    서버에는 「없음」이 한 종류뿐이라, 키를 안 보낸 것과 null 을 보낸 것이 똑같이
//    도착한다. 그래서 서버가 빈 문자열을 「뗀다」로 정했다.
//
//    2026-09-19 까지 화면은 null 을 보내고 있었다. 서버는 그것을 「안 바꾼다」로 읽고
//    **사진이 그대로인 계정 정보를 200 으로 돌려줬다.** 실패가 아니어서 아무도 몰랐고,
//    화면은 「기본 프로필로 돌아왔어요」까지 띄우고 있었다.
import { updateMe } from '../authApi';

const originalFetch = globalThis.fetch;

afterEach(() => {
  globalThis.fetch = originalFetch;
  jest.restoreAllMocks();
});

/** 서버가 준 답 대신 아무 계정이나 돌려준다. 여기서 보는 것은 **보낸 것**이다. */
function captureBody() {
  const fetchMock = jest.fn().mockResolvedValue({
    ok: true,
    status: 200,
    headers: { get: () => 'application/json' },
    json: async () => ({ data: { userId: 'u1', email: 'a@b.c', displayName: '진미리', language: 'KO', status: 'ACTIVE' } }),
    text: async () => '',
  });
  globalThis.fetch = fetchMock as unknown as typeof fetch;
  // 🔴 첫 호출을 집으면 안 된다. 그 전에 **익명 세션 표를 받아 오는 요청**이 한 번 나가고,
  //    그 요청에는 바디가 없다. 주소로 골라야 다음에 요청이 하나 더 늘어도 안 깨진다.
  return () => {
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/api/v1/auth/me'));
    if (!call) throw new Error('내 계정 고치기 요청이 안 나갔다');
    return JSON.parse((call[1] as RequestInit).body as string);
  };
}

describe('사진을 뗀다고 말하는 값', () => {
  it('🔴 프로필 사진: null 은 빈 문자열로 나간다 — null 을 그대로 보내면 서버가 안 바꾼다', async () => {
    const body = captureBody();

    await updateMe('token', { avatarUrl: null });

    expect(body()).toEqual({ avatarUrl: '' });
  });

  it('🔴 커버 사진도 같은 규칙이다 — 두 사진이 한 화면에 나란히 있다', async () => {
    const body = captureBody();

    await updateMe('token', { coverUrl: null });

    expect(body()).toEqual({ coverUrl: '' });
  });

  it('주소를 주면 그대로 나간다', async () => {
    const body = captureBody();

    await updateMe('token', { coverUrl: 'https://cdn/x.jpg' });

    expect(body()).toEqual({ coverUrl: 'https://cdn/x.jpg' });
  });

  it('🔴 키를 안 보내면 그 칸은 바디에 없다 — 「안 바꾼다」와 「뗀다」가 서로 다른 값이어야 한다', async () => {
    const body = captureBody();

    await updateMe('token', { displayName: '새 이름' });

    expect(body()).toEqual({ displayName: '새 이름' });
    expect('avatarUrl' in body()).toBe(false);
    expect('coverUrl' in body()).toBe(false);
  });
});
