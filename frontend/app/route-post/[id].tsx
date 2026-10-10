// 루트 포스트 상세 — 경로 지도 · 들른 곳(사진·순서) · 좋아요/저장 · 「이 루트로 여행 만들기」 (S15P21E201-2027).
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';

import { useAuth } from '@/auth/AuthProvider';
import { AppImage } from '@/components/AppImage';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { localizeMessage } from '@/i18n/messages';
import { useLayout } from '@/layout/useLayout';
import { routeMetaLine, stopName } from '@/routepost/RoutePostCard';
import { RouteThumb } from '@/routepost/RouteThumb';
import { copyRouteIntoNewTrip, deleteRoutePost, getRoutePost, ROUTE_POSTS_KEY, routePostKey, setRouteReaction, txd, type RoutePostDto } from '@/routepost/routePosts';
import { AuthorAvatar } from '@/social/AuthorAvatar';

export default function RoutePostScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const { tx, language } = useI18n();
  const { accessToken } = useAuth();
  const { desktop } = useLayout();
  const queryClient = useQueryClient();
  const signedIn = Boolean(accessToken);
  const key = routePostKey(id ?? '', signedIn);
  const query = useQuery({ queryKey: key, queryFn: () => getRoutePost(id ?? '', accessToken), enabled: Boolean(id) });
  const [busy, setBusy] = useState<'like' | 'save' | 'copy' | 'delete' | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  // 몇 곳이 빠진 채 만들어졌으면 바로 넘어가지 않고 알린 뒤 이 단추로 넘어간다 — 빠진 것을 숨기지 않는다.
  const [created, setCreated] = useState<string | null>(null);

  const goBack = () => (router.canGoBack() ? router.back() : router.replace('/feed' as never));
  const requireSignIn = () => router.push({ pathname: '/sign-in', params: { returnTo: `/route-post/${id}` } } as never);

  const replace = (post: RoutePostDto) => {
    queryClient.setQueryData(key, { state: 'success', post });
    void queryClient.invalidateQueries({ queryKey: ROUTE_POSTS_KEY, predicate: (q) => q.queryKey[1] !== 'one' });
  };

  const toggle = async (post: RoutePostDto, kind: 'like' | 'save') => {
    if (!signedIn) { requireSignIn(); return; }
    if (busy) return;
    setBusy(kind);
    const next = await setRouteReaction(post.routePostId, kind, kind === 'like' ? !post.likedByMe : !post.savedByMe, accessToken);
    setBusy(null);
    if (next.state === 'success') replace(next.post); else setNotice(tx('지금은 반영하지 못했어요. 잠시 뒤 다시 눌러 주세요.', "Couldn't update that right now. Please tap again in a moment."));
  };

  const copy = async (post: RoutePostDto) => {
    if (!signedIn) { requireSignIn(); return; }
    if (busy) return;
    setBusy('copy'); setNotice(null);
    const result = await copyRouteIntoNewTrip(post, accessToken);
    setBusy(null);
    if (result.state !== 'success') { setNotice(localizeMessage(tx, result.message)); return; }
    void queryClient.invalidateQueries({ queryKey: ROUTE_POSTS_KEY });
    // 몇 곳이 빠졌으면 숨기지 않고 말한다 — 그사이 없어진 장소일 수 있다.
    if (result.skipped > 0) {
      setNotice(txd(tx, '%d곳은 지금 넣을 수 없어 뺐어요.', "%d stops couldn't be added and were left out.", result.skipped));
      setCreated(result.itineraryId);
      return;
    }
    router.push(`/trips/${encodeURIComponent(result.itineraryId)}/self-plan` as never);
  };

  const remove = async (post: RoutePostDto) => {
    if (busy) return;
    setBusy('delete');
    const result = await deleteRoutePost(post.routePostId, accessToken);
    setBusy(null);
    if (result.state !== 'success') { setNotice(localizeMessage(tx, result.message)); return; }
    void queryClient.invalidateQueries({ queryKey: ROUTE_POSTS_KEY });
    goBack();
  };

  const data = query.data;
  if (query.isLoading || !data) {
    return <Screen scroll><View style={styles.center}><ActivityIndicator color={color.action.primary} /></View></Screen>;
  }
  if (data.state !== 'success') {
    return (
      <Screen scroll>
        <BackRow onBack={goBack} />
        <View style={styles.stateCard}>
          <Text variant="title" weight="bold">{data.state === 'not-found' ? tx('루트를 찾을 수 없어요', 'Route not found') : tx('루트를 불러오지 못했어요', 'Could not load the route')}</Text>
          {data.state === 'not-found' ? <Text color={color.text.body}>{tx('지워졌거나 볼 수 없는 루트예요.', 'It was deleted or is not available.')}</Text> : <Button label={tx('다시 시도', 'Try again')} variant="tertiary" compact onPress={() => void query.refetch()} />}
        </View>
      </Screen>
    );
  }
  const post = data.post;
  const dayOrder = [...new Set(post.stops.map((s) => s.dayIndex))].sort((a, b) => a - b);
  const multiDay = dayOrder.length > 1;

  const head = (
    <View style={styles.head}>
      <Text variant="caption" weight="bold" color={color.text.muted}>{tx('루트', 'Route')}</Text>
      <Text variant={desktop ? 'hero' : 'display'} weight="bold" color={color.text.heading}>{post.title}</Text>
      <Text color={color.text.muted}>{routeMetaLine(post, tx)}</Text>
      <Pressable accessibilityRole="link" onPress={() => router.push(`/user/${post.author.userId}` as never)} style={styles.author}>
        <AuthorAvatar name={post.author.displayName} uri={post.author.avatarUrl ?? undefined} style={styles.avatar} />
        <Text weight="medium" color={color.text.body}>{post.author.displayName}</Text>
      </Pressable>
      {post.body ? <Text color={color.text.body}>{post.body}</Text> : null}
    </View>
  );

  const actions = (
    <View style={styles.actions}>
      <Button testID="route-copy" label={busy === 'copy' ? tx('여행을 만드는 중…', 'Creating your trip…') : tx('이 루트로 여행 만들기', 'Make a trip from this route')} disabled={busy !== null} onPress={() => void copy(post)} />
      <View style={styles.reactRow}>
        <Pressable accessibilityRole="button" accessibilityState={{ selected: post.likedByMe, busy: busy === 'like' }} onPress={() => void toggle(post, 'like')} style={[styles.react, post.likedByMe && styles.reactOn]}>
          <Text weight="bold" color={post.likedByMe ? color.action.primary : color.text.body}>{`♥ ${tx('좋아요', 'Like')} ${post.likeCount}`}</Text>
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityState={{ selected: post.savedByMe, busy: busy === 'save' }} onPress={() => void toggle(post, 'save')} style={[styles.react, post.savedByMe && styles.reactOn]}>
          <Text weight="bold" color={post.savedByMe ? color.action.primary : color.text.body}>{`★ ${tx('저장', 'Save')} ${post.saveCount}`}</Text>
        </Pressable>
        <Text variant="caption" color={color.text.muted}>{txd(tx, '따라 간 사람 %d', '%d followed', post.copyCount)}</Text>
      </View>
      {notice ? <Text accessibilityLiveRegion="polite" variant="caption" color={color.state.danger}>{notice}</Text> : null}
      {created ? <Button label={tx('만든 여행 보기', 'Open the new trip')} variant="secondary" compact onPress={() => router.push(`/trips/${encodeURIComponent(created)}/self-plan` as never)} /> : null}
      {post.mine ? <Button label={busy === 'delete' ? tx('지우는 중…', 'Deleting…') : tx('이 루트 지우기', 'Delete this route')} variant="tertiary" compact disabled={busy !== null} onPress={() => void remove(post)} /> : null}
    </View>
  );

  const stops = (
    <View style={styles.stops}>
      <Text variant="title" weight="bold" color={color.text.heading}>{tx('들른 곳', 'Stops')}</Text>
      {post.stops.map((stop, index) => (
        <View key={stop.seq}>
          {multiDay && (index === 0 || post.stops[index - 1].dayIndex !== stop.dayIndex)
            ? <Text variant="caption" weight="bold" color={color.text.muted} style={styles.dayLabel}>{txd(tx, '%d일차', 'Day %d', dayOrder.indexOf(stop.dayIndex) + 1)}</Text>
            : null}
          <Pressable accessibilityRole="link" onPress={() => router.push(`/place/${stop.placeId}` as never)} style={({ pressed }) => [styles.stop, pressed && styles.pressed]}>
            <View style={styles.num}><Text variant="caption" weight="bold" color={color.text.onAction}>{String(index + 1)}</Text></View>
            {stop.photoUrl
              ? <AppImage source={{ uri: stop.photoUrl }} resizeMode="cover" accessibilityLabel="" style={styles.photo} fallback={<View style={[styles.photo, styles.photoEmpty]} />} />
              : <View style={[styles.photo, styles.photoEmpty]} />}
            <View style={styles.stopText}>
              <Text weight="bold" numberOfLines={2} color={color.text.heading}>{stopName(stop, language)}</Text>
              {stop.stayMinutes ? <Text variant="caption" color={color.text.muted}>{txd(tx, '머문 시간 %d분', 'Stayed %d min', stop.stayMinutes)}</Text> : null}
            </View>
            <Text color={color.text.muted}>›</Text>
          </Pressable>
        </View>
      ))}
    </View>
  );

  return (
    <Screen scroll wide={desktop}>
      <BackRow onBack={goBack} />
      {desktop ? (
        <View style={styles.wideGrid}>
          <View style={styles.wideLeft}>
            <View style={styles.mapCard}><RouteThumb path={post.path} stops={post.stops} height={420} width={560} /></View>
          </View>
          <View style={styles.wideRight}>{head}{actions}{stops}</View>
        </View>
      ) : (
        <View style={styles.column}>
          {head}
          <View style={styles.mapCard}><RouteThumb path={post.path} stops={post.stops} height={220} /></View>
          {actions}
          {stops}
        </View>
      )}
    </Screen>
  );
}

function BackRow({ onBack }: { onBack: () => void }) {
  const { tx } = useI18n();
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={onBack} style={styles.back}>
      <Text variant="title">‹</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  center: { padding: spacing[8], alignItems: 'center' },
  back: { width: 44, height: 44, justifyContent: 'center' },
  stateCard: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  column: { gap: spacing[4] },
  wideGrid: { flexDirection: 'row', gap: spacing[6], alignItems: 'flex-start' },
  wideLeft: { flex: 1.1 },
  wideRight: { flex: 1, gap: spacing[4] },
  head: { gap: spacing[2] },
  author: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], alignSelf: 'flex-start', minHeight: 44 },
  avatar: { width: 28, height: 28, borderRadius: 14 },
  mapCard: { borderRadius: radius.lg, overflow: 'hidden', borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  actions: { gap: spacing[3] },
  reactRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], flexWrap: 'wrap' },
  react: { minHeight: 40, paddingHorizontal: spacing[4], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, justifyContent: 'center' },
  reactOn: { borderColor: color.action.primary, backgroundColor: color.surface.blush },
  stops: { gap: spacing[2] },
  dayLabel: { marginTop: spacing[3] },
  stop: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card, minHeight: 72 },
  pressed: { opacity: 0.85 },
  num: { width: 26, height: 26, borderRadius: 13, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' },
  photo: { width: 56, height: 56, borderRadius: radius.md },
  photoEmpty: { backgroundColor: color.surface.soft },
  stopText: { flex: 1, gap: 2 },
});
