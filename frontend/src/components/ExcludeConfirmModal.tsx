// 장소 제외 전 확인 창 — S15P21E201-257.
// "장소 하나만 빠지겠지" 하고 눌렀는데 그 날짜 일정이 통째로 바뀌면 다음부터는 이 버튼을
// 안 누른다. 그래서 누르기 전에 무엇이 유지되고 무엇이 바뀔 수 있는지 여기서 밝힌다
// (상세설계서 v2 5장 F-ITN-04).
import { ActivityIndicator, Modal, Pressable, StyleSheet, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { Text } from './Text';

type ExcludeConfirmModalProps = {
  visible: boolean;
  placeTitle: string;
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
};

export function ExcludeConfirmModal({ visible, placeTitle, busy, onCancel, onConfirm }: ExcludeConfirmModalProps) {
  const { tx } = useI18n();

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <Text variant="title" weight="bold">{tx(`'${placeTitle}'를 제외할까요?`, `Exclude '${placeTitle}'?`)}</Text>
          <Text color={color.text.body}>{tx('제외하면 이 날짜 일정을 다시 계산해요. 확인하기 전에 무엇이 바뀔 수 있는지 확인해 주세요.', "Excluding this recalculates today's itinerary. Please check what may change before confirming.")}</Text>

          <View style={styles.row}>
            <View style={[styles.dot, styles.dotKeep]} />
            <View style={styles.rowCopy}>
              <Text weight="bold">{tx('유지', 'Stays the same')}</Text>
              <Text variant="caption" color={color.text.body}>{tx('고정한 장소 · 다른 날짜의 일정', 'Locked places · Other days')}</Text>
            </View>
          </View>
          <View style={styles.row}>
            <View style={[styles.dot, styles.dotChange]} />
            <View style={styles.rowCopy}>
              <Text weight="bold">{tx('변경 가능', 'May change')}</Text>
              <Text variant="caption" color={color.text.body}>{tx('이 날짜의 방문지 · 시간 · 이동거리', "This day's places, times, and walking distance")}</Text>
            </View>
          </View>

          <View style={styles.actions}>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('제외 취소', 'Cancel exclude')} accessibilityState={{ disabled: busy }} disabled={busy} onPress={onCancel} style={[styles.cancelButton, busy && styles.actionDisabled]}>
              <Text weight="bold" color={color.brand.navy}>{tx('취소', 'Cancel')}</Text>
            </Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('제외 확인', 'Confirm exclude')} accessibilityState={{ busy, disabled: busy }} disabled={busy} onPress={onConfirm} style={[styles.confirmButton, busy && styles.actionDisabled]}>
              {busy ? <ActivityIndicator color={color.text.onAction} /> : <Text weight="bold" color={color.text.onAction}>{tx('제외', 'Exclude')}</Text>}
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(11,29,58,0.62)' },
  card: { width: '100%', maxWidth: 420, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  row: { flexDirection: 'row', gap: spacing[3], alignItems: 'flex-start' },
  dot: { width: 10, height: 10, marginTop: spacing[1], borderRadius: radius.full },
  dotKeep: { backgroundColor: color.state.success },
  dotChange: { backgroundColor: color.state.danger },
  rowCopy: { flex: 1, gap: spacing[1] },
  actions: { flexDirection: 'row', gap: spacing[3], marginTop: spacing[2] },
  cancelButton: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy },
  confirmButton: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.state.danger },
  actionDisabled: { opacity: 0.5 },
});
