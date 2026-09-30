// 경로 선을 무엇으로 칠하나 — 사용자가 고른 조건(경사 피하기 · 그늘 우선)이 정한다 (S15P21E201-1896).
//
// 요청(장효준, 2026-09-30): 코스 지도에서
//   ① 경사를 골랐으면 경로와 상관없이 깔리던 경사·그늘 «겹» 을 없애고 **경로 선 자체** 를 경사 색으로 칠한다
//   ② 경사와 그늘을 함께 골랐으면 둘을 합친 점수로 칠한다
//   ③ 추천 코스를 볼 때와, 여행 중 GPS 로 그 일정을 따라갈 때 모두
//
// 🔴 언제 칠하나 — **고른 조건이 있을 때만** 이다. 전에는 걷는 구간이면 조건과 상관없이 늘 경사 색이었다(S15P21E201-1658).
//    경사도 그늘도 안 골랐으면 경로는 자기 색 한 가지(여행 페이지는 남색)로 그린다 — 안 고른 조건으로 칠해
//    「내가 원하지 않은 기준의 빨강」을 보이지 않는다.
//
// 색을 정하는 규칙(사용자가 고른 조건마다 다르다)
//
//   경사만        5% 미만 초록 · 5~8.33% 노랑 · 8.33% 초과 빨강                          (slopeGrades.ts)
//   그늘만        나쁨 점수 = 1 − 그늘.            0.33 미만 초록 · 0.66 미만 노랑 · 그 이상 빨강
//   경사 + 그늘   나쁨 점수 = 0.6 × min(경사 ÷ 8.33, 1) + 0.4 × (1 − 그늘).   같은 문턱(0.33 · 0.66)
//   계단          어느 경우든 빨강
//   한쪽만 안다   아는 쪽을 **그 쪽 자기 기준** 으로 — 경사만 알면 «경사만» 색, 그늘만 알면 «그늘만» 색
//   둘 다 모른다  회색
//
// 🔴 «한쪽만 알면 그 쪽 자기 기준» 인 까닭: 그늘을 잰 길이 서버 길 전체의 일부뿐이라(건물 그림자 자료가 여섯 지역의 일부 길에만
//    맞았다) 그늘을 모르는 조각이 많다. 그 조각을 합친 점수의 문턱(경사 2.75%·5.5%)으로 칠하면, 같은 길인데 그늘 자료의 유무에
//    따라 «경사만 골랐을 때» 와 색의 기준이 달라진다. 자료가 없는 곳은 조용히 «경사만 골랐을 때» 의 그림으로 물러난다.
import type { MapPathPoint } from './types';
import { GRADE_COLOR, piecesFitPath, slopeGrade, STEEP_SLOPE_PERCENT, type Grade, type SlopePiece } from './slopeGrades';

/**
 * 사용자가 고른 조건. slope = «가파른 경사 피하기»(AVOID), shade = «그늘 많은 곳 우선»(PREFER).
 * 둘 다 false 면 조건을 안 골랐다는 뜻이고, 그때는 조각으로 자르지 않는다.
 */
export type RouteGrading = { slope: boolean; shade: boolean };

export const NO_GRADING: RouteGrading = { slope: false, shade: false };

/**
 * 🔴 grading 을 «아예 안 준» 경로(이동 경로 상세 화면 등 조건을 모르는 자리)는 전과 똑같이 경사로 칠한다.
 *    조건을 아는 여행 페이지만 이 값을 명시한다 — 이동 경로 화면의 동작을 이 일이 바꾸지 않는다.
 */
const LEGACY_GRADING: RouteGrading = { slope: true, shade: false };

/** 합친 점수의 무게 — 경사 60%, 그늘 40% (조율 세션 결정, 2026-09-30). */
export const SLOPE_WEIGHT = 0.6;
export const SHADE_WEIGHT = 0.4;
/** 점수(0 좋음 ~ 1 나쁨)의 문턱 — 0.33 미만 초록, 0.66 미만 노랑, 그 이상 빨강. */
export const GOOD_BELOW = 0.33;
export const FAIR_BELOW = 0.66;

export type ColoredSegment = { points: MapPathPoint[]; color: string };

/** 조각의 그늘(0~1). 없거나 숫자가 아니면 null(모름) — 0 으로 바꾸지 않는다. 범위 밖은 끝으로 붙인다. */
export function shadeOf(piece: Pick<SlopePiece, 'shade'>): number | null {
  const shade = piece.shade;
  return typeof shade === 'number' && Number.isFinite(shade) ? Math.min(1, Math.max(0, shade)) : null;
}

function gradeOfScore(score: number): Grade {
  if (score < GOOD_BELOW) return 'good';
  if (score < FAIR_BELOW) return 'fair';
  return 'bad';
}

/**
 * 경사와 그늘을 둘 다 알 때의 나쁨 점수(0 좋음 ~ 1 나쁨) — 0.6 × min(경사 ÷ 8.33, 1) + 0.4 × (1 − 그늘).
 * 경사는 8.33% 에서 1 로 붙는다(그보다 가팔라도 «가장 나쁨» 이상은 없다). 그늘은 0~1 이고 1 이 온통 그늘이다.
 */
export function combinedScore(slopePercent: number, shade: number): number {
  const slopeBad = Math.min(Math.max(slopePercent, 0) / STEEP_SLOPE_PERCENT, 1);
  return SLOPE_WEIGHT * slopeBad + SHADE_WEIGHT * (1 - shade);
}

/** 고른 조건으로 본 조각의 등급. */
export function pieceGrade(piece: SlopePiece, grading: RouteGrading): Grade {
  if (!grading.slope && !grading.shade) return 'unknown';
  if (piece.stairs) return 'bad';
  const shade = grading.shade ? shadeOf(piece) : null;
  const slopeKnown = grading.slope && typeof piece.slopePercent === 'number' && Number.isFinite(piece.slopePercent);
  if (slopeKnown && shade !== null) return gradeOfScore(combinedScore(piece.slopePercent as number, shade));
  if (slopeKnown) return slopeGrade(piece);
  if (shade !== null) return gradeOfScore(1 - shade);
  return 'unknown';
}

export function pieceColor(piece: SlopePiece, grading: RouteGrading): string {
  return GRADE_COLOR[pieceGrade(piece, grading)];
}

/**
 * 조각마다 path 를 잘라 색을 붙인다. null 이면 **한 선(경로 자기 색)** 으로 그린다 — 조건을 안 골랐거나,
 * 조각이 없거나, 조각 번호가 path 밖일 때.
 *
 * 이웃한 조각이 같은 색이면 한 선으로 합친다(그릴 선이 줄고, 이음매도 없다).
 */
export function gradedSegments(path: MapPathPoint[], pieces: SlopePiece[] | undefined | null, grading?: RouteGrading | null): ColoredSegment[] | null {
  const rule = grading ?? LEGACY_GRADING;
  if (!rule.slope && !rule.shade) return null;
  if (!piecesFitPath(path, pieces)) return null;
  const parts: ColoredSegment[] = [];
  let lastEnd = -1;
  for (const piece of pieces) {
    const color = pieceColor(piece, rule);
    const last = parts[parts.length - 1];
    if (last && last.color === color && lastEnd === piece.from) {
      last.points = last.points.concat(path.slice(piece.from + 1, piece.to + 1));
    } else {
      parts.push({ points: path.slice(piece.from, piece.to + 1), color });
    }
    lastEnd = piece.to;
  }
  return parts;
}

/** 범례가 무엇을 말할지 — 고른 조건과, 그려진 조각 가운데 그늘을 아는 것이 하나라도 있는가. */
export type RouteLegend = { grading: RouteGrading; hasShade: boolean };

/**
 * 지도에 조각 색으로 칠한 선이 하나라도 있을 때만 범례를 낸다. 없으면 null.
 * 🔴 조건을 골랐어도 그릴 조각이 없으면(대중교통 구간뿐이거나 아직 길을 받는 중) 색의 뜻을 읽히지 않는다 —
 *    안 칠한 선의 범례는 「이 둘레엔 문제가 없구나」와 「아직 못 받았구나」를 구분하지 못하게 한다.
 * 🔴 grading 을 명시한 경로만 센다 — 안 준 경로(조건을 모르는 화면)는 범례를 낼 근거가 없다.
 */
export function routeLegendOf(
  routes: ReadonlyArray<{ path?: MapPathPoint[]; pieces?: SlopePiece[]; grading?: RouteGrading; weight?: number }>,
): RouteLegend | null {
  let grading: RouteGrading | null = null;
  let hasShade = false;
  for (const route of routes) {
    if (route.weight != null || !route.path?.length || !route.grading) continue;
    if (gradedSegments(route.path, route.pieces, route.grading) === null) continue;
    grading = route.grading;
    if ((route.pieces ?? []).some((piece) => shadeOf(piece) !== null)) hasShade = true;
  }
  return grading ? { grading, hasShade } : null;
}
