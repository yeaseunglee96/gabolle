// 18 방문 인증·만족도 — Figma 18_방문 인증·만족도 실측 그대로.
//
// 🔴 명세와 정면으로 충돌하는 화면이다. 명세 FR-COM-03 은 "방문 완료 버튼은 MVP 제외" 라고
// 못박는데, Jira 는 GPS 200m 반경 방문 인증을 완료 기준까지 갖춰 진행 중이다(이 화면 자체가
// 그 결과물이다). 어느 쪽을 따를지는 사람이 정할 일이라 판단하지 않고 Figma 대로 만든다.
// 실제 GPS 판정 로직은 없다 — "위치 기반 방문 인증 완료" 배지는 고정 목업이다.
import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Button } from '@/components/Button';

type FeedbackKey = 'sea' | 'alley' | 'photo' | 'local' | 'move' | 'accurate';

const FEEDBACK_OPTIONS: { key: FeedbackKey; label: string }[] = [
  { key: 'sea', label: '바다 전망' },
  { key: 'alley', label: '골목 분위기' },
  { key: 'photo', label: '사진 명소' },
  { key: 'local', label: '현지 느낌' },
  { key: 'move', label: '이동 편의' },
  { key: 'accurate', label: '정보 정확' },
];

type Accuracy = '정확해요' | '달라졌어요' | '잘 모르겠어요';
const ACCURACY_OPTIONS: Accuracy[] = ['정확해요', '달라졌어요', '잘 모르겠어요'];

const STAR_COUNT = 5;

export default function CheckIn() {
  const router = useRouter();
  const [rating, setRating] = useState(5);
  const [feedback, setFeedback] = useState<Set<FeedbackKey>>(() => new Set(['sea', 'alley', 'accurate']));
  const [accuracy, setAccuracy] = useState<Accuracy>('정확해요');
  const [note, setNote] = useState('');

  function toggleFeedback(key: FeedbackKey) {
    setFeedback((prev) => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  return (
    <Screen scroll>
      <Text variant="eyebrow" weight="bold">
        방문 후 · 8월 24일 16:42
      </Text>
      <Text variant="display" weight="bold" style={styles.title}>
        여행은 어떠셨나요?
      </Text>

      <View style={styles.placeCard}>
        {/* TODO: 실제 방문 장소 사진. 자산이 오기 전까지 색 면으로 대체한다. */}
        <View style={styles.placeImage} />
        <View style={styles.placeBody}>
          <Text variant="body" weight="bold">
            흰여울문화마을
          </Text>
          <Text variant="caption" weight="bold" color={color.state.success}>
            ✓ 위치 기반 방문 인증 완료
          </Text>
          <Text variant="caption" style={styles.placeTime}>
            체류 1시간 24분
          </Text>
        </View>
      </View>

      <Text variant="title" weight="bold" style={styles.sectionTitle}>
        전반적인 만족도
      </Text>
      <View style={styles.starsRow}>
        {Array.from({ length: STAR_COUNT }, (_, index) => index + 1).map((value) => (
          <Pressable key={value} onPress={() => setRating(value)}>
            <Text variant="hero" color={value <= rating ? color.state.rating : color.surface.field}>
              ★
            </Text>
          </Pressable>
        ))}
      </View>

      <Text variant="title" weight="bold" style={styles.sectionTitle}>
        어떤 점이 좋았나요?
      </Text>
      <View style={styles.feedbackGrid}>
        {FEEDBACK_OPTIONS.map((option) => {
          const selected = feedback.has(option.key);
          return (
            <Pressable
              key={option.key}
              onPress={() => toggleFeedback(option.key)}
              style={[styles.feedbackChip, selected && styles.feedbackChipSelected]}
            >
              <Text variant="caption" weight="bold" color={selected ? color.text.accent : color.text.body}>
                {selected ? `✓ ${option.label}` : option.label}
              </Text>
            </Pressable>
          );
        })}
      </View>

      <View style={styles.accuracyCard}>
        <Text variant="body" weight="bold">
          현장 정보가 실제와 같았나요?
        </Text>
        <Text variant="caption" style={styles.accuracyDesc}>
          운영시간 · 경사 · 혼잡도
        </Text>
        <View style={styles.accuracyRow}>
          {ACCURACY_OPTIONS.map((option) => {
            const selected = option === accuracy;
            return (
              <Pressable
                key={option}
                onPress={() => setAccuracy(option)}
                style={[styles.accuracyOption, selected && styles.accuracyOptionSelected]}
              >
                <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.body}>
                  {option}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </View>

      <View style={styles.noteBox}>
        <TextInput
          style={styles.noteInput}
          placeholder="다음 여행자에게 도움이 될 한마디"
          placeholderTextColor={color.text.muted}
          value={note}
          onChangeText={setNote}
          multiline
        />
        <Text variant="caption" weight="medium" color={color.state.success}>
          여행 종료 전까지 비공개
        </Text>
      </View>

      <Button
        label="후기 저장하기"
        variant="field"
        containerStyle={styles.cta}
        onPress={() => router.back()}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: {
    marginTop: spacing[1],
    marginBottom: spacing[4],
  },
  placeCard: {
    flexDirection: 'row',
    gap: spacing[3],
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    padding: spacing[3],
  },
  placeImage: {
    width: 80,
    height: 80,
    borderRadius: radius.md,
    backgroundColor: color.surface.soft,
  },
  placeBody: {
    flex: 1,
    justifyContent: 'center',
    gap: spacing[1],
  },
  placeTime: {
    color: color.text.body,
  },
  sectionTitle: {
    marginTop: spacing[6],
    marginBottom: spacing[3],
  },
  starsRow: {
    flexDirection: 'row',
    gap: spacing[2],
  },
  feedbackGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing[2],
  },
  feedbackChip: {
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
  },
  feedbackChipSelected: {
    backgroundColor: color.surface.tint,
  },
  accuracyCard: {
    marginTop: spacing[6],
    backgroundColor: color.surface.soft,
    borderRadius: radius.md,
    padding: spacing[4],
    gap: spacing[1],
  },
  accuracyDesc: {
    color: color.text.body,
  },
  accuracyRow: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[2],
  },
  accuracyOption: {
    flex: 1,
    alignItems: 'center',
    backgroundColor: color.surface.card,
    borderRadius: radius.sm,
    paddingVertical: spacing[2],
  },
  accuracyOptionSelected: {
    backgroundColor: color.action.brand,
  },
  noteBox: {
    marginTop: spacing[4],
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    padding: spacing[3],
    gap: spacing[1],
  },
  noteInput: {
    fontSize: 15,
    color: color.text.heading,
    minHeight: 24,
  },
  cta: {
    marginTop: spacing[6],
  },
});
