// 03 온보딩·권한 안내 — Figma 03_온보딩·권한 안내 실측 그대로.
//
// 🔴 권한을 요청하기 전에 왜 필요한지 먼저 설명하는 화면이다(스토어 심사 항목).
// 사용자가 고른 권한만 CTA 시점에 OS에 요청한다. 거부된 항목은 false로 저장하되,
// 권한 거부 때문에 로그인이나 일정 생성 진입을 막지는 않는다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useState } from 'react';
import { Platform, Pressable, StyleSheet, View } from 'react-native';
import * as Notifications from 'expo-notifications';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { Button } from '@/components/Button';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';

const PERMISSION_PREFERENCES_KEY = '@gabolle/permission-preferences';

export default function Permissions() {
  const router = useRouter();
  const { tx } = useI18n();
  const { kind } = useLayout();
  const [notification, setNotification] = useState(false);
  const [requesting, setRequesting] = useState(false);
  async function continueTo(path: string, requestNotification = notification) {
    if (requesting) return;
    setRequesting(true);
    let notificationGranted = false;
    if (Platform.OS !== 'web' && requestNotification) {
      try { notificationGranted = (await Notifications.requestPermissionsAsync()).status === 'granted'; } catch { notificationGranted = false; }
    }
    await AsyncStorage.setItem(PERMISSION_PREFERENCES_KEY, JSON.stringify({ notification: notificationGranted }));
    router.replace({ pathname: '/sign-in', params: { returnTo: path } });
  }

  return (
    <Screen wide style={styles.screen}>
      <View style={[styles.layout, kind === 'tablet' && styles.layoutWide]}>
      <View style={[styles.introColumn, kind === 'tablet' && styles.introWide]}>
      {kind === 'tablet' && <BrandLogoLink href="/" imageStyle={styles.logo} />}
      <Eyebrow>{tx('앱 권한 안내', 'App permissions')}</Eyebrow>
      <Text variant="display" weight="bold" style={styles.title}>
        {tx('부산 여행에 꼭 필요한\n기능을 준비할게요', "We'll set up what your\nBusan trip really needs")}
      </Text>
      <Text variant="caption" style={styles.subtitle}>
        {tx('허용하지 않아도 둘러볼 수 있고, 설정에서 언제든 바꿀 수 있어요.', 'You can still browse without allowing these, and change them anytime in settings.')}
      </Text>
      {kind === 'tablet' && <View style={styles.webNote}><Text variant="body" weight="bold">{tx('알림은 지금 선택할 수 있어요', 'You can choose notifications now')}</Text><Text variant="caption" color={color.text.body}>{tx('위치와 카메라는 실제 기능을 처음 사용할 때 이유를 먼저 안내한 뒤 요청합니다.', "We'll explain why before asking for location and camera, the first time you actually use those features.")}</Text></View>}
      </View>

      <View style={styles.actionColumn}><View style={[styles.cards, kind === 'tablet' && styles.cardsWide]}>
          <View style={styles.card}>
            <View style={styles.cardIcon}>
              <Text variant="title">🔔</Text>
            </View>
            <View style={styles.cardBody}>
              <View style={styles.cardTopRow}>
                <Text variant="body" weight="bold">{tx('여행 알림 받기', 'Get trip notifications')}</Text>
                <Pressable accessibilityRole="switch" accessibilityState={{ checked: notification }} onPress={() => setNotification((current) => !current)} style={[styles.choice, notification && styles.choiceActive]}><Text variant="caption" weight="bold" color={notification ? color.text.onAction : color.text.body}>{notification ? tx('받을게요', "I'll allow it") : tx('나중에', 'Later')}</Text></Pressable>
              </View>
              <Text variant="caption" color={color.text.body}>
                {tx('일정 변경과 출발 시간을 놓치지 않도록 알려드려요.', "We'll let you know about schedule changes and departure times.")}
              </Text>
              <View style={styles.cardBottomRow}>
                <Text variant="caption" color={color.text.muted} style={styles.cardNote}>
                  {tx('광고 알림 없이 중요한 여행 안내만 보내요.', "No ads — just the trip updates that matter.")}
                </Text>
              </View>
            </View>
          </View>
      </View>

      <Pressable accessibilityRole="link" accessibilityLabel={tx('개인정보 처리 안내 보기', 'View privacy information')} onPress={() => router.push('/legal/privacy')} style={({ pressed }) => [styles.privacyBox, pressed && styles.privacyPressed]}>
        <Text variant="title">🔒</Text>
        <View style={styles.privacyCopy}>
          <Text variant="caption" weight="bold" color={color.text.heading}>
            {tx('개인정보는 추천 기능에만 사용하며 제3자에게 제공하지 않아요.', "We use your personal data only for recommendations and never share it with third parties.")}
          </Text>
          <Text variant="caption" weight="medium" color={color.text.accent}>
            {tx('자세한 개인정보 처리방침 보기 ›', 'View the full Privacy Policy ›')}
          </Text>
        </View>
      </Pressable>

      <Pressable accessibilityRole="button" accessibilityState={{ disabled: requesting }} disabled={requesting} onPress={() => void continueTo('/home', false)}>
        <Text variant="caption" weight="bold" color={color.text.muted} style={styles.laterLink}>
          {tx('나중에 설정', 'Set up later')}
        </Text>
      </Pressable>

      <Button label={requesting ? tx('권한 확인 중…', 'Checking permissions…') : tx('선택하고 로그인·회원가입으로', 'Continue to sign in / sign up')} disabled={requesting} containerStyle={styles.cta} onPress={() => void continueTo('/home')} />
      </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  layout: { flex: 1 },
  layoutWide: { flexDirection: 'row', alignItems: 'center', gap: spacing[8] },
  introColumn: {},
  introWide: { flex: 1, alignSelf: 'stretch', justifyContent: 'center', padding: spacing[8], borderRadius: radius.lg, backgroundColor: '#fff1e8' },
  actionColumn: { flex: 1, width: '100%', justifyContent: 'center' },
  logo: { width: 120, height: 44, marginBottom: spacing[8] },
  webNote: { marginTop: spacing[8], gap: spacing[2] },
  title: {
    marginTop: spacing[1],
  },
  subtitle: {
    marginTop: spacing[3],
    color: color.text.body,
  },
  cards: {
    flex: 1,
    justifyContent: 'center',
    gap: spacing[2],
  },
  cardsWide: { flex: 0 },
  card: {
    flexDirection: 'row',
    gap: spacing[3],
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    padding: spacing[3],
    borderWidth: 1,
    borderColor: '#eee5da',
  },
  cardIcon: {
    width: 48,
    height: 48,
    borderRadius: radius.md,
    backgroundColor: '#fff1e8',
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardBody: {
    flex: 1,
    gap: spacing[1],
  },
  cardTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  choice: { minHeight: 36, justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  choiceActive: { borderColor: color.brand.orange, backgroundColor: color.brand.orange },
  cardBottomRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: spacing[1],
  },
  cardNote: {
    flex: 1,
  },
  badge: {
    borderRadius: radius.full,
    paddingHorizontal: spacing[2],
    paddingVertical: 2,
  },
  recommendedBadge: { backgroundColor: '#fff1e8' },
  privacyBox: {
    flexDirection: 'row',
    gap: spacing[3],
    backgroundColor: '#fff1e8',
    borderRadius: radius.md,
    padding: spacing[4],
    marginTop: spacing[3],
  },
  privacyCopy: {
    flex: 1,
    gap: spacing[1],
  },
  privacyPressed: { opacity: 0.72, transform: [{ scale: 0.99 }] },
  laterLink: {
    marginTop: spacing[3],
  },
  cta: {
    marginTop: spacing[3],
    minHeight: 54,
    borderRadius: radius.full,
    backgroundColor: color.brand.orange,
  },
});
