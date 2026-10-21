// 축제 기간 고르기 — UI 캔버스 ⑪.
//
// 🔴 이 시험이 지키는 것: 「YYYY-MM-DD」 칸에 숫자를 쳐 넣던 것을 칩 한 번으로.
//    ① 이번 주말은 «다가오는» 토·일이다 — 지난 주말이면 끝난 축제가 섞인다
//    ② 칩을 누르면 조회 단추 없이 바로 그 기간으로 찾는다
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => true }), usePathname: () => '/festivals' }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 0, left: 0, right: 0, bottom: 0 }),
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: null }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', desktop: false, width: 390, height: 844, isLandscape: false }) }));
jest.mock('@/discovery/festivals', () => ({ ...jest.requireActual('@/discovery/festivals'), getFestivals: jest.fn() }));
const { getFestivals } = jest.requireMock('@/discovery/festivals') as { getFestivals: jest.Mock };

import Festivals, { weekendRange } from '../festivals';
import { addDays } from '@/home/startBarValue';
import { localDateKey } from '@/plan/tripProgress';

jest.setTimeout(20000);

describe('이번 주말', () => {
  it('🔴 화요일이면 이번 토·일', () => {
    expect(weekendRange(new Date(2026, 8, 29))).toEqual({ from: '2026-10-03', to: '2026-10-04' });
  });
  it('토요일이면 오늘부터, 일요일이면 오늘 하루', () => {
    expect(weekendRange(new Date(2026, 9, 3))).toEqual({ from: '2026-10-03', to: '2026-10-04' });
    expect(weekendRange(new Date(2026, 9, 4))).toEqual({ from: '2026-10-04', to: '2026-10-04' });
  });
});

describe('기간 칩', () => {
  beforeEach(() => getFestivals.mockReset().mockResolvedValue([]));

  it('🔴 처음엔 오늘부터 30일, 「이번 주말」을 누르면 바로 그 기간으로 찾는다', async () => {
    render(<OnboardingPreferencesProvider><Festivals /></OnboardingPreferencesProvider>);
    const today = localDateKey(new Date());
    await waitFor(() => expect(getFestivals).toHaveBeenCalledWith(today, addDays(today, 30), expect.anything()), { timeout: 10000 });
    expect(screen.queryByPlaceholderText('YYYY-MM-DD')).toBeNull();
    fireEvent.press(screen.getByText('이번 주말'));
    const w = weekendRange(new Date());
    await waitFor(() => expect(getFestivals).toHaveBeenLastCalledWith(w.from, w.to, expect.anything()));
  });
});
