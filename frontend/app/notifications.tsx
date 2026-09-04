import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

export default function Notifications() {
  const router = useRouter();

  return (
    <Screen>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" accessibilityLabel="홈으로 돌아가기" onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={styles.backButton}>
          <Text variant="title">‹</Text>
        </Pressable>
        <Text variant="title" weight="bold">알림</Text>
        <View style={styles.headerSpacer} />
      </View>
      <View style={styles.empty}>
        <View style={styles.icon}><Text variant="display">🔔</Text></View>
        <Text variant="title" weight="bold">아직 도착한 알림이 없어요</Text>
        <Text variant="body" color={color.text.muted} style={styles.description}>여행 일정 생성과 변경 알림 API가 연결되면 이곳에서 확인할 수 있어요.</Text>
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
  description: { textAlign: 'center', lineHeight: 23 },
});
