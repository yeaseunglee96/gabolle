// 피드의 「루트」 탭 — 인기 루트 / 새 루트 (S15P21E201-2027).
// 피드백: 「유명한 루트 생기면 가보고 싶게」. 기본이 인기순이고 1·2·3위에 순위를 단다.
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { localizeMessage } from '@/i18n/messages';

import { RoutePostCard } from './RoutePostCard';
import { loadRouteFeed, routeFeedKey, type RouteFeedResult, type RouteSort } from './routePosts';

export function RouteFeedSection({ compact }: { compact: boolean }) {
  const { tx } = useI18n();
  const router = useRouter();
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const signedIn = Boolean(accessToken);
  const [sort, setSort] = useState<RouteSort>('POPULAR');
  const [loadingMore, setLoadingMore] = useState(false);
  const key = routeFeedKey(sort, signedIn);
  const query = useQuery({ queryKey: key, queryFn: () => loadRouteFeed({ sort, accessToken }) });
  const result: RouteFeedResult = query.data ?? { state: 'success', items: [], nextCursor: null };

  const loadMore = async () => {
    if (result.state !== 'success' || !result.nextCursor || loadingMore) return;
    setLoadingMore(true);
    const next = await loadRouteFeed({ sort, cursor: result.nextCursor, accessToken });
    setLoadingMore(false);
    if (next.state !== 'success') return;
    queryClient.setQueryData<RouteFeedResult>(key, (current) => current && current.state === 'success'
      ? { state: 'success', items: [...current.items, ...next.items.filter((item) => !current.items.some((have) => have.routePostId === item.routePostId))], nextCursor: next.nextCursor }
      : current);
  };

  const chip = (value: RouteSort, label: string) => (
    <Pressable key={value} accessibilityRole="radio" accessibilityState={{ checked: sort === value }} onPress={() => setSort(value)} style={[styles.chip, sort === value && styles.chipOn]}>
      <Text variant="caption" weight="bold" color={sort === value ? color.text.onAction : color.text.body}>{label}</Text>
    </Pressable>
  );

  return (
    <View style={styles.wrap}>
      <View style={styles.subRow}>
        <Text variant="caption" color={color.text.muted} style={styles.hint}>{tx('다른 여행자가 다녀온 코스를 그대로 따라가 보세요', 'Follow the routes other travelers took')}</Text>
        <View accessibilityRole="radiogroup" style={styles.chips}>
          {chip('POPULAR', tx('인기 루트', 'Popular routes'))}
          {chip('RECENT', tx('새 루트', 'New routes'))}
        </View>
      </View>

      {query.isLoading ? <View style={styles.state}><ActivityIndicator color={color.action.primary} /><Text weight="bold">{tx('루트를 불러오고 있어요', 'Loading routes')}</Text></View> : null}

      {!query.isLoading && result.state !== 'success' ? (
        <View style={styles.state}>
          <Text weight="bold">{result.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : tx('루트를 불러오지 못했어요', 'Could not load routes')}</Text>
          <Text variant="caption" color={color.text.body}>{localizeMessage(tx, result.message)}</Text>
          <Button label={tx('다시 시도', 'Try again')} variant="tertiary" compact onPress={() => void query.refetch()} />
        </View>
      ) : null}

      {!query.isLoading && result.state === 'success' && !result.items.length ? (
        <View style={styles.state}>
          <Text variant="title" weight="bold">{tx('아직 올라온 루트가 없어요', 'No routes yet')}</Text>
          <Text color={color.text.body}>{tx('내 여행 화면의 ⋯ 메뉴에서 「루트로 올리기」를 누르면 첫 루트가 될 수 있어요.', 'Open ⋯ on your trip and tap "Post as a route" to share the first one.')}</Text>
          <Button label={tx('내 여행 보기', 'My trips')} variant="secondary" compact onPress={() => router.push('/trips' as never)} />
        </View>
      ) : null}

      {!query.isLoading && result.state === 'success' && result.items.length ? (
        <View style={compact ? styles.list : styles.grid}>
          {result.items.map((post, index) => (
            <View key={post.routePostId} style={compact ? null : styles.gridCell}>
              <RoutePostCard post={post} compact={compact} rank={sort === 'POPULAR' && index < 3 ? index + 1 : null} onOpen={() => router.push(`/route-post/${post.routePostId}` as never)} />
            </View>
          ))}
        </View>
      ) : null}

      {result.state === 'success' && result.nextCursor ? (
        <Button label={loadingMore ? tx('불러오는 중…', 'Loading…') : tx('더 보기', 'Load more')} variant="tertiary" disabled={loadingMore} onPress={() => void loadMore()} />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing[4], marginTop: spacing[2] },
  subRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], flexWrap: 'wrap' },
  hint: { flexShrink: 1 },
  chips: { flexDirection: 'row', gap: spacing[2] },
  chip: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, justifyContent: 'center' },
  chipOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  state: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'flex-start' },
  list: { gap: spacing[4] },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[4] },
  gridCell: { flexBasis: '47%', flexGrow: 1, minWidth: 260 },
});
