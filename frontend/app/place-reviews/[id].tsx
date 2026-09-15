// 리뷰와 방문 인증 화면 (S15P21E201-291, 상세설계서 Part II P-24). 방문 인증(GPS)에 성공해야
// 리뷰 쓰기 입력창이 열린다 — 인증 없이 아무나 별점을 매기면 "다녀온 사람의 평가"라는 신뢰가
// 무너진다. 실패 사유(권한 거부·너무 멂·정확도 나쁨)를 구분해 보여주는 것이 이 화면의 절반이다
// (거부당한 이유에 따라 사용자가 할 일이 다르기 때문 — 티켓 "목적" 참고).
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Location from 'expo-location';

import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { getPlace, type Place as ApiPlace } from '@/discovery/places';
import { placeNameForLanguage } from '@/discovery/romanize';
import { useI18n } from '@/i18n';
import { loadPlaceReviews, submitPlaceReview, verifyPlaceVisit, type PlaceReviewDto, type ThreeStepScore, type VisitVerificationStatus } from '@/review/placeReviews';

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

// 🔴 VERIFIED 가 아닌 상태는 기본적으로 입력창을 닫아 둔다. 딱 하나 예외가 PERMISSION_DENIED
// 다 — 위치 권한이 아예 없는 기기에서도 리뷰 자체는 쓸 수 있어야 한다는 게 티켓의 명시적
// 요구다("리뷰는 쓸 수 있지만 점수에 반영되지 않는다"). TOO_FAR·LOW_ACCURACY 는 다시 시도하면
// 풀릴 수 있는 상태라 그 안내와 재시도 버튼만 보여주고 입력창은 열지 않는다.
type VerifyState = { kind: 'idle' } | { kind: 'checking' } | { kind: 'verified' } | { kind: 'permission-denied' } | { kind: 'error'; message: string } | { kind: Exclude<VisitVerificationStatus, 'VERIFIED'>; distanceM: number | null; message: string };

export default function PlaceReviews() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { accessToken } = useAuth();
  const { id } = useLocalSearchParams<{ id: string }>();

  const [place, setPlace] = useState<ApiPlace | null>(null);
  const [reviews, setReviews] = useState<PlaceReviewDto[] | null>(null);
  const [averageScore, setAverageScore] = useState<number | null>(null);
  const [listState, setListState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [verify, setVerify] = useState<VerifyState>({ kind: 'idle' });
  const [scores, setScores] = useState<Scores>(EMPTY_SCORES);
  const [body, setBody] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const loadReviews = useCallback(async () => {
    if (!id) return;
    setListState('loading');
    const result = await loadPlaceReviews(id, accessToken);
    if (result.state === 'success') {
      setReviews(result.reviews);
      setAverageScore(result.averageScore);
      setListState('ready');
    } else {
      setListState('error');
    }
  }, [id, accessToken]);

  useEffect(() => {
    if (!id) return;
    void getPlace(id).then(setPlace).catch(() => setPlace(null));
    void loadReviews();
  }, [id, loadReviews]);

  const requestVerification = async () => {
    if (!id) return;
    setVerify({ kind: 'checking' });
    const permission = await Location.requestForegroundPermissionsAsync();
    if (!permission.granted) {
      setVerify({ kind: 'permission-denied' });
      return;
    }
    const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
    const outcome = await verifyPlaceVisit({
      placeId: id,
      lat: position.coords.latitude,
      lng: position.coords.longitude,
      accuracyM: Math.round(position.coords.accuracy ?? 999),
      accessToken,
    });
    if (outcome.state !== 'success') {
      setVerify({ kind: 'error', message: outcome.message });
      return;
    }
    if (outcome.outcome.verified || outcome.outcome.status === 'VERIFIED') {
      setVerify({ kind: 'verified' });
    } else {
      setVerify({ kind: outcome.outcome.status, distanceM: outcome.outcome.distanceM, message: outcome.outcome.message });
    }
  };

  const canWrite = verify.kind === 'verified' || verify.kind === 'permission-denied';
  const hasAnyScore = Object.values(scores).some((value) => value !== null);

  const submit = async () => {
    if (!id || !hasAnyScore || submitting) return;
    setSubmitting(true);
    setSubmitError(null);
    const result = await submitPlaceReview({ placeId: id, ...scores, body: body.trim() || undefined, accessToken });
    setSubmitting(false);
    if (result.state === 'success') {
      setScores(EMPTY_SCORES);
      setBody('');
      setVerify({ kind: 'idle' });
      void loadReviews();
    } else {
      setSubmitError(tx('평가를 접수하지 못했어요. 다시 시도해 주세요.', 'Could not submit the review. Please try again.'));
    }
  };

  const title = place ? placeNameForLanguage(place.nameKo, place.nameEn, language) : '';

  return (
    <Screen scroll style={styles.screen}>
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹</Text>
        </Pressable>
        <BrandLogoLink href="/home" imageStyle={styles.logo} />
        <View style={styles.spacer} />
      </View>

      <Text variant="display" weight="bold">{tx('리뷰와 방문 인증', 'Reviews & visit verification')}</Text>
      {title ? <Text color={color.text.body} style={styles.subtitle}>{title}</Text> : null}

      <View style={styles.verifyCard}>
        {verify.kind === 'idle' ? (
          <>
            <Text variant="body" weight="bold">{tx('다녀오셨나요?', 'Have you visited?')}</Text>
            <Text variant="caption" color={color.text.muted}>{tx('현재 위치로 방문을 인증하면 신뢰도 높은 평가를 남길 수 있어요.', 'Verify your visit with your current location to leave a trusted review.')}</Text>
            <Button label={tx('방문 인증하기', 'Verify my visit')} onPress={() => void requestVerification()} containerStyle={styles.verifyButton} />
          </>
        ) : null}
        {verify.kind === 'checking' ? <View style={styles.verifyingRow}><ActivityIndicator color={color.brand.orange} /><Text color={color.text.body}>{tx('위치를 확인하고 있어요…', 'Checking your location…')}</Text></View> : null}
        {verify.kind === 'verified' ? <Text variant="body" weight="bold" color={color.state.success}>{tx('방문이 인증되었습니다.', 'Your visit has been verified.')}</Text> : null}
        {verify.kind === 'permission-denied' ? (
          <>
            <Text variant="body" weight="bold" color={color.state.danger}>{tx('위치 권한이 없어 인증할 수 없어요', "We can't verify without location access")}</Text>
            <Text variant="caption" color={color.text.body}>{tx('리뷰는 쓸 수 있지만 점수에는 반영되지 않아요.', "You can still write a review, but it won't count toward the score.")}</Text>
          </>
        ) : null}
        {verify.kind === 'TOO_FAR' ? (
          <>
            <Text variant="body" weight="bold" color={color.state.danger}>{tx('현재 위치가 장소에서 멀리 떨어져 있습니다', 'Your current location is far from this place')}</Text>
            {verify.distanceM != null ? <Text variant="caption" color={color.text.body}>{tx(`약 ${verify.distanceM}m 떨어져 있어요.`, `About ${verify.distanceM}m away.`)}</Text> : null}
            <Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void requestVerification()} containerStyle={styles.verifyButton} />
          </>
        ) : null}
        {verify.kind === 'LOW_ACCURACY' ? (
          <>
            <Text variant="body" weight="bold" color={color.state.danger}>{tx('위치 정확도가 낮아요', 'Location accuracy is too low')}</Text>
            <Text variant="caption" color={color.text.body}>{verify.message}</Text>
            <Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void requestVerification()} containerStyle={styles.verifyButton} />
          </>
        ) : null}
        {verify.kind === 'error' ? (
          <>
            <Text variant="body" weight="bold" color={color.state.danger}>{verify.message}</Text>
            <Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void requestVerification()} containerStyle={styles.verifyButton} />
          </>
        ) : null}
      </View>

      {canWrite ? (
        <View style={styles.composeCard}>
          <Text variant="title" weight="bold">{tx('평가 남기기', 'Leave a review')}</Text>
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
          {submitError ? <Text accessibilityRole="alert" color={color.state.danger}>{submitError}</Text> : null}
          <Pressable accessibilityRole="button" accessibilityState={{ disabled: !hasAnyScore || submitting }} disabled={!hasAnyScore || submitting} onPress={() => void submit()} style={[styles.submitButton, (!hasAnyScore || submitting) && styles.submitButtonDisabled]}>
            {submitting ? <ActivityIndicator color={color.text.onAction} /> : <Text variant="body" weight="bold" color={color.text.onAction}>{tx('평가 제출', 'Submit review')}</Text>}
          </Pressable>
        </View>
      ) : null}

      <Text variant="title" weight="bold" style={styles.listTitle}>{tx('리뷰', 'Reviews')}</Text>
      {averageScore != null ? <Text color={color.text.body} style={styles.average}>{tx(`인증된 평가 평균 ${averageScore.toFixed(1)}점`, `Average of verified reviews: ${averageScore.toFixed(1)}`)}</Text> : null}

      {listState === 'loading' ? <View style={styles.notice}><ActivityIndicator color={color.brand.orange} /></View> : null}
      {listState === 'error' ? <View style={styles.notice}><Text color={color.text.body}>{tx('리뷰를 불러오지 못했어요.', 'Could not load reviews.')}</Text><Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void loadReviews()} /></View> : null}
      {listState === 'ready' && reviews && reviews.length === 0 ? <View style={styles.notice}><Text color={color.text.body}>{tx('아직 리뷰가 없어요.', 'No reviews yet.')}</Text></View> : null}
      {listState === 'ready' && reviews && reviews.length > 0 ? (
        <View style={styles.reviewList}>
          {reviews.map((review) => (
            <View key={review.placeReviewId} style={styles.reviewRow}>
              <View style={styles.reviewHeader}>
                {review.verified ? (
                  <View style={styles.verifiedBadge}><Text variant="caption" weight="bold" color={color.state.success}>{tx('인증됨', 'Verified')}</Text></View>
                ) : (
                  <View style={styles.unverifiedBadge}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('미인증', 'Unverified')}</Text></View>
                )}
                {review.mine ? <Text variant="caption" color={color.text.muted}>{tx('내 리뷰', 'My review')}</Text> : null}
              </View>
              {review.body ? <Text color={color.text.body}>{review.body}</Text> : null}
            </View>
          ))}
        </View>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[3] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  logo: { width: 96, height: 28 },
  spacer: { width: 44 },
  subtitle: { marginTop: spacing[1] },
  verifyCard: { gap: spacing[2], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  verifyingRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  verifyButton: { alignSelf: 'flex-start', marginTop: spacing[1] },
  composeCard: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  categoryRow: { gap: spacing[2] },
  stepRow: { flexDirection: 'row', gap: spacing[2] },
  stepOption: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.soft, borderWidth: 1, borderColor: color.surface.field },
  stepOptionSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  bodyInput: { minHeight: 72, padding: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, backgroundColor: color.surface.soft, color: color.text.heading, textAlignVertical: 'top' },
  submitButton: { minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.orange },
  submitButtonDisabled: { opacity: 0.5 },
  listTitle: { marginTop: spacing[6] },
  average: { marginTop: spacing[1] },
  notice: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  reviewList: { gap: spacing[3], marginTop: spacing[4] },
  reviewRow: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  reviewHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  verifiedBadge: { alignSelf: 'flex-start', paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.state.successBg },
  unverifiedBadge: { alignSelf: 'flex-start', paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
});
