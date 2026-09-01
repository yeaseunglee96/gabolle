// 12 지도·동선 — 발표의 핵심 화면.
//
// 지도 SDK 가 아직 안 정해져서(Jira 미결) 실제 지도 대신 자리표시자를 두고, 그 아래에
// 실측 데이터 비교 카드를 놓는다 — 이게 발표에서 보여줄 내용이다.
//
// 🔴 비교 카드의 숫자는 실제 측정값이다. 반올림하거나 다듬지 않는다.
// 🔴 이 앱은 "모르는 것을 아는 척하지 않는다" 는 원칙(PASS/FAIL/UNKNOWN)을 따른다.
//    그래서 판정이 안 된 구간이 있다는 것도 숨기지 않고 UNKNOWN 으로 그대로 보여준다.
import { StyleSheet, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Card } from '@/components/Card';

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

const STOPS = [
  { number: 1, name: '송도' },
  { number: 2, name: '남포동' },
  { number: 3, name: '흰여울' },
  { number: 4, name: '광안리' },
];

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
  return (
    <Screen scroll>
      <Text variant="caption">DAY 1 · 4곳</Text>
      <Text variant="display" weight="bold" style={styles.title}>
        여행 지도
      </Text>

      <View style={styles.mapPlaceholder}>
        {/* TODO: 실제 지도 SDK 연동 전까지 자리표시자로 둔다 (지도 공급자 미정). */}
        <Text variant="body" weight="bold" color={color.text.muted}>
          지도 자리 — SDK 미정
        </Text>
        <Text variant="caption" style={styles.mapPlaceholderSub}>
          실제 서비스에서는 여기에 동선이 표시됩니다.
        </Text>

        <View style={styles.stopsRow}>
          {STOPS.map((stop) => (
            <View key={stop.number} style={styles.stop}>
              <View style={styles.stopMarker}>
                <Text variant="caption" weight="bold" color={color.text.onAction}>
                  {stop.number}
                </Text>
              </View>
              <Text variant="caption">{stop.name}</Text>
            </View>
          ))}
        </View>

        <Text variant="caption" style={styles.attribution}>
          © OpenStreetMap contributors
        </Text>
      </View>

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
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: {
    marginTop: spacing[1],
    marginBottom: spacing[4],
  },
  mapPlaceholder: {
    height: 260,
    borderRadius: radius.lg,
    backgroundColor: color.surface.soft,
    borderWidth: 1,
    borderColor: color.surface.field,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing[1],
    padding: spacing[4],
  },
  mapPlaceholderSub: {
    textAlign: 'center',
  },
  stopsRow: {
    flexDirection: 'row',
    gap: spacing[4],
    marginTop: spacing[4],
  },
  stop: {
    alignItems: 'center',
    gap: spacing[1],
  },
  stopMarker: {
    width: 28,
    height: 28,
    borderRadius: radius.full,
    backgroundColor: color.action.brand,
    alignItems: 'center',
    justifyContent: 'center',
  },
  attribution: {
    position: 'absolute',
    bottom: spacing[2],
    left: spacing[2],
    color: color.text.muted,
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
