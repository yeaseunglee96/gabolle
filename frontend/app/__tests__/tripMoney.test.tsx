// 여행 돈 화면(S15P21E201-1935) — 합계가 보이고, 정산이 맞고, 적기·지우기가 서버로 간다.
import { fireEvent, render, waitFor } from '@testing-library/react-native';

import TripMoney from '../(trip)/[id]/money';
import { addExpense, loadLedger, removeExpense } from '@/trip/expenses';
import { listTripMembers } from '@/trip/collaboration';

jest.mock('expo-router', () => ({ useLocalSearchParams: () => ({ id: 'trip-1' }), useRouter: () => ({ back: jest.fn(), replace: jest.fn(), canGoBack: () => true }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token' }) }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko, language: 'ko' }) }));
jest.mock('@/components/Screen', () => ({ Screen: ({ children }: { children: unknown }) => children }));
jest.mock('@/trip/collaboration', () => ({ listTripMembers: jest.fn() }));
jest.mock('@/trip/expenses', () => ({ ...jest.requireActual('@/trip/expenses'), loadLedger: jest.fn(), addExpense: jest.fn(), removeExpense: jest.fn(), saveBudget: jest.fn() }));

const ME = { userId: 'me', displayName: '진미리', role: 'OWNER', joinedAt: '', invitedBy: null, invitedAt: null, isMe: true };
const JISU = { userId: 'jisu', displayName: '지수', role: 'EDITOR', joinedAt: '', invitedBy: 'me', invitedAt: null, isMe: false };
const line = (expenseId: string, paidBy: string, amountKrw: number) => ({ expenseId, createdBy: paidBy, paidBy, amountKrw, category: 'FOOD' as const, placeName: '광안리 밀면집', note: null, splitEven: true, spentAt: '2026-10-03T12:40:00+09:00' });
const LEDGER = { budgetKrw: 300000, totalKrw: 48800, items: [line('e1', 'me', 42000), line('e2', 'jisu', 6800)] };

describe('여행 돈', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (loadLedger as jest.Mock).mockResolvedValue({ state: 'success', ledger: LEDGER });
    (listTripMembers as jest.Mock).mockResolvedValue({ state: 'success', members: [ME, JISU], myRole: 'OWNER', canEdit: true });
  });

  it('쓴 돈·남은 돈·정산 한 줄', async () => {
    const view = render(<TripMoney />);
    await waitFor(() => expect(view.getByTestId('money-total').props.children).toBe('48,800원'));
    expect(view.getByText('251,200원 남았어요')).toBeTruthy();
    expect(view.getByText('지수 → 진미리 (나) · 17,600원')).toBeTruthy();
    expect(view.getAllByText('내가 냄', { exact: false }).length).toBeGreaterThan(0);
  });

  it('적기 — 쉼표를 뺀 숫자와 낸 사람(나)이 서버로 간다', async () => {
    (addExpense as jest.Mock).mockResolvedValue({ state: 'success', ledger: LEDGER });
    const view = render(<TripMoney />);
    await waitFor(() => expect(view.getByTestId('money-total')).toBeTruthy());
    fireEvent.press(view.getByTestId('money-add'));
    fireEvent.changeText(view.getByTestId('money-amount'), '9,000');
    fireEvent.press(view.getByTestId('money-save'));
    await waitFor(() => expect(addExpense).toHaveBeenCalledWith('trip-1', expect.objectContaining({ amountKrw: 9000, category: 'FOOD', paidBy: 'me', splitEven: true }), 'token'));
  });

  it('🔴 지우기는 두 번 눌러야 — 한 번에 지우지 않는다', async () => {
    (removeExpense as jest.Mock).mockResolvedValue({ state: 'success', ledger: { ...LEDGER, items: [LEDGER.items[1]], totalKrw: 6800 } });
    const view = render(<TripMoney />);
    await waitFor(() => expect(view.getAllByLabelText('이 줄 지우기').length).toBe(2));
    fireEvent.press(view.getAllByLabelText('이 줄 지우기')[0]);
    expect(removeExpense).not.toHaveBeenCalled();
    fireEvent.press(view.getByLabelText('정말 지우기'));
    await waitFor(() => expect(removeExpense).toHaveBeenCalledWith('trip-1', 'e1', 'token'));
  });
});
