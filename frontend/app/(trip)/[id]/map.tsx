// 12 지도·동선 — 발표의 핵심 화면.
//
// 웹은 카카오 지도 JavaScript SDK를 사용하고, 앱은 SDK 결정 전까지 목록과 동선 요약을
// 유지한다. 지도 키가 없거나 SDK 로딩에 실패해도 흰 화면 대신 같은 목록을 계속 제공한다.
//
// 🔴 비교 카드의 숫자는 실제 측정값이다. 반올림하거나 다듬지 않는다.
// 🔴 이 앱은 "모르는 것을 아는 척하지 않는다" 는 원칙(PASS/FAIL/UNKNOWN)을 따른다.
//    그래서 판정이 안 된 구간이 있다는 것도 숨기지 않고 UNKNOWN 으로 그대로 보여준다.
import { useCallback, useState } from 'react';
import { useRouter } from 'expo-router';
import { Platform, Pressable, StyleSheet, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Card } from '@/components/Card';
import { Split } from '@/layout/Split';
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
  const [day, setDay] = useState<'DAY 1' | 'DAY 2'>('DAY 1');
  const stops = DAY_STOPS[day];
  const [selectedId, setSelectedId] = useState(stops[0].id);

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

      <Split
        master={
          // 🔴 기존 지도 placeholder 안의 동그란 마커 행(stopsRow)은 "지도 위 핀" 을 흉내
          // 내는 것이라 그대로 두고, 여기 별도의 세로 목록을 새로 만들었다 — Figma·명세에
          // 없는 화면이라 phone 에서는(순차 배치) 같은 정보가 두 번 보인다. 실제 지도 SDK가
          // 들어오면 지도 안 마커는 지도가 대신하고 이 목록만 남기면 된다. 사람 검토 필요.
          <View style={styles.stopsList}>
            <Text variant="title" weight="bold" style={styles.masterTitle}>
              동선 목록
            </Text>
            {stops.map((stop) => (
              <Pressable nativeID={`map-stop-${stop.id}`} accessibilityRole="button" accessibilityState={{ selected: selectedId === stop.id }} onPress={() => setSelectedId(stop.id)} key={stop.id} style={[styles.stopRow, selectedId === stop.id && styles.stopRowSelected]}>
                <View style={styles.stopMarker}>
                  <Text variant="caption" weight="bold" color={color.text.onAction}>
                    {stop.number}
                  </Text>
                </View>
                <Text variant="body" weight="bold" style={styles.stopName}>
                  {stop.name}
                </Text>
                <Text variant="caption" color={color.text.muted}>장소 보기</Text>
              </Pressable>
            ))}
          </View>
        }
        detail={
          <>
            <RouteMap stops={stops} selectedId={selectedId} onSelect={selectStopFromMap} onBack={() => router.back()} />

            <Text variant="title" weight="bold" style={styles.sectionTitle}>
              실측 경로 비교
            </Text>

            <View style={styles.comparisons}>
              <ComparisonCard route={SHADE_ROUTE} />
              <ComparisonCard route={WHEELCHAIR_ROUTE} />
            </View>

            <View style={styles.unknownNote}>
              <Text variant="caption" weight="bold" color={color.text.muted}>
                UNKNOWN
              </Text>
              <Text variant="caption" style={styles.unknownBody}>
                일부 구간은 데이터가 없어 판정하지 않았습니다. 모르는 것을 아는 척하지 않습니다.
              </Text>
            </View>
          </>
        }
      />
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
});
