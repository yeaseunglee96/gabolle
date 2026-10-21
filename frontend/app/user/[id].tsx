// 남의 프로필 — `/me` 와 «같은 부품, 다른 단추»다.
//
// 🔴 전에는 이 화면만 다르게 생겼다. 자기 프로필은 커버 사진 위에 이름이 얹히고 기록이
//    격자로 깔리는데, 남의 프로필은 흰 카드에 이름과 숫자 셋이 서고 기록이 세로 목록이었다.
//    같은 「프로필」인데 두 모양이면, 사용자는 둘이 다른 «종류»의 화면이라고 배운다.
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View, useWindowDimensions } from 'react-native';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, desktopGutter, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';
import { CoverButton, MyPageCover } from '@/me/MyPageCover';
import { ProfileCard, ProfileCardButton } from '@/me/ProfileCard';
import { RecordCard, recordPhoneGrid } from '@/me/RecordCard';
import { BlockUserDialog } from '@/social/BlockUserDialog';
import { getUserProfile, loadUserStories, setBlocked, setFollowing, type FeedLoadResult, type UserProfileDto } from '@/social/stories';
import { localizeMessage } from '@/i18n/messages';

type ProfileState = { status: 'loading' } | { status: 'loaded'; profile: UserProfileDto } | { status: 'unavailable'; message: string };

export default function UserProfile() {
  const router = useRouter();
  const { accessToken, user } = useAuth();
  const { tx } = useI18n();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { width } = useWindowDimensions();
  // 데스크톱 판인가 — 폭만이 아니라 폴드 펼침 가로까지, 판정은 useLayout 한 곳(S15P21E201-1563).
  const wide = useLayout().desktop;
  // 남의 프로필은 화살표 없이 전부 펼친다(시안 3절) — 내 기록 줄과 같은 4열 폭을 쓴다.
  const cardWidth = Math.max(180, Math.floor((width - desktopGutter * 2 - 3 * spacing[4]) / 4));
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
    // 🔴 S15P21E201-1722 — S15P21E201-1714 로 차단이 양방향이 됐는데 이 안내는 한 방향만 말하고 있었다.
    setBlockNotice(tx(
      '이제 이 사용자에게 내 글이 안 보이고, 내 피드에도 이 사람 글이 안 보여요.',
      "This user can no longer see your posts, and their posts won't show up in your feed either.",
    ));
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
  const profile = state.status === 'loaded' ? state.profile : null;
  // 문자열로 맞춰 비교한다 — 두 응답의 userId 가 타입 선언과 다르게 오면(숫자 vs 문자열)
  // !== 가 늘 참이 되어 본인 프로필에도 팔로우 단추가 뜬다.
  const mine = Boolean(accessToken && profile && String(user?.userId ?? '') === String(profile.userId));
  const goBack = () => (router.canGoBack() ? router.back() : router.replace('/feed'));

  const counts = profile ? [
    { label: tx('기록', 'Records'), value: profile.storyCount },
    { label: tx('팔로워', 'Followers'), value: profile.followerCount, onPress: () => router.push(`/user/${id}/followers`) },
    { label: tx('팔로잉', 'Following'), value: profile.followingCount, onPress: () => router.push(`/user/${id}/following`) },
  ] : [];

  const followLabel = followBusy ? tx('처리 중…', 'Working…') : profile?.following ? tx('팔로잉', 'Following') : tx('팔로우', 'Follow');
  const blockLabel = blockBusy ? tx('처리 중…', 'Working…') : profile?.blocked ? tx('차단 해제', 'Unblock') : tx('차단하기', 'Block');
  const onBlockPress = () => (profile?.blocked ? void unblock() : setConfirmingBlock(true));
  const canAct = Boolean(accessToken && profile && !profile.blockedByUser);

  // 커버 오른쪽 단추. 내 프로필이면 편집 하나, 남이면 팔로우 + 차단 둘이다.
  // 🔴 「팔로잉」으로 바뀌면 동백을 뺀다 — 이미 한 일은 「그다음에 할 일」이 아니다.
  const coverActions = !profile ? null : mine ? (
    <CoverButton label={tx('프로필 편집', 'Edit profile')} onPress={() => router.push('/me')} />
  ) : canAct ? (
    <>
      <CoverButton label={followLabel} tone={profile.following ? 'light' : 'primary'} disabled={followBusy || Boolean(profile.blocked)} onPress={() => void toggleFollow()} />
      <CoverButton label={blockLabel} disabled={blockBusy} onPress={onBlockPress} />
    </>
  ) : null;

  const cardActions = !profile ? null : mine ? (
    <ProfileCardButton label={tx('프로필 편집', 'Edit profile')} onPress={() => router.push('/me')} />
  ) : canAct ? (
    <>
      <ProfileCardButton label={followLabel} tone={profile.following ? 'outline' : 'primary'} disabled={followBusy || Boolean(profile.blocked)} onPress={() => void toggleFollow()} />
      <ProfileCardButton label={blockLabel} tone="outline" disabled={blockBusy} onPress={onBlockPress} />
    </>
  ) : null;

  // 🔴 남의 프로필에는 「새 기록」 칸이 없다 — 남의 자리에 내 글을 쓰는 입구를 두지 않는다.
  const recordsGrid = (
    <View style={[wide ? styles.grid : recordPhoneGrid, styles.gridTop]}>
      {storiesLoading ? <ActivityIndicator color={color.action.primary} /> : null}
      {!storiesLoading && !items.length ? <Text color={color.text.body} style={styles.empty}>{tx('아직 공개된 기록이 없어요.', 'No public records yet.')}</Text> : null}
      {items.map((story) => (
        <RecordCard key={story.id} story={story} width={wide ? cardWidth : undefined} onPress={() => router.push(`/feed/${story.id}`)} tx={tx} />
      ))}
    </View>
  );

  // 차단당한 쪽이 보는 화면. 빈 화면도 404 도 아니다 — 없는 사람으로 만들면 실수로
  // 눌렀을 때 상대가 계정이 사라졌다고 오해하고 되돌릴 길이 막힌다.
  const blockedNotice = profile?.blockedByUser ? (
    <View accessibilityRole="alert" style={styles.stateCard}>
      <Text variant="title" weight="bold">{tx('차단되어 볼 수 없습니다', 'Blocked — you cannot view this profile')}</Text>
    </View>
  ) : null;

  const dialog = (
    <BlockUserDialog
      visible={confirmingBlock}
      displayName={profile?.displayName ?? ''}
      onClose={() => setConfirmingBlock(false)}
      onConfirm={confirmBlock}
    />
  );

  // ── 넓은 화면 — /me 와 같은 전폭 커버 ────────────────────────────────────
  // Screen 을 안 쓴다. 그 껍데기가 좌우 여백을 넣어 커버가 화면 끝까지 못 간다 — /me 와 같다.
  if (wide && profile && !profile.blockedByUser) {
    return (
      <View style={styles.shell}>
        <ScrollView contentContainerStyle={styles.wideContent}>
          <MyPageCover
            name={profile.displayName}
            // 🔴 남의 이메일은 안 보여 준다. 여행 횟수 칸은 서버 응답에 «아직 없다» — null 을 주면
            //    「부산 여행 N번째」 줄은 안 그린다. 아바타(S15P21E201-1821)·배경 사진(-1842)은 서버가 준다 —
            //    배경 사진이 없으면(안 골랐거나 배포 전) 부품이 기본 사진을 깐다.
            email={null}
            tripCount={null}
            avatarUri={profile.avatarUrl ?? null}
            coverUri={profile.coverUrl ?? null}
            counts={counts}
            eyebrow={(
              <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={goBack}>
                <Eyebrow>{`‹ ${tx('뒤로', 'Back')}`}</Eyebrow>
              </Pressable>
            )}
            actions={coverActions}
            tx={tx}
          />
          <View style={styles.wideBody}>
            {blockNotice ? <Text accessibilityLiveRegion="polite" variant="caption" color={color.text.body}>{blockNotice}</Text> : null}
            {recordsGrid}
          </View>
        </ScrollView>
        {dialog}
      </View>
    );
  }

  return (
    <Screen scroll>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={goBack} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
        <Text variant="title" weight="bold">‹ {tx('뒤로', 'Back')}</Text>
      </Pressable>

      {state.status === 'loading' ? (
        <View accessibilityLiveRegion="polite" style={styles.stateCard}><ActivityIndicator color={color.action.primary} /><Text color={color.text.body}>{tx('프로필을 불러오고 있어요', 'Loading profile')}</Text></View>
      ) : null}

      {/* 🔴 손님에게 서버는 401 을 준다(2026-09-21 배포본 실측, S15P21E201-1372). 그것을 「인증 정보가 올바르지 않습니다」라고
          옮겨 적으면 고장으로 읽힌다 — 고장이 아니라 문이 잠긴 것이니 문을 준다. 서버가 프로필을 익명에게 열면 이 갈래는 안 탄다. */}
      {state.status === 'unavailable' && !accessToken ? (
        <View style={styles.stateCard}>
          <Text variant="title" weight="bold">{tx('로그인하면 프로필을 볼 수 있어요', 'Sign in to see this profile')}</Text>
          <Text color={color.text.body}>{tx('기록은 누구나 볼 수 있지만, 작성자의 프로필과 팔로우는 로그인한 분에게만 열려요.', 'Anyone can read records, but profiles and following are for signed-in members.')}</Text>
          <Button label={tx('로그인', 'Sign in')} onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: `/user/${id}` } })} containerStyle={styles.stateButton} />
        </View>
      ) : state.status === 'unavailable' ? (
        <View accessibilityRole="alert" style={styles.stateCard}>
          <Text variant="title" weight="bold">{tx('프로필을 불러오지 못했어요', "We couldn't load this profile")}</Text>
          <Text color={color.text.body}>{localizeMessage(tx, state.message)}</Text>
          <Button compact label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void load()} />
        </View>
      ) : null}

      {profile && !profile.blockedByUser ? (
        <>
          <ProfileCard
            name={profile.displayName}
            email={null}
            avatarUri={profile.avatarUrl ?? null}
            coverUri={profile.coverUrl ?? null}
            counts={counts}
            actions={cardActions}
            tx={tx}
          />
          {blockNotice ? <Text accessibilityLiveRegion="polite" variant="caption" color={color.text.body} style={styles.notice}>{blockNotice}</Text> : null}
          {recordsGrid}
        </>
      ) : null}

      {blockedNotice}
      {dialog}
    </Screen>
  );
}

const styles = StyleSheet.create({
  back: { minHeight: 44, alignSelf: 'flex-start', justifyContent: 'center', marginBottom: spacing[3] },
  pressed: { opacity: 0.72 },
  stateButton: { alignSelf: 'stretch' },
  stateCard: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  notice: { marginTop: spacing[3] },

  // 넓은 화면 — /me 와 같은 뼈대.
  shell: { flex: 1, backgroundColor: color.canvas },
  wideContent: { paddingBottom: 64 },
  wideBody: { paddingHorizontal: desktopGutter, paddingTop: spacing[8] },

  // 🔴 남의 프로필은 화살표 없이 전부 펼친다(시안 3절). 폰은 폭을 안 줘서 2열이 된다.
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[4] },
  gridTop: { marginTop: spacing[4] },
  empty: { textAlign: 'center', marginTop: spacing[4] },
});
