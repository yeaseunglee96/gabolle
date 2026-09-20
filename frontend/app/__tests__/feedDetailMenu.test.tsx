// 피드 상세 우상단 ⋯ 메뉴 — S15P21E201-1244.
import type { ReactElement } from 'react';
import { fireEvent, render as rtlRender, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

jest.setTimeout(30000);

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
const AUTHOR_ID = '44444444-4444-4444-4444-444444444444';

function envelope(data: unknown) {
  return JSON.stringify({ data, error: null, meta: { requestId: 'r' } });
}

function post(overrides: Record<string, unknown> = {}) {
  return {
    id: STORY_ID,
    author: { id: AUTHOR_ID, displayName: '이예승' },
    body: '오늘의 기록',
    parentId: null,
    images: [],
    visibility: 'PUBLIC',
    publishAt: '2026-09-18T00:00:00Z',
    createdAt: '2026-09-18T00:00:00Z',
    updatedAt: '2026-09-18T00:00:00Z',
    mine: false,
    published: true,
    ...overrides,
  };
}

const requests: Array<{ url: string; method: string; body: unknown }> = [];
let following = false;

function installFetch() {
  requests.length = 0;
  following = false;
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    if (url.includes('/auth/anonymous')) {
      return new Response(envelope({ sessionId: 's', sessionToken: 't', issuedAt: '' }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    requests.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : undefined });

    if (url.endsWith(`/api/v1/stories/${STORY_ID}`) && method === 'GET') {
      return new Response(envelope(post({ mine: mineStory })), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    if (url.endsWith(`/api/v1/stories/${STORY_ID}/replies`)) {
      return new Response(envelope([]), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    if (url.endsWith(`/api/v1/users/${AUTHOR_ID}/profile`) && method === 'GET') {
      return new Response(envelope({ userId: AUTHOR_ID, displayName: '이예승', followerCount: 0, followingCount: 0, storyCount: 0, following }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    if (url.endsWith(`/api/v1/users/${AUTHOR_ID}/follow`) && (method === 'PUT' || method === 'DELETE')) {
      following = method === 'PUT';
      return new Response(envelope({ userId: AUTHOR_ID, following, followerCount: following ? 1 : 0, followingCount: 0 }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    if (url.endsWith(`/api/v1/stories/${STORY_ID}/reports`) && method === 'POST') {
      return new Response(null, { status: 204 });
    }
    if (url.endsWith(`/api/v1/stories/${STORY_ID}`) && method === 'DELETE') {
      return new Response(null, { status: 204 });
    }
    throw new Error(`시험이 준비 안 한 요청: ${method} ${url}`);
  }) as unknown as typeof fetch;
}

let mineStory = false;

beforeEach(() => {
  jest.clearAllMocks();
  mineStory = false;
  installFetch();
});

describe('피드 상세 ⋯ 메뉴', () => {
  it('내 글이면 삭제만 보인다', async () => {
    mineStory = true;
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());

    fireEvent.press(view.getByLabelText('더 보기'));

    expect(view.getByText('삭제')).toBeTruthy();
    expect(view.queryByText('신고하기')).toBeNull();
    expect(view.queryByText('사용자 차단')).toBeNull();
  });

  it('🔴 남의 글이면 팔로우·신고·차단이 보인다 — 팔로우 상태를 안 지어낸다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());

    fireEvent.press(view.getByLabelText('더 보기'));

    await waitFor(() => expect(view.getByText('팔로우')).toBeTruthy());
    expect(view.getByText('이 글 신고')).toBeTruthy();
    expect(view.getByText('사용자 차단')).toBeTruthy();
    expect(view.queryByText('삭제')).toBeNull();
  });

  it('🔴 팔로우를 누르면 PUT 이 나가고, 다시 열면 "팔로잉 취소"로 바뀐다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());

    fireEvent.press(view.getByLabelText('더 보기'));
    await waitFor(() => expect(view.getByText('팔로우')).toBeTruthy());
    fireEvent.press(view.getByText('팔로우'));

    await waitFor(() => {
      const followCall = requests.find((r) => r.url.endsWith(`/users/${AUTHOR_ID}/follow`));
      expect(followCall?.method).toBe('PUT');
    });

    fireEvent.press(view.getByLabelText('더 보기'));
    await waitFor(() => expect(view.getByText('팔로잉 취소')).toBeTruthy());
  });

  it('삭제를 고르면 메뉴가 닫히고 기존 인라인 삭제 확인이 뜬다', async () => {
    mineStory = true;
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());

    fireEvent.press(view.getByLabelText('더 보기'));
    fireEvent.press(view.getByText('삭제'));

    await waitFor(() => expect(view.getByText('정말 삭제할까요? 되돌릴 수 없어요.')).toBeTruthy());
    // 메뉴는 닫혔다 — "삭제 확정" 버튼(확인 흐름)과 메뉴 항목 "삭제"가 동시에 있지 않다.
    expect(view.queryAllByText('삭제').length).toBe(0);
    expect(view.getByText('삭제 확정')).toBeTruthy();
  });

  it('🔴 바깥을 누르면 메뉴가 닫힌다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());

    fireEvent.press(view.getByLabelText('더 보기'));
    await waitFor(() => expect(view.getByText('이 글 신고')).toBeTruthy());

    fireEvent.press(view.getByLabelText('메뉴 닫기'));

    await waitFor(() => expect(view.queryByText('이 글 신고')).toBeNull());
  });
});
