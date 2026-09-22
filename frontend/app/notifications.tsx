// 알림 화면.
//
// 본문은 NotificationsBody 가 그린다 — 마이페이지에서 패널로 열 때도 같은 것을 쓴다.
import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { NotificationsBody } from '@/me/panels/NotificationsBody';

export default function Notifications() {
  const router = useRouter();
  const { tx } = useI18n();
  return (
    <Screen>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('홈으로 돌아가기', 'Back to home')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={styles.backButton}>
          <Text variant="title">‹</Text>
        </Pressable>
        <Text variant="title" weight="bold">{tx('알림', 'Notifications')}</Text>
        <View style={styles.headerSpacer} />
      </View>
      <NotificationsBody />
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  backButton: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  headerSpacer: { width: 44 },
});
