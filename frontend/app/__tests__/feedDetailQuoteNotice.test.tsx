// 기록 상세의 인용(링크 복사) 알림 — S15P21E201-1787.
//
// 목록에서는 인용을 누르면 화면 아래에 「링크를 복사했어요」가 떴는데, 상세에서는 같은 알림이 댓글 아래 글 맨 끝에
// 붙었다. 글 중간에서 누르면 화면에 아무 변화가 없어 「안 눌린다」로 보였다. 알림이 굴러가는 판 밖에 뜨는지를 본다.
import type { ReactElement } from 'react';
import { fireEvent, render as rtlRender, waitFor } from '@testing-library/react-native';
import { ScrollView } from 'react-native';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

jest.setTimeout(30000);

function render(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return rtlRender(<QueryClientProvider client={queryClient}><OnboardingPreferencesProvider>{ui}</OnboardingPreferencesProvider></QueryClientProvider>);
}

const mockAuth = { accessToken: 'token' };

jest.mock('expo-router', () => ({
  useRouter: () => ({ back: jest.fn(), replace: jest.fn(), push: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => ({ id: STORY_ID }),
  useFocusEffect: (cb: () => void | (() => void)) => {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    require('react').useEffect(() => cb(), [cb]);
  },
}));
jest.mock('expo-clipboard', () => ({ setStringAsync: jest.fn(async () => true) }));
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

function json(data: unknown) {
  return new Response(envelope(data), { status: 200, headers: { 'content-type': 'application/json' } });
}

const story = {
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
  linkCopyCount: 0,
};

beforeEach(() => {
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    if (url.includes('/auth/anonymous')) return json({ sessionId: 's', sessionToken: 't', issuedAt: '' });
    if (url.endsWith(`/api/v1/stories/${STORY_ID}`) && method === 'GET') return json(story);
    if (url.endsWith(`/api/v1/stories/${STORY_ID}/replies`)) return json([]);
    if (url.endsWith(`/api/v1/stories/${STORY_ID}/link-copies`) && method === 'POST') return json({ ...story, linkCopyCount: 1 });
    if (url.endsWith(`/api/v1/users/${AUTHOR_ID}/profile`)) return json({ userId: AUTHOR_ID, displayName: '이예승', followerCount: 0, followingCount: 0, storyCount: 0, following: false });
    if (url.endsWith('/api/v1/me/saved-stories')) return json({ items: [] });
    throw new Error(`시험이 준비 안 한 요청: ${method} ${url}`);
  }) as unknown as typeof fetch;
});

/** 이 노드가 굴러가는 판(ScrollView) 안에 있나. 안에 있으면 글 중간에서는 안 보인다. */
function insideScroll(node: { parent?: unknown; type?: unknown } | null): boolean {
  for (let n = node; n; n = n.parent as typeof n) if (n.type === ScrollView) return true;
  return false;
}

describe('기록 상세 인용 알림', () => {
  it('인용을 누르면 알림이 굴러가는 판 밖, 화면 아래에 뜬다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());

    fireEvent.press(view.getByLabelText('링크 복사해서 인용하기'));

    const notice = await waitFor(() => view.getByText('링크를 복사했어요.'));
    expect(insideScroll(notice)).toBe(false);
    await waitFor(() => expect(view.getAllByText('인용 1').length).toBeGreaterThan(0));
  });

  it('알림은 잠깐 떴다 사라진다 — 다음에 눌렀을 때 새로 뜬 것을 알 수 있다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());

    fireEvent.press(view.getByLabelText('링크 복사해서 인용하기'));
    await waitFor(() => expect(view.getByText('링크를 복사했어요.')).toBeTruthy());

    // 가짜 시계는 불러오기(react-query·fetch)와 엉켜 멈춘다 — 실제 시간 2.6초를 기다린다.
    await waitFor(() => expect(view.queryByText('링크를 복사했어요.')).toBeNull(), { timeout: 4000 });
  });
});
