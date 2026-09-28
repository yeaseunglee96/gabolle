// 「내 일정에 추가」 후보 여행 — 끝난 여행은 빼고 진행 중 여행은 넣는다 (S15P21E201-1786).
import { render, screen, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn() }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token' }) }));

function dateKey(offsetDays: number) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function trip(tripId: string, title: string, status: string, start: string, end: string) {
  return { tripId, title, startDate: start, endDate: end, dayCount: 1, partySize: 1, status, role: 'OWNER', createdAt: '', updatedAt: '' };
}

const mockTrips = jest.fn();
jest.mock('@/trip/trips', () => ({
  ...jest.requireActual('@/trip/trips'),
  loadTrips: () => mockTrips(),
}));

import { AddPlaceToItineraryModal } from '../AddPlaceToItineraryModal';

const wrapper = ({ children }: { children: React.ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;

it('🔴 서버가 아직 READY 라고 해도 날짜가 지난 여행은 후보에 없다', async () => {
  mockTrips.mockResolvedValue({ state: 'success', trips: [
    trip('past', '어제 끝난 여행', 'READY', dateKey(-2), dateKey(-1)),
    trip('soon', '다음 주 여행', 'READY', dateKey(7), dateKey(8)),
    trip('now', '지금 여행 중', 'IN_PROGRESS', dateKey(0), dateKey(1)),
    trip('plan', '일정 없는 여행', 'PLANNING', dateKey(7), dateKey(8)),
  ] });
  render(<AddPlaceToItineraryModal visible placeId="p1" onClose={jest.fn()} />, { wrapper });

  await waitFor(() => expect(screen.getByText('다음 주 여행')).toBeTruthy());
  expect(screen.getByText('지금 여행 중')).toBeTruthy();
  expect(screen.queryByText('어제 끝난 여행')).toBeNull();
  expect(screen.queryByText('일정 없는 여행')).toBeNull();
});
