// 루트 지도 썸네일 — 선 + 번호 매긴 정류장 (S15P21E201-2027).
//
// 진짜 지도(카카오) 대신 SVG 로 그린다. 피드 한 쪽에 카드가 20장인데 카드마다 지도 창(WebView)을 띄우면
// 폰이 버티지 못한다. 대신 선의 모양과 순서는 정확히 그린다 — 경도는 위도에 맞춰 줄여(cos) 모양이 눌리지 않게.
import { StyleSheet, View } from 'react-native';
import Svg, { Circle, Polyline, Rect, Text as SvgText } from 'react-native-svg';

import { color } from '@/design/tokens';

import type { RoutePostStop } from './routePosts';

type Point = [number, number];

/** 위경도를 상자 안 좌표로. 내보내 시험한다. */
export function project(points: Point[], width: number, height: number, pad: number): (p: Point) => [number, number] {
  if (!points.length) return () => [width / 2, height / 2];
  const lats = points.map((p) => p[0]);
  const lngs = points.map((p) => p[1]);
  const minLat = Math.min(...lats), maxLat = Math.max(...lats);
  const minLng = Math.min(...lngs), maxLng = Math.max(...lngs);
  const k = Math.cos(((minLat + maxLat) / 2) * Math.PI / 180);
  const spanX = Math.max((maxLng - minLng) * k, 1e-6);
  const spanY = Math.max(maxLat - minLat, 1e-6);
  const scale = Math.min((width - pad * 2) / spanX, (height - pad * 2) / spanY);
  const offX = (width - spanX * scale) / 2;
  const offY = (height - spanY * scale) / 2;
  return (p) => [offX + (p[1] - minLng) * k * scale, height - (offY + (p[0] - minLat) * scale)];
}

export function RouteThumb({ path, stops, height = 160, width = 320 }: { path: Point[]; stops: RoutePostStop[]; height?: number; width?: number }) {
  const all: Point[] = [...path, ...stops.map((s) => [s.lat, s.lng] as Point)];
  const at = project(all, width, height, 18);
  const line = (path.length >= 2 ? path : stops.map((s) => [s.lat, s.lng] as Point)).map((p) => at(p).join(',')).join(' ');
  return (
    <View style={styles.box} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <Svg width="100%" height={height} viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="xMidYMid meet">
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
    </View>
  );
}

const styles = StyleSheet.create({
  box: { width: '100%', overflow: 'hidden' },
});
