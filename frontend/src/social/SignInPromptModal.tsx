// 로그인 유도 —. 피드를 로그인 없이 볼 수 있게 열었으니(-974), 얼마쯤
// 보고 나면 가입을 권한다. 인스타그램이 하는 방식이다.
import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

type SignInPromptModalProps = {
  visible: boolean;
  onClose: () => void;
  onSignIn: () => void;
};

export function SignInPromptModal({ visible, onClose, onSignIn }: SignInPromptModalProps) {
  const { tx } = useI18n();
  const gains: Array<[string, string]> = [
    ['여행 기록을 남길 수 있어요', 'Write your own travel records'],
    ['팔로우한 사람의 기록만 모아 볼 수 있어요', 'See a feed of just the people you follow'],
    ['마음에 든 장소를 저장해 여행에 넣을 수 있어요', 'Save places you like and add them to a trip'],
  ];

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <Text variant="title" weight="bold">{tx('여기까지는 로그인 없이 볼 수 있어요', "You've been browsing without an account")}</Text>
          <Text color={color.text.body}>{tx('계정을 만들면 이런 것을 할 수 있어요.', 'With an account you can do this:')}</Text>

          <View style={styles.gainList}>
            {gains.map(([ko, en]) => (
              <View key={ko} style={styles.gainRow}>
                <Text variant="body" weight="bold" color={color.brand.orange}>·</Text>
                <Text color={color.text.body} style={styles.gainText}>{tx(ko, en)}</Text>
              </View>
            ))}
          </View>

          <View style={styles.buttonRow}>
            {/* 닫기가 먼저다 — 닫을 수 없어 보이면 그 자체가 막는 창이 된다. */}
            <Pressable accessibilityRole="button" onPress={onClose} style={[styles.button, styles.ghostButton]}>
              <Text variant="body" weight="bold">{tx('계속 둘러보기', 'Keep browsing')}</Text>
            </Pressable>
            <Pressable accessibilityRole="button" onPress={onSignIn} style={[styles.button, styles.primaryButton]}>
              <Text variant="body" weight="bold" color={color.text.onAction}>{tx('로그인', 'Sign in')}</Text>
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
  gainList: { gap: spacing[2] },
  gainRow: { flexDirection: 'row', gap: spacing[2] },
  gainText: { flex: 1 },
  buttonRow: { flexDirection: 'row', gap: spacing[2] },
  button: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full },
  ghostButton: { backgroundColor: color.surface.card },
  primaryButton: { backgroundColor: color.brand.orange },
});
