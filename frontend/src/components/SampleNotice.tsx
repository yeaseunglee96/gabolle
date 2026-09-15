// 「이 자리는 진짜 내 데이터가 아니다」 를 말하는 띠 — S15P21E201-1009.
//
// 🔴 화면마다 사실이 다르다. 어떤 자리는 지어낸 숫자고, 어떤 자리는 실측인데 이 여행의
// 것이 아니다. 그래서 배지 글자와 설명을 화면이 정하게 두고 여기서는 모양만 맞춘다 —
// 전부 「샘플」로 뭉개면 실측을 가짜라고 말하게 되고, 그것도 거짓말이다.
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
