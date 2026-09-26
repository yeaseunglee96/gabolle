// 사용자 차단 확인창 —(애플 심사 지침 1.2, 학대 사용자 차단).
import { useState } from 'react';
import { ActivityIndicator, Modal, Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';

type BlockUserDialogProps = {
  visible: boolean;
  displayName: string;
  onClose: () => void;
  onConfirm: () => Promise<boolean>;
};

export function BlockUserDialog({ visible, displayName, onClose, onConfirm }: BlockUserDialogProps) {
  const { tx } = useI18n();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const close = () => {
    setError(null);
    onClose();
  };

  const confirm = async () => {
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    const ok = await onConfirm();
    setSubmitting(false);
    if (ok) close();
    else setError(tx('차단하지 못했어요. 다시 시도해 주세요.', 'Could not block this user. Please try again.'));
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={close}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <Text variant="title" weight="bold">{txf(tx, '%s님을 차단할까요?', 'Block %s?', displayName)}</Text>
          {/* 🔴 S15P21E201-1722 — S15P21E201-1714(가이드라인 1.2)로 차단이 양방향이 된 뒤에도
              이 문구는 「상대가 내 글을 못 본다」한 방향만 말하고 있었다. 실기(웹, 2026-09-26)로
              차단해 보면 내 피드에서도 상대 글이 바로 사라지는데, 그 사실을 안내하지 않은 것이다. */}
          <Text color={color.text.body}>{tx('이 사용자에게 내 글이 안 보이고, 내 피드에도 이 사람 글이 안 보여요.', "This user won't see your posts, and their posts won't show up in your feed either.")}</Text>
          <View style={styles.warning}>
            <Text variant="caption" weight="bold" color={color.state.danger}>
              {tx('팔로우도 함께 끊겨요. 차단을 풀어도 팔로우는 돌아오지 않아요.', "Your follow connection ends too. Unblocking does not restore it.")}
            </Text>
          </View>

          {error ? <Text accessibilityRole="alert" color={color.state.danger}>{localizeMessage(tx, error)}</Text> : null}

          <View style={styles.buttonRow}>
            <Pressable accessibilityRole="button" disabled={submitting} onPress={close} style={[styles.button, styles.cancelButton]}>
              <Text variant="body" weight="bold">{tx('취소', 'Cancel')}</Text>
            </Pressable>
            <Pressable accessibilityRole="button" accessibilityState={{ disabled: submitting }} disabled={submitting} onPress={() => void confirm()} style={[styles.button, styles.confirmButton, submitting && styles.disabled]}>
              {submitting ? <ActivityIndicator color={color.state.danger} /> : <Text variant="body" weight="bold" color={color.state.danger}>{tx('차단하기', 'Block')}</Text>}
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
  warning: { padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card },
  buttonRow: { flexDirection: 'row', gap: spacing[2] },
  button: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full },
  cancelButton: { backgroundColor: color.surface.card },
  // 위험은 채우지 않는다 — 연분홍 배경 + 빨간 글자. 주 버튼과 같은 무게로 보이면 안 된다.
  confirmButton: { backgroundColor: color.state.dangerBg },
  disabled: { opacity: 0.5 },
});
