// 저장해 둔 여행 조건을 사람 말로 — 「채식 · 한 번에 1km까지 걷기 · 가파른 경사 피하기」.
//
// 🔴 여행 만들기 확인 표(UI 캔버스 ⑤-5)와 일정 완성 승차권의 「이 조건을 지켜서 만들었어요」(⑤-6)가 같은 값을 말해야 한다 —
//    두 곳이 따로 만들면 한쪽만 고쳐진다. 초안에 실제로 있는 조건만 적는다(모르는 칸은 안 적는다).
import { txf } from '@/i18n/format';
import type { PlanDraft } from '@/plan/PlanProvider';
import { DIETS } from '@/plan/travelConditions';
import { effectiveDietCodes } from '@/plan/tasteDiets';

type Tx = (ko: string, en: string) => string;

export function conditionLabels(
  draft: Pick<PlanDraft, 'dietStatus' | 'dietTypes' | 'foods' | 'preferenceAnswerStatus' | 'maxWalkingDistanceM' | 'slopeConstraint' | 'stairsConstraint' | 'wheelchair' | 'stroller'>,
  tx: Tx,
  { withAids = true }: { withAids?: boolean } = {},
): string[] {
  const walk = draft.maxWalkingDistanceM;
  return [
    // 🔴 식단은 서버가 실제로 지키는 것 — 음식 취향의 채식·할랄도 더해진다(S15P21E201-1878, 서버 S15P21E201-1873).
    //    초안의 식단 칸만 적으면 채식으로 걸러 놓고 「지킨 조건」에는 채식이 없었다.
    ...(() => { const diets = effectiveDietCodes(draft); return DIETS.filter(([code]) => diets.includes(code)).map(([, ko, en]) => tx(ko, en)); })(),
    // 0 은 「제한 없음」이다(ConditionsPromptModal) — 「한 번에 0m까지 걷기」로 적지 않는다(S15P21E201-1903).
    ...(typeof walk === 'number' && walk > 0 ? [txf(tx, '한 번에 %s까지 걷기', 'Walk up to %s at a time', walk >= 1000 ? `${walk / 1000}km` : `${walk}m`)] : []),
    ...(draft.slopeConstraint === 'AVOID' ? [tx('가파른 경사 피하기', 'Avoid steep slopes')] : []),
    ...(draft.stairsConstraint === 'AVOID' ? [tx('계단 피하기', 'Avoid stairs')] : []),
    ...(withAids && draft.wheelchair ? [tx('휠체어', 'Wheelchair')] : []),
    ...(withAids && draft.stroller ? [tx('유아차', 'Stroller')] : []),
  ];
}
