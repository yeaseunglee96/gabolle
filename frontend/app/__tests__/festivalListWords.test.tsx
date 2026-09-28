// 축제 목록 카드 — S15P21E201-1679.
//
// 🔴 이 시험이 지키는 것:
//    ① 날짜가 「2026-09-26 — 2026-09-27」처럼 받은 글자 그대로였다 — 내 여행 목록과 같은 「9월 26일 (토) – 9월 27일 (일)」.
//    ② 사진 없는 축제는 회색 판에 「GABOLLE」 글자만 있었다 — 둘러보기처럼 📍 그림(조율 세션 결정).
import { render, screen, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import type { Festival } from '@/discovery/festivals';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => true }), usePathname: () => '/festivals' }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 0, left: 0, right: 0, bottom: 0 }),
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token' }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', desktop: false, width: 390, height: 844, isLandscape: false }) }));
jest.mock('@/discovery/festivals', () => ({ ...jest.requireActual('@/discovery/festivals'), getFestivals: jest.fn() }));
const { getFestivals } = jest.requireMock('@/discovery/festivals') as { getFestivals: jest.Mock };

import Festivals from '../festivals';

jest.setTimeout(20000);

const ROCK: Festival = {
  placeId: 'f1', nameKo: '2026 부산국제록페스티벌', address: '부산광역시 사상구 삼락동 29-46',
  startDate: '2026-09-26', endDate: '2026-09-27', overlapDates: ['2026-09-26', '2026-09-27'], photoUrl: null,
};

beforeEach(() => getFestivals.mockResolvedValue([ROCK]));

async function open() {
  render(<OnboardingPreferencesProvider><Festivals /></OnboardingPreferencesProvider>);
  // 첫 화면은 부품을 처음 불러오느라 몇 초 걸린다 — 기다림보다 시험 제한 시간을 길게 둔다(S15P21E201-1665 규칙).
  await waitFor(() => expect(screen.getByText('2026 부산국제록페스티벌')).toBeTruthy(), { timeout: 10000 });
}

describe('축제 목록 카드', () => {
  it('🔴 날짜는 「9월 26일 (토) – 9월 27일 (일)」 모양이다', async () => {
    await open();
    expect(screen.getByText('9월 26일 (토) – 9월 27일 (일)')).toBeTruthy();
    expect(screen.queryByText(/2026-09-26/)).toBeNull();
  });

  it('🔴 사진이 없으면 둘러보기처럼 📍 그림 — 「GABOLLE」 글자 판이 아니다', async () => {
    await open();
    expect(screen.getByText('📍')).toBeTruthy();
    expect(screen.queryByText('GABOLLE')).toBeNull();
  });
});
