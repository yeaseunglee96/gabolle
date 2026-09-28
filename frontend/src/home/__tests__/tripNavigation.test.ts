import { resolveTripItinerary, type TripItineraryChoice } from '@/trip/trips';
import { resolveHomeTripDestination } from '../tripNavigation';

// 무엇을 열지는 resolveTripItinerary 가 정한다(그 시험: src/trip/__tests__/resolveTripItinerary.test.ts).
// 여기서는 그 답을 주소로 옮기는 것만 본다.
jest.mock('@/trip/trips', () => ({ ...jest.requireActual('@/trip/trips'), resolveTripItinerary: jest.fn() }));

const mockedResolve = jest.mocked(resolveTripItinerary);

beforeEach(() => mockedResolve.mockReset());

describe('resolveHomeTripDestination', () => {
  // 🔴 S15P21E201-1605 — 일정이 여럿이어도 고르기로 보내지 않는다. 내 여행 카드와 같은 규칙으로 고른 일정을 연다.
  it('opens the itinerary the trip rule picked — same rule as the my-trips card', async () => {
    mockedResolve.mockResolvedValue({ state: 'open', itineraryId: 'it-b' });
    await expect(resolveHomeTripDestination({ tripId: 'trip-3', currentItineraryId: 'it-b' }, 'token')).resolves.toBe('/trips/it-b/itinerary');
    expect(mockedResolve).toHaveBeenCalledWith({ tripId: 'trip-3', currentItineraryId: 'it-b' }, 'token');
  });

  it.each<TripItineraryChoice>([
    { state: 'none' },
    { state: 'offline', message: 'offline' },
  ])('falls back to my trips when there is nothing to open', async (result) => {
    mockedResolve.mockResolvedValue(result);
    await expect(resolveHomeTripDestination({ tripId: 'trip-3' }, 'token')).resolves.toBe('/trips');
  });
});
