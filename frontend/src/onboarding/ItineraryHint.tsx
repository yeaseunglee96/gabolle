// 일정 첫 힌트 — 시안 5 의 04g. 첫 일정을 열었을 때 목록 위에 한 번 뜨는 말풍선.
//
// 막도 단계도 없다. 일정 화면에서 처음 온 사람이 모르는 것은 딱 하나 — 「이건 초안이고 내가
// 고칠 수 있다」. 고정·제외·다시 계산 세 낱말만 알려 주고 「알겠어요」로 끝난다.
import { Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

export function ItineraryHint({ onDone }: { onDone: () => void }) {
  const { tx } = useI18n();
  return (
    <View accessibilityRole="alert" style={styles.card}>
      <Text variant="body" weight="bold" color={color.text.onAction}>{tx('이 일정은 초안이에요', 'This itinerary is a draft')}</Text>
      <Text variant="util" color={color.text.onDarkMuted}>
        {tx('꼭 갈 곳은 🔒 고정, 빼고 싶은 곳은 「이 장소 제외」. 그다음 「다시 계산」이 나머지를 맞춰요.', 'Lock 🔒 the places you must visit, remove the ones you don’t want, then “Recalculate” fits the rest around them.')}
      </Text>
      <Pressable accessibilityRole="button" onPress={onDone} style={({ pressed }) => [styles.ok, pressed && styles.pressed]}>
        <Text variant="util" weight="bold" color={color.text.onAction}>{tx('알겠어요', 'Got it')}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { marginHorizontal: spacing[4], marginBottom: spacing[3], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.action.secondary, gap: spacing[1] },
  ok: { alignSelf: 'flex-end', minHeight: 36, paddingHorizontal: spacing[2], justifyContent: 'center' },
  pressed: { opacity: 0.8 },
});
