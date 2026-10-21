// 장소 상세의 «근처 주차장» 한 줄 (S15P21E201-2052).
//
// 이 장소 자리에서 700m 안의 주차장을 «1시간 기준»으로 서버에 물어, 요금을 아는 곳 중 가장 싼 곳의 요금과 거리를 한 줄로 보인다.
// 누르면 「근처 편의시설」 화면의 주차장 탭이 이 장소 기준으로 열린다(같은 1시간으로 — 줄과 목록의 숫자가 같다).
//
// 🔴 이 줄은 «있으면 덤»이다. 불러오는 중이거나, 실패했거나, 0곳이면 **아무것도 그리지 않는다** — 자리를 비워 두지도, «불러오는 중…»을
//    걸어 두지도 않는다. 장소 상세는 이 줄 없이도 완전해야 하고, 서버가 느리거나 자료가 없는 곳에서 안내가 영원히 걸려 있으면 안 된다.
// 🔴 요금을 모르는 곳은 «가장 싼 곳»이 아니다(요금 모름 ≠ 0원). 그 판정은 parkingLineOf 가 한다.
// 🔴 이 장소의 좌표를 줄이지 않고 보낸다(exact) — 100m 로 줄이는 약속은 «내 위치»의 것이고, 줄이면 «이 장소에서 몇 m»가 틀어진다.
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import {
  DEFAULT_RADIUS_M,
  formatWon,
  getNearbyFacilities,
  PARKING_LINE_STAY_MINUTES,
  parkingLineOf,
  type ParkingLine,
} from '@/field/nearbyFacilities';
import { distanceText } from '@/field/subwayStations';
import { useI18n } from '@/i18n';
import { enPlural, txf } from '@/i18n/format';

type Props = {
  lat: number;
  lng: number;
  /** 화면에 그려진 그 장소의 이름(고른 언어로) — 열리는 화면이 «「이름」 근처에서 찾았어요»라고 밝힌다 */
  placeName: string;
  accessToken: string | null;
};

type Loaded = { key: string; line: ParkingLine | null };

/** 좌표가 쓸 만한가 — 모르는 장소가 (0, 0) 으로 오는 일이 있어 그것도 거른다. */
function usable(lat: number, lng: number): boolean {
  return Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180 && !(lat === 0 && lng === 0);
}

export function NearbyParkingLine({ lat, lng, placeName, accessToken }: Props) {
  const router = useRouter();
  const { tx } = useI18n();
  // 어느 좌표의 답인지 함께 들고 있는다 — 장소가 바뀐 직후 앞 장소의 줄이 잠깐 보이는 것을 막는다.
  const key = `${lat},${lng}`;
  const [loaded, setLoaded] = useState<Loaded | null>(null);

  useEffect(() => {
    if (!usable(lat, lng)) return;
    const controller = new AbortController();
    let alive = true;
    getNearbyFacilities('PARKING', { latitude: lat, longitude: lng }, accessToken, {
      radiusM: DEFAULT_RADIUS_M,
      stayMinutes: PARKING_LINE_STAY_MINUTES,
      exact: true,
      signal: controller.signal,
    })
      .then((data) => { if (alive) setLoaded({ key, line: parkingLineOf(data) }); })
      .catch(() => { if (alive) setLoaded({ key, line: null }); });
    return () => { alive = false; controller.abort(); };
  }, [lat, lng, key, accessToken]);

  const line = loaded?.key === key ? loaded.line : null;
  if (!line) return null;

  const label = tx('근처 주차장', 'Nearby parking');
  const value = (() => {
    switch (line.kind) {
      case 'fee': return txf(tx, '1시간 %s원 · %s', '₩%s for 1 hour · %s', formatWon(line.won), distanceText(line.distanceM));
      case 'free': return txf(tx, '무료 · %s', 'Free · %s', distanceText(line.distanceM));
      case 'unknown': return txf(tx, '%s곳 · 가장 가까운 곳 %s', `%s ${enPlural(line.count, 'lot', 'lots')} · nearest %s`, line.count, distanceText(line.nearestM));
    }
  })();

  const open = () => router.push({
    pathname: '/field/nearby',
    // stay=1 — 줄이 «1시간 기준»이라 열리는 화면도 1시간 칩으로 열어 같은 숫자를 보게 한다.
    params: { kind: 'PARKING', lat: String(lat), lng: String(lng), name: placeName, stay: '1' },
  });

  return (
    <View style={styles.card}>
      <Pressable testID="place-nearby-parking" accessibilityRole="link" accessibilityLabel={`${label}, ${value}`} onPress={open} style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
        <Text variant="caption" weight="bold" color={color.text.muted} style={styles.label}>{label}</Text>
        <Text testID="place-nearby-parking-value" variant="body" style={styles.value}>{value}</Text>
        <Text variant="title" color={color.text.muted}>›</Text>
      </Pressable>
    </View>
  );
}

// 모양은 장소 상세의 정보 줄(infoRows · infoRow)과 같게 — 이름표는 줄지 않고 값만 준다(S15P21E201-1727).
const styles = StyleSheet.create({
  card: { marginTop: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, overflow: 'hidden' },
  row: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], paddingVertical: spacing[3] },
  pressed: { opacity: 0.72 },
  label: { flexShrink: 0 },
  value: { flexShrink: 1, flexGrow: 1, textAlign: 'right' },
});
