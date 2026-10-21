// 음식 취향의 채식·할랄이 이번 여행의 식단 조건으로 적용되는가 — S15P21E201-1878.
//
// 🔴 서버가 2026-09-30 부터 음식 취향(FOOD_PREFERENCE)의 채식·할랄을 식단 조건 「반드시」로 더해 채점한다
//    (백엔드 S15P21E201-1873, BaselineCandidateScorer.withTasteDiets). 더한 조건은 저장하지 않아서 여행 조건
//    응답에는 안 나온다 — 앱이 따로 셈하지 않으면 「지킨 조건」에 채식이 빠진 채 고기집이 빠진 일정이 나온다.
//    규칙을 서버와 한 줄씩 같게 둔다.
//      - 이번 여행의 식단을 「해당 없음(NONE)」으로 답했으면 더하지 않는다(여행 답이 계정 취향보다 이긴다).
//      - 이미 같은 식단을 골랐으면 한 번만.
//      - 음식 취향 가운데 식단으로 보는 코드는 채식·할랄뿐.
import type { ConstraintSelectionStatus } from '@/plan/PlanProvider';

export const TASTE_DIETS = ['VEGETARIAN', 'HALAL'] as const;

type DietDraft = {
  dietStatus: ConstraintSelectionStatus;
  dietTypes?: readonly string[];
  foods?: readonly string[];
  /** 여행 초안이면 준다 — 음식 취향은 「골랐음(SELECTED)」일 때만 서버로 간다(tripApi 의 preference()). */
  preferenceAnswerStatus?: { foodPreference: string };
};

/** 음식 취향에서 와서 이번 여행에 식단 조건으로 더해지는 것 — 식단에서 이미 고른 것은 뺀다. */
export function tasteDietsApplied(draft: DietDraft): string[] {
  if (draft.dietStatus === 'NONE') return [];
  if (draft.preferenceAnswerStatus && draft.preferenceAnswerStatus.foodPreference !== 'SELECTED') return [];
  const chosen = draft.dietTypes ?? [];
  return [...new Set((draft.foods ?? []).filter((code) => (TASTE_DIETS as readonly string[]).includes(code) && !chosen.includes(code)))];
}

/** 서버가 실제로 지키는 식단 — 보내는 식단(tripApi: 「해당 없음」이 아니면 초안의 식단 코드) + 음식 취향에서 더해진 것. */
export function effectiveDietCodes(draft: DietDraft): string[] {
  // 예전 저장본에서 불러온 초안은 식단 칸이 비어 있을 수 있다 — 없으면 없는 것으로 친다.
  const chosen = draft.dietStatus !== 'NONE' ? [...(draft.dietTypes ?? [])] : [];
  return [...chosen, ...tasteDietsApplied(draft)];
}
