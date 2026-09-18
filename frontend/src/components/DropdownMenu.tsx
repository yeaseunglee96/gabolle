// 케밥(⋯) 버튼을 눌러서 여는 작은 메뉴 — S15P21E201-1244.
//
// 🔴 버튼 바로 아래에 정확히 붙이지 않는다. 이 화면(피드 상세)은 스크롤이 되는데,
// RN 에서 스크롤 중에도 버튼의 화면 위치를 계속 재는 것(measureInWindow)은 이
// 저장소에 아직 한 번도 쓴 적 없는 방식이라 새 위험을 늘린다. 대신 화면 우상단
// 고정 자리에 연다 — 케밥이 있는 자리(카드 맨 위)와 화면을 막 열었을 때 보이는
// 자리가 같아서 어색하지 않다.
//
// 🔴 기존 ReportModal·BlockUserDialog 는 배경(backdrop)을 눌러도 안 닫힌다 — 이 메뉴는
// 「드롭다운」이라 바깥을 누르면 닫히는 것이 자연스러워 여기서 새로 넣는다.
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
