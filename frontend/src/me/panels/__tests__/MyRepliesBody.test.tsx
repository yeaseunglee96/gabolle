// 마이페이지 「내 댓글」 — S15P21E201-1652 (서버 S15P21E201-1600).
//
// 🔴 이 시험이 지키는 것:
//    ① 원글 상태를 한 줄로 알린다 — 보임이면 「작성자님의 글 · 미리보기」, 가려짐(신고·나만 보기·팔로워 공개·차단)이면
//       「원글을 볼 수 없어요」, 지워짐이면 「원글이 지워졌어요」. 가려진 글을 이 목록으로 엿보지 않는다.
//    ② 서버는 미리보기·이름을 HTML 인코딩해 보낸다(&amp; · &#39;). 그대로 찍으면 「A &amp; B」가 보인다 — 되돌려 보인다.
//    ③ 누르면 보이는 원글이면 원글 상세로, 아니면 내 댓글 상세로 간다.
//    ④ 원글이 지워졌어도 내 댓글은 지울 수 있다.
//    ⑤ 서버가 아직 이 기능을 모르면(404) 오류가 아니라 「준비 중이에요」.
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

const mockPush = jest.fn();
jest.mock('expo-router', () => ({ useRouter: () => ({ push: mockPush, replace: jest.fn(), back: jest.fn() }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', user: { userId: 'me' } }) }));
jest.mock('@/components/DongbaekMascot', () => ({ GabolleMascot: () => null }));

import { MyRepliesBody } from '../MyRepliesBody';

const reply = (id: string, body: string) => ({
  id, author: { id: 'me', displayName: '김부산' }, body, parentId: 'x', images: [], visibility: 'PUBLIC', mine: true, published: true,
  publishAt: '2026-09-24T10:00:00Z', createdAt: '2026-09-24T10:00:00Z', updatedAt: '2026-09-24T10:00:00Z',
});
const PAGE_ONE = {
  items: [
    { reply: reply('reply-1', '저도 가 봤어요 &amp; 좋았어요'), parent: { id: 'parent-1', state: 'VISIBLE', bodyPreview: '광안리 &amp; 민락 &#39;야경&#39;', authorName: '탐 &amp; 제리' } },
    { reply: reply('reply-2', '가려진 글에 단 댓글'), parent: { id: 'parent-2', state: 'HIDDEN', bodyPreview: null, authorName: null } },
    { reply: reply('reply-3', '지워진 글에 단 댓글'), parent: { id: 'parent-3', state: 'DELETED', bodyPreview: null, authorName: null } },
  ],
  nextCursor: 'c2',
};
const PAGE_TWO = { items: [{ reply: reply('reply-4', '둘째 쪽 댓글'), parent: { id: 'parent-4', state: 'VISIBLE', bodyPreview: '해운대', authorName: '이예승' } }], nextCursor: null };

const requests: Array<{ url: string; method: string }> = [];
let mode: 'ok' | 'empty' | 'missing' = 'ok';
const json = (data: unknown, status = 200) => new Response(JSON.stringify({ data, error: null, meta: {} }), { status, headers: { 'content-type': 'application/json' } });

beforeEach(() => {
  mockPush.mockClear();
  requests.length = 0;
  mode = 'ok';
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    requests.push({ url, method });
    if (url.includes('/api/v1/users/me/replies')) {
      if (mode === 'missing') return new Response(JSON.stringify({ data: null, error: { code: 'NOT_FOUND', message: 'No static resource' }, meta: {} }), { status: 404, headers: { 'content-type': 'application/json' } });
      if (mode === 'empty') return json({ items: [], nextCursor: null });
      return json(url.includes('cursor=c2') ? PAGE_TWO : PAGE_ONE);
    }
    if (url.includes('/api/v1/stories/') && method === 'DELETE') return new Response(null, { status: 204 });
    throw new Error(`시험이 준비 안 한 요청: ${method} ${url}`);
  }) as unknown as typeof fetch;
});

const mount = () => render(<OnboardingPreferencesProvider><MyRepliesBody /></OnboardingPreferencesProvider>);

describe('내 댓글', () => {
  it('🔴 원글 상태를 한 줄로 알리고, 서버가 인코딩한 글자를 되돌려 보인다', async () => {
    mount();
    await waitFor(() => expect(screen.getByText('저도 가 봤어요 & 좋았어요')).toBeTruthy());
    expect(screen.getByText("탐 & 제리님의 글 · 광안리 & 민락 '야경'")).toBeTruthy();
    expect(screen.getByText('원글을 볼 수 없어요')).toBeTruthy();
    expect(screen.getByText('원글이 지워졌어요')).toBeTruthy();
    expect(screen.queryByText(/&amp;|&#39;/)).toBeNull();
  });

  it('🔴 보이는 원글이면 원글 상세로, 가려졌거나 지워졌으면 내 댓글 상세로 간다', async () => {
    mount();
    await waitFor(() => expect(screen.getAllByText('자세히 →')).toHaveLength(3));
    const open = screen.getAllByText('자세히 →');
    fireEvent.press(open[0]);
    fireEvent.press(open[1]);
    fireEvent.press(open[2]);
    expect(mockPush.mock.calls.map(([to]) => to)).toEqual(['/feed/parent-1', '/feed/reply-2', '/feed/reply-3']);
  });

  it('🔴 원글이 지워진 댓글도 지울 수 있다 — 확인한 뒤에만 지우고, 성공하면 목록에서 뺀다', async () => {
    mount();
    await waitFor(() => expect(screen.getByText('지워진 글에 단 댓글')).toBeTruthy());
    fireEvent.press(screen.getAllByText('삭제')[2]);
    expect(requests.some((request) => request.method === 'DELETE')).toBe(false);
    await act(async () => { fireEvent.press(screen.getByText('삭제 확정')); });
    await waitFor(() => expect(screen.queryByText('지워진 글에 단 댓글')).toBeNull());
    expect(requests.filter((request) => request.method === 'DELETE').map((request) => request.url)).toEqual([expect.stringContaining('/api/v1/stories/reply-3')]);
  });

  it('더 보기를 누르면 다음 쪽을 뒤에 붙인다', async () => {
    mount();
    await waitFor(() => expect(screen.getByText('더 보기')).toBeTruthy());
    await act(async () => { fireEvent.press(screen.getByText('더 보기')); });
    await waitFor(() => expect(screen.getByText('둘째 쪽 댓글')).toBeTruthy());
    expect(screen.getByText('저도 가 봤어요 & 좋았어요')).toBeTruthy();
    expect(requests.some((request) => request.url.includes('cursor=c2'))).toBe(true);
    expect(screen.queryByText('더 보기')).toBeNull();
  });

  it('🔴 서버가 아직 이 기능을 모르면(404) 오류가 아니라 「준비 중이에요」', async () => {
    mode = 'missing';
    mount();
    await waitFor(() => expect(screen.getByText('준비 중이에요')).toBeTruthy());
    expect(screen.queryByText(/불러오지 못했어요/)).toBeNull();
  });

  it('남긴 댓글이 없으면 빈 상태를 보인다', async () => {
    mode = 'empty';
    mount();
    await waitFor(() => expect(screen.getByText('아직 남긴 댓글이 없어요')).toBeTruthy());
  });
});
