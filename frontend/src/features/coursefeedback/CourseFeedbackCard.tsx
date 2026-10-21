// 코스별 품질 피드백 — 「이 코스 어땠어요?」 카드와 이유 고르기 창 (S15P21E201-2020).
//
// 투자자 피드백 「경로 추천의 품질은? 각 코스마다 피드백할 수 있는 UI 필요」. 추천으로 만든 일정에만 그린다 —
// 직접 짠 일정(origin SELF)은 평가할 추천이 없다(부르는 쪽이 거른다).
// 🔴 여행 화면에는 이미 빨간 주 버튼이 있다. 카드의 두 버튼은 테두리 버튼이고, 빨간 「저장」은 창 안에만 하나 둔다.
// 장소 하나하나의 평가는 지난 여행의 별점 후기(PlaceReviewModal)가 맡는다 — 여기서 또 만들지 않는다.
import { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Modal, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import {
  COMMENT_MAX, REASONS_BY_RATING, loadMyCourseFeedback, saveCourseFeedback,
  type CourseFeedbackDto, type CourseFeedbackReason, type CourseRating,
} from './courseFeedbackApi';

type Tx = (ko: string, en: string) => string;

export function reasonLabel(tx: Tx, reason: CourseFeedbackReason): string {
  switch (reason) {
    case 'LONG_TRANSIT': return tx('이동이 너무 길어요', 'Too much travel');
    case 'NOT_MY_TASTE': return tx('취향과 달라요', 'Not my taste');
    case 'TOO_TIGHT': return tx('시간이 빠듯해요', 'Too tight');
    case 'PLACE_CLOSED': return tx('장소가 문을 닫았어요', 'A place was closed');
    case 'HOURS_MISMATCH': return tx('영업시간이 안 맞아요', 'Opening hours didn\'t fit');
    case 'GREAT_PLACES': return tx('좋은 장소가 많아요', 'Great places');
    case 'GOOD_FLOW': return tx('동선이 좋아요', 'Smooth route');
    case 'GOOD_PACE': return tx('여유가 알맞아요', 'Good pace');
  }
}

export function CourseFeedbackCard({ itineraryId, courseLabel, past = false, accessToken, tx }: {
  itineraryId: string;
  /** 코스 글자(A/B/C). 모르면 null. */
  courseLabel: string | null;
  /** 끝난 여행이면 묻는 말을 「다녀온 코스 어땠어요?」로. */
  past?: boolean;
  accessToken: string | null;
  tx: Tx;
}) {
  const [mine, setMine] = useState<CourseFeedbackDto | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [sheet, setSheet] = useState<CourseRating | null>(null);

  useEffect(() => {
    let alive = true;
    setLoaded(false);
    setMine(null);
    loadMyCourseFeedback(itineraryId, accessToken)
      .then((value) => { if (alive) { setMine(value); setLoaded(true); } })
      // 못 불러왔으면 카드를 안 그린다 — 「아직 안 남겼다」로 보이면 이미 남긴 사람이 또 누른다.
      .catch(() => { if (alive) setLoaded(false); });
    return () => { alive = false; };
  }, [itineraryId, accessToken]);

  if (!loaded) return null;

  const question = past ? tx('다녀온 코스는 어땠어요?', 'How was the course you took?') : tx('이 코스 어때요?', 'How is this course?');
  return (
    <View style={styles.card} testID="course-feedback-card">
      {mine ? (
        <View style={styles.row}>
          <View style={styles.copy}>
            <Text variant="caption" color={color.text.muted}>{question}</Text>
            <Text weight="bold">{mine.rating === 'UP' ? tx('👍 좋아요라고 남겼어요', '👍 You liked it') : tx('👎 아쉬워요라고 남겼어요', '👎 You found it lacking')}</Text>
            {mine.reasons.length > 0 ? <Text variant="caption" color={color.text.body} numberOfLines={2}>{mine.reasons.map((reason) => reasonLabel(tx, reason)).join(' · ')}</Text> : null}
          </View>
          <Pressable testID="course-feedback-edit" accessibilityRole="button" onPress={() => setSheet(mine.rating)} style={({ pressed }) => [styles.link, pressed && styles.dim]}>
            <Text variant="caption" weight="bold" color={color.brand.navy}>{tx('바꾸기', 'Change')}</Text>
          </Pressable>
        </View>
      ) : (
        <>
          <View style={styles.copy}>
            <Text weight="bold">{question}</Text>
            <Text variant="caption" color={color.text.muted}>{tx('남겨 주시면 다음 추천이 더 좋아져요.', 'Your rating helps us recommend better courses.')}</Text>
          </View>
          <View style={styles.buttons}>
            <Button testID="course-feedback-up" variant="outline" compact label={tx('👍 좋아요', '👍 Good')} onPress={() => setSheet('UP')} containerStyle={styles.half} />
            <Button testID="course-feedback-down" variant="outline" compact label={tx('👎 아쉬워요', '👎 Not great')} onPress={() => setSheet('DOWN')} containerStyle={styles.half} />
          </View>
        </>
      )}
      {sheet ? (
        <CourseFeedbackSheet
          initialRating={sheet}
          initial={mine}
          onClose={() => setSheet(null)}
          onSave={async (rating, reasons, comment) => {
            const saved = await saveCourseFeedback({ itineraryId, rating, reasons, comment, courseLabel, accessToken });
            setMine(saved);
            setSheet(null);
          }}
          tx={tx}
        />
      ) : null}
    </View>
  );
}

export function CourseFeedbackSheet({ initialRating, initial, onSave, onClose, tx }: {
  initialRating: CourseRating;
  initial: CourseFeedbackDto | null;
  onSave: (rating: CourseRating, reasons: CourseFeedbackReason[], comment: string) => Promise<void>;
  onClose: () => void;
  tx: Tx;
}) {
  const [rating, setRating] = useState<CourseRating>(initialRating);
  const [reasons, setReasons] = useState<CourseFeedbackReason[]>(initial?.rating === initialRating ? initial.reasons : []);
  const [comment, setComment] = useState(initial?.comment ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pickRating = (next: CourseRating) => {
    setRating(next);
    // 쪽을 바꾸면 그쪽에 없는 이유는 뺀다 — 「좋아요」에 「이동이 너무 길어요」가 남으면 집계가 흐려진다.
    setReasons((current) => current.filter((reason) => REASONS_BY_RATING[next].includes(reason)));
  };
  const toggle = (reason: CourseFeedbackReason) =>
    setReasons((current) => (current.includes(reason) ? current.filter((each) => each !== reason) : [...current, reason]));

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      await onSave(rating, reasons, comment);
    } catch {
      setError(tx('저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.', 'Could not save. Please try again in a moment.'));
      setBusy(false);
    }
  };

  return (
    <Modal visible transparent animationType="fade" onRequestClose={onClose}>
      {/* Modal 은 앱 화면 바깥이라 화면의 키보드 장치가 안 먹는다 — 창에도 같은 규칙(S15P21E201-1684). */}
      <KeyboardAvoidingView style={styles.backdrop} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View accessibilityViewIsModal style={styles.sheet} testID="course-feedback-sheet">
          <ScrollView contentContainerStyle={styles.sheetBody} keyboardShouldPersistTaps="handled">
            <Text variant="title" weight="bold">{tx('코스 평가', 'Rate this course')}</Text>
            <View style={styles.buttons}>
              {(['UP', 'DOWN'] as const).map((value) => {
                const selected = rating === value;
                return (
                  <Pressable
                    key={value}
                    testID={`course-feedback-rating-${value}`}
                    accessibilityRole="radio"
                    accessibilityState={{ selected }}
                    onPress={() => pickRating(value)}
                    style={({ pressed }) => [styles.choice, selected && styles.choiceSelected, pressed && styles.dim]}
                  >
                    <Text weight="bold" color={selected ? color.text.onAction : color.text.heading}>{value === 'UP' ? tx('👍 좋아요', '👍 Good') : tx('👎 아쉬워요', '👎 Not great')}</Text>
                  </Pressable>
                );
              })}
            </View>
            <Text variant="caption" color={color.text.muted}>{tx('이유를 골라 주세요 (선택)', 'Pick reasons (optional)')}</Text>
            <View style={styles.chips}>
              {REASONS_BY_RATING[rating].map((reason) => {
                const selected = reasons.includes(reason);
                return (
                  <Pressable
                    key={reason}
                    testID={`course-feedback-reason-${reason}`}
                    accessibilityRole="checkbox"
                    accessibilityState={{ checked: selected }}
                    onPress={() => toggle(reason)}
                    style={({ pressed }) => [styles.chip, selected && styles.chipSelected, pressed && styles.dim]}
                  >
                    <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.body}>{reasonLabel(tx, reason)}</Text>
                  </Pressable>
                );
              })}
            </View>
            <TextInput
              testID="course-feedback-comment"
              accessibilityLabel={tx('한마디 (선택)', 'A short note (optional)')}
              value={comment}
              onChangeText={(text) => setComment(text.slice(0, COMMENT_MAX))}
              maxLength={COMMENT_MAX}
              multiline
              placeholder={tx('한마디 남기기 (선택)', 'Leave a short note (optional)')}
              placeholderTextColor={color.text.muted}
              style={styles.input}
            />
            <Text variant="micro" color={color.text.muted} style={styles.counter}>{`${comment.length}/${COMMENT_MAX}`}</Text>
            {error ? <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{error}</Text> : null}
            <Button testID="course-feedback-save" label={tx('저장', 'Save')} disabled={busy} onPress={() => void save()} />
            <Pressable testID="course-feedback-close" accessibilityRole="button" onPress={onClose} style={({ pressed }) => [styles.close, pressed && styles.dim]}>
              <Text weight="bold" color={color.brand.navy}>{tx('닫기', 'Close')}</Text>
            </Pressable>
          </ScrollView>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  copy: { flex: 1, gap: spacing[1] },
  buttons: { flexDirection: 'row', gap: spacing[2] },
  half: { flex: 1 },
  link: { minHeight: 44, paddingHorizontal: spacing[2], justifyContent: 'center' },
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  sheet: { width: '100%', maxWidth: 420, maxHeight: '90%', borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  sheetBody: { gap: spacing[3], padding: spacing[4] },
  choice: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  choiceSelected: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 36, paddingHorizontal: spacing[3], justifyContent: 'center', borderRadius: radius.chip, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  chipSelected: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  input: { minHeight: 72, padding: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, color: color.text.heading, textAlignVertical: 'top' },
  counter: { alignSelf: 'flex-end' },
  close: { minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  dim: { opacity: 0.6 },
});
