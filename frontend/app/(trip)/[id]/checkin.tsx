// 18 방문 인증·만족도 — Figma 18_방문 인증·만족도 실측 그대로.
//
// 🔴 명세와 정면으로 충돌하는 화면이다. 명세 FR-COM-03 은 "방문 완료 버튼은 MVP 제외" 라고
// 못박는데, Jira 는 GPS 200m 반경 방문 인증을 완료 기준까지 갖춰 진행 중이다(이 화면 자체가
// 그 결과물이다). 어느 쪽을 따를지는 사람이 정할 일이라 판단하지 않고 Figma 대로 만든다.
// 실제 GPS 판정 로직은 없다 — "위치 기반 방문 인증 완료" 배지는 고정 목업이다.
import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { sendAppEvent } from '@/analytics/appEvents';
import { useAuth } from '@/auth/AuthProvider';
import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { SampleNotice } from '@/components/SampleNotice';
import { Button } from '@/components/Button';
import { Toggle } from '@/components/Toggle';
import { useI18n } from '@/i18n';
import { useBehaviorConsent } from '@/personalization/behaviorConsent';

type FeedbackKey = 'sea' | 'alley' | 'photo' | 'local' | 'move' | 'accurate';

const FEEDBACK_OPTIONS: { key: FeedbackKey; labelKo: string; labelEn: string }[] = [
  { key: 'sea', labelKo: '바다 전망', labelEn: 'Sea view' },
  { key: 'alley', labelKo: '골목 분위기', labelEn: 'Alley atmosphere' },
  { key: 'photo', labelKo: '사진 명소', labelEn: 'Photo spot' },
  { key: 'local', labelKo: '현지 느낌', labelEn: 'Local feel' },
  { key: 'move', labelKo: '이동 편의', labelEn: 'Easy to get around' },
  { key: 'accurate', labelKo: '정보 정확', labelEn: 'Accurate info' },
];

type Accuracy = 'accurate' | 'changed' | 'unsure';
const ACCURACY_OPTIONS: { key: Accuracy; labelKo: string; labelEn: string }[] = [
  { key: 'accurate', labelKo: '정확해요', labelEn: 'Accurate' },
  { key: 'changed', labelKo: '달라졌어요', labelEn: 'Changed' },
  { key: 'unsure', labelKo: '잘 모르겠어요', labelEn: 'Not sure' },
];

const STAR_COUNT = 5;

export default function CheckIn() {
  const router = useRouter();
  const { tx } = useI18n();
  const { id: tripId } = useLocalSearchParams<{ id?: string }>();
  const { accessToken } = useAuth();
  const { enabled: reflectInRecommendations, setEnabled: setReflectInRecommendations } = useBehaviorConsent(accessToken);
  const [rating, setRating] = useState(5);
  const [feedback, setFeedback] = useState<Set<FeedbackKey>>(() => new Set(['sea', 'alley', 'accurate']));
  const [accuracy, setAccuracy] = useState<Accuracy>('accurate');
  const [note, setNote] = useState('');

  // 후기를 저장하면 방문 이벤트를 보낸다 — 동의가 꺼져 있으면 sendAppEvent 가 아무것도 안 보낸다.
  //
  // 🔴 화면은 서버를 기다리지 않는다. 후기 저장은 사용자에게 이미 끝난 일이고, 이벤트가
  //    못 갔다고 뒤로가기가 늦어지면 그건 사용자가 손해를 보는 것이다.
  // 🔴 한마디(자유 입력)는 담지 않는다. 담긴 것은 "썼는가" 뿐이다 — 자유 입력 원문은
  //    이벤트 표에 남기지 않기로 한 규칙이 있고, 서버 SensitivePayloadGuard 도 그것을 막는다.
  // 🔴 어느 장소인지는 아직 못 담는다. 이 화면의 장소는 고정 목업이라 place_id 가 없다.
  function saveReview() {
    sendAppEvent({
      type: 'place_visit',
      accessToken,
      tripId,
      payload: {
        rating,
        chips: [...feedback],
        accuracy,
        has_note: note.trim().length > 0,
      },
    });
    router.back();
  }

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
      <Eyebrow>
        {tx('방문 후 · 8월 24일 16:42', 'After your visit · Aug 24, 16:42')}
      </Eyebrow>
      <Text variant="display" weight="bold" style={styles.title}>
        {tx('여행은 어떠셨나요?', 'How was your trip?')}
      </Text>

      {/* S15P21E201-1009 — 장소·체류 시간·「위치 기반 방문 인증 완료」는 고정 목업이다.
          GPS 판정 로직이 아직 없다(파일 머리말 참고). 인증됐다고 읽히면 안 된다. */}
      <SampleNotice
        badge={tx('샘플', 'Sample')}
        description={tx('아래 장소와 「방문 인증 완료」 표시는 예시예요. 위치 확인은 아직 실제로 돌지 않아요.', 'The place and the "visit verified" mark below are placeholders — location checking is not live yet.')}
      />

      <View style={styles.placeCard}>
        {/* TODO: 실제 방문 장소 사진. 자산이 오기 전까지 색 면으로 대체한다. */}
        <View style={styles.placeImage} />
        <View style={styles.placeBody}>
          <Text variant="body" weight="bold">
            {tx('흰여울문화마을', 'Huinnyeoul Culture Village')}
          </Text>
          <Text variant="caption" weight="bold" color={color.state.success}>
            {tx('✓ 위치 기반 방문 인증 완료', '✓ Location-based visit verified')}
          </Text>
          <Text variant="caption" style={styles.placeTime}>
            {tx('체류 1시간 24분', 'Stayed 1h 24m')}
          </Text>
        </View>
      </View>

      <Text variant="title" weight="bold" style={styles.sectionTitle}>
        {tx('전반적인 만족도', 'Overall satisfaction')}
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
        {tx('어떤 점이 좋았나요?', 'What did you like?')}
      </Text>
      <View style={styles.feedbackGrid}>
        {FEEDBACK_OPTIONS.map((option) => {
          const selected = feedback.has(option.key);
          const label = tx(option.labelKo, option.labelEn);
          return (
            <Pressable
              key={option.key}
              onPress={() => toggleFeedback(option.key)}
              style={[styles.feedbackChip, selected && styles.feedbackChipSelected]}
            >
              <Text variant="caption" weight="bold" color={selected ? color.text.accent : color.text.body}>
                {selected ? `✓ ${label}` : label}
              </Text>
            </Pressable>
          );
        })}
      </View>

      <View style={styles.accuracyCard}>
        <Text variant="body" weight="bold">
          {tx('현장 정보가 실제와 같았나요?', 'Did the on-site info match reality?')}
        </Text>
        <Text variant="caption" style={styles.accuracyDesc}>
          {tx('운영시간 · 경사 · 혼잡도', 'Hours · Slope · Crowd level')}
        </Text>
        <View style={styles.accuracyRow}>
          {ACCURACY_OPTIONS.map((option) => {
            const selected = option.key === accuracy;
            return (
              <Pressable
                key={option.key}
                onPress={() => setAccuracy(option.key)}
                style={[styles.accuracyOption, selected && styles.accuracyOptionSelected]}
              >
                <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.body}>
                  {tx(option.labelKo, option.labelEn)}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </View>

      <View style={styles.noteBox}>
        <TextInput
          style={styles.noteInput}
          placeholder={tx('다음 여행자에게 도움이 될 한마디', 'A tip for the next traveler')}
          placeholderTextColor={color.text.muted}
          value={note}
          onChangeText={setNote}
          multiline
        />
        <Text variant="caption" weight="medium" color={color.state.success}>
          {tx('여행 종료 전까지 비공개', 'Private until the trip ends')}
        </Text>
      </View>

      {/* 켜는 자리를 여기 둔 이유 — 방문 직후가 "무엇을 켜는지" 를 사용자가 이해하는 유일한
          순간이다. 온보딩에서 물으면 무엇을 켜는지 모르고 켜고, 그 동의는 동의가 아니다.
          기본은 꺼짐이고, 마이페이지에서 언제든 다시 끌 수 있다. */}
      <View style={styles.consentCard}>
        <View style={styles.consentCopy}>
          <Text variant="body" weight="bold">
            {tx('이 후기를 다음 추천에 반영할까요?', 'Use this review for your next recommendations?')}
          </Text>
          <Text variant="caption" style={styles.consentDesc}>
            {tx(
              '저장·제외·일정 수정·체크인 후기를 보고 추천 순서를 바꿔요. 마이페이지에서 언제든 끌 수 있어요.',
              'We reorder recommendations using your saves, exclusions, itinerary edits, and check-in reviews. You can turn this off anytime in My page.',
            )}
          </Text>
        </View>
        <Toggle
          value={reflectInRecommendations}
          onValueChange={setReflectInRecommendations}
        />
      </View>

      <Button
        label={tx('후기 저장하기', 'Save review')}
        variant="field"
        containerStyle={styles.cta}
        onPress={saveReview}
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
  consentCard: {
    marginTop: spacing[6],
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
    backgroundColor: color.surface.soft,
    borderRadius: radius.md,
    padding: spacing[4],
  },
  consentCopy: {
    flex: 1,
    gap: spacing[1],
  },
  consentDesc: {
    color: color.text.body,
  },
  cta: {
    marginTop: spacing[4],
  },
});
