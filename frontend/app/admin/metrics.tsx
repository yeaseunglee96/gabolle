// 운영 지표(운영자) — S15P21E201-2010. 신고 검토·장소 제안 검토처럼 탭에 안 올리고 이 주소(/admin/metrics)로 직접 들어온다.
// 접근은 서버가 ADMIN(배포 설정 GABOLLE_ADMIN_EMAILS)으로 막는다 — 여기서는 403 이면 「권한 없음」만 보인다.
// 운영자용이라 한국어로만 쓴다. 지표 정의는 docs/metrics/METRICS.md.
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Platform, StyleSheet, View } from 'react-native';
import Svg, { Polyline } from 'react-native-svg';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { fetchMetrics, fetchMetricsCsv, formatRate, linePoints, type MetricsReport } from '@/admin/metrics';
import { useLayout } from '@/layout/useLayout';

const CHART_W = 600;
const CHART_H = 160;
const SERIES = [
  { key: 'mau', label: 'MAU(30일)', stroke: color.action.primary },
  { key: 'wau', label: 'WAU(7일)', stroke: color.text.heading },
  { key: 'dau', label: 'DAU(하루)', stroke: color.text.muted },
] as const;

export default function AdminMetrics() {
  const { accessToken, ready } = useAuth();
  const { desktop } = useLayout();
  const [state, setState] = useState<'loading' | 'ready' | 'forbidden' | 'error'>('loading');
  const [report, setReport] = useState<MetricsReport | null>(null);
  const [errorMessage, setErrorMessage] = useState('');

  const load = useCallback(async () => {
    if (!accessToken) return;
    setState('loading');
    const result = await fetchMetrics(accessToken, 90);
    if (result.state === 'success') { setReport(result.report); setState('ready'); }
    else if (result.state === 'forbidden') setState('forbidden');
    else { setErrorMessage(result.message); setState('error'); }
  }, [accessToken]);

  useEffect(() => {
    if (!ready) return;
    if (!accessToken) { setState('forbidden'); return; }
    void load();
  }, [ready, accessToken, load]);

  async function downloadCsv() {
    try {
      const text = await fetchMetricsCsv(accessToken, 90);
      const url = URL.createObjectURL(new Blob([text], { type: 'text/csv' }));
      const a = document.createElement('a');
      a.href = url;
      a.download = `gabolle-metrics-${report?.today ?? 'daily'}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'CSV 를 받지 못했어요.');
    }
  }

  const t = report?.totals;
  const since = report?.notes.requestTrackingSince;
  const recentCohorts = report ? report.cohorts.filter((c) => c.size > 0).slice(-14).reverse() : [];
  const last14 = report ? report.daily.slice(-14).reverse() : [];

  return (
    <Screen scroll wide={desktop}>
      <Text variant="display" weight="bold">운영 지표</Text>
      <Text variant="caption" color={color.text.body} style={styles.subtitle}>
        {'활성 = 그날 로그인한 상태로 앱을 쓴 것(한국 날짜). 회원만 세고, 비회원은 기기 수로 따로 봅니다. 다운로드 수는 스토어 콘솔에서 봅니다.'}
      </Text>
      {state === 'loading' ? <ActivityIndicator color={color.action.primary} /> : null}
      {state === 'forbidden' ? <Text color={color.text.body}>운영자만 볼 수 있어요.</Text> : null}
      {state === 'error' ? <Text color={color.state.danger}>{errorMessage || '지표를 불러오지 못했어요.'}</Text> : null}

      {state === 'ready' && report && t ? (
        <View style={styles.stack}>
          <Text variant="caption" color={color.state.danger}>
            {since
              ? `정확한 활동 기록은 ${since}부터입니다. 그 전 날짜는 로그인·토큰 갱신·일정 기록으로 거꾸로 채운 값이라 실제보다 작습니다.`
              : '정확한 활동 기록이 아직 하루도 없습니다. 지금 보이는 활동 숫자는 모두 거꾸로 채운 값이라 실제보다 작습니다.'}
          </Text>

          <View style={styles.tiles}>
            {[
              ['MAU', t.mau], ['WAU', t.wau], ['DAU(오늘)', t.dau], ['비회원 MAU(기기)', t.anonymousMau],
              ['전체 회원', t.users], ['전체 여행', t.trips], ['코스 공유 글', t.routePosts], ['장소 제안', t.placeSuggestions],
            ].map(([label, value]) => (
              <View key={label as string} style={[styles.tile, desktop && styles.tileWide]}>
                <Text variant="caption" color={color.text.body}>{label as string}</Text>
                <Text variant="display" weight="bold">{String(value)}</Text>
              </View>
            ))}
          </View>

          <View style={styles.card}>
            <Text variant="title" weight="bold">{`활성 사용자 추이 (${report.from} ~ ${report.today})`}</Text>
            <View style={styles.legend}>
              {SERIES.map((s) => (
                <View key={s.key} style={styles.legendItem}>
                  <View style={[styles.swatch, { backgroundColor: s.stroke }]} />
                  <Text variant="caption">{s.label}</Text>
                </View>
              ))}
            </View>
            <Svg width="100%" height={CHART_H} viewBox={`0 0 ${CHART_W} ${CHART_H}`} preserveAspectRatio="none">
              {SERIES.map((s) => (
                <Polyline key={s.key} points={linePoints(report.daily.map((d) => d[s.key]), CHART_W, CHART_H - 4)}
                  fill="none" stroke={s.stroke} strokeWidth={2} />
              ))}
            </Svg>
            <Text variant="caption" color={color.text.muted}>{`세로축 최대 ${Math.max(0, ...report.daily.map((d) => d.mau))}명 · 선마다 같은 축`}</Text>
          </View>

          <View style={styles.card}>
            <Text variant="title" weight="bold">재방문율 (가입일 기준)</Text>
            <View style={styles.tiles}>
              {(['d1', 'd7', 'd30'] as const).map((k) => (
                <View key={k} style={styles.tile}>
                  <Text variant="caption" color={color.text.body}>{k.toUpperCase()}</Text>
                  <Text variant="title" weight="bold">{formatRate(report.retention[k].rate)}</Text>
                  <Text variant="caption" color={color.text.muted}>{`${report.retention[k].returned}/${report.retention[k].cohortUsers}명`}</Text>
                </View>
              ))}
            </View>
            <Row cells={['가입일', '가입', 'D1', 'D7', 'D30']} header />
            {recentCohorts.map((c) => (
              <Row key={c.signupDay} cells={[c.signupDay, String(c.size), cell(c.d1), cell(c.d7), cell(c.d30)]} />
            ))}
          </View>

          <View style={styles.card}>
            <Text variant="title" weight="bold">최근 14일</Text>
            <Row cells={['날짜', 'DAU', '가입', '일정 AI', '직접 짜기', '일정 없음', '코스 글', '장소 제안']} header />
            {last14.map((d) => (
              <Row key={d.day} cells={[d.day.slice(5), d.dau, d.signups, d.tripsAi, d.tripsSelf, d.tripsNoItinerary, d.routePosts, d.placeSuggestions].map(String)} />
            ))}
          </View>

          {Platform.OS === 'web' ? (
            <Button label="하루 단위 CSV 받기 (90일)" variant="tertiary" compact onPress={() => void downloadCsv()} />
          ) : null}
        </View>
      ) : null}
    </Screen>
  );
}

function cell(v: number | null) {
  return v == null ? '—' : String(v);
}

function Row({ cells, header }: { cells: string[]; header?: boolean }) {
  return (
    <View style={styles.row}>
      {cells.map((c, i) => (
        <Text key={i} variant="caption" weight={header ? 'bold' : undefined} color={header ? color.text.body : undefined}
          style={[styles.cellText, i === 0 && styles.firstCell]}>{c}</Text>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  subtitle: { marginTop: spacing[1], marginBottom: spacing[4] },
  stack: { gap: spacing[4] },
  tiles: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  tile: { minWidth: 140, flexGrow: 1, padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card, gap: spacing[1] },
  tileWide: { flexBasis: '22%' },
  card: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  legend: { flexDirection: 'row', gap: spacing[3], flexWrap: 'wrap' },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: spacing[1] },
  swatch: { width: 12, height: 3, borderRadius: 2 },
  row: { flexDirection: 'row', paddingVertical: spacing[1] },
  cellText: { flex: 1, textAlign: 'right' },
  firstCell: { flex: 1.4, textAlign: 'left' },
});
