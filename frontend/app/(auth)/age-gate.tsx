// 신규 화면 — Figma 23 화면 표에 없다. 원본 요청서에서 새로 추가된 자리라 최대한 단순하게 만든다.
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Button } from '@/components/Button';

export default function AgeGate() {
  const router = useRouter();
  const [checked, setChecked] = useState(false);

  return (
    <Screen>
      <View style={styles.body}>
        <Text variant="display" weight="bold">
          만 14세 이상이신가요?
        </Text>
        <Text variant="body" style={styles.description}>
          생년월일은 묻지 않아요. 이 확인 사실 외에는 아무것도 저장하지 않습니다.
        </Text>
      </View>

      <View style={styles.footer}>
        <Pressable style={styles.checkboxRow} onPress={() => setChecked((prev) => !prev)}>
          <View style={[styles.checkbox, checked && styles.checkboxChecked]}>
            {checked && (
              <Text variant="caption" weight="bold" color={color.text.onAction}>
                ✓
              </Text>
            )}
          </View>
          <Text variant="body">만 14세 이상이며, 위 내용을 확인했어요.</Text>
        </Pressable>

        <Button label="계속" disabled={!checked} onPress={() => router.replace('/permissions')} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: {
    flex: 1,
    justifyContent: 'center',
    gap: spacing[2],
  },
  description: {
    color: color.text.body,
  },
  footer: {
    gap: spacing[4],
  },
  checkboxRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
  },
  checkbox: {
    width: 24,
    height: 24,
    borderRadius: radius.sm,
    borderWidth: 1.5,
    borderColor: color.surface.field,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkboxChecked: {
    backgroundColor: color.action.primary,
    borderColor: color.action.primary,
  },
});
