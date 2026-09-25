// 추천 노출 기록 — 추천한 장소 카드가 «실제로 화면에 보일 때만» 보낸다(S15P21E201-1696, 계획서 8.1 · 07 계약).
//
// 🔴 목록에 들었다는 이유로는 보내지 않는다. 카드의 절반 넘게 화면(창) 안에 들어왔을 때만 — 내려 본 곳만 센다.
// 🔴 한 화면에서 같은 (추천 요청, 장소)는 한 번. 다시 들어오면 다시 센다 — 그것도 한 번의 노출이다.
// 🔴 추천 요청 번호(requestId)가 없는 곳(손으로 더한 곳 · 옛 서버)은 보내지 않는다 — 서버가 요청 번호 없는 노출은 400 이다.
// 폰·웹이 같은 방식이다: 1초마다 카드의 화면 위치(measureInWindow)를 잰다. 웹의 IntersectionObserver 는 폰에 없다.
// 순위·이유 코드는 안 싣는다 — 서버가 요청 번호 + 장소 번호로 채운다(07).
import { useCallback, useEffect, useRef, type ReactNode } from 'react';
import { View, useWindowDimensions } from 'react-native';

import { sendAppEvent } from './appEvents';

/** 어느 화면에서 보였나 — 서버는 검사하지 않고 그대로 저장한다(조율 세션 확인). */
export type ImpressionScreen = 'TRIP_ITINERARY' | 'TRIP_COURSES';

/** 카드가 이만큼 넘게 창 안에 들어와야 「보였다」. */
export const IMPRESSION_MIN_VISIBLE = 0.5;
/** 이 간격으로 잰다. */
export const IMPRESSION_CHECK_MS = 1000;

type Frame = { x: number; y: number; width: number; height: number };

/** 창 안에 들어온 넓이의 비율(0~1). */
export function visibleFraction(frame: Frame, window: { width: number; height: number }): number {
  if (frame.width <= 0 || frame.height <= 0) return 0;
  const width = Math.max(0, Math.min(frame.x + frame.width, window.width) - Math.max(frame.x, 0));
  const height = Math.max(0, Math.min(frame.y + frame.height, window.height) - Math.max(frame.y, 0));
  return (width * height) / (frame.width * frame.height);
}

type Tracked = { node: View; placeId: string; requestId: string };
export type ImpressionTracker = { register: (placeId: string, requestId: string | null | undefined) => ((node: View | null) => void) | undefined };

export function useImpressionTracker({ accessToken, sourceScreen, active }: {
  accessToken: string | null;
  sourceScreen: ImpressionScreen;
  /** 이 목록이 지금 보이는 화면인가. 아니면 재지 않는다. */
  active: boolean;
}): ImpressionTracker {
  const tracked = useRef(new Map<string, Tracked>());
  const refs = useRef(new Map<string, (node: View | null) => void>());
  const sent = useRef(new Set<string>());
  const window = useWindowDimensions();
  const windowRef = useRef(window);
  windowRef.current = window;

  // 카드마다 같은 ref 함수를 준다 — 그릴 때마다 새 함수면 붙였다 뗐다를 되풀이한다.
  const register = useCallback((placeId: string, requestId: string | null | undefined) => {
    if (!requestId || !placeId) return undefined;
    const key = `${requestId}:${placeId}`;
    let ref = refs.current.get(key);
    if (!ref) {
      ref = (node: View | null) => {
        if (node) tracked.current.set(key, { node, placeId, requestId });
        else tracked.current.delete(key);
      };
      refs.current.set(key, ref);
    }
    return ref;
  }, []);

  useEffect(() => {
    if (!active || !accessToken) return undefined;
    const check = () => {
      tracked.current.forEach((entry, key) => {
        const sentKey = `${sourceScreen}:${key}`;
        if (sent.current.has(sentKey)) return;
        entry.node.measureInWindow((x, y, width, height) => {
          if (sent.current.has(sentKey)) return;
          if (visibleFraction({ x, y, width, height }, windowRef.current) < IMPRESSION_MIN_VISIBLE) return;
          sent.current.add(sentKey);
          sendAppEvent({
            type: 'recommendation_impression',
            accessToken,
            requestId: entry.requestId,
            payload: { placeId: entry.placeId, sourceScreen },
            requiresConsent: false,
          });
        });
      });
    };
    check();
    const timer = setInterval(check, IMPRESSION_CHECK_MS);
    return () => clearInterval(timer);
  }, [active, accessToken, sourceScreen]);

  return { register };
}

/** 추천 카드를 감싸 노출을 잰다. 틀은 보이지 않고, 크기·배치는 안의 카드 그대로다. */
export function ImpressionView({ tracker, placeId, requestId, children }: {
  tracker: ImpressionTracker;
  placeId: string;
  requestId: string | null | undefined;
  children: ReactNode;
}) {
  return <View ref={tracker.register(placeId, requestId)} collapsable={false}>{children}</View>;
}
