// 팔로워·팔로잉·차단 목록의 공통 몸통 —·-1181.
import { useCallback, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, View } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { useAuth } from '@/auth/AuthProvider';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { setFollowing, type RelationItem, type RelationListResult } from './stories';

type RelationListProps = {
  emptyMessage: string;
  loader: (accessToken: string | null, cursor?: string | null) => Promise<RelationListResult>;
  /** 기본은 팔로우/팔로잉 버튼이다. 차단 목록처럼 다른 동작이 필요하면 이 자리를 갈아 끼운다. */
  renderAction?: (item: RelationItem, refresh: () => void) => React.ReactNode;
};

type ListState = { status: 'loading' } | { status: 'loaded'; items: RelationItem[]; nextCursor: string | null } | { status: 'unavailable'; message: string };

function Avatar({ uri }: { uri?: string }) {
  return uri ? <Image source={{ uri }} accessibilityIgnoresInvertColors style={styles.avatarImage} /> : <View style={styles.avatarFallback} />;
}

/** 목록 한 줄의 기본 오른쪽 버튼 — 지금 보는 내가 이 사람을 팔로우하는지(following)를 그대로 그린다. */
function FollowActionButton({ item, refresh }: { item: RelationItem; refresh: () => void }) {
  const { accessToken } = useAuth();
  const { tx } = useI18n();
  const [busy, setBusy] = useState(false);
  const toggle = async () => {
    if (busy) return;
    setBusy(true);
    await setFollowing(item.userId, !item.following, accessToken);
    setBusy(false);
    refresh();
  };
  return (
    <Button
      compact
      variant={item.following ? 'tertiary' : 'primary'}
      label={busy ? tx('처리 중…', 'Working…') : item.following ? tx('팔로잉', 'Following') : tx('팔로우', 'Follow')}
      disabled={busy}
      onPress={() => void toggle()}
    />
  );
}

/** Screen 없이 목록만 그린다 — MyPageShell처럼 이미 Screen 안에 있는 자리에서 쓴다. */
export function RelationList({ emptyMessage, loader, renderAction }: RelationListProps) {
  const router = useRouter();
  const { accessToken } = useAuth();
  const { tx } = useI18n();
  const [state, setState] = useState<ListState>({ status: 'loading' });
  const [loadingMore, setLoadingMore] = useState(false);

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    const result = await loader(accessToken);
    setState(result.state === 'success' ? { status: 'loaded', items: result.items, nextCursor: result.nextCursor } : { status: 'unavailable', message: result.message });
  }, [accessToken, loader]);

  useFocusEffect(useCallback(() => { void load(); }, [load]));

  // FlatList가 아니라 map이다. 이 목록이 화면에 따라 이미 스크롤 중인 자리(Screen
  // scroll, MyPageShell) 안에 얹히므로, FlatList를 또 넣으면 스크롤 가능한 것이 중첩된다
  // (경고 이전에, onEndReached가 부모가 스크롤할 때는 안 불려서 "더 읽기"가 조용히 죽는다).
  // 한 페이지가 최대 50명이라 가상화 없이 map으로도 무겁지 않다.
  const loadMore = async () => {
    if (state.status !== 'loaded' || !state.nextCursor || loadingMore) return;
    setLoadingMore(true);
    const result = await loader(accessToken, state.nextCursor);
    setLoadingMore(false);
    if (result.state === 'success') {
      setState({ status: 'loaded', items: [...state.items, ...result.items], nextCursor: result.nextCursor });
    }
  };

  return (
    <View style={styles.body}>
      {state.status === 'loading' ? <View style={styles.stateCard}><ActivityIndicator color={color.action.primary} /></View> : null}

      {state.status === 'unavailable' ? (
        <View accessibilityRole="alert" style={styles.stateCard}>
          <Text color={color.text.body}>{state.message}</Text>
          <Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void load()} />
        </View>
      ) : null}

      {state.status === 'loaded' && !state.items.length ? (
        <View style={styles.stateCard}><Text color={color.text.body}>{emptyMessage}</Text></View>
      ) : null}

      {state.status === 'loaded' && state.items.length ? (
        <View>
          {state.items.map((item) => (
            <Pressable key={item.userId} accessibilityRole="button" onPress={() => router.push(`/user/${item.userId}`)} style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
              <Avatar uri={item.avatarUrl} />
              <View style={styles.name}>
                <Text weight="bold" numberOfLines={1}>{item.displayName}</Text>
                {/* 🔴 안 센 것(null)은 아예 안 그린다. 0 으로 그리면 화면이 「기록 0개」라고
                    단언하게 되는데, 차단 목록처럼 세지 않은 자리에서는 사실이 아니다. */}
                {item.storyCount === null ? null : (
                  <Text variant="caption" color={color.text.muted}>
                    {tx(`기록 ${item.storyCount}개`, `${item.storyCount} ${item.storyCount === 1 ? 'record' : 'records'}`)}
                  </Text>
                )}
              </View>
              {renderAction ? renderAction(item, () => void load()) : <FollowActionButton item={item} refresh={() => void load()} />}
            </Pressable>
          ))}
          {state.nextCursor ? (
            loadingMore
              ? <ActivityIndicator color={color.action.primary} style={styles.footerSpinner} />
              : <Button label={tx('더 보기', 'Load more')} variant="tertiary" onPress={() => void loadMore()} />
          ) : null}
        </View>
      ) : null}
    </View>
  );
}

type RelationListScreenProps = RelationListProps & { title: string };

/** 독립된 화면으로 쓸 때(뒤로가기·제목 필요) — `/user/[id]/followers` 등. */
export function RelationListScreen({ title, ...listProps }: RelationListScreenProps) {
  const router = useRouter();
  const { tx } = useI18n();
  return (
    <Screen scroll>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => (router.canGoBack() ? router.back() : router.replace('/home'))} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹ {tx('뒤로', 'Back')}</Text>
        </Pressable>
        <Text variant="title" weight="bold">{title}</Text>
        <View style={styles.backSpacer} />
      </View>
      <RelationList {...listProps} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], marginBottom: spacing[3] },
  back: { minHeight: 44, minWidth: 44, justifyContent: 'center' },
  backSpacer: { minWidth: 44 },
  pressed: { opacity: 0.72 },
  body: { flex: 1 },
  stateCard: { gap: spacing[3], marginTop: spacing[6], padding: spacing[4], alignItems: 'center' },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], paddingVertical: spacing[3] },
  avatarImage: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.soft },
  avatarFallback: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.soft },
  name: { flex: 1, minWidth: 0, gap: 2 },
  footerSpinner: { marginVertical: spacing[4] },
});
