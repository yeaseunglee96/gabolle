// 내 여행 ⋯ 메뉴 — 아래에서 올라오는 시트(넓은 화면은 가운데 카드). S15P21E201-1867.
//
// 🔴 전에는 ⋯ 를 누르면 그 줄 «아래에» 메뉴 칸이 끼어들어 아래 여행들이 밀려 내려갔고, 메뉴가 오른쪽 끝에
//    작게 떠 있어 어느 여행의 메뉴인지 헷갈렸다(사용자 지적). 시트는 어느 여행인지 제목으로 말하고, 목록은 그대로 둔다.
import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { useSheetBottomPadding } from '@/components/sheetBottomInset';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';

export type TripAction = { key: string; label: string; danger?: boolean; disabled?: boolean; onPress: () => void };

export function TripActionSheet({ title, subtitle, actions, onClose, tx }: {
  title: string;
  subtitle?: string | null;
  actions: TripAction[];
  onClose: () => void;
  tx: (ko: string, en: string) => string;
}) {
  const { kind } = useLayout();
  const phone = kind === 'phone';
  const bottomPad = useSheetBottomPadding(spacing[4]);
  return (
    <Modal transparent visible animationType={phone ? 'slide' : 'fade'} onRequestClose={onClose}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={onClose} style={[styles.backdrop, phone ? styles.backdropPhone : styles.backdropWide]}>
        {/* 안쪽을 눌렀을 때 닫히지 않게 누름을 여기서 멈춘다. */}
        <Pressable onPress={() => {}} style={phone ? [styles.sheet, { paddingBottom: bottomPad }] : styles.card}>
          {phone ? <View style={styles.handle} /> : null}
          <View style={styles.head}>
            <Text weight="bold" numberOfLines={2}>{title}</Text>
            {subtitle ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{subtitle}</Text> : null}
          </View>
          <View style={styles.list}>
            {actions.map((action, index) => (
              <Pressable
                key={action.key}
                accessibilityRole="button"
                accessibilityState={{ disabled: Boolean(action.disabled) }}
                disabled={action.disabled}
                onPress={action.onPress}
                style={({ pressed }) => [styles.row, index > 0 && styles.divider, pressed && styles.pressed]}
              >
                <Text weight="bold" color={action.danger ? color.state.danger : color.text.heading}>{action.label}</Text>
              </Pressable>
            ))}
          </View>
          <Pressable accessibilityRole="button" onPress={onClose} style={({ pressed }) => [styles.cancel, pressed && styles.pressed]}>
            <Text weight="bold">{tx('취소', 'Cancel')}</Text>
          </Pressable>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(25,25,25,0.62)' },
  backdropPhone: { justifyContent: 'flex-end' },
  backdropWide: { alignItems: 'center', justifyContent: 'center', padding: spacing[4] },
  sheet: { gap: spacing[3], paddingTop: spacing[3], paddingHorizontal: spacing[4], borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg, backgroundColor: color.canvas },
  card: { gap: spacing[3], width: '100%', maxWidth: 420, padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.canvas },
  handle: { alignSelf: 'center', width: 36, height: 4, borderRadius: 2, backgroundColor: color.surface.field },
  head: { gap: 2, paddingHorizontal: spacing[1] },
  list: { borderRadius: radius.lg, backgroundColor: color.surface.card, overflow: 'hidden' },
  row: { minHeight: 52, justifyContent: 'center', paddingHorizontal: spacing[4] },
  divider: { borderTopWidth: 1, borderTopColor: color.surface.border },
  cancel: { minHeight: 52, alignItems: 'center', justifyContent: 'center', borderRadius: radius.lg, backgroundColor: color.surface.card },
  pressed: { opacity: 0.7 },
});
