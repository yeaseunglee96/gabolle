// 케밥(⋯) 버튼을 눌러서 여는 작은 메뉴 — S15P21E201-1244.
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { color, radius, spacing } from '@/design/tokens';
import { Text } from './Text';

export type DropdownMenuItem = {
  key: string;
  label: string;
  /** 삭제·차단처럼 되돌리기 어렵거나 남에게 안 좋은 뜻의 항목 — 위험색으로 그린다. */
  destructive?: boolean;
  onPress: () => void;
};

export function DropdownMenu({ visible, items, onClose }: {
  visible: boolean;
  items: DropdownMenuItem[];
  onClose: () => void;
}) {
  const insets = useSafeAreaInsets();
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <Pressable accessibilityRole="button" accessibilityLabel="메뉴 닫기" style={StyleSheet.absoluteFill} onPress={onClose} />
      <View style={[styles.menu, { top: insets.top + 64 }]}>
        {items.map((item, index) => (
          <Pressable
            key={item.key}
            accessibilityRole="menuitem"
            onPress={() => { onClose(); item.onPress(); }}
            style={({ pressed }) => [styles.item, index > 0 && styles.itemBorder, pressed && styles.pressed]}
          >
            <Text variant="body" weight="bold" color={item.destructive ? color.state.danger : color.text.heading}>{item.label}</Text>
          </Pressable>
        ))}
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  menu: {
    position: 'absolute',
    right: spacing[4],
    minWidth: 180,
    borderRadius: radius.md,
    backgroundColor: color.brand.ivory,
    shadowColor: color.brand.navy,
    shadowOpacity: 0.16,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 6,
    overflow: 'hidden',
  },
  item: { minHeight: 48, justifyContent: 'center', paddingHorizontal: spacing[4] },
  itemBorder: { borderTopWidth: 1, borderTopColor: color.surface.border },
  pressed: { opacity: 0.72 },
});
