// 긴급 도움 화면의 가까운 병원 지도 — 들어오자마자 보이게(S15P21E201-1934, 사용자 의견 2026-10-02 「지도도 바로 보이는 게 낫지 않나」).
//
// 🔴 전화 셋(119·112·1330)보다 위에 두지 않는다 — 이 화면은 「바로 건다」가 먼저다. 지도는 그 아래 자리.
// 🔴 위치를 여기서 묻지 않는다 — 이미 허락한 사람에게만 조용히 지도를 그린다. 처음인 사람은 지금처럼 「가까운 도움」으로
//    들어가 거기서 「내 위치 켜기」를 누른다(위치는 누른 뒤에만 묻는다는 규칙, nearby-help.tsx · transit.tsx 와 같다).
// 그래서 못 그리는 동안(허락 전·읽는 중·실패)은 원래 있던 한 줄짜리 입구를 그대로 보인다 — 빈 지도 칸을 남기지 않는다.
import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import * as Location from 'expo-location';

import { useAuth } from '@/auth/AuthProvider';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { getNearbyHelp } from '@/field/helpPlacesApi';
import { nearestHelp } from '@/field/nearbyHelp';
import { distanceText } from '@/field/subwayStations';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { readCurrentPosition } from '@/location/currentPosition';
import { RouteMap, type MapRouteLayer } from '@/map/RouteMap';
import type { MapStop } from '@/map/types';
import { syncLocationConsent } from '@/personalization/locationConsent';

const NO_ROUTES: MapRouteLayer[] = [];
/** 긴급 화면에서는 가까운 셋만 — 더 보려면 「가까운 도움」으로 */
const PREVIEW_COUNT = 3;

type Place = { name: string; latitude: number; longitude: number; distanceM: number; openNow: boolean | null };

export function NearbyHelpPreview({ testID }: { testID?: string }) {
  const router = useRouter();
  const { tx } = useI18n();
  const { accessToken, ready } = useAuth();
  const [coords, setCoords] = useState<{ latitude: number; longitude: number } | null>(null);
  const [places, setPlaces] = useState<Place[] | null>(null);

  // 이미 허락했으면 위치를 읽는다 — 묻는 창은 띄우지 않는다
  useEffect(() => {
    if (!ready) return;
    let alive = true;
    void (async () => {
      try {
        if ((await syncLocationConsent(accessToken)) !== true) return;
        const permission = await Location.getForegroundPermissionsAsync();
        if (!permission.granted) return;
        const position = await readCurrentPosition();
        if (alive) setCoords({ latitude: position.coords.latitude, longitude: position.coords.longitude });
      } catch {
        // 못 읽으면 입구 한 줄 그대로
      }
    })();
    return () => { alive = false; };
  }, [ready, accessToken]);

  // 병원 — 서버 먼저, 못 받으면 앱 자료(가까운 도움 화면과 같은 순서)
  useEffect(() => {
    if (!coords) return;
    let alive = true;
    void getNearbyHelp('hospital', coords, accessToken, PREVIEW_COUNT).then((server) => {
      if (!alive) return;
      setPlaces(server
        ? server.places.slice(0, PREVIEW_COUNT).map((place) => ({ name: place.name, latitude: place.lat, longitude: place.lng, distanceM: place.distanceMeters, openNow: place.openNow }))
        : nearestHelp('hospital', coords, PREVIEW_COUNT).map((place) => ({ name: place.name, latitude: place.latitude, longitude: place.longitude, distanceM: place.distanceM, openNow: null })));
    });
    return () => { alive = false; };
  }, [coords, accessToken]);

  const stops: MapStop[] = useMemo(() => (places ?? []).map((place, index) => ({ id: `help-${index}`, number: index + 1, name: place.name, latitude: place.latitude, longitude: place.longitude })), [places]);
  const open = () => router.push('/nearby-help');
  const nearest = places?.[0];

  if (!coords || !places || !nearest) {
    return (
      <Pressable testID={testID} accessibilityRole="link" onPress={open} style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
        <View style={styles.body}>
          <Text variant="body" weight="bold" color={color.text.heading}>{tx('가까운 병원·약국·경찰 지도', 'Hospitals, pharmacies and police nearby')}</Text>
          <Text variant="caption" color={color.text.body}>{tx('내 위치 근처를 지도로 보고, 전화하거나 길 안내를 받아요', 'See them on a map near you, then call or get directions')}</Text>
        </View>
        <Text variant="title" color={color.text.muted}>›</Text>
      </Pressable>
    );
  }

  return (
    <View testID={testID} style={styles.card}>
      <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('가까운 병원 · 지금 있는 곳에서', 'Nearest hospitals · from where you are')}</Text>
      <View style={styles.map}>
        <RouteMap stops={stops} routes={NO_ROUTES} selectedId={stops[0]?.id ?? ''} onSelect={open} currentLocation={coords} height={180} />
      </View>
      <View style={styles.nearest}>
        <View style={styles.badge}><Text variant="caption" weight="bold" color={color.text.onAction}>1</Text></View>
        <View style={styles.body}>
          <Text variant="body" weight="bold" numberOfLines={1}>{nearest.name}</Text>
          <Text variant="caption" color={nearest.openNow ? color.state.success : color.text.body}>{nearest.openNow ? txf(tx, '%s · 진료 중', '%s · open now', distanceText(nearest.distanceM)) : distanceText(nearest.distanceM)}</Text>
        </View>
      </View>
      <Pressable testID={testID ? `${testID}-open` : undefined} accessibilityRole="link" onPress={open} style={({ pressed }) => [styles.more, pressed && styles.pressed]}>
        <Text variant="util" weight="bold" color={color.text.onAction}>{tx('약국·경찰까지 지도로 보기', 'See pharmacies and police too')}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[4], marginBottom: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  card: { gap: spacing[3], padding: spacing[4], marginBottom: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  map: { borderRadius: radius.md, overflow: 'hidden' },
  nearest: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  badge: { width: 28, height: 28, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.secondary },
  body: { flex: 1, minWidth: 0, gap: 2 },
  more: { minHeight: 48, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.secondary },
  pressed: { opacity: 0.7 },
});
