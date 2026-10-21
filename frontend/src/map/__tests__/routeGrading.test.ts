// 경로 선을 고른 조건대로 칠하기 — S15P21E201-1896.
//
// 🔴 이 시험이 지키는 것(장효준 요청 2026-09-30, 조율 세션 결정):
//    ① 조건을 골랐을 때만 조각으로 칠한다. 아무것도 안 골랐으면 조각이 있어도 null — 경로 자기 색 한 가지로 그린다.
//    ② 경사만 → 전과 같은 경사 색(5% · 8.33%). 그늘만 → shadeBad(그늘). 둘 다 → 0.6·slopeBad(경사) + 0.4·shadeBad(그늘).
//       점수 0.33 미만 초록 · 0.66 미만 노랑 · 그 이상 빨강. slopeBad 는 경사 경계(5 · 8.33)를, shadeBad 는 부산 길 기준
//       그늘 경계(0.30 · 0.05)를 점수 문턱에 맞춘다(S15P21E201-1898 — 절대값 1 − 그늘 은 부산 지도를 거의 빨갛게 칠했다).
//    ③ 계단은 늘 빨강. 한쪽만 알면 그 쪽 «자기 기준» 으로. 둘 다 모르면 회색.
//    ④ 그늘 값이 없어도(옛 서버·자료 밖) 무너지지 않는다 — 경사만 골랐을 때의 그림으로 물러난다.
//    ⑤ grading 을 아예 안 준 경로는 전처럼 경사로 칠한다(이동 경로 상세 화면).
import { color } from '@/design/tokens';
import {
  combinedScore, FAIR_BELOW, GOOD_BELOW, gradedSegments, NO_GRADING, pieceGrade, routeLegendOf, SHADE_BAD_AT, SHADE_GOOD_AT, shadeBad, shadeOf,
  SHADE_WEIGHT, slopeBad, SLOPE_WEIGHT, type RouteGrading,
} from '@/map/routeGrading';
import { slopeGrade } from '@/map/slopeGrades';
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

describe('그늘만 고른 여행 — 나쁨 점수 = shadeBad(그늘), 부산 길 기준 경계 0.30 · 0.05', () => {
  it('🔴 그늘 0.30 이상 초록 · 0.05 초과 노랑 · 0.05 이하 빨강', () => {
    expect(SHADE_GOOD_AT).toBe(0.3);
    expect(SHADE_BAD_AT).toBe(0.05);
    expect(pieceGrade(piece(null, 1), SHADE)).toBe('good');
    expect(pieceGrade(piece(null, 0.7), SHADE)).toBe('good');
    expect(pieceGrade(piece(null, 0.3), SHADE)).toBe('good'); // 경계 자신은 초록
    expect(pieceGrade(piece(null, 0.29), SHADE)).toBe('fair');
    expect(pieceGrade(piece(null, 0.2), SHADE)).toBe('fair'); // 부산 길 그늘의 중앙값
    expect(pieceGrade(piece(null, 0.06), SHADE)).toBe('fair');
    expect(pieceGrade(piece(null, 0.05), SHADE)).toBe('bad'); // 경계 자신은 빨강
    expect(pieceGrade(piece(null, 0), SHADE)).toBe('bad');
  });

  it('🔴 shadeBad 는 0~1 안에서 그늘이 많을수록 줄어든다(끊김 없이)', () => {
    let prev = Infinity;
    for (let g = 0; g <= 1.0001; g += 0.01) {
      const bad = shadeBad(g);
      expect(bad).toBeGreaterThanOrEqual(0);
      expect(bad).toBeLessThanOrEqual(1);
      expect(bad).toBeLessThanOrEqual(prev + 1e-9);
      prev = bad;
    }
    expect(shadeBad(0)).toBeCloseTo(1);
    expect(shadeBad(1)).toBeCloseTo(0);
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

describe('경사와 그늘을 함께 고른 여행 — 0.6·slopeBad(경사) + 0.4·shadeBad(그늘)', () => {
  it('🔴 합친 점수의 세 단계', () => {
    expect(pieceGrade(piece(0, 1), BOTH)).toBe('good'); // 0
    expect(pieceGrade(piece(4.9, 0.3), BOTH)).toBe('good'); // 0.194 + 0.132 — 둘 다 자기 기준으로 초록이면 합쳐도 초록
    expect(pieceGrade(piece(4, 0.2), BOTH)).toBe('fair'); // 0.158 + 0.185 — 부산 길 중앙값 그늘
    expect(pieceGrade(piece(0, 0), BOTH)).toBe('fair'); // 0.4 — 평지여도 볕뿐이면 보통
    expect(pieceGrade(piece(20, 1), BOTH)).toBe('fair'); // 0.6 — 아주 가팔라도 온통 그늘이면 보통
    expect(pieceGrade(piece(8.33, 0.6), BOTH)).toBe('fair'); // 0.396 + 0.075
    expect(pieceGrade(piece(7, 0.02), BOTH)).toBe('bad'); // 0.317 + 0.346 — 가파른 편에 볕뿐
    expect(pieceGrade(piece(20, 0), BOTH)).toBe('bad'); // 1.0
  });

  it('🔴 점수식 그대로 — 무게 0.6 · 0.4, 문턱 0.33 · 0.66', () => {
    expect(SLOPE_WEIGHT).toBe(0.6);
    expect(SHADE_WEIGHT).toBe(0.4);
    expect(GOOD_BELOW).toBe(0.33);
    expect(FAIR_BELOW).toBe(0.66);
    expect(combinedScore(0, 1)).toBeCloseTo(0);
    expect(combinedScore(20, 0)).toBeCloseTo(1);
    expect(combinedScore(20, 1)).toBeCloseTo(0.6);
    expect(combinedScore(5, SHADE_GOOD_AT)).toBeCloseTo(0.33); // 두 경계가 만나는 자리는 점수 경계
  });

  it('🔴 slopeBad 의 경계는 «경사만» 의 경계와 같다 — 0~20% 를 0.01% 마다 대 본다', () => {
    const band = (score: number) => (score < GOOD_BELOW ? 'good' : score < FAIR_BELOW ? 'fair' : 'bad');
    for (let s = 0; s <= 20; s = Math.round((s + 0.01) * 100) / 100) {
      expect([s, band(slopeBad(s))]).toEqual([s, slopeGrade({ slopePercent: s, stairs: false })]);
    }
    expect(slopeBad(0)).toBe(0);
    expect(slopeBad(5)).toBeCloseTo(0.33);
    expect(slopeBad(8.33)).toBeCloseTo(0.66);
    expect(slopeBad(16.66)).toBeCloseTo(1);
    expect(slopeBad(40)).toBe(1);
  });

  it('계단은 어느 점수여도 빨강', () => {
    expect(pieceGrade(piece(0, 1, true), BOTH)).toBe('bad');
  });

  it('🔴 그늘을 모르면 «경사만 골랐을 때» 의 색으로 물러난다 — 합친 점수의 문턱으로 다시 매기지 않는다', () => {
    // 4.9% 는 경사만 골랐을 때 초록이다(5% 미만).
    expect(pieceGrade(piece(4.9, null), BOTH)).toBe('good');
    expect(pieceGrade(piece(4.9), BOTH)).toBe('good');
    expect(pieceGrade(piece(8.34, null), BOTH)).toBe('bad');
    // 같은 경사여도 그늘을 알면 합친 점수다 — 볕뿐이면(그늘 0) 노랑
    expect(pieceGrade(piece(4.9, 0), BOTH)).toBe('fair');
  });

  it('🔴 경사를 모르면 «그늘만 골랐을 때» 의 색으로 — 둘 다 모르면 회색', () => {
    expect(pieceGrade(piece(null, 0.9), BOTH)).toBe('good');
    expect(pieceGrade(piece(null, 0.2), BOTH)).toBe('fair');
    expect(pieceGrade(piece(null, 0.02), BOTH)).toBe('bad');
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
