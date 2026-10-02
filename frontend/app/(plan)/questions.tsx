// 여행 만들기 — 네 단계 + 「이대로 만들까요?」 (UI 개선 캔버스 ⑤, S15P21E201-1865).
//
// 🔴 전에는 질문 일곱을 하나씩 물었다(-1425). 그런데 서버가 반드시 요구하는 날짜·출발지·숙소는 질문이
//    아니라 위 칩 줄과 홈 시작 바에 있어서, 「필수 질문 3개」를 다 답하고도 마지막 단추가 잠겼다.
//    이제 서버가 요구하는 것까지 전부 네 화면 안에서 묻는다 —
//      1 언제·누구와  2 어디서 출발·어떻게 다닐지  3 어디로·얼마나  4 어떤 여행(선택)  → 확인 표
//    각 화면의 몸은 src/plan/PlanSteps.tsx 에 있다. 여기는 자리(몇 단계인가)·제출·동의·조건 창만 맡는다.
//
// 🔴 판정은 PLAN_QUESTIONS(필수 질문의 「답했나」)와 서버 요구(날짜·출발지·숙소) 그대로다. 단계가 바뀌어도
//    무엇을 보내는지는 안 바뀐다 — 보내는 값이 달라지면 같은 조건에서 다른 일정이 나온다.
import { useEffect, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { updateMyConsents } from '@/auth/authApi';
import { Button } from '@/components/Button';
import { GuestPlanGate } from '@/plan/GuestPlanGate';
import { ConditionsPromptModal } from '@/plan/ConditionsPromptModal';
import { COVERAGE_FEATURE, coverageCountsOf, hasScarcePlaceData, useConditionCoverage } from '@/plan/conditionCoverage';
import { createRecommendationJobAdapter, type RecommendationJobSnapshot } from '@/plan/recommendationJob';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';
import { usePlan } from '@/plan/PlanProvider';
import { clearQuestionState, loadQuestionState, saveQuestionState } from '@/plan/questionState';
import { INITIAL_QUESTION_STATE, PLAN_QUESTIONS, dayWindowIssue, type QuestionKey, type QuestionState } from '@/plan/planQuestions';
import { PLAN_STEP_COUNT, PlanStepBody, REVIEW_STEP, StepBar, firstIncompleteStep, planStepTitle, stepBlocker, whenSummary, type StepReadiness } from '@/plan/PlanSteps';
import Svg, { Path } from 'react-native-svg';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { lodgingMissing as isLodgingMissing } from '@/plan/lodgingRequired';
import { PlanStartBar } from '@/home/PlanStartBar';
import { startBarEndDate, startBarFromDraft, type StartBarValue } from '@/home/startBarValue';
import { assistantPrefillPatch } from '@/plan/assistantPrefill';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';

type BarSection = 'origin' | 'lodging';

/**
 * 🔴 비회원이면 질문보다 먼저 「로그인이 필요하다」를 알린다 — S15P21E201-1818.
 *    예전엔 일곱 질문을 다 답한 뒤 마지막 버튼에서야 로그인으로 보냈다. 인증을 다 읽기 전(authReady 거짓)엔
 *    로그인한 사람에게 안내가 번쩍이지 않도록 질문 화면을 그대로 둔다(그 화면도 제 로딩 문구를 낸다).
 */
export default function PlanQuestionsRoute() {
  const { user, ready: authReady } = useAuth();
  if (authReady && !user) return <GuestPlanGate />;
  return <PlanConditions />;
}

const answeredOf = (key: QuestionKey) => PLAN_QUESTIONS.find((item) => item.key === key)!.answered;

function PlanConditions() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { kind, width } = useLayout();
  const wide = kind !== 'phone';
  // 세로로 든 태블릿·펼친 폴드 — 폰 판이지만 폭이 넉넉하다. 단추는 폰처럼 바닥에 두고, 기둥만 가운데로 묶는다(반응형 점검 2026-10-02).
  const roomy = !wide && width >= 600;
  const insets = useSafeAreaInsets();
  const { draft, ready, update, completeStep } = usePlan();
  const { user, accessToken } = useAuth();
  // 접근성 자료가 얼마나 덮였나 — 「있는데 적을 때」만 이동 보조 칸 아래에 적는다(S15P21E201-1855).
  // 못 물어봤으면(끝점 없는 서버·네트워크 실패) null 이라 아무 말도 안 나간다.
  const coverage = useConditionCoverage();
  const accessibilityCounts = hasScarcePlaceData(coverage, COVERAGE_FEATURE.accessibility)
    ? coverageCountsOf(coverage, COVERAGE_FEATURE.accessibility)
    : null;
  const [job, setJob] = useState<RecommendationJobSnapshot | null>(null);
  // 조건 창은 두 길로 열린다 — 만들기 직전 식단을 몰라서(submit: 닫히면 보낸다), 확인 표의 「바꾸기」(edit: 닫히면 그 자리).
  const [conditionsMode, setConditionsMode] = useState<'submit' | 'edit' | null>(null);
  const [state, setState] = useState<QuestionState>(INITIAL_QUESTION_STATE);
  // 🔴 자리를 기기에서 잇는다 — S15P21E201-1376. 출발지를 고르러 나갔다 오거나 로그인하고
  //    돌아와도 1단계로 안 돌아간다. 다 읽기 전에는 저장하지 않는다(빈 자리로 덮어쓴다).
  const [stateRestored, setStateRestored] = useState(false);
  useEffect(() => {
    let active = true;
    // 읽는 사이에 사람이 벌써 눌렀으면(빠른 손) 그 손을 이긴다 — 저장된 자리로 덮어쓰지 않는다.
    loadQuestionState().then((saved) => { if (!active) return; setState((prev) => (prev === INITIAL_QUESTION_STATE ? saved : prev)); setStateRestored(true); });
    return () => { active = false; };
  }, []);
  useEffect(() => { if (stateRestored) void saveQuestionState(state); }, [state, stateRestored]);
  const scrollRef = useRef<ScrollView>(null);

  const searchParams = useLocalSearchParams<{ days?: string; people?: string }>();
  const prefilled = useRef(false);
  useEffect(() => {
    if (!ready || prefilled.current) return;
    prefilled.current = true;
    const patch = assistantPrefillPatch(searchParams, draft);
    if (Object.keys(patch).length) update(patch);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready]);

  // 미확인 필수 조건(식단)이 남아 있나. 「모르면 안전하다고 치지 않는다」가 방침이다.
  //
  // 🔴 알레르기는 여기서 «빠져야» 한다 (-1513). 조건 창이 알레르기를 더는 안 묻는데(-1497)
  //    기본값이 'UNKNOWN' 이라, 남겨 두면 모두가 영영 「모름」이다 — 만들기를 누르면 이미 답한
  //    조건 창이 한 번 더 뜬다. 창의 저장 조건과 같게 맞춘다.
  const hardUnknown = draft.dietStatus === 'UNKNOWN'
    || (draft.dietStatus === 'VALUES' && !draft.dietTypes.length);

  // 🔴 날짜·출발지·(1박 이상이면) 숙소는 서버가 반드시 요구한다 — S15P21E201-1337·1342·1584.
  //    이제 이 셋도 1·2단계가 묻는다. 필수 질문(지역·예산·이동)은 PLAN_QUESTIONS 의 판정 그대로.
  const readiness: StepReadiness = {
    datesMissing: !draft.startDate || !draft.endDate,
    endMissing: Boolean(draft.startDate) && !draft.endDate,
    originMissing: draft.originLat === null || draft.originLng === null,
    lodgingMissing: isLodgingMissing(draft),
    transportMissing: !draft.transport,
    hoursInvalid: dayWindowIssue(draft) !== null,
    areasMissing: !answeredOf('areas')(draft),
    budgetMissing: !answeredOf('budget')(draft),
  };
  const missing = PLAN_QUESTIONS.filter((item) => !item.skippable && !item.answered(draft));
  // 서버가 실제로 만들 수 있는 조건 — 필수 질문 + 날짜 + 출발지 + (1박 이상이면) 숙소. 마지막 단추가 이걸 본다.
  const readyToBuild = missing.length === 0 && !readiness.datesMissing && !readiness.originMissing && !readiness.lodgingMissing;

  // 🔴 자리는 «단계»다(0~3, 4 = 확인 표). 옛 저장값(질문 번호 0~6)이 남아 있어도 여기서 잘린다.
  //    그리고 처음으로 덜 채운 필수 단계보다 뒤에는 서지 않는다 — 빈 필수 단계를 건너뛴 채 확인 표에 서면
  //    「왜 못 만들지」를 표에서 찾아야 한다. 고친 값이 앞 단계를 다시 비우면(날짜를 바꿔 숙소가 필요해지면) 그 단계로 돌아간다.
  const step = Math.min(state.open, REVIEW_STEP, firstIncompleteStep(readiness));
  const review = step === REVIEW_STEP;
  const styleSkipped = Boolean(state.skipped.cats || state.skipped.pace);

  // 🔴 확인 표의 칸을 눌러 단계로 갔으면, 고친 뒤 «바로» 확인 표로 돌아온다 — S15P21E201-1869.
  //    예전엔 3단계 예산을 고치고 4단계를 다시 지나야 표로 돌아왔다(「칸을 누르면 그 자리에서 고쳐요」와 딴판).
  //    저장하지 않는다 — 앱을 껐다 켜면 평소 단계 흐름으로 돌아가는 게 맞다.
  const [editFromReview, setEditFromReview] = useState(false);
  const goTo = (next: number) => {
    setEditFromReview(false);
    setState((prev) => ({ ...prev, open: Math.max(0, Math.min(REVIEW_STEP, next)), editing: null }));
    scrollRef.current?.scrollTo({ y: 0, animated: false });
  };
  const editFromReviewAt = (next: number) => { goTo(next); setEditFromReview(true); };
  const skipStyle = () => {
    setState((prev) => ({ ...prev, skipped: { ...prev.skipped, cats: true, pace: true } }));
    goTo(REVIEW_STEP);
  };

  // 🔴 출발지·숙소를 «이름으로 찾기»는 시작 바의 검색 시트를 이 화면 위에 연다(UI 캔버스 ⑤). 홈에 갔다 오면
  //    답하던 자리가 흐트러지고, 「일정 물어보기」를 한 번 더 눌러야 돌아왔다. 추천 출발지·동네는 2단계가 바로 고른다.
  const [barSheet, setBarSheet] = useState<BarSection | null>(null);
  const [barValue, setBarValue] = useState<StartBarValue | null>(null);
  const openBar = (section: BarSection) => { setBarValue(startBarFromDraft(draft)); setBarSheet(section); };
  const applyBar = (value: StartBarValue) => {
    update({
      origin: value.origin, originLat: value.originLat, originLng: value.originLng,
      lodging: value.lodging, lodgingLat: value.lodgingLat, lodgingLng: value.lodgingLng, lodgingPlace: value.lodgingPlace,
      originEnglish: value.originEnglish ?? null, lodgingEnglish: value.lodgingEnglish ?? null,
      startDate: value.startDate, endDate: startBarEndDate(value),
      adults: value.adults, children: value.children, travelers: value.adults + value.children,
    });
    setBarSheet(null);
  };
  const goGenerating = (jobId: string) => router.push({ pathname: '/plan/generating', params: { jobId } });

  /**
   * @param afterConditions 조건 창에서 막 돌아온 길인가. 🔴 참이면 조건을 <b>다시 묻지 않는다.</b>
   *     안 그러면 저장 → 창 열림 → 저장 → 창 열림이 되어 영영 못 나간다.
   */
  // 🔴 그리기 상태(job.state)만으로는 빠른 두 번 누름을 못 막는다 — 다시 그려지기 전에 두 번째가 들어와 작업이 둘 생긴다
  //    (S15P21E201-1823). 누르는 즉시 잠그고, 실패로 돌아오면 푼다(성공이면 화면을 떠난다).
  const submittingNow = useRef(false);
  const submitPlan = async (afterConditions = false) => {
    if (submittingNow.current) return;
    if (hardUnknown && !afterConditions) { setConditionsMode('submit'); return; }
    if (!user) { router.push({ pathname: '/sign-in', params: { returnTo: '/plan' } }); return; }
    submittingNow.current = true;
    try {
      setJob({ state: 'submitting', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: null, resultRef: null });
      const next = await createRecommendationJobAdapter(accessToken).submit(draft);
      setJob(next);
      if (next.jobId) { void clearQuestionState(); goGenerating(next.jobId); } else submittingNow.current = false;
    } catch (cause) {
      submittingNow.current = false;
      throw cause;
    }
  };

  const grantHealthConsentAndRetry = async () => {
    if (!accessToken) return;
    try {
      await updateMyConsents(accessToken, { HEALTH_CONSTRAINTS: true });
      setJob({ state: 'submitting', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: null, resultRef: null });
      const next = await createRecommendationJobAdapter(accessToken).submit(draft);
      setJob(next);
      if (next.jobId) goGenerating(next.jobId);
    } catch (cause) {
      setJob({ state: 'failed', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: cause instanceof ApiClientError ? cause.message : tx('동의 처리에 실패했어요. 잠시 후 다시 시도해 주세요.', 'Could not save your consent. Please try again shortly.'), resultRef: null });
    }
  };

  if (!ready) return <Screen scroll scrollRef={scrollRef}><Text>{tx('불러오는 중이에요…', 'Loading…')}</Text></Screen>;

  const { eyebrow, title } = planStepTitle(step, tx);
  const blocker = stepBlocker(step, readiness, tx);
  const submitting = job?.state === 'submitting';

  // 1단계 바닥 한 줄 — 무엇을 골랐는지(시안: 「출발 10월 9일 (금) · 성인 2」, 날짜만 굵게).
  const summary = step === 0 ? whenSummary(draft, tx) : null;

  const backToReview = editFromReview && !review;
  const primaryLabel = review
    ? submitting ? tx('만드는 중…', 'Building…') : blocker ?? tx('이 조건으로 일정 만들기', 'Build my itinerary')
    : blocker ?? (backToReview ? tx('조건 확인으로 돌아가기', 'Back to trip review') : step === PLAN_STEP_COUNT - 1 ? tx('다음 · 한눈에 보기', 'Next · review') : tx('다음', 'Next'));
  const onPrimary = () => {
    completeStep(step + 1);
    if (review) { if (readyToBuild) void submitPlan(); }
    else if (backToReview) goTo(REVIEW_STEP);
    else goTo(step + 1);
  };

  // 확인 표에서 왜 못 만드는지 · 동의 · 실패 — 잠근 이유를 반드시 말한다. 잠그기만 하면 고장인 줄 안다.
  const submitAlerts = review ? (
    <>
      {hardUnknown ? (
        // 🔴 「눌러서 알려주세요」라고 적어 두고 눌리지 않는 글자였다(UI 캔버스 ⑤).
        <Pressable accessibilityRole="button" onPress={() => setConditionsMode('edit')} style={({ pressed }) => [styles.dietAsk, pressed && styles.pressed]}>
          <Text variant="caption" weight="bold" color={color.state.danger}>{tx('식단을 아직 안 알려주셨어요 · 눌러서 알려주기 ›', 'We still need your diet answer · tap to add ›')}</Text>
        </Pressable>
      ) : null}
      {job?.state === 'consent-required' && job.requiredConsent === 'HEALTH_CONSTRAINTS' ? (
        <View style={styles.consent}>
          <Text accessibilityRole="alert" variant="caption" weight="bold">{tx('알레르기·식단 정보 사용에 동의가 필요해요', 'We need your consent to use allergy/diet info')}</Text>
          <Text variant="caption" color={color.text.body}>{tx('입력하신 조건으로 안전한 곳만 고르려면 이 정보를 써야 해요.', 'We need this information to pick places that are safe for you.')}</Text>
          <Button label={tx('동의하고 계속', 'Agree and continue')} variant="tertiary" onPress={() => void grantHealthConsentAndRetry()} />
        </View>
      ) : null}
      {job?.errorMessage && job.state !== 'consent-required' ? (
        <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{localizeMessage(tx, job.errorMessage)}</Text>
      ) : null}
    </>
  ) : null;

  // 🔴 「이전」은 1단계와 확인 표에는 없다(시안) — 1단계는 누를 곳이 없고, 확인 표는 칸을 눌러 그 단계로 간다. 나가는 길은 머리의 ‹ 다.
  const nav = (
    <View style={styles.navRow}>
      {step > 0 && !review ? (
        <Pressable accessibilityRole="button" onPress={() => goTo(step - 1)} style={({ pressed }) => [styles.prev, pressed && styles.pressed]}>
          <Text weight="bold">{tx('이전', 'Back')}</Text>
        </Pressable>
      ) : null}
      <View style={styles.next}>
        <Button
          accessibilityState={{ busy: submitting }}
          label={primaryLabel}
          disabled={Boolean(blocker) || (review && (submitting || !readyToBuild))}
          onPress={onPrimary}
        />
      </View>
    </View>
  );

  const bottom = (
    <View style={styles.bottomStack}>
      {summary ? (
        <Text variant="caption" color={color.text.body} style={styles.center}>
          {summary.lead ? `${summary.lead} ` : ''}<Text variant="caption" weight="bold" color={color.text.heading}>{summary.bold}</Text>{summary.rest ? ` · ${summary.rest}` : ''}
        </Text>
      ) : null}
      {submitAlerts}
      {nav}
    </View>
  );

  const head = (
    <View style={styles.head}>
      <View style={styles.topRow}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={step > 0 ? tx('이전 단계', 'Previous step') : tx('뒤로 가기', 'Go back')}
          onPress={() => (step > 0 ? goTo(step - 1) : router.canGoBack() ? router.back() : router.replace('/home'))}
          style={styles.back}
        >
          <Svg width={22} height={22} viewBox="0 0 24 24" fill="none" stroke={color.text.heading} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round"><Path d="M15 5l-7 7 7 7" /></Svg>
        </Pressable>
        <Text weight="bold" style={styles.topTitle}>{tx('여행 만들기', 'Plan a trip')}</Text>
        <Text variant="caption" weight="bold" color={color.text.muted} style={styles.topCount}>{review ? tx('확인', 'Review') : `${step + 1} / ${PLAN_STEP_COUNT}`}</Text>
      </View>
      <StepBar step={step} tx={tx} />
      {review ? (
        <View style={styles.reviewTitle}>
          <View style={styles.grow}>
            <Text variant="display" weight="bold">{title}</Text>
            <Text color={color.text.muted}>{tx('칸을 누르면 고치고 바로 돌아와요.', 'Tap a row to change it, then come right back.')}</Text>
          </View>
          {/* 동백이는 글 옆 제 칸에 — 좁은 폰에서 글을 누르지 않게 작게 두고, 글은 남은 폭을 다 쓴다. */}
          <GabolleMascot state="open" still style={styles.reviewMascot} />
        </View>
      ) : (
        <View style={styles.titleBlock}>
          <View style={styles.eyebrowRow}>
            <Text variant="caption" weight="bold" color={color.text.eyebrow}>{eyebrow}</Text>
            {step === PLAN_STEP_COUNT - 1 ? (
              <Pressable accessibilityRole="button" onPress={skipStyle} style={styles.skip}>
                <Text variant="caption" weight="bold" color={color.text.muted}>{tx('건너뛰기', 'Skip')}</Text>
              </Pressable>
            ) : null}
          </View>
          <Text variant="display" weight="bold">{title}</Text>
        </View>
      )}
    </View>
  );

  const body = (
    <PlanStepBody
      step={step}
      draft={draft}
      update={update}
      tx={tx}
      language={language}
      readiness={readiness}
      styleSkipped={styleSkipped && !draft.preferences.length}
      accessibilityCounts={accessibilityCounts}
      goTo={review ? editFromReviewAt : goTo}
      onSkipStyle={skipStyle}
      onSearchPlace={openBar}
      onEditConditions={() => setConditionsMode('edit')}
      onOpenMenuScan={() => router.push('/field/menu-scan')}
    />
  );

  return (
    <View style={styles.shell}>
      <Screen scroll wide={wide} style={styles.canvas} scrollRef={scrollRef}>
        <View style={[styles.column, (wide || roomy) && styles.columnWide]}>
          {head}
          {body}
          {/* 넓은 화면은 단추가 몸 바로 아래 — 화면 바닥에 붙이면 760 기둥과 떨어져 논다. */}
          {wide ? bottom : null}
        </View>
      </Screen>
      {/* 🔴 폰은 이전/다음을 바닥에 붙인다 — 달력·카드가 길어 단추가 스크롤 아래로 숨으면 「다음」을 찾아 내려가야 한다. */}
      {wide ? null : <View style={[styles.bottomBar, { paddingBottom: spacing[3] + insets.bottom }]}>{roomy ? <View style={styles.bottomRoomy}>{bottom}</View> : bottom}</View>}

      {/*
        🔴 -1334 — 닫힐 때 무엇을 골랐는지를 반드시 본다. ✕ 로 닫은 것(DISMISSED)만 그 자리에
        남고, 건너뛰든 저장하든 가려던 곳으로 간다. 확인 표의 「바꾸기」로 연 것은 닫히면 그 자리다.
      */}
      <ConditionsPromptModal
        visible={conditionsMode !== null}
        reprompt
        onClose={(outcome) => {
          const mode = conditionsMode;
          setConditionsMode(null);
          if (mode === 'submit' && outcome !== 'DISMISSED') void submitPlan(true);
        }}
      />
      {/* 넓은 화면은 시트를 가운데 기둥 폭으로 — 1280 을 꽉 채우면 칸 하나가 화면 끝에서 끝까지 늘어진다. */}
      {barSheet && barValue ? (
        <View style={[styles.sheetLayer, wide && styles.sheetLayerWide]}><View style={[styles.sheetFrame, wide && styles.sheetFrameWide]}>
        <PlanStartBar
          sheet
          wide={false}
          accessToken={accessToken}
          value={barValue}
          onChange={setBarValue}
          initialSection={barSheet}
          onClose={() => setBarSheet(null)}
          onSubmit={applyBar}
          submitLabel={tx('이대로 적용', 'Apply')}
        />
        </View></View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  canvas: { backgroundColor: color.canvas },
  shell: { flex: 1, backgroundColor: color.canvas },
  column: { gap: spacing[4], paddingBottom: spacing[6] },
  columnWide: { width: '100%', maxWidth: 640, alignSelf: 'center', marginTop: spacing[6] },
  head: { gap: spacing[3] },
  topRow: { minHeight: 44, flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  back: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', marginLeft: -spacing[3] },
  topTitle: { flex: 1, textAlign: 'center' },
  topCount: { minWidth: 44, textAlign: 'right' },
  titleBlock: { gap: 6, marginTop: spacing[2] },
  eyebrowRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', minHeight: 20 },
  reviewTitle: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginTop: spacing[1] },
  reviewMascot: { width: 56, height: 56 },
  titleRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3], marginTop: spacing[1] },
  grow: { flex: 1, minWidth: 0, gap: 2 },
  skip: { minHeight: 32, justifyContent: 'center', paddingLeft: spacing[2] },
  center: { textAlign: 'center' },
  // 시안: 바닥 단추는 캔버스 바탕 위 — 선도 흰 판도 없다.
  bottomBar: { paddingTop: spacing[3], paddingHorizontal: spacing[6], backgroundColor: color.canvas },
  bottomStack: { gap: spacing[2] },
  navRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  prev: { width: 88, minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, backgroundColor: color.surface.soft },
  next: { flex: 1 },
  pressed: { opacity: 0.8 },
  sheetLayer: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, zIndex: 25 },
  sheetLayerWide: { alignItems: 'center', backgroundColor: color.canvas },
  sheetFrame: { flex: 1, width: '100%' },
  sheetFrameWide: { maxWidth: 640 },
  bottomRoomy: { width: '100%', maxWidth: 640, alignSelf: 'center' },
  dietAsk: { minHeight: 44, justifyContent: 'center' },
  consent: { gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg },
});
