// 이동 보조(휠체어·유아차·큰 짐) 접근이 확인 안 된 일정임을 알리는 창 — S15P21E201-1160, -1814.
import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { Text } from './Text';
import { accessibilityHeadline, type MobilityAid } from '@/plan/accessibilityNotice';

type AccessibilityUnverifiedModalProps = {
  visible: boolean;
  /** 이 일정에서 접근성이 확인되지 않은 장소 수. 0이면 이 창을 띄우지 않는다. */
  unverifiedCount: number;
  /** 일정 전체 장소 수. 0이면 "N곳 중" 을 쓰지 않고 "N곳" 으로만 말한다. */
  totalCount: number;
  /** 이번 여행에서 고른 이동 보조. 비었으면(모른다) 「고르신 이동 조건」으로 말한다. */
  aids: readonly MobilityAid[];
  onClose: () => void;
  /** true 면 창(Modal)이 아니라 결과 화면 안의 작은 안내로 — S15P21E201-1994. */
  inline?: boolean;
};

export function AccessibilityUnverifiedModal({ visible, unverifiedCount, totalCount, aids, onClose, inline = false }: AccessibilityUnverifiedModalProps) {
  const { tx } = useI18n();

  // 전체 수를 모르면(일정을 아직 못 읽었다) 분모를 지어내지 않는다.
  // 문장은 고른 이동 보조의 말로 쓴다 — 유아차만 고른 사람에게 휠체어 문장을 띄우지 않는다(S15P21E201-1814).
  const headline = accessibilityHeadline(tx, aids, unverifiedCount, totalCount);

  // 🔴 결과 화면을 막는 창 대신 안내 한 장 — S15P21E201-1994. 「12곳 중 12곳 미확인」 창이 승차권 앞을 막아
  //    처음 보는 사람에게 「기능이 아직 안 된다」로 읽혔다. 반영한 것(경사)을 먼저 말하고, 미확인은 그 뒤에 적는다.
  if (inline) {
    if (!visible) return null;
    return (
      <View testID="accessibility-inline-notice" accessibilityRole="summary" style={styles.inlineCard}>
        <Text weight="bold" color={color.text.heading}>{tx('가는 길의 경사는 반영했어요', 'We factored in how steep the way is')}</Text>
        <Text variant="caption" color={color.text.body}>{headline} {tx('갈 수 없다는 뜻은 아니에요.', "That doesn't mean you can't go.")}</Text>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('안내 닫기', 'Dismiss notice')} onPress={onClose} hitSlop={8} style={styles.inlineDismiss}>
          <Text variant="caption" weight="bold" color={color.action.secondary}>{tx('알겠어요', 'Got it')}</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <View style={styles.mark}>
            <Text weight="bold" color={color.brand.navy} style={styles.markGlyph}>{aids.length === 1 && aids[0] !== 'WHEELCHAIR' ? '!' : '♿'}</Text>
          </View>

          <Text variant="title" weight="bold">{tx('확인되지 않은 곳이 있어요', 'Some places are unchecked')}</Text>
          <Text color={color.text.body}>{headline}</Text>

          {/* "못 간다" 로 읽히지 않게, 우리가 실제로 반영한 것을 같이 말한다.
              경사 값은 2,682곳에 들어가 있어서 이 문장은 빈말이 아니다.
          */}
          <Text color={color.text.body}>{tx('갈 수 없다는 뜻은 아니에요. 아직 아무도 확인하지 않았다는 뜻이에요. 가는 길의 경사는 반영했습니다.', "That doesn't mean you can't go — it means no one has checked yet. We did factor in how steep the way there is.")}</Text>

          <Pressable
            accessibilityRole="button"
            accessibilityLabel={tx('확인', 'OK')}
            onPress={onClose}
            style={({ pressed }) => [styles.confirmButton, pressed && styles.confirmPressed]}
          >
            <Text weight="bold" color={color.text.onAction}>{tx('확인', 'OK')}</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  inlineCard: { gap: spacing[1], marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  inlineDismiss: { alignSelf: 'flex-end', minHeight: 32, justifyContent: 'center' },
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  card: { width: '100%', maxWidth: 420, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  mark: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.subtle },
  markGlyph: { fontSize: 26, lineHeight: 32 },
  confirmButton: { minHeight: 48, marginTop: spacing[2], alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.navy },
  confirmPressed: { opacity: 0.82 },
});
