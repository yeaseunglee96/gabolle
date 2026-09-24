// 내 여행 탭 새 시안 — S15P21E201-1587 (frontend/docs/design_handoff_my_trips).
//
// 🔴 이 시험이 지키는 것은 셋이다.
//    ① 부슐랭 단추가 없다 — 헤더의 행동은 「새 여행」 하나다.
//    ② 넓은 화면은 3열 격자이고, 사진 없는 카드에도 빈 판(높이 160)을 둔다 — 같은 줄 제목 높이를 맞추려고(사용자 결정).
//    ③ 폰은 사진 없는 카드에 자리를 만들지 않는다 — 빈 회색 판은 「못 불러왔다」로 읽힌다.
//    배치가 «보기에 맞는가»는 못 잡는다 — 그건 시안과 나란히 찍은 화면으로 본다.
import type { ReactNode } from 'react';
import { StyleSheet } from 'react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react-native';
import { SafeAreaProvider, initialWindowMetrics } from 'react-native-safe-area-context';

import { color } from '@/design/tokens';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import type { TripSummaryDto } from '@/trip/trips';

let mockDesktop = false;
const mockLoadTrips = jest.fn();
jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => false }),
  useLocalSearchParams: () => ({}),
  usePathname: () => '/trips',
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', user: { userId: 'me' } }) }));
jest.mock('@/layout/useLayout', () => ({
  useLayout: () => ({ desktop: mockDesktop, kind: mockDesktop ? 'tablet' : 'phone', width: mockDesktop ? 1440 : 390, height: 900, isLandscape: false }),
}));
jest.mock('@/trip/trips', () => ({ ...jest.requireActual('@/trip/trips'), loadTrips: (...args: unknown[]) => mockLoadTrips(...args) }));
// 탭바는 이 시험의 대상이 아니다 — 높이 계산에 쓰는 값은 그대로 두고 그림만 뺀다.
jest.mock('@/components/TabBar', () => ({ ...jest.requireActual('@/components/TabBar'), TabBar: () => null }));

import Trips from '../../../app/(tabs)/trips';

const trip = (tripId: string, coverImageUrl: string | null, title: string | null): TripSummaryDto => ({
  tripId, title, startDate: '2026-10-10', endDate: '2026-10-11', dayCount: 2, partySize: 2, status: 'READY', role: 'OWNER',
  createdAt: '2026-09-20T00:00:00Z', updatedAt: '2026-09-20T00:00:00Z', coverImageUrl, firstStopNameKo: null, firstStopNameEn: null,
});
const TRIPS = [trip('t1', 'https://example.test/haeundae.png', '부산 가족여행'), trip('t2', null, '광안리 야경 투어'), trip('t3', null, null)];

function Providers({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } });
  return (
    <SafeAreaProvider initialMetrics={initialWindowMetrics ?? { frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 47, left: 0, right: 0, bottom: 34 } }}>
      <QueryClientProvider client={client}><OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider></QueryClientProvider>
    </SafeAreaProvider>
  );
}

/** 사진 없는 카드의 빈 판 — 연한 판(surface.tint)이고 높이 160 이다. */
const blankCovers = () => screen.UNSAFE_root.findAll((node) => {
  if (typeof node.type !== 'string') return false;
  const flat = StyleSheet.flatten(node.props.style) as { height?: number; backgroundColor?: string } | undefined;
  return flat?.height === 160 && flat.backgroundColor === color.surface.tint;
});
const gridSlots = () => screen.UNSAFE_root.findAll((node) => typeof node.type === 'string' && (StyleSheet.flatten(node.props.style) as { width?: string } | undefined)?.width === '33.3333%');
const WAIT = { timeout: 5000 };

beforeEach(() => { mockDesktop = false; mockLoadTrips.mockResolvedValue({ state: 'success', trips: TRIPS }); });

describe('내 여행 탭 — 새 시안', () => {
  it('🔴 부슐랭 단추가 없다 — 폰과 넓은 화면 모두', async () => {
    for (const desktop of [false, true]) {
      mockDesktop = desktop;
      const view = render(<Trips />, { wrapper: Providers });
      expect(await screen.findByText('부산 가족여행', {}, WAIT)).toBeTruthy();
      expect(screen.queryByText('부슐랭')).toBeNull();
      expect(screen.getByText('새 여행')).toBeTruthy();
      view.unmount();
    }
    // 첫 시험은 화면을 처음 불러오는 값(수 초)을 치르고 두 번 그린다 — 기본 5초로는 모자란다.
  }, 20000);

  it('🔴 넓은 화면은 3열 격자 — 사진 없는 카드 둘에 빈 판이 선다', async () => {
    mockDesktop = true;
    render(<Trips />, { wrapper: Providers });
    expect(await screen.findByText('광안리 야경 투어', {}, WAIT)).toBeTruthy();

    expect(gridSlots()).toHaveLength(TRIPS.length);
    expect(blankCovers()).toHaveLength(2);
  });

  it('🔴 폰은 사진 없는 카드에 자리를 만들지 않는다 — 격자도 아니다', async () => {
    render(<Trips />, { wrapper: Providers });
    expect(await screen.findByText('광안리 야경 투어', {}, WAIT)).toBeTruthy();

    expect(blankCovers()).toHaveLength(0);
    expect(gridSlots()).toHaveLength(0);
  });
});
