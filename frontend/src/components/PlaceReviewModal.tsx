// 다녀온 장소 평가 모달 —. 음식·가격·접근성·현장 이용 편의 네 항목을
// 각각 3단계(별로·보통·좋아요)로 받는다. 길게 물으면 아무도 안 쓴다는 게 완료 기준의
// 전제라 텍스트 후기는 선택으로 둔다.
import { useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import type { ThreeStepScore } from '@/review/placeReviews';
import { Text } from './Text';

const CATEGORIES: Array<{ key: 'food' | 'price' | 'accessibility' | 'onsite'; labelKo: string; labelEn: string }> = [
  { key: 'food', labelKo: '음식', labelEn: 'Food' },
  { key: 'price', labelKo: '가격', labelEn: 'Price' },
  { key: 'accessibility', labelKo: '접근성', labelEn: 'Accessibility' },
  { key: 'onsite', labelKo: '현장 이용 편의', labelEn: 'On-site convenience' },
];
const STEPS: Array<{ value: NonNullable<ThreeStepScore>; labelKo: string; labelEn: string }> = [
  { value: 'LOW', labelKo: '별로예요', labelEn: 'Not great' },
  { value: 'MID', labelKo: '보통이에요', labelEn: 'Okay' },
  { value: 'HIGH', labelKo: '좋아요', labelEn: 'Good' },
];
const BODY_MAX = 300;

type Scores = Record<'food' | 'price' | 'accessibility' | 'onsite', ThreeStepScore>;
const EMPTY_SCORES: Scores = { food: null, price: null, accessibility: null, onsite: null };

type PlaceReviewModalProps = {
  visible: boolean;
  placeTitle: string;
  onClose: () => void;
  onSubmit: (scores: Scores, body: string) => Promise<boolean>;
};

export function PlaceReviewModal({ visible, placeTitle, onClose, onSubmit }: PlaceReviewModalProps) {
  const { tx } = useI18n();
  const [scores, setScores] = useState<Scores>(EMPTY_SCORES);
  const [body, setBody] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const close = () => {
    setScores(EMPTY_SCORES);
    setBody('');
    setError(null);
    onClose();
  };

  const hasAnyScore = Object.values(scores).some((value) => value !== null);

  const submit = async () => {
    if (!hasAnyScore || submitting) return;
    setSubmitting(true);
    setError(null);
    const ok = await onSubmit(scores, body);
    setSubmitting(false);
    if (ok) close();
    else setError(tx('평가를 접수하지 못했어요. 다시 시도해 주세요.', 'Could not submit the review. Please try again.'));
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={close}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <View style={styles.header}>
            <View style={styles.grow}><Text variant="title" weight="bold">{tx('다녀오셨나요?', 'Did you visit?')}</Text><Text variant="caption" color={color.text.muted}>{placeTitle}</Text></View>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={close} style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}>
              <Text variant="title" weight="bold">✕</Text>
            </Pressable>
          </View>

          <ScrollView style={styles.body} contentContainerStyle={styles.bodyContent}>
            {CATEGORIES.map((category) => (
              <View key={category.key} style={styles.categoryRow}>
                <Text variant="body" weight="bold">{tx(category.labelKo, category.labelEn)}</Text>
                <View accessibilityRole="radiogroup" accessibilityLabel={tx(category.labelKo, category.labelEn)} style={styles.stepRow}>
                  {STEPS.map((step) => {
                    const selected = scores[category.key] === step.value;
                    return (
                      <Pressable key={step.value} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => setScores((current) => ({ ...current, [category.key]: selected ? null : step.value }))} style={[styles.stepOption, selected && styles.stepOptionSelected]}>
                        <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.heading}>{tx(step.labelKo, step.labelEn)}</Text>
                      </Pressable>
                    );
                  })}
                </View>
              </View>
            ))}

            <TextInput
              accessibilityLabel={tx('한줄 후기 (선택)', 'One-line note (optional)')}
              style={styles.bodyInput}
              multiline
              placeholder={tx('한줄 후기 (선택)', 'One-line note (optional)')}
              placeholderTextColor={color.text.muted}
              value={body}
              onChangeText={(value) => setBody(value.slice(0, BODY_MAX))}
              maxLength={BODY_MAX}
            />
          </ScrollView>

          {error ? <Text accessibilityRole="alert" color={color.state.danger}>{error}</Text> : null}

          <Pressable accessibilityRole="button" accessibilityState={{ disabled: !hasAnyScore || submitting }} disabled={!hasAnyScore || submitting} onPress={() => void submit()} style={[styles.submitButton, (!hasAnyScore || submitting) && styles.submitButtonDisabled]}>
            {submitting ? <ActivityIndicator color={color.text.onAction} /> : <Text variant="body" weight="bold" color={color.text.onAction}>{tx('평가 제출', 'Submit review')}</Text>}
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(11,29,58,0.62)' },
  card: { width: '100%', maxWidth: 420, maxHeight: '86%', gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  header: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: spacing[2] },
  grow: { flex: 1, gap: spacing[1] },
  closeButton: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.card },
  pressed: { opacity: 0.72 },
  body: { flexGrow: 0 },
  bodyContent: { gap: spacing[3], paddingBottom: spacing[2] },
  categoryRow: { gap: spacing[2] },
  stepRow: { flexDirection: 'row', gap: spacing[2] },
  stepOption: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  stepOptionSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  bodyInput: { minHeight: 72, padding: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, backgroundColor: color.surface.card, color: color.text.heading, textAlignVertical: 'top' },
  submitButton: { minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.action.primary },
  submitButtonDisabled: { opacity: 0.5 },
});
