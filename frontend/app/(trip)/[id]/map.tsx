// 12 지도·동선 — 발표의 핵심 화면.
//
// 웹은 카카오 지도 JavaScript SDK를 사용하고, 앱은 SDK 결정 전까지 목록과 동선 요약을
// 유지한다. 지도 키가 없거나 SDK 로딩에 실패해도 흰 화면 대신 같은 목록을 계속 제공한다.
//
// 🔴 비교 카드의 숫자는 실제 측정값이다. 반올림하거나 다듬지 않는다.
// 🔴 이 앱은 "모르는 것을 아는 척하지 않는다" 는 원칙(PASS/FAIL/UNKNOWN)을 따른다.
//    그래서 판정이 안 된 구간이 있다는 것도 숨기지 않고 UNKNOWN 으로 그대로 보여준다.
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'expo-router';
import { AppState, Platform, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import * as Location from 'expo-location';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Card } from '@/components/Card';
import { Button } from '@/components/Button';
import { RouteMap, type CurrentLocation } from '@/map/RouteMap';
import { city3dUrlForStops, openCity3D } from '@/map/city3d';

const pinIcon = require('../../../assets/icons/common/pin.png');
import type { MapStop } from '@/map/types';
import { PermissionRationale } from '@/components/PermissionRationale';
import { useI18n } from '@/i18n';
import { isAtLeast, widthTier } from '@/layout/breakpoints';

type RouteStatus = 'pass' | 'fail' | 'neutral';

type RouteRowSeed = {
  labelKo: string; labelEn: string;
  distance: string;
  noteKo: string; noteEn: string;
  status: RouteStatus;
};

type RouteComparisonSeed = {
  titleKo: string; titleEn: string;
  subtitleKo: string; subtitleEn: string;
  rows: RouteRowSeed[];
  deltaKo: string; deltaEn: string;
};

// 🔴 아래 숫자는 실제 측정값이다. 번역하며 반올림하거나 다듬지 않는다.
const SHADE_ROUTE: RouteComparisonSeed = {
  titleKo: '그늘 경로', titleEn: 'Shaded route',
  subtitleKo: '해운대 → 광안리, 오후 5시 (실측 2026-09-01)', subtitleEn: 'Haeundae → Gwangalli, 5 PM (measured 2026-09-01)',
  rows: [
    { labelKo: '최단 경로', labelEn: 'Shortest route', distance: '2,703m', noteKo: '그늘 15.9%', noteEn: '15.9% shaded', status: 'neutral' },
    { labelKo: '그늘 우선', labelEn: 'Shade-first route', distance: '2,915m', noteKo: '그늘 30.8%', noteEn: '30.8% shaded', status: 'pass' },
  ],
  deltaKo: '212m(5.2%) 더 걸어 그늘이 두 배', deltaEn: 'Walk 212m (5.2%) more for twice the shade',
};

const WHEELCHAIR_ROUTE: RouteComparisonSeed = {
  titleKo: '휠체어 경로', titleEn: 'Wheelchair route',
  subtitleKo: '좌수영교 → 신세계 센텀시티 (실측)', subtitleEn: 'Jwasuyeong Bridge → Shinsegae Centum City (measured)',
  rows: [
    { labelKo: '최단 경로', labelEn: 'Shortest route', distance: '1,489m', noteKo: '접근 불가 구간 2개(4m) 포함', noteEn: 'Includes 2 inaccessible segments (4m)', status: 'fail' },
    { labelKo: '휠체어 최선', labelEn: 'Best for wheelchair', distance: '1,749m', noteKo: '불가 구간 0개', noteEn: '0 inaccessible segments', status: 'pass' },
  ],
  deltaKo: '4m 짜리 계단을 피하려고 260m(17%)를 돌아간다', deltaEn: 'Detours 260m (17%) to avoid a 4m staircase',
};

const STATUS_COLOR: Record<RouteStatus, string> = {
  pass: color.state.success,
  fail: color.state.danger,
  neutral: color.text.body,
};

type MapStopSeed = { id: string; number: number; nameKo: string; nameEn: string; latitude: number; longitude: number };

const DAY_STOPS_SEED: Record<'DAY 1' | 'DAY 2', MapStopSeed[]> = {
  'DAY 1': [
    { id: 'songdo', number: 1, nameKo: '송도 해상 케이블카', nameEn: 'Songdo Marine Cable Car', latitude: 35.0764, longitude: 129.0239 },
    { id: 'nampo', number: 2, nameKo: '남포동 로컬 맛집', nameEn: 'Nampo-dong local eatery', latitude: 35.0987, longitude: 129.0304 },
    { id: 'huinnyeoul', number: 3, nameKo: '흰여울문화마을', nameEn: 'Huinnyeoul Culture Village', latitude: 35.0788, longitude: 129.0444 },
    { id: 'gwangalli', number: 4, nameKo: '광안리 해수욕장', nameEn: 'Gwangalli Beach', latitude: 35.1532, longitude: 129.1187 },
  ],
  'DAY 2': [
    { id: 'haeundae', number: 1, nameKo: '해운대 해수욕장', nameEn: 'Haeundae Beach', latitude: 35.1587, longitude: 129.1604 },
    { id: 'blueline', number: 2, nameKo: '해운대 블루라인파크', nameEn: 'Haeundae Blueline Park', latitude: 35.1611, longitude: 129.171 },
    { id: 'centum', number: 3, nameKo: '센텀시티', nameEn: 'Centum City', latitude: 35.1699, longitude: 129.1291 },
  ],
};

const DAY_COLORS = { 'DAY 1': color.brand.orange, 'DAY 2': color.state.success } as const;
const EXTRA_STOPS_SEED = {
  souvenir: [
    { id: 'souvenir-nampo', number: 1, nameKo: '남포동 부산 기념품점', nameEn: 'Nampo-dong Busan souvenir shop', latitude: 35.0979, longitude: 129.0298 },
    { id: 'souvenir-haeundae', number: 2, nameKo: '해운대 로컬 숍', nameEn: 'Haeundae local shop', latitude: 35.1594, longitude: 129.1591 },
  ],
  night: [
    { id: 'night-gwangalli', number: 1, nameKo: '광안대교 야경', nameEn: 'Gwangan Bridge night view', latitude: 35.1531, longitude: 129.1189 },
    { id: 'night-thebay', number: 2, nameKo: '더베이101 야경', nameEn: 'The Bay 101 night view', latitude: 35.1567, longitude: 129.1522 },
  ],
} satisfies Record<string, MapStopSeed[]>;

function localizeStops(tx: (ko: string, en: string) => string, seeds: MapStopSeed[]): MapStop[] {
  return seeds.map((seed) => ({ id: seed.id, number: seed.number, name: tx(seed.nameKo, seed.nameEn), latitude: seed.latitude, longitude: seed.longitude }));
}

function ComparisonCard({ route }: { route: RouteComparisonSeed }) {
  const { tx } = useI18n();
  return (
    <Card tinted style={styles.comparisonCard}>
      <Text variant="title" weight="bold">
        {tx(route.titleKo, route.titleEn)}
      </Text>
      <Text variant="caption" style={styles.comparisonSubtitle}>
        {tx(route.subtitleKo, route.subtitleEn)}
      </Text>

      <View style={styles.rows}>
        {route.rows.map((row) => (
          <View key={row.labelKo} style={styles.row}>
            <Text variant="body" weight="medium" style={styles.rowLabel}>
              {tx(row.labelKo, row.labelEn)}
            </Text>
            <Text variant="body" weight="bold" style={styles.rowDistance}>
              {row.distance}
            </Text>
            <Text variant="caption" weight="bold" color={STATUS_COLOR[row.status]} style={styles.rowNote}>
              {tx(row.noteKo, row.noteEn)}
            </Text>
          </View>
        ))}
      </View>

      <View style={styles.deltaBox}>
        <Text variant="caption" weight="bold" color={color.text.eyebrow}>
          → {tx(route.deltaKo, route.deltaEn)}
        </Text>
      </View>
    </Card>
  );
}

export default function Map() {
  const router = useRouter();
  const { width } = useWindowDimensions();
  const { tx } = useI18n();
  const [day, setDay] = useState<'DAY 1' | 'DAY 2'>('DAY 1');
  const [routeScope, setRouteScope] = useState<'selected' | 'all'>('selected');
  const [showSouvenirs, setShowSouvenirs] = useState(false);
  const [showNight, setShowNight] = useState(false);
  const stops = useMemo(() => localizeStops(tx, DAY_STOPS_SEED[day]), [day, tx]);
  const [selectedId, setSelectedId] = useState(stops[0].id);
  const [locationPermission, setLocationPermission] = useState<'checking' | 'undetermined' | 'granted' | 'denied'>(Platform.OS === 'web' ? 'granted' : 'checking');
  const [requestingLocation, setRequestingLocation] = useState(false);
  const [currentLocation, setCurrentLocation] = useState<CurrentLocation | null>(null);

  // 웹은 이 화면에서 위치 권한을 앞서 확인하지 않고(위 locationPermission 이 그대로 'granted'인
  // 이유) 브라우저 Geolocation API 를 직접 부른다 — 프롬프트는 브라우저가 알아서 띄운다.
  // 거부해도 실패 콜백만 조용히 무시한다: 현재 위치 점만 빠지고 경로·단계별 안내는
  // 그대로 보여야 한다(완료 기준 1·3).
  useEffect(() => {
    if (Platform.OS !== 'web' || typeof navigator === 'undefined' || !navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (position) => setCurrentLocation({ latitude: position.coords.latitude, longitude: position.coords.longitude }),
      () => {},
    );
  }, []);

  useEffect(() => {
    if (Platform.OS === 'web') return;
    const refreshPermission = () => void Location.getForegroundPermissionsAsync()
      .then((result) => setLocationPermission(result.granted ? 'granted' : result.status === 'denied' ? 'denied' : 'undetermined'))
      .catch(() => setLocationPermission('undetermined'));
    refreshPermission();
    const subscription = AppState.addEventListener('change', (state) => { if (state === 'active') refreshPermission(); });
    return () => subscription.remove();
  }, []);

  async function requestLocation() {
    setRequestingLocation(true);
    try {
      const result = await Location.requestForegroundPermissionsAsync();
      setLocationPermission(result.granted ? 'granted' : 'denied');
    } catch {
      setLocationPermission('denied');
    } finally {
      setRequestingLocation(false);
    }
  }
  const routes = useMemo(() => routeScope === 'all'
    ? (Object.keys(DAY_STOPS_SEED) as Array<keyof typeof DAY_STOPS_SEED>).map((key) => ({ id: key, color: DAY_COLORS[key], stops: localizeStops(tx, DAY_STOPS_SEED[key]) }))
    : [{ id: day, color: DAY_COLORS[day], stops }], [day, routeScope, stops, tx]);
  const points = useMemo(() => [
    ...(showSouvenirs ? [{ id: 'souvenir', label: tx('선물', 'Souvenirs'), color: color.text.eyebrow, stops: localizeStops(tx, EXTRA_STOPS_SEED.souvenir) }] : []),
    ...(showNight ? [{ id: 'night', label: tx('야경', 'Night views'), color: color.brand.navy, stops: localizeStops(tx, EXTRA_STOPS_SEED.night) }] : []),
  ], [showNight, showSouvenirs, tx]);

  const selectDay = (nextDay: 'DAY 1' | 'DAY 2') => {
    setDay(nextDay);
    setSelectedId(DAY_STOPS_SEED[nextDay][0].id);
  };
  const selectStopFromMap = useCallback((id: string) => {
    setSelectedId(id);
    if (Platform.OS !== 'web') return;
    requestAnimationFrame(() => {
      const card = document.getElementById(`map-stop-${id}`);
      card?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      card?.focus({ preventScroll: true });
    });
  }, []);

  const LayerControls = () => <View accessibilityLabel={tx('지도 겹쳐 보기', 'Map overlays')} style={[styles.layerPanel, widthTier(width) === 'sm' && styles.layerSheet]}>
    <View style={styles.layerHeading}><View><Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('겹쳐 보기', 'Overlays')}</Text><Text variant="caption" color={color.text.muted}>{tx('지도에 함께 볼 정보를 골라요', 'Choose what to show on the map')}</Text></View><Text variant="caption" weight="bold">{routeScope === 'all' ? tx('전체 동선', 'All routes') : day}</Text></View>
    <View style={styles.layerOptions}>
      <Pressable accessibilityRole="radio" accessibilityState={{ checked: routeScope === 'selected' }} onPress={() => setRouteScope('selected')} style={[styles.layerChip, routeScope === 'selected' && styles.layerChipActive]}><Text variant="caption" weight="bold" color={routeScope === 'selected' ? color.text.onAction : color.text.body}>{tx('선택 날짜만', 'Selected day only')}</Text></Pressable>
      <Pressable accessibilityRole="radio" accessibilityState={{ checked: routeScope === 'all' }} onPress={() => setRouteScope('all')} style={[styles.layerChip, routeScope === 'all' && styles.layerChipActive]}><Text variant="caption" weight="bold" color={routeScope === 'all' ? color.text.onAction : color.text.body}>{tx('전체 동선', 'All routes')}</Text></Pressable>
      <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: showSouvenirs }} onPress={() => setShowSouvenirs((value) => !value)} style={[styles.layerChip, showSouvenirs && styles.layerChipActive]}><Text variant="caption" weight="bold" color={showSouvenirs ? color.text.onAction : color.text.body}>{tx('기념품샵', 'Souvenir shops')}</Text></Pressable>
      <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: showNight }} onPress={() => setShowNight((value) => !value)} style={[styles.layerChip, showNight && styles.layerChipActive]}><Text variant="caption" weight="bold" color={showNight ? color.text.onAction : color.text.body}>{tx('야경 명소', 'Night view spots')}</Text></Pressable>
    </View>
    {routeScope === 'all' ? <View style={styles.legend}><Text variant="caption" color={DAY_COLORS['DAY 1']}>● DAY 1</Text><Text variant="caption" color={DAY_COLORS['DAY 2']}>● DAY 2</Text></View> : null}
  </View>;

  return (
    <Screen scroll wide>
      <View style={styles.headerRow}>
        <View>
          <Text variant="caption">{tx(`${day} · ${stops.length}곳`, `${day} · ${stops.length} places`)}</Text>
          <Text variant="display" weight="bold" style={styles.title}>{tx('여행 지도', 'Trip map')}</Text>
        </View>
        <View accessibilityRole="tablist" style={styles.dayToggle}>
          {(['DAY 1', 'DAY 2'] as const).map((option) => (
            <Pressable key={option} accessibilityRole="tab" accessibilityState={{ selected: day === option }} onPress={() => selectDay(option)} style={[styles.dayOption, day === option && styles.dayOptionSelected]}>
              <Text variant="caption" weight="bold" color={day === option ? color.text.onAction : color.text.muted}>{option}</Text>
            </Pressable>
          ))}
        </View>
      </View>

      {locationPermission !== 'granted' && (
        <PermissionRationale
          icon={pinIcon}
          title={tx('현재 위치로 길을 안내할까요?', 'Use your location to guide you?')}
          description={tx('여행 중 가까운 장소와 출발 경로를 안내할 때만 위치를 사용해요. 허용하지 않아도 일정 지도는 볼 수 있어요.', 'We only use your location to guide you to nearby places and starting routes during your trip. You can still view the itinerary map without allowing it.')}
          denied={locationPermission === 'denied'}
          busy={locationPermission === 'checking' || requestingLocation}
          actionLabel={tx('현재 위치 사용', 'Use current location')}
          onRequest={() => void requestLocation()}
        />
      )}

      <View style={styles.mapStage}>
        <RouteMap stops={stops} selectedId={selectedId} onSelect={selectStopFromMap} routes={routes} points={points} currentLocation={currentLocation} onBackToList={() => router.back()} height={widthTier(width) === 'sm' ? 420 : 600} />
        {isAtLeast(width, 'md') ? <View style={styles.floatingLayers}><LayerControls /></View> : null}
      </View>
      {widthTier(width) === 'sm' ? <LayerControls /> : null}

      {/* 3D 도시로 가는 문. 웹 지도가 살아 있든 죽어 있든, 앱이든 웹이든 여기서 열린다.
          3D 화면은 웹 페이지 한 장이라 앱에 새 부품을 하나도 안 깐다 (S15P21E201-649). */}
      <Card tinted style={styles.city3dCard}>
        <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('가보기 전에', 'Before you go')}</Text>
        <Text variant="title" weight="bold">{tx('이 날짜의 장소를 3D 부산에서 보기', "See today's places in 3D Busan")}</Text>
        <Text variant="caption" color={color.text.muted} style={styles.city3dBody}>
          {tx('실제 건물과 땅 높이 위에서 봅니다. 해 위치를 옮기면 그 시각의 그림자가 같이 움직여요.', 'Rendered on real buildings and terrain. Move the sun and the shadows for that hour move with it.')}
        </Text>
        <Button
          label={tx('3D 로 보기', 'View in 3D')}
          variant="secondary"
          containerStyle={styles.city3dButton}
          onPress={() => { void openCity3D(city3dUrlForStops(stops)); }}
        />
      </Card>

      <View style={styles.stopsList}>
        <Text variant="title" weight="bold" style={styles.masterTitle}>{tx('선택 날짜 장소', 'Places for the selected day')}</Text>
        {stops.map((stop) => <Pressable nativeID={`map-stop-${stop.id}`} accessibilityRole="button" accessibilityState={{ selected: selectedId === stop.id }} onPress={() => setSelectedId(stop.id)} key={stop.id} style={[styles.stopRow, selectedId === stop.id && styles.stopRowSelected]}><View style={styles.stopMarker}><Text variant="caption" weight="bold" color={color.text.onAction}>{stop.number}</Text></View><Text variant="body" weight="bold" style={styles.stopName}>{stop.name}</Text><Text variant="caption" color={color.text.muted}>{tx('지도에서 보기', 'View on map')}</Text></Pressable>)}
      </View>

      <Text variant="title" weight="bold" style={styles.sectionTitle}>{tx('실측 경로 비교', 'Measured route comparison')}</Text>

      <View style={styles.comparisons}><ComparisonCard route={SHADE_ROUTE} /><ComparisonCard route={WHEELCHAIR_ROUTE} /></View>

      <View style={styles.unknownNote}><Text variant="caption" weight="bold" color={color.text.muted}>UNKNOWN</Text><Text variant="caption" style={styles.unknownBody}>{tx('일부 구간은 데이터가 없어 판정하지 않았습니다. 모르는 것을 아는 척하지 않습니다.', "Some segments weren't judged due to missing data. We don't pretend to know what we don't.")}</Text></View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: {
    marginTop: spacing[1],
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing[4],
  },
  dayToggle: {
    flexDirection: 'row',
    backgroundColor: color.surface.soft,
    borderRadius: radius.full,
    padding: spacing[1],
  },
  dayOption: {
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
    borderRadius: radius.full,
  },
  dayOptionSelected: {
    backgroundColor: color.brand.orange,
  },
  city3dCard: {
    marginTop: spacing[4],
    gap: spacing[1],
  },
  city3dBody: {
    marginTop: spacing[1],
  },
  city3dButton: {
    marginTop: spacing[3],
  },
  stopsList: {
    gap: spacing[3],
  },
  masterTitle: {
    marginBottom: spacing[1],
  },
  stopRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
    padding: spacing[3],
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: color.surface.field,
  },
  stopRowSelected: {
    borderColor: color.brand.orange,
    backgroundColor: color.state.dangerBg,
  },
  stopName: {
    flex: 1,
  },
  stopMarker: {
    width: 28,
    height: 28,
    borderRadius: radius.full,
    backgroundColor: color.brand.orange,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sectionTitle: {
    marginTop: spacing[8],
  },
  comparisons: {
    marginTop: spacing[4],
    gap: spacing[4],
  },
  comparisonCard: {
    gap: spacing[1],
  },
  comparisonSubtitle: {
    color: color.text.eyebrow,
    marginBottom: spacing[2],
  },
  rows: {
    gap: spacing[2],
    marginTop: spacing[1],
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
  },
  rowLabel: {
    width: 84,
  },
  rowDistance: {
    width: 68,
  },
  rowNote: {
    flex: 1,
  },
  deltaBox: {
    marginTop: spacing[3],
    backgroundColor: color.surface.card,
    borderRadius: radius.sm,
    padding: spacing[3],
  },
  unknownNote: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing[2],
    marginTop: spacing[6],
    backgroundColor: color.surface.field,
    borderRadius: radius.md,
    padding: spacing[4],
  },
  unknownBody: {
    flex: 1,
    color: color.text.body,
  },
  mapStage: { position: 'relative' },
  floatingLayers: { position: 'absolute', right: spacing[3], bottom: spacing[3], width: 310, maxWidth: '80%' },
  layerPanel: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, shadowColor: color.brand.navy, shadowOpacity: 0.16, shadowRadius: 18, shadowOffset: { width: 0, height: 8 }, elevation: 8 },
  layerSheet: { marginTop: spacing[3], borderBottomLeftRadius: 0, borderBottomRightRadius: 0, shadowOpacity: 0 },
  layerHeading: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] },
  layerOptions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  layerChip: { minHeight: 44, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  layerChipActive: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  legend: { flexDirection: 'row', gap: spacing[3] },
});
