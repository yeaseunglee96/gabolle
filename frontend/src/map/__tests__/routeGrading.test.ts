// 경로 선을 고른 조건대로 칠하기 — S15P21E201-1896.
//
// 🔴 이 시험이 지키는 것(장효준 요청 2026-09-30, 조율 세션 결정):
//    ① 조건을 골랐을 때만 조각으로 칠한다. 아무것도 안 골랐으면 조각이 있어도 null — 경로 자기 색 한 가지로 그린다.
//    ② 경사만 → 전과 같은 경사 색(5% · 8.33%). 그늘만 → 나쁨 점수 = 1 − 그늘. 둘 다 → 0.6·min(경사/8.33, 1) + 0.4·(1 − 그늘).
//       점수 0.33 미만 초록 · 0.66 미만 노랑 · 그 이상 빨강.
//    ③ 계단은 늘 빨강. 한쪽만 알면 그 쪽 «자기 기준» 으로. 둘 다 모르면 회색.
//    ④ 그늘 값이 없어도(옛 서버·자료 밖) 무너지지 않는다 — 경사만 골랐을 때의 그림으로 물러난다.
//    ⑤ grading 을 아예 안 준 경로는 전처럼 경사로 칠한다(이동 경로 상세 화면).
import { color } from '@/design/tokens';
import {
  combinedScore, FAIR_BELOW, GOOD_BELOW, gradedSegments, NO_GRADING, pieceGrade, routeLegendOf, shadeOf, SHADE_WEIGHT, SLOPE_WEIGHT, type RouteGrading,
} from '@/map/routeGrading';
import { GRADE_COLOR, slopeSegments, type SlopePiece } from '@/map/slopeGrades';

const SLOPE: RouteGrading = { slope: true, shade: false };
const SHADE: RouteGrading = { slope: false, shade: true };
const BOTH: RouteGrading = { slope: true, shade: true };

const piece = (slopePercent: number | null, shade?: number | null, stairs = false): SlopePiece => ({ from: 0, to: 1, slopePercent, stairs, shade });
const at = (latitude: number) => ({ latitude, longitude: 129.1 });
const path = [at(35.1), at(35.101), at(35.102), at(35.103), at(35.104)];

describe('경사만 고른 여행', () => {
  it('🔴 전과 같은 경사 색 — 5% 미만 초록 · 5~8.33% 노랑 · 8.33% 초과 빨강, 8.33% 자신은 노랑', () => {
    expect(pieceGrade(piece(4.99), SLOPE)).toBe('good');
    expect(pieceGrade(piece(5), SLOPE)).toBe('fair');
    expect(pieceGrade(piece(8.33), SLOPE)).toBe('fair');
    expect(pieceGrade(piece(8.34), SLOPE)).toBe('bad');
  });

  it('그늘 값이 있어도 무시한다 — 안 고른 기준으로 칠하지 않는다', () => {
    expect(pieceGrade(piece(2, 0), SLOPE)).toBe('good');
    expect(pieceGrade(piece(9, 1), SLOPE)).toBe('bad');
  });

  it('계단은 빨강, 경사를 모르면 회색', () => {
    expect(pieceGrade(piece(1, 1, true), SLOPE)).toBe('bad');
    expect(pieceGrade(piece(null, 1, true), SLOPE)).toBe('bad');
    expect(pieceGrade(piece(null, 0.5), SLOPE)).toBe('unknown');
  });
});

describe('그늘만 고른 여행 — 나쁨 점수 = 1 − 그늘', () => {
  it('🔴 그늘 많으면 초록 · 중간 노랑 · 적으면 빨강 (문턱 0.33 · 0.66)', () => {
    expect(pieceGrade(piece(null, 1), SHADE)).toBe('good');
    expect(pieceGrade(piece(null, 0.7), SHADE)).toBe('good'); // 0.3
    expect(pieceGrade(piece(null, 0.68), SHADE)).toBe('good'); // 0.32
    expect(pieceGrade(piece(null, 0.66), SHADE)).toBe('fair'); // 0.34
    expect(pieceGrade(piece(null, 0.4), SHADE)).toBe('fair'); // 0.6
    expect(pieceGrade(piece(null, 0.35), SHADE)).toBe('fair'); // 0.65
    expect(pieceGrade(piece(null, 0.33), SHADE)).toBe('bad'); // 0.67
    expect(pieceGrade(piece(null, 0), SHADE)).toBe('bad');
  });

  it('경사는 안 본다 — 가파른 길이어도 그늘이 많으면 초록', () => {
    expect(pieceGrade(piece(20, 0.9), SHADE)).toBe('good');
  });

  it('🔴 그늘을 모르면 회색이다 — 0(그늘 없음=빨강)으로 치지 않는다', () => {
    expect(pieceGrade(piece(2, null), SHADE)).toBe('unknown');
    expect(pieceGrade(piece(2), SHADE)).toBe('unknown');
  });

  it('계단은 늘 빨강', () => {
    expect(pieceGrade(piece(null, 1, true), SHADE)).toBe('bad');
  });
});

describe('경사와 그늘을 함께 고른 여행 — 0.6·min(경사/8.33, 1) + 0.4·(1 − 그늘)', () => {
  it('🔴 합친 점수의 세 단계', () => {
    expect(pieceGrade(piece(0, 1), BOTH)).toBe('good'); // 0
    expect(pieceGrade(piece(4.165, 1), BOTH)).toBe('good'); // 0.3
    expect(pieceGrade(piece(4.165, 0.9), BOTH)).toBe('fair'); // 0.34
    expect(pieceGrade(piece(5, 0.5), BOTH)).toBe('fair'); // 0.36 + 0.2
    expect(pieceGrade(piece(0, 0), BOTH)).toBe('fair'); // 0.4 — 평지여도 볕뿐이면 보통
    expect(pieceGrade(piece(20, 1), BOTH)).toBe('fair'); // 0.6 — 경사가 8.33 을 넘어도 1 로 붙는다, 그늘이 다 받쳐 준다
    expect(pieceGrade(piece(8.33, 0.6), BOTH)).toBe('bad'); // 0.76
    expect(pieceGrade(piece(20, 0), BOTH)).toBe('bad'); // 1.0
  });

  it('🔴 점수식 그대로 — 0.6·min(경사/8.33, 1) + 0.4·(1 − 그늘), 경사가 그늘보다 무겁다', () => {
    expect(SLOPE_WEIGHT).toBe(0.6);
    expect(SHADE_WEIGHT).toBe(0.4);
    expect(GOOD_BELOW).toBe(0.33);
    expect(FAIR_BELOW).toBe(0.66);
    expect(combinedScore(0, 1)).toBeCloseTo(0);
    expect(combinedScore(4.165, 0.5)).toBeCloseTo(0.5); // 0.3 + 0.2
    expect(combinedScore(8.33, 0)).toBeCloseTo(1);
    expect(combinedScore(20, 1)).toBeCloseTo(0.6); // 경사는 8.33% 에서 1 로 붙는다
    // 가장 가파르고 온통 그늘(0.6)이 평지인데 볕뿐(0.4)인 길보다 나쁘다 — 경사의 무게가 크다
    expect(combinedScore(8.33, 1)).toBeGreaterThan(combinedScore(0, 0));
  });

  it('계단은 어느 점수여도 빨강', () => {
    expect(pieceGrade(piece(0, 1, true), BOTH)).toBe('bad');
  });

  it('🔴 그늘을 모르면 «경사만 골랐을 때» 의 색으로 물러난다 — 합친 점수의 문턱으로 다시 매기지 않는다', () => {
    // 4.9% 는 경사만 골랐을 때 초록이다(5% 미만). 합친 점수의 문턱으로 셈하면 0.6·0.588 = 0.353 → 노랑이 된다.
    expect(pieceGrade(piece(4.9, null), BOTH)).toBe('good');
    expect(pieceGrade(piece(4.9), BOTH)).toBe('good');
    expect(pieceGrade(piece(8.34, null), BOTH)).toBe('bad');
    // 같은 경사여도 그늘을 알면 합친 점수다
    expect(pieceGrade(piece(4.9, 0.3), BOTH)).toBe('fair');
  });

  it('🔴 경사를 모르면 «그늘만 골랐을 때» 의 색으로 — 둘 다 모르면 회색', () => {
    expect(pieceGrade(piece(null, 0.9), BOTH)).toBe('good');
    expect(pieceGrade(piece(null, 0.2), BOTH)).toBe('bad');
    expect(pieceGrade(piece(null, null), BOTH)).toBe('unknown');
    expect(pieceGrade(piece(null), BOTH)).toBe('unknown');
  });
});

describe('그늘 값 다듬기', () => {
  it('숫자가 아니거나 없으면 모른다(null), 범위 밖은 끝으로 붙인다', () => {
    expect(shadeOf({ shade: undefined })).toBeNull();
    expect(shadeOf({ shade: null })).toBeNull();
    expect(shadeOf({ shade: Number.NaN })).toBeNull();
    expect(shadeOf({ shade: '0.5' as unknown as number })).toBeNull();
    expect(shadeOf({ shade: 0 })).toBe(0);
    expect(shadeOf({ shade: 1.4 })).toBe(1);
    expect(shadeOf({ shade: -0.2 })).toBe(0);
  });
});

describe('조각으로 자르기 — gradedSegments', () => {
  const pieces: SlopePiece[] = [
    { from: 0, to: 1, slopePercent: null, stairs: false },
    { from: 1, to: 3, slopePercent: 2.3, stairs: false },
    { from: 3, to: 4, slopePercent: null, stairs: true },
  ];

  it('🔴 조건을 안 골랐으면 조각이 있어도 null — 경로 자기 색 한 가지로 그린다', () => {
    expect(gradedSegments(path, pieces, NO_GRADING)).toBeNull();
    expect(gradedSegments(path, pieces, { slope: false, shade: false })).toBeNull();
  });

  it('🔴 경사를 골랐으면 전과 같은 조각·같은 색', () => {
    const segments = gradedSegments(path, pieces, SLOPE);
    expect(segments?.map((segment) => segment.points.length)).toEqual([2, 3, 2]);
    expect(segments?.map((segment) => segment.color)).toEqual([color.text.muted, color.state.success, color.state.danger]);
    expect(segments).toEqual(slopeSegments(path, pieces));
  });

  it('🔴 grading 을 안 준 경로는 전처럼 경사로 칠한다(이동 경로 상세)', () => {
    expect(gradedSegments(path, pieces)).toEqual(slopeSegments(path, pieces));
    expect(gradedSegments(path, pieces, null)).toEqual(slopeSegments(path, pieces));
  });

  it('조각이 없거나 번호가 path 밖이면 null', () => {
    expect(gradedSegments(path, undefined, BOTH)).toBeNull();
    expect(gradedSegments(path, [], BOTH)).toBeNull();
    expect(gradedSegments(path, [{ from: 0, to: 9, slopePercent: 1, stairs: false }], BOTH)).toBeNull();
  });

  it('🔴 이웃한 조각이 같은 색이면 한 선으로 합친다 — 이음매도 그릴 선도 줄어든다', () => {
    const flat: SlopePiece[] = [
      { from: 0, to: 1, slopePercent: 1, stairs: false },
      { from: 1, to: 2, slopePercent: 2, stairs: false },
      { from: 2, to: 4, slopePercent: 9, stairs: false },
    ];
    const segments = gradedSegments(path, flat, SLOPE);
    expect(segments?.map((segment) => segment.color)).toEqual([color.state.success, color.state.danger]);
    expect(segments?.[0].points).toEqual(path.slice(0, 3));
    expect(segments?.[1].points).toEqual(path.slice(2, 5));
  });

  it('이어지지 않은 조각은 색이 같아도 합치지 않는다 — 사이 구멍을 선으로 메우지 않는다', () => {
    const gap: SlopePiece[] = [
      { from: 0, to: 1, slopePercent: 1, stairs: false },
      { from: 2, to: 3, slopePercent: 1, stairs: false },
    ];
    expect(gradedSegments(path, gap, SLOPE)).toHaveLength(2);
  });

  it('🔴 합친 점수로 칠한 조각 — 그늘 값이 조각마다 다르면 색이 나뉜다', () => {
    const shaded: SlopePiece[] = [
      { from: 0, to: 1, slopePercent: 1, stairs: false, shade: 0.9 }, // 0.6·0.12 + 0.4·0.1 = 0.112 초록
      { from: 1, to: 2, slopePercent: 1, stairs: false, shade: 0 }, // 0.072 + 0.4 = 0.472 노랑
      { from: 2, to: 3, slopePercent: 1, stairs: false }, // 그늘 모름 → 경사만: 초록
    ];
    const segments = gradedSegments(path, shaded, BOTH);
    expect(segments?.map((segment) => segment.color)).toEqual([GRADE_COLOR.good, GRADE_COLOR.fair, GRADE_COLOR.good]);
  });
});

describe('범례 정보 — routeLegendOf', () => {
  const route = (extra: object = {}) => ({ path, pieces: [{ from: 0, to: 1, slopePercent: 1, stairs: false }], grading: SLOPE, ...extra });

  it('🔴 칠한 선이 있을 때만 낸다 — 조건을 안 골랐거나 조각이 없으면 null', () => {
    expect(routeLegendOf([])).toBeNull();
    expect(routeLegendOf([route({ grading: NO_GRADING })])).toBeNull();
    expect(routeLegendOf([route({ pieces: undefined })])).toBeNull();
    expect(routeLegendOf([route({ pieces: [{ from: 0, to: 9, slopePercent: 1, stairs: false }] })])).toBeNull();
    expect(routeLegendOf([route({ path: undefined })])).toBeNull();
  });

  it('🔴 grading 을 명시하지 않은 경로는 범례 근거가 아니다(조건을 모르는 화면)', () => {
    expect(routeLegendOf([route({ grading: undefined })])).toBeNull();
  });

  it('굵기를 직접 준 보조 선은 세지 않는다', () => {
    expect(routeLegendOf([route({ weight: 4 })])).toBeNull();
  });

  it('고른 조건과, 그늘을 아는 조각이 있는지를 알려 준다', () => {
    expect(routeLegendOf([route()])).toEqual({ grading: SLOPE, hasShade: false });
    const shaded = route({ grading: BOTH, pieces: [{ from: 0, to: 1, slopePercent: 1, stairs: false, shade: 0.5 }] });
    expect(routeLegendOf([route({ grading: BOTH }), shaded])).toEqual({ grading: BOTH, hasShade: true });
    expect(routeLegendOf([route({ grading: BOTH })])).toEqual({ grading: BOTH, hasShade: false });
  });
});
