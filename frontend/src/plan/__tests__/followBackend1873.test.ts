// 백엔드 2026-09-30 변경을 화면이 따라가는가 — S15P21E201-1878 (서버 S15P21E201-1873).
//
// 서버는 음식 취향의 채식·할랄을 식단 조건 「반드시」로 더해 채점하고(저장은 안 한다), 여행 식단을 NONE 으로 답한 여행만
// 더하지 않는다. 그 셈을 화면이 모르면 ① 「지킨 조건」에 채식이 빠지고 ② 「이번엔 해당 없음」이 서버에 안 닿는다.
import { toCreateTripPayload } from '@/api/tripApi';
import { EMPTY_PLAN } from '@/plan/PlanProvider';
import { conditionLabels } from '@/plan/conditionLabels';
import { effectiveDietCodes, tasteDietsApplied } from '@/plan/tasteDiets';

const tx = (_ko: string, en: string) => en;
const base = { ...EMPTY_PLAN, startDate: '2026-10-12', endDate: '2026-10-13' };
const tasteVeg = { ...base, foods: ['SEAFOOD', 'VEGETARIAN'], preferenceAnswerStatus: { ...base.preferenceAnswerStatus, foodPreference: 'SELECTED' as const } };

describe('음식 취향의 식단 — 서버 withTasteDiets 와 같은 셈', () => {
  it('🔴 음식 취향에 채식이 있으면 식단을 안 정했어도 지킨 조건에 채식이 나온다', () => {
    expect(tasteDietsApplied(tasteVeg)).toEqual(['VEGETARIAN']);
    expect(conditionLabels(tasteVeg, tx)).toContain('Vegetarian');
  });

  it('🔴 이번 여행 식단을 「해당 없음」으로 답하면 더하지 않는다 — 여행 답이 계정 취향보다 이긴다', () => {
    const declined = { ...tasteVeg, dietStatus: 'NONE' as const, dietAnswered: true };
    expect(effectiveDietCodes(declined)).toEqual([]);
    expect(conditionLabels(declined, tx)).not.toContain('Vegetarian');
  });

  it('식단에서 이미 고른 것은 한 번만 · 다른 식단과는 함께', () => {
    expect(effectiveDietCodes({ ...tasteVeg, dietStatus: 'VALUES', dietTypes: ['VEGETARIAN'] })).toEqual(['VEGETARIAN']);
    expect(effectiveDietCodes({ ...tasteVeg, dietStatus: 'VALUES', dietTypes: ['HALAL'] })).toEqual(['HALAL', 'VEGETARIAN']);
  });

  it('음식 취향을 고르지 않은 상태면(서버로 안 감) 더하지 않는다 · 채식·할랄 말고는 식단이 아니다', () => {
    expect(tasteDietsApplied({ ...tasteVeg, preferenceAnswerStatus: { ...base.preferenceAnswerStatus, foodPreference: 'UNKNOWN' } })).toEqual([]);
    expect(tasteDietsApplied({ ...tasteVeg, foods: ['SEAFOOD', 'MILMYEON'] })).toEqual([]);
  });
});

describe('식단 「해당 없음」을 서버에 보낸다', () => {
  it('🔴 NONE 을 서버가 읽는 모양으로 — 값 없음·SOFT·dietRequirement 없음(동의 검사에 안 걸린다)', () => {
    const payload = toCreateTripPayload({ ...tasteVeg, dietStatus: 'NONE', dietAnswered: true });
    const diets = payload.constraints.filter((c) => c.type === 'DIET');
    expect(diets).toEqual([{
      type: 'DIET', constraintKey: 'VEGETARIAN', severity: 'SOFT', operator: null,
      value: null, threshold: null, answerStatus: 'NONE', dietRequirement: null,
    }]);
  });

  it('고른 식단은 예전 그대로 · 안 정했으면 식단 제약을 안 보낸다', () => {
    const chosen = toCreateTripPayload({ ...base, dietStatus: 'VALUES', dietTypes: ['HALAL'], dietAnswered: true });
    expect(chosen.constraints.filter((c) => c.type === 'DIET')).toEqual([expect.objectContaining({ constraintKey: 'HALAL', answerStatus: 'SELECTED', dietRequirement: 'REQUIRED' })]);
    expect(toCreateTripPayload(base).constraints.filter((c) => c.type === 'DIET')).toEqual([]);
  });

  it('「해당 없음」인데 초안에 예전 식단 코드가 남아 있어도 그것을 고른 것으로 보내지 않는다', () => {
    const stale = toCreateTripPayload({ ...base, dietStatus: 'NONE', dietTypes: ['HALAL'], dietAnswered: true });
    expect(stale.constraints.filter((c) => c.type === 'DIET' && c.answerStatus === 'SELECTED')).toEqual([]);
  });
});
