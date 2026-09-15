import { loadTripItineraries, type TripItinerariesResult } from '@/trip/trips';
import { resolveHomeTripDestination } from '../tripNavigation';

jest.mock('@/trip/trips', () => ({ loadTripItineraries: jest.fn() }));

const mockedLoad = jest.mocked(loadTripItineraries);

describe('resolveHomeTripDestination', () => {
  it('opens the itinerary id returned for a trip', async () => {
    mockedLoad.mockResolvedValue({ state: 'success', role: 'OWNER', itineraries: [{ itineraryId: 'itinerary-7', latestVersion: 1 }] });
    await expect(resolveHomeTripDestination('trip-3', 'token')).resolves.toBe('/trips/itinerary-7/itinerary');
    expect(mockedLoad).toHaveBeenCalledWith('trip-3', 'token');
  });

  it.each<TripItinerariesResult>([
    { state: 'success', role: 'OWNER', itineraries: [] },
    { state: 'success', role: 'OWNER', itineraries: [{ itineraryId: 'a', latestVersion: 1 }, { itineraryId: 'b', latestVersion: 2 }] },
    { state: 'offline', message: 'offline' },
  ])('falls back to the trip picker when a direct itinerary is not safe', async (result) => {
    mockedLoad.mockResolvedValue(result);
    await expect(resolveHomeTripDestination('trip-3', 'token')).resolves.toBe('/trips');
  });
});
