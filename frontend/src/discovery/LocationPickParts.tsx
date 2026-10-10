// 위치 고르기 지도의 공통 조각 — 웹·앱 두 판이 같이 쓴다(S15P21E201-2031).
import { StyleSheet, View } from 'react-native';

import { color } from '@/design/tokens';

export type LatLng = { lat: number; lng: number };

export type LocationPickMapProps = {
  /** 처음 보여 줄 가운데. */
  initial: LatLng;
  /** 바뀌면 지도를 그 자리로 옮긴다(「내 위치로」). */
  moveTo?: LatLng | null;
  /** 지도가 멈출 때마다 가운데 좌표 — 핀이 가리키는 곳. */
  onCenter: (center: LatLng) => void;
  height?: number;
};

/**
 * 가운데에 고정된 핀. 지도를 끌어 핀 아래로 장소를 맞춘다 — 눌러서 찍는 방식은 손가락이 핀을 가려 어디를 찍었는지 안 보인다.
 * 바늘 끝이 정확히 가운데에 오도록 몸통을 바늘 길이만큼 올린다.
 */
export function CenterPin() {
  return (
    <View pointerEvents="none" style={styles.wrap}>
      <View style={styles.head} />
      <View style={styles.needle} />
    </View>
  );
}

const HEAD = 22;
const NEEDLE = 12;

const styles = StyleSheet.create({
  wrap: { position: 'absolute', left: '50%', top: '50%', width: HEAD, height: HEAD + NEEDLE, marginLeft: -HEAD / 2, marginTop: -(HEAD + NEEDLE), alignItems: 'center' },
  head: { width: HEAD, height: HEAD, borderRadius: HEAD / 2, backgroundColor: color.action.primary, borderWidth: 3, borderColor: color.surface.card },
  needle: { width: 3, height: NEEDLE, backgroundColor: color.action.secondary },
});
