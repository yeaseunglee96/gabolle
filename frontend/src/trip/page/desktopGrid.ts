// 데스크톱 판 일정 — 장소 카드 열과 지도 칸의 나눔 (S15P21E201-1947).
//
// 🔴 전에는 카드를 언제나 한 줄에 넷, 지도는 언제나 440 이었다. 시안(1440)에서는 맞지만 갤럭시 탭 세로(753dp,
//    본문 약 690)에서는 지도가 440 을 차지하고 남은 220 에 카드 넷을 넣어 한 장이 45px — 글자가 한 자씩 세로로 쌓였다.
//    이제 지도는 본문의 일부만 가져가고(260~440), 카드는 한 장이 CARD_MIN 이상이 되는 만큼만 한 줄에 놓는다.
import { spacing } from '@/design/tokens';

/** 시안 지도 칸. 넓은 화면에서는 여기서 멈춘다. */
export const MAP_COLUMN = 440;
/** 지도 칸이 이보다 좁으면 지도로서 쓸모가 없다. */
const MAP_COLUMN_MIN = 260;
/** 본문 중 지도가 가져가는 몫. */
const MAP_SHARE = 0.42;
/** 장소 카드 한 장이 읽을 만한 최소 폭 — 장소 이름 한 줄 · 시각 · 사진이 들어간다. */
const CARD_MIN = 160;
/** 카드 한 장의 최대 폭 — TripPageDesktop 의 CARD_MAX_WIDTH 와 같다. */
const CARD_MAX = 260;
const MAX_COLUMNS = 4;
/** 카드 사이 · 카드 열과 지도 사이 (TripPageDesktop 의 styles.grid · styles.body). */
const CARD_GAP = spacing[3];
const BODY_GAP = spacing[6];

export type DesktopCardGrid = { mapWidth: number; columns: number; cardWidth: number };

const clamp = (value: number, low: number, high: number) => Math.min(high, Math.max(low, value));

/** 본문 폭(카드 열 + 지도 칸)을 받아 지도 폭과 카드 열 수를 정한다. 0 은 아직 못 잰 것. */
export function desktopCardGrid(body: number): DesktopCardGrid {
  if (!body) return { mapWidth: MAP_COLUMN, columns: MAX_COLUMNS, cardWidth: CARD_MAX };
  const mapWidth = clamp(Math.round(body * MAP_SHARE), MAP_COLUMN_MIN, MAP_COLUMN);
  const cards = body - mapWidth - BODY_GAP;
  const columns = clamp(Math.floor((cards + CARD_GAP) / (CARD_MIN + CARD_GAP)), 1, MAX_COLUMNS);
  const cardWidth = Math.min(CARD_MAX, Math.floor((cards - CARD_GAP * (columns - 1)) / columns));
  return { mapWidth, columns, cardWidth };
}

/** 실제로 잰 카드 줄 폭에서 한 장의 폭. */
export function cardWidthIn(row: number, columns: number): number {
  return Math.floor((row - CARD_GAP * (columns - 1)) / columns);
}
