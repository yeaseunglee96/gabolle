// 마이페이지 진입 화면.
import { loadProfileAvatar } from '@/me/profileAvatar';
import { useState } from 'react';
import { Animated, BackHandler, Easing, Image, Modal, Platform, Pressable, ScrollView, StyleSheet, View, useWindowDimensions } from 'react-native';
import { useEffect, useRef, type ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { Toggle } from '@/components/Toggle';
import { color, desktopGutter, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { useLayout } from '@/layout/useLayout';
import { CoverButton, MyPageCover } from '@/me/MyPageCover';
import { loadUserStories, relativeStoryTime, type StoryDto } from '@/social/stories';
import { KeptPanes } from '@/me/KeptPanes';
import { MyPageModal } from '@/me/MyPageModal';
import { RecordsLoadFailed } from '@/me/RecordsLoadFailed';
import { MyPageSheetBody, myPageSheetHeight } from '@/me/MyPageSheet';
import { isPanelKey, myPanelBody, panelTitle, type MyPanelKey } from '@/me/myPanels';
import { MyTripCard } from '@/home/HomeBlocks';
import { RecordsBrowser } from '@/me/RecordsBrowser';
import { ProfileCard, ProfileCardButton } from '@/me/ProfileCard';
import { InfoRow } from '@/me/InfoRow';
import { AppLanguageSetting } from '@/me/AppLanguageSetting';
import { useMyPageCounts } from '@/me/myPageCounts';
import { pickActiveTrip } from '@/home/useHomeData';
import { loadTrips } from '@/trip/trips';
import { useQuery } from '@tanstack/react-query';
import { useBehaviorConsent } from '@/personalization/behaviorConsent';
import { useLocationConsent } from '@/personalization/locationConsent';
import { usePlan } from '@/plan/PlanProvider';
import { PREFERENCE_TOTAL } from '@/preferences/accountPreferences';

export default function Me() {
  const router = useRouter();
  const { user, signOut, accessToken } = useAuth();
  const { tx, locale } = useI18n();
  const plan = usePlan();
  const { width, height } = useWindowDimensions();
  const { answeredPreferences, storyCount, followerCount, followingCount } = useMyPageCounts();
  const { enabled: behaviorPersonalization, setEnabled: setBehaviorPersonalization } = useBehaviorConsent(accessToken);
  const location = useLocationConsent(accessToken);
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
    // 🔴 계정에 붙은 사진이 «먼저»다. 여기가 기기 저장소만 읽어서, 사진을 올려도
    //    마이페이지는 끝까지 첫 글자 동그라미였다 — 편집 화면에서는 바뀌어 보이는데
    //    나오면 그대로라 「저장이 안 됐나」로 읽혔다(팀원 실기 지적).
    let active = true;
    void loadProfileAvatar(user?.userId ?? null, user?.avatarUrl).then((next) => { if (active) setAvatarUri(next); });
    return () => { active = false; };
  }, [user?.userId, user?.avatarUrl]);

  // 데스크톱 판인가 — 폭만이 아니라 폴드 펼침 가로까지, 판정은 useLayout 한 곳(S15P21E201-1563).
  const wide = useLayout().desktop;
  // 시트 높이 계산은 myPageSheetHeight 가 소유한다 — 왜 위쪽 안전영역을 빼는지도 거기 적혀 있다.
  const insets = useSafeAreaInsets();
  const sheetHeight = myPageSheetHeight(height, insets.top, insets.bottom);

  const name = user?.displayName || tx('여행자', 'Traveler');
  const none = tx('아직 없음 ›', 'None yet ›');

  // 넓은 화면 기록 줄은 4열이다 — 홈의 7열과 다르다. 화살표가 한 장씩 밀려면
  // 이 값을 줄에도 같이 줘야 한다.
  const recordRowCardWidth = Math.max(180, Math.floor((width - desktopGutter * 2 - 3 * spacing[4]) / 4));

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
      {/* 원글이 지워지거나 가려지면 내 댓글로 가는 길이 없어진다 — 여기서 모아 찾고 지운다(S15P21E201-1652). */}
      <InfoRow label={tx('내 댓글', 'My comments')} value="›" onPress={() => openPanel('replies')} disabled={!user} />
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
      {/* 처음 켜는 자리는 첫 체크인 화면이고, 여기는 언제든 끄는 자리다. 끄는 길이 설정
          안쪽 어딘가에만 있으면 사용자는 못 찾고, 못 찾으면 켠 적 없는 사람처럼 취급된다.
          🔴 「앱」 묶음 맨 아래(약관·고지 다음)였다 — 폰에서는 탭바에 반쯤 가렸고 취향과 떨어져 있었다.
          취향을 추천에 쓰는 설정이라 「여행 취향」 바로 아래다(사용자 결정, S15P21E201-1644).
      */}
      <View style={styles.consentRow}>
        <View style={styles.consentCopy}>
          <Text weight="bold">{tx('맞춤 추천', 'Personalized picks')}</Text>
          {/* 🔴 「추천을 맞춰요」가 아니다 — S15P21E201-1489(B-16). 이 동의값은 가입·소셜
              로그인 때만 서버로 가고(src/auth/authApi.ts), 일정 추천 요청에는 실리지 않는다.
              실기기에서 스위치를 켜고 같은 조건으로 다시 만들어 보니 결과가 완전히 같았다.
              「지금 켜면 달라진다」로 읽히는 문구는 안 지킬 약속이라, 언제부터 반영되는지를 적는다. */}
          {/* 🔴 「이 기기에 저장돼요」였다 — 사실과 달랐다(S15P21E201-1644). 켜고 끄면 서버의 계정 동의도 바로 바뀐다
              (setBehaviorConsent → PATCH /me/consents, 운영 DB 확인). 「다른 기기에서도 같다」고는 쓰지 않는다 — 다른 기기는
              그 기기에서 켠 적이 없으면 꺼진 채다(reconcileConsent 가 서버를 따라 켜지는 않는다). */}
          <Text variant="caption">{tx('저장·제외·일정 수정·체크인 후기 같은 활동을 다음 여행부터 추천에 반영해요. 지금 보고 있는 일정은 바뀌지 않아요. 이 설정은 내 계정에도 저장돼요.', 'From your next trip on, we use activity like saves, exclusions, itinerary edits, and check-in reviews. The itinerary you are looking at now will not change. This setting is also saved to your account.')}</Text>
        </View>
        {/* 🔴 라벨을 스위치에 직접 준다 — S15P21E201-1489(B-03). 글자는 형제 View 에 있어서
            스위치와 안 묶인다. 실기기 VoiceOver 가 「스위치」라고만 읽었다(개인정보 동의라 더 나쁘다). */}
        <Toggle
          value={behaviorPersonalization}
          onValueChange={setBehaviorPersonalization}
          accessibilityLabel={tx('맞춤 추천', 'Personalized picks')}
        />
      </View>
      {/* 🔴 위치 사용 — 「내 주변」 찾기와 여행 중 도착·출발 자동 기록을 동의 하나로 덮는다(S15P21E201-1691, 사용자 결정).
          처음 묻는 자리는 위치를 처음 쓰는 순간의 동의 창이고, 여기는 언제든 끄고 켜는 자리다. 끄면 위치를 아예 읽지 않는다. */}
      <View style={styles.consentRow}>
        <View style={styles.consentCopy}>
          <Text weight="bold">{tx('위치 사용', 'Use location')}</Text>
          <Text variant="caption">{tx('내 주변 찾기, 방문 인증, 여행 중 도착·출발 자동 기록에 써요. 끄면 위치를 읽지 않아요. 이 설정은 내 계정에도 저장돼요.', 'Used to find things near you, to check visits, and to record arrivals and departures on your trip. When off, we never read your location. This setting is also saved to your account.')}</Text>
        </View>
        <Toggle value={location.consent === true} onValueChange={location.set} accessibilityLabel={tx('위치 사용', 'Use location')} />
      </View>
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
    </View>
  </>;

  const logoutButton = user ? <Button label={tx('로그아웃', 'Sign out')} variant="tertiary" onPress={() => setLogoutAsk(true)} containerStyle={styles.logoutWide} /> : <View style={styles.guestActions}><Button label={tx('로그인', 'Sign in')} onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/me' } })} /><Button label={tx('회원가입', 'Create account')} variant="tertiary" onPress={() => router.push({ pathname: '/sign-up', params: { returnTo: '/me' } })} /></View>;

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

        {/* 🔴 커버와 3열 사이에 내 기록. 전에는 한 줄(HomeRow)이었는데 «무엇을 썼는지»만 보이고
            «언제 어디»는 못 찾았다 — 격자 | 달력 보기와 지역·#태그 칩으로 바꿨다(S15P21E201-1444).
            폰의 「기록」 탭과 같은 부품이다. 관리(삭제·공개 범위)는 여전히 「내 기록」 시트다. */}
        {user ? (
          <View style={styles.wideRecords}>
            <View style={styles.wideRecordsHead}>
              <Text variant="title" weight="bold">{storyCount === null
                ? txf(tx, '%s의 기록', "%s's records", name)
                : txf(tx, '%s의 기록 %s개', "%s's records · %s", name, String(storyCount))}</Text>
              <Pressable accessibilityRole="button" onPress={() => openPanel('posts')} hitSlop={8}><Text weight="bold" color={color.text.muted}>{tx('기록 관리', 'Manage records')} ›</Text></Pressable>
            </View>
            {myStories !== null && myStories.length === 0 ? (
              <View style={styles.recordsEmpty}>
                <GabolleMascot state="thinking" still style={styles.recordsEmptyMascot} />
                <Text variant="title" weight="bold">{tx('아직 남긴 기록이 없어요', 'No records yet')}</Text>
                <Button label={tx('첫 기록 남기기', 'Write your first record')} variant="secondary" onPress={() => router.push('/feed/compose')} containerStyle={styles.recordsEmptyCta} />
              </View>
            ) : (
              <RecordsBrowser stories={myStories ?? []} tx={tx} locale={locale} cardWidth={recordRowCardWidth} onOpen={(story) => router.push(`/feed/${story.id}`)} onCompose={() => router.push('/feed/compose')} />
            )}
            {myStories === null && !myStoriesQuery.isPending ? <RecordsLoadFailed onRetry={() => void myStoriesQuery.refetch()} tx={tx} /> : null}
          </View>
        ) : null}

        {/* 🔴 2열이다 — 전에는 3열(내 계정 | 앱 | 내 여행·로그아웃)이었다(시안 design_handoff_mypage_v2
            변경점 3, S15P21E201-1526). 왼쪽: 내 여행(가로형) → 내 계정 → 로그아웃 / 오른쪽: 앱. */}
        <View style={styles.wideGrid}>
          <View style={styles.wideColumn}>
            {/* 🔴 여기에 「내 여행」 눈썹을 붙이지 않는다. MyTripCard 가 같은 것을 스스로
                그린다 — 붙이면 같은 말이 두 줄로 겹친다. */}
            <MyTripCard layout="row" trip={trip} signedIn={Boolean(user)} loaded={!tripsQuery.isLoading} hasTrips={(tripCount ?? 0) > 0} />
            <Eyebrow>{tx('내 계정', 'Account')}</Eyebrow>
            {accountGroup}
            {logoutButton}
          </View>
          <View style={styles.wideColumn}>
            <Eyebrow>{tx('앱', 'App')}</Eyebrow>
            {appGroup}
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
      guest={!user}
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
    {/* 🔴 누른 탭은 아래 RecordsSettingsTabs 가 쥔다 — 화면 전체가 쥐면 누를 때마다 프로필·기록 카드·설정 목록이
        전부 다시 그려져 전환 애니메이션을 삼켰다(S15P21E201-1603). 두 판은 여기서 한 번 만들어 넘긴다. */}
    <RecordsSettingsTabs
      recordsLabel={storyCount === null ? tx('기록', 'Records') : txf(tx, '기록 %s', 'Records %s', String(storyCount))}
      settingsLabel={tx('설정', 'Settings')}
      panes={{ records: (
      /* 🔴 비었을 때는 격자 대신 시안 4 의 02c — 동백이가 「아직 남긴 기록이 없어요」라고 말한다(S15P21E201-1418).
          예전엔 「새 기록 남기기」 타일 하나만 덩그러니 있어 빈 화면이 고장처럼 보였다. 못 불러온 것(null)은 비어 있는 것과 다르다. */
      user && myStories !== null && myStories.length === 0 ? (
        <View style={styles.recordsEmpty}>
          <GabolleMascot state="thinking" still style={styles.recordsEmptyMascot} />
          <Text variant="title" weight="bold">{tx('아직 남긴 기록이 없어요', 'No records yet')}</Text>
          <Text color={color.text.body} style={styles.recordsEmptyCopy}>{tx('여행 중 찍은 사진 한 장이면 충분해요.\n기록은 피드에도 함께 보여요.', 'One photo from your trip is enough.\nYour records also show up in the feed.')}</Text>
          <Button label={tx('첫 기록 남기기', 'Write your first record')} variant="secondary" onPress={() => router.push('/feed/compose')} containerStyle={styles.recordsEmptyCta} />
        </View>
      ) : (
        <View>
          {user ? <RecordsBrowser stories={myStories ?? []} tx={tx} locale={locale} onOpen={(story) => router.push(`/feed/${story.id}`)} onCompose={() => router.push('/feed/compose')} /> : null}
          {/* 못 불러온 것을 「없다」로 바꾸지 않는다. */}
          {user && myStories === null && !myStoriesQuery.isPending ? (
            <RecordsLoadFailed onRetry={() => void myStoriesQuery.refetch()} tx={tx} />
          ) : null}
        </View>
      )), settings: (
        <>
          <Text variant="eyebrow" weight="bold" style={styles.groupLabel}>{tx('내 계정', 'Account')}</Text>
          {accountGroup}
          <Text variant="eyebrow" weight="bold" style={styles.groupLabel}>{tx('앱', 'App')}</Text>
          {appGroup}
          {user ? <Button label={tx('로그아웃', 'Sign out')} variant="tertiary" onPress={() => setLogoutAsk(true)} containerStyle={styles.logout} /> : <View style={styles.guestActions}><Button label={tx('로그인', 'Sign in')} onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/me' } })} /><Button label={tx('회원가입', 'Create account')} variant="tertiary" onPress={() => router.push({ pathname: '/sign-up', params: { returnTo: '/me' } })} /></View>}
        </>
      ) }}
    />

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

/**
 * 폰의 「기록 | 설정」 — 누른 탭과 움직임을 이 부품이 쥔다(S15P21E201-1603).
 *
 * 🔴 두 판(panes)은 화면이 만들어 넘긴다. 누를 때 이 부품만 다시 그려지고, 넘겨받은 판은 같은 것이라 React 가
 *    건너뛴다. 전에는 화면 전체가 탭을 쥐어서, 누를 때마다 화면 전체가 다시 그려졌다(폰 성능 흉내에서 한 번에 약 0.9초).
 */
function RecordsSettingsTabs({ recordsLabel, settingsLabel, panes }: { recordsLabel: string; settingsLabel: string; panes: Record<'records' | 'settings', ReactNode> }) {
  // 폰의 「기록 | 설정」 — 주소는 안 바뀐다. 내용만 갈아 끼운다.
  //
  // 움직임 셋 (시안 design_handoff_mypage_v2 변경점 2, S15P21E201-1526):
  //   · 빨간 표시가 미끄러진다 — 320ms, cubic-bezier(0.2,0.8,0.2,1)
  //   · 글자색이 바뀐다 — 240ms ease. 두 벌을 겹쳐 두고 서로 흐려지게 한다(Text 가 색 애니메이션을 못 받는다)
  //   · 내용이 들어온다 — 기록은 왼쪽(-24)에서, 설정은 오른쪽(+24)에서. 표시가 가는 쪽과 같다
  // 탭을 눌렀는데 내용이 «즉시» 갈리면 바뀐 줄 모르고 지나간다. 같은 자리에 같은 크기의 흰
  // 화면이 있기 때문이다 — 시작 바 칸 전환과 같은 이유다.
  const [meTab, setMeTab] = useState<'records' | 'settings'>('records');
  /** 빨간 표시의 자리 — 0 기록 · 1 설정 */
  const tabPos = useRef(new Animated.Value(0)).current;
  /** 글자색 — 0 이면 기록이 흰 글자, 1 이면 설정이 흰 글자 */
  const tabInk = useRef(new Animated.Value(0)).current;
  /** 내용이 들어오는 정도 */
  const tabIn = useRef(new Animated.Value(1)).current;
  /** 표시가 달리는 칸의 폭 — 표시가 반 칸이므로 이 값의 반만큼 옮긴다 */
  const [segmentTrack, setSegmentTrack] = useState(0);
  const firstTab = useRef(true);
  useEffect(() => {
    const to = meTab === 'settings' ? 1 : 0;
    // 🔴 첫 로드는 움직이지 않는다(시안). 전에는 들어오자마자 내용이 한 번 떠올랐다.
    if (firstTab.current) { firstTab.current = false; return; }
    const slide = Easing.bezier(0.2, 0.8, 0.2, 1);
    tabIn.setValue(0);
    // 셋 다 transform·opacity 라 네이티브 드라이버로 돈다 — 앱에서는 JS 가 바빠도 안 끊긴다(S15P21E201-1603).
    Animated.parallel([
      Animated.timing(tabPos, { toValue: to, duration: 320, easing: slide, useNativeDriver: true }),
      Animated.timing(tabInk, { toValue: to, duration: 240, easing: Easing.ease, useNativeDriver: true }),
      Animated.timing(tabIn, { toValue: 1, duration: 320, easing: slide, useNativeDriver: true }),
    ]).start();
  }, [meTab, tabPos, tabInk, tabIn]);

  return <>
    <View style={styles.segment}>
      {/* 빨간 표시 — 반 칸짜리 하나가 미끄러진다. 표시가 달리는 칸은 세그먼트 안쪽(여백 4 를 뺀 자리)이다. */}
      <View pointerEvents="none" style={styles.segmentTrack} onLayout={(event) => setSegmentTrack(event.nativeEvent.layout.width)}>
        <Animated.View style={[styles.segmentIndicator, { transform: [{ translateX: tabPos.interpolate({ inputRange: [0, 1], outputRange: [0, segmentTrack / 2] }) }] }]} />
      </View>
      {(['records', 'settings'] as const).map((key, index) => {
        const label = key === 'settings' ? settingsLabel : recordsLabel;
        // 이 탭이 흰 글자인 정도. 기록은 tabInk 0 에서, 설정은 1 에서 흰 글자다.
        const white = tabInk.interpolate({ inputRange: [0, 1], outputRange: index === 0 ? [1, 0] : [0, 1] });
        const dark = tabInk.interpolate({ inputRange: [0, 1], outputRange: index === 0 ? [0, 1] : [1, 0] });
        return (
          <Pressable
            key={key}
            accessibilityRole="tab"
            accessibilityLabel={label}
            accessibilityState={{ selected: meTab === key }}
            onPress={() => setMeTab(key)}
            style={styles.segmentItem}
          >
            {/* 🔴 같은 글자를 두 벌 겹친다 — 낭독기가 두 번 읽지 않게 둘 다 숨기고 이름은 위 accessibilityLabel 이 준다. */}
            <Animated.View aria-hidden style={[styles.segmentInk, { opacity: dark }]}><Text weight="bold" color={color.text.body} numberOfLines={1}>{label}</Text></Animated.View>
            <Animated.View aria-hidden style={[styles.segmentInk, { opacity: white }]}><Text weight="bold" color={color.text.onAction} numberOfLines={1}>{label}</Text></Animated.View>
          </Pressable>
        );
      })}
    </View>

    <Animated.View style={{ opacity: tabIn, transform: [{ translateX: tabIn.interpolate({ inputRange: [0, 1], outputRange: [meTab === 'settings' ? 24 : -24, 0] }) }] }}>
      {/* 🔴 두 판은 한 번 만든 뒤 붙여 둔다 — 누를 때마다 새로 만들지 않는다. */}
      <KeptPanes active={meTab} panes={panes} />
    </Animated.View>
  </>;
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
  // 시안: padding 40 40 64 · gap 24 · 위쪽 맞춤. 위 40 은 좌우와 같은 desktopGutter 다.
  wideGrid: {
    flexDirection: 'row', alignItems: 'flex-start', gap: spacing[6],
    paddingTop: desktopGutter, paddingHorizontal: desktopGutter, paddingBottom: 64,
  },
  // 두 칸이 같은 폭을 나눠 가진다. 칸마다 내용 길이가 달라 위쪽을 맞춘다.
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
  // 넓은 화면은 내 계정 목록 바로 아래라 16 (시안 1b). 폰은 위 24 그대로.
  logoutWide: { marginTop: spacing[4] },
  guestActions: { gap: spacing[2], marginTop: spacing[6], marginBottom: spacing[4] },

  modalBackdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  modalCard: { width: '100%', maxWidth: 400, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  modalActions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] },
  modalAction: { flex: 1 },

  // ── 폰 「기록 | 설정」 ────────────────────────────────────────────────────
  segment: { flexDirection: 'row', padding: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft, marginTop: spacing[6] },
  segmentItem: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full },
  // 표시가 달리는 칸 = 세그먼트 안쪽. 여백(4)만큼 들여 놓아야 반 칸이 정확히 탭 하나가 된다.
  segmentTrack: { position: 'absolute', top: spacing[1], bottom: spacing[1], left: spacing[1], right: spacing[1] },
  // 🔴 이 화면의 「활성 탭」 동백 채움 — check-palette 의 me.tsx 예외가 말하는 바로 그것이다(사용자 지시).
  //    전에는 누른 탭 자체를 칠했고, 지금은 이 표시가 미끄러지며 칠한다. 채움이 하나 는 것이 아니다.
  //    그림자는 시안 값(0 2px 8px, 동백 28%) — 표시가 바닥에서 살짝 떠 보여 «움직이는 것»으로 읽힌다.
  segmentIndicator: {
    width: '50%', height: '100%', borderRadius: radius.full, backgroundColor: color.action.primary,
    shadowColor: color.action.primary, shadowOpacity: 0.28, shadowRadius: 8, shadowOffset: { width: 0, height: 2 }, elevation: 3,
  },
  segmentInk: { position: 'absolute', top: 0, bottom: 0, left: 0, right: 0, alignItems: 'center', justifyContent: 'center' },

  // 🔴 스크롤 칸 안이라 flex 를 안 쓴다. 쓰면 카드가 세로로 눌린다.
  // 커버와 「○○의 기록」 사이 24 — 시안 1b 의 margin-top:24px. 전에는 0 이라 제목이 커버 아랫단에 붙었다.
  wideRecords: { gap: spacing[2], paddingHorizontal: desktopGutter, marginTop: spacing[6] },
  wideRecordsHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3] },
  recordsEmpty: { alignItems: 'center', gap: spacing[3], marginTop: spacing[3], paddingVertical: spacing[8], paddingHorizontal: spacing[4] },
  recordsEmptyMascot: { width: 104, height: 104 },
  recordsEmptyCopy: { textAlign: 'center' },
  // 🔴 minWidth 로는 껍데기 폭이 «자동»으로 남는다. 그러면 안쪽 단추의 width:'100%' 가
  //    풀리지 않아 글자 폭으로 줄고 왼쪽에 붙는다 — 실기에서 껍데기는 251..829(가운데 540)인데
  //    단추는 251..508(가운데 379)이었다(S15P21E201-1456). width 를 확정해 주면 풀린다.
  recordsEmptyCta: { marginTop: spacing[1], alignSelf: 'center', width: '100%', maxWidth: 320 },
});
