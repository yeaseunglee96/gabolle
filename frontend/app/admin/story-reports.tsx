// 신고 검토(운영자) 화면 —. 탭 네비게이션에는 안 올린다 — 일반
// 사용자가 볼 이유가 없는 화면이고, 접근 자체는 어차피 서버가 ROLE_ADMIN으로 막는다
// (moderation.ts 주석 참고). 운영자는 이 경로(/admin/story-reports)로 직접 들어온다.
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';

import { dismissReport, fetchModerationQueue, removeStory, type ModerationQueueItem } from '@/admin/moderation';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, spacing } from '@/design/tokens';

const REASON_LABEL: Record<string, string> = {
  PRIVACY: '개인정보 노출',
  OFFENSIVE: '불쾌한 내용',
  SPAM: '스팸',
  OTHER: '기타',
};

const OVERDUE_SECONDS = 86400; // 24시간 — S15P21E201-271 완료 기준

function formatElapsed(seconds: number): string {
  if (seconds < 3600) return `${Math.max(1, Math.round(seconds / 60))}분 전 접수`;
  if (seconds < 86400) return `${Math.round(seconds / 3600)}시간 전 접수`;
  return `${Math.round(seconds / 86400)}일 전 접수`;
}

// 백엔드가 오래된 신고 순으로 주지만(ModerationQueueService), 24시간 넘은 것을 "맨 위에
// 고정"이라는 요구는 그 정렬만으로는 보장되지 않는다 — 신고가 몰리는 시점이 섞이면
// 24시간 넘은 항목 사이에 안 넘은 항목이 끼어들 수 있다. 그래서 여기서 한 번 더 가른다
// 각 그룹 안에서는 오래된 순을 그대로 유지한 채, 넘은 것을 앞으로 옮긴다(안정 정렬).
function sortWithOverdueFirst(items: ModerationQueueItem[]): ModerationQueueItem[] {
  const overdue = items.filter((item) => item.elapsedSeconds >= OVERDUE_SECONDS);
  const rest = items.filter((item) => item.elapsedSeconds < OVERDUE_SECONDS);
  return [...overdue, ...rest];
}

export default function AdminStoryReports() {
  const { accessToken, ready } = useAuth();
  const [state, setState] = useState<'loading' | 'ready' | 'forbidden' | 'error'>('loading');
  const [items, setItems] = useState<ModerationQueueItem[]>([]);
  const [errorMessage, setErrorMessage] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setState('loading');
    const result = await fetchModerationQueue(accessToken);
    if (result.state === 'success') { setItems(sortWithOverdueFirst(result.items)); setState('ready'); }
    else if (result.state === 'forbidden') setState('forbidden');
    else { setErrorMessage(result.message); setState('error'); }
  }, [accessToken]);

  useEffect(() => {
    if (!ready) return;
    if (!accessToken) { setState('forbidden'); return; }
    void load();
  }, [ready, accessToken, load]);

  async function act(storyId: string, action: 'remove' | 'dismiss') {
    if (busyId) return;
    setBusyId(storyId);
    const result = action === 'remove' ? await removeStory(storyId, accessToken) : await dismissReport(storyId, accessToken);
    setBusyId(null);
    if (result.state === 'success') setItems((current) => current.filter((item) => item.storyId !== storyId));
    else if (result.state === 'forbidden') setState('forbidden');
    else setErrorMessage(result.message);
  }

  return (
    <Screen scroll>
      <View style={styles.titleRow}>
        <Text variant="display" weight="bold">신고 검토</Text>
        {state === 'ready' && items.length > 0 && (
          <View style={styles.countBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>미처리 {items.length}건</Text></View>
        )}
      </View>
      <Text variant="caption" color={color.text.body} style={styles.subtitle}>
        미처리 신고가 있는 기록을 오래된 순으로 보여줘요. 24시간이 지난 항목은 빨간색으로 맨 위에 고정돼요. 삭제하면 기록이 즉시 비노출돼요.
      </Text>

      {state === 'loading' && <ActivityIndicator style={styles.spinner} color={color.action.primary} />}

      {state === 'forbidden' && (
        <Card tinted>
          <Text weight="bold">이 화면은 운영자만 볼 수 있어요.</Text>
        </Card>
      )}

      {state === 'error' && (
        <Card tinted>
          <Text accessibilityRole="alert" color={color.state.danger}>{errorMessage}</Text>
          <Button label="다시 시도" variant="secondary" containerStyle={styles.retry} onPress={() => void load()} />
        </Card>
      )}

      {state === 'ready' && items.length === 0 && (
        <Card>
          <Text color={color.text.body}>처리할 신고가 없어요.</Text>
        </Card>
      )}

      {state === 'ready' && (
        <View style={styles.list}>
          {items.map((item) => {
            const overdue = item.elapsedSeconds >= OVERDUE_SECONDS;
            return (
            <Card key={item.storyId} style={[styles.itemCard, overdue && styles.itemCardOverdue]}>
              <View style={styles.itemHeader}>
                <Text weight="bold">{item.authorName ?? '(탈퇴한 사용자)'}</Text>
                <Text variant="caption" weight={overdue ? 'bold' : undefined} color={overdue ? color.state.danger : color.text.muted}>
                  {overdue ? `⚠ ${formatElapsed(item.elapsedSeconds)}` : formatElapsed(item.elapsedSeconds)}
                </Text>
              </View>
              <Text style={styles.body}>{item.body}</Text>
              <View style={styles.reasonRow}>
                {item.reasons.map((reason) => (
                  <View key={reason} style={styles.reasonChip}>
                    <Text variant="caption" weight="bold">{REASON_LABEL[reason] ?? reason}</Text>
                  </View>
                ))}
                <Text variant="caption" color={color.text.muted}>신고 {item.reportCount}건</Text>
              </View>
              <View style={styles.actionRow}>
                <Button
                  label={busyId === item.storyId ? '처리 중…' : '기각'}
                  variant="secondary"
                  disabled={busyId === item.storyId}
                  onPress={() => void act(item.storyId, 'dismiss')}
                />
                <Button
                  label={busyId === item.storyId ? '처리 중…' : '삭제'}
                  variant="primary"
                  disabled={busyId === item.storyId}
                  onPress={() => void act(item.storyId, 'remove')}
                />
              </View>
            </Card>
            );
          })}
        </View>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  countBadge: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: 999, backgroundColor: color.state.danger },
  subtitle: { marginTop: spacing[1], marginBottom: spacing[4] },
  spinner: { marginTop: spacing[6] },
  retry: { marginTop: spacing[2] },
  list: { gap: spacing[3] },
  itemCard: { gap: spacing[2] },
  itemCardOverdue: { borderWidth: 2, borderColor: color.state.danger },
  itemHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  body: { color: color.text.heading },
  reasonRow: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: spacing[2] },
  reasonChip: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: 999, backgroundColor: color.surface.tint },
  actionRow: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[1] },
});
