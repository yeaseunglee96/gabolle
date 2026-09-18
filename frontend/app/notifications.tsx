import { useCallback, useEffect, useState } from 'react';
import { AppState, Image, Linking, Platform, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import * as ExpoNotifications from 'expo-notifications';

import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

const bellIcon = require('../assets/icons/home/bell.png');

export default function Notifications() {
  const router = useRouter();
  const { tx } = useI18n();
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

  return (
    <Screen>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('홈으로 돌아가기', 'Back to home')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={styles.backButton}>
          <Text variant="title">‹</Text>
        </Pressable>
        <Text variant="title" weight="bold">{tx('알림', 'Notifications')}</Text>
        <View style={styles.headerSpacer} />
      </View>
      <View style={styles.empty}>
        <View style={styles.icon}><Image source={bellIcon} resizeMode="contain" style={styles.iconImage} /></View>
        <Text variant="title" weight="bold">{tx('아직 도착한 알림이 없어요', 'No notifications yet')}</Text>
        <Text variant="body" color={color.text.muted} style={styles.description}>{tx('여행 일정 생성과 변경 알림 API가 연결되면 이곳에서 확인할 수 있어요.', "Once the trip creation and change alert API is connected, you'll see them here.")}</Text>
        <View accessibilityLiveRegion="polite" style={styles.permissionCard}>
          <View style={[styles.statusDot, { backgroundColor: statusCopy.tone }]} />
          <View style={styles.permissionCopy}><Text variant="body" weight="bold">{statusCopy.label}</Text><Text variant="caption" color={color.text.body}>{statusCopy.body}</Text></View>
        </View>
        {permission === 'undetermined' && <Button label={busy ? tx('확인 중…', 'Checking…') : tx('알림 허용하기', 'Allow notifications')} disabled={busy} onPress={() => void requestPermission()} containerStyle={styles.action} />}
        {permission === 'denied' && <Button label={tx('기기 알림 설정 열기', 'Open device notification settings')} variant="ghost" onPress={() => void Linking.openSettings()} containerStyle={styles.action} />}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  backButton: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  headerSpacer: { width: 44 },
  empty: { flex: 1, minHeight: 420, alignItems: 'center', justifyContent: 'center', gap: spacing[3], paddingHorizontal: spacing[6] },
  icon: { width: 72, height: 72, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.state.warningBg },
  iconImage: { width: 32, height: 32 },
  description: { textAlign: 'center', lineHeight: 23 },
  permissionCard: { width: '100%', maxWidth: 360, minHeight: 72, marginTop: spacing[4], padding: spacing[4], flexDirection: 'row', alignItems: 'center', gap: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  statusDot: { width: 10, height: 10, borderRadius: radius.full },
  permissionCopy: { flex: 1, gap: spacing[1] },
  action: { maxWidth: 360 },
});
