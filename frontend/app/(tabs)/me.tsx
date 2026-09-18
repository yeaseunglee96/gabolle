// 마이페이지 진입 화면.
import { useState } from 'react';
import { BackHandler, Image, Modal, Platform, Pressable, ScrollView, StyleSheet, View, useWindowDimensions } from 'react-native';
import { useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { Toggle } from '@/components/Toggle';
import { color, desktopGutter, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { MyPageCover } from '@/me/MyPageCover';
import { MyPageModal } from '@/me/MyPageModal';
import { MyPageSheetBody } from '@/me/MyPageSheet';
import { hasPanel, myPanelBody, panelTitle, type MyPanelKey } from '@/me/myPanels';
import { MyTripCard } from '@/home/HomeBlocks';
import { ProfileCard } from '@/me/ProfileCard';
import { InfoRow } from '@/me/InfoRow';
import { AppLanguageSetting } from '@/me/AppLanguageSetting';
import { useMyPageCounts } from '@/me/MyPageShell';
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

  /**
   * 메뉴를 눌렀을 때 — 옮긴 것은 겹쳐 열고, 아직인 것은 지금처럼 화면을 바꾼다.
   *
   * 🔴 한 번에 열한 개를 다 옮기면 무엇이 깨졌는지 못 찾는다. 옮긴 것부터 하나씩 켠다.
   */
  const openPanel = (key: MyPanelKey, fallbackPath: string) => {
    if (hasPanel(key)) { setPanel(key); return; }
    router.push(fallbackPath as never);
  };

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

  // 두 배치가 같은 카드를 쓴다. 두 벌로 만들면 한쪽만 고쳐지고, 그 차이는 두 폭을
  // 나란히 열어 봐야만 보인다.
  const accountGroup = <>
    <View style={styles.group}>
      <InfoRow
        first
        label={tx('내 기록', 'My records')}
        value={storyCount === null ? '›' : storyCount > 0 ? tx(`${storyCount}개 ›`, `${storyCount} ›`) : none}
        onPress={() => openPanel('posts', '/me/posts')}
        disabled={!user}
      />
      {/* 사용자 리포트: "마이페이지에 저장 누르면 저장했던 피드들 뜨게" —. */}
      <InfoRow
        label={tx('저장한 기록', 'Saved records')}
        value="›"
        onPress={() => openPanel('saved', '/me/saved')}
        disabled={!user}
      />
      {/* — 인스타그램처럼 팔로워·팔로잉을 눌러 목록으로 들어갈 수 있어야
          한다는 사용자 리포트. 숫자만 있던 자리를 실제 목록 화면으로 잇는다.
      */}
      <InfoRow
        label={tx('팔로워', 'Followers')}
        value={followerCount === null ? '›' : tx(`${followerCount}명 ›`, `${followerCount} ›`)}
        onPress={() => user && openPanel('followers', `/user/${user.userId}/followers`)}
        disabled={!user}
      />
      <InfoRow
        label={tx('팔로잉', 'Following')}
        value={followingCount === null ? '›' : tx(`${followingCount}명 ›`, `${followingCount} ›`)}
        onPress={() => user && openPanel('following', `/user/${user.userId}/following`)}
        disabled={!user}
      />
      <InfoRow
        label={tx('여행 취향', 'Travel preferences')}
        value={answeredPreferences === null
          ? '›'
          : answeredPreferences > 0
            ? tx(`${answeredPreferences} / ${PREFERENCE_TOTAL} 답함 ›`, `${answeredPreferences} / ${PREFERENCE_TOTAL} answered ›`)
            : none}
        onPress={() => openPanel('preferences', '/me/preferences')}
        disabled={!user}
      />
      <InfoRow label={tx('연결된 소셜 계정', 'Connected accounts')} value="›" onPress={() => openPanel('identities', '/me/identities')} disabled={!user} />
    </View>
  </>;

  const appGroup = <>
    <View style={styles.group}>
      <AppLanguageSetting />
      {/* — 알림·차단된 계정처럼 스토어 심사가 보는 기본 기능이 마이페이지
          안에서 안 보였다는 사용자 리포트. 알림 화면은 이미 있다(app/notifications.tsx
          홈 종 아이콘) — 여기서는 같은 화면으로 가는 입구만 하나 더 둔다.
      */}
      <InfoRow label={tx('알림', 'Notifications')} value="›" onPress={() => openPanel('notifications', '/notifications')} />
      <InfoRow label={tx('차단된 계정', 'Blocked accounts')} value="›" onPress={() => openPanel('blocked', '/me/blocked')} disabled={!user} />
      <InfoRow label={tx('도움말·문의', 'Help & support')} description={tx('앱 소개, 자주 묻는 질문, 문제 해결', 'App tour, FAQs, and troubleshooting')} value="›" onPress={() => openPanel('help', '/help')} />
      <InfoRow label={tx('약관·고지', 'Terms & notices')} value="›" onPress={() => openPanel('terms', '/me/terms')} />
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

  const logoutButton = user ? <Button label={tx('로그아웃', 'Sign out')} variant="ghost" onPress={() => setLogoutAsk(true)} containerStyle={styles.logout} /> : <View style={styles.guestActions}><Button label={tx('로그인', 'Sign in')} onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/me' } })} /><Button label={tx('회원가입', 'Create account')} variant="ghost" onPress={() => router.push({ pathname: '/sign-up', params: { returnTo: '/me' } })} /></View>;

  const logoutModal = <Modal visible={logoutAsk} transparent animationType="fade" onRequestClose={() => setLogoutAsk(false)}>
      <View style={styles.modalBackdrop}><View accessibilityViewIsModal style={styles.modalCard}>
        <Text variant="title" weight="bold">{tx('로그아웃할까요?', 'Sign out?')}</Text>
        <Text>{tx('여행과 기록은 계정에 그대로 남아요.', 'Your trips and records stay on your account.')}</Text>
        <View style={styles.modalActions}>
          <Button label={tx('취소', 'Cancel')} variant="ghost" onPress={() => setLogoutAsk(false)} containerStyle={styles.modalAction} />
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
          avatarUri={avatarUri}
          coverUri={user?.coverUrl ?? null}
          counts={[
            { label: tx('기록', 'Records'), value: storyCount, onPress: () => user && openPanel('posts', '/me/posts') },
            { label: tx('팔로워', 'Followers'), value: followerCount, onPress: () => user && openPanel('followers', `/user/${user.userId}/followers`) },
            { label: tx('팔로잉', 'Following'), value: followingCount, onPress: () => user && openPanel('following', `/user/${user.userId}/following`) },
          ]}
          onEdit={() => (user ? openPanel('profile', '/me/profile') : router.push({ pathname: '/sign-in', params: { returnTo: '/me/profile' } }))}
          tx={tx}
        />

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
            <Eyebrow>{tx('내 여행', 'My trip')}</Eyebrow>
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
        { label: tx('기록', 'Records'), value: storyCount, onPress: () => user && router.push('/me/posts') },
        { label: tx('팔로워', 'Followers'), value: followerCount, onPress: () => user && router.push(`/user/${user.userId}/followers`) },
        { label: tx('팔로잉', 'Following'), value: followingCount, onPress: () => user && router.push(`/user/${user.userId}/following`) },
      ]}
      onEdit={() => (user ? router.push('/me/profile') : router.push({ pathname: '/sign-in', params: { returnTo: '/me/profile' } }))}
      tx={tx}
    />

    <Text variant="eyebrow" weight="bold" style={styles.groupLabel}>{tx('내 계정', 'Account')}</Text>
    {accountGroup}
    <Text variant="eyebrow" weight="bold" style={styles.groupLabel}>{tx('앱', 'App')}</Text>
    {appGroup}
    {user ? <Button label={tx('로그아웃', 'Sign out')} variant="ghost" onPress={() => setLogoutAsk(true)} containerStyle={styles.logout} /> : <View style={styles.guestActions}><Button label={tx('로그인', 'Sign in')} onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/me' } })} /><Button label={tx('회원가입', 'Create account')} variant="ghost" onPress={() => router.push({ pathname: '/sign-up', params: { returnTo: '/me' } })} /></View>}

    <Modal visible={logoutAsk} transparent animationType="fade" onRequestClose={() => setLogoutAsk(false)}>
      <View style={styles.modalBackdrop}><View accessibilityViewIsModal style={styles.modalCard}>
        <Text variant="title" weight="bold">{tx('로그아웃할까요?', 'Sign out?')}</Text>
        <Text>{tx('여행과 기록은 계정에 그대로 남아요.', 'Your trips and records stay on your account.')}</Text>
        <View style={styles.modalActions}>
          <Button label={tx('취소', 'Cancel')} variant="ghost" onPress={() => setLogoutAsk(false)} containerStyle={styles.modalAction} />
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
  shell: { flex: 1, backgroundColor: color.brand.ivory },

  // ── 넓은 화면 (시안 01) ───────────────────────────────────────────────────
  //
  // 🔴 Screen 을 안 쓰므로 좌우 여백도 여기서 직접 준다. 커버는 여백 없이 전폭이고,
  //    아래 3열만 좌우 40(desktopGutter)을 받는다.
  // 시트 뒤를 가리는 어둠막. 탭바(시트)보다 낮고 내용보다 높다.
  sheetBackdrop: {
    position: Platform.OS === 'web' ? ('fixed' as 'absolute') : 'absolute',
    top: 0, left: 0, right: 0, bottom: 0,
    backgroundColor: 'rgba(11,29,58,0.62)',
    zIndex: 20,
  },

  wideScroll: { flex: 1, backgroundColor: color.brand.ivory },
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

  modalBackdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(11,29,58,0.62)' },
  modalCard: { width: '100%', maxWidth: 400, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  modalActions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] },
  modalAction: { flex: 1 },
});
