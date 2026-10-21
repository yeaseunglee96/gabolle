// 여행 돈 — 떠 있는 「쓴 돈 적기」는 아래 시스템 막대만큼 올라간다(S15P21E201-1974).
//
// 갤럭시 탭 앱에서 단추가 아래 작업 표시줄 밑으로 반쯤 깔렸다 — 안전 영역(insets.bottom)을 안 더했다.
import { StyleSheet } from 'react-native';
import { render, waitFor } from '@testing-library/react-native';

import TripMoney from '../(trip)/[id]/money';
import { loadLedger } from '@/trip/expenses';
import { listTripMembers } from '@/trip/collaboration';

jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, right: 0, bottom: 48, left: 0 }) }));
jest.mock('expo-router', () => ({ useLocalSearchParams: () => ({ id: 'trip-1' }), useRouter: () => ({ back: jest.fn(), replace: jest.fn(), canGoBack: () => true }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token' }) }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko, language: 'ko' }) }));
jest.mock('@/components/Screen', () => ({ Screen: ({ children }: { children: unknown }) => children }));
jest.mock('@/trip/collaboration', () => ({ listTripMembers: jest.fn() }));
jest.mock('@/trip/trips', () => ({ loadTripItineraries: jest.fn(async () => ({ state: 'success', role: 'OWNER', itineraries: [{ itineraryId: 'it-1' }] })) }));
jest.mock('@/plan/itinerary', () => ({ loadItinerary: jest.fn(async () => ({ state: 'success', itinerary: { days: [{ date: '2000-01-01', items: [{ id: 'i1', title: '광안리 밀면집', locked: false, startsAt: null }] }] } })) }));
jest.mock('@/trip/expenses', () => ({ ...jest.requireActual('@/trip/expenses'), loadLedger: jest.fn(), addExpense: jest.fn(), removeExpense: jest.fn(), saveBudget: jest.fn() }));


const ME = { userId: 'me', displayName: '진미리', role: 'OWNER', joinedAt: '', invitedBy: null, invitedAt: null, isMe: true };

it('🔴 「쓴 돈 적기」 자리는 아래 시스템 막대 높이만큼 올라간다', async () => {
  (loadLedger as jest.Mock).mockResolvedValue({ state: 'success', ledger: { budgetKrw: null, totalKrw: 0, items: [] } });
  (listTripMembers as jest.Mock).mockResolvedValue({ state: 'success', members: [ME], myRole: 'OWNER', canEdit: true });
  const view = render(<TripMoney />);
  await waitFor(() => expect(view.getByTestId('money-dock')).toBeTruthy());
  expect(StyleSheet.flatten(view.getByTestId('money-dock').props.style).bottom).toBe(16 + 48);
});
