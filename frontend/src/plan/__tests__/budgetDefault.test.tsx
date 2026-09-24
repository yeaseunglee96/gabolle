// 총예산 기본값 · 하루 끝 21:00 — S15P21E201-1591.
//
// 🔴 이 시험이 지키는 것은 셋이다.
//    ① 기본값은 1인 × 일수 × 5만원이다 — 전에는 인원·박수와 상관없이 10만원이라 2명·2박이면 식비도 안 됐다.
//    ② 사용자가 손댄 예산은 인원·날짜가 바뀌어도 덮지 않는다. 손대지 않은 기본값만 따라간다.
//    ③ 모든 입력이 지나는 update 에서 그렇게 된다 — 홈 시작 바·질문 화면·AI 도우미 어디서 바꿔도.
import type { ReactNode } from 'react';
import { act, render } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { applyBudgetDefault, defaultBudgetKrw, LEGACY_DEFAULT_BUDGET_KRW, restoreBudget, tripDayCount } from '@/plan/budgetDefault';
import { EMPTY_PLAN, PlanProvider, usePlan } from '@/plan/PlanProvider';

jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: null, accessToken: null }) }));

const basis = (adults: number, children: number, startDate: string, endDate: string) => ({ ...EMPTY_PLAN, adults, children, startDate, endDate });

describe('예산 기본값 — 1인 × 일수 × 5만원', () => {
  it('2명 · 2박 3일이면 30만원', () => {
    expect(defaultBudgetKrw(basis(2, 0, '2026-10-03', '2026-10-05'))).toBe(300000);
  });

  it('아이도 한 사람으로 센다 · 당일치기는 하루', () => {
    expect(defaultBudgetKrw(basis(2, 1, '2026-10-03', '2026-10-03'))).toBe(150000);
  });

  it('날짜·인원을 아직 모르면 1명 · 하루로 본다 — 빈 초안의 기본값이 5만원이다', () => {
    expect(tripDayCount('', '')).toBe(1);
    expect(defaultBudgetKrw(basis(0, 0, '', ''))).toBe(50000);
    expect(EMPTY_PLAN.budgetKrw).toBe(50000);
    expect(EMPTY_PLAN.budgetEdited).toBe(false);
  });

  it('하루 끝 기본 시각은 21:00 — 18시면 저녁이 일정에 안 들어간다. 시작은 09:00 그대로', () => {
    expect(EMPTY_PLAN.dayEndTime).toBe('21:00');
    expect(EMPTY_PLAN.dayStartTime).toBe('09:00');
  });
});

describe('손대지 않은 기본값만 따라간다', () => {
  it('인원·날짜가 바뀌면 기본값이 따라 바뀐다', () => {
    const two = applyBudgetDefault(EMPTY_PLAN, { adults: 2, startDate: '2026-10-03', endDate: '2026-10-05' });
    expect(two.budgetKrw).toBe(300000);
    expect(applyBudgetDefault(two, { endDate: '2026-10-03' }).budgetKrw).toBe(100000);
  });

  it('🔴 사용자가 바꾼 예산은 인원·날짜가 바뀌어도 그대로다', () => {
    const edited = applyBudgetDefault(EMPTY_PLAN, { budgetKrw: 70000 });
    expect(edited.budgetEdited).toBe(true);
    expect(applyBudgetDefault(edited, { adults: 4, startDate: '2026-10-03', endDate: '2026-10-06' }).budgetKrw).toBe(70000);
  });

  it('인원·날짜와 무관한 칸이 바뀌면 예산을 건드리지 않는다', () => {
    const current = { ...EMPTY_PLAN, budgetKrw: 120000 };
    expect(applyBudgetDefault(current, { transport: 'CAR' }).budgetKrw).toBe(120000);
  });
});

describe('저장해 둔 옛 초안을 되살릴 때', () => {
  const stored = { adults: 2, children: 0, startDate: '2026-10-03', endDate: '2026-10-05' };

  it('「손댔다」 표시가 없던 옛 초안이 옛 기본값(10만원)이면 새 기본값으로 다시 센다', () => {
    const old = { ...stored, budgetKrw: LEGACY_DEFAULT_BUDGET_KRW };
    expect(restoreBudget(old, { ...EMPTY_PLAN, ...old }).budgetKrw).toBe(300000);
  });

  it('🔴 옛 초안이라도 10만원이 아니면 사용자가 바꾼 것으로 본다 — 덮지 않는다', () => {
    const old = { ...stored, budgetKrw: 80000 };
    const restored = restoreBudget(old, { ...EMPTY_PLAN, ...old });
    expect(restored.budgetKrw).toBe(80000);
    expect(restored.budgetEdited).toBe(true);
  });
});

describe('초안 공급자 — 모든 입력이 지나는 update', () => {
  let plan: ReturnType<typeof usePlan>;
  function Probe() { plan = usePlan(); return null; }
  const Providers = ({ children }: { children: ReactNode }) => <OnboardingPreferencesProvider><PlanProvider>{children}</PlanProvider></OnboardingPreferencesProvider>;

  it('인원·날짜를 바꾸면 예산이 따라가고, 예산을 손댄 뒤에는 멈춘다', () => {
    render(<Probe />, { wrapper: Providers });

    act(() => plan.update({ adults: 2, children: 0, travelers: 2, startDate: '2026-10-03', endDate: '2026-10-05' }));
    expect(plan.draft.budgetKrw).toBe(300000);

    act(() => plan.update({ budgetKrw: 200000 }));
    act(() => plan.update({ adults: 4, travelers: 4 }));
    expect(plan.draft.budgetKrw).toBe(200000);
  });
});
