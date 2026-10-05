// 여행 돈 — 쓴 돈 적기·지우기·예산과 정산 계산(S15P21E201-1935). 서버 계약: back/dev
// `GET/POST /api/v1/trips/{id}/expenses` · `DELETE …/expenses/{expenseId}` · `PUT …/budget` — 모두 같은 장부를 돌려준다.
//
// 🔴 정산(누가 누구에게 얼마)은 서버가 저장하지 않는다 — 구성원이 들고 나면 바뀌는 값이라 여기서 그때그때 계산한다.
import { apiRequest, ApiClientError } from '@/api/client';

export type ExpenseCategory = 'FOOD' | 'CAFE' | 'TRANSPORT' | 'ADMISSION' | 'SHOPPING' | 'LODGING' | 'OTHER';
export const EXPENSE_CATEGORIES: ExpenseCategory[] = ['FOOD', 'CAFE', 'TRANSPORT', 'ADMISSION', 'SHOPPING', 'LODGING', 'OTHER'];

export type Expense = {
  expenseId: string;
  createdBy: string;
  paidBy: string;
  amountKrw: number;
  category: ExpenseCategory;
  placeName: string | null;
  note: string | null;
  splitEven: boolean;
  spentAt: string;
};

export type Ledger = { budgetKrw: number | null; totalKrw: number; items: Expense[] };

export type NewExpense = { amountKrw: number; category: ExpenseCategory; paidBy?: string; placeName?: string | null; note?: string | null; splitEven?: boolean };

export type LedgerResult = { state: 'success'; ledger: Ledger } | { state: 'forbidden' | 'error'; message: string };

function failure(error: unknown, fallback: string): Exclude<LedgerResult, { state: 'success' }> {
  if (error instanceof ApiClientError && error.status === 403) return { state: 'forbidden', message: error.message };
  return { state: 'error', message: error instanceof Error ? error.message : fallback };
}

const base = (tripId: string) => `/api/v1/trips/${encodeURIComponent(tripId)}`;

export async function loadLedger(tripId: string, accessToken: string | null): Promise<LedgerResult> {
  try {
    return { state: 'success', ledger: await apiRequest<Ledger>(`${base(tripId)}/expenses`, { accessToken }) };
  } catch (error) {
    return failure(error, '여행 경비를 불러오지 못했어요.');
  }
}

export async function addExpense(tripId: string, expense: NewExpense, accessToken: string | null): Promise<LedgerResult> {
  try {
    return { state: 'success', ledger: await apiRequest<Ledger>(`${base(tripId)}/expenses`, { method: 'POST', accessToken, body: expense }) };
  } catch (error) {
    return failure(error, '지출을 추가하지 못했어요.');
  }
}

export async function removeExpense(tripId: string, expenseId: string, accessToken: string | null): Promise<LedgerResult> {
  try {
    return { state: 'success', ledger: await apiRequest<Ledger>(`${base(tripId)}/expenses/${encodeURIComponent(expenseId)}`, { method: 'DELETE', accessToken }) };
  } catch (error) {
    return failure(error, '지우지 못했어요.');
  }
}

export async function saveBudget(tripId: string, amountKrw: number | null, accessToken: string | null): Promise<LedgerResult> {
  try {
    return { state: 'success', ledger: await apiRequest<Ledger>(`${base(tripId)}/budget`, { method: 'PUT', accessToken, body: { amountKrw } }) };
  } catch (error) {
    return failure(error, '예산을 저장하지 못했어요.');
  }
}

/** 갈래별 합계 — 큰 것부터. 0원인 갈래는 뺀다. */
export function totalsByCategory(items: Expense[]): Array<{ category: ExpenseCategory; amountKrw: number }> {
  const sums = new Map<ExpenseCategory, number>();
  for (const item of items) sums.set(item.category, (sums.get(item.category) ?? 0) + item.amountKrw);
  return [...sums.entries()].map(([category, amountKrw]) => ({ category, amountKrw })).sort((a, b) => b.amountKrw - a.amountKrw);
}

export type Transfer = { from: string; to: string; amountKrw: number };

/**
 * 누가 누구에게 얼마 — 보내는 횟수가 적게(가장 많이 받을 사람에게 가장 많이 낼 사람부터).
 *
 * - 「반씩 나누기」 줄은 지금 구성원 모두가 똑같이 나눈다. 원 단위로 안 나누어떨어지는 나머지는 낸 사람이 진다
 *   — 1원 때문에 정산 줄이 하나 더 생기지 않게.
 * - 「반씩 나누기」가 아닌 줄은 낸 사람 혼자 쓴 돈이라 정산에 안 들어간다.
 * - 구성원이 아닌 사람(나간 동행)이 낸 돈도 그 사람 몫으로 셈한다 — 받을 돈이 사라지면 안 된다.
 */
export function settle(items: Expense[], memberIds: string[]): Transfer[] {
  if (memberIds.length < 2) return [];
  const balance = new Map<string, number>(memberIds.map((id) => [id, 0]));
  for (const item of items) {
    if (!item.splitEven) continue;
    const share = Math.floor(item.amountKrw / memberIds.length);
    for (const id of memberIds) balance.set(id, (balance.get(id) ?? 0) - share);
    // 낸 사람은 전액을 받을 돈으로 — 나머지(원 단위)는 낸 사람이 진다
    balance.set(item.paidBy, (balance.get(item.paidBy) ?? 0) + share * memberIds.length);
  }
  const creditors = [...balance.entries()].filter(([, v]) => v > 0).sort((a, b) => b[1] - a[1]).map(([id, v]) => ({ id, v }));
  const debtors = [...balance.entries()].filter(([, v]) => v < 0).sort((a, b) => a[1] - b[1]).map(([id, v]) => ({ id, v: -v }));
  const transfers: Transfer[] = [];
  let c = 0;
  let d = 0;
  while (c < creditors.length && d < debtors.length) {
    const amount = Math.min(creditors[c].v, debtors[d].v);
    if (amount > 0) transfers.push({ from: debtors[d].id, to: creditors[c].id, amountKrw: amount });
    creditors[c].v -= amount;
    debtors[d].v -= amount;
    if (creditors[c].v === 0) c += 1;
    if (debtors[d].v === 0) d += 1;
  }
  return transfers;
}


const CURRENCY_SIGN: Record<string, string> = { JPY: '¥', CNY: '¥', CNH: '¥', TWD: 'NT$', USD: '$' };

/**
 * 원을 그 나라 돈으로 어림한 글 — 「≈ ¥5,312」. 외국어 화면에서 합계 아래 한 줄로만 쓴다(S15P21E201-1935).
 * 작은 단위가 없는 통화(엔·대만 달러)와 큰 금액은 정수로, 그 밖(달러·위안)은 소수 둘째 자리까지 — 「≈ $36.41」.
 */
export function approxForeignText(krw: number, code: string, perUnitKrw: number): string | null {
  if (!Number.isFinite(krw) || !(perUnitKrw > 0)) return null;
  const amount = krw / perUnitKrw;
  const whole = code === 'JPY' || code === 'TWD' || amount >= 1000;
  const number = whole
    ? Math.round(amount).toLocaleString('en-US')
    : amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const sign = CURRENCY_SIGN[code];
  return sign ? `≈ ${sign}${number}` : `≈ ${number} ${code}`;
}
