// 빌드 47 폴드 확인 — S15P21E201-1990.
//
// 🔴 이 시험이 지키는 것
//    ① 앱 안에서 공유 링크(s/…)를 열면 나갈 길이 없었다 — 화면에 뒤로 단추가 없고, 안드로이드 뒤로 가기도 먹지 않았다.
//       앱에서는 뒤로 단추를 보이고, 갈 곳이 없으면 홈으로 간다. 웹(로그인 없이 보는 사람)은 그대로 둔다.
//    ② 이름 붙이기 창 첫 문장이 「지금은 카드에 날짜 6곳 · 약 9.1만원 · 이동 69분가 보여요」였다.
//       넘어오는 값은 날짜일 때도, 요약일 때도 있어서 「날짜」라고 부르면 틀리고, 「가」는 받침을 안 본다.
// tsconfig 가 node 타입을 안 들고 있어서 require 로 읽는다(다른 시험과 같은 방식).
// eslint-disable-next-line @typescript-eslint/no-explicit-any
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import type { SharedItineraryDto } from '@/share/sharedItinerary';

const mockRouter = { push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: jest.fn(() => false) };
jest.mock('expo-router', () => ({
  useRouter: () => mockRouter,
  useLocalSearchParams: () => ({ token: 'abc123' }),
}));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 0, left: 0, right: 0, bottom: 0 }),
}));
jest.mock('@/plan/PlanProvider', () => ({ usePlan: () => ({ update: jest.fn(), clear: jest.fn(async () => {}) }) }));
jest.mock('@/share/sharedItinerary', () => ({ getSharedItinerary: jest.fn() }));
const { getSharedItinerary } = jest.requireMock('@/share/sharedItinerary') as { getSharedItinerary: jest.Mock };

import SharedItinerary from '../s/[token]';

const DATA = {
  title: '부산 가을 바다 2박 3일', startDate: '2026-10-03', finishDate: '2026-10-05', expiresAt: '2026-10-25T00:00:00+09:00',
  notShared: [], days: [],
} as unknown as SharedItineraryDto;

jest.setTimeout(20000);

beforeEach(() => {
  getSharedItinerary.mockResolvedValue({ state: 'success', data: DATA });
  mockRouter.back.mockClear(); mockRouter.replace.mockClear();
});

describe('앱 안 공유 페이지에서 나가기', () => {
  it('🔴 뒤로 단추가 있고, 갈 곳이 없으면 홈으로', async () => {
    render(<OnboardingPreferencesProvider><SharedItinerary /></OnboardingPreferencesProvider>);
    await waitFor(() => expect(screen.getByText('부산 가을 바다 2박 3일')).toBeTruthy(), { timeout: 10000 });
    fireEvent.press(screen.getByLabelText('뒤로 가기'));
    expect(mockRouter.replace).toHaveBeenCalledWith('/home');
  });

  it('🔴 갈 곳이 있으면 뒤로', async () => {
    mockRouter.canGoBack.mockReturnValueOnce(true);
    render(<OnboardingPreferencesProvider><SharedItinerary /></OnboardingPreferencesProvider>);
    await waitFor(() => expect(screen.getByText('부산 가을 바다 2박 3일')).toBeTruthy(), { timeout: 10000 });
    mockRouter.canGoBack.mockReturnValueOnce(true);
    fireEvent.press(screen.getByLabelText('뒤로 가기'));
    expect(mockRouter.back).toHaveBeenCalled();
  });

  it('🔴 안드로이드 뒤로 가기를 이 화면이 받는다', () => {
    const src = readFileSync(join(__dirname, '../s/[token].tsx'), 'utf8');
    expect(src).toMatch(/BackHandler\.addEventListener\('hardwareBackPress'/);
  });
});

describe('이름 붙이기 창 첫 문장', () => {
  const sheet = readFileSync(join(__dirname, '../../src/trip/TripNameSheet.tsx'), 'utf8');
  it('🔴 「날짜」라고 부르지 않고, 받침을 안 보는 「가 보여요」를 쓰지 않는다', () => {
    expect(sheet).not.toContain("'지금은 카드에 날짜 '");
    expect(sheet).not.toContain("'가 보여요.'");
    expect(sheet).toContain("'지금 카드에는 이렇게 보여요: '");
  });
});
