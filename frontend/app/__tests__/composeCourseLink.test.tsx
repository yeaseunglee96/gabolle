// 글쓰기의 코스 링크 — S15P21E201-1593.
//
// 🔴 이 시험이 지키는 것:
//    ① 여행 화면의 「기록 남기기」(tripId)로 온 글쓰기에만 「이 여행과 연결됨」·「코스 링크 함께 올리기」가 있다.
//    ② 켜고 올리면 읽기 전용 링크를 만들어 본문 끝에 붙인다. 끄면 링크를 만들지도 붙이지도 않는다.
//    ③ 링크를 못 만들면 링크 없이 몰래 올리지 않는다 — 올리지 않고 말한다.
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { SafeAreaProvider, initialWindowMetrics } from 'react-native-safe-area-context';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

let mockParams: { tripId?: string } = {};
const mockCreateStory = jest.fn();
const mockIssue = jest.fn();
jest.mock('expo-router', () => ({
  useRouter: () => ({ back: jest.fn(), replace: jest.fn(), push: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => mockParams,
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', user: { userId: 'me' } }) }));
jest.mock('@/social/stories', () => ({ ...jest.requireActual('@/social/stories'), createStory: (...args: unknown[]) => mockCreateStory(...args) }));
jest.mock('@/share/sharedItinerary', () => ({ ...jest.requireActual('@/share/sharedItinerary'), issueShareLink: (...args: unknown[]) => mockIssue(...args) }));
jest.mock('@/onboarding/firstRun', () => ({ markChecklistStep: jest.fn() }));

import Compose from '../feed/compose';

const SHARE_URL = 'https://j15e201.p.ssafy.io/s/tok123';
const WAIT = { timeout: 5000 };
// 🔴 기다림(WAIT 5초)보다 시험 제한 시간이 길어야 기다림이 먹힌다. jest 기본 제한 시간도 5초라, 부하가 걸리면 기다림이
//    끝나기 전에 시험이 먼저 끝났다(「Exceeded timeout of 5000 ms」 — S15P21E201-1665). 파일 전체에 넉넉히 준다.
jest.setTimeout(20000);
const Providers = ({ children }: { children: ReactNode }) => (
  <SafeAreaProvider initialMetrics={initialWindowMetrics ?? { frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } }}>
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })}>
      <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>
    </QueryClientProvider>
  </SafeAreaProvider>
);

beforeEach(() => {
  mockParams = { tripId: 'trip-1' };
  mockCreateStory.mockReset().mockResolvedValue({ state: 'success', story: { id: 's1' } });
  mockIssue.mockReset().mockResolvedValue({ shareUrl: SHARE_URL, token: 'tok123', expiresAt: '2026-10-24T00:00:00Z' });
});

async function writeAndPost(text: string) {
  fireEvent.changeText(await screen.findByTestId('compose-body', {}, WAIT), text);
  fireEvent.press(screen.getByTestId('compose-submit'));
}

describe('글쓰기 — 코스 링크', () => {
  it('여행 화면에서 오지 않았으면 「이 여행과 연결됨」도 코스 링크도 없다', async () => {
    mockParams = {};
    render(<Compose />, { wrapper: Providers });
    expect(await screen.findByTestId('compose-body', {}, WAIT)).toBeTruthy();
    expect(screen.queryByText('✓ 이 여행과 연결됨')).toBeNull();
    expect(screen.queryByText('코스 링크 함께 올리기')).toBeNull();
  }, 20000);

  it('🔴 켜고 올리면 읽기 전용 링크를 만들어 본문 끝에 붙인다', async () => {
    render(<Compose />, { wrapper: Providers });
    expect(await screen.findByText('✓ 이 여행과 연결됨', {}, WAIT)).toBeTruthy();
    fireEvent.press(screen.getByRole('switch'));
    await writeAndPost('광안리 다녀왔어요');

    await waitFor(() => expect(mockCreateStory).toHaveBeenCalled());
    expect(mockIssue).toHaveBeenCalledWith('trip-1', 'token');
    expect(mockCreateStory.mock.calls[0][0]).toMatchObject({ body: `광안리 다녀왔어요\n\n${SHARE_URL}`, tripId: 'trip-1' });
  });

  it('끄면(기본) 링크를 만들지도 붙이지도 않는다', async () => {
    render(<Compose />, { wrapper: Providers });
    await writeAndPost('광안리 다녀왔어요');

    await waitFor(() => expect(mockCreateStory).toHaveBeenCalled());
    expect(mockIssue).not.toHaveBeenCalled();
    expect(mockCreateStory.mock.calls[0][0].body).toBe('광안리 다녀왔어요');
  });

  it('🔴 링크를 못 만들면 올리지 않고 말한다 — 링크 없이 몰래 올리지 않는다', async () => {
    mockIssue.mockRejectedValue(new Error('500'));
    render(<Compose />, { wrapper: Providers });
    fireEvent.press(await screen.findByRole('switch', {}, WAIT));
    await writeAndPost('광안리 다녀왔어요');

    expect(await screen.findByText('코스 링크를 만들지 못했어요. 잠시 뒤 다시 올리거나 코스 링크를 끄고 올려 주세요.', {}, WAIT)).toBeTruthy();
    expect(mockCreateStory).not.toHaveBeenCalled();
  });
});
