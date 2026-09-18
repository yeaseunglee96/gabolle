// 휠체어 접근이 확인 안 된 일정임을 알리는 창 — S15P21E201-1160.
//
// 🔴 이 창이 있는 이유는 "확인된 곳만 골라 줬겠지" 라는 오해 하나 때문이다.
//
//    사용자가 휠체어 조건을 켜면, 화면은 그 조건으로 걸러진 결과를 보여 준다고 읽힌다.
//    실제로는 접근성이 확인된 장소가 운영 2,762곳 중 102곳(3.7%)뿐이다. 나머지는
//    **못 들어간다고 확인된 것이 아니라 아무도 안 재 본 것**이고, S15P21E201-540 이
//    그 "모름" 을 탈락으로 세지 않게 고친 덕에 비로소 결과가 나오기 시작했다.
//
//    그러니 결과는 나오는데 그 결과가 무엇인지는 말해 주지 않는 상태가 된다. 이 창이
//    그 자리를 메운다 — 걸러 줬다고 믿게 두지 않는다.
//
// 🔴 문구는 "못 들어간다" 가 아니라 "안 재 봤다" 여야 한다.
//
//    영어도 Not verified 이지 Not accessible 이 아니다. 두 말의 차이가 이 창의 전부다.
//    전자는 사용자가 직접 확인할 여지를 남기고, 후자는 갈 수 있는 곳을 못 가게 만든다.
//
// 🔴 버튼이 하나인 것도 결정이다 (2026-09-17).
//
//    "조건 바꾸기" 를 같이 주는 안이 있었지만, 조건을 바꿔도 확인된 곳은 여전히 102곳이라
//    사용자가 할 수 있는 일이 실제로는 없다. 할 수 없는 일을 버튼으로 주면 그 버튼을
//    누른 사람은 두 번 실망한다.
import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { Text } from './Text';

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
    ? tx(
        `이번 일정 ${totalCount}곳 중 ${unverifiedCount}곳은 휠체어로 들어갈 수 있는지 아직 확인되지 않았어요.`,
        `${unverifiedCount} of the ${totalCount} places in this trip have not been checked for wheelchair access yet.`,
      )
    : tx(
        `이번 일정의 ${unverifiedCount}곳은 휠체어로 들어갈 수 있는지 아직 확인되지 않았어요.`,
        `${unverifiedCount} places in this trip have not been checked for wheelchair access yet.`,
      );

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <View style={styles.mark}>
            <Text weight="bold" color={color.brand.navy} style={styles.markGlyph}>♿</Text>
          </View>

          <Text variant="title" weight="bold">{tx('확인되지 않은 곳이 있어요', 'Some places are unchecked')}</Text>
          <Text color={color.text.body}>{headline}</Text>

          {/* 🔴 "못 간다" 로 읽히지 않게, 우리가 실제로 반영한 것을 같이 말한다.
              경사 값은 2,682곳에 들어가 있어서 이 문장은 빈말이 아니다. */}
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
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(11,29,58,0.62)' },
  card: { width: '100%', maxWidth: 420, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  mark: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.subtle },
  markGlyph: { fontSize: 26, lineHeight: 32 },
  confirmButton: { minHeight: 48, marginTop: spacing[2], alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.navy },
  confirmPressed: { opacity: 0.82 },
});
