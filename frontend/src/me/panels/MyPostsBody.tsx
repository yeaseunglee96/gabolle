// MyPosts 본문 — 화면과 마이페이지 패널이 같은 것을 쓴다.
//
// 제목과 설명은 껍데기가 그린다(myPanels 의 panelTitle). 여기서 또 그리면 두 번 나온다.
import { useCallback, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, View } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { deleteStory, loadUserStories, relativeStoryTime, VISIBILITY_LABEL, type FeedLoadResult, type StoryDto, type StoryVisibility } from '@/social/stories';

type Filter = 'ALL' | StoryVisibility;

const FILTERS: Array<{ key: Filter; ko: string; en: string }> = [
  { key: 'ALL', ko: '전체', en: 'All' },
  { key: 'PUBLIC', ko: VISIBILITY_LABEL.PUBLIC[0], en: VISIBILITY_LABEL.PUBLIC[1] },
  { key: 'FOLLOWERS', ko: VISIBILITY_LABEL.FOLLOWERS[0], en: VISIBILITY_LABEL.FOLLOWERS[1] },
  { key: 'PRIVATE', ko: VISIBILITY_LABEL.PRIVATE[0], en: VISIBILITY_LABEL.PRIVATE[1] },
];

export function MyPostsBody() {
  const router = useRouter();
  const { tx } = useI18n();
  const { accessToken, user } = useAuth();
  const [result, setResult] = useState<FeedLoadResult>({ state: 'success', items: [], nextCursor: null });
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<Filter>('ALL');
  const [askDelete, setAskDelete] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState<string | null>(null);

  const load = useCallback(async () => {
    // 사용자가 없으면 그냥 빠져나가면 안 된다 — loading 이 true 로 남아 뱅글이가 영원히 돈다.
    // 세션을 되살리는 동안 user 가 잠깐 null 인 순간이 실제로 있다.
    if (!user?.userId) { setLoading(false); return; }
    setLoading(true);
    setResult(await loadUserStories(user.userId, accessToken));
    setLoading(false);
  }, [accessToken, user?.userId]);

  useFocusEffect(useCallback(() => { void load(); }, [load]));

  async function remove(id: string) {
    if (busy) return;
    setBusy(true);
    const outcome = await deleteStory(id, accessToken);
    setBusy(false);
    setAskDelete(null);
    if (outcome.state === 'success') {
      setResult((prev) => (prev.state === 'success' ? { ...prev, items: prev.items.filter((item) => item.id !== id) } : prev));
      setToast(tx('기록을 지웠어요. 지도 핀도 함께 사라졌어요.', 'Record deleted. Its map pin is gone too.'));
    } else {
      setToast(tx('지우지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not delete it. Try again shortly.'));
    }
  }

  const items = result.state === 'success' ? result.items : [];
  const shown = items.filter((item) => filter === 'ALL' || item.visibility === filter);

  return (
    <>
      <View style={styles.filters}>
        {FILTERS.map((item) => {
          const on = filter === item.key;
          return (
            <Pressable key={item.key} accessibilityRole="button" accessibilityState={{ selected: on }} onPress={() => setFilter(item.key)} style={[styles.filter, on && styles.filterOn]}>
              <Text variant="caption" weight="bold" color={on ? color.text.onAction : color.text.body}>{tx(item.ko, item.en)}</Text>
            </Pressable>
          );
        })}
      </View>

      {toast ? <View accessibilityRole="alert" style={styles.toast}><Text variant="caption" weight="bold" color={color.state.success}>{toast}</Text></View> : null}

      {loading ? <ActivityIndicator color={color.action.primary} style={styles.loading} /> : null}

      {!loading && result.state !== 'success' ? (
        <View style={styles.stateCard}>
          <Text variant="title" weight="bold">{tx('기록을 불러오지 못했어요', "We couldn't load your records")}</Text>
          <Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void load()} />
        </View>
      ) : null}

      {!loading && result.state === 'success' && shown.length === 0 ? (
        <View style={styles.empty}>
          <GabolleMascot state={items.length ? 'idle' : 'thinking'} still style={styles.mascot} />
          <Text variant="title" weight="bold">{items.length ? tx('이 조건에 맞는 기록이 없어요', 'No records match this filter') : tx('아직 남긴 기록이 없어요', 'No records yet')}</Text>
          <Text style={styles.emptyCopy}>{items.length ? tx('다른 조건으로 보거나 새 기록을 남겨 보세요.', 'Try another filter or write a new record.') : tx('여행 중 찍은 사진 한 장이면 충분해요.\n기록은 피드에도 함께 보여요.', 'One photo from your trip is enough.\nYour records also show up in the feed.')}</Text>
          {items.length === 0 ? <Button label={tx('첫 기록 남기기', 'Write your first record')} variant="secondary" onPress={() => router.push('/feed/compose')} containerStyle={styles.emptyCta} /> : null}
        </View>
      ) : null}

      <View style={styles.list}>
        {shown.map((story: StoryDto) => (
          <View key={story.id} style={styles.card}>
            <View style={styles.cardHead}>
              <Text variant="caption" numberOfLines={1} style={styles.cardMeta}>
                {relativeStoryTime(story.createdAt, tx)}{story.place?.name ? ` · ${story.place.name}` : story.region ? ` · ${story.region}` : ''}
              </Text>
              <View style={[styles.visPill, story.visibility === 'PUBLIC' && styles.visPillPublic]}>
                <Text variant="caption" weight="bold" color={story.visibility === 'PUBLIC' ? color.state.success : color.text.body}>
                  {tx(VISIBILITY_LABEL[story.visibility][0], VISIBILITY_LABEL[story.visibility][1])}
                </Text>
              </View>
            </View>

            <View style={styles.cardBody}>
              {story.images.length ? <Image source={{ uri: story.images[0].url }} resizeMode="cover" accessibilityLabel={tx('여행 기록 사진', 'Trip record photo')} style={styles.thumb} /> : null}
              <Text numberOfLines={3} color={color.text.heading} style={styles.body}>{story.body}</Text>
            </View>

            {askDelete === story.id ? (
              <View style={styles.confirm}>
                <Text variant="caption" weight="bold" color={color.state.danger}>{tx('이 기록을 지울까요? 지도 핀도 함께 사라져요.', 'Delete this record? Its map pin goes too.')}</Text>
                <View style={styles.confirmActions}>
                  <Button label={tx('취소', 'Cancel')} variant="tertiary" disabled={busy} onPress={() => setAskDelete(null)} containerStyle={styles.confirmAction} />
                  <Pressable accessibilityRole="button" disabled={busy} onPress={() => void remove(story.id)} style={[styles.delete, busy && styles.deleteBusy]}>
                    <Text weight="bold" color={color.state.danger}>{busy ? tx('지우는 중…', 'Deleting…') : tx('지우기', 'Delete')}</Text>
                  </Pressable>
                </View>
              </View>
            ) : (
              <View style={styles.actions}>
                <Pressable accessibilityRole="button" onPress={() => router.push(`/feed/${story.id}`)} style={({ pressed }) => [styles.action, pressed && styles.pressed]}>
                  <Text weight="bold" color={color.brand.navy}>{tx('자세히 →', 'Open →')}</Text>
                </Pressable>
                <Pressable accessibilityRole="button" onPress={() => { setAskDelete(story.id); setToast(null); }} style={({ pressed }) => [styles.action, styles.actionEnd, pressed && styles.pressed]}>
                  <Text weight="medium" color={color.state.danger}>{tx('삭제', 'Delete')}</Text>
                </Pressable>
              </View>
            )}
          </View>
        ))}
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  filters: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], marginBottom: spacing[4] },
  filter: { minHeight: 36, justifyContent: 'center', paddingHorizontal: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, backgroundColor: color.surface.card },
  filterOn: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  toast: { marginBottom: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.successBg },
  loading: { marginTop: spacing[4] },
  stateCard: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },

  empty: { alignItems: 'center', gap: spacing[2], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  mascot: { width: 104, height: 104 },
  emptyCopy: { textAlign: 'center' },
  emptyCta: { marginTop: spacing[2], minWidth: 220 },

  list: { gap: spacing[3] },
  card: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  cardHead: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  cardMeta: { flex: 1, minWidth: 0 },
  visPill: { minHeight: 32, justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.soft },
  visPillPublic: { backgroundColor: color.state.successBg },
  cardBody: { flexDirection: 'row', gap: spacing[3] },
  thumb: { width: 64, height: 64, borderRadius: radius.md, backgroundColor: color.surface.soft },
  body: { flex: 1, minWidth: 0, lineHeight: 22 },

  actions: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.surface.border, paddingTop: spacing[2] },
  action: { minHeight: 44, justifyContent: 'center' },
  actionEnd: { marginLeft: 'auto' },
  pressed: { opacity: 0.7 },

  confirm: { gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.dangerBg },
  confirmActions: { flexDirection: 'row', gap: spacing[2] },
  confirmAction: { flex: 1 },
  delete: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, backgroundColor: color.state.dangerBg, borderWidth: 1, borderColor: color.state.danger },
  deleteBusy: { opacity: 0.6 },
});
