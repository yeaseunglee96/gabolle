// 폰 여행 화면 — 여행·일정을 못 불러올 때 — S15P21E201-1660.
//
// 🔴 이 시험이 지키는 것(사용자 결정 (가)): 못 불러왔으면 빈 지도 자리와 창 대신 화면 가운데에 오류와 큰 「다시 시도」를
//    그린다. 전에는 위 4분의 3 이 빈 회색 지도 자리이고, 아래 창 안에 작은 오류 카드·작은 알약만 있었다.
//    여행 자체(page)를 못 불러올 때와 일정(itinerary)만 못 불러올 때 둘 다 같다.
import type { ReactNode } from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { TripPageMobile } from '@/trip/page/TripPageMobile';

const mockMap = jest.fn();
const mockLoad = jest.fn();
const mockBack = jest.fn();
let mockState: 'page-error' | 'itinerary-error' = 'page-error';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: mockBack, canGoBack: () => true }) }));
jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', desktop: false, width: 390, height: 844, isLandscape: false }) }));
jest.mock('@/map/RouteMap', () => ({ RouteMap: () => { mockMap(); return null; } }));
jest.mock('@/trip/TripNameSheet', () => ({ TripNameSheet: () => null }));
jest.mock('@/components/Skeleton', () => ({ Skeleton: () => null }));
jest.mock('@/trip/page/useTripProgress', () => ({
  useTripProgress: () => ({ progress: { status: 'PLANNED', currentStopIndex: 0, outcomes: {} }, deviceOnly: false, error: null, start: jest.fn(), pause: jest.fn(), arrive: jest.fn(), skip: jest.fn() }),
}));
jest.mock('@/trip/page/useLiveLocation', () => ({ useLiveLocation: () => ({ state: 'off', fix: null }) }));
jest.mock('@/trip/page/useTripPage', () => ({
  useTripPage: () => {
    const noop = () => {};
    const course = { id: 'it-1', title: '', tagline: '', days: [], summary: { places: 0, moveMin: null, walkKm: null, costKrw: null }, status: 'ESTIMATED', rationale: null, itineraryId: 'it-1', preview: null };
    const pageError = mockState === 'page-error';
    return {
      page: pageError ? { state: 'error', message: '여행을 찾을 수 없어요.' } : { state: 'ready', tripId: 'trip-1', courses: [course], full: false, confirmedCourseId: 'it-1', coursesLoading: false },
      load: mockLoad, courses: pageError ? [] : [course], course: pageError ? null : course, courseIndex: 0, setCourseIndex: noop, confirmed: true, setConfirmed: noop, tripId: 'trip-1',
      itinerary: pageError ? null : { id: 'it-1', value: null, message: '일정을 불러오지 못했어요.' }, setItinerary: noop, loaded: null, reloadItinerary: noop,
      dayIndex: 0, setDayIndex: noop, items: [], selectedId: null, setSelectedId: noop, photos: {}, pace: null, reloadPace: noop,
      map: { stops: [], days: [] }, routes: [], points: [], anyEstimatedLine: false, allItems: [], travelTotal: 0, budget: null, atRisk: [], allEstimated: false,
      title: '여행', headSub: null, confirm: noop, confirming: false,
    };
  },
}));

const wrapper = ({ children }: { children: ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;
const mount = () => render(<TripPageMobile source={{ kind: 'trip', tripId: 'trip-1' }} />, { wrapper });

beforeEach(() => { mockMap.mockClear(); mockLoad.mockClear(); mockBack.mockClear(); });

describe.each([['여행 자체를 못 불러올 때', 'page-error', '여행을 찾을 수 없어요.'], ['일정만 못 불러올 때', 'itinerary-error', '일정을 불러오지 못했어요.']] as const)(
  '폰 여행 화면 — %s', (_label, state, message) => {
    beforeEach(() => { mockState = state; });

    it('🔴 빈 지도 자리·창 대신 가운데에 오류와 큰 「다시 시도」 — 누르면 다시 불러온다', () => {
      mount();
      expect(screen.getByText('여행을 불러오지 못했어요')).toBeTruthy();
      expect(screen.getByText(message)).toBeTruthy();
      expect(mockMap).not.toHaveBeenCalled();
      // 지도 뒤에 깔던 접힌 탭 줄과 밀어 올리는 창을 아예 안 그린다(숨김 처리된 것까지 찾는다)
      expect(screen.queryByLabelText('일정 펼치기', { includeHiddenElements: true })).toBeNull();
      expect(screen.queryByText('홈', { includeHiddenElements: true })).toBeNull();
      fireEvent.press(screen.getByRole('button', { name: '다시 시도' }));
      expect(mockLoad).toHaveBeenCalledTimes(1);
    });

    it('왼쪽 위 「뒤로」로 나갈 수 있다', () => {
      mount();
      fireEvent.press(screen.getByLabelText('뒤로'));
      expect(mockBack).toHaveBeenCalled();
    });
  },
);
