// 장소 제외 전 확인 창 — S15P21E201-257.
// "장소 하나만 빠지겠지" 하고 눌렀는데 그 날짜 일정이 통째로 바뀌면 다음부터는 이 버튼을
// 안 누른다. 그래서 누르기 전에 무엇이 유지되고 무엇이 바뀔 수 있는지 여기서 밝힌다
// (상세설계서 v2 5장 F-ITN-04).
import { ActivityIndicator, Modal, Pressable, StyleSheet, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { Text } from './Text';
import { txf } from '@/i18n/format';

/**
 * 빼는 이유 — 한 번 누르면 그 이유로 뺀다(S15P21E201-1695, 조율 세션 결정 · 07 계약).
 * 값은 서버가 검사하는 기계용 코드다(계획서 8.2 운영 사유 어휘와 같은 이름). 모르는 값은 서버가 400 을 준다.
 * 「이유 없이 제외」는 건너뛰기 — 칸을 비운다(null). 「안 물었다」와 「고르지 않았다」를 가르려고 빈 글자가 아니다.
 */
export const EXCLUDE_REASONS = [
  { code: 'ALREADY_VISITED', ko: '가 봤어요', en: 'Been there' },
  { code: 'NOT_INTERESTED', ko: '취향 아님', en: 'Not my taste' },
  { code: 'TOO_FAR', ko: '멀어요', en: 'Too far' },
  { code: 'CLOSED', ko: '문 닫음', en: 'Closed' },
  { code: 'OTHER', ko: '그냥', en: 'Just because' },
] as const;
export type ExcludeReason = (typeof EXCLUDE_REASONS)[number]['code'];

type ExcludeConfirmModalProps = {
  visible: boolean;
  placeTitle: string;
  busy: boolean;
  onCancel: () => void;
  /** 고른 이유. 「이유 없이 제외」면 null. */
  onConfirm: (reason: ExcludeReason | null) => void;
};

export function ExcludeConfirmModal({ visible, placeTitle, busy, onCancel, onConfirm }: ExcludeConfirmModalProps) {
  const { tx } = useI18n();

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <Text variant="title" weight="bold">{txf(tx, '\'%s\'를 제외할까요?', 'Exclude \'%s\'?', placeTitle)}</Text>
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

          <View style={styles.reasonBlock}>
            <Text variant="caption" weight="bold">{tx('왜 빼나요? 누르면 바로 빼요', 'Why remove it? Tap one to remove right away')}</Text>
            <View style={styles.reasonRow}>
              {EXCLUDE_REASONS.map((reason) => (
                <Pressable
                  key={reason.code}
                  accessibilityRole="button"
                  accessibilityLabel={txf(tx, '%s — 이 이유로 제외', '%s — remove for this reason', tx(reason.ko, reason.en))}
                  accessibilityState={{ disabled: busy }}
                  disabled={busy}
                  onPress={() => onConfirm(reason.code)}
                  style={({ pressed }) => [styles.reasonChip, (pressed || busy) && styles.actionDisabled]}
                >
                  <Text variant="caption" weight="bold" color={color.text.heading}>{tx(reason.ko, reason.en)}</Text>
                </Pressable>
              ))}
            </View>
          </View>

          <View style={styles.actions}>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('제외 취소', 'Cancel exclude')} accessibilityState={{ disabled: busy }} disabled={busy} onPress={onCancel} style={[styles.cancelButton, busy && styles.actionDisabled]}>
              <Text weight="bold" color={color.text.heading}>{tx('취소', 'Cancel')}</Text>
            </Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('이유 없이 제외', 'Remove without a reason')} accessibilityState={{ busy, disabled: busy }} disabled={busy} onPress={() => onConfirm(null)} style={[styles.confirmButton, busy && styles.actionDisabled]}>
              {busy ? <ActivityIndicator color={color.state.danger} /> : <Text weight="bold" color={color.state.danger}>{tx('이유 없이 제외', 'Remove without a reason')}</Text>}
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  card: { width: '100%', maxWidth: 420, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  row: { flexDirection: 'row', gap: spacing[3], alignItems: 'flex-start' },
  dot: { width: 10, height: 10, marginTop: spacing[1], borderRadius: radius.full },
  dotKeep: { backgroundColor: color.state.success },
  // 글자 없는 점이라 점 전용 빨강을 쓴다. 채움·글자의 동백과 섞지 않는다.
  dotChange: { backgroundColor: color.state.dot },
  rowCopy: { flex: 1, gap: spacing[1] },
  reasonBlock: { gap: spacing[2], marginTop: spacing[1] },
  reasonRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  reasonChip: { minHeight: 40, justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.soft },
  actions: { flexDirection: 'row', gap: spacing[3], marginTop: spacing[2] },
  cancelButton: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.action.tertiary },
  confirmButton: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.state.dangerBg },
  actionDisabled: { opacity: 0.5 },
});
