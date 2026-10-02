// 여행 화면 도구 줄의 「여행 돈」(S15P21E201-1935) — 창이 아니라 여행 돈 화면으로 간다. 가짜는 tripSheet.test 와 같다.
import type { ReactNode } from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { TripPageMobile } from '@/trip/page/TripPageMobile';

const mapProps: Array<{ height?: number; bottomInset?: number; topInset?: number; refitKey?: string | number | null }> = [];
// 색 범례(S15P21E201-1896) — 칠한 선이 있을 때만 있다. 가짜 useTripPage 가 이 값을 그대로 돌려준다.
let mockRouteLegend: { grading: { slope: boolean; shade: boolean }; hasShade: boolean } | null = null;

const mockPush = jest.fn();
jest.mock('expo-router', () => ({ useRouter: () => ({ push: mockPush, replace: jest.fn(), back: jest.fn(), canGoBack: () => true }) }));
jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', desktop: false, width: 390, height: 844, isLandscape: false }) }));
jest.mock('@/map/RouteMap', () => ({ RouteMap: (props: { height?: number; bottomInset?: number }) => { mapProps.push(props); return null; } }));
jest.mock('@/trip/TripInvitePanel', () => ({ TripInvitePanel: () => { const { Text: T } = jest.requireActual('react-native'); return <T>invite-panel</T>; } }));
jest.mock('@/trip/TripReadLinkPanel', () => ({ TripReadLinkPanel: () => { const { Text: T } = jest.requireActual('react-native'); return <T>share-panel</T>; } }));
jest.mock('@/trip/TripWeatherPanel', () => ({ TripWeatherPanel: () => { const { Text: T } = jest.requireActual('react-native'); return <T>weather-panel</T>; } }));
jest.mock('@/social/StoryComposeForm', () => ({ StoryComposeForm: () => { const { Text: T } = jest.requireActual('react-native'); return <T>record-panel</T>; } }));
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
  return { useTripPage: () => ({ ...value, routeLegend: mockRouteLegend }) };
});

const wrapper = ({ children }: { children: ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;
const mount = () => render(<TripPageMobile source={{ kind: 'itinerary', itineraryId: 'it-1' }} />, { wrapper });

beforeEach(() => { mapProps.length = 0; mockRouteLegend = null; mockPush.mockClear(); jest.useFakeTimers(); });
// 창의 움직임을 끝까지 돌리고 정리한다 — 안 그러면 시험이 끝난 뒤에도 움직임이 돌아 경고가 쏟아진다.
afterEach(() => { act(() => { jest.runOnlyPendingTimers(); }); jest.useRealTimers(); });

describe('여행 돈 입구', () => {
  it('도구 줄의 「여행 돈」은 그 여행의 여행 돈 화면을 연다', () => {
    mount();
    fireEvent.press(screen.getByLabelText('여행 돈'));
    expect(mockPush).toHaveBeenCalledWith('/trip-1/money');
  });
});
