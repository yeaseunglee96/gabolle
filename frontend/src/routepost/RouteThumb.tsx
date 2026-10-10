// 루트 지도 썸네일 — 진짜 지도 위에 선 + 번호 매긴 정류장 (S15P21E201-2027).
//
// 처음에는 회색 상자에 SVG 로만 그렸다(카드마다 지도를 띄우면 무거워서). 사용자 — 「지도 위에 표시하지 않고 회색 사각형에
// 표시하니 너무 이상하다」. 그래서 카카오 지도(여행 지도와 같은 것)를 바탕에 깐다. 무게는 이렇게 막는다:
//  · 화면 가까이 온 카드만 지도를 띄운다(웹 IntersectionObserver, 앱은 위치를 재서). 앱은 멀어지면 다시 내린다.
//  · 지도는 움직이지 않는다 — 끌기·확대 없음, 누르면 카드가 열린다.
// 지도가 뜨기 전·못 뜰 때(키 없음·차단)는 SVG 그림을 그대로 보여 준다. 그림은 카드 폭을 꽉 채운다(전에는 양옆이 비었다).
import { useEffect, useMemo, useRef, useState } from 'react';
import { Dimensions, Platform, StyleSheet, View, type LayoutChangeEvent } from 'react-native';
import Svg, { Circle, Polyline, Rect, Text as SvgText } from 'react-native-svg';

import { color } from '@/design/tokens';

import { miniRouteData, project } from './miniRouteMap';

export { project };
import { RouteMapLayer } from './RouteMapLayer';
import type { RoutePostStop } from './routePosts';

type Point = [number, number];

/**
 * 화면 가까이 왔나. eager 면 늘 참(상세 화면 — 지도 하나뿐).
 * 웹은 한 번 들어오면 계속 둔다(지도 div 는 가볍다). 앱은 WebView 라 화면에서 멀어지면 내린다 — 들일 때보다
 * 내릴 때 범위를 넓게 잡아(위아래 두 화면) 경계에서 깜빡이지 않게 한다.
 */
function useNearViewport(ref: React.RefObject<View | null>, eager: boolean): boolean {
  const [near, setNear] = useState(eager);
  useEffect(() => {
    if (eager) return;
    const node = ref.current as unknown as Element | null;
    if (Platform.OS === 'web') {
      if (!node || typeof IntersectionObserver === 'undefined') { setNear(true); return; }
      const io = new IntersectionObserver((entries) => {
        if (entries.some((e) => e.isIntersecting)) { setNear(true); io.disconnect(); }
      }, { rootMargin: '300px 0px' });
      io.observe(node);
      return () => io.disconnect();
    }
    let alive = true;
    const check = () => {
      ref.current?.measureInWindow((_x, y, _w, h) => {
        if (!alive || !h) return;
        const screen = Dimensions.get('window').height;
        setNear((was) => (was ? y < screen * 3 && y + h > -screen * 2 : y < screen * 1.5 && y + h > -screen * 0.5));
      });
    };
    check();
    const timer = setInterval(check, 700);
    return () => { alive = false; clearInterval(timer); };
  }, [eager, ref]);
  return near;
}

export function RouteThumb({ path, stops, height = 160, eager = false }: { path: Point[]; stops: RoutePostStop[]; height?: number; eager?: boolean }) {
  const boxRef = useRef<View | null>(null);
  const [width, setWidth] = useState(0);
  const [map, setMap] = useState<'idle' | 'ready' | 'fail'>('idle');
  const near = useNearViewport(boxRef, eager);
  const appKey = process.env.EXPO_PUBLIC_KAKAO_MAP_JS_KEY;
  const data = useMemo(() => miniRouteData(path, stops), [path, stops]);
  // 🔴 값이 바뀌어도 'idle' 로 되돌리지 않는다 — 자식(지도)의 effect 가 부모보다 먼저 돌아, 다시 그려 'ready' 를 알린 직후
  //    부모가 'idle' 로 덮어 지도가 투명한 채 남았다(상세 화면 — 글을 다시 받아 오면 path·stops 가 새 배열이 된다).
  const onLayout = (e: LayoutChangeEvent) => {
    const w = Math.round(e.nativeEvent.layout.width);
    if (w && w !== width) setWidth(w);
  };
  const showMap = Boolean(near && data && appKey && map !== 'fail');
  return (
    <View ref={boxRef} onLayout={onLayout} style={[styles.box, { height }]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      {map !== 'ready' ? <Sketch path={path} stops={stops} width={width || 320} height={height} /> : null}
      {showMap && data && appKey ? (
        <View style={[StyleSheet.absoluteFill, map !== 'ready' && styles.hidden]} pointerEvents="none">
          <RouteMapLayer data={data} appKey={appKey} onReady={() => setMap('ready')} onFail={() => setMap('fail')} />
        </View>
      ) : null}
    </View>
  );
}

/** 지도가 뜨기 전·못 뜰 때의 그림. viewBox 를 실제 폭에 맞춰 카드 폭을 꽉 채운다. */
function Sketch({ path, stops, width, height }: { path: Point[]; stops: RoutePostStop[]; width: number; height: number }) {
  const all: Point[] = [...path, ...stops.map((s) => [s.lat, s.lng] as Point)];
  const at = project(all, width, height, 22);
  const line = (path.length >= 2 ? path : stops.map((s) => [s.lat, s.lng] as Point)).map((p) => at(p).join(',')).join(' ');
  return (
    <Svg width="100%" height={height} viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="xMidYMid slice" style={StyleSheet.absoluteFill}>
      <Rect x={0} y={0} width={width} height={height} fill={color.surface.soft} />
      <Polyline points={line} fill="none" stroke={color.brand.navy} strokeWidth={3} strokeLinejoin="round" strokeLinecap="round" opacity={0.85} />
      {stops.map((stop, index) => {
        const [x, y] = at([stop.lat, stop.lng]);
        return [
          <Circle key={`${stop.seq}-c`} cx={x} cy={y} r={10} fill={color.action.primary} stroke={color.surface.card} strokeWidth={2} />,
          <SvgText key={`${stop.seq}-n`} x={x} y={y + 4} fontSize={11} fontWeight="bold" fill={color.text.onAction} textAnchor="middle">{String(index + 1)}</SvgText>,
        ];
      })}
    </Svg>
  );
}

const styles = StyleSheet.create({
  box: { width: '100%', overflow: 'hidden', backgroundColor: color.surface.soft },
  hidden: { opacity: 0 },
});
