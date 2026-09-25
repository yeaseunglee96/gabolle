// 걷는 길의 경사 색 — S15P21E201-1658 (백엔드 !1626 의 pieces).
//
// 사용자 결정(2026-09-25): 내비가 막힘을 칠하듯, 버튼 없이 걷는 구간을 경사 색으로 칠한다. 색은 세 단계다.
// 🔴 기준값은 여기 한 곳에만 둔다.
//    · 8.33% — 온톨로지의 휠체어 경사로 기준(1:12). 경사 판정과 같은 값이다. 경사 겹(mobilityLayers.ts)은 이 값을
//      파일에서 천분율 정수(83 = 8.3%)로 읽는다 — 반올림 차이일 뿐 같은 기준이고, 색(state.danger)도 같다.
//    · 5% — 우리가 정한 값이다(온톨로지 기준이 아니다). 「조금 가파름」의 시작.
import { color } from '@/design/tokens';

import type { MapPathPoint } from './types';

export const STEEP_SLOPE_PERCENT = 8.33;
export const MODERATE_SLOPE_PERCENT = 5;

/**
 * 걷는 길의 한 조각 — 백엔드 /routes/directions 걷기 응답의 pieces.
 * from·to = path 의 자리 번호(둘 다 포함, 이웃 조각은 앞.to == 뒤.from). slopePercent 는 방향 없는 기울기(%)이고,
 * null 은 모른다는 뜻이다(30m 미만·다리·터널·길 밖 토막) — 0(평지)과 다르다.
 */
export type SlopePiece = { from: number; to: number; slopePercent: number | null; stairs: boolean };

/** 조각의 색 — 8.33% 이상·계단 빨강 · 5~8.33% 노랑 · 5% 미만 초록 · 모름 회색. */
export function slopeColor(piece: Pick<SlopePiece, 'slopePercent' | 'stairs'>): string {
  if (piece.stairs) return color.state.danger;
  if (piece.slopePercent == null) return color.text.muted;
  if (piece.slopePercent >= STEEP_SLOPE_PERCENT) return color.state.danger;
  if (piece.slopePercent >= MODERATE_SLOPE_PERCENT) return color.state.warning;
  return color.state.success;
}

/** 조각마다 path 를 잘라 색을 붙인다. 조각이 없거나 번호가 path 밖이면 null — 그때는 한 선으로 그린다. */
export function slopeSegments(path: MapPathPoint[], pieces: SlopePiece[] | undefined): Array<{ points: MapPathPoint[]; color: string }> | null {
  if (!pieces?.length) return null;
  const valid = pieces.every((piece) => Number.isInteger(piece.from) && Number.isInteger(piece.to) && piece.from >= 0 && piece.to < path.length && piece.from < piece.to);
  if (!valid) return null;
  return pieces.map((piece) => ({ points: path.slice(piece.from, piece.to + 1), color: slopeColor(piece) }));
}
