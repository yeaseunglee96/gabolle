// 마이페이지 진입 화면 (S15P21E201-965).
//
// 전에는 이 한 화면에 프로필 편집 · 취향 줄 · 내 기록 줄 · 소셜 계정 · 약관 · 로그아웃 · 탈퇴가
// 전부 쌓여 있었다. 지금은 **길 안내만** 하고 실제 내용은 `/me/<탭>` 다섯이 나눠 맡는다.
//
// 🔴 데스크톱(≥ lg)에서는 이 화면을 거치지 않고 바로 프로필 탭으로 보낸다. 넓은 화면의
// 마이페이지는 왼쪽 메뉴가 있는 셸 한 벌이라(MyPageShell), 그 앞에 목록 화면을 한 번 더 두면
// 메뉴를 두 번 고르게 된다.
import { useState } from 'react';
import { Image, Modal, Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
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
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { ProfileCard } from '@/me/ProfileCard';
import { InfoRow } from '@/me/InfoRow';
import { AppLanguageSetting } from '@/me/AppLanguageSetting';
import { useMyPageCounts } from '@/me/MyPageShell';
import { useBehaviorConsent } from '@/personalization/behaviorConsent';
import { usePlan } from '@/plan/PlanProvider';
import { PREFERENCE_TOTAL } from '@/preferences/accountPreferences';

export default function Me() {
  const router = useRouter();
  const { user, signOut, accessToken } = useAuth();
  const { tx } = useI18n();
  const plan = usePlan();
  const { width } = useWindowDimensions();
  const { answeredPreferences, storyCount, followerCount, followingCount } = useMyPageCounts();
  const { enabled: behaviorPersonalization, setEnabled: setBehaviorPersonalization } = useBehaviorConsent(accessToken);
  const [avatarUri, setAvatarUri] = useState<string | null>(null);
  const [logoutAsk, setLogoutAsk] = useState(false);

  useEffect(() => {
    if (!user?.userId) { setAvatarUri(null); return; }
    void AsyncStorage.getItem(`gabolle:profile-avatar:${user.userId}`).then(setAvatarUri);
  }, [user?.userId]);

  // 🔴 2026-09-18 (S15P21E201-1237) — **넓은 화면을 다른 화면으로 보내던 것을 없앴다.**
  //    마이페이지가 폰(메뉴 목록)과 데스크톱(/me/profile 편집 폼)으로 갈라져 있어서,
  //    같은 일을 두 곳에서 고쳐야 했고 실제로 한쪽만 고쳐진 날이 있었다.
  //    이제 한 화면이 넓어질 뿐이다. `/me/profile` 은 딥링크로 남는다.
  const wide = isAtLeast(width, 'lg');

  const name = user?.displayName || tx('여행자', 'Traveler');
  const none = tx('아직 없음 ›', 'None yet ›');

  return <View style={styles.shell}><Screen scroll withTabBar>
    <View style={styles.heading}><Eyebrow>{tx('내 계정', 'Account')}</Eyebrow><Text variant="display" weight="bold">{tx('마이페이지', 'My page')}</Text></View>

    {/* 🔴 시안의 프로필 카드 (S15P21E201-1237). 전에는 한 줄짜리 띠였고, 넓은 화면에서는
        이 화면이 아예 안 보였다. 없는 값(한 줄 소개·거주지)은 줄 자체를 안 그린다 —
        서버에 그 칸이 아직 없다. */}
    <ProfileCard
      name={name}
      email={user?.email ?? null}
      avatarUri={avatarUri}
      coverUri={null}
      bio={null}
      homeCity={null}
      wide={wide}
      counts={[
        { label: tx('기록', 'Records'), value: storyCount, onPress: () => user && router.push('/me/posts') },
        { label: tx('팔로워', 'Followers'), value: followerCount, onPress: () => user && router.push(`/user/${user.userId}/followers`) },
        { label: tx('팔로잉', 'Following'), value: followingCount, onPress: () => user && router.push(`/user/${user.userId}/following`) },
      ]}
      onEdit={() => (user ? router.push('/me/profile') : router.push({ pathname: '/sign-in', params: { returnTo: '/me/profile' } }))}
      tx={tx}
    />

    <Text variant="eyebrow" weight="bold" style={styles.groupLabel}>{tx('내 계정', 'Account')}</Text>
    <View style={styles.group}>
      <InfoRow
        first
        label={tx('내 기록', 'My records')}
        value={storyCount === null ? '›' : storyCount > 0 ? tx(`${storyCount}개 ›`, `${storyCount} ›`) : none}
        onPress={() => router.push('/me/posts')}
        disabled={!user}
      />
      {/* 사용자 리포트: "마이페이지에 저장 누르면 저장했던 피드들 뜨게" — S15P21E201-1221. */}
      <InfoRow
        label={tx('저장한 기록', 'Saved records')}
        value="›"
        onPress={() => router.push('/me/saved')}
        disabled={!user}
      />
      {/* 🔴 S15P21E201-1180 — 인스타그램처럼 팔로워·팔로잉을 눌러 목록으로 들어갈 수 있어야
          한다는 사용자 리포트. 숫자만 있던 자리를 실제 목록 화면으로 잇는다. */}
      <InfoRow
        label={tx('팔로워', 'Followers')}
        value={followerCount === null ? '›' : tx(`${followerCount}명 ›`, `${followerCount} ›`)}
        onPress={() => user && router.push(`/user/${user.userId}/followers`)}
        disabled={!user}
      />
      <InfoRow
        label={tx('팔로잉', 'Following')}
        value={followingCount === null ? '›' : tx(`${followingCount}명 ›`, `${followingCount} ›`)}
        onPress={() => user && router.push(`/user/${user.userId}/following`)}
        disabled={!user}
      />
      <InfoRow
        label={tx('여행 취향', 'Travel preferences')}
        value={answeredPreferences === null
          ? '›'
          : answeredPreferences > 0
            ? tx(`${answeredPreferences} / ${PREFERENCE_TOTAL} 답함 ›`, `${answeredPreferences} / ${PREFERENCE_TOTAL} answered ›`)
            : none}
        onPress={() => router.push('/me/preferences')}
        disabled={!user}
      />
      <InfoRow label={tx('연결된 소셜 계정', 'Connected accounts')} value="›" onPress={() => router.push('/me/identities')} disabled={!user} />
    </View>

    <Text variant="eyebrow" weight="bold" style={styles.groupLabel}>{tx('앱', 'App')}</Text>
    <View style={styles.group}>
      <AppLanguageSetting />
      {/* 🔴 S15P21E201-1181 — 알림·차단된 계정처럼 스토어 심사가 보는 기본 기능이 마이페이지
          안에서 안 보였다는 사용자 리포트. 알림 화면은 이미 있다(app/notifications.tsx,
          홈 종 아이콘) — 여기서는 같은 화면으로 가는 입구만 하나 더 둔다. */}
      <InfoRow label={tx('알림', 'Notifications')} value="›" onPress={() => router.push('/notifications')} />
      <InfoRow label={tx('차단된 계정', 'Blocked accounts')} value="›" onPress={() => router.push('/me/blocked')} disabled={!user} />
      <InfoRow label={tx('도움말·문의', 'Help & support')} description={tx('앱 소개, 자주 묻는 질문, 문제 해결', 'App tour, FAQs, and troubleshooting')} value="›" onPress={() => router.push('/help')} />
      <InfoRow label={tx('약관·고지', 'Terms & notices')} value="›" onPress={() => router.push('/me/terms')} />
      {/* 처음 켜는 자리는 첫 체크인 화면이고, 여기는 **언제든 끄는 자리**다. 끄는 길이 설정
          안쪽 어딘가에만 있으면 사용자는 못 찾고, 못 찾으면 켠 적 없는 사람처럼 취급된다. */}
      <View style={styles.consentRow}>
        <View style={styles.consentCopy}>
          <Text weight="bold">{tx('맞춤 추천', 'Personalized picks')}</Text>
          <Text variant="caption">{tx('저장·제외·일정 수정·체크인 후기 같은 활동을 바탕으로 추천을 맞춰요. 이 설정은 이 기기에 저장돼요.', 'We tune your picks using activity like saves, exclusions, itinerary edits, and check-in reviews. This setting is stored on this device.')}</Text>
        </View>
        <Toggle value={behaviorPersonalization} onValueChange={setBehaviorPersonalization} />
      </View>
    </View>

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
  </Screen><TabBar active="me" /></View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.brand.ivory },
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

  logout: { marginTop: spacing[6], marginBottom: spacing[4], borderColor: color.brand.orange },
  guestActions: { gap: spacing[2], marginTop: spacing[6], marginBottom: spacing[4] },

  modalBackdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(11,29,58,0.62)' },
  modalCard: { width: '100%', maxWidth: 400, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  modalActions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] },
  modalAction: { flex: 1 },
});
