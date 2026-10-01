// 「한 번에 0m까지 걷기」 — S15P21E201-1903. 0 은 「제한 없음」이라 조건으로 적지 않는다.
import { conditionLabels } from '@/plan/conditionLabels';
import { EMPTY_PLAN } from '@/plan/PlanProvider';

const tx = (_ko: string, en: string) => en;

describe('걷는 거리 조건 글자', () => {
  it('🔴 0(제한 없음)이면 걷기 조건을 적지 않는다', () => {
    expect(conditionLabels({ ...EMPTY_PLAN, maxWalkingDistanceM: 0 }, tx).join(' · ')).not.toContain('Walk up to');
  });
  it('정한 거리는 그대로 적는다', () => {
    expect(conditionLabels({ ...EMPTY_PLAN, maxWalkingDistanceM: 1000 }, tx)).toContain('Walk up to 1km at a time');
    expect(conditionLabels({ ...EMPTY_PLAN, maxWalkingDistanceM: 500 }, tx)).toContain('Walk up to 500m at a time');
  });
});
