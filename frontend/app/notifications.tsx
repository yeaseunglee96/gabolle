// 알림 화면.
//
// 본문은 NotificationsBody 가 그린다 — 마이페이지에서 패널로 열 때도 같은 것을 쓴다.
//
// 🔴 `scroll` 을 빼면 알림이 한 화면을 넘는 순간 아래가 영영 안 보인다 (S15P21E201-1788).
//    Screen 은 `scroll` 이 있을 때만 ScrollView 를 만들고, 없으면 그냥 View 다.
//    NotificationsBody 에는 제 스크롤이 없다 — 마이페이지에서는 MyPageSheet 의
//    ScrollView 가 감싸 주기 때문이다. 그래서 패널로 열면 멀쩡하고 이 화면만 잘렸다.
//
//    알림은 여행 12개 × 각 10건 = 최대 120행이고 한 화면에 8행쯤 들어간다. 게다가
//    「알림 허용하기」 카드가 목록 «아래»에 있어서, 알림을 켜러 들어온 사람이
//    그 버튼에 닿지 못했다.
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
    <Screen scroll>
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
