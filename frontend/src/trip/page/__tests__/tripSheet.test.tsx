// 폰 여행 화면의 일정 창 — S15P21E201-1607.
//
// 🔴 이 시험이 지키는 것(사용자가 폰에서 짚은 셋):
//    ① 동행 초대·공유·날씨는 창 «안에서» 내용만 바뀐다 — 창 위에 판(모달)이 하나 더 올라와 두 겹이 되지 않는다.
//    ② 창을 여닫아도 지도 크기가 그대로다 — 전에는 창이 열리면 지도 칸을 줄여서 지도가 다시 가운데를 잡으며 튀었다.
//    ③ 창은 밀어 올린다(transform, 네이티브 드라이버). 올라갈 때와 내려갈 때 같은 시간·곡선이다.
import type { ReactNode } from 'react';
import { Animated, Modal, Text } from 'react-native';
import { act, fireEvent, render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { TripPageMobile } from '@/trip/page/TripPageMobile';

const mapProps: Array<{ height?: number; bottomInset?: number; topInset?: number; refitKey?: string | number | null }> = [];

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => true }) }));
jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', desktop: false, width: 390, height: 844, isLandscape: false }) }));
jest.mock('@/map/RouteMap', () => ({ RouteMap: (props: { height?: number; bottomInset?: number }) => { mapProps.push(props); return null; } }));
jest.mock('@/map/MobilityLayerToggle', () => ({ MobilityLayerToggle: () => null }));
jest.mock('@/map/mobilityLayers', () => ({ ...jest.requireActual('@/map/mobilityLayers'), useMobilityLayer: () => ({ lines: [], basis: null }) }));
jest.mock('@/trip/TripInvitePanel', () => ({ TripInvitePanel: () => { const { Text: T } = jest.requireActual('react-native'); return <T>invite-panel</T>; } }));
jest.mock('@/trip/TripReadLinkPanel', () => ({ TripReadLinkPanel: () => { const { Text: T } = jest.requireActual('react-native'); return <T>share-panel</T>; } }));
jest.mock('@/trip/TripWeatherPanel', () => ({ TripWeatherPanel: () => { const { Text: T } = jest.requireActual('react-native'); return <T>weather-panel</T>; } }));
jest.mock('@/trip/TripNameSheet', () => ({ TripNameSheet: () => null }));
// 뼈대 부품은 reanimated 를 부르는데, 시험 환경에서는 그 꾸러미가 안 불린다(signUpConsentBlockers.test 와 같은 사정).
jest.mock('@/components/Skeleton', () => ({ Skeleton: () => null }));
jest.mock('@/trip/page/useTripProgress', () => ({
  useTripProgress: () => ({ progress: { status: 'PLANNED', currentStopIndex: 0, outcomes: {} }, deviceOnly: false, error: null, start: jest.fn(), pause: jest.fn(), arrive: jest.fn(), skip: jest.fn() }),
}));
jest.mock('@/trip/page/useLiveLocation', () => ({ useLiveLocation: () => ({ state: 'off', fix: null }) }));
jest.mock('@/trip/page/useTripPage', () => {
  const item = { id: 'i1', startsAt: '2030-10-03T10:00:00', title: '카페오뜨', locked: false, placeId: 'p1', lat: 35.15, lng: 129.11 };
  const loaded = { id: 'it-1', tripId: 'trip-1', title: '광안리 여행', version: 1, days: [{ date: '2030-10-03', items: [item] }] };
  const course = { id: 'it-1', title: '', tagline: '', days: [], summary: { places: 1, moveMin: null, walkKm: null, costKrw: null }, status: 'ESTIMATED', rationale: null, itineraryId: 'it-1', preview: null };
  const page = { state: 'ready', tripId: 'trip-1', courses: [course], full: false, confirmedCourseId: 'it-1', coursesLoading: false };
  const noop = () => {};
  const value = {
    page, load: noop, courses: [course], course, courseIndex: 0, setCourseIndex: noop, confirmed: true, setConfirmed: noop, tripId: 'trip-1',
    itinerary: { id: 'it-1', value: loaded, message: null }, setItinerary: noop, loaded, reloadItinerary: noop,
    dayIndex: 0, setDayIndex: noop, items: [item], selectedId: 'i1', setSelectedId: noop, photos: {}, pace: null, reloadPace: noop,
    map: { stops: [{ id: 'i1', latitude: 35.15, longitude: 129.11, label: '1', title: '카페오뜨' }], days: [] }, routes: [], points: [],
    anyEstimatedLine: false, allItems: [item], travelTotal: 0, budget: null, atRisk: [], allEstimated: false,
    title: '광안리 여행', headSub: '10월 3일', confirm: noop, confirming: false,
  };
  return { useTripPage: () => value };
});

const wrapper = ({ children }: { children: ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;
const mount = () => render(<TripPageMobile source={{ kind: 'itinerary', itineraryId: 'it-1' }} />, { wrapper });
const openModals = () => screen.UNSAFE_queryAllByType(Modal).filter((modal) => modal.props.visible);

beforeEach(() => { mapProps.length = 0; jest.useFakeTimers(); });
// 창의 움직임을 끝까지 돌리고 정리한다 — 안 그러면 시험이 끝난 뒤에도 움직임이 돌아 경고가 쏟아진다.
afterEach(() => { act(() => { jest.runOnlyPendingTimers(); }); jest.useRealTimers(); });

describe('폰 여행 화면의 일정 창', () => {
  it.each([['동행 초대', 'invite-panel'], ['공유', 'share-panel'], ['날씨', 'weather-panel']])(
    '🔴 「%s」는 창 안에서 내용만 바뀐다 — 판이 하나 더 올라오지 않는다', (pill, panel) => {
      mount();
      fireEvent.press(screen.getByText(pill));
      expect(screen.getByText(panel)).toBeTruthy();
      expect(openModals()).toHaveLength(0);
      // 창 안의 「뒤로」로 일정으로 돌아온다
      fireEvent.press(screen.getByLabelText('일정으로 돌아가기'));
      expect(screen.queryByText(panel)).toBeNull();
      expect(screen.getAllByText('카페오뜨').length).toBeGreaterThan(0);
    },
  );

  it('🔴 창을 접고 펴도 지도 크기가 그대로다 — 지도가 다시 가운데를 잡으며 튀지 않는다', () => {
    mount();
    const opened = mapProps[mapProps.length - 1].height;
    const coveredOpen = mapProps[mapProps.length - 1].bottomInset ?? 0;
    fireEvent.press(screen.getByText('지도 보기'));
    expect(mapProps[mapProps.length - 1].height).toBe(opened);
    // 대신 창에 가린 높이를 지도에 알린다 — 열렸을 때가 접혔을 때보다 많이 가린다
    expect(coveredOpen).toBeGreaterThan(mapProps[mapProps.length - 1].bottomInset ?? 0);
    fireEvent.press(screen.getByLabelText('일정 펼치기'));
    expect(mapProps[mapProps.length - 1].height).toBe(opened);
  });

  it('🔴 «지도 보기»로 접을 때만 지도를 다시 맞춘다 — 창을 열 때는 안 맞춘다(S15P21E201-1754)', () => {
    mount();
    const last = () => mapProps[mapProps.length - 1];
    // 창이 열려 있을 때는 다시 맞추라는 신호가 없다
    expect(last().refitKey ?? null).toBeNull();
    // 위쪽 칩(요약 40 자리 + 경사/그늘 32) 만큼은 늘 가려져 있다고 알린다
    expect(last().topInset ?? 0).toBeGreaterThanOrEqual(72);
    fireEvent.press(screen.getByText('지도 보기'));
    expect(last().refitKey).not.toBeNull();
    fireEvent.press(screen.getByLabelText('일정 펼치기'));
    expect(last().refitKey ?? null).toBeNull();
  });

  it('🔴 창은 네이티브 드라이버로 밀어 올린다 — 올라갈 때와 내려갈 때 같은 시간·곡선', () => {
    const timing = jest.spyOn(Animated, 'timing');
    try {
      mount();
      timing.mockClear();
      fireEvent.press(screen.getByText('지도 보기'));
      const down = timing.mock.calls.map(([, config]) => config).find((config) => config.toValue === 0);
      fireEvent.press(screen.getByLabelText('일정 펼치기'));
      const up = timing.mock.calls.map(([, config]) => config).find((config) => config.toValue === 1);
      expect(down?.useNativeDriver).toBe(true);
      expect(up?.useNativeDriver).toBe(true);
      expect(up?.duration).toBe(down?.duration);
      expect(up?.easing).toBe(down?.easing);
    } finally {
      timing.mockRestore();
    }
  });
});

// 쓰지 않는 가져오기 경고를 막는다 — Text 는 가짜 판 안에서 requireActual 로 쓴다.
void Text;
