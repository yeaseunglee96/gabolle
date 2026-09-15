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
import { Redirect, useLocalSearchParams, useRouter } from 'expo-router';

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
import { InfoRow } from '@/me/InfoRow';
import { useMyPageCounts } from '@/me/MyPageShell';
import { useBehaviorConsent } from '@/personalization/behaviorConsent';
import { usePlan } from '@/plan/PlanProvider';
import { PREFERENCE_TOTAL } from '@/preferences/accountPreferences';

export default function Me() {
  const router = useRouter();
  const { preview } = useLocalSearchParams<{ preview?: string }>();
  const { user, signOut } = useAuth();
  const { language, tx } = useI18n();
  const plan = usePlan();
  const { width } = useWindowDimensions();
  const { answeredPreferences, storyCount } = useMyPageCounts();
  const { enabled: behaviorPersonalization, setEnabled: setBehaviorPersonalization } = useBehaviorConsent();
  const [avatarUri, setAvatarUri] = useState<string | null>(null);
  const [logoutAsk, setLogoutAsk] = useState(false);

  useEffect(() => {
    if (!user?.userId) { setAvatarUri(null); return; }
    void AsyncStorage.getItem(`gabolle:profile-avatar:${user.userId}`).then(setAvatarUri);
  }, [user?.userId]);

  // 🔴 `?preview=ui` 를 그대로 들고 넘어간다. 안 넘기면 로그인 없이 화면만 보는 통로
  // (me/_layout.tsx 의 ProtectedRoute)가 넓은 화면에서만 끊겨 로그인 화면으로 튕긴다.
  if (isAtLeast(width, 'lg')) return <Redirect href={preview ? `/me/profile?preview=${preview}` : '/me/profile'} />;

  const name = user?.displayName || tx('여행자', 'Traveler');
  const none = tx('아직 없음 ›', 'None yet ›');

  return <View style={styles.shell}><Screen scroll withTabBar>
    <View style={styles.heading}><Eyebrow>{tx('내 계정', 'Account')}</Eyebrow><Text variant="display" weight="bold">{tx('마이페이지', 'My page')}</Text></View>

    <Pressable accessibilityRole="button" disabled={!user} onPress={() => router.push('/me/profile')} style={({ pressed }) => [styles.profile, pressed && styles.pressed]}>
      <View style={styles.avatar}>
        {avatarUri
          ? <Image source={{ uri: avatarUri }} resizeMode="cover" accessibilityLabel={tx('현재 프로필 사진', 'Current profile photo')} style={styles.avatarPhoto} />
          : <Text variant="title" weight="bold" color={color.text.onAction}>{name.slice(0, 1)}</Text>}
      </View>
      <View style={styles.profileCopy}>
        <Text variant="title" weight="bold">{name}</Text>
        <Text variant="caption" numberOfLines={1}>{user?.email || tx('계정 정보를 불러오지 못했어요', 'Account information is unavailable')}</Text>
      </View>
      <Text variant="caption" weight="bold" color={color.brand.orange}>{tx('프로필 ›', 'Profile ›')}</Text>
    </Pressable>

    <Text variant="eyebrow" weight="bold" style={styles.groupLabel}>{tx('내 계정', 'Account')}</Text>
    <View style={styles.group}>
      <InfoRow
        first
        label={tx('내 기록', 'My records')}
        value={storyCount === null ? '›' : storyCount > 0 ? tx(`${storyCount}개 ›`, `${storyCount} ›`) : none}
        onPress={() => router.push('/me/posts')}
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
      <InfoRow first label={tx('언어', 'Language')} value={`${language === 'ko' ? '한국어' : 'English'} ›`} onPress={() => router.push('/me/profile')} disabled={!user} />
      <InfoRow label={tx('약관·고지', 'Terms & notices')} value="›" onPress={() => router.push('/me/terms')} />
      {/* 처음 켜는 자리는 첫 체크인 화면이고, 여기는 **언제든 끄는 자리**다. 끄는 길이 설정
          안쪽 어딘가에만 있으면 사용자는 못 찾고, 못 찾으면 켠 적 없는 사람처럼 취급된다. */}
      <View style={styles.consentRow}>
        <View style={styles.consentCopy}>
          <Text weight="bold">{tx('행동으로 추천 다듬기', 'Tune recommendations from my activity')}</Text>
          <Text variant="caption">{tx('저장·제외·일정 수정·체크인 후기를 보고 추천 순서를 바꿔요. 이 선택은 이 기기에 저장돼요.', 'We reorder recommendations using your saves, exclusions, itinerary edits, and check-in reviews. This choice is stored on this device.')}</Text>
        </View>
        <Toggle value={behaviorPersonalization} onValueChange={setBehaviorPersonalization} />
      </View>
    </View>

    <Button label={tx('로그아웃', 'Sign out')} variant="ghost" onPress={() => setLogoutAsk(true)} containerStyle={styles.logout} />

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

  modalBackdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(11,29,58,0.62)' },
  modalCard: { width: '100%', maxWidth: 400, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  modalActions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] },
  modalAction: { flex: 1 },
});
