import { StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

// 여행 식별자 없이 열린 화면이 쓰는 안내 (S15P21E201-1000). 예전에는 이럴 때 'demo-trip'
// 이라는 가짜 식별자로 서버를 불렀고, 서버는 그것을 UUID 로 못 읽어 개발자용 오류를
// 그대로 냈다 — 그 문장이 사용자 화면에 보였다. 부르지 않는 것이 고치는 방법이다.
export function SelectTripFirst() {
  const router = useRouter();
  const { tx } = useI18n();
  return (
    <Screen scroll>
      <View style={styles.card}>
        <Text variant="title" weight="bold">{tx('먼저 여행을 골라 주세요', 'Choose a trip first')}</Text>
        <Text color={color.text.body}>{tx('이 화면은 어떤 여행인지 알아야 보여드릴 수 있어요.', 'We need to know which trip this is before we can show it.')}</Text>
        <Button label={tx('내 여행 보기', 'View my trips')} onPress={() => router.replace('/trips')} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { marginTop: spacing[6], gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
});
