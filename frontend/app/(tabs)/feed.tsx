import { useCallback, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, View } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, gutter, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { loadFeed, setFollowing, VISIBILITY_LABEL, type FeedLoadResult, type FeedScope, type StoryDto } from '@/social/stories';

function relativeTime(iso: string, tx: (ko: string, en: string) => string) {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  const minutes = Math.round((Date.now() - date.getTime()) / 60000);
  if (minutes < 1) return tx('방금', 'just now');
  if (minutes < 60) return tx(`${minutes}분 전`, `${minutes}m ago`);
  const hours = Math.round(minutes / 60);
  if (hours < 24) return tx(`${hours}시간 전`, `${hours}h ago`);
  const days = Math.round(hours / 24);
  if (days < 7) return tx(`${days}일 전`, `${days}d ago`);
  return `${date.getFullYear()}.${date.getMonth() + 1}.${date.getDate()}`;
}

function StoryCard({ story, showUnfollow, unfollowBusy, onUnfollow }: { story: StoryDto; showUnfollow: boolean; unfollowBusy: boolean; onUnfollow: () => void }) {
  const { tx } = useI18n();
  const place = story.place?.name ?? story.region ?? null;
  return <View style={styles.card}>
    <View style={styles.cardHeader}>
      <View style={styles.grow}><Text variant="body" weight="bold">{story.author.displayName}</Text><Text variant="caption" color={color.text.muted}>{relativeTime(story.createdAt, tx)}{place ? ` · ${place}` : ''}</Text></View>
      {story.mine && story.visibility !== 'PUBLIC' ? <View style={styles.visibilityBadge}><Text variant="caption" weight="bold" color={color.text.muted}>{tx(...VISIBILITY_LABEL[story.visibility])}</Text></View> : null}
      {showUnfollow ? <Pressable accessibilityRole="button" accessibilityLabel={tx(`${story.author.displayName} 언팔로우`, `Unfollow ${story.author.displayName}`)} accessibilityState={{ busy: unfollowBusy }} disabled={unfollowBusy} onPress={onUnfollow} style={[styles.unfollowButton, unfollowBusy && styles.busy]}><Text variant="caption" weight="bold" color={color.text.body}>{unfollowBusy ? tx('처리 중', 'Working') : tx('팔로잉', 'Following')}</Text></Pressable> : null}
    </View>
    <Text color={color.text.body} style={styles.body}>{story.body}</Text>
    {story.images.length ? <View style={styles.images}>{story.images.slice(0, 3).map((image) => <Image key={image.url} source={{ uri: image.url }} resizeMode="cover" accessibilityLabel={tx('여행 기록 사진', 'Trip record photo')} style={styles.image} />)}</View> : null}
  </View>;
}

export default function Feed() {
  const router = useRouter();
  const { accessToken } = useAuth();
  const { tx } = useI18n();
  const [scope, setScope] = useState<FeedScope>('ALL');
  const [result, setResult] = useState<FeedLoadResult>({ state: 'success', items: [], nextCursor: null });
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [unfollowingId, setUnfollowingId] = useState<string | null>(null);

  const load = useCallback(async (targetScope: FeedScope) => {
    setLoading(true);
    const next = await loadFeed({ scope: targetScope, accessToken });
    setResult(next);
    setLoading(false);
  }, [accessToken]);

  useFocusEffect(useCallback(() => { void load(scope); }, [load, scope]));

  const loadMore = async () => {
    if (result.state !== 'success' || !result.nextCursor || loadingMore) return;
    setLoadingMore(true);
    const next = await loadFeed({ scope, cursor: result.nextCursor, accessToken });
    setLoadingMore(false);
    if (next.state === 'success') setResult({ state: 'success', items: [...result.items, ...next.items], nextCursor: next.nextCursor });
  };

  const unfollow = async (story: StoryDto) => {
    setUnfollowingId(story.author.id);
    const outcome = await setFollowing(story.author.id, false, accessToken);
    setUnfollowingId(null);
    if (outcome.state === 'success' && result.state === 'success') setResult({ state: 'success', items: result.items.filter((item) => item.author.id !== story.author.id), nextCursor: result.nextCursor });
  };

  const items = result.state === 'success' ? result.items : [];

  return <View style={styles.shell}><Screen scroll>
    <View style={styles.headerRow}><View><Text variant="eyebrow" weight="bold">TRAVEL STORIES</Text><Text variant="display" weight="bold" style={styles.headerTitle}>{tx('여행 이야기', 'Travel stories')}</Text></View>{accessToken ? <Button label={tx('기록 남기기', 'Write')} onPress={() => router.push('/feed/compose')} containerStyle={styles.writeButton} /> : null}</View>

    <View accessibilityRole="tablist" style={styles.scopeTabs}>
      <Pressable accessibilityRole="tab" accessibilityState={{ selected: scope === 'ALL' }} onPress={() => setScope('ALL')} style={[styles.scopeTab, scope === 'ALL' && styles.scopeTabActive]}><Text variant="caption" weight="bold" color={scope === 'ALL' ? color.text.onAction : color.text.body}>{tx('전체', 'All')}</Text></Pressable>
      <Pressable accessibilityRole="tab" accessibilityState={{ selected: scope === 'FOLLOWING', disabled: !accessToken }} disabled={!accessToken} onPress={() => setScope('FOLLOWING')} style={[styles.scopeTab, scope === 'FOLLOWING' && styles.scopeTabActive, !accessToken && styles.scopeTabDisabled]}><Text variant="caption" weight="bold" color={scope === 'FOLLOWING' ? color.text.onAction : color.text.body}>{tx('팔로잉', 'Following')}</Text></Pressable>
    </View>

    {!accessToken ? <View style={styles.loginNotice}><Text variant="caption" color={color.text.body}>{tx('로그인하면 기록을 남기고 팔로잉 피드를 볼 수 있어요.', 'Sign in to write records and see your following feed.')}</Text><Pressable accessibilityRole="link" onPress={() => router.push('/sign-in')}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('로그인 →', 'Sign in →')}</Text></Pressable></View> : null}

    {loading ? <View accessibilityLiveRegion="polite" style={styles.stateCard}><ActivityIndicator color={color.brand.orange} /><Text variant="title" weight="bold">{tx('피드를 불러오고 있어요', 'Loading the feed')}</Text></View> : null}

    {!loading && result.state !== 'success' ? <View style={styles.stateCard}><Text variant="title" weight="bold">{result.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : result.state === 'unavailable' ? tx('피드 API를 기다리고 있어요', 'Waiting for the feed API') : tx('피드를 불러오지 못했어요', 'Could not load the feed')}</Text><Text color={color.text.body}>{result.message}</Text><Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void load(scope)} /></View> : null}

    {!loading && result.state === 'success' && !items.length ? <View style={styles.stateCard}>
      <View style={styles.icon}><Image source={require('../../assets/icons/home/wand.png')} accessibilityLabel={tx('AI 여행 추천', 'AI trip recommendation')} style={styles.stateIcon} /></View>
      {scope === 'FOLLOWING' ? <><Text variant="title" weight="bold">{tx('아직 팔로우한 사람의 기록이 없어요', 'No records from people you follow yet')}</Text><Button label={tx('전체 보기', 'See all')} onPress={() => setScope('ALL')} containerStyle={styles.primaryAction} /></> : <><Text variant="title" weight="bold">{tx('부산 여행 기록을 모으고 있어요', 'Collecting Busan travel stories')}</Text><Text color={color.text.body} style={styles.description}>{tx('먼저 여행을 준비하고 기록을 남겨 보세요.', 'Prepare a trip first, then write your own record.')}</Text><Button label={tx('내 여행 보기', 'See my trips')} onPress={() => router.push('/trips')} containerStyle={styles.primaryAction} /></>}
    </View> : null}

    {!loading && result.state === 'success' && items.length ? <View style={styles.list}>{items.map((story) => <StoryCard key={story.id} story={story} showUnfollow={scope === 'FOLLOWING'} unfollowBusy={unfollowingId === story.author.id} onUnfollow={() => void unfollow(story)} />)}</View> : null}

    {!loading && result.state === 'success' && result.nextCursor ? <Button label={loadingMore ? tx('불러오는 중…', 'Loading…') : tx('더 보기', 'Load more')} variant="ghost" disabled={loadingMore} onPress={() => void loadMore()} containerStyle={styles.loadMore} /> : null}

    <Pressable accessibilityRole="button" onPress={() => router.replace('/home')} style={({ pressed }) => [styles.homeLink, pressed && styles.pressed]}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('홈으로 돌아가기', 'Back to home')}</Text></Pressable>
    <View style={styles.tabBar}><TabBar active="home" /></View>
  </Screen></View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.brand.ivory }, headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: spacing[3] }, headerTitle: { marginTop: spacing[1] }, writeButton: { width: 'auto', paddingHorizontal: spacing[4] },
  scopeTabs: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[4] }, scopeTab: { minHeight: 40, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, alignItems: 'center', justifyContent: 'center' }, scopeTabActive: { backgroundColor: color.brand.navy, borderColor: color.brand.navy }, scopeTabDisabled: { opacity: 0.5 },
  loginNotice: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], alignItems: 'center', marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft },
  stateCard: { minHeight: 240, marginTop: spacing[6], padding: spacing[6], borderWidth: 1, borderColor: '#ece6dc', borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center', gap: spacing[3] }, icon: { width: 68, height: 68, borderRadius: radius.full, backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' }, stateIcon: { width: 32, height: 32 }, description: { maxWidth: 360, textAlign: 'center' }, primaryAction: { width: '100%', maxWidth: 320, marginTop: spacing[2], backgroundColor: color.brand.navy },
  list: { gap: spacing[3], marginTop: spacing[4] },
  card: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card }, cardHeader: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2] }, grow: { flex: 1, gap: spacing[1] },
  visibilityBadge: { minHeight: 28, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  unfollowButton: { minWidth: 72, minHeight: 32, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' }, busy: { opacity: 0.6 },
  body: { lineHeight: 22 },
  images: { flexDirection: 'row', gap: spacing[2] }, image: { flex: 1, aspectRatio: 1, borderRadius: radius.md, backgroundColor: color.surface.soft },
  loadMore: { marginTop: spacing[4] },
  homeLink: { minHeight: 44, marginTop: spacing[6], alignItems: 'center', justifyContent: 'center' }, pressed: { opacity: 0.72 }, tabBar: { marginTop: spacing[2], marginHorizontal: -gutter },
});
