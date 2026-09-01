// 06·08 화면 우상단에 반복되는 "한국어 · EN" 배지. 언어 전환은 아직 안 붙인다(표시만).
import { StyleSheet, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { Text } from './Text';

export function LanguageBadge() {
  return (
    <View style={styles.badge}>
      <Text variant="caption" weight="bold" color={color.text.accent}>
        한국어 · EN
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    alignSelf: 'flex-end',
    backgroundColor: color.surface.soft,
    borderRadius: radius.md,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[1],
  },
});
