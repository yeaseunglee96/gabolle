// 걷는 길의 경사 색 — S15P21E201-1658 (백엔드 !1626 의 pieces).
//
// 사용자 결정(2026-09-25): 내비가 막힘을 칠하듯 걷는 구간을 경사 색으로 칠한다. 색은 세 단계다.
// 🔴 2026-09-30(S15P21E201-1896): 이제 «경사를 피하고 싶다» 고 고른 여행에서만 칠한다 — 언제 칠하고 그늘을 어떻게 섞는지는
//    routeGrading.ts 가 정하고, 이 파일은 경사 하나만 본 색(단계·기준값)을 정한다.
// 🔴 기준값은 여기 한 곳에만 둔다.
//    · 8.33% — 온톨로지의 휠체어 경사로 기준(1:12). 경사 판정과 같은 값이다.
//    🔴 빨강은 8.33% 를 «넘는» 것이다(> 8.33, 8.33 자신은 노랑). 서버는 경사가 8.33% 이하면 통과로 본다
//       (slope <= 8.33 — 휠체어 경사로 기준 1:12 가 «이하»다). 앱이 >= 로 칠하면 서버가 통과시킨 8.33% 길을 빨갛게,
//       즉 「못 간다」로 보여 준다.
//    · 5% — 우리가 정한 값이다(온톨로지 기준이 아니다). 「조금 가파름」의 시작.
import { color } from '@/design/tokens';

import type { MapPathPoint } from './types';

export const STEEP_SLOPE_PERCENT = 8.33;
export const MODERATE_SLOPE_PERCENT = 5;

/**
 * 걷는 길의 한 조각 — 백엔드 /routes/directions 걷기 응답의 pieces.
 * from·to = path 의 자리 번호(둘 다 포함, 이웃 조각은 앞.to == 뒤.from). slopePercent 는 방향 없는 기울기(%)이고,
 * null 은 모른다는 뜻이다(30m 미만·다리·터널·길 밖 토막) — 0(평지)과 다르다.
 *
 * shade = 그 조각의 그늘(0~1, 1 이 온통 그늘 — 서버가 0.1 단위로 맞춰 보낸다, S15P21E201-1895). 🔴 **null·없음은 «그늘이 0» 이 아니라
 * «모른다»** 이다 — 건물 그림자를 잰 길이 전체의 일부뿐이고, 옛 서버는 이 칸을 아예 안 보낸다. 모르는 것을 그늘 없음(=나쁨)으로
 * 칠하면 자료가 없는 길이 «볕이 뜨거운 길» 로 보인다. 색은 routeGrading.ts 가 정한다.
 */
export type SlopePiece = { from: number; to: number; slopePercent: number | null; stairs: boolean; shade?: number | null };

/** 조각의 등급 — 좋음(초록) · 보통(노랑) · 나쁨(빨강) · 모름(회색). 색은 아래 GRADE_COLOR 한 곳에서만 고른다. */
export type Grade = 'good' | 'fair' | 'bad' | 'unknown';

export const GRADE_COLOR: Record<Grade, string> = {
  good: color.state.success,
  fair: color.state.warning,
  bad: color.state.danger,
  unknown: color.text.muted,
};

/** 경사만 본 조각의 등급 — 8.33% 초과·계단 나쁨 · 5~8.33%(둘 다 포함) 보통 · 5% 미만 좋음 · 모름. */
export function slopeGrade(piece: Pick<SlopePiece, 'slopePercent' | 'stairs'>): Grade {
  if (piece.stairs) return 'bad';
  if (piece.slopePercent == null) return 'unknown';
  if (piece.slopePercent > STEEP_SLOPE_PERCENT) return 'bad';
  if (piece.slopePercent >= MODERATE_SLOPE_PERCENT) return 'fair';
  return 'good';
}

/** 조각의 색 — 8.33% 초과·계단 빨강 · 5~8.33%(둘 다 포함) 노랑 · 5% 미만 초록 · 모름 회색. */
export function slopeColor(piece: Pick<SlopePiece, 'slopePercent' | 'stairs'>): string {
  return GRADE_COLOR[slopeGrade(piece)];
}

/** 조각 번호가 path 안에 들어맞나 — 하나라도 path 밖이거나 거꾸로면 조각 전체를 못 쓴다. 없거나 비었어도 못 쓴다. */
export function piecesFitPath(path: MapPathPoint[], pieces: SlopePiece[] | undefined | null): pieces is SlopePiece[] {
  if (!pieces?.length) return false;
  return pieces.every((piece) => Number.isInteger(piece.from) && Number.isInteger(piece.to) && piece.from >= 0 && piece.to < path.length && piece.from < piece.to);
}

/** 조각마다 path 를 잘라 색을 붙인다. 조각이 없거나 번호가 path 밖이면 null — 그때는 한 선으로 그린다. */
export function slopeSegments(path: MapPathPoint[], pieces: SlopePiece[] | undefined): Array<{ points: MapPathPoint[]; color: string }> | null {
  if (!piecesFitPath(path, pieces)) return null;
  return pieces.map((piece) => ({ points: path.slice(piece.from, piece.to + 1), color: slopeColor(piece) }));
}
