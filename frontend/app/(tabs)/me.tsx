// 마이페이지 진입 화면.
import { useState } from 'react';
import { Animated, BackHandler, Easing, Image, Modal, Platform, Pressable, ScrollView, StyleSheet, View, useWindowDimensions } from 'react-native';
import { useEffect, useRef } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { Toggle } from '@/components/Toggle';
import { color, desktopGutter, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { isAtLeast } from '@/layout/breakpoints';
import { CoverButton, MyPageCover } from '@/me/MyPageCover';
import { loadUserStories, relativeStoryTime, type StoryDto } from '@/social/stories';
import { MyPageModal } from '@/me/MyPageModal';
import { MyPageSheetBody } from '@/me/MyPageSheet';
import { isPanelKey, myPanelBody, panelTitle, type MyPanelKey } from '@/me/myPanels';
import { MyTripCard } from '@/home/HomeBlocks';
import { HomeRow } from '@/home/HomeRow';
import { RecordCard } from '@/me/RecordCard';
import { ProfileCard, ProfileCardButton } from '@/me/ProfileCard';
import { InfoRow } from '@/me/InfoRow';
import { AppLanguageSetting } from '@/me/AppLanguageSetting';
import { useMyPageCounts } from '@/me/myPageCounts';
import { pickActiveTrip } from '@/home/useHomeData';
import { loadTrips } from '@/trip/trips';
import { useQuery } from '@tanstack/react-query';
import { useBehaviorConsent } from '@/personalization/behaviorConsent';
import { usePlan } from '@/plan/PlanProvider';
import { PREFERENCE_TOTAL } from '@/preferences/accountPreferences';

export default function Me() {
  const router = useRouter();
  const { user, signOut, accessToken } = useAuth();
  const { tx } = useI18n();
  const plan = usePlan();
  const { width, height } = useWindowDimensions();
  const { answeredPreferences, storyCount, followerCount, followingCount } = useMyPageCounts();
  const { enabled: behaviorPersonalization, setEnabled: setBehaviorPersonalization } = useBehaviorConsent(accessToken);
  const [avatarUri, setAvatarUri] = useState<string | null>(null);
  const [logoutAsk, setLogoutAsk] = useState(false);
  // 열려 있는 패널 하나. 데스크톱은 모달이, 폰은 시트가 같은 값을 받는다.
  const [panel, setPanel] = useState<MyPanelKey | null>(null);
  const params = useLocalSearchParams<{ panel?: string }>();

  /**
   * 메뉴를 눌렀을 때 — 언제나 겹쳐 연다.
   *
   * 🔴 -1331 정정 — 예전에는 「아직 안 옮긴 것」이 있어서 그때는 화면을 바꿨다. 열한 개가
   *    다 옮겨졌고 옛 주소(`app/me/…`)도 없앴다. 이제 **화면을 바꾸는 길은 없다.**
   */
  const openPanel = (key: MyPanelKey) => setPanel(key);

  /**
   * 밖에서 `/me?panel=identities` 처럼 창을 지목해 올 수 있다 — 웹 소셜 연결이 제공자에
   * 갔다 돌아오는 자리가 여기다.
   *
   * 🔴 **한 번 열고 다시는 안 연다.** 안 그러면 시트를 내려도 주소에 값이 그대로라 곧바로
   *    다시 열린다 — 사용자는 내려지지 않는 시트를 보게 된다.
   *
   * 🔴 **주소에서 값을 지우지 않는다. 지울 방법이 없어서다.** 실측(2026-09-19):
   *    {@code router.setParams} 는 이 자리에서 <b>아무 일도 안 한다</b>(주소가 그대로다).
   *    {@code router.replace('/me')} 는 주소는 지우는데 <b>화면을 다시 만들어서</b> 방금
   *    연 창이 닫힌다. 남겨 두는 편이 낫다 — 새로고침하면 그 창이 다시 열리는데, 주소로
   *    지목해 들어온 사람에게는 그게 맞는 동작이다.
   */
  const openedFromUrl = useRef<string | null>(null);
  useEffect(() => {
    const want = params.panel;
    if (typeof want !== 'string' || want === '' || openedFromUrl.current === want) return;
    openedFromUrl.current = want;
    // 모르는 값이면 아무것도 안 연다 — 주소에는 누구나 아무거나 적을 수 있다.
    if (isPanelKey(want)) setPanel(want);
  }, [params.panel]);

  // 🔴 뒤로가기는 패널을 먼저 닫는다. 안 그러면 마이페이지에서 나가 버린다 —
  //    사용자는 「목록으로 돌아가려고」 눌렀는데 앱 밖으로 나간다.
  useEffect(() => {
    if (!panel) return;
    if (Platform.OS === 'web') {
      // 웹에는 하드웨어 뒤로가기가 없다. ESC 가 그 자리를 대신한다.
      const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setPanel(null); };
      window.addEventListener('keydown', onKey);
      return () => window.removeEventListener('keydown', onKey);
    }
    const sub = BackHandler.addEventListener('hardwareBackPress', () => { setPanel(null); return true; });
    return () => sub.remove();
  }, [panel]);

  // 「내 여행」 칸 하나를 위해 홈 데이터 훅을 부르지 않는다 — 그 훅은 기록·날씨·갈래까지
  // 같이 불러온다. 여기서 필요한 것은 여행 목록 하나뿐이다.
  const tripsQuery = useQuery({
    queryKey: ['me', 'trips', user?.userId ?? 'guest'],
    enabled: Boolean(accessToken),
    queryFn: () => loadTrips(accessToken),
  });
  const trip = tripsQuery.data?.state === 'success' ? pickActiveTrip(tripsQuery.data.trips) : null;
  // 🔴 아직 못 받았으면 null 이다 — 0 과 다르다. 0 은 「하나도 안 만들었다」이고
  //    null 은 「모른다」인데, 둘을 같이 다루면 받아 오는 동안 「0번째」가 깜빡인다.
  const tripCount = tripsQuery.data?.state === 'success' ? tripsQuery.data.trips.length : null;

  useEffect(() => {
    if (!user?.userId) { setAvatarUri(null); return; }
    void AsyncStorage.getItem(`gabolle:profile-avatar:${user.userId}`).then(setAvatarUri);
  }, [user?.userId]);

  const wide = isAtLeast(width, 'lg');
  // 시안 06 은 화면을 거의 다 채운다 — 아래 띄움과 위 틈을 뺀 나머지.
  // 숫자를 박지 않는다. 화면 높이는 기기마다 다르다.
  const sheetHeight = Math.max(320, height - spacing[2] - spacing[8]);

  const name = user?.displayName || tx('여행자', 'Traveler');
  const none = tx('아직 없음 ›', 'None yet ›');

  // 넓은 화면 기록 줄은 4열이다 — 홈의 7열과 다르다. 화살표가 한 장씩 밀려면
  // 이 값을 줄에도 같이 줘야 한다.
  const recordRowCardWidth = Math.max(180, Math.floor((width - desktopGutter * 2 - 3 * spacing[4]) / 4));

  // 폰의 「기록 | 설정」 — 주소는 안 바뀐다. 내용만 갈아 끼운다.
  const [meTab, setMeTab] = useState<'records' | 'settings'>('records');
  const tabIn = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    // 탭을 눌렀는데 내용이 «즉시» 갈리면 바뀐 줄 모르고 지나간다. 같은 자리에 같은
    // 크기의 흰 화면이 있기 때문이다 — 시작 바 칸 전환과 같은 이유다.
    tabIn.setValue(0);
    Animated.timing(tabIn, { toValue: 1, duration: 300, easing: Easing.bezier(0.22, 1, 0.36, 1), useNativeDriver: false }).start();
  }, [meTab, tabIn]);

  // 내 기록. 숫자(storyCount)만으로는 격자를 못 그린다 — 실제 글이 필요하다.
  const myStoriesQuery = useQuery({
    queryKey: ['me', 'stories', user?.userId ?? ''],
    enabled: Boolean(user?.userId),
    queryFn: () => loadUserStories(user?.userId ?? '', accessToken),
  });
  // 실패를 빈 배열로 바꾸지 않는다 — 「아직 기록이 없어요」와 「못 불러왔다」는 다른 말이다.
  const myStories: StoryDto[] | null = myStoriesQuery.data?.state === 'success' ? myStoriesQuery.data.items : null;

  // 두 배치가 같은 카드를 쓴다. 두 벌로 만들면 한쪽만 고쳐지고, 그 차이는 두 폭을
  // 나란히 열어 봐야만 보인다.
  const accountGroup = <>
    <View style={styles.group}>
      {/* 🔴 「내 기록」 행은 여기 없다 — 폰은 「기록」 탭이, 넓은 화면은 커버 아래 기록 줄이
          그 일을 한다. 같은 곳으로 가는 길을 둘 두면 어느 쪽이 진짜인지 헷갈리고,
          한쪽만 고쳐졌을 때 서로 다른 것을 보여 준다. 되살리지 마라 — S15P21E201-1379. */}
      {/* 사용자 리포트: "마이페이지에 저장 누르면 저장했던 피드들 뜨게" — S15P21E201-1221. */}
      <InfoRow
        first
        label={tx('저장한 기록', 'Saved records')}
        value="›"
        onPress={() => openPanel('saved')}
        disabled={!user}
      />
      {/* 🔴 「팔로워」「팔로잉」 행도 여기 없다 — 위 프로필 카드의 타일(기록·팔로워·팔로잉)이 같은 곳으로
          간다(S15P21E201-1331). 같은 문이 둘이면 한쪽만 고쳐진다 — 「내 기록」과 같은 이유(1379). 되살리지 마라 — S15P21E201-1390. */}
      <InfoRow
        label={tx('여행 취향', 'Travel preferences')}
        value={answeredPreferences === null
          ? '›'
          : answeredPreferences > 0
            ? tx(`${answeredPreferences} / ${PREFERENCE_TOTAL} 답함 ›`, `${answeredPreferences} / ${PREFERENCE_TOTAL} answered ›`)
            : none}
        onPress={() => openPanel('preferences')}
        disabled={!user}
      />
      <InfoRow label={tx('연결된 소셜 계정', 'Connected accounts')} value="›" onPress={() => openPanel('identities')} disabled={!user} />
      {/* 백엔드(DELETE /me)도 흐름도 있는데 프로필 편집 맨 아래에만 있어 설정에서 안 보였다(2026-09-21 실기, S15P21E201-1401). */}
      <InfoRow label={tx('회원 탈퇴', 'Delete account')} description={tx('여행, 기록, 취향이 모두 지워져요', 'Deletes your trips, records, and preferences')} value="›" onPress={() => openPanel('delete-account')} disabled={!user} />
    </View>
  </>;

  const appGroup = <>
    <View style={styles.group}>
      <AppLanguageSetting />
      {/* — 알림·차단된 계정처럼 스토어 심사가 보는 기본 기능이 마이페이지
          안에서 안 보였다는 사용자 리포트. 알림 화면은 이미 있다(app/notifications.tsx
          홈 종 아이콘) — 여기서는 같은 화면으로 가는 입구만 하나 더 둔다.
      */}
      <InfoRow label={tx('알림', 'Notifications')} value="›" onPress={() => openPanel('notifications')} />
      <InfoRow label={tx('차단된 계정', 'Blocked accounts')} value="›" onPress={() => openPanel('blocked')} disabled={!user} />
      <InfoRow label={tx('도움말·문의', 'Help & support')} description={tx('앱 소개, 자주 묻는 질문, 문제 해결', 'App tour, FAQs, and troubleshooting')} value="›" onPress={() => openPanel('help')} />
      <InfoRow label={tx('약관·고지', 'Terms & notices')} value="›" onPress={() => openPanel('terms')} />
      {/* 처음 켜는 자리는 첫 체크인 화면이고, 여기는 언제든 끄는 자리다. 끄는 길이 설정
          안쪽 어딘가에만 있으면 사용자는 못 찾고, 못 찾으면 켠 적 없는 사람처럼 취급된다.
      */}
      <View style={styles.consentRow}>
        <View style={styles.consentCopy}>
          <Text weight="bold">{tx('맞춤 추천', 'Personalized picks')}</Text>
          <Text variant="caption">{tx('저장·제외·일정 수정·체크인 후기 같은 활동을 바탕으로 추천을 맞춰요. 이 설정은 이 기기에 저장돼요.', 'We tune your picks using activity like saves, exclusions, itinerary edits, and check-in reviews. This setting is stored on this device.')}</Text>
        </View>
        <Toggle value={behaviorPersonalization} onValueChange={setBehaviorPersonalization} />
      </View>
    </View>
  </>;

  const logoutButton = user ? <Button label={tx('로그아웃', 'Sign out')} variant="tertiary" onPress={() => setLogoutAsk(true)} containerStyle={styles.logout} /> : <View style={styles.guestActions}><Button label={tx('로그인', 'Sign in')} onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/me' } })} /><Button label={tx('회원가입', 'Create account')} variant="tertiary" onPress={() => router.push({ pathname: '/sign-up', params: { returnTo: '/me' } })} /></View>;

  const logoutModal = <Modal visible={logoutAsk} transparent animationType="fade" onRequestClose={() => setLogoutAsk(false)}>
      <View style={styles.modalBackdrop}><View accessibilityViewIsModal style={styles.modalCard}>
        <Text variant="title" weight="bold">{tx('로그아웃할까요?', 'Sign out?')}</Text>
        <Text>{tx('여행과 기록은 계정에 그대로 남아요.', 'Your trips and records stay on your account.')}</Text>
        <View style={styles.modalActions}>
          <Button label={tx('취소', 'Cancel')} variant="tertiary" onPress={() => setLogoutAsk(false)} containerStyle={styles.modalAction} />
          <Button label={tx('로그아웃', 'Sign out')} onPress={() => void (async () => { setLogoutAsk(false); await signOut(); await plan.clear(); })()} containerStyle={styles.modalAction} />
        </View>
      </View></View>
    </Modal>;

  // ── 넓은 화면 — 커버 전폭 + 3열 (시안 01) ────────────────────────────────
  //
  // 🔴 Screen 을 안 쓴다. 그 껍데기는 좌우 여백을 넣어 주는데, 그 안에서는 커버 사진이
  //    화면 끝까지 못 간다. 데스크톱 홈이 이미 같은 이유로 직접 스크롤을 깐다.
  if (wide) {
    return <View style={styles.shell}>
      <ScrollView style={styles.wideScroll} contentContainerStyle={styles.wideContent}>
        <MyPageCover
          name={name}
          email={user?.email ?? null}
          tripCount={tripCount}
          avatarUri={avatarUri}
          coverUri={user?.coverUrl ?? null}
          // 로그인 전에도 눌린다 — 「–」 타일이 아무 반응이 없으면 고장으로 읽힌다. 로그인하고 그 패널로 돌아온다.
          counts={[
            { label: tx('기록', 'Records'), value: storyCount, onPress: () => (user ? openPanel('posts') : router.push({ pathname: '/sign-in', params: { returnTo: '/me?panel=posts' } })) },
            { label: tx('팔로워', 'Followers'), value: followerCount, onPress: () => (user ? openPanel('followers') : router.push({ pathname: '/sign-in', params: { returnTo: '/me?panel=followers' } })) },
            { label: tx('팔로잉', 'Following'), value: followingCount, onPress: () => (user ? openPanel('following') : router.push({ pathname: '/sign-in', params: { returnTo: '/me?panel=following' } })) },
          ]}
          eyebrow={<Eyebrow>{tx('내 계정', 'Account')}</Eyebrow>}
          actions={(
            <CoverButton
              label={tx('프로필 편집', 'Edit profile')}
              onPress={() => (user ? openPanel('profile') : router.push({ pathname: '/sign-in', params: { returnTo: '/me?panel=profile' } }))}
            />
          )}
          tx={tx}
        />

        {/* 🔴 커버와 3열 사이에 기록 줄. 전에는 「내 기록 ›」 행이 이 일을 했는데,
            그 행은 눌러서 모달을 열어야만 무엇이 있는지 보였다 — 자기 기록인데도
            «몇 개 있는지»만 알고 «무엇을 썼는지»는 한 번 더 눌러야 했다. */}
        <HomeRow
          title={storyCount === null
            ? txf(tx, '%s의 기록', "%s's records", name)
            : txf(tx, '%s의 기록 %s개', "%s's records · %s", name, String(storyCount))}
          onOpen={() => openPanel('posts')}
          openLabel={tx('기록 전체 보기', 'See all records')}
          width={width}
          cardWidth={recordRowCardWidth}
        >
          {(myStories ?? []).map((story) => (
            <RecordCard key={story.id} story={story} width={recordRowCardWidth} onPress={() => router.push(`/feed/${story.id}`)} tx={tx} />
          ))}
          {user ? (
            <Pressable accessibilityRole="button" onPress={() => router.push('/feed')} style={[styles.recordNew, { width: recordRowCardWidth }]}>
              <View style={styles.recordNewIcon}><Text weight="bold" color={color.text.onAction}>✎</Text></View>
              <Text weight="bold" numberOfLines={1}>{tx('새 기록 남기기', 'Write a record')}</Text>
              <Text variant="caption" color={color.text.muted} numberOfLines={1}>{tx('사진 3장까지', 'Up to 3 photos')}</Text>
            </Pressable>
          ) : null}
        </HomeRow>

        <View style={styles.wideGrid}>
          <View style={styles.wideColumn}>
            <Eyebrow>{tx('내 계정', 'Account')}</Eyebrow>
            {accountGroup}
          </View>
          <View style={styles.wideColumn}>
            <Eyebrow>{tx('앱', 'App')}</Eyebrow>
            {appGroup}
          </View>
          <View style={styles.wideColumn}>
            {/* 🔴 여기에 「내 여행」 눈썹을 붙이지 않는다. MyTripCard 가 같은 것을 스스로
                그린다 — 붙이면 같은 말이 두 줄로 겹친다. 옆의 두 칸과 다르게 생긴 것이
                아니라, 제목을 그리는 쪽이 다를 뿐이다. */}
            <MyTripCard trip={trip} signedIn={Boolean(user)} loaded={!tripsQuery.isLoading} />
            {logoutButton}
          </View>
        </View>
      </ScrollView>
      {logoutModal}
      {panel ? (
        <MyPageModal
          open
          title={panelTitle(panel, tx).title}
          description={panelTitle(panel, tx).description}
          onClose={() => setPanel(null)}
          tx={tx}
        >
          {myPanelBody(panel)}
        </MyPageModal>
      ) : null}
    </View>;
  }

  return <View style={styles.shell}><Screen scroll withTabBar>
    <View style={styles.heading}><Eyebrow>{tx('내 계정', 'Account')}</Eyebrow><Text variant="display" weight="bold">{tx('마이페이지', 'My page')}</Text></View>

    {/* 시안의 프로필 카드. 전에는 한 줄짜리 띠였고, 넓은 화면에서는
        이 화면이 아예 안 보였다. 없는 값(한 줄 소개·거주지)은 줄 자체를 안 그린다
        서버에 그 칸이 아직 없다.
    */}
    <ProfileCard
      name={name}
      email={user?.email ?? null}
      avatarUri={avatarUri}
      // 🔴 안 고른 사람에게는 서버가 이 칸을 **아예 안 보낸다**(S15P21E201-1297). 그래서
      //    여기서 null 이 되고, 부품이 기본 사진을 깐다 — 기본 사진을 고르는 것은 화면의 몫이다.
      coverUri={user?.coverUrl ?? null}
      counts={[
        // 🔴 -1331 — 여기만 겹쳐 열기로 안 옮겨져 있었다. 폰에서 이 숫자를 누르면
        //    시트가 아니라 옛 전체 페이지로 넘어갔다. 같은 화면에서 어떤 것은 겹쳐
        //    열리고 어떤 것은 화면이 바뀌는 상태였다.
        { label: tx('기록', 'Records'), value: storyCount, onPress: () => (user ? openPanel('posts') : router.push({ pathname: '/sign-in', params: { returnTo: '/me?panel=posts' } })) },
        { label: tx('팔로워', 'Followers'), value: followerCount, onPress: () => (user ? openPanel('followers') : router.push({ pathname: '/sign-in', params: { returnTo: '/me?panel=followers' } })) },
        { label: tx('팔로잉', 'Following'), value: followingCount, onPress: () => (user ? openPanel('following') : router.push({ pathname: '/sign-in', params: { returnTo: '/me?panel=following' } })) },
      ]}
      actions={(
        <ProfileCardButton
          label={tx('프로필 편집', 'Edit profile')}
          onPress={() => (user ? openPanel('profile') : router.push({ pathname: '/sign-in', params: { returnTo: '/me?panel=profile' } }))}
        />
      )}
      tx={tx}
    />

    {/* 「기록 | 설정」 — 주소를 안 바꾸고 내용만 갈아 끼운다. 설정을 보러 들어온 사람이
        기록을 스크롤해 지나가지 않아도 되고, 기록을 보러 온 사람이 설정 목록을 안 본다. */}
    <View style={styles.segment}>
      {(['records', 'settings'] as const).map((key) => (
        <Pressable
          key={key}
          accessibilityRole="tab"
          accessibilityState={{ selected: meTab === key }}
          onPress={() => setMeTab(key)}
          style={[styles.segmentItem, meTab === key && styles.segmentItemOn]}
        >
          <Text weight="bold" color={meTab === key ? color.text.onAction : color.text.body} numberOfLines={1}>
            {key === 'settings'
              ? tx('설정', 'Settings')
              : storyCount === null ? tx('기록', 'Records') : tx(`기록 ${storyCount}`, `Records ${storyCount}`)}
          </Text>
        </Pressable>
      ))}
    </View>

    <Animated.View style={{ opacity: tabIn, transform: [{ translateY: tabIn.interpolate({ inputRange: [0, 1], outputRange: [10, 0] }) }] }}>
      {meTab === 'records' ? (
        <View style={styles.recordsGrid}>
          {(myStories ?? []).map((story) => (
            <RecordCard key={story.id} story={story} onPress={() => router.push(`/feed/${story.id}`)} tx={tx} />
          ))}
          {/* 남의 프로필에는 이 칸이 없다 — 그건 /user/[id] 가 따로 그린다. */}
          {user ? (
            <Pressable accessibilityRole="button" onPress={() => router.push('/feed')} style={styles.recordNew}>
              <View style={styles.recordNewIcon}><Text weight="bold" color={color.text.onAction}>✎</Text></View>
              <Text weight="bold" numberOfLines={1}>{tx('새 기록 남기기', 'Write a record')}</Text>
              <Text variant="caption" color={color.text.muted} numberOfLines={1}>{tx('사진 3장까지', 'Up to 3 photos')}</Text>
            </Pressable>
          ) : null}
          {/* 못 불러온 것을 「없다」로 바꾸지 않는다. */}
          {user && myStories === null && !myStoriesQuery.isPending ? (
            <Text variant="caption" color={color.text.muted}>{tx('기록을 불러오지 못했어요.', "We couldn't load your records.")}</Text>
          ) : null}
        </View>
      ) : (
        <>
          <Text variant="eyebrow" weight="bold" style={styles.groupLabel}>{tx('내 계정', 'Account')}</Text>
          {accountGroup}
          <Text variant="eyebrow" weight="bold" style={styles.groupLabel}>{tx('앱', 'App')}</Text>
          {appGroup}
          {user ? <Button label={tx('로그아웃', 'Sign out')} variant="tertiary" onPress={() => setLogoutAsk(true)} containerStyle={styles.logout} /> : <View style={styles.guestActions}><Button label={tx('로그인', 'Sign in')} onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/me' } })} /><Button label={tx('회원가입', 'Create account')} variant="tertiary" onPress={() => router.push({ pathname: '/sign-up', params: { returnTo: '/me' } })} /></View>}
        </>
      )}
    </Animated.View>

    <Modal visible={logoutAsk} transparent animationType="fade" onRequestClose={() => setLogoutAsk(false)}>
      <View style={styles.modalBackdrop}><View accessibilityViewIsModal style={styles.modalCard}>
        <Text variant="title" weight="bold">{tx('로그아웃할까요?', 'Sign out?')}</Text>
        <Text>{tx('여행과 기록은 계정에 그대로 남아요.', 'Your trips and records stay on your account.')}</Text>
        <View style={styles.modalActions}>
          <Button label={tx('취소', 'Cancel')} variant="tertiary" onPress={() => setLogoutAsk(false)} containerStyle={styles.modalAction} />
          <Button label={tx('로그아웃', 'Sign out')} onPress={() => void (async () => { setLogoutAsk(false); await signOut(); await plan.clear(); })()} containerStyle={styles.modalAction} />
        </View>
      </View></View>
    </Modal>
  </Screen>

    {/* 🔴 시트가 열려 있으면 뒤를 가린다. 안 가리면 시트를 문지르다 뒤 화면이 따라 움직이고,
        손가락이 뒤 메뉴를 누른다. 누르면 닫힌다. */}
    {panel ? (
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={tx('내리기', 'Close')}
        onPress={() => setPanel(null)}
        style={styles.sheetBackdrop}
      />
    ) : null}

    <TabBar
      active="me"
      expanded={Boolean(panel)}
      onCollapse={() => setPanel(null)}
      sheetHeight={sheetHeight}
      children={panel ? (
        <MyPageSheetBody
          title={panelTitle(panel, tx).title}
          description={panelTitle(panel, tx).description}
          onClose={() => setPanel(null)}
          tx={tx}
        >
          {myPanelBody(panel)}
        </MyPageSheetBody>
      ) : undefined}
    />
  </View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.canvas },

  // ── 넓은 화면 (시안 01) ───────────────────────────────────────────────────
  //
  // 🔴 Screen 을 안 쓰므로 좌우 여백도 여기서 직접 준다. 커버는 여백 없이 전폭이고,
  //    아래 3열만 좌우 40(desktopGutter)을 받는다.
  // 시트 뒤를 가리는 어둠막. 탭바(시트)보다 낮고 내용보다 높다.
  sheetBackdrop: {
    position: Platform.OS === 'web' ? ('fixed' as 'absolute') : 'absolute',
    top: 0, left: 0, right: 0, bottom: 0,
    backgroundColor: 'rgba(25,25,25,0.62)',
    zIndex: 20,
  },

  wideScroll: { flex: 1, backgroundColor: color.canvas },
  wideContent: { minHeight: '100%' },
  wideGrid: {
    flexDirection: 'row', alignItems: 'flex-start', gap: spacing[6],
    paddingTop: spacing[8], paddingHorizontal: desktopGutter, paddingBottom: 64,
  },
  // 세 칸이 같은 폭을 나눠 가진다. 칸마다 내용 길이가 달라 위쪽을 맞춘다.
  wideColumn: { flex: 1, minWidth: 0, gap: spacing[2] },
  heading: { gap: spacing[2], marginBottom: spacing[6] },
  profile: { flexDirection: 'row', alignItems: 'center', gap: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  pressed: { opacity: 0.7 },
  avatar: { width: 56, height: 56, overflow: 'hidden', borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },
  avatarPhoto: { width: '100%', height: '100%' },
  profileCopy: { flex: 1, gap: spacing[1], minWidth: 0 },

  groupLabel: { marginTop: spacing[6], marginBottom: spacing[2] },
  group: { overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card },
  consentRow: { minHeight: 62, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], paddingVertical: spacing[3], borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.surface.border },
  consentCopy: { flex: 1, gap: spacing[1] },

  // borderColor 를 여기서 뺐다. 이 스타일은 Button 의 containerStyle 로
  // 가는데, 껍데기에는 borderWidth 가 없어서 색만 있는 테두리는 아무것도 안 그린다.
  // 즉 주황 테두리는 처음부터 화면에 없었다 — 보이는 것은 ghost 의 회색 테두리다.
  // 주황으로 하려면 Button 에 그 variant 가 있어야 한다. 여기서 흉내내면 버튼 뒤에
  // 도형이 하나 더 남을 뿐이다.
  logout: { marginTop: spacing[6], marginBottom: spacing[4] },
  guestActions: { gap: spacing[2], marginTop: spacing[6], marginBottom: spacing[4] },

  modalBackdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  modalCard: { width: '100%', maxWidth: 400, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  modalActions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] },
  modalAction: { flex: 1 },

  // ── 폰 「기록 | 설정」 ────────────────────────────────────────────────────
  segment: { flexDirection: 'row', padding: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft, marginTop: spacing[6] },
  segmentItem: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full },
  segmentItemOn: { backgroundColor: color.action.primary },

  // 🔴 스크롤 칸 안이라 flex 를 안 쓴다. 쓰면 카드가 세로로 눌린다.
  recordsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3], marginTop: spacing[3] },
  recordNew: {
    width: '48%', aspectRatio: 0.78, alignItems: 'center', justifyContent: 'center', gap: spacing[2],
    borderRadius: radius.lg, borderWidth: 1, borderStyle: 'dashed', borderColor: color.surface.field,
  },
  recordNewIcon: { width: 40, height: 40, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.primary },
});
