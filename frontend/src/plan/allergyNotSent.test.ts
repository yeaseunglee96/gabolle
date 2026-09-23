// 알레르기를 여행 생성 요청에 «싣지 않는다» — S15P21E201-1497 (결정은 -1468 의 ㄱ).
//
// 🔴 이 시험이 지키는 것은 문구가 아니라 «사람이 여행을 만들 수 있는가» 다.
//    운영 place_feature 에 ALLERGEN_TAG 가 0행이라, 이 제약을 보내는 순간 채점기가
//    후보를 전부 「확인 못 함」으로 빼고 일정 생성이 실패한다.
//    실측(2026-09-22): 알레르기·식단을 «고른» 작업 8건 중 성공 0건 — 한 명도 못 만들었다.
//
//    질문을 화면에서 지우는 것만으로는 부족하다. 전에 답해 둔 사람의 값이 draft 에 그대로
//    남아 있고, 그 사람들은 질문이 사라져도 계속 실패한다. 그래서 «보내는 자리»를 막는다.
import { toCreateTripPayload } from '@/api/tripApi';
import { EMPTY_PLAN } from '@/plan/PlanProvider';

const base = { ...EMPTY_PLAN, startDate: '2026-10-12', endDate: '2026-10-13' };

describe('여행 생성 요청과 알레르기', () => {
  it('🔴 전에 답해 둔 알레르기가 남아 있어도 제약으로 안 보낸다', () => {
    const payload = toCreateTripPayload({
      ...base,
      allergies: ['PEANUT', 'SHELLFISH_CRUSTACEAN'],
      allergyStatus: 'VALUES',
      allergyAnswered: true,
    });

    expect(payload.constraints.filter((c) => c.type === 'ALLERGY')).toEqual([]);
    // 코드 자체가 어떤 모양으로도 실리면 안 된다 — 종류만 바꿔 보내는 실수를 막는다.
    expect(JSON.stringify(payload.constraints)).not.toContain('PEANUT');
    expect(JSON.stringify(payload.constraints)).not.toContain('SHELLFISH_CRUSTACEAN');
  });

  it('식단·이동 조건은 그대로 보낸다 — 알레르기만 뺀 것이지 기능을 끈 것이 아니다', () => {
    const payload = toCreateTripPayload({
      ...base,
      allergies: ['EGG'],
      dietTypes: ['VEGAN'],
      wheelchair: true,
    });

    expect(payload.constraints.some((c) => c.type === 'DIET' && c.constraintKey === 'VEGAN')).toBe(true);
    expect(payload.constraints.some((c) => c.type === 'MOBILITY' && c.constraintKey === 'WHEELCHAIR')).toBe(true);
    expect(payload.constraints.some((c) => c.type === 'ALLERGY')).toBe(false);
  });

  it('알레르기를 고르지 않은 사람의 요청은 전과 같다', () => {
    const before = toCreateTripPayload({ ...base, dietTypes: ['HALAL'] });
    const after = toCreateTripPayload({ ...base, dietTypes: ['HALAL'], allergies: [] });
    expect(after.constraints).toEqual(before.constraints);
  });
});
