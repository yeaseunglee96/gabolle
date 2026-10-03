// 사진을 못 불러오면 빈 회색 판을 남기지 않는다 (S15P21E201-1940).
//
// 🔴 실제로 있었던 일: 「지금 여행 중」 카드의 커버가 폴드6 에서 회색 빈칸이었다. 사진 주소
//    (www.visitbusan.net)가 TLS 인증서 중간 고리(중간 CA — 브라우저는 알아서 찾아오지만 안드로이드는
//    서버가 보내 줘야 하는 인증서)를 빠뜨려서 안드로이드에서만 못 받는다. 웹에서는 보였다.
//    Image 의 배경색(surface.soft)만 남아 「못 불러왔다」로 읽혔다 — 사진이 없을 때와 같게 그린다.
import type { ReactNode } from 'react';
// 사진은 공용 부품 AppImage(expo-image)로 그린다(S15P21E201-1975) — 그 부품을 찾는다.
import { AppImage as Image } from '@/components/AppImage';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen } from '@testing-library/react-native';
import { SafeAreaProvider, initialWindowMetrics } from 'react-native-safe-area-context';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import type { TripSummaryDto } from '@/trip/trips';

const mockLoadTrips = jest.fn();
jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => false }),
  useLocalSearchParams: () => ({}),
  usePathname: () => '/trips',
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', user: { userId: 'me' }, ready: true }) }));
jest.mock('@/layout/useLayout', () => ({
  useLayout: () => ({ desktop: false, kind: 'phone', width: 369, height: 905, isLandscape: false }),
}));
jest.mock('@/trip/trips', () => ({ ...jest.requireActual('@/trip/trips'), loadTrips: (...args: unknown[]) => mockLoadTrips(...args) }));
jest.mock('@/components/TabBar', () => ({ ...jest.requireActual('@/components/TabBar'), TabBar: () => null }));

import Trips from '../../../app/(tabs)/trips';

const iso = (offsetDays: number) => {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
const trip = (tripId: string, start: number, end: number, coverImageUrl: string, title: string): TripSummaryDto => ({
  tripId, title, startDate: iso(start), endDate: iso(end), dayCount: end - start + 1, partySize: 2, status: 'READY', role: 'OWNER',
  createdAt: '2026-09-20T00:00:00Z', updatedAt: '2026-09-20T00:00:00Z', coverImageUrl, firstStopNameKo: null, firstStopNameEn: null,
});
const NOW = 'https://www.visitbusan.net/now-cover';
const LATER = 'https://www.visitbusan.net/later-cover';

function Providers({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } });
  return (
    <SafeAreaProvider initialMetrics={initialWindowMetrics ?? { frame: { x: 0, y: 0, width: 369, height: 905 }, insets: { top: 47, left: 0, right: 0, bottom: 34 } }}>
      <QueryClientProvider client={client}><OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider></QueryClientProvider>
    </SafeAreaProvider>
  );
}
const imageWith = (uri: string) => screen.UNSAFE_root.findAllByType(Image).filter((node) => (node.props.source as { uri?: string } | undefined)?.uri === uri);
jest.setTimeout(20000);

beforeEach(() => {
  mockLoadTrips.mockResolvedValue({ state: 'success', trips: [trip('now', -1, 1, NOW, '지금 여행'), trip('later', 9, 10, LATER, '다음 여행')] });
});

describe('커버 사진을 못 불러오면', () => {
  it('🔴 지금 여행 중 큰 카드 — 사진 자리를 지운다(폰은 사진 없는 카드와 같다)', async () => {
    render(<Trips />, { wrapper: Providers });
    expect(await screen.findByText('지금 여행', {}, { timeout: 5000 })).toBeTruthy();
    expect(imageWith(NOW)).toHaveLength(1);
    await act(async () => { imageWith(NOW)[0].props.onError?.({ nativeEvent: { error: 'ssl' } }); });
    await act(async () => { imageWith(NOW)[0].props.onError?.({ nativeEvent: { error: 'ssl' } }); });
    expect(imageWith(NOW)).toHaveLength(0);
  });

  it('🔴 작은 줄의 사진 — 빈 연한 판으로 바꾼다', async () => {
    render(<Trips />, { wrapper: Providers });
    expect(await screen.findByText('다음 여행', {}, { timeout: 5000 })).toBeTruthy();
    expect(imageWith(LATER)).toHaveLength(1);
    await act(async () => { imageWith(LATER)[0].props.onError?.({ nativeEvent: { error: 'ssl' } }); });
    await act(async () => { imageWith(LATER)[0].props.onError?.({ nativeEvent: { error: 'ssl' } }); });
    expect(imageWith(LATER)).toHaveLength(0);
  });
});

describe('커버 사진이 한 번 실패하면 (S15P21E201-1967)', () => {
  // 🔴 탭에서 돌리고 언어를 바꾼 뒤 멀쩡한 커버 둘이 회색 판으로 남았다 — 서버 사진은 그때도 200 이었다.
  //    한 번의 실패로 앱을 끌 때까지 그 주소를 안 부르면, 잠깐의 실패가 영영 남는다. 한 번은 다시 불러 본다.
  const ONCE = 'https://www.visitbusan.net/once-cover';
  it('🔴 한 번은 다시 불러 본다 — 사진 칸이 새로 만들어진다', async () => {
    mockLoadTrips.mockResolvedValue({ state: 'success', trips: [trip('once', -1, 1, ONCE, '한 번 실패')] });
    render(<Trips />, { wrapper: Providers });
    expect(await screen.findByText('한 번 실패', {}, { timeout: 5000 })).toBeTruthy();
    const first = imageWith(ONCE)[0];
    await act(async () => { first.props.onError?.({ nativeEvent: { error: 'timeout' } }); });
    const again = imageWith(ONCE);
    expect(again).toHaveLength(1);
    expect(again[0]).not.toBe(first);
  });
});
