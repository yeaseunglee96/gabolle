// 리뷰와 방문 인증 화면 (상세설계서 Part II P-24). 방문 인증(GPS)에 성공해야
// 리뷰 쓰기 입력창이 열린다 — 인증 없이 아무나 별점을 매기면 "다녀온 사람의 평가"라는 신뢰가
// 무너진다. 실패 사유(권한 거부·너무 멂·정확도 나쁨)를 구분해 보여주는 것이 이 화면의 절반이다
// (거부당한 이유에 따라 사용자가 할 일이 다르기 때문 — 티켓 "목적" 참고).
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Location from 'expo-location';

import { useAuth } from '@/auth/AuthProvider';
import { useLocationGate } from '@/personalization/useLocationGate';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { getPlace, type Place as ApiPlace } from '@/discovery/places';
import { placeNameForLanguage } from '@/discovery/romanize';
import { useI18n } from '@/i18n';
import { loadPlaceReviews, submitPlaceReview, verifyPlaceVisit, type PlaceReviewDto, type ThreeStepScore, type VisitVerificationStatus } from '@/review/placeReviews';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';
import { otherNameFor } from '@/discovery/localNames';

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

type Tx = (ko: string, en: string) => string;
const SCORE_KEYS: Record<(typeof CATEGORIES)[number]['key'], 'foodScore' | 'priceScore' | 'accessibilityScore' | 'onsiteScore'> = {
  food: 'foodScore', price: 'priceScore', accessibility: 'accessibilityScore', onsite: 'onsiteScore',
};

/**
 * 리뷰 한 건에 매긴 항목을 「음식 · 좋아요」처럼 — 매긴 것만(S15P21E201-1973).
 * 전에는 목록이 본문만 그려서, 점수만 남긴 리뷰가 「미인증」 배지만 있는 빈 칸으로 보였다.
 * 서버 점수는 1~5 다(쓸 때 별로예요=1·보통이에요=3·좋아요=5) — 2 이하는 별로, 4 이상은 좋아요, 그 사이는 보통.
 */
export function reviewScoreLabels(review: Pick<PlaceReviewDto, 'foodScore' | 'priceScore' | 'accessibilityScore' | 'onsiteScore'>, tx: Tx): string[] {
  return CATEGORIES.flatMap((category) => {
    const score = review[SCORE_KEYS[category.key]];
    if (score == null) return [];
    const step = STEPS[score <= 2 ? 0 : score >= 4 ? 2 : 1];
    return [`${tx(category.labelKo, category.labelEn)} · ${tx(step.labelKo, step.labelEn)}`];
  });
}
type Scores = Record<'food' | 'price' | 'accessibility' | 'onsite', ThreeStepScore>;
const EMPTY_SCORES: Scores = { food: null, price: null, accessibility: null, onsite: null };

// VERIFIED 가 아닌 상태는 기본적으로 입력창을 닫아 둔다. 딱 하나 예외가 PERMISSION_DENIED
// 다 — 위치 권한이 아예 없는 기기에서도 리뷰 자체는 쓸 수 있어야 한다는 게 티켓의 명시적
// 요구다("리뷰는 쓸 수 있지만 점수에 반영되지 않는다"). TOO_FAR·LOW_ACCURACY 는 다시 시도하면
// 풀릴 수 있는 상태라 그 안내와 재시도 버튼만 보여주고 입력창은 열지 않는다.
type VerifyState = { kind: 'idle' } | { kind: 'checking' } | { kind: 'verified' } | { kind: 'permission-denied' } | { kind: 'error'; message: string } | { kind: Exclude<VisitVerificationStatus, 'VERIFIED'>; distanceM: number | null; message: string };

export default function PlaceReviews() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { accessToken } = useAuth();
  const locationGate = useLocationGate(accessToken);
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
    // 🔴 로그인 확인이 «위치보다 먼저»다 (S15P21E201-1795).
    //
    // 전에는 비회원에게도 위치 권한을 묻고 GPS 좌표까지 읽은 «다음»에 서버가 거절해서
    // 빨간 오류만 났다. 끝내 못 할 일 때문에 위치를 받는 것은 그 자체로 문제다.
    //
    // 게다가 위치에서 막히면 kind 가 'permission-denied' 가 되는데, canWrite 가 그것을
    // 「써도 되는 상태」로 쳐서 비회원에게 후기 작성 칸이 열렸다.
    if (!accessToken) { router.push({ pathname: '/sign-in', params: { returnTo: `/place-reviews/${id}` } }); return; }
    // 🔴 위치 동의가 먼저다(S15P21E201-1691). 서버도 방문 인증에 이 동의(PRECISE_LOCATION)를 요구한다(ConsentGuard) —
    //    동의 기록이 없던 때에는 인증이 서버에서 막혔다. 거절하면 기기 권한이 없을 때와 같은 길로 간다.
    if (!(await locationGate.request())) { setVerify({ kind: 'permission-denied' }); return; }
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
    // 비회원이 여기까지 왔다면(위 canWrite 경로) 쓴 글을 잃기 전에 로그인으로 보낸다.
    // 돌아오면 이 화면이 다시 열린다 (S15P21E201-1795).
    if (!accessToken) { router.push({ pathname: '/sign-in', params: { returnTo: `/place-reviews/${id}` } }); return; }
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

  const title = place ? placeNameForLanguage(place.nameKo, otherNameFor(place.nameEn, place.localNames, language), language) : '';

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
            <Button compact variant="outline" label={tx('방문 인증하기', 'Verify my visit')} onPress={() => void requestVerification()} containerStyle={styles.verifyButton} />
          </>
        ) : null}
        {verify.kind === 'checking' ? <View style={styles.verifyingRow}><ActivityIndicator color={color.action.primary} /><Text color={color.text.body}>{tx('위치를 확인하고 있어요…', 'Checking your location…')}</Text></View> : null}
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
            <Button compact label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void requestVerification()} containerStyle={styles.verifyButton} />
          </>
        ) : null}
        {verify.kind === 'LOW_ACCURACY' ? (
          <>
            <Text variant="body" weight="bold" color={color.state.danger}>{tx('위치 정확도가 낮아요', 'Location accuracy is too low')}</Text>
            <Text variant="caption" color={color.text.body}>{localizeMessage(tx, verify.message)}</Text>
            <Button compact label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void requestVerification()} containerStyle={styles.verifyButton} />
          </>
        ) : null}
        {verify.kind === 'error' ? (
          <>
            <Text variant="body" weight="bold" color={color.state.danger}>{localizeMessage(tx, verify.message)}</Text>
            <Button compact label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void requestVerification()} containerStyle={styles.verifyButton} />
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
      {averageScore != null ? <Text color={color.text.body} style={styles.average}>{txf(tx, '인증된 평가 평균 %s점', 'Average of verified reviews: %s', averageScore.toFixed(1))}</Text> : null}

      {listState === 'loading' ? <View style={styles.notice}><ActivityIndicator color={color.action.primary} /></View> : null}
      {/* 손님에게는 리뷰 조회도 401 이다(2026-09-21 실측, S15P21E201-1372) — 오류가 아니라 잠긴 문으로 보여 준다. */}
      {listState === 'error' && !accessToken ? <View style={styles.notice}><Text color={color.text.body}>{tx('로그인하면 리뷰를 볼 수 있어요.', 'Sign in to read reviews.')}</Text><Button label={tx('로그인', 'Sign in')} variant="tertiary" onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: `/place-reviews/${id}` } })} containerStyle={styles.noticeButton} /></View> : null}
      {listState === 'error' && accessToken ? <View style={styles.notice}><Text color={color.text.body}>{tx('리뷰를 불러오지 못했어요.', 'Could not load reviews.')}</Text><Button compact label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void loadReviews()} /></View> : null}
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
              {reviewScoreLabels(review, tx).length ? (
                <View style={styles.scoreRow}>
                  {reviewScoreLabels(review, tx).map((label) => (
                    <View key={label} style={styles.scoreChip}><Text variant="caption" weight="medium" color={color.text.body}>{label}</Text></View>
                  ))}
                </View>
              ) : null}
              {review.body ? <Text color={color.text.body}>{review.body}</Text> : null}
            </View>
          ))}
        </View>
      ) : null}
      {locationGate.sheet}
    </Screen>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.canvas },
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[3] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  logo: { width: 154, height: 28 },
  spacer: { width: 44 },
  subtitle: { marginTop: spacing[1] },
  verifyCard: { gap: spacing[2], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  verifyingRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  verifyButton: { alignSelf: 'flex-start', marginTop: spacing[1] },
  composeCard: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  categoryRow: { gap: spacing[2] },
  scoreRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  scoreChip: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
  stepRow: { flexDirection: 'row', gap: spacing[2] },
  stepOption: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.soft, borderWidth: 1, borderColor: color.surface.field },
  stepOptionSelected: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  bodyInput: { minHeight: 72, padding: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, backgroundColor: color.surface.soft, color: color.text.heading, textAlignVertical: 'top' },
  submitButton: { minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.action.primary },
  submitButtonDisabled: { opacity: 0.5 },
  listTitle: { marginTop: spacing[6] },
  average: { marginTop: spacing[1] },
  noticeButton: { alignSelf: 'stretch' },
  notice: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  reviewList: { gap: spacing[3], marginTop: spacing[4] },
  reviewRow: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  reviewHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  verifiedBadge: { alignSelf: 'flex-start', paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.state.successBg },
  unverifiedBadge: { alignSelf: 'flex-start', paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
});
