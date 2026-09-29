// 여행 결과 화면 — 확정 줄과 위험 띠 (S15P21E201-1670).
//
// 🔴 이 시험이 지키는 것(사용자 결정):
//    ① 「코스 A로 확정」 줄은 처음부터 보인다. 전에는 코스 알약을 한 번 눌러야 펼쳐져서(시안 Interactions),
//       처음 온 사람은 확정 단추가 있는 줄 몰랐다. 첫 코스가 골라진 채로 시작한다.
//    ② 하루 안에 끝나는지 모를 때 「무엇을」 모르는지 말한다 — 「아직 확인 못 했어요」만으로는 알 수 없었다.
//       넓은 화면은 모를 때도 초록(괜찮다는 색)이었다 — 모르면 흐린 색이다.
import type { ReactNode } from 'react';
import { StyleSheet } from 'react-native';
import { render, screen } from '@testing-library/react-native';

import { color } from '@/design/tokens';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { TripPageDesktop } from '@/trip/page/TripPageDesktop';
import { TripPageMobile } from '@/trip/page/TripPageMobile';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => true }) }));
jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', desktop: false, width: 390, height: 844, isLandscape: false }) }));
jest.mock('@/map/RouteMap', () => ({ RouteMap: () => null }));
jest.mock('@/map/MobilityLayerToggle', () => ({ MobilityLayerToggle: () => null }));
jest.mock('@/map/mobilityLayers', () => ({ ...jest.requireActual('@/map/mobilityLayers'), useMobilityLayer: () => ({ lines: [], basis: null }) }));
jest.mock('@/trip/TripInvitePanel', () => ({ TripInvitePanel: () => null }));
jest.mock('@/trip/TripReadLinkPanel', () => ({ TripReadLinkPanel: () => null }));
jest.mock('@/trip/TripWeatherPanel', () => ({ TripWeatherPanel: () => null }));
jest.mock('@/trip/TripNameSheet', () => ({ TripNameSheet: () => null }));
// 뼈대 부품은 reanimated 를 부르는데, 시험 환경에서는 그 꾸러미가 안 불린다(tripSheet.test 와 같은 사정).
jest.mock('@/components/Skeleton', () => ({ Skeleton: () => null }));
jest.mock('@/trip/page/useTripProgress', () => ({
  useTripProgress: () => ({ progress: { status: 'PLANNED', currentStopIndex: 0, outcomes: {} }, deviceOnly: false, error: null, start: jest.fn(), pause: jest.fn(), arrive: jest.fn(), skip: jest.fn() }),
}));
jest.mock('@/trip/page/useLiveLocation', () => ({ useLiveLocation: () => ({ state: 'off', fix: null }) }));
jest.mock('@/trip/page/useTripPage', () => {
  const item = { id: 'i1', startsAt: '2030-10-03T10:00:00', title: '카페오뜨', locked: false, placeId: 'p1', lat: 35.15, lng: 129.11 };
  const loaded = { id: 'it-1', tripId: 'trip-1', title: '광안리 여행', version: 1, days: [{ date: '2030-10-03', items: [item] }] };
  const course = (id: string) => ({ id, title: '', tagline: '', days: [], summary: { places: 1, moveMin: null, walkKm: null, costKrw: null }, status: 'ESTIMATED', rationale: null, itineraryId: id, preview: null });
  const courses = [course('it-1'), course('it-2'), course('it-3')];
  const page = { state: 'ready', tripId: 'trip-1', courses, full: true, confirmedCourseId: null, coursesLoading: false };
  const noop = () => {};
  const value = {
    // 추천에서 막 넘어온 화면 — 아직 확정 전, 첫 코스가 골라져 있다. 하루 안에 끝나는지(pace)는 아직 모른다.
    page, load: noop, courses, course: courses[0], courseIndex: 0, setCourseIndex: noop, confirmed: false, setConfirmed: noop, tripId: 'trip-1',
    itinerary: { id: 'it-1', value: loaded, message: null }, setItinerary: noop, loaded, reloadItinerary: noop,
    dayIndex: 0, setDayIndex: noop, items: [item], selectedId: 'i1', setSelectedId: noop, photos: {}, pace: null, reloadPace: noop,
    map: { stops: [{ id: 'i1', latitude: 35.15, longitude: 129.11, label: '1', title: '카페오뜨' }], days: [] }, routes: [], points: [],
    anyEstimatedLine: false, allItems: [item], travelTotal: 0, budget: null, atRisk: [], allEstimated: false,
    title: '광안리 여행', headSub: '10월 3일', confirm: noop, confirming: false,
  };
  return { useTripPage: () => value };
});

const wrapper = ({ children }: { children: ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;
const source = { kind: 'trip' as const, tripId: 'trip-1', jobId: null };

type Node = { parent?: Node | null; props?: { style?: unknown } };
/** 이 글자를 덮는 가장 가까운 투명도 — 트리에 있어도 투명도가 0 이면 눈에 안 보인다. */
function opacityOf(node: unknown): number | undefined {
  for (let current = node as Node | null; current; current = current.parent ?? null) {
    const flat = StyleSheet.flatten(current.props?.style as never) as { opacity?: number } | undefined;
    if (typeof flat?.opacity === 'number') return flat.opacity;
  }
  return undefined;
}
function colorOf(node: unknown): string | undefined {
  return (StyleSheet.flatten((node as Node).props?.style as never) as { color?: string } | undefined)?.color;
}

describe('폰 — 결과 화면', () => {
  it('🔴 「코스 A로 확정」은 알약을 누르기 전에도 보인다', () => {
    render(<TripPageMobile source={source} />, { wrapper });
    expect(opacityOf(screen.getByText('코스 A로 확정'))).toBe(1);
  });

  // 모를 때 띠를 아예 안 그린다(UI 캔버스 ④) — 할 일이 없는 문장이라 일정 맨 위를 차지할 이유가 없다.
  //   그래도 🔴 「괜찮다」고 말해서는 안 된다(S15P21E201-1670).
  it('🔴 하루 안에 끝나는지 모르면 괜찮다고 말하지 않는다 — 띠를 안 그린다', () => {
    render(<TripPageMobile source={source} />, { wrapper });
    expect(screen.queryByText('하루 안에 여유 있게 끝나요')).toBeNull();
    expect(screen.queryByText('하루 안에 끝나는지 아직 몰라요')).toBeNull();
    expect(screen.queryByText('아직 확인 못 했어요')).toBeNull();
  });
});

describe('넓은 화면 — 「확인할 것」 칸', () => {
  it('🔴 모르면 언제 알게 되는지 적고, 초록이 아니라 흐린 색이다', () => {
    render(<TripPageDesktop source={source} />, { wrapper });
    const unknown = screen.getByText('여행 당일 이동 기록이 쌓이면 알려 드려요');
    expect(colorOf(unknown)).not.toBe(color.state.success);
    expect(screen.queryByText('아직 확인 못 했어요')).toBeNull();
  });
});
