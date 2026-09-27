// 시트 손잡이 끌기 — 잡고 아래로 끌면 내려가는 창(S15P21E201-1787, QA).
//
// 전에는 손잡이가 «누르면 닫히는 단추»뿐이었다. 사람은 막대 모양을 보고 끌어 내리는데, 짧게 쓸면 누른 것으로 쳐서 닫히고
// 길게 쓸면 손가락이 단추 밖으로 나가 취소돼 «될 때도 있고 안 될 때도 있는» 것처럼 보였다.
//
// 🔴 누르기는 그대로 둔다. 끌기는 손가락이 세로로 조금(DRAG_START) 움직인 뒤에야 가져간다 — 그 전까지는 안쪽 단추(손잡이 누르기,
//    「내리기」 칩 등)가 누름을 받는다. 화면 읽기 프로그램도 지금처럼 단추를 누른다.
import { useRef } from 'react';
import { PanResponder, type GestureResponderHandlers, type PanResponderGestureState } from 'react-native';

/** 이만큼 세로로 움직여야 끌기로 본다. 이보다 적으면 누르기다. */
export const DRAG_START = 6;
/** 이만큼 끌어 내리면 닫는다. */
export const DISMISS_DISTANCE = 72;
/** 이만큼 빠르게(px/ms) 쓸어내리면 거리가 짧아도 닫는다. */
export const DISMISS_VELOCITY = 0.9;

/** 끌기로 볼 움직임인가 — 세로로 DRAG_START 넘게, 가로보다 세로로 더. */
export function isVerticalDrag(dx: number, dy: number): boolean {
  return Math.abs(dy) > DRAG_START && Math.abs(dy) > Math.abs(dx);
}

/** 손을 뗐을 때 닫을까 — 충분히 내렸거나, 아래로 빠르게 쓸었다. */
export function shouldDismiss(dy: number, vy: number): boolean {
  return dy > DISMISS_DISTANCE || (dy > DRAG_START && vy > DISMISS_VELOCITY);
}

/**
 * 높이를 끌어서 정하는 창(여행 상세)에서 손을 뗐을 때 — 접을지, 몇 높이에 멈출지.
 * 최소 높이보다 낮게 내렸거나 빠르게 쓸어내렸으면 접는다. 아니면 최소~최대 사이 그 높이에 멈춘다.
 */
export function settleSheetHeight(startHeight: number, dy: number, vy: number, bounds: { min: number; max: number }): { collapse: true } | { collapse: false; height: number } {
  const next = startHeight - dy;
  if (next < bounds.min || (dy > DRAG_START && vy > DISMISS_VELOCITY)) return { collapse: true };
  return { collapse: false, height: Math.round(Math.min(bounds.max, Math.max(bounds.min, next))) };
}

type DragHandlers = {
  /** 끌기를 가져간 순간. */
  onStart?: () => void;
  /** 손가락이 움직일 때 — dy 는 시작점에서 아래로 간 거리(위로 가면 음수). */
  onMove?: (dy: number) => void;
  /** 손을 뗐을 때(다른 곳이 가져가 끊겼을 때도). */
  onEnd: (dy: number, vy: number) => void;
};

/**
 * 손잡이를 감싼 View 에 붙이는 끌기 처리. 누르기는 가져가지 않고 세로 움직임만 가로챈다(capture) — 안쪽 단추가 먼저 누름을
 * 잡았어도 끌기가 시작되면 넘겨받는다.
 */
export function useSheetDrag(handlers: DragHandlers): GestureResponderHandlers {
  const latest = useRef(handlers);
  latest.current = handlers;
  const responder = useRef(PanResponder.create({
    onStartShouldSetPanResponder: () => false,
    onMoveShouldSetPanResponderCapture: (_, g: PanResponderGestureState) => isVerticalDrag(g.dx, g.dy),
    onMoveShouldSetPanResponder: (_, g: PanResponderGestureState) => isVerticalDrag(g.dx, g.dy),
    onPanResponderGrant: () => latest.current.onStart?.(),
    onPanResponderMove: (_, g) => latest.current.onMove?.(g.dy),
    onPanResponderRelease: (_, g) => latest.current.onEnd(g.dy, g.vy),
    onPanResponderTerminate: (_, g) => latest.current.onEnd(g.dy, g.vy),
    // 끌고 있는 동안 안쪽 목록이 가져가지 못하게 한다 — 반쯤 끌다 창이 멈추면 고장으로 보인다.
    onPanResponderTerminationRequest: () => false,
  })).current;
  return responder.panHandlers;
}
