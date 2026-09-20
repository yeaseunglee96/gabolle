// 휠체어 접근이 확인 안 된 일정임을 알리는 창 — S15P21E201-1160.
import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { Text } from './Text';
import { txf } from '@/i18n/format';

type AccessibilityUnverifiedModalProps = {
  visible: boolean;
  /** 이 일정에서 접근성이 확인되지 않은 장소 수. 0이면 이 창을 띄우지 않는다. */
  unverifiedCount: number;
  /** 일정 전체 장소 수. 0이면 "N곳 중" 을 쓰지 않고 "N곳" 으로만 말한다. */
  totalCount: number;
  onClose: () => void;
};

export function AccessibilityUnverifiedModal({ visible, unverifiedCount, totalCount, onClose }: AccessibilityUnverifiedModalProps) {
  const { tx } = useI18n();

  // 전체 수를 모르면(일정을 아직 못 읽었다) 분모를 지어내지 않는다.
  const headline = totalCount > 0
    // 영어는 「전체 중 몇」이 아니라 「몇 / 전체」 순이라 값 순서를 한국어(전체 · 몇)에 맞춰 영어 틀을 고쳐 적었다 — txf 는 앞에서부터 차례로 끼운다.
    ? txf(tx, '이번 일정 %s곳 중 %s곳은 휠체어로 들어갈 수 있는지 아직 확인되지 않았어요.', 'Of the %s places in this trip, %s have not been checked for wheelchair access yet.', totalCount, unverifiedCount)
    : txf(tx, '이번 일정의 %s곳은 휠체어로 들어갈 수 있는지 아직 확인되지 않았어요.', '%s places in this trip have not been checked for wheelchair access yet.', unverifiedCount);

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <View style={styles.mark}>
            <Text weight="bold" color={color.brand.navy} style={styles.markGlyph}>♿</Text>
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
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  card: { width: '100%', maxWidth: 420, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  mark: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.subtle },
  markGlyph: { fontSize: 26, lineHeight: 32 },
  confirmButton: { minHeight: 48, marginTop: spacing[2], alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.navy },
  confirmPressed: { opacity: 0.82 },
});
