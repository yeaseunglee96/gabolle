// 마이페이지 카드 안의 한 줄 (S15P21E201-965). `(tabs)/me.tsx` 안에만 있던 것을 탭 화면들이
// 함께 쓰도록 뺐다.
//
// 경계선은 **위쪽에 긋고 첫 줄만 뺀다.** 아래쪽에 그으면 카드 마지막 줄 밑에 선이 하나 남아
// 카드 테두리와 겹쳐 두 겹으로 보인다.
import { Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

type InfoRowProps = {
  label: string;
  /** 오른쪽 작은 글자. 「›」를 직접 붙여서 넘긴다. */
  value?: string;
  /** 라벨 아래 한 줄 설명. 있으면 줄 높이가 늘어난다. */
  description?: string;
  onPress?: () => void;
  disabled?: boolean;
  /** 카드의 첫 줄이면 위 경계선을 긋지 않는다. */
  first?: boolean;
};

export function InfoRow({ label, value, description, onPress, disabled = false, first = false }: InfoRowProps) {
  const body = (
    <>
      <View style={styles.copy}>
        <Text weight="bold">{label}</Text>
        {description ? <Text variant="caption">{description}</Text> : null}
      </View>
      {value ? <Text variant="caption" color={disabled ? color.text.muted : color.text.body}>{value}</Text> : null}
    </>
  );
  if (!onPress) {
    return <View style={[styles.row, !first && styles.divided, disabled && styles.disabled]}>{body}</View>;
  }
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [styles.row, !first && styles.divided, pressed && styles.pressed, disabled && styles.disabled]}
    >
      {body}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { minHeight: 62, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], paddingVertical: spacing[3] },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.surface.border },
  copy: { flex: 1, gap: spacing[1], minWidth: 0 },
  pressed: { opacity: 0.7, backgroundColor: color.surface.tint, borderRadius: radius.sm },
  disabled: { opacity: 0.58 },
});
