// 접근성 자료가 «있는데 적다»는 것을 미리 말한다 — S15P21E201-1855.
//
// 🔴 hasNoPlaceData 로는 이 상태를 못 잡는다. 접근성 표식은 6,866곳 중 102곳(1.5%)이라
//    0곳이 «아니어서» 화면이 아무 말도 안 했다. 그런데 휠체어를 고르면 거의 모든 곳이
//    「확인되지 않았어요」로 나온다 — QA 에서 「거의 높은 확률로 뜬다」로 올라온 그것이다.
//    사용자는 그것을 「앱이 고장 났다」로 읽는다. 자료가 적다는 것이 사실이고, 그 사실을
//    말하면 「고장」이 「아직 덜 모았구나」가 된다.
import { COVERAGE_FEATURE, coverageCountsOf, hasNoPlaceData, hasScarcePlaceData, toCoverage, type ConditionCoverageResponse } from '@/plan/conditionCoverage';

declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

/** 2026-09-16 실측값 그대로. 접근성 102 / 6,866 = 1.5%. */
const response: ConditionCoverageResponse = {
  conditions: [
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

describe('자료가 적은 조건을 가려낸다', () => {
  const coverage = toCoverage(response);

  it('🔴 「없다」와 「적다」는 다른 말이다 — 접근성은 102곳이라 «없지» 않다', () => {
    expect(hasNoPlaceData(coverage, COVERAGE_FEATURE.accessibility)).toBe(false);
    expect(hasScarcePlaceData(coverage, COVERAGE_FEATURE.accessibility)).toBe(true);
  });

  it('0곳은 「적다」가 아니라 「없다」가 맡는다 — 한 줄에 두 말이 겹치지 않게', () => {
    expect(hasNoPlaceData(coverage, COVERAGE_FEATURE.stairs)).toBe(true);
    expect(hasScarcePlaceData(coverage, COVERAGE_FEATURE.stairs)).toBe(false);
  });

  it('넉넉한 조건에는 안 붙는다 — 경사는 39%다', () => {
    expect(hasScarcePlaceData(coverage, COVERAGE_FEATURE.slope)).toBe(false);
  });

  it('🔴 못 물어봤으면 아무 말도 안 한다 — 숫자를 지어내지 않는다', () => {
    expect(hasScarcePlaceData(null, COVERAGE_FEATURE.accessibility)).toBe(false);
    expect(coverageCountsOf(null, COVERAGE_FEATURE.accessibility)).toBeNull();
    // 응답에 아예 없는 표식도 「모른다」다.
    expect(hasScarcePlaceData(coverage, 'NOT_THERE')).toBe(false);
  });

  it('센 숫자를 그대로 준다 — 비율만 적으면 얼마나 적은지가 안 와닿는다', () => {
    expect(coverageCountsOf(coverage, COVERAGE_FEATURE.accessibility)).toEqual({ placeCount: 102, totalPlaceCount: 6866 });
  });

  it('🔴 이동 보조 문항이 이 안내를 실제로 붙인다', () => {
    const source: string = readFileSync(join(__dirname, '..', '..', '..', 'app', '(plan)', 'questions.tsx'), 'utf8');
    expect(source).toContain('hasScarcePlaceData(coverage, COVERAGE_FEATURE.accessibility)');
    expect(source).toContain('지금 접근성을 확인한 곳은');
  });
});
