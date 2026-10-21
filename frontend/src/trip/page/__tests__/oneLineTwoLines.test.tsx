// 장소 한 줄 소개를 폰 일정 카드에서 두 줄까지 — S15P21E201-2029 후속.
//
// 🔴 이 시험이 지키는 것: 폰(390) 카드의 글자 칸이 좁아 한 줄이면 「한옥 감성 인테리어와 …」처럼 핵심이 잘린다.
//    그래서 두 줄까지 보이고, 두 줄을 넘으면 그 뒤를 줄임표로 자른다(numberOfLines=2).
import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react-native';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { TripPageMobile } from '@/trip/page/TripPageMobile';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => true }) }));
jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', desktop: false, width: 390, height: 844, isLandscape: false }) }));
jest.mock('@/map/RouteMap', () => ({ RouteMap: () => null }));
jest.mock('@/trip/TripNameSheet', () => ({ TripNameSheet: () => null }));
jest.mock('@/components/Skeleton', () => ({ Skeleton: () => null }));
jest.mock('@/trip/page/useTripProgress', () => ({
  useTripProgress: () => ({ progress: { status: 'PLANNED', currentStopIndex: 0, outcomes: {} }, deviceOnly: false, error: null, start: jest.fn(), pause: jest.fn(), arrive: jest.fn(), skip: jest.fn() }),
}));
jest.mock('@/trip/page/useLiveLocation', () => ({ useLiveLocation: () => ({ state: 'off', fix: null }) }));
jest.mock('@/trip/page/useTripPage', () => {
  // 옛 일정처럼 — 출발지·거리가 붙어 있고 설문 이유도 있는 곳 하나, 이유가 없는 곳 하나(옛 서버: 칸 없음)
  const withIntro = { id: 'i1', startsAt: '2030-10-03T10:00:00', title: '카페오뜨', locked: false, placeId: 'p1', oneLine: { ko: '한옥 감성 인테리어와 마당 정원이 있는 조용한 카페' } };
  const items = [withIntro];
  const loaded = { id: 'it-1', tripId: 'trip-1', title: '광안리 여행', version: 1, days: [{ date: '2030-10-03', items }] };
  const course = { id: 'it-1', title: '', tagline: '', days: [], summary: { places: 2, moveMin: null, walkKm: null, costKrw: null }, status: 'ESTIMATED', rationale: null, itineraryId: 'it-1', preview: null };
  const noop = () => {};
  const value = {
    page: { state: 'ready', tripId: 'trip-1', courses: [course], full: false, confirmedCourseId: 'it-1', coursesLoading: false }, load: noop,
    courses: [course], course, courseIndex: 0, setCourseIndex: noop, confirmed: true, setConfirmed: noop, tripId: 'trip-1',
    itinerary: { id: 'it-1', value: loaded, message: null }, setItinerary: noop, loaded, reloadItinerary: noop,
    dayIndex: 0, setDayIndex: noop, items, selectedId: 'i1', setSelectedId: noop, photos: {}, pace: null, reloadPace: noop,
    map: { stops: [], days: [] }, routes: [], points: [], anyEstimatedLine: false, allItems: items, travelTotal: 0, budget: null, atRisk: [], allEstimated: false,
    title: '광안리 여행', headSub: '10월 3일', confirm: noop, confirming: false,
  };
  return { useTripPage: () => value };
});

const wrapper = ({ children }: { children: ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;
it('🔴 폰 일정 카드: 한 줄 소개는 두 줄까지, 넘치면 줄임표', () => {
  render(<TripPageMobile source={{ kind: 'itinerary', itineraryId: 'it-1' }} />, { wrapper });
  expect(screen.getByText('한옥 감성 인테리어와 마당 정원이 있는 조용한 카페').props.numberOfLines).toBe(2);
});
