// 저장해 둔 여행 조건을 사람 말로 — 「채식 · 한 번에 1km까지 걷기 · 가파른 경사 피하기」.
//
// 🔴 여행 만들기 확인 표(UI 캔버스 ⑤-5)와 일정 완성 승차권의 「이 조건을 지켜서 만들었어요」(⑤-6)가 같은 값을 말해야 한다 —
//    두 곳이 따로 만들면 한쪽만 고쳐진다. 초안에 실제로 있는 조건만 적는다(모르는 칸은 안 적는다).
import { txf } from '@/i18n/format';
import type { PlanDraft } from '@/plan/PlanProvider';
import { DIETS } from '@/plan/travelConditions';

type Tx = (ko: string, en: string) => string;

export function conditionLabels(
  draft: Pick<PlanDraft, 'dietStatus' | 'dietTypes' | 'maxWalkingDistanceM' | 'slopeConstraint' | 'stairsConstraint' | 'wheelchair' | 'stroller'>,
  tx: Tx,
  { withAids = true }: { withAids?: boolean } = {},
): string[] {
  const walk = draft.maxWalkingDistanceM;
  return [
    ...(draft.dietStatus === 'VALUES' ? DIETS.filter(([code]) => draft.dietTypes.includes(code)).map(([, ko, en]) => tx(ko, en)) : []),
    ...(typeof walk === 'number' ? [txf(tx, '한 번에 %s까지 걷기', 'Walk up to %s at a time', walk >= 1000 ? `${walk / 1000}km` : `${walk}m`)] : []),
    ...(draft.slopeConstraint === 'AVOID' ? [tx('가파른 경사 피하기', 'Avoid steep slopes')] : []),
    ...(draft.stairsConstraint === 'AVOID' ? [tx('계단 피하기', 'Avoid stairs')] : []),
    ...(withAids && draft.wheelchair ? [tx('휠체어', 'Wheelchair')] : []),
    ...(withAids && draft.stroller ? [tx('유아차', 'Stroller')] : []),
  ];
}
