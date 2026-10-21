// 여행 경비 — 「지출 추가」 창의 「적기」 단추가 아래 시스템 막대(탭 작업 표시줄)에 깔리지 않는다(S15P21E201-1996).
//
// 전시 영상 녹화 중 갤럭시 탭에서 창 맨 아래 「적기」가 작업 표시줄 밑에 깔려 지출을 저장할 수 없었다.
import { StyleSheet } from 'react-native';
import { fireEvent, render, waitFor } from '@testing-library/react-native';

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

it('🔴 「지출 추가」 창 아래 여백에 시스템 막대 높이가 들어가고, 내용은 스크롤된다', async () => {
  (loadLedger as jest.Mock).mockResolvedValue({ state: 'success', ledger: { budgetKrw: null, totalKrw: 0, items: [] } });
  (listTripMembers as jest.Mock).mockResolvedValue({ state: 'success', members: [ME], myRole: 'OWNER', canEdit: true });
  const view = render(<TripMoney />);
  await waitFor(() => expect(view.getByTestId('money-add')).toBeTruthy());
  fireEvent.press(view.getByTestId('money-add'));
  const sheet = await waitFor(() => view.getByTestId('money-sheet'));
  expect(StyleSheet.flatten(sheet.props.style).paddingBottom).toBe(24 + 48);
  expect(view.getByTestId('money-sheet-scroll')).toBeTruthy();
  expect(view.getByTestId('money-save')).toBeTruthy();
});
