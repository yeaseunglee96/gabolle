// 코스 만족도(운영자) — S15P21E201-2020. 사용자가 코스(A/B/C)에 남긴 좋아요/아쉬워요를 모아 본다.
// 지표 화면(/admin/metrics, S15P21E201-2010)과 파일을 나눠 둔다 — 다른 작업과 겹치지 않게.
// 접근은 서버가 ADMIN 으로 막는다 — 여기서는 403 이면 「권한 없음」만 보인다. 운영자용이라 한국어로만 쓴다.
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';

import { ApiClientError, apiRequest } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { reasonLabel } from '@/features/coursefeedback/CourseFeedbackCard';
import type { CourseFeedbackReason } from '@/features/coursefeedback/courseFeedbackApi';
import { useLayout } from '@/layout/useLayout';

type Bucket = { key: string; total: number; up: number; upPercent: number | null };
type Summary = { days: number; total: number; up: number; upPercent: number | null; byCourse: Bucket[]; byArea: Bucket[]; reasons: Record<string, number> };

const ko = (text: string) => text;
const WINDOWS = [7, 30, 90];

export default function AdminCourseFeedback() {
  const { accessToken, ready } = useAuth();
  const { desktop } = useLayout();
  const [days, setDays] = useState(7);
  const [state, setState] = useState<'loading' | 'ready' | 'forbidden' | 'error'>('loading');
  const [summary, setSummary] = useState<Summary | null>(null);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setState('loading');
    try {
      setSummary(await apiRequest<Summary>(`/api/v1/admin/course-feedback/summary?days=${days}`, { accessToken }));
      setState('ready');
    } catch (error) {
      setState(error instanceof ApiClientError && (error.status === 403 || error.status === 401) ? 'forbidden' : 'error');
    }
  }, [accessToken, days]);

  useEffect(() => {
    if (!ready) return;
    if (!accessToken) { setState('forbidden'); return; }
    void load();
  }, [ready, accessToken, load]);

  return (
    <Screen scroll wide={desktop}>
      <Text variant="display" weight="bold">코스 만족도</Text>
      <Text variant="caption" color={color.text.body} style={styles.gap}>
        추천 코스에 남긴 👍/👎 를 모았습니다. 한 사람이 한 일정의 한 판에 하나, 바꾸면 마지막 값으로 셉니다.
      </Text>
      <View style={styles.row}>
        {WINDOWS.map((value) => (
          <Pressable key={value} accessibilityRole="tab" accessibilityState={{ selected: value === days }} onPress={() => setDays(value)} style={[styles.chip, value === days && styles.chipOn]}>
            <Text variant="caption" weight="bold" color={value === days ? color.text.onAction : color.text.heading}>{`최근 ${value}일`}</Text>
          </Pressable>
        ))}
      </View>
      {state === 'loading' ? <ActivityIndicator color={color.brand.navy} /> : null}
      {state === 'forbidden' ? <Text color={color.state.danger}>운영자만 볼 수 있어요.</Text> : null}
      {state === 'error' ? <Text color={color.state.danger}>불러오지 못했어요. 잠시 뒤 다시 열어 주세요.</Text> : null}
      {state === 'ready' && summary ? (
        <View style={styles.gap}>
          <View style={styles.card}>
            <Text variant="caption" color={color.text.muted}>전체</Text>
            <Text variant="display" weight="bold">{summary.upPercent === null ? '—' : `좋아요 ${summary.upPercent}%`}</Text>
            <Text variant="caption" color={color.text.body}>{`평가 ${summary.total}건 · 좋아요 ${summary.up}건`}</Text>
          </View>
          <Table title="코스별" rows={summary.byCourse} name={(key) => (key === '-' ? '코스 모름' : `코스 ${key}`)} />
          <Table title="지역별(출발 지역)" rows={summary.byArea} name={(key) => (key === '-' ? '지역 모름' : key)} />
          <View style={styles.card}>
            <Text weight="bold">이유별 건수</Text>
            {Object.keys(summary.reasons).length === 0 ? <Text variant="caption" color={color.text.muted}>아직 고른 이유가 없어요.</Text> : null}
            {Object.entries(summary.reasons).sort((a, b) => b[1] - a[1]).map(([reason, count]) => (
              <View key={reason} style={styles.line}>
                <Text variant="caption">{reasonLabel(ko, reason as CourseFeedbackReason) ?? reason}</Text>
                <Text variant="caption" weight="bold">{`${count}건`}</Text>
              </View>
            ))}
          </View>
        </View>
      ) : null}
    </Screen>
  );
}

function Table({ title, rows, name }: { title: string; rows: Bucket[]; name: (key: string) => string }) {
  return (
    <View style={styles.card}>
      <Text weight="bold">{title}</Text>
      {rows.length === 0 ? <Text variant="caption" color={color.text.muted}>아직 평가가 없어요.</Text> : null}
      {rows.map((row) => (
        <View key={row.key} style={styles.line}>
          <Text variant="caption">{name(row.key)}</Text>
          <Text variant="caption" weight="bold">{`${row.upPercent ?? '—'}% · ${row.total}건`}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  gap: { gap: spacing[3], marginTop: spacing[3] },
  row: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[3], marginBottom: spacing[3] },
  chip: { minHeight: 36, paddingHorizontal: spacing[3], justifyContent: 'center', borderRadius: radius.chip, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  chipOn: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  card: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  line: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing[3] },
});
