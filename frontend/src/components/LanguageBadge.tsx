import { Pressable, StyleSheet } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { Text } from './Text';
import { useI18n } from '@/i18n';

export function LanguageBadge() {
  const { language, setLanguage, tx } = useI18n();
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={tx('언어를 영어로 변경', 'Change language to Korean')} onPress={() => setLanguage(language === 'ko' ? 'en' : 'ko')} style={({ pressed }) => [styles.badge, pressed && styles.pressed]}>
      <Text variant="caption" weight="bold" color={color.text.accent}>
        {language === 'ko' ? '한국어 · EN' : 'English · KO'}
      </Text>
    </Pressable>
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
  pressed: { opacity: 0.7 },
});
