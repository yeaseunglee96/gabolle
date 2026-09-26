// 「내 여행」 빈 칸 — 지난 여행만 있으면 「아직 만든 여행이 없어요」라고 하지 않는다 (S15P21E201-1770).
import { render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { MyTripCard } from '@/home/HomeBlocks';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn() }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token' }) }));

const wrapper = ({ children }: { children: React.ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;

it('🔴 여행이 있는데 다가오는 것이 없으면 「다가오는 여행이 없어요」', () => {
  render(<MyTripCard trip={null} signedIn loaded hasTrips />, { wrapper });
  expect(screen.getByText('다가오는 여행이 없어요')).toBeTruthy();
  expect(screen.queryByText('아직 만든 여행이 없어요')).toBeNull();
});

it('여행이 하나도 없으면 지금처럼', () => {
  render(<MyTripCard trip={null} signedIn loaded />, { wrapper });
  expect(screen.getByText('아직 만든 여행이 없어요')).toBeTruthy();
});
