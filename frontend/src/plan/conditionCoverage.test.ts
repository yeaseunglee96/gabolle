// 「자료가 없다」와 「모른다」를 섞지 않는가 — S15P21E201-1044.
//
// 🔴 이 셋이 화면에서 서로 다른 말이 되어야 한다.
//
//      자료가 없다   → 「확인할 자료가 없어요」를 적는다
//      자료가 있다   → 아무 말도 안 한다
//      못 물어봤다   → **아무 말도 안 한다**  ← 여기가 틀리기 쉬운 자리다
//
// 마지막 줄을 「없다」로 다루면, 끝점이 아직 안 올라온 배포에서 **모든 문항에**
// 「자료가 없어요」가 붙는다. 자료는 멀쩡한데 화면만 거짓말을 하게 된다.
import { COVERAGE_FEATURE, hasNoPlaceData, toCoverage, type ConditionCoverageResponse } from '@/plan/conditionCoverage';

const response: ConditionCoverageResponse = {
  conditions: [
    { kind: 'CONSTRAINT', code: 'DIET', features: [{ featureType: 'DIETARY_SUPPORT_TAG', placeCount: 0, totalPlaceCount: 6866 }] },
    {
      kind: 'CONSTRAINT',
      code: 'MOBILITY',
      features: [
        { featureType: 'ACCESSIBILITY_TAG', placeCount: 102, totalPlaceCount: 6866 },
        { featureType: 'STAIRS_PRESENT', placeCount: 0, totalPlaceCount: 6866 },
      ],
    },
    { kind: 'PREFERENCE', code: 'SLOPE_PREFERENCE', features: [{ featureType: 'SLOPE_PERCENT', placeCount: 2682, totalPlaceCount: 6866 }] },
  ],
};

describe('여행 조건 — 판정할 장소 자료가 있나', () => {
  it('한 곳도 없는 표식만 「없다」로 본다', () => {
    const coverage = toCoverage(response);
    expect(hasNoPlaceData(coverage, COVERAGE_FEATURE.diet)).toBe(true);
    expect(hasNoPlaceData(coverage, COVERAGE_FEATURE.slope)).toBe(false);
  });

  it('🔴 문항이 아니라 표식으로 가른다 — 접근성이 계단을 덮지 않는다', () => {
    const coverage = toCoverage(response);
    // 같은 MOBILITY 문항인데 한쪽은 102곳, 한쪽은 0곳이다. 뭉치면 계단 줄이 계속
    // 못 지키는 약속으로 남는다.
    expect(hasNoPlaceData(coverage, 'ACCESSIBILITY_TAG')).toBe(false);
    expect(hasNoPlaceData(coverage, COVERAGE_FEATURE.stairs)).toBe(true);
  });

  it('🔴 못 물어봤으면 아무 문항에도 안 붙는다', () => {
    expect(hasNoPlaceData(null, COVERAGE_FEATURE.diet)).toBe(false);
    // 응답에 아예 없는 표식도 「모른다」다. 서버가 새 문항을 아직 안 알려준 경우다.
    expect(hasNoPlaceData(toCoverage(response), COVERAGE_FEATURE.shade)).toBe(false);
  });

  it('🔴 장소가 통째로 0곳이면 이 조건만의 문제가 아니다', () => {
    const empty = toCoverage({
      conditions: [{ kind: 'CONSTRAINT', code: 'DIET', features: [{ featureType: 'DIETARY_SUPPORT_TAG', placeCount: 0, totalPlaceCount: 0 }] }],
    });
    // DB 가 비었거나 아직 안 실린 것이다. 「이 조건만 자료가 없어요」라고 적으면
    // 원인을 엉뚱한 곳으로 돌린다.
    expect(hasNoPlaceData(empty, COVERAGE_FEATURE.diet)).toBe(false);
  });

  it('모양이 예상과 다른 줄은 버리고 나머지를 살린다', () => {
    const coverage = toCoverage({
      conditions: [
        { kind: 'CONSTRAINT', code: 'DIET', features: [{ featureType: undefined as unknown as string, placeCount: 0, totalPlaceCount: 10 }] },
        { kind: 'PREFERENCE', code: 'SHADE_PREFERENCE', features: [{ featureType: 'SHADE_SCORE', placeCount: 0, totalPlaceCount: 10 }] },
      ],
    });
    expect(hasNoPlaceData(coverage, COVERAGE_FEATURE.shade)).toBe(true);
  });
});
