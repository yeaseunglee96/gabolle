import { useCallback, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, View } from 'react-native';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { BlockUserDialog } from '@/social/BlockUserDialog';
import { getUserProfile, loadUserStories, relativeStoryTime, setBlocked, setFollowing, type FeedLoadResult, type StoryDto, type UserProfileDto } from '@/social/stories';

type ProfileState = { status: 'loading' } | { status: 'loaded'; profile: UserProfileDto } | { status: 'unavailable'; message: string };

export default function UserProfile() {
  const router = useRouter();
  const { accessToken, user } = useAuth();
  const { tx } = useI18n();
  const { id } = useLocalSearchParams<{ id: string }>();
  const [state, setState] = useState<ProfileState>({ status: 'loading' });
  const [stories, setStories] = useState<FeedLoadResult>({ state: 'success', items: [], nextCursor: null });
  const [storiesLoading, setStoriesLoading] = useState(true);
  const [followBusy, setFollowBusy] = useState(false);
  const [confirmingBlock, setConfirmingBlock] = useState(false);
  const [blockBusy, setBlockBusy] = useState(false);
  const [blockNotice, setBlockNotice] = useState('');

  const load = useCallback(async () => {
    if (!id) return;
    setState({ status: 'loading' });
    setStoriesLoading(true);
    const [profileResult, storiesResult] = await Promise.all([getUserProfile(id, accessToken), loadUserStories(id, accessToken)]);
    setState(profileResult.state === 'success' ? { status: 'loaded', profile: profileResult.profile } : { status: 'unavailable', message: profileResult.message });
    setStories(storiesResult);
    setStoriesLoading(false);
  }, [id, accessToken]);

  useFocusEffect(useCallback(() => { void load(); }, [load]));

  const toggleFollow = async () => {
    if (state.status !== 'loaded' || !id) return;
    const nextFollowing = !state.profile.following;
    setFollowBusy(true);
    const outcome = await setFollowing(id, nextFollowing, accessToken);
    setFollowBusy(false);
    if (outcome.state === 'success') {
      setState({ status: 'loaded', profile: { ...state.profile, following: outcome.following, followerCount: outcome.followerCount, followingCount: outcome.followingCount } });
    }
  };

  // 차단하면 팔로우가 서버에서 함께 끊기므로 화면도 다시 불러온다
  // 따로 계산해 맞추면 서버와 조용히 갈라진다.
  const confirmBlock = async () => {
    if (state.status !== 'loaded' || !id) return false;
    const outcome = await setBlocked(id, true, accessToken);
    if (outcome.state !== 'success') return false;
    setBlockNotice(tx('이제 이 사용자에게 내 글이 보이지 않아요.', "This user can no longer see your posts."));
    await load();
    return true;
  };

  const unblock = async () => {
    if (state.status !== 'loaded' || !id || blockBusy) return;
    setBlockBusy(true);
    const outcome = await setBlocked(id, false, accessToken);
    setBlockBusy(false);
    if (outcome.state !== 'success') return;
    setBlockNotice('');
    await load();
  };

  const items = stories.state === 'success' ? stories.items : [];

  return (
    <Screen scroll>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => (router.canGoBack() ? router.back() : router.replace('/feed'))} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
        <Text variant="title" weight="bold">‹ {tx('뒤로', 'Back')}</Text>
      </Pressable>

      {state.status === 'loading' ? (
        <View accessibilityLiveRegion="polite" style={styles.stateCard}><ActivityIndicator color={color.action.primary} /><Text color={color.text.body}>{tx('프로필을 불러오고 있어요', 'Loading profile')}</Text></View>
      ) : null}

      {state.status === 'unavailable' ? (
        <View accessibilityRole="alert" style={styles.stateCard}>
          <Text variant="title" weight="bold">{tx('프로필을 불러오지 못했어요', "We couldn't load this profile")}</Text>
          <Text color={color.text.body}>{state.message}</Text>
          <Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void load()} />
        </View>
      ) : null}

      {state.status === 'loaded' ? (
        <View style={styles.header}>
          <Text variant="display" weight="bold">{state.profile.displayName}</Text>
          {/* — 숫자만 있고 누를 곳이 없었다. 목록 화면이 생겼으니 잇는다. */}
          <View style={styles.statRow}>
            <View style={styles.stat}><Text variant="title" weight="bold">{state.profile.storyCount}</Text><Text variant="caption" color={color.text.muted}>{tx('기록', 'Records')}</Text></View>
            <Pressable accessibilityRole="button" onPress={() => router.push(`/user/${id}/followers`)} style={styles.stat}><Text variant="title" weight="bold">{state.profile.followerCount}</Text><Text variant="caption" color={color.text.muted}>{tx('팔로워', 'Followers')}</Text></Pressable>
            <Pressable accessibilityRole="button" onPress={() => router.push(`/user/${id}/following`)} style={styles.stat}><Text variant="title" weight="bold">{state.profile.followingCount}</Text><Text variant="caption" color={color.text.muted}>{tx('팔로잉', 'Following')}</Text></Pressable>
          </View>
          {/* 문자열로 맞춰 비교한다 — 두 응답의 userId가 타입 선언과 다르게 오면(숫자 vs 문자열) !==가 늘 참이 되어 본인 프로필에도 팔로우 버튼이 뜬다. */}
          {accessToken && String(user?.userId ?? '') === String(state.profile.userId) ? (
            <Button label={tx('프로필 수정', 'Edit profile')} variant="tertiary" onPress={() => router.push('/me')} containerStyle={styles.followButton} />
          ) : accessToken && !state.profile.blockedByUser ? (
            <View style={styles.actionRow}>
              <Button
                label={followBusy ? tx('처리 중…', 'Working…') : state.profile.following ? tx('팔로잉', 'Following') : tx('팔로우', 'Follow')}
                variant={state.profile.following ? 'tertiary' : 'primary'}
                disabled={followBusy || state.profile.blocked}
                onPress={() => void toggleFollow()}
                containerStyle={styles.followButton}
              />
              {/* 차단·해제는 같은 자리에서 바뀐다. 차단은 확인창을 거치고, 해제는 되돌리는
                  동작이라 바로 한다 — 실수로 눌러도 잃는 것이 없다.
              */}
              <Button
                label={blockBusy ? tx('처리 중…', 'Working…') : state.profile.blocked ? tx('차단 해제', 'Unblock') : tx('차단하기', 'Block')}
                variant="tertiary"
                disabled={blockBusy}
                onPress={() => (state.profile.blocked ? void unblock() : setConfirmingBlock(true))}
                containerStyle={styles.followButton}
              />
            </View>
          ) : null}

          {blockNotice ? <Text accessibilityLiveRegion="polite" variant="caption" color={color.text.body}>{blockNotice}</Text> : null}
        </View>
      ) : null}

      {/* 차단당한 쪽이 보는 화면. 빈 화면도 404 도 아니다 — 없는 사람으로 만들면 실수로
          눌렀을 때 상대가 계정이 사라졌다고 오해하고 되돌릴 길이 막힌다
      */}
      {state.status === 'loaded' && state.profile.blockedByUser ? (
        <View accessibilityRole="alert" style={styles.stateCard}>
          <Text variant="title" weight="bold">{tx('차단되어 볼 수 없습니다', 'Blocked — you cannot view this profile')}</Text>
        </View>
      ) : null}

      {state.status === 'loaded' && !state.profile.blockedByUser ? (
        <View style={styles.list}>
          {storiesLoading ? <ActivityIndicator color={color.action.primary} /> : null}
          {!storiesLoading && !items.length ? <Text color={color.text.body} style={styles.empty}>{tx('아직 공개된 기록이 없어요.', 'No public records yet.')}</Text> : null}
          {items.map((story: StoryDto) => (
            <Pressable key={story.id} accessibilityRole="button" onPress={() => router.push(`/feed/${story.id}`)} style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}>
              <Text variant="caption" color={color.text.muted}>{relativeStoryTime(story.createdAt, tx)}{story.place?.name ? ` · ${story.place.name}` : story.region ? ` · ${story.region}` : ''}</Text>
              <Text color={color.text.body} style={styles.body}>{story.body}</Text>
              {story.images.length ? <View style={styles.images}>{story.images.slice(0, 3).map((image) => <Image key={image.url} source={{ uri: image.url }} resizeMode="cover" accessibilityLabel={tx('여행 기록 사진', 'Trip record photo')} style={styles.image} />)}</View> : null}
            </Pressable>
          ))}
        </View>
      ) : null}

      <BlockUserDialog
        visible={confirmingBlock}
        displayName={state.status === 'loaded' ? state.profile.displayName : ''}
        onClose={() => setConfirmingBlock(false)}
        onConfirm={confirmBlock}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  back: { minHeight: 44, alignSelf: 'flex-start', justifyContent: 'center', marginBottom: spacing[3] },
  pressed: { opacity: 0.72 },
  stateCard: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  header: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  statRow: { flexDirection: 'row', gap: spacing[4] },
  stat: { alignItems: 'flex-start' },
  followButton: { alignSelf: 'flex-start', paddingHorizontal: spacing[4] },
  actionRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  list: { gap: spacing[3], marginTop: spacing[4] },
  empty: { textAlign: 'center', marginTop: spacing[4] },
  card: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  cardPressed: { opacity: 0.85 },
  body: { lineHeight: 22 },
  images: { flexDirection: 'row', gap: spacing[2] },
  image: { flex: 1, aspectRatio: 1, borderRadius: radius.md, backgroundColor: color.surface.soft },
});
