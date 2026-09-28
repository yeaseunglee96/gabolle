// 공개 전 기록 — S15P21E201-1737. 2026-09-26 실기: 「여행이 끝난 뒤」로 올린 글이 어디에도 안 보여
// 「올라갔나?」를 알 길이 없었다. 서버는 이제 본인에게 공개 전 글을 준다(published=false). 앱은 그것을 표시한다.
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

const mockBack = jest.fn();
const mockLoadUserStories = jest.fn();
const mockCreateStory = jest.fn();
jest.mock('expo-router', () => ({
  useRouter: () => ({ back: mockBack, replace: jest.fn(), push: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => ({}),
  useFocusEffect: (effect: () => void) => { const { useEffect } = jest.requireActual('react'); useEffect(effect, [effect]); },
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', user: { userId: 'me' } }) }));
jest.mock('@/social/stories', () => ({
  ...jest.requireActual('@/social/stories'),
  loadUserStories: (...args: unknown[]) => mockLoadUserStories(...args),
  createStory: (...args: unknown[]) => mockCreateStory(...args),
}));
jest.mock('@/onboarding/firstRun', () => ({ markChecklistStep: jest.fn() }));

import { MyPostsBody } from '../panels/MyPostsBody';
import Compose from '../../../app/feed/compose';

const WAIT = { timeout: 5000 };
jest.setTimeout(20000);
const METRICS = { frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } };
const Providers = ({ children }: { children: ReactNode }) => (
  <SafeAreaProvider initialMetrics={METRICS}>
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })}>
      <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>
    </QueryClientProvider>
  </SafeAreaProvider>
);

const story = (id: string, published: boolean) => ({
  id, author: { id: 'me', displayName: '나' }, body: `글 ${id}`, images: [], visibility: 'PRIVATE',
  publishAt: '2026-09-27T00:00:00+09:00', createdAt: '2026-09-26T07:00:00Z', updatedAt: '2026-09-26T07:00:00Z',
  mine: true, published,
});

beforeEach(() => {
  mockBack.mockReset();
  mockCreateStory.mockReset();
  mockLoadUserStories.mockReset();
});

describe('마이페이지 기록 — 공개 전 표시', () => {
  it('🔴 공개 전 기록에만 「공개 예정」이 붙는다', async () => {
    mockLoadUserStories.mockResolvedValue({ state: 'success', items: [story('pending', false), story('live', true)], nextCursor: null });
    render(<MyPostsBody />, { wrapper: Providers });
    expect(await screen.findByTestId('pending-pending', {}, WAIT)).toBeTruthy();
    expect(screen.queryByTestId('pending-live')).toBeNull();
  }, 20000);
});

describe('글쓰기 — 공개 전으로 올린 뒤', () => {
  async function post() {
    render(<Compose />, { wrapper: Providers });
    fireEvent.changeText(await screen.findByTestId('compose-body', {}, WAIT), '광안리 다녀왔어요');
    fireEvent.press(screen.getByTestId('compose-submit'));
  }

  it('🔴 공개 전이면 바로 돌아가지 않고 언제 공개되는지 말한다', async () => {
    mockCreateStory.mockResolvedValue({ state: 'success', story: story('s1', false) });
    await post();
    expect(await screen.findByTestId('compose-scheduled', {}, WAIT)).toBeTruthy();
    expect(mockBack).not.toHaveBeenCalled();
    fireEvent.press(screen.getByText('확인'));
    expect(mockBack).toHaveBeenCalled();
  }, 20000);

  it('바로 공개된 글이면 전처럼 곧장 돌아간다', async () => {
    mockCreateStory.mockResolvedValue({ state: 'success', story: story('s2', true) });
    await post();
    await waitFor(() => expect(mockBack).toHaveBeenCalled());
    expect(screen.queryByTestId('compose-scheduled')).toBeNull();
  }, 20000);
});
