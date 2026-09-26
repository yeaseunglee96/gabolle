// 알림 본문 — 화면(`app/notifications.tsx`)과 마이페이지 패널이 같은 것을 쓴다.
//
// 뒤로 가는 머리는 화면만 그린다. 패널은 껍데기가 이미 닫는 자리를 준다.
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, AppState, Image, Linking, Platform, Pressable, StyleSheet, View } from 'react-native';
import * as ExpoNotifications from 'expo-notifications';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { formatDayHeading } from '@/i18n/datetime';
import { hasUnseen, loadActivityFeed, loadSeenAt, markSeenNow, noticeCopy, type ActivityNotice } from '@/notifications/activityFeed';
import { relativeStoryTime } from '@/social/stories';

const bellIcon = require('../../../assets/icons/home/bell.png');

export function NotificationsBody() {
  const { tx, language, locale } = useI18n();
  const router = useRouter();
  const { accessToken, user } = useAuth();
  // 🔴 알림은 여행 활동에서 나온다(S15P21E201-1380) — 서버 알림 API 가 없어 늘 비어 있던 화면이었다.
  const [feed, setFeed] = useState<{ state: 'loading' } | { state: 'ready'; items: ActivityNotice[]; seenAt: string | null } | { state: 'error'; message: string }>({ state: 'loading' });
  useEffect(() => {
    let active = true;
    if (!user) { setFeed({ state: 'ready', items: [], seenAt: null }); return undefined; }
    (async () => {
      const [result, seenAt] = await Promise.all([loadActivityFeed(accessToken, tx, locale), loadSeenAt()]);
      if (!active) return;
      setFeed(result.state === 'success' ? { state: 'ready', items: result.items, seenAt } : { state: 'error', message: result.message });
      // 목록을 본 순간부터는 읽은 것이다 — 점은 다음에 새것이 올 때만 다시 뜬다.
      if (result.state === 'success' && result.items.length) void markSeenNow();
    })();
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken, user?.userId]);
  const [permission, setPermission] = useState<'loading' | 'granted' | 'denied' | 'undetermined' | 'unsupported'>('loading');
  const [busy, setBusy] = useState(false);

  const refreshPermission = useCallback(async () => {
    if (Platform.OS === 'web') { setPermission('unsupported'); return; }
    try {
      const result = await ExpoNotifications.getPermissionsAsync();
      setPermission(result.status);
    } catch {
      setPermission('unsupported');
    }
  }, []);

  useEffect(() => {
    void refreshPermission();
    const subscription = AppState.addEventListener('change', (state) => { if (state === 'active') void refreshPermission(); });
    return () => subscription.remove();
  }, [refreshPermission]);

  async function requestPermission() {
    if (busy) return;
    setBusy(true);
    try {
      const result = await ExpoNotifications.requestPermissionsAsync();
      setPermission(result.status);
    } finally {
      setBusy(false);
    }
  }

  const statusCopy = permission === 'granted'
    ? { label: tx('알림 허용됨', 'Notifications allowed'), body: tx('여행 일정 알림을 받을 준비가 됐어요.', "You're ready to receive trip schedule alerts."), tone: color.state.success }
    : permission === 'denied'
      ? { label: tx('알림 꺼짐', 'Notifications off'), body: tx('기기 설정에서 언제든 다시 허용할 수 있어요.', 'You can allow them again anytime in device settings.'), tone: color.state.danger }
      : permission === 'unsupported'
        ? { label: tx('앱에서 설정 가능', 'Available in the app'), body: tx('알림 권한 관리는 iOS·Android 앱에서 제공해요.', 'Notification permissions are managed in the iOS/Android app.'), tone: color.text.muted }
        : permission === 'loading'
          ? { label: tx('권한 확인 중', 'Checking permission'), body: tx('기기의 알림 설정을 확인하고 있어요.', "We're checking your device's notification settings."), tone: color.text.muted }
          : { label: tx('알림을 켜볼까요?', 'Turn on notifications?'), body: tx('허용 여부를 먼저 물어본 뒤에만 알림을 보내요.', "We'll only send notifications after you allow it."), tone: color.action.primary };

  const permissionCard = (
    <>
      <View accessibilityLiveRegion="polite" style={styles.permissionCard}>
        <View style={[styles.statusDot, { backgroundColor: statusCopy.tone }]} />
        <View style={styles.permissionCopy}><Text variant="body" weight="bold">{statusCopy.label}</Text><Text variant="caption" color={color.text.body}>{statusCopy.body}</Text></View>
      </View>
      {permission === 'undetermined' && <Button label={busy ? tx('확인 중…', 'Checking…') : tx('알림 허용하기', 'Allow notifications')} disabled={busy} onPress={() => void requestPermission()} containerStyle={styles.action} />}
      {permission === 'denied' && <Button label={tx('기기 알림 설정 열기', 'Open device notification settings')} variant="tertiary" onPress={() => void Linking.openSettings()} containerStyle={styles.action} />}
    </>
  );

  if (feed.state === 'loading') {
    return <View style={styles.empty}><ActivityIndicator color={color.action.primary} /></View>;
  }

  if (feed.state === 'ready' && feed.items.length) {
    const todayKey = new Date().toISOString().slice(0, 10);
    const today = feed.items.filter((item) => item.at.slice(0, 10) === todayKey);
    const earlier = feed.items.filter((item) => item.at.slice(0, 10) !== todayKey);
    const unseenFrom = feed.seenAt;
    const row = (item: ActivityNotice) => {
      const copy = noticeCopy(item, tx);
      const fresh = unseenFrom ? item.at > unseenFrom : true;
      return (
        <Pressable key={item.id} accessibilityRole="button" accessibilityLabel={copy.title} onPress={() => router.push(`/trips/${item.itineraryId}/itinerary`)} style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
          <View style={[styles.rowIcon, item.operation === 'CREATE' && styles.rowIconCreate]}>
            <Text variant="body" weight="bold" color={item.operation === 'CREATE' ? color.state.success : color.text.heading}>{item.operation === 'CREATE' ? '✦' : '⇄'}</Text>
          </View>
          <View style={styles.rowBody}>
            <View style={styles.rowTitle}>
              <Text weight="bold" numberOfLines={1} style={styles.rowTitleText}>{copy.title}</Text>
              {fresh ? <View style={styles.freshDot} /> : null}
            </View>
            <Text variant="caption" color={color.text.body} numberOfLines={2}>{copy.body}</Text>
            <Text variant="micro" color={color.text.muted}>{relativeStoryTime(item.at, tx)}</Text>
          </View>
        </Pressable>
      );
    };
    return (
      <View style={styles.list}>
        {today.length ? <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('오늘', 'Today')}</Text> : null}
        {today.map(row)}
        {earlier.length ? <Text variant="caption" weight="bold" color={color.text.eyebrow} style={styles.sectionGap}>{tx('이전 알림', 'Earlier')}</Text> : null}
        {earlier.map(row)}
        <Text variant="caption" color={color.text.muted} style={styles.footnote}>{tx('일정이 만들어지거나 바뀌면 이곳에서 알려드려요. 동행이 바꾼 것도 보여요.', "We'll tell you here when a trip is created or changed — including changes by companions.")}</Text>
        <View style={styles.permissionWrap}>{permissionCard}</View>
      </View>
    );
  }

  return (
      <View style={styles.empty}>
        <View style={styles.icon}><Image source={bellIcon} resizeMode="contain" style={styles.iconImage} /></View>
        <Text variant="title" weight="bold">{tx('아직 도착한 알림이 없어요', 'No notifications yet')}</Text>
        <Text variant="body" color={color.text.muted} style={styles.description}>{user ? tx('여행 일정이 만들어지거나 바뀌면 이곳에서 알려드려요.', "We'll let you know here when a trip is created or changed.") : tx('로그인하면 내 여행의 소식을 여기서 볼 수 있어요.', 'Sign in to see updates about your trips here.')}</Text>
        {!user ? <Button label={tx('로그인', 'Sign in')} variant="outline" onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/notifications' } })} containerStyle={styles.action} /> : null}
        {feed.state === 'error' ? <Text variant="caption" color={color.state.danger}>{feed.message}</Text> : null}
        {permissionCard}
      </View>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacing[2], paddingTop: spacing[3] },
  sectionGap: { marginTop: spacing[3] },
  row: { flexDirection: 'row', gap: spacing[3], padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  rowIcon: { width: 40, height: 40, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.soft },
  rowIconCreate: { backgroundColor: color.state.successBg },
  rowBody: { flex: 1, minWidth: 0, gap: 2 },
  rowTitle: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  rowTitleText: { flexShrink: 1 },
  freshDot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.action.outline },
  footnote: { textAlign: 'center', marginTop: spacing[3] },
  permissionWrap: { alignItems: 'center', gap: spacing[3] },
  pressed: { opacity: 0.85 },
  empty: { flex: 1, minHeight: 420, alignItems: 'center', justifyContent: 'center', gap: spacing[3], paddingHorizontal: spacing[6] },
  icon: { width: 72, height: 72, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.state.warningBg },
  iconImage: { width: 32, height: 32 },
  description: { textAlign: 'center', lineHeight: 23 },
  permissionCard: { width: '100%', maxWidth: 360, minHeight: 72, marginTop: spacing[4], padding: spacing[4], flexDirection: 'row', alignItems: 'center', gap: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  statusDot: { width: 10, height: 10, borderRadius: radius.full },
  permissionCopy: { flex: 1, gap: spacing[1] },
  // 가운데 정렬 안에서는 껍데기가 글자 폭으로 줄어 버튼이 쪼그라든다 — 폭을 적어야 360 까지 편다(S15P21E201-1524).
  action: { width: '100%', maxWidth: 360 },
});
