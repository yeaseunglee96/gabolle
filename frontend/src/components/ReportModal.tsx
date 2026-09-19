// 여행 기록 신고 — 카드의 ⋯ 메뉴에서 연다.
import { useState } from 'react';
import { ActivityIndicator, Modal, Pressable, StyleSheet, TextInput, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { REPORT_REASON_LABEL, type StoryReportReason } from '@/social/stories';
import { Text } from './Text';

const REASONS: StoryReportReason[] = ['PRIVACY', 'OFFENSIVE', 'SPAM', 'OTHER'];
const DETAIL_MAX = 300;

type ReportModalProps = {
  visible: boolean;
  onClose: () => void;
  onSubmit: (reason: StoryReportReason, detail: string | undefined) => Promise<boolean>;
};

export function ReportModal({ visible, onClose, onSubmit }: ReportModalProps) {
  const { tx } = useI18n();
  const [reason, setReason] = useState<StoryReportReason | null>(null);
  const [detail, setDetail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const close = () => {
    setReason(null);
    setDetail('');
    setError(null);
    onClose();
  };

  const submit = async () => {
    if (!reason || submitting) return;
    setSubmitting(true);
    setError(null);
    const ok = await onSubmit(reason, reason === 'OTHER' ? detail.trim() || undefined : undefined);
    setSubmitting(false);
    if (ok) close();
    else setError(tx('신고를 접수하지 못했어요. 다시 시도해 주세요.', 'Could not submit the report. Please try again.'));
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={close}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <View style={styles.header}>
            <Text variant="title" weight="bold">{tx('신고하기', 'Report')}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={close} style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}>
              <Text variant="title" weight="bold">✕</Text>
            </Pressable>
          </View>

          <Text variant="caption" color={color.text.body}>{tx('신고 사유를 골라 주세요.', 'Choose a reason for reporting.')}</Text>

          <View accessibilityRole="radiogroup" style={styles.reasonList}>
            {REASONS.map((value) => {
              const selected = reason === value;
              return (
                <Pressable key={value} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => setReason(value)} style={[styles.reasonOption, selected && styles.reasonOptionSelected]}>
                  <Text variant="body" weight="bold" color={selected ? color.text.onAction : color.text.heading}>{tx(...REPORT_REASON_LABEL[value])}</Text>
                </Pressable>
              );
            })}
          </View>

          {reason === 'OTHER' ? (
            <TextInput
              accessibilityLabel={tx('신고 상세 내용', 'Report detail')}
              style={styles.detailInput}
              multiline
              placeholder={tx('어떤 점이 문제인지 적어 주세요', 'Tell us what the issue is')}
              placeholderTextColor={color.text.muted}
              value={detail}
              onChangeText={(value) => setDetail(value.slice(0, DETAIL_MAX))}
              maxLength={DETAIL_MAX}
            />
          ) : null}

          {error ? <Text accessibilityRole="alert" color={color.state.danger}>{error}</Text> : null}

          <Pressable accessibilityRole="button" accessibilityState={{ disabled: !reason || submitting }} disabled={!reason || submitting} onPress={() => void submit()} style={[styles.submitButton, (!reason || submitting) && styles.submitButtonDisabled]}>
            {submitting ? <ActivityIndicator color={color.text.onAction} /> : <Text variant="body" weight="bold" color={color.text.onAction}>{tx('신고 접수', 'Submit report')}</Text>}
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(11,29,58,0.62)' },
  card: { width: '100%', maxWidth: 420, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  closeButton: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.card },
  pressed: { opacity: 0.72 },
  reasonList: { gap: spacing[2] },
  reasonOption: { minHeight: 48, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  reasonOptionSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  detailInput: { minHeight: 80, padding: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, backgroundColor: color.surface.card, color: color.text.heading, textAlignVertical: 'top' },
  submitButton: { minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.action.primary },
  submitButtonDisabled: { opacity: 0.5 },
});
