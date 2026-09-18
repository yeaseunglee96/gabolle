// 마이페이지 › 저장한 기록 — 사용자 리포트: "마이페이지에 저장 누르면 저장했던 피드들 뜨게".
import { useCallback, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, View } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { MyPageShell } from '@/me/MyPageShell';
import { loadSavedStories, relativeStoryTime, setStorySaved, type FeedLoadResult, type StoryDto } from '@/social/stories';

export default function MyPageSaved() {
  const router = useRouter();
  const { tx } = useI18n();
  const { accessToken } = useAuth();
  const [result, setResult] = useState<FeedLoadResult>({ state: 'success', items: [], nextCursor: null });
  const [loading, setLoading] = useState(true);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setResult(await loadSavedStories(accessToken));
    setLoading(false);
  }, [accessToken]);

  useFocusEffect(useCallback(() => { void load(); }, [load]));

  async function unsave(id: string) {
    if (removingId) return;
    setRemovingId(id);
    const outcome = await setStorySaved(id, false, accessToken);
    setRemovingId(null);
    // 낙관적으로 지우지 않고 성공했을 때만 지운다 — 실패했는데 화면에서 사라지면
    // 사용자는 "저장이 풀렸나?" 를 다시 눌러 확인해야 한다. posts.tsx의 delete와 같은 판단.
    if (outcome.state === 'success') {
      setResult((prev) => (prev.state === 'success' ? { ...prev, items: prev.items.filter((item) => item.id !== id) } : prev));
    }
  }

  const items = result.state === 'success' ? result.items : [];

  return (
    <MyPageShell
      tab="saved"
      title={tx('저장한 기록', 'Saved records')}
      description={tx('다른 여행자의 기록 중 눌러 담아 둔 것이에요.', 'Records from other travellers that you bookmarked.')}
    >
      {loading ? <ActivityIndicator color={color.brand.orange} style={styles.loading} /> : null}

      {!loading && result.state !== 'success' ? (
        <View style={styles.stateCard}>
          <Text variant="title" weight="bold">{tx('저장한 기록을 불러오지 못했어요', "We couldn't load your saved records")}</Text>
          <Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void load()} />
        </View>
      ) : null}

      {!loading && result.state === 'success' && items.length === 0 ? (
        <View style={styles.empty}>
          <GabolleMascot state="idle" style={styles.mascot} />
          <Text variant="title" weight="bold">{tx('아직 저장한 기록이 없어요', 'No saved records yet')}</Text>
          <Text style={styles.emptyCopy}>{tx('피드에서 마음에 드는 기록을 눌러 저장해 보세요.', 'Save a record you like from the feed.')}</Text>
          <Button label={tx('피드 보러 가기', 'Browse the feed')} onPress={() => router.push('/feed')} containerStyle={styles.emptyCta} />
        </View>
      ) : null}

      <View style={styles.list}>
        {items.map((story: StoryDto) => (
          <View key={story.id} style={styles.card}>
            <Text variant="caption" numberOfLines={1} style={styles.cardMeta}>
              {story.author.displayName}{' · '}{relativeStoryTime(story.createdAt, tx)}{story.place?.name ? ` · ${story.place.name}` : story.region ? ` · ${story.region}` : ''}
            </Text>

            <View style={styles.cardBody}>
              {story.images.length ? <Image source={{ uri: story.images[0].url }} resizeMode="cover" accessibilityLabel={tx('여행 기록 사진', 'Trip record photo')} style={styles.thumb} /> : null}
              <Text numberOfLines={3} color={color.text.heading} style={styles.body}>{story.body}</Text>
            </View>

            <View style={styles.actions}>
              <Pressable accessibilityRole="button" onPress={() => router.push(`/feed/${story.id}`)} style={({ pressed }) => [styles.action, pressed && styles.pressed]}>
                <Text weight="bold" color={color.brand.navy}>{tx('자세히 →', 'Open →')}</Text>
              </Pressable>
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ busy: removingId === story.id }}
                disabled={removingId === story.id}
                onPress={() => void unsave(story.id)}
                style={({ pressed }) => [styles.action, styles.actionEnd, pressed && styles.pressed]}
              >
                <Text weight="medium" color={color.text.muted}>{removingId === story.id ? tx('처리 중…', 'Working…') : tx('저장 취소', 'Unsave')}</Text>
              </Pressable>
            </View>
          </View>
        ))}
      </View>
    </MyPageShell>
  );
}

const styles = StyleSheet.create({
  loading: { marginTop: spacing[4] },
  stateCard: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },

  empty: { alignItems: 'center', gap: spacing[2], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  mascot: { width: 88, height: 88 },
  emptyCopy: { textAlign: 'center' },
  emptyCta: { marginTop: spacing[2] },

  list: { gap: spacing[3] },
  card: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  cardMeta: { minWidth: 0 },
  cardBody: { flexDirection: 'row', gap: spacing[3] },
  thumb: { width: 64, height: 64, borderRadius: radius.md, backgroundColor: color.surface.soft },
  body: { flex: 1, minWidth: 0, lineHeight: 22 },

  actions: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.surface.border, paddingTop: spacing[2] },
  action: { minHeight: 44, justifyContent: 'center' },
  actionEnd: { marginLeft: 'auto' },
  pressed: { opacity: 0.7 },
});
