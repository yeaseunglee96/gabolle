// 여행 경비 — 예산을 따로 안 정했으면 여행 계획 예산을 처음 값으로 보여 준다(S15P21E201-1992).
//
// 폴드 점검(10/5): 여행 화면에는 예산 300,000원이 보이는데 여행 경비 화면은 「예산 정하기」로 비어 있었다.
// 같은 여행인데 두 화면이 서로 다른 말을 했다.
import { render, waitFor } from '@testing-library/react-native';

import TripMoney from '../(trip)/[id]/money';
import { loadLedger } from '@/trip/expenses';
import { listTripMembers } from '@/trip/collaboration';
import { loadTripBudget } from '@/trip/tripBudget';

jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, right: 0, bottom: 0, left: 0 }) }));
jest.mock('expo-router', () => ({ useLocalSearchParams: () => ({ id: 'trip-1' }), useRouter: () => ({ back: jest.fn(), replace: jest.fn(), canGoBack: () => true }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token' }) }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko, language: 'ko' }) }));
jest.mock('@/components/Screen', () => ({ Screen: ({ children }: { children: unknown }) => children }));
jest.mock('@/trip/collaboration', () => ({ listTripMembers: jest.fn() }));
jest.mock('@/trip/trips', () => ({ loadTripItineraries: jest.fn(async () => ({ state: 'success', role: 'OWNER', itineraries: [] })) }));
jest.mock('@/plan/itinerary', () => ({ loadItinerary: jest.fn() }));
jest.mock('@/trip/tripBudget', () => ({ loadTripBudget: jest.fn() }));
jest.mock('@/trip/expenses', () => ({ ...jest.requireActual('@/trip/expenses'), loadLedger: jest.fn(), addExpense: jest.fn(), removeExpense: jest.fn(), saveBudget: jest.fn() }));

const ME = { userId: 'me', displayName: '나', role: 'OWNER', joinedAt: '', invitedBy: null, invitedAt: null, isMe: true };
const ledger = (budgetKrw: number | null, totalKrw: number) => ({ state: 'success', ledger: { budgetKrw, totalKrw, items: [] } });

beforeEach(() => {
  (listTripMembers as jest.Mock).mockResolvedValue({ state: 'success', members: [ME], myRole: 'OWNER', canEdit: true });
});

it('🔴 여행 경비 예산이 비어 있으면 여행 계획 예산을 출처와 함께 보여 주고 남은 예산을 그 값으로 계산한다', async () => {
  (loadLedger as jest.Mock).mockResolvedValue(ledger(null, 50000));
  (loadTripBudget as jest.Mock).mockResolvedValue({ state: 'success', budgetKrw: 300000 });
  const view = render(<TripMoney />);
  await waitFor(() => expect(view.getByText(/여행 계획 예산/)).toBeTruthy());
  expect(view.getByText(/250,000/)).toBeTruthy();
  expect(view.queryByText('예산 정하기 ›')).toBeNull();
});

it('🔴 여행 경비에서 직접 정한 예산이 있으면 그 값이 우선이다', async () => {
  (loadLedger as jest.Mock).mockResolvedValue(ledger(100000, 50000));
  (loadTripBudget as jest.Mock).mockResolvedValue({ state: 'success', budgetKrw: 300000 });
  const view = render(<TripMoney />);
  await waitFor(() => expect(view.getByText(/예산 100,000/)).toBeTruthy());
  expect(view.queryByText(/여행 계획 예산/)).toBeNull();
});

it('둘 다 없으면 「예산 정하기」', async () => {
  (loadLedger as jest.Mock).mockResolvedValue(ledger(null, 0));
  (loadTripBudget as jest.Mock).mockResolvedValue({ state: 'success', budgetKrw: null });
  const view = render(<TripMoney />);
  await waitFor(() => expect(view.getByText('예산 정하기 ›')).toBeTruthy());
});
