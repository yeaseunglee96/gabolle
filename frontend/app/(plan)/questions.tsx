// 여행 조건 — **한 번에 하나씩** 묻는다 (시안 ①, 인계 §1 · S15P21E201-1425).
//
// 🔴 필수 3 + 선택 4 = 일곱 질문. 로컬성·조용함·음식은 온보딩 취향이 draft 로 이식되므로
//    (PlanProvider prefill) 여기서 다시 묻지 않는다. 예전 「좋아하는 분위기·척도·음식 취향」
//    세 질문과 3-장 묶음(-1377)은 걷어냈다.
//
// 🔴 넓은 화면은 두 기둥이다. 왼쪽 280 레일 = 「이번 여행」 카드 + 일곱 줄 체크리스트,
//    오른쪽 760 = 눈썹·진행 막대·질문 카드 하나·이전/다음. 레일이 「어디까지 왔나」를
//    대신하므로 오른쪽엔 답한 목록·다음 질문·상태 문구를 안 그린다.
//    폰은 기존 구조(위 칩 줄 · StepDots · 상태 문구 · 답한 행 · 질문 카드 · 다음 질문)를 유지한다.
import { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { updateMyConsents } from '@/auth/authApi';
import { Button } from '@/components/Button';
import { ConditionsPromptModal } from '@/plan/ConditionsPromptModal';
import { createRecommendationJobAdapter, type RecommendationJobSnapshot } from '@/plan/recommendationJob';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { resolveTextLanguage } from '@/i18n/languages';
import { useLayout } from '@/layout/useLayout';
import { usePlan, type PlanDraft } from '@/plan/PlanProvider';
import { MustVisitSearch } from '@/plan/MustVisitSearch';
import { EffectBand, OptionCard, StepDots } from '@/plan/PlanStepperParts';
import { DateRangeCard, dateRangeLabel } from '@/plan/DateRangeCard';
import { clearQuestionState, loadQuestionState, saveQuestionState } from '@/plan/questionState';
import {
  AREA_OPTIONS, CATEGORY_IMAGES, CATEGORY_OPTIONS, PACE_OPTIONS, TRANSPORT_OPTIONS,
  effectOf, type PlanOption,
} from '@/plan/planOptions';
import {
  INITIAL_QUESTION_STATE, PLAN_QUESTIONS, isSettled,
  type PlanQuestion, type QuestionKey, type QuestionState,
  dayWindowIssue,
} from '@/plan/planQuestions';
import { maskTimeInput } from '@/plan/inputMasks';
import { startBarChips } from '@/home/startBarValue';
import { assistantPrefillPatch } from '@/plan/assistantPrefill';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';

const BUDGET_STEPS = [10000, 30000, 50000, 100000] as const;

type Tx = (ko: string, en: string) => string;
function labelOf(option: PlanOption, tx: Tx) { return tx(option[1], option[2]); }
function subOf(option: PlanOption, tx: Tx) { return tx(option[3], option[4]); }

/** 답한 내용을 한 줄로. 안 고른 칸은 적지 않는다 — 「미정」이 답처럼 보인다. */
export function summaryOf(key: QuestionKey, draft: PlanDraft, tx: Tx, skipped: boolean, koNames = true): string {
  if (skipped) return tx('건너뜀', 'Skipped');
  const labels = (list: readonly PlanOption[], picked: string[]) =>
    list.filter(([code]) => picked.includes(code)).map((option) => labelOf(option, tx)).join(' · ');
  switch (key) {
    case 'areas': return labels(AREA_OPTIONS, draft.travelAreas);
    case 'budget': return draft.budgetKrw ? tx(`${(draft.budgetKrw / 10000).toLocaleString()}만원`, `₩${draft.budgetKrw.toLocaleString()}`) : '';
    case 'move': {
      const transport = TRANSPORT_OPTIONS.find(([code]) => code === draft.transport);
      const hours = draft.dayStartTime && draft.dayEndTime ? `${draft.dayStartTime}–${draft.dayEndTime}` : '';
      return [hours, transport ? labelOf(transport, tx) : ''].filter(Boolean).join(' · ');
    }
    case 'cats': return labels(CATEGORY_OPTIONS, draft.preferences);
    case 'pace': {
      const pace = PACE_OPTIONS.find(([code]) => code === draft.paceLevel);
      return pace ? labelOf(pace, tx) : '';
    }
    case 'aids': {
      const parts: string[] = [];
      if (draft.wheelchair) parts.push(tx('휠체어', 'Wheelchair'));
      if (draft.stroller) parts.push(tx('유아차', 'Stroller'));
      if (draft.luggage) parts.push(tx('큰 짐', 'Large luggage'));
      return parts.length ? parts.join(' · ') : tx('해당 없음', 'None');
    }
    case 'must': return draft.mustVisitPlaces.length
      ? draft.mustVisitPlaces.map((place) => (koNames ? place.nameKo : place.nameEn ?? place.nameKo)).join(' · ')
      : tx('없음', 'None');
    default: return '';
  }
}

export default function PlanConditions() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { kind } = useLayout();
  const wide = kind !== 'phone';
  const { draft, ready, update, completeStep } = usePlan();
  const { user, accessToken } = useAuth();
  const [job, setJob] = useState<RecommendationJobSnapshot | null>(null);
  const [conditionsOpen, setConditionsOpen] = useState(false);
  // 🔴 「영어가 아니면 한국어」로 가르면 일본어·중국어 사용자가 한국어를 본다 — S15P21E201-1296.
  const ko = resolveTextLanguage(language) === 'ko';
  const [state, setState] = useState<QuestionState>(INITIAL_QUESTION_STATE);
  // 🔴 자리를 기기에서 잇는다 — S15P21E201-1376. 날짜를 정하러 나갔다 오거나 로그인하고
  //    돌아와도 1번으로 안 돌아간다. 다 읽기 전에는 저장하지 않는다(빈 자리로 덮어쓴다).
  const [stateRestored, setStateRestored] = useState(false);
  useEffect(() => {
    let active = true;
    // 읽는 사이에 사람이 벌써 눌렀으면(빠른 손) 그 손을 이긴다 — 저장된 자리로 덮어쓰지 않는다.
    loadQuestionState().then((saved) => { if (!active) return; setState((prev) => (prev === INITIAL_QUESTION_STATE ? saved : prev)); setStateRestored(true); });
    return () => { active = false; };
  }, []);
  useEffect(() => { if (stateRestored) void saveQuestionState(state); }, [state, stateRestored]);
  // 날짜 카드 — 날짜가 없으면 펼쳐진 채로 시작하고, 고르면 접힌다. 머리의 「수정」이 다시 편다.
  const [datesOpen, setDatesOpen] = useState<boolean | null>(null);

  const searchParams = useLocalSearchParams<{ days?: string; people?: string }>();
  const prefilled = useRef(false);
  useEffect(() => {
    if (!ready || prefilled.current) return;
    prefilled.current = true;
    const patch = assistantPrefillPatch(searchParams, draft);
    if (Object.keys(patch).length) update(patch);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready]);

  // 🔴 자리는 «질문»이다 — 시안이 3-장 묶음을 버리고 질문 하나씩 보이는 스테퍼로 돌아갔다(-1425).
  //    예전 저장값(장 번호 0~2, 옛 질문 번호 0~9)이 남아 있어도 질문 수 안으로 잘린다.
  const index = Math.min(state.open, PLAN_QUESTIONS.length - 1);
  const q = PLAN_QUESTIONS[index];
  const last = index === PLAN_QUESTIONS.length - 1;
  // 앞의 몇 개가 필수인가 — 눈썹·진행 막대·상태 문구가 「셋 + 나머지」로 읽히게 한다.
  const requiredCount = PLAN_QUESTIONS.filter((item) => !item.skippable).length;

  const settledAt = (i: number) => isSettled(PLAN_QUESTIONS[i], draft, state);
  // 🔴 지금보다 앞에서 실제로 지나온 것만 센다 — 아직 안 본 질문의 기본값을 세면 「남은 0개」 거짓말이 된다.
  const settledSoFar = PLAN_QUESTIONS.filter((_, i) => i < index && settledAt(i)).length;

  // 🔴 못 건너뛰는 질문 가운데 아직 안 답한 것. 마지막 단추를 잠그는 근거이자 무엇이 모자란지 말해 주는 근거다.
  const missing = PLAN_QUESTIONS.filter((item) => !item.skippable && !item.answered(draft));
  const requiredReady = missing.length === 0;

  // 미확인 필수 조건(식단)이 남아 있나. 「모르면 안전하다고 치지 않는다」가 방침이다.
  //
  // 🔴 알레르기는 여기서 «빠져야» 한다 (-1513). 조건 창이 알레르기를 더는 안 묻는데(-1497)
  //    기본값이 'UNKNOWN' 이라, 남겨 두면 모두가 영영 「모름」이다 — 마지막 질문에 경고가 늘
  //    뜨고, 만들기를 누르면 이미 답한 조건 창이 한 번 더 뜬다. 창의 저장 조건과 같게 맞춘다.
  const hardUnknown = draft.dietStatus === 'UNKNOWN'
    || (draft.dietStatus === 'VALUES' && !draft.dietTypes.length);

  /**
   * 🔴 -1337 — 날짜는 여기서 묻지 않는데 <b>서버는 반드시 요구한다.</b> 위쪽 「여행 만들기」로
   * 들어오면 날짜가 비어 있다. 옆 레일(넓은 화면)·위 칩 줄(폰)이 「날짜를 아직 안 정했어요」라고
   * 적고 있으니, 알면서 보내지 않는다.
   */
  const datesMissing = !draft.startDate || !draft.endDate;
  // 🔴 출발지가 없으면 서버가 일정을 안 만들어 준다 — S15P21E201-1342. 이 앱에서 출발지를 채우는
  //    곳은 홈 시작 바 하나뿐이다(PlanStartBar).
  const originMissing = draft.originLat === null || draft.originLng === null;
  // 서버가 실제로 만들 수 있는 조건 — 필수 질문 + 날짜 + 출발지. 마지막 단추가 이걸 본다.
  const readyToBuild = missing.length === 0 && !datesMissing && !originMissing;

  // 🔴 예전에는 홈의 시작 줄로 보냈다(S15P21E201-1350). 돌아오면 문항이 1번부터라 열 개를 다 답한
  //    사람이 처음부터 다시 했다. 이제 이 화면 안의 달력 카드를 편다.
  const goSetDates = () => setDatesOpen(true);
  // 🔴 출발지 고르기는 검색이 붙어 있어(PlanStartBar) 여기 한 벌 더 만들지 않는다 — 시작 바를
  //    출발지 칸이 열린 채로 연다. 「일정 물어보기」를 누르면 홈이 다시 /plan 으로 돌려보낸다.
  const goPickOrigin = () => router.push({ pathname: wide ? '/' : '/home', params: { edit: 'origin' } });
  const goGenerating = (jobId: string) => router.push({ pathname: '/plan/generating', params: { jobId } });

  /**
   * @param afterConditions 조건 창에서 막 돌아온 길인가. 🔴 참이면 조건을 <b>다시 묻지 않는다.</b>
   *     안 그러면 저장 → 창 열림 → 저장 → 창 열림이 되어 영영 못 나간다.
   */
  const submitPlan = async (afterConditions = false) => {
    if (hardUnknown && !afterConditions) { setConditionsOpen(true); return; }
    if (!user) { router.push({ pathname: '/sign-in', params: { returnTo: '/plan' } }); return; }
    setJob({ state: 'submitting', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: null, resultRef: null });
    const next = await createRecommendationJobAdapter(accessToken).submit(draft);
    setJob(next);
    if (next.jobId) { void clearQuestionState(); goGenerating(next.jobId); }
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

  const headerChips = useMemo(() => startBarChips({
    origin: draft.origin, originLat: draft.originLat, originLng: draft.originLng,
    lodging: draft.lodging, lodgingLat: draft.lodgingLat, lodgingLng: draft.lodgingLng, lodgingPlace: draft.lodgingPlace,
    startDate: draft.startDate, endDate: draft.endDate,
    adults: draft.adults, children: draft.children,
  }, tx), [draft.lodgingPlace, draft.adults, draft.children, draft.endDate, draft.lodging, draft.lodgingLat, draft.lodgingLng, draft.origin, draft.originLat, draft.originLng, draft.startDate, tx]);

  const goTo = (next: number) => setState((prev) => ({ ...prev, open: Math.max(0, Math.min(PLAN_QUESTIONS.length - 1, next)), editing: null }));
  /** 지금 질문을 「건너뜀」으로 적고 다음으로. 선택 질문의 카드 머리에 있는 단추가 부른다. */
  const skipCurrent = () => {
    setState((prev) => ({ ...prev, skipped: { ...prev.skipped, [q.key]: true } }));
    if (!last) goTo(index + 1);
  };
  /** 그 질문으로 갈 수 있나 — 선택이거나, 필수를 다 채웠거나, 이미 지나온 자리면. */
  const jumpable = (i: number) => PLAN_QUESTIONS[i].skippable || requiredReady || i <= index;

  const toggleIn = (list: string[], code: string, max?: number) => {
    if (list.includes(code)) return list.filter((item) => item !== code);
    if (max && list.length >= max) return list;
    return [...list, code];
  };

  const optionGrid = (options: readonly PlanOption[], picked: string[], onPick: (code: string) => void, max?: number, images?: Record<string, number>) => (
    <View style={styles.optionGrid}>
      {options.map((option) => (
        <View key={option[0]} style={styles.optionCell}>
          <OptionCard
            image={images?.[option[0]]}
            label={labelOf(option, tx)}
            sub={subOf(option, tx)}
            selected={picked.includes(option[0])}
            disabled={Boolean(max) && picked.length >= (max as number) && !picked.includes(option[0])}
            onPress={() => onPick(option[0])}
          />
        </View>
      ))}
    </View>
  );

  const body = (item: PlanQuestion) => {
    switch (item.key) {
      case 'areas':
        return optionGrid(AREA_OPTIONS, draft.travelAreas, (code) => update({ travelAreas: toggleIn(draft.travelAreas, code) }));
      case 'budget':
        return (
          <View style={styles.stack}>
            <Text variant="hero" weight="bold" color={color.text.heading}>
              {tx(`${((draft.budgetKrw ?? 0) / 10000).toLocaleString()}만원`, `₩${(draft.budgetKrw ?? 0).toLocaleString()}`)}
            </Text>
            <View style={styles.chips}>
              {BUDGET_STEPS.map((step) => (
                <Pressable key={step} accessibilityRole="button" onPress={() => update({ budgetKrw: (draft.budgetKrw ?? 0) + step })} style={styles.chip}>
                  <Text weight="bold">+{tx(`${step / 10000}만`, `${step / 1000}k`)}</Text>
                </Pressable>
              ))}
              <Pressable accessibilityRole="button" onPress={() => update({ budgetKrw: 0 })} style={styles.chip}>
                <Text weight="bold" color={color.action.secondary}>{tx('전체 지우기', 'Clear')}</Text>
              </Pressable>
            </View>
          </View>
        );
      case 'move':
        return (
          <View style={styles.stack}>
            <View style={styles.timeRow}>
              {([['dayStartTime', '시작', 'Start'], ['dayEndTime', '종료', 'End']] as const).map(([field, k, e]) => (
                <View key={field} style={styles.timeField}>
                  <Text variant="caption" color={color.text.muted}>{tx(k, e)}</Text>
                  {/* 🔴 마스크를 안 꽂아서 「0800」이 그대로 서버까지 갔다. maskTimeInput 은
                      진작 있었고 시험도 붙어 있었는데 화면 어디서도 안 썼다 (S15P21E201-1452). */}
                  <TextInput
                    value={draft[field]}
                    onChangeText={(value) => update({ [field]: maskTimeInput(value) } as Partial<PlanDraft>)}
                    keyboardType="number-pad"
                    maxLength={5}
                    placeholder={field === 'dayStartTime' ? '09:00' : '18:00'}
                    placeholderTextColor={color.text.muted}
                    accessibilityLabel={tx(k, e)}
                    style={styles.input}
                  />
                </View>
              ))}
            </View>
            {/* 🔴 넘어가지 못하는 이유를 그 자리에서 말한다. 「다음」이 안 눌리는데 이유가
                없으면 사람은 자기가 무엇을 잘못했는지 모른 채 앱을 떠난다. */}
            {dayWindowIssue(draft) === 'FORMAT' ? (
              <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>
                {tx('시각을 09:00 처럼 네 자리로 적어 주세요.', 'Enter the time as four digits, like 09:00.')}
              </Text>
            ) : dayWindowIssue(draft) === 'ORDER' ? (
              <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>
                {tx('종료 시각은 시작 시각보다 늦어야 해요.', 'The end time must be later than the start time.')}
              </Text>
            ) : null}
            {optionGrid(TRANSPORT_OPTIONS, draft.transport ? [draft.transport] : [], (code) => update({ transport: code as PlanDraft['transport'] }))}
          </View>
        );
      case 'cats':
        return optionGrid(CATEGORY_OPTIONS, draft.preferences, (code) => update({ preferences: toggleIn(draft.preferences, code, 3) }), 3, CATEGORY_IMAGES);
      case 'pace':
        return optionGrid(PACE_OPTIONS, draft.paceLevel ? [draft.paceLevel] : [], (code) => update({ paceLevel: code as PlanDraft['paceLevel'] }));
      case 'aids':
        return <View style={styles.stack}>{([
          ['wheelchair', '휠체어를 써요', 'I use a wheelchair'],
          ['stroller', '유아차가 있어요', 'I have a stroller'],
          ['luggage', '큰 짐이 있어요', 'I have large luggage'],
        ] as const).map(([field, k, e]) => (
          <View key={field} style={styles.binaryRow}>
            <Text style={styles.binaryLabel}>{tx(k, e)}</Text>
            <View style={styles.chips}>
              {([[true, '예', 'Yes'], [false, '아니요', 'No']] as const).map(([value, yk, ye]) => (
                <Pressable
                  key={String(value)}
                  accessibilityRole="radio"
                  accessibilityState={{ selected: draft[field] === value }}
                  onPress={() => update({ [field]: value } as Partial<PlanDraft>)}
                  style={[styles.chip, draft[field] === value && styles.chipOn]}
                >
                  <Text weight="bold" color={draft[field] === value ? color.text.onAction : color.text.heading}>{tx(yk, ye)}</Text>
                </Pressable>
              ))}
            </View>
          </View>
        ))}</View>;
      case 'must':
        return (
          <MustVisitSearch
            picked={draft.mustVisitPlaces}
            onChange={(next) => update({ mustVisitPlaces: next })}
            tx={tx}
            ko={ko}
          />
        );
      default:
        return null;
    }
  };

  if (!ready) return <Screen scroll><Text>{tx('불러오는 중이에요…', 'Loading…')}</Text></Screen>;

  const stepEyebrow = q.skippable
    ? txf(tx, '선택 %s / %s', 'Optional %s / %s', index + 1 - requiredCount, PLAN_QUESTIONS.length - requiredCount)
    : txf(tx, '필수 %s / %s', 'Required %s / %s', index + 1, requiredCount);
  const stepTitle = q.skippable
    ? tx('더 답하면 일정이 좋아져요', 'A few more and the plan gets better')
    : txf(tx, '필수 질문은 %s개뿐이에요', 'Just %s required questions', requiredCount);
  const fillPct = Math.round((settledSoFar / PLAN_QUESTIONS.length) * 100);
  const dateLabel = dateRangeLabel({ startDate: draft.startDate, endDate: draft.endDate }, tx);
  const showDateCard = datesOpen ?? datesMissing;
  const effect = effectOf(q.key, draft, tx);
  // 선택 질문은 언제나 「다음」이 열린다(누르면 넘어가고, 「건너뛰기」가 따로 표로 남긴다).
  const canNext = q.answered(draft) || q.skippable;
  const statusLine = requiredReady
    ? readyToBuild
      ? txf(tx, '이제 만들 수 있어요 · 남은 %s개는 답할수록 일정이 좋아지는 질문이에요', 'You can build now · the remaining %s tune the plan to you', PLAN_QUESTIONS.length - settledSoFar)
      : originMissing
        ? tx('필수 질문은 다 답했어요 · 출발지만 고르면 만들 수 있어요', 'Required questions done · just pick a starting point to build')
        : tx('필수 질문은 다 답했어요 · 날짜만 정하면 만들 수 있어요', 'Required questions done · just pick your dates to build')
    : txf(tx, '필수 %s개만 답하면 만들 수 있어요 · 나머지는 건너뛰어도 돼요', 'Answer the %s required questions to build · the rest are optional', requiredCount);

  // ── 어디서나 쓰는 조각들 ──────────────────────────────────────────────────

  // 🔴 출발지 — 없으면 여기서 짚어 준다(S15P21E201-1342). 다 답하고 「만들기」를 누른 뒤에야
  //    서버 원문으로 막히던 것을 앞으로 당긴다.
  const originAsk = originMissing ? (
    <Pressable accessibilityRole="button" onPress={goPickOrigin} style={({ pressed }) => [styles.originAsk, pressed && styles.pressed]}>
      <Text variant="body" weight="bold" color={color.text.heading}>{tx('어디에서 출발하세요?', 'Where are you starting from?')}</Text>
      <Text variant="caption" color={color.text.muted}>{tx('출발지를 골라야 일정을 만들 수 있어요 · 눌러서 고르기', 'We need a starting point to build your trip · tap to choose')}</Text>
    </Pressable>
  ) : null;

  // 날짜 — 문항 화면 안에서 고른다(S15P21E201-1376). 없으면 펼친 카드로.
  const dateCardEl = showDateCard ? (
    <DateRangeCard
      value={{ startDate: draft.startDate, endDate: draft.endDate }}
      onChange={(next) => update({ startDate: next.startDate, endDate: next.endDate })}
      onDone={() => setDatesOpen(false)}
      tx={tx}
    />
  ) : null;

  const questionCard = (
    <View key={q.key} style={[styles.card, wide ? styles.cardWide : styles.cardPhone]}>
      <View style={styles.cardHead}>
        <View style={styles.cardCopy}>
          <Text variant="caption" weight="bold" color={color.text.eyebrow}>
            {q.skippable ? tx('선택 · 건너뛰어도 돼요', 'Optional · you can skip') : tx('필수', 'Required')}
          </Text>
          <Text variant="title" weight="bold">{tx(q.ko, q.en)}</Text>
          <Text color={color.text.muted}>{tx(q.hintKo, q.hintEn)}</Text>
        </View>
        {q.skippable ? (
          <Pressable accessibilityRole="button" onPress={skipCurrent} style={styles.skip}>
            <Text variant="caption" weight="bold" color={color.action.secondary}>{tx('건너뛰기', 'Skip')}</Text>
          </Pressable>
        ) : null}
      </View>
      {body(q)}
      {effect ? <EffectBand text={effect} tx={tx} /> : null}
    </View>
  );

  const navRow = (
    <View style={styles.navRow}>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ disabled: index === 0 }}
        disabled={index === 0}
        onPress={() => goTo(index - 1)}
        style={({ pressed }) => [styles.prev, index === 0 && styles.prevOff, pressed && styles.pressed]}
      >
        <Text weight="bold" color={index === 0 ? color.text.muted : color.text.heading}>{tx('이전', 'Back')}</Text>
      </Pressable>
      <View style={styles.next}>
        <Button
          accessibilityState={{ busy: job?.state === 'submitting' }}
          label={last
            ? job?.state === 'submitting' ? tx('만드는 중…', 'Building…') : tx('이 조건으로 일정 만들기', 'Build my itinerary')
            : tx('다음', 'Next')}
          disabled={last ? missing.length > 0 || datesMissing || originMissing || job?.state === 'submitting' : !canNext}
          onPress={() => {
            completeStep(index + 1);
            if (last) { if (readyToBuild) void submitPlan(); }
            else goTo(index + 1);
          }}
        />
      </View>
    </View>
  );

  // 마지막 자리에서 왜 못 만드는지 — 잠근 이유를 반드시 말한다. 잠그기만 하면 고장인 줄 안다.
  const submitAlerts = (
    <>
      {last && missing.length ? (
        <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>
          {txf(tx, '아직 안 답한 게 있어요: %s', 'Still missing: %s', missing.map((item) => tx(item.ko, item.en)).join(' · '))}
        </Text>
      ) : null}
      {last && requiredReady && originMissing ? (
        <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{tx('출발지를 골라야 만들 수 있어요 · 위에서 골라 주세요', 'Pick a starting point above to build')}</Text>
      ) : null}
      {last && requiredReady && !originMissing && datesMissing ? (
        <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{tx('날짜를 정해야 만들 수 있어요 · 위에서 골라 주세요', 'Pick your dates above to build')}</Text>
      ) : null}
      {last && hardUnknown ? (
        <Text variant="caption" color={color.state.danger}>{tx('식단을 아직 안 알려주셨어요. 눌러서 알려주세요.', 'We still need your diet answer — tap to add it.')}</Text>
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
  );

  // ── 넓은 화면 왼쪽 레일 — 「이번 여행」 카드 + 일곱 줄 체크리스트 ─────────────
  const rail = (
    <View style={styles.rail}>
      <View style={styles.tripCard}>
        <View style={styles.tripCardHead}>
          <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('이번 여행', 'This trip')}</Text>
          <Pressable accessibilityRole="button" accessibilityLabel={tx('이번 여행 수정', 'Edit this trip')} onPress={() => router.push('/')}>
            <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('수정', 'Edit')}</Text>
          </Pressable>
        </View>
        <Text weight="bold" numberOfLines={1}>{headerChips[0] ?? tx('날짜를 아직 안 정했어요', 'No dates yet')}</Text>
        {headerChips.slice(1).length ? (
          <Text variant="caption" color={color.text.muted} numberOfLines={1}>{headerChips.slice(1).join(' · ')}</Text>
        ) : null}
      </View>
      <View style={styles.checklist}>
        {PLAN_QUESTIONS.map((item, i) => {
          const now = i === index;
          const done = i < index && settledAt(i);
          const value = done
            ? summaryOf(item.key, draft, tx, Boolean(state.skipped[item.key]), ko)
            : item.skippable ? tx('선택', 'Optional') : tx('필수', 'Required');
          return (
            <Pressable
              key={item.key}
              accessibilityRole="button"
              accessibilityState={{ selected: now, disabled: !jumpable(i) }}
              disabled={!jumpable(i)}
              onPress={() => goTo(i)}
              style={[styles.checkRow, now && styles.checkRowNow]}
            >
              <View style={[styles.checkDot, now && styles.checkDotNow, done && styles.checkDotDone]}>
                <Text variant="micro" weight="bold" color={now || done ? color.text.onAction : color.text.muted}>{done ? '✓' : String(i + 1)}</Text>
              </View>
              <View style={styles.checkBody}>
                <Text variant="caption" weight={now ? 'bold' : 'medium'} color={now ? color.text.heading : color.text.body} numberOfLines={1}>{tx(item.ko, item.en)}</Text>
                <Text variant="micro" color={color.text.muted} numberOfLines={1}>{value}</Text>
              </View>
            </Pressable>
          );
        })}
      </View>
    </View>
  );

  // ── 넓은 화면 오른쪽 기둥 — 눈썹·진행 막대·질문 카드·이전/다음 ─────────────
  const desktopColumn = (
    <View style={styles.questions}>
      <View style={styles.stepCopy}>
        <Text variant="caption" weight="bold" color={color.text.eyebrow}>{stepEyebrow}</Text>
        <Text variant="title" weight="bold">{stepTitle}</Text>
      </View>
      <View style={styles.track}><View style={[styles.fill, { width: `${fillPct}%` }]} /></View>
      {originAsk}
      {dateCardEl}
      {questionCard}
      {navRow}
      {submitAlerts}
    </View>
  );

  // ── 폰 — 위 칩 줄 · StepDots · 상태 문구 · 답한 행 · 질문 카드 · 다음 질문 ──
  const answeredQuestions = PLAN_QUESTIONS.map((item, i) => ({ item, i })).filter(({ i }) => i < index && settledAt(i));
  const upcomingList = PLAN_QUESTIONS.slice(index + 1);
  const mobileColumn = (
    <View style={styles.questions}>
      <View style={styles.stepHead}>
        <View style={styles.stepCopy}>
          <Text variant="caption" weight="bold" color={color.text.eyebrow}>{stepEyebrow}</Text>
          <Text variant="title" weight="bold">{stepTitle}</Text>
        </View>
        <StepDots
          total={PLAN_QUESTIONS.length}
          required={requiredCount}
          index={index}
          settled={settledAt}
          onJump={(i) => { if (jumpable(i)) goTo(i); }}
          label={(i) => tx(PLAN_QUESTIONS[i].ko, PLAN_QUESTIONS[i].en)}
        />
      </View>
      <View style={styles.track}><View style={[styles.fill, { width: `${fillPct}%` }]} /></View>
      <Text variant="caption" color={color.text.muted}>{statusLine}</Text>

      {originAsk}
      {dateCardEl}

      {answeredQuestions.length || (!originMissing) || (!showDateCard && dateLabel) ? (
        <View style={styles.answeredList}>
          {!originMissing ? (
            <Pressable accessibilityRole="button" accessibilityLabel={tx('출발지 수정', 'Edit starting point')} onPress={goPickOrigin} style={({ pressed }) => [styles.answeredRowItem, pressed && styles.pressed]}>
              <View style={styles.answeredCheck}><Text variant="micro" weight="bold" color={color.text.onAction}>✓</Text></View>
              <View style={styles.answeredBody}>
                <Text variant="micro" color={color.text.muted} numberOfLines={1}>{tx('출발지', 'Starting point')}</Text>
                <Text variant="caption" weight="bold" numberOfLines={1}>{draft.origin || tx('고른 곳', 'Chosen')}</Text>
              </View>
              <Text variant="caption" weight="bold" color={color.text.muted}>{tx('수정', 'Edit')}</Text>
            </Pressable>
          ) : null}
          {!showDateCard && dateLabel ? (
            <Pressable accessibilityRole="button" accessibilityLabel={tx('여행 날짜 수정', 'Edit trip dates')} onPress={() => setDatesOpen(true)} style={({ pressed }) => [styles.answeredRowItem, pressed && styles.pressed]}>
              <View style={styles.answeredCheck}><Text variant="micro" weight="bold" color={color.text.onAction}>✓</Text></View>
              <View style={styles.answeredBody}>
                <Text variant="micro" color={color.text.muted} numberOfLines={1}>{tx('여행 날짜', 'Trip dates')}</Text>
                <Text variant="caption" weight="bold" numberOfLines={1}>{dateLabel}</Text>
              </View>
              <Text variant="caption" weight="bold" color={color.text.muted}>{tx('수정', 'Edit')}</Text>
            </Pressable>
          ) : null}
          {answeredQuestions.map(({ item, i }) => (
            <Pressable key={item.key} accessibilityRole="button" accessibilityLabel={txf(tx, '%s 수정', 'Edit %s', tx(item.ko, item.en))} onPress={() => goTo(i)} style={({ pressed }) => [styles.answeredRowItem, pressed && styles.pressed]}>
              <View style={styles.answeredCheck}><Text variant="micro" weight="bold" color={color.text.onAction}>✓</Text></View>
              <View style={styles.answeredBody}>
                <Text variant="micro" color={color.text.muted} numberOfLines={1}>{tx(item.ko, item.en)}</Text>
                <Text variant="caption" weight="bold" numberOfLines={1}>{summaryOf(item.key, draft, tx, Boolean(state.skipped[item.key]), ko) || tx('건너뜀', 'Skipped')}</Text>
              </View>
              <Text variant="caption" weight="bold" color={color.text.muted}>{tx('수정', 'Edit')}</Text>
            </Pressable>
          ))}
        </View>
      ) : null}

      {questionCard}
      {navRow}
      {submitAlerts}

      {upcomingList.length ? (
        <View style={styles.upcoming}>
          <Text variant="micro" weight="bold" color={color.text.eyebrow}>{tx('다음 질문', 'Coming up')}</Text>
          {upcomingList.map((item, offset) => {
            const i = index + 1 + offset;
            return (
              <Pressable key={item.key} accessibilityRole="button" disabled={!jumpable(i)} onPress={() => goTo(i)} style={({ pressed }) => [styles.upcomingRow, pressed && styles.pressed]}>
                <View style={styles.upcomingNo}><Text variant="micro" weight="bold" color={color.text.muted}>{i + 1}</Text></View>
                <Text variant="caption" color={color.text.muted} numberOfLines={1} style={styles.upcomingLabel}>{tx(item.ko, item.en)}</Text>
                <Text variant="micro" weight={item.skippable ? 'regular' : 'bold'} color={item.skippable ? color.text.muted : color.text.body}>{item.skippable ? tx('선택', 'optional') : tx('필수', 'required')}</Text>
              </Pressable>
            );
          })}
        </View>
      ) : null}
    </View>
  );

  return (
    <Screen scroll wide={wide} style={styles.canvas}>
      {/* 🔴 폰은 위 줄 하나에 뒤로 가기와 홈에서 받은 칩(출발·날짜·인원)을 같이 둔다(시안 01b).
          걸음 수는 바로 아래 눈썹이 이미 말하므로 여기 또 적지 않는다. 넓은 화면은 왼쪽 레일이 대신한다. */}
      {wide ? null : (
        <View style={styles.phoneTop}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={tx('뒤로 가기', 'Go back')}
            onPress={() => (router.canGoBack() ? router.back() : router.replace('/home'))}
            style={styles.phoneBack}
          >
            <Text variant="title">‹</Text>
          </Pressable>
          {/* 🔴 칩은 줄을 바꾸지 않는다 — 셋(출발·날짜·인원)이 두 줄로 깨져 「성인 1」이 따로 놀았다
              (2026-09-21 실기, S15P21E201-1401). 넘치면 가로로 밀어 본다. */}
          <ScrollView horizontal showsHorizontalScrollIndicator={false} keyboardShouldPersistTaps="handled" style={styles.phoneChipsScroll} contentContainerStyle={styles.phoneChips}>
            {headerChips.map((chip) => (
              <View key={chip} style={styles.phoneGivenChip}><Text variant="caption" weight="bold" numberOfLines={1}>{chip}</Text></View>
            ))}
          </ScrollView>
          <View>
            {/* 🔴 -1337 — 받은 것이 없을 때도 그린다. 없으면 「날짜 정하기」로 말만 바꾼다. */}
            <Pressable accessibilityRole="button" onPress={goSetDates} style={styles.phoneGivenEdit}>
              <Text variant="caption" weight="bold" color={color.action.secondary}>
                {headerChips.length ? tx('수정', 'Edit') : tx('날짜 정하기', 'Set dates')}
              </Text>
            </Pressable>
          </View>
        </View>
      )}

      {wide ? (
        <View style={styles.split}>
          {rail}
          {desktopColumn}
        </View>
      ) : (
        mobileColumn
      )}

      {/*
        🔴 -1334 — 닫힐 때 무엇을 골랐는지를 반드시 본다. ✕ 로 닫은 것(DISMISSED)만 그 자리에
        남고, 건너뛰든 저장하든 가려던 곳으로 간다 — 홈 화면이 이미 같은 규칙을 쓴다.
      */}
      <ConditionsPromptModal
        visible={conditionsOpen}
        reprompt
        onClose={(outcome) => {
          setConditionsOpen(false);
          if (outcome !== 'DISMISSED') void submitPlan(true);
        }}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  canvas: { backgroundColor: color.canvas },
  // 시안: justify-content:center · gap 32. 레일(280)과 카드(760)를 가운데로 모은다.
  split: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'center', gap: spacing[8], marginTop: spacing[6] },
  questions: { flex: 1, minWidth: 0, maxWidth: 760, gap: spacing[3] },

  // 왼쪽 레일 — 스크롤해도 붙어 있다(position: sticky 는 웹에서만 먹지만 RN 웹이 대상이다).
  rail: { width: 280, flexShrink: 0, gap: spacing[3], position: 'sticky' as unknown as 'relative', top: spacing[6] },
  tripCard: { gap: spacing[1], paddingVertical: spacing[3], paddingHorizontal: spacing[4], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  tripCardHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  checklist: { gap: 2, padding: spacing[2], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  checkRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 44, paddingVertical: spacing[1], paddingHorizontal: spacing[2], borderRadius: radius.md },
  checkRowNow: { backgroundColor: color.surface.tint },
  checkDot: { width: 24, height: 24, borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  checkDotNow: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  checkDotDone: { backgroundColor: color.state.success, borderColor: color.state.success },
  checkBody: { flex: 1, minWidth: 0 },

  phoneTop: { minHeight: 44, flexDirection: 'row', alignItems: 'center', gap: spacing[2], marginBottom: spacing[3] },
  phoneChipsScroll: { flex: 1, minWidth: 0 },
  phoneChips: { flexDirection: 'row', alignItems: 'center', gap: spacing[1], paddingRight: spacing[1] },
  phoneBack: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', marginLeft: -spacing[3] },
  phoneGivenChip: { paddingHorizontal: spacing[3], paddingVertical: 6, borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  phoneGivenEdit: { minHeight: 44, paddingLeft: spacing[2], justifyContent: 'center' },

  stepHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3] },
  stepCopy: { flex: 1, minWidth: 0, gap: 2 },
  track: { height: 4, borderRadius: radius.full, backgroundColor: color.surface.field, overflow: 'hidden' },
  fill: { height: 4, borderRadius: radius.full, backgroundColor: color.action.secondary },

  card: { gap: spacing[4], padding: spacing[6], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  cardWide: {},
  cardPhone: { padding: spacing[4] },
  cardHead: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: spacing[3] },
  cardCopy: { flex: 1, minWidth: 0, gap: spacing[1] },
  skip: { minHeight: 32, justifyContent: 'center' },

  optionGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  // 시안의 auto-fill minmax(150px,1fr) 을 RN 에서 흉내 낸다 — RN 에는 grid 가 없다.
  optionCell: { flexGrow: 1, flexBasis: 140, minWidth: 140 },

  navRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  prev: { minHeight: 48, justifyContent: 'center', paddingHorizontal: spacing[6], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  prevOff: { opacity: 0.5 },
  next: { flex: 1 },
  pressed: { opacity: 0.8 },

  // 🔴 출발지를 묻는 자리. 날짜 카드와 같은 무게로 둔다 — 둘 다 없으면 못 만든다.
  originAsk: { gap: spacing[1], padding: spacing[4], borderRadius: radius.lg, borderWidth: 1, borderColor: color.action.primary, backgroundColor: color.surface.card },
  answeredList: { gap: spacing[1] },
  answeredRowItem: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 44, paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card },
  answeredCheck: { width: 18, height: 18, borderRadius: radius.full, backgroundColor: color.state.success, alignItems: 'center', justifyContent: 'center' },
  answeredBody: { flex: 1, paddingVertical: spacing[1] },
  upcoming: { gap: 2, marginTop: spacing[2] },
  upcomingRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 36, paddingHorizontal: spacing[2] },
  upcomingNo: { width: 22, height: 22, borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, alignItems: 'center', justifyContent: 'center' },
  upcomingLabel: { flex: 1 },
  consent: { gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg },

  stack: { gap: spacing[3] },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 44, paddingHorizontal: spacing[4], justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.ivory, borderWidth: 1, borderColor: color.surface.border },
  chipOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  timeRow: { flexDirection: 'row', gap: spacing[3] },
  timeField: { flex: 1, gap: spacing[1] },
  input: { minHeight: 48, paddingHorizontal: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, color: color.text.heading },
  binaryRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3] },
  binaryLabel: { flex: 1 },
});
