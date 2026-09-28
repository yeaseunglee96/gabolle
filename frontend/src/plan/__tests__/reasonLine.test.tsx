// 일정 장소 카드의 추천 이유 한 줄 — S15P21E201-1645.
//
// 🔴 이 시험이 지키는 것:
//    ① 여럿이면 정한 순서로 하나만 — 꼭 가고 싶다고 적은 곳·직접 넣은 곳 → 테마·취향 → 설문 → 그 장소만의 특징 →
//       출발지에서 가까움 → 인기(사용자 결정, 백엔드 제안 순서). 옛 일정은 거의 모든 곳에 「출발지·거리」가 저장돼 있어서
//       그 둘이 앞이면 모든 카드가 같은 말을 한다.
//    ② 이유가 없거나 보일 이유가 아니면(DIVERSITY_RERANKED · 모르는 코드) 줄을 그리지 않는다 — 빈 줄·자리표시 없이.
import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { pickReasonLine } from '@/plan/recommendations';
import { TripPageMobile } from '@/trip/page/TripPageMobile';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => true }) }));
jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', desktop: false, width: 390, height: 844, isLandscape: false }) }));
jest.mock('@/map/RouteMap', () => ({ RouteMap: () => null }));
jest.mock('@/map/MobilityLayerToggle', () => ({ MobilityLayerToggle: () => null }));
jest.mock('@/map/mobilityLayers', () => ({ ...jest.requireActual('@/map/mobilityLayers'), useMobilityLayer: () => ({ lines: [], basis: null }) }));
jest.mock('@/trip/TripNameSheet', () => ({ TripNameSheet: () => null }));
jest.mock('@/components/Skeleton', () => ({ Skeleton: () => null }));
jest.mock('@/trip/page/useTripProgress', () => ({
  useTripProgress: () => ({ progress: { status: 'PLANNED', currentStopIndex: 0, outcomes: {} }, deviceOnly: false, error: null, start: jest.fn(), pause: jest.fn(), arrive: jest.fn(), skip: jest.fn() }),
}));
jest.mock('@/trip/page/useLiveLocation', () => ({ useLiveLocation: () => ({ state: 'off', fix: null }) }));
jest.mock('@/trip/page/useTripPage', () => {
  // 옛 일정처럼 — 출발지·거리가 붙어 있고 설문 이유도 있는 곳 하나, 이유가 없는 곳 하나(옛 서버: 칸 없음)
  const withReasons = { id: 'i1', startsAt: '2030-10-03T10:00:00', title: '카페오뜨', locked: false, placeId: 'p1', reasonCodes: ['NEAR_ORIGIN', 'TOP_CONTRIBUTOR_distance', 'PREF_ALIGNED_QUIETNESS', 'DIVERSITY_RERANKED'] };
  const without = { id: 'i2', startsAt: '2030-10-03T12:00:00', title: '늘리', locked: false, placeId: 'p2' };
  const items = [withReasons, without];
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

describe('추천 이유 한 줄 고르기', () => {
  it('🔴 정한 순서 — 꼭·직접 → 테마 → 설문 → 그 장소만의 특징 → 출발지 → 인기', () => {
    expect(pickReasonLine(['POPULAR', 'NEAR_ORIGIN', 'MUST_VISIT_PLACE'])).toBe('내가 꼭 가고 싶다고 적은 곳');
    expect(pickReasonLine(['NEAR_ORIGIN', 'USER_ADDED'])).toBe('내가 직접 넣은 곳');
    expect(pickReasonLine(['TOP_CONTRIBUTOR_interest', 'PREF_ALIGNED_QUIETNESS', 'TAG_MATCH_INTEREST'])).toBe('관심 카테고리와 일치');
    expect(pickReasonLine(['NEAR_ORIGIN', 'TOP_CONTRIBUTOR_distance', 'PREF_ALIGNED_QUIETNESS'])).toBe('조용한 곳 취향과 맞음');
    expect(pickReasonLine(['NEAR_ORIGIN', 'TOP_CONTRIBUTOR_distance'])).toBe('다른 곳보다 거리가 돋보임');
    expect(pickReasonLine(['POPULAR', 'NEAR_ORIGIN'])).toBe('출발지에서 가까움');
    expect(pickReasonLine(['POPULAR'])).toBe('인기 있는 곳');
  });

  it('🔴 이유가 없거나 보일 이유가 아니면 null — 줄을 안 그린다', () => {
    expect(pickReasonLine(undefined)).toBeNull();
    expect(pickReasonLine([])).toBeNull();
    expect(pickReasonLine(['DIVERSITY_RERANKED'])).toBeNull();
    expect(pickReasonLine(['SOMETHING_NEW'])).toBeNull();
  });

  it('🔴 폰 일정 줄: 이유가 있는 곳에만 한 줄 — 옛 일정은 설문 이유가 먼저 보인다', () => {
    render(<TripPageMobile source={{ kind: 'itinerary', itineraryId: 'it-1' }} />, { wrapper });
    expect(screen.getAllByText('조용한 곳 취향과 맞음')).toHaveLength(1);
    expect(screen.queryByText('출발지에서 가까움')).toBeNull();
    expect(screen.queryByText('다른 곳보다 거리가 돋보임')).toBeNull();
    expect(screen.queryByText('추천 조건 반영')).toBeNull();
  });
});
