// 케밥(⋯) 버튼을 눌러서 여는 작은 메뉴 — S15P21E201-1244.
//
// 메뉴는 누른 버튼 바로 아래에 뜬다(S15P21E201-1576). 전에는 언제나 화면 오른쪽 위였다 —
// 폰에서는 원글 ⋯ 가 그 근처라 티가 안 났는데, 넓은 화면에서 댓글 ⋯ 를 누르면 메뉴가
// 반대편 구석에 떠서 「안 열린다」로 읽혔다.
import { useRef, useState } from 'react';
import { Modal, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { color, radius, spacing } from '@/design/tokens';
import { Text } from './Text';

export type DropdownMenuItem = {
  key: string;
  label: string;
  /** 삭제·차단처럼 되돌리기 어렵거나 남에게 안 좋은 뜻의 항목 — 위험색으로 그린다. */
  destructive?: boolean;
  /** 이름 아래 작은 설명 줄 — 「일정 편집」의 「순서·고정·제외·다시 계산」처럼. 없으면 안 그린다. */
  hint?: string;
  onPress: () => void;
};

/** 메뉴를 연 버튼이 화면 어디에 있나 — measureInWindow 가 주는 값 그대로. */
export type MenuAnchor = { x: number; y: number; width: number; height: number };

const ITEM_HEIGHT = 48;
const GAP = 4;

/**
 * 버튼 바로 아래, 버튼 오른쪽 끝에 맞춘다. 아래가 모자라면 버튼 위로 올린다 —
 * 맨 끝 댓글의 ⋯ 는 화면 바닥 근처라 아래로 펴면 잘린다.
 * 버튼을 아직 안 쟀으면(null) 예전 자리인 화면 오른쪽 위.
 */
export function menuPosition(
  anchor: MenuAnchor | null,
  itemCount: number,
  screen: { width: number; height: number },
  insets: { top: number; bottom: number },
): { top: number; right: number } {
  if (!anchor) return { top: insets.top + 64, right: spacing[4] };
  const menuHeight = itemCount * (ITEM_HEIGHT + 1);
  const below = anchor.y + anchor.height + GAP;
  const top = below + menuHeight <= screen.height - insets.bottom
    ? below
    : Math.max(insets.top, anchor.y - GAP - menuHeight);
  return { top, right: Math.max(spacing[2], screen.width - (anchor.x + anchor.width)) };
}

/** ⋯ 버튼에 달 ref 와, 누르면 그 버튼 자리를 재고 나서 메뉴를 여는 함수. */
export function useDropdownMenu() {
  const buttonRef = useRef<View>(null);
  const [anchor, setAnchor] = useState<MenuAnchor | null>(null);
  const [open, setOpen] = useState(false);
  const openMenu = () => {
    buttonRef.current?.measureInWindow((x, y, width, height) => {
      setAnchor({ x, y, width, height });
      setOpen(true);
    });
  };
  return { buttonRef, anchor, open, openMenu, close: () => setOpen(false) };
}

export function DropdownMenu({ visible, anchor, items, onClose }: {
  visible: boolean;
  anchor: MenuAnchor | null;
  items: DropdownMenuItem[];
  onClose: () => void;
}) {
  const insets = useSafeAreaInsets();
  const screen = useWindowDimensions();
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <Pressable accessibilityRole="button" accessibilityLabel="메뉴 닫기" style={StyleSheet.absoluteFill} onPress={onClose} />
      <View style={[styles.menu, menuPosition(anchor, items.length, screen, insets)]}>
        {items.map((item, index) => (
          <Pressable
            key={item.key}
            accessibilityRole="menuitem"
            onPress={() => { onClose(); item.onPress(); }}
            style={({ pressed }) => [styles.item, index > 0 && styles.itemBorder, pressed && styles.pressed]}
          >
            <Text variant="body" weight="bold" color={item.destructive ? color.state.danger : color.text.heading}>{item.label}</Text>
            {item.hint ? <Text variant="caption" color={color.text.muted}>{item.hint}</Text> : null}
          </Pressable>
        ))}
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  menu: {
    position: 'absolute',
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
  item: { minHeight: ITEM_HEIGHT, justifyContent: 'center', paddingHorizontal: spacing[4], paddingVertical: spacing[2] },
  itemBorder: { borderTopWidth: 1, borderTopColor: color.surface.border },
  pressed: { opacity: 0.72 },
});
