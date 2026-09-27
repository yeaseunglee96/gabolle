// 시트 손잡이 끌기 판정 — S15P21E201-1787(QA).
import { DISMISS_DISTANCE, DRAG_START, isVerticalDrag, settleSheetHeight, shouldDismiss } from '../sheetDrag';

describe('끌기와 누르기 가르기', () => {
  it('조금 움직인 것은 누르기다 — 손잡이 누르기·「내리기」 칩이 그대로 받는다', () => {
    expect(isVerticalDrag(0, DRAG_START)).toBe(false);
  });

  it('세로로 움직이면 끌기다. 옆으로 더 움직였으면 아니다', () => {
    expect(isVerticalDrag(2, 20)).toBe(true);
    expect(isVerticalDrag(30, 20)).toBe(false);
  });
});

describe('끌어서 닫기', () => {
  it('충분히 내리면 닫는다', () => {
    expect(shouldDismiss(DISMISS_DISTANCE + 1, 0)).toBe(true);
  });

  it('짧아도 빠르게 쓸어내리면 닫는다', () => {
    expect(shouldDismiss(20, 1.5)).toBe(true);
  });

  it('조금 내렸다 멈추면 안 닫는다 — 제자리로 돌아간다', () => {
    expect(shouldDismiss(30, 0.1)).toBe(false);
  });

  it('위로 빠르게 쓸어도 안 닫는다', () => {
    expect(shouldDismiss(-40, -2)).toBe(false);
  });
});

describe('높이를 끌어 정하는 창(여행 상세)', () => {
  const bounds = { min: 200, max: 600 };

  it('조금 내리면 그 높이에 멈춘다 — 뒤의 지도가 더 보인다', () => {
    expect(settleSheetHeight(600, 150, 0.1, bounds)).toEqual({ collapse: false, height: 450 });
  });

  it('최소보다 낮게 내리면 접는다', () => {
    expect(settleSheetHeight(600, 420, 0.1, bounds)).toEqual({ collapse: true });
  });

  it('빠르게 쓸어내리면 거리가 짧아도 접는다', () => {
    expect(settleSheetHeight(600, 40, 1.4, bounds)).toEqual({ collapse: true });
  });

  it('위로 끌면 다시 키울 수 있지만 최대를 넘지 않는다', () => {
    expect(settleSheetHeight(450, -300, -0.2, bounds)).toEqual({ collapse: false, height: 600 });
  });
});
