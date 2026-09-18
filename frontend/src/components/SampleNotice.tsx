// 「이 자리는 진짜 내 데이터가 아니다」 를 말하는 띠 —.
import { StyleSheet, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { Text } from './Text';

export function SampleNotice({ badge, description }: { badge: string; description: string }) {
  return (
    <View accessibilityRole="summary" style={styles.row}>
      <View style={styles.chip}><Text variant="caption" weight="bold">{badge}</Text></View>
      <Text variant="caption" color={color.text.body} style={styles.description}>{description}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], marginBottom: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  chip: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.card },
  description: { flex: 1 },
});
