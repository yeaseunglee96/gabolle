// 12 지도·동선 — 발표의 핵심 화면.
//
// 웹은 카카오 지도 JavaScript SDK를 사용하고, 앱은 SDK 결정 전까지 목록과 동선 요약을
// 유지한다. 지도 키가 없거나 SDK 로딩에 실패해도 흰 화면 대신 같은 목록을 계속 제공한다.
//
// 🔴 비교 카드의 숫자는 실제 측정값이다. 반올림하거나 다듬지 않는다.
// 🔴 이 앱은 "모르는 것을 아는 척하지 않는다" 는 원칙(PASS/FAIL/UNKNOWN)을 따른다.
//    그래서 판정이 안 된 구간이 있다는 것도 숨기지 않고 UNKNOWN 으로 그대로 보여준다.
import { useCallback, useMemo, useState } from 'react';
import { useRouter } from 'expo-router';
import { Platform, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Card } from '@/components/Card';
import { RouteMap } from '@/map/RouteMap';
import type { MapStop } from '@/map/types';

type RouteStatus = 'pass' | 'fail' | 'neutral';

type RouteRow = {
  label: string;
  distance: string;
  note: string;
  status: RouteStatus;
};

type RouteComparison = {
  title: string;
  subtitle: string;
  rows: RouteRow[];
  delta: string;
};

const SHADE_ROUTE: RouteComparison = {
  title: '그늘 경로',
  subtitle: '해운대 → 광안리, 오후 5시 (실측 2026-09-01)',
  rows: [
    { label: '최단 경로', distance: '2,703m', note: '그늘 15.9%', status: 'neutral' },
    { label: '그늘 우선', distance: '2,915m', note: '그늘 30.8%', status: 'pass' },
  ],
  delta: '212m(5.2%) 더 걸어 그늘이 두 배',
};

const WHEELCHAIR_ROUTE: RouteComparison = {
  title: '휠체어 경로',
  subtitle: '좌수영교 → 신세계 센텀시티 (실측)',
  rows: [
    { label: '최단 경로', distance: '1,489m', note: '접근 불가 구간 2개(4m) 포함', status: 'fail' },
    { label: '휠체어 최선', distance: '1,749m', note: '불가 구간 0개', status: 'pass' },
  ],
  delta: '4m 짜리 계단을 피하려고 260m(17%)를 돌아간다',
};

const STATUS_COLOR: Record<RouteStatus, string> = {
  pass: color.state.success,
  fail: color.state.danger,
  neutral: color.text.body,
};

const DAY_STOPS: Record<'DAY 1' | 'DAY 2', MapStop[]> = {
  'DAY 1': [
    { id: 'songdo', number: 1, name: '송도 해상 케이블카', latitude: 35.0764, longitude: 129.0239 },
    { id: 'nampo', number: 2, name: '남포동 로컬 맛집', latitude: 35.0987, longitude: 129.0304 },
    { id: 'huinnyeoul', number: 3, name: '흰여울문화마을', latitude: 35.0788, longitude: 129.0444 },
    { id: 'gwangalli', number: 4, name: '광안리 해수욕장', latitude: 35.1532, longitude: 129.1187 },
  ],
  'DAY 2': [
    { id: 'haeundae', number: 1, name: '해운대 해수욕장', latitude: 35.1587, longitude: 129.1604 },
    { id: 'blueline', number: 2, name: '해운대 블루라인파크', latitude: 35.1611, longitude: 129.171 },
    { id: 'centum', number: 3, name: '센텀시티', latitude: 35.1699, longitude: 129.1291 },
  ],
};

const DAY_COLORS = { 'DAY 1': color.brand.orange, 'DAY 2': color.state.success } as const;
const EXTRA_STOPS = {
  souvenir: [
    { id: 'souvenir-nampo', number: 1, name: '남포동 부산 기념품점', latitude: 35.0979, longitude: 129.0298 },
    { id: 'souvenir-haeundae', number: 2, name: '해운대 로컬 숍', latitude: 35.1594, longitude: 129.1591 },
  ],
  night: [
    { id: 'night-gwangalli', number: 1, name: '광안대교 야경', latitude: 35.1531, longitude: 129.1189 },
    { id: 'night-thebay', number: 2, name: '더베이101 야경', latitude: 35.1567, longitude: 129.1522 },
  ],
} satisfies Record<string, MapStop[]>;

function ComparisonCard({ route }: { route: RouteComparison }) {
  return (
    <Card tinted style={styles.comparisonCard}>
      <Text variant="title" weight="bold">
        {route.title}
      </Text>
      <Text variant="caption" style={styles.comparisonSubtitle}>
        {route.subtitle}
      </Text>

      <View style={styles.rows}>
        {route.rows.map((row) => (
          <View key={row.label} style={styles.row}>
            <Text variant="body" weight="medium" style={styles.rowLabel}>
              {row.label}
            </Text>
            <Text variant="body" weight="bold" style={styles.rowDistance}>
              {row.distance}
            </Text>
            <Text variant="caption" weight="bold" color={STATUS_COLOR[row.status]} style={styles.rowNote}>
              {row.note}
            </Text>
          </View>
        ))}
      </View>

      <View style={styles.deltaBox}>
        <Text variant="caption" weight="bold" color={color.text.eyebrow}>
          → {route.delta}
        </Text>
      </View>
    </Card>
  );
}

export default function Map() {
  const router = useRouter();
  const { width } = useWindowDimensions();
  const [day, setDay] = useState<'DAY 1' | 'DAY 2'>('DAY 1');
  const [routeScope, setRouteScope] = useState<'selected' | 'all'>('selected');
  const [showSouvenirs, setShowSouvenirs] = useState(false);
  const [showNight, setShowNight] = useState(false);
  const stops = DAY_STOPS[day];
  const [selectedId, setSelectedId] = useState(stops[0].id);
  const routes = useMemo(() => routeScope === 'all'
    ? (Object.keys(DAY_STOPS) as Array<keyof typeof DAY_STOPS>).map((key) => ({ id: key, color: DAY_COLORS[key], stops: DAY_STOPS[key] }))
    : [{ id: day, color: DAY_COLORS[day], stops }], [day, routeScope, stops]);
  const points = useMemo(() => [
    ...(showSouvenirs ? [{ id: 'souvenir', label: '선물', color: color.text.eyebrow, stops: EXTRA_STOPS.souvenir }] : []),
    ...(showNight ? [{ id: 'night', label: '야경', color: color.brand.navy, stops: EXTRA_STOPS.night }] : []),
  ], [showNight, showSouvenirs]);

  const selectDay = (nextDay: 'DAY 1' | 'DAY 2') => {
    setDay(nextDay);
    setSelectedId(DAY_STOPS[nextDay][0].id);
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

  const LayerControls = () => <View accessibilityLabel="지도 겹쳐 보기" style={[styles.layerPanel, width <= 599 && styles.layerSheet]}>
    <View style={styles.layerHeading}><View><Text variant="caption" weight="bold" color={color.text.eyebrow}>겹쳐 보기</Text><Text variant="caption" color={color.text.muted}>지도에 함께 볼 정보를 골라요</Text></View><Text variant="caption" weight="bold">{routeScope === 'all' ? '전체 동선' : day}</Text></View>
    <View style={styles.layerOptions}>
      <Pressable accessibilityRole="radio" accessibilityState={{ checked: routeScope === 'selected' }} onPress={() => setRouteScope('selected')} style={[styles.layerChip, routeScope === 'selected' && styles.layerChipActive]}><Text variant="caption" weight="bold" color={routeScope === 'selected' ? color.text.onAction : color.text.body}>선택 날짜만</Text></Pressable>
      <Pressable accessibilityRole="radio" accessibilityState={{ checked: routeScope === 'all' }} onPress={() => setRouteScope('all')} style={[styles.layerChip, routeScope === 'all' && styles.layerChipActive]}><Text variant="caption" weight="bold" color={routeScope === 'all' ? color.text.onAction : color.text.body}>전체 동선</Text></Pressable>
      <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: showSouvenirs }} onPress={() => setShowSouvenirs((value) => !value)} style={[styles.layerChip, showSouvenirs && styles.layerChipActive]}><Text variant="caption" weight="bold" color={showSouvenirs ? color.text.onAction : color.text.body}>기념품샵</Text></Pressable>
      <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: showNight }} onPress={() => setShowNight((value) => !value)} style={[styles.layerChip, showNight && styles.layerChipActive]}><Text variant="caption" weight="bold" color={showNight ? color.text.onAction : color.text.body}>야경 명소</Text></Pressable>
    </View>
    {routeScope === 'all' ? <View style={styles.legend}><Text variant="caption" color={DAY_COLORS['DAY 1']}>● DAY 1</Text><Text variant="caption" color={DAY_COLORS['DAY 2']}>● DAY 2</Text></View> : null}
  </View>;

  return (
    <Screen scroll wide>
      <View style={styles.headerRow}>
        <View>
          <Text variant="caption">{day} · {stops.length}곳</Text>
          <Text variant="display" weight="bold" style={styles.title}>여행 지도</Text>
        </View>
        <View accessibilityRole="tablist" style={styles.dayToggle}>
          {(['DAY 1', 'DAY 2'] as const).map((option) => (
            <Pressable key={option} accessibilityRole="tab" accessibilityState={{ selected: day === option }} onPress={() => selectDay(option)} style={[styles.dayOption, day === option && styles.dayOptionSelected]}>
              <Text variant="caption" weight="bold" color={day === option ? color.text.onAction : color.text.muted}>{option}</Text>
            </Pressable>
          ))}
        </View>
      </View>

      <View style={styles.mapStage}>
        <RouteMap stops={stops} selectedId={selectedId} onSelect={selectStopFromMap} routes={routes} points={points} onBackToList={() => router.back()} height={width <= 599 ? 420 : 600} />
        {width > 599 ? <View style={styles.floatingLayers}><LayerControls /></View> : null}
      </View>
      {width <= 599 ? <LayerControls /> : null}

      <View style={styles.stopsList}>
        <Text variant="title" weight="bold" style={styles.masterTitle}>선택 날짜 장소</Text>
        {stops.map((stop) => <Pressable nativeID={`map-stop-${stop.id}`} accessibilityRole="button" accessibilityState={{ selected: selectedId === stop.id }} onPress={() => setSelectedId(stop.id)} key={stop.id} style={[styles.stopRow, selectedId === stop.id && styles.stopRowSelected]}><View style={styles.stopMarker}><Text variant="caption" weight="bold" color={color.text.onAction}>{stop.number}</Text></View><Text variant="body" weight="bold" style={styles.stopName}>{stop.name}</Text><Text variant="caption" color={color.text.muted}>지도에서 보기</Text></Pressable>)}
      </View>

      <Text variant="title" weight="bold" style={styles.sectionTitle}>실측 경로 비교</Text>

      <View style={styles.comparisons}><ComparisonCard route={SHADE_ROUTE} /><ComparisonCard route={WHEELCHAIR_ROUTE} /></View>

      <View style={styles.unknownNote}><Text variant="caption" weight="bold" color={color.text.muted}>UNKNOWN</Text><Text variant="caption" style={styles.unknownBody}>일부 구간은 데이터가 없어 판정하지 않았습니다. 모르는 것을 아는 척하지 않습니다.</Text></View>
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
