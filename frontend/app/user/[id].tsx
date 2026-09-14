// 다른 사용자 프로필 화면 (S15P21E201-238). GET /api/v1/users/{userId}/profile ·
// GET /api/v1/users/{userId}/stories 계약은 jaehyeon 님이 2026-09-08 axmap으로 확인해 준
// 것을 그대로 쓴다.
import { useCallback, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, View } from 'react-native';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { getUserProfile, loadUserStories, relativeStoryTime, setFollowing, type FeedLoadResult, type StoryDto, type UserProfileDto } from '@/social/stories';

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

  const items = stories.state === 'success' ? stories.items : [];

  return (
    <Screen scroll>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => (router.canGoBack() ? router.back() : router.replace('/feed'))} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
        <Text variant="title" weight="bold">‹ {tx('뒤로', 'Back')}</Text>
      </Pressable>

      {state.status === 'loading' ? (
        <View accessibilityLiveRegion="polite" style={styles.stateCard}><ActivityIndicator color={color.brand.orange} /><Text color={color.text.body}>{tx('프로필을 불러오고 있어요', 'Loading profile')}</Text></View>
      ) : null}

      {state.status === 'unavailable' ? (
        <View accessibilityRole="alert" style={styles.stateCard}>
          <Text variant="title" weight="bold">{tx('프로필을 불러오지 못했어요', "We couldn't load this profile")}</Text>
          <Text color={color.text.body}>{state.message}</Text>
          <Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void load()} />
        </View>
      ) : null}

      {state.status === 'loaded' ? (
        <View style={styles.header}>
          <Text variant="display" weight="bold">{state.profile.displayName}</Text>
          <View style={styles.statRow}>
            <View style={styles.stat}><Text variant="title" weight="bold">{state.profile.storyCount}</Text><Text variant="caption" color={color.text.muted}>{tx('기록', 'Records')}</Text></View>
            <View style={styles.stat}><Text variant="title" weight="bold">{state.profile.followerCount}</Text><Text variant="caption" color={color.text.muted}>{tx('팔로워', 'Followers')}</Text></View>
            <View style={styles.stat}><Text variant="title" weight="bold">{state.profile.followingCount}</Text><Text variant="caption" color={color.text.muted}>{tx('팔로잉', 'Following')}</Text></View>
          </View>
          {/* 문자열로 맞춰 비교한다 — 두 응답의 userId가 타입 선언과 다르게 오면(숫자 vs 문자열) !==가 늘 참이 되어 본인 프로필에도 팔로우 버튼이 뜬다. */}
          {accessToken && String(user?.userId ?? '') === String(state.profile.userId) ? (
            <Button label={tx('프로필 수정', 'Edit profile')} variant="ghost" onPress={() => router.push('/me')} containerStyle={styles.followButton} />
          ) : accessToken ? (
            <Button
              label={followBusy ? tx('처리 중…', 'Working…') : state.profile.following ? tx('팔로잉', 'Following') : tx('팔로우', 'Follow')}
              variant={state.profile.following ? 'ghost' : 'primary'}
              disabled={followBusy}
              onPress={() => void toggleFollow()}
              containerStyle={styles.followButton}
            />
          ) : null}
        </View>
      ) : null}

      {state.status === 'loaded' ? (
        <View style={styles.list}>
          {storiesLoading ? <ActivityIndicator color={color.brand.orange} /> : null}
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
  list: { gap: spacing[3], marginTop: spacing[4] },
  empty: { textAlign: 'center', marginTop: spacing[4] },
  card: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  cardPressed: { opacity: 0.85 },
  body: { lineHeight: 22 },
  images: { flexDirection: 'row', gap: spacing[2] },
  image: { flex: 1, aspectRatio: 1, borderRadius: radius.md, backgroundColor: color.surface.soft },
});
