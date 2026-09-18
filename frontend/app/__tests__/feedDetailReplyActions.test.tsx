// 댓글 수정·삭제·신고 — S15P21E201-1239.
//
// 🔴 사용자 신고: "피드에 댓글을 썼는데 댓글 수정하거나 지우기, 신고 버튼이 없네".
//    서버는 이미 열려 있었다(PATCH/DELETE /stories/{id}, POST /stories/{id}/reports —
//    댓글도 같은 story 표라 그대로 된다) — 없던 것은 화면뿐이었다. 이 시험은 그 화면이
//    맞는 요청을 맞는 대상(댓글 id)으로 보내는지, 그리고 원글 신고와 달리 화면 전체를
//    지우지 않고 그 댓글 한 장만 빼는지를 잰다.
import type { ReactElement } from 'react';
import { fireEvent, render as rtlRender, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

function render(ui: ReactElement) {
  return rtlRender(<OnboardingPreferencesProvider>{ui}</OnboardingPreferencesProvider>);
}

const mockBack = jest.fn();
const mockReplace = jest.fn();
const mockPush = jest.fn();
const mockCanGoBack = jest.fn(() => true);
const mockAuth = { accessToken: 'token' };

jest.mock('expo-router', () => ({
  useRouter: () => ({ back: mockBack, replace: mockReplace, push: mockPush, canGoBack: mockCanGoBack }),
  useLocalSearchParams: () => ({ id: STORY_ID }),
  useFocusEffect: (cb: () => void | (() => void)) => {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    require('react').useEffect(() => cb(), [cb]);
  },
}));

jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => mockAuth }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }) }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

import StoryDetail from '../feed/[id]';

const STORY_ID = '11111111-1111-1111-1111-111111111111';
const MY_REPLY_ID = '22222222-2222-2222-2222-222222222222';
const OTHER_REPLY_ID = '33333333-3333-3333-3333-333333333333';

function post() {
  return {
    id: STORY_ID,
    author: { id: 'author-1', displayName: '이예승' },
    body: '오늘의 기록',
    parentId: null,
    images: [],
    visibility: 'PUBLIC',
    publishAt: '2026-09-18T00:00:00Z',
    createdAt: '2026-09-18T00:00:00Z',
    updatedAt: '2026-09-18T00:00:00Z',
    mine: false,
    published: true,
    replyCount: 2,
  };
}

function myReply() {
  return {
    id: MY_REPLY_ID,
    author: { id: 'me', displayName: '진미리' },
    body: '제 댓글이에요',
    parentId: STORY_ID,
    images: [],
    visibility: 'PUBLIC',
    publishAt: '2026-09-18T00:00:00Z',
    createdAt: '2026-09-18T00:00:00Z',
    updatedAt: '2026-09-18T00:00:00Z',
    mine: true,
    published: true,
  };
}

function otherReply() {
  return {
    id: OTHER_REPLY_ID,
    author: { id: 'stranger', displayName: '모진성' },
    body: '남의 댓글이에요',
    parentId: STORY_ID,
    images: [],
    visibility: 'PUBLIC',
    publishAt: '2026-09-18T00:00:00Z',
    createdAt: '2026-09-18T00:00:00Z',
    updatedAt: '2026-09-18T00:00:00Z',
    mine: false,
    published: true,
  };
}

const requests: Array<{ url: string; method: string; body: unknown }> = [];

function envelope(data: unknown) {
  return JSON.stringify({ data, error: null, meta: { requestId: 'r' } });
}

function installFetch() {
  requests.length = 0;
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    if (url.includes('/auth/anonymous')) {
      return new Response(envelope({ sessionId: 's', sessionToken: 't', issuedAt: '' }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    requests.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : undefined });

    if (url.endsWith(`/api/v1/stories/${STORY_ID}`) && method === 'GET') {
      return new Response(envelope(post()), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    if (url.endsWith(`/api/v1/stories/${STORY_ID}/replies`)) {
      return new Response(envelope([myReply(), otherReply()]), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    if (url.endsWith(`/api/v1/stories/${MY_REPLY_ID}`) && method === 'PATCH') {
      return new Response(envelope({ ...myReply(), body: '고친 댓글' }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    if (url.endsWith(`/api/v1/stories/${MY_REPLY_ID}`) && method === 'DELETE') {
      return new Response(null, { status: 204 });
    }
    if (url.endsWith(`/api/v1/stories/${OTHER_REPLY_ID}/reports`) && method === 'POST') {
      return new Response(null, { status: 204 });
    }
    throw new Error(`시험이 준비 안 한 요청: ${method} ${url}`);
  }) as unknown as typeof fetch;
}

beforeEach(() => {
  jest.clearAllMocks();
  installFetch();
});

describe('댓글 카드 — 수정·삭제·신고', () => {
  it('내 댓글에는 수정·삭제가, 남의 댓글에는 신고가 보인다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('제 댓글이에요')).toBeTruthy());

    expect(view.getAllByLabelText('댓글 수정')).toHaveLength(1);
    expect(view.getAllByLabelText('댓글 삭제')).toHaveLength(1);
    expect(view.getAllByLabelText('댓글 신고')).toHaveLength(1);
    // 내 댓글에는 신고가, 남의 댓글에는 수정·삭제가 섞여 있지 않다.
    expect(view.queryAllByLabelText('댓글 수정')).toHaveLength(1);
  });

  it('🔴 수정 — PATCH 가 그 댓글 id 로 나가고, 화면이 고친 내용으로 바뀐다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('제 댓글이에요')).toBeTruthy());

    fireEvent.press(view.getByLabelText('댓글 수정'));
    const editField = view.getByDisplayValue('제 댓글이에요');
    fireEvent.changeText(editField, '고친 댓글');
    fireEvent.press(view.getByText('저장'));

    await waitFor(() => expect(view.getByText('고친 댓글')).toBeTruthy());
    const patchCall = requests.find((r) => r.method === 'PATCH');
    expect(patchCall?.url).toContain(`/stories/${MY_REPLY_ID}`);
    expect(patchCall?.body).toEqual({ body: '고친 댓글' });
  });

  it('🔴 삭제 — 확인 뒤 DELETE 가 그 댓글 id 로 나가고, 목록에서 빠진다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('제 댓글이에요')).toBeTruthy());

    fireEvent.press(view.getByLabelText('댓글 삭제'));
    fireEvent.press(view.getByText('삭제 확정'));

    await waitFor(() => expect(view.queryByText('제 댓글이에요')).toBeNull());
    const deleteCall = requests.find((r) => r.method === 'DELETE');
    expect(deleteCall?.url).toContain(`/stories/${MY_REPLY_ID}`);
    // 남의 댓글은 그대로 남는다 — 지운 것은 딱 하나뿐이다.
    expect(view.getByText('남의 댓글이에요')).toBeTruthy();
  });

  it('🔴 신고 — 그 댓글 id 로 신고가 나가고, 화면 전체가 아니라 그 댓글 한 장만 사라진다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('남의 댓글이에요')).toBeTruthy());

    fireEvent.press(view.getByLabelText('댓글 신고'));
    fireEvent.press(view.getByText('스팸'));
    fireEvent.press(view.getByText('신고 접수'));

    await waitFor(() => expect(view.queryByText('남의 댓글이에요')).toBeNull());
    const reportCall = requests.find((r) => r.url.includes('/reports'));
    expect(reportCall?.url).toContain(`/stories/${OTHER_REPLY_ID}/reports`);
    // 원글 신고와 다르다 — "신고가 접수됐어요" 전체 화면 안내로 안 바뀐다. 원글 본문은 그대로 있다.
    expect(view.getByText('오늘의 기록')).toBeTruthy();
    expect(view.queryByText('신고가 접수됐어요')).toBeNull();
  });
});
