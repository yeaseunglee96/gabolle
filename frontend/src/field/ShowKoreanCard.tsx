// 말이 안 통할 때 상대에게 보여 주는 한국어 카드 — S15P21E201-1879.
// 화면 언어와 상관없이 한국어로 크게 보이고, 아래에 내 언어 뜻을 작게 단다. 「크게 보여 주기」는 화면 가득 펼친다.
import { useState } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import type { Bilingual } from '@/field/emergencyContacts';

export function ShowKoreanCard({ korean, gloss, testID }: { korean: string; gloss: Bilingual; testID?: string }) {
  const { tx } = useI18n();
  const [big, setBig] = useState(false);
  return <View testID={testID} style={styles.card}>
    <Text variant="caption" weight="bold" color={color.text.muted}>{tx('말이 안 통하면 이 화면을 보여 주세요', 'Show this screen if you cannot make yourself understood')}</Text>
    {/* 🔴 번역하지 않는다 — 읽는 사람이 한국 사람이다. */}
    <Text variant="title" weight="bold" color={color.text.heading} style={styles.korean}>{korean}</Text>
    <Text variant="caption" color={color.text.body}>{tx(gloss.ko, gloss.en)}</Text>
    <Pressable accessibilityRole="button" onPress={() => setBig(true)} style={({ pressed }) => [styles.bigButton, pressed && styles.pressed]}>
      <Text variant="body" weight="bold" color={color.text.heading}>{tx('크게 보여 주기', 'Show it large')}</Text>
    </Pressable>
    <Modal visible={big} animationType="fade" onRequestClose={() => setBig(false)}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={() => setBig(false)} style={styles.full}>
        <Text weight="bold" color={color.text.heading} style={styles.fullText}>{korean}</Text>
        <Text variant="caption" color={color.text.muted}>{tx('화면을 누르면 닫혀요', 'Tap to close')}</Text>
      </Pressable>
    </Modal>
  </View>;
}

const styles = StyleSheet.create({
  card: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  korean: { lineHeight: 34 },
  bigButton: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.tint },
  pressed: { opacity: 0.7 },
  full: { flex: 1, gap: spacing[6], alignItems: 'center', justifyContent: 'center', padding: spacing[6], backgroundColor: color.surface.card },
  fullText: { fontSize: 40, lineHeight: 56, textAlign: 'center' },
});
