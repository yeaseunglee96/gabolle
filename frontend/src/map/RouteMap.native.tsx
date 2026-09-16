// 앱(폰)의 지도 — S15P21E201-1140.
//
// 🔴 **폰에는 지도가 없었다.** `RouteMap.tsx` 가 `Platform.OS === 'web'` 일 때만 지도를 그리고,
//    폰에서는 「앱 지도 연동을 준비하고 있어요」 라는 자리표시자가 나왔다. 사용자 보고
//    3번(「앱 지도가 안 돼」)은 연동이 끊긴 것이 아니라 **만든 적이 없던 것**이다.
//
// 🔴 이 파일은 `RouteMap.native.tsx` 다. 이름 가운데의 `.native` 는 **번들러가 폰에서만 이
//    파일을 쓴다는 표시**다 — 웹은 옆의 `RouteMap.tsx` 를 그대로 쓴다. 웹 지도(카카오)는
//    잘 돌고 있으므로 건드리지 않는다.
//
// 🔴 왜 카카오가 아니라 `react-native-maps` 인가.
//    · **iOS 는 키가 아예 필요 없다** — 애플 지도를 쓴다. 키를 받는 절차 없이 아이폰에서
//      바로 지도가 뜬다. 이게 결정적이었다
//    · 카카오는 폰용 SDK 가 없어 WebView 안에 웹 지도를 넣어야 하고, 그러면 웹에 쓰는 키가
//      EAS 빌드에도 들어가야 한다. 지금 `eas.json` 에는 그 값이 없다
//
// 🔴 안드로이드만 Google Maps 키가 필요하다. 없으면 **회색 네모**가 뜨는데, 회색 네모는
//    사용자에게 "고장났다" 로 읽힌다. 그래서 키가 없으면 지도 대신 이유를 적는다.
import { useEffect, useMemo, useRef } from 'react';
import { StyleSheet, View } from 'react-native';
import MapView, { Marker, Polyline, type LatLng } from 'react-native-maps';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

import { androidMapKeyMissing } from './androidMapKey';

import type { MapStop } from './types';

export type MapRouteLayer = { id: string; color: string; stops: MapStop[] };
export type MapPointLayer = { id: string; label: string; color: string; stops: MapStop[] };
export type CurrentLocation = { latitude: number; longitude: number };

type RouteMapProps = {
  stops: MapStop[];
  selectedId: string;
  onSelect: (id: string) => void;
  routes?: MapRouteLayer[];
  points?: MapPointLayer[];
  currentLocation?: CurrentLocation | null;
  onBackToList?: () => void;
  height?: number;
};

/**
 * 🔴 기본값 배열을 **파일 수준에서 한 번만** 만든다. 함수 시그니처에 `points = []` 라고
 * 쓰면 이 컴포넌트가 다시 그려질 때마다 새 배열이 생기고, 그게 `useMemo`·`useEffect` 의
 * 의존성으로 들어가 매번 다시 돈다. 웹 지도가 같은 실수로 무한 루프에 빠진 적이 있다
 * (S15P21E201-435). 여기서 되풀이하지 않는다.
 */
const NO_POINT_LAYERS: MapPointLayer[] = [];

const coordinate = (stop: MapStop): LatLng => ({ latitude: stop.latitude, longitude: stop.longitude });

export function RouteMap({
  stops,
  selectedId,
  onSelect,
  routes,
  points = NO_POINT_LAYERS,
  currentLocation,
  onBackToList,
  height = 340,
}: RouteMapProps) {
  const { tx } = useI18n();
  const mapRef = useRef<MapView | null>(null);
  const visibleStops = useMemo(() => [...stops, ...points.flatMap((layer) => layer.stops)], [points, stops]);
  const keyMissing = androidMapKeyMissing();

  useEffect(() => {
    if (keyMissing || !visibleStops.length) return;
    // 지도가 자리를 잡기 전에 범위를 맞추면 아무 데도 안 맞는다. 한 프레임 뒤에 맞춘다.
    const timer = setTimeout(() => {
      // 🔴 장소가 하나면 범위의 넓이가 0 이라 지도가 최대 배율로 튄다 — 마커가 화면을 덮어
      //    어디인지 알 수 없게 된다. 웹 지도가 S15P21E201-919 에서 같은 것을 겪었다.
      if (visibleStops.length === 1) {
        mapRef.current?.animateCamera({ center: coordinate(visibleStops[0]), zoom: 14 }, { duration: 0 });
        return;
      }
      mapRef.current?.fitToCoordinates(visibleStops.map(coordinate), {
        edgePadding: { top: 56, right: 44, bottom: 56, left: 44 },
        animated: false,
      });
    }, 120);
    return () => clearTimeout(timer);
  }, [keyMissing, visibleStops]);

  useEffect(() => {
    if (keyMissing) return;
    const selected = visibleStops.find((stop) => stop.id === selectedId);
    if (selected) mapRef.current?.animateCamera({ center: coordinate(selected) }, { duration: 280 });
  }, [keyMissing, selectedId, visibleStops]);

  // 🔴 키가 없으면 회색 네모 대신 이유를 적는다. 고칠 사람이 읽는 한 줄도 화면에 낸다 —
  //    웹 지도가 같은 방식으로 원인을 말하고, 그 덕에 2026-09-07 배포본의 원인을 찾았다.
  if (keyMissing) {
    return (
      <View style={[styles.fallback, { minHeight: height }]}>
        <Text variant="title" weight="bold">{tx('이 빌드에 안드로이드 지도 키가 안 들어갔어요', 'This build has no Android map key')}</Text>
        <Text variant="body" style={styles.description}>
          {tx(
            '안드로이드에서 지도를 그리려면 빌드할 때 구글 지도 키가 함께 들어가야 합니다. 방문 순서와 장소 목록은 아래에서 그대로 볼 수 있어요.',
            'Drawing a map on Android needs a Google Maps key baked in at build time. You can still use the visit order and place list below.',
          )}
        </Text>
        <Text variant="caption" style={styles.tech}>GOOGLE_MAPS_ANDROID_API_KEY = (빈 값) · EAS 빌드 환경 변수로 넣어야 한다</Text>
        {onBackToList ? <Button label={tx('목록으로 돌아가기', 'Back to list')} variant="ghost" onPress={onBackToList} /> : null}
      </View>
    );
  }

  // 그릴 것이 없으면 빈 자리만 둔다. 없는 지도를 그리려다 첫 좌표를 읽고 터지지 않게 한다.
  if (!stops.length) return <View style={[styles.empty, { height }]} />;

  const routeLayers = routes ?? [{ id: 'selected', color: color.brand.orange, stops }];

  return (
    <View style={[styles.shell, { height }]}>
      <MapView
        ref={mapRef}
        style={styles.map}
        initialRegion={{ ...coordinate(stops[0]), latitudeDelta: 0.08, longitudeDelta: 0.08 }}
        showsCompass
        showsScale
        showsUserLocation={Boolean(currentLocation)}
        toolbarEnabled={false}
      >
        {routeLayers.map((route) => (route.stops.length > 1 ? (
          <Polyline key={route.id} coordinates={route.stops.map(coordinate)} strokeColor={route.color} strokeWidth={5} />
        ) : null))}
        {visibleStops.map((stop) => {
          const layer = points.find((item) => item.stops.some((entry) => entry.id === stop.id));
          return (
            <Marker
              key={stop.id}
              coordinate={coordinate(stop)}
              title={stop.name}
              // 🔴 순서가 없는 점(주변 장소 같은 것)에 번호를 붙이지 않는다. 방문 순서가 아닌데
              //    숫자를 보여주면 "1번부터 가면 되는구나" 로 읽힌다.
              description={layer ? layer.label : tx(`${stop.number}번째 방문`, `Stop ${stop.number}`)}
              pinColor={stop.id === selectedId ? color.brand.orange : (layer?.color ?? color.brand.navy)}
              onPress={() => onSelect(stop.id)}
            />
          );
        })}
      </MapView>
      {onBackToList ? (
        <View style={styles.backRow}>
          <Button label={tx('목록으로 돌아가기', 'Back to list')} variant="ghost" onPress={onBackToList} />
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  shell: { width: '100%', borderRadius: radius.lg, overflow: 'hidden', backgroundColor: color.surface.soft },
  map: { width: '100%', height: '100%' },
  backRow: { position: 'absolute', left: spacing[3], bottom: spacing[3] },
  empty: { width: '100%', borderRadius: radius.lg, backgroundColor: color.surface.soft },
  fallback: {
    width: '100%', borderRadius: radius.lg, backgroundColor: color.surface.soft,
    borderWidth: 1, borderColor: color.surface.field,
    alignItems: 'center', justifyContent: 'center', padding: spacing[6], gap: spacing[2],
  },
  description: { color: color.text.body, textAlign: 'center', maxWidth: 420 },
  // 고칠 사람이 읽는 한 줄. 여행자에게는 작고 흐리게 보인다.
  tech: { color: color.text.muted, textAlign: 'center', maxWidth: 460 },
});
