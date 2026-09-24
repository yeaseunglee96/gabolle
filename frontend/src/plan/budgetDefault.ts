// 총예산 기본값 — 1인 × 일수 × 5만원(S15P21E201-1591, 사용자 결정 2026-09-24).
//
// 전에는 인원·박수와 상관없이 10만원이라 2명·2박이면 식비도 안 됐다. 숙박비는 뺀 값이다 —
// 서버의 예산 상한도 메뉴 값(식비·카페·입장료)만 센다.
//
// 🔴 사용자가 손댄 예산은 덮지 않는다. 기본값이 5만원일 때 사용자가 직접 5만원을 골랐는지는 값만으로
//    알 수 없어서, 초안에 「손댔다」 표시(budgetEdited)를 따로 둔다. 인원·날짜가 바뀌면 손대지 않은
//    기본값만 따라 바뀐다.
import type { PlanDraft } from './PlanProvider';

export const DAILY_BUDGET_PER_PERSON_KRW = 50000;
/** 옛 기본값. 「손댔다」 표시가 없던 옛 초안이 이 값이면 손대지 않은 기본값으로 본다. */
export const LEGACY_DEFAULT_BUDGET_KRW = 100000;

type BudgetBasis = Pick<PlanDraft, 'adults' | 'children' | 'startDate' | 'endDate'>;

/** 여행 일수 — 가는 날과 오는 날을 다 센다(2박 3일 → 3). 날짜를 아직 모르면 하루로 본다. */
export function tripDayCount(startDate: string, endDate: string): number {
  if (!startDate || !endDate) return 1;
  const days = Math.round((Date.parse(`${endDate}T00:00:00Z`) - Date.parse(`${startDate}T00:00:00Z`)) / 86400000) + 1;
  return Number.isFinite(days) && days > 0 ? days : 1;
}

/** 1인 × 일수 × 5만원, 1만원 단위. 인원을 아직 모르면 한 명으로 본다. */
export function defaultBudgetKrw(basis: BudgetBasis): number {
  const people = Math.max(1, (basis.adults || 0) + (basis.children || 0));
  return Math.round((people * tripDayCount(basis.startDate, basis.endDate) * DAILY_BUDGET_PER_PERSON_KRW) / 10000) * 10000;
}

const BASIS_KEYS = ['adults', 'children', 'startDate', 'endDate'] as const;

/**
 * 초안에 바뀐 값을 얹는다. 예산을 직접 바꾸면 「손댔다」가 되고, 그렇지 않으면 인원·날짜가 바뀔 때
 * 손대지 않은 기본값만 다시 센다.
 */
export function applyBudgetDefault(current: PlanDraft, patch: Partial<PlanDraft>): PlanDraft {
  const next = { ...current, ...patch };
  if ('budgetKrw' in patch) return 'budgetEdited' in patch ? next : { ...next, budgetEdited: true };
  if (next.budgetEdited) return next;
  const basisChanged = BASIS_KEYS.some((key) => key in patch && patch[key] !== current[key]);
  return basisChanged ? { ...next, budgetKrw: defaultBudgetKrw(next) } : next;
}

/**
 * 저장해 둔 초안을 되살릴 때. 「손댔다」 표시가 없던 옛 초안은 옛 기본값(10만원)이면 손대지 않은 것으로 보고
 * 새 기본값으로 다시 센다 — 그러지 않으면 만들다 만 2명·2박 초안에 10만원이 그대로 남는다.
 */
export function restoreBudget(stored: Partial<PlanDraft>, restored: PlanDraft): PlanDraft {
  const edited = typeof stored.budgetEdited === 'boolean'
    ? stored.budgetEdited
    : typeof stored.budgetKrw === 'number' && stored.budgetKrw !== LEGACY_DEFAULT_BUDGET_KRW;
  return edited ? { ...restored, budgetEdited: true } : { ...restored, budgetEdited: false, budgetKrw: defaultBudgetKrw(restored) };
}
