// 기록 상세에서 좋아요·저장·링크 복사가 실패할 때 — S15P21E201-1824.
//
// 실패하면 아무 말 없이 돌아가서 버튼이 고장난 것처럼 보였다. 링크 복사는 웹에서 클립보드가 거절되면
// 처리 안 된 오류로 남았다. 실패하면 한 줄 알림이 뜨는지를 본다.
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
const mockClipboard = jest.fn(async () => true);
jest.mock('expo-clipboard', () => ({ setStringAsync: (...args: unknown[]) => mockClipboard(...(args as [])) }));
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
    if (/\/(reaction|save)$/.test(url)) return new Response(JSON.stringify({ data: null, error: { code: 'INTERNAL', message: 'boom' } }), { status: 500, headers: { 'content-type': 'application/json' } });
    throw new Error(`시험이 준비 안 한 요청: ${method} ${url}`);
  }) as unknown as typeof fetch;
});

const FAIL = '지금은 반영하지 못했어요. 잠시 뒤 다시 눌러 주세요.';

describe('기록 상세 실패 알림', () => {
  it('좋아요가 실패하면 조용히 넘어가지 않고 한 줄로 알린다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());
    fireEvent.press(view.getByLabelText('좋아요'));
    await waitFor(() => expect(view.getByText(FAIL)).toBeTruthy());
  });

  it('저장이 실패하면 한 줄로 알린다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());
    await waitFor(() => expect(view.getByLabelText('저장')).toBeTruthy());
    fireEvent.press(view.getByLabelText('저장'));
    await waitFor(() => expect(view.getByText(FAIL)).toBeTruthy());
  });

  it('클립보드가 거절하면 복사했다고 하지 않고 못 했다고 말한다', async () => {
    mockClipboard.mockRejectedValueOnce(new Error('NotAllowedError'));
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());
    fireEvent.press(view.getByLabelText('링크 복사해서 인용하기'));
    await waitFor(() => expect(view.getByText('링크를 복사하지 못했어요.')).toBeTruthy());
    expect(view.queryByText('링크를 복사했어요.')).toBeNull();
  });
});

void ScrollView;
