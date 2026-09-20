// 여행 조건 — **한 번에 하나씩** 묻는다 (시안 ①, 인계 §2).
//
// 🔴 전에는 답한 질문이 한 줄로 접히며 아래로 쌓였다. 열 개를 다 답하면 화면이 길어지고,
//    「지금 무엇을 묻고 있는지」가 스크롤 어딘가로 밀렸다. 이제는 카드 하나만 남고 나머지는
//    위의 점과 아래의 요약 칩으로만 보인다.
//
// 🔴 넓은 화면은 두 기둥이다. 왼쪽은 **답하는 근거**(홈에서 받은 것 · 계정에 기억된 취향),
//    오른쪽은 질문. 근거를 안 보여 주면 사람은 같은 것을 또 묻는 줄 알고 되돌아간다.
import { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
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
import { CONFLICT_LABEL_PAIR, conflictingFoodCode, FOODS } from '@/plan/foodConflicts';
import { MustVisitSearch } from '@/plan/MustVisitSearch';
import { PlanContextColumn } from '@/plan/PlanContextColumn';
import { AnsweredChip, EffectBand, OptionCard, StepDots } from '@/plan/PlanStepperParts';
import {
  AREA_OPTIONS, ATMOSPHERE_OPTIONS, CATEGORY_OPTIONS, FOOD_SUBTITLES, PACE_OPTIONS, TRANSPORT_OPTIONS,
  effectOf, type PlanOption,
} from '@/plan/planOptions';
import {
  INITIAL_QUESTION_STATE, PLAN_QUESTIONS, canAdvance,
  type PlanQuestion, type QuestionKey, type QuestionState,
} from '@/plan/planQuestions';
import { startBarChips } from '@/home/startBarValue';
import { assistantPrefillPatch } from '@/plan/assistantPrefill';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';

const BUDGET_STEPS = [10000, 30000, 50000, 100000] as const;
const SCALES = [
  { key: 'localityLevel' as const, ko: '로컬 느낌', en: 'Local feel', lowKo: '관광지', lowEn: 'Touristy', highKo: '동네', highEn: 'Neighborhood' },
  { key: 'quietLevel' as const, ko: '조용함', en: 'Quietness', lowKo: '북적임', lowEn: 'Busy', highKo: '조용함', highEn: 'Quiet' },
  { key: 'touristLevel' as const, ko: '관광지 비중', en: 'Tourist spots', lowKo: '적게', lowEn: 'Fewer', highKo: '많이', highEn: 'More' },
];

type Tx = (ko: string, en: string) => string;
function labelOf(option: PlanOption, tx: Tx) { return tx(option[1], option[2]); }
function subOf(option: PlanOption, tx: Tx) { return tx(option[3], option[4]); }

function Scale({ value, onChange, lowLabel, highLabel }: { value: number | null; onChange: (next: number) => void; lowLabel: string; highLabel: string }) {
  return (
    <View style={styles.scaleBlock}>
      <View style={styles.scaleRow}>
        {[1, 2, 3, 4, 5].map((level) => (
          <Pressable
            key={level}
            accessibilityRole="radio"
            accessibilityState={{ selected: value === level }}
            accessibilityLabel={String(level)}
            onPress={() => onChange(level)}
            style={[styles.scaleDot, value === level && styles.scaleDotOn]}
          >
            <Text variant="caption" weight="bold" color={value === level ? color.text.onAction : color.text.heading}>{level}</Text>
          </Pressable>
        ))}
      </View>
      <View style={styles.scaleEnds}>
        <Text variant="caption" color={color.text.muted}>{lowLabel}</Text>
        <Text variant="caption" color={color.text.muted}>{highLabel}</Text>
      </View>
    </View>
  );
}

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
    case 'moods': return labels(ATMOSPHERE_OPTIONS, draft.atmospheres);
    case 'scales': return SCALES.filter((scale) => draft[scale.key] !== null)
      .map((scale) => `${tx(scale.ko, scale.en)} ${draft[scale.key]}`).join(' · ');
    case 'foods': return FOODS.filter(([code]) => draft.foods.includes(code)).map(([, k, e]) => tx(k, e)).join(' · ');
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

  const searchParams = useLocalSearchParams<{ days?: string; people?: string }>();
  const prefilled = useRef(false);
  useEffect(() => {
    if (!ready || prefilled.current) return;
    prefilled.current = true;
    const patch = assistantPrefillPatch(searchParams, draft);
    if (Object.keys(patch).length) update(patch);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready]);

  const index = Math.min(state.open, PLAN_QUESTIONS.length - 1);
  const question = PLAN_QUESTIONS[index];
  const last = index === PLAN_QUESTIONS.length - 1;

  // 미확인 필수 조건(알레르기·식단)이 남아 있나. 「모르면 안전하다고 치지 않는다」가 방침이다.
  const hardUnknown = draft.allergyStatus === 'UNKNOWN' || draft.dietStatus === 'UNKNOWN'
    || (draft.allergyStatus === 'VALUES' && !draft.allergies.length)
    || (draft.dietStatus === 'VALUES' && !draft.dietTypes.length);

  // 🔴 못 건너뛰는 질문 가운데 아직 안 답한 것. 마지막 단추를 잠그는 근거이자, 무엇이
  //    모자란지 말해 주는 근거다. 잠그기만 하고 이유를 안 적으면 사람은 고장인 줄 안다.
  const missing = PLAN_QUESTIONS.filter((item) => !item.skippable && !item.answered(draft));

  /**
   * 🔴 -1337 — 날짜는 여기서 묻지 않는데 <b>서버는 반드시 요구한다.</b>
   *
   * <p>위쪽 「여행 만들기」로 들어오면 날짜가 비어 있다. 그대로 열 개를 다 답하고 마지막
   * 단추를 누르면 <b>서버 말투가 그대로</b> 나왔다 — {@code finishDate: 널이어서는 안됩니다
   * (TRIP_VALIDATION_FAILED)}. 열 개를 다 답한 뒤에.
   *
   * <p>화면은 처음부터 알고 있다 — 옆 기둥이 「날짜를 아직 안 정했어요」라고 적고 있다.
   * 알면서 보내지 않는다.
   */
  const datesMissing = !draft.startDate || !draft.endDate;

  /** 날짜를 정하는 자리 — 폰은 홈의 시작 줄, 넓은 화면은 첫 화면의 시작 줄이다. */
  /**
   * 🔴 「날짜 정하기」·「수정」이 그냥 홈으로 튕기던 것 — S15P21E201-1350.
   *
   * <p>예전에는 `router.push('/home')` 뿐이었다. 홈에 도착해도 시작 바는 접혀 있고,
   * 고칠 칸도 안 열리고, 문항으로 돌아올 길도 없었다. 바로 아래 주석이 「막아 놓고 문을
   * 안 준 상태였다」라고 적어 둔 그 문제인데, 문을 달고 보니 <b>아무것도 열려 있지 않은
   * 방</b>으로 이어져 있었다. 2026-09-19 일본어(「編集」)·2026-09-20 영어(「Set dates」)
   * 양쪽 실기에서 확인했다.
   *
   * <p>이제 열 칸을 함께 보낸다. 홈의 시작 바가 그 칸을 펴고 지금 값까지 채워서 뜬다.
   * 「일정 물어보기」를 누르면 홈이 다시 `/plan` 으로 돌려보내므로 왕복이 닫힌다.
   */
  const goSetDates = () => router.push({ pathname: wide ? '/' : '/home', params: { edit: 'dates' } });

  const goGenerating = (jobId: string) => router.push({ pathname: '/plan/generating', params: { jobId } });

  /**
   * @param afterConditions 조건 창에서 막 돌아온 길인가.
   *     🔴 참이면 조건을 <b>다시 묻지 않는다.</b> 안 그러면 저장 → 창 열림 → 저장 →
   *     창 열림이 되어 영영 못 나간다.
   */
  const submitPlan = async (afterConditions = false) => {
    if (hardUnknown && !afterConditions) { setConditionsOpen(true); return; }
    if (!user) { router.push({ pathname: '/sign-in', params: { returnTo: '/plan' } }); return; }
    setJob({ state: 'submitting', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: null, resultRef: null });
    const next = await createRecommendationJobAdapter(accessToken).submit(draft);
    setJob(next);
    if (next.jobId) goGenerating(next.jobId);
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
    startDate: draft.startDate, endDate: draft.endDate,
    adults: draft.adults, children: draft.children,
  }, tx), [draft.adults, draft.children, draft.endDate, draft.origin, draft.originLat, draft.originLng, draft.startDate, tx]);

  // 계정에 기억된 취향 — 옆 기둥이 쓴다.
  const tastes = useMemo(() => {
    const picked: string[] = [];
    for (const option of CATEGORY_OPTIONS) if (draft.preferences.includes(option[0])) picked.push(labelOf(option, tx));
    for (const option of ATMOSPHERE_OPTIONS) if (draft.atmospheres.includes(option[0])) picked.push(labelOf(option, tx));
    for (const scale of SCALES) if (draft[scale.key] !== null) picked.push(`${tx(scale.ko, scale.en)} ${draft[scale.key]}`);
    return picked;
  }, [draft.atmospheres, draft.localityLevel, draft.preferences, draft.quietLevel, draft.touristLevel, tx]);

  const goTo = (next: number) => setState((prev) => ({ ...prev, open: Math.max(0, Math.min(PLAN_QUESTIONS.length - 1, next)), editing: null }));
  const skip = (item: PlanQuestion) => {
    setState((prev) => ({ ...prev, skipped: { ...prev.skipped, [item.key]: true } }));
    if (!last) goTo(index + 1);
  };

  const toggleIn = (list: string[], code: string, max?: number) => {
    if (list.includes(code)) return list.filter((item) => item !== code);
    if (max && list.length >= max) return list;
    return [...list, code];
  };

  const optionGrid = (options: readonly PlanOption[], picked: string[], onPick: (code: string) => void, max?: number) => (
    <View style={styles.optionGrid}>
      {options.map((option) => (
        <View key={option[0]} style={styles.optionCell}>
          <OptionCard
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
                  <TextInput
                    value={draft[field]}
                    onChangeText={(value) => update({ [field]: value } as Partial<PlanDraft>)}
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
            {optionGrid(TRANSPORT_OPTIONS, draft.transport ? [draft.transport] : [], (code) => update({ transport: code as PlanDraft['transport'] }))}
          </View>
        );
      case 'cats':
        return optionGrid(CATEGORY_OPTIONS, draft.preferences, (code) => update({ preferences: toggleIn(draft.preferences, code, 3) }), 3);
      case 'pace':
        return optionGrid(PACE_OPTIONS, draft.paceLevel ? [draft.paceLevel] : [], (code) => update({ paceLevel: code as PlanDraft['paceLevel'] }));
      case 'moods':
        return optionGrid(ATMOSPHERE_OPTIONS, draft.atmospheres, (code) => update({ atmospheres: toggleIn(draft.atmospheres, code) }));
      case 'scales':
        return <View style={styles.stack}>{SCALES.map((scale) => (
          <View key={scale.key} style={styles.stack}>
            <Text weight="bold">{tx(scale.ko, scale.en)}</Text>
            <Scale
              value={draft[scale.key]}
              onChange={(level) => update({ [scale.key]: level } as Partial<PlanDraft>)}
              lowLabel={tx(scale.lowKo, scale.lowEn)}
              highLabel={tx(scale.highKo, scale.highEn)}
            />
          </View>
        ))}</View>;
      case 'foods':
        return (
          <View style={styles.optionGrid}>
            {FOODS.map(([code, k, e]) => {
              // 알레르기·식단과 부딪히는 음식은 고를 수 없게 하고 왜인지 부제 자리에 적는다.
              const conflict = conflictingFoodCode(code, draft.allergies, draft.dietTypes);
              const hint = FOOD_SUBTITLES[code];
              return (
                <View key={code} style={styles.optionCell}>
                  <OptionCard
                    label={tx(k, e)}
                    sub={conflict ? tx(...CONFLICT_LABEL_PAIR[conflict.code]) : hint ? tx(hint[0], hint[1]) : ''}
                    selected={draft.foods.includes(code)}
                    disabled={conflict !== null}
                    onPress={() => update({ foods: toggleIn(draft.foods, code) })}
                  />
                </View>
              );
            })}
          </View>
        );
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

  const effect = effectOf(question.key, draft, tx);
  const settledAt = (i: number) => {
    const item = PLAN_QUESTIONS[i];
    return Boolean(state.skipped[item.key]) || item.answered(draft);
  };

  const questionColumn = (
    <View style={styles.questions}>
      <View style={styles.stepHead}>
        <View style={styles.stepCopy}>
          <Text variant="caption" weight="bold" color={color.text.eyebrow}>
            {tx(`질문 ${index + 1} / ${PLAN_QUESTIONS.length}`, `Question ${index + 1} / ${PLAN_QUESTIONS.length}`)}
          </Text>
          <Text variant="title" weight="bold">{tx('여행 조건 알려주기', 'Tell us about your trip')}</Text>
        </View>
        <StepDots
          total={PLAN_QUESTIONS.length}
          index={index}
          settled={settledAt}
          onJump={goTo}
          label={(i) => tx(PLAN_QUESTIONS[i].ko, PLAN_QUESTIONS[i].en)}
        />
      </View>
      <View style={styles.track}>
        <View style={[styles.fill, { width: `${Math.round((index / PLAN_QUESTIONS.length) * 100)}%` }]} />
      </View>

      {/* 🔴 key 에 질문 열쇠를 준다. 질문이 바뀌면 카드가 통째로 새로 마운트되어, 앞 질문의
          스크롤 위치와 입력 포커스가 따라오지 않는다. 안 주면 열 문항이 한 카드처럼 느껴진다. */}
      <View key={question.key} style={[styles.card, wide ? styles.cardWide : styles.cardPhone]}>
        <View style={styles.cardHead}>
          <View style={styles.cardCopy}>
            <Text variant="caption" weight="bold" color={color.text.eyebrow}>{index + 1} / {PLAN_QUESTIONS.length}</Text>
            <Text variant="title" weight="bold">{tx(question.ko, question.en)}</Text>
            <Text color={color.text.muted}>{tx(question.hintKo, question.hintEn)}</Text>
          </View>
          {question.skippable ? (
            <Pressable accessibilityRole="button" onPress={() => skip(question)} style={styles.skip}>
              <Text variant="caption" weight="bold" color={color.action.secondary}>{tx('건너뛰기', 'Skip')}</Text>
            </Pressable>
          ) : null}
        </View>

        {body(question)}
        {effect ? <EffectBand text={effect} tx={tx} /> : null}
      </View>

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
            disabled={last ? missing.length > 0 || datesMissing || job?.state === 'submitting' : !canAdvance(question, draft) && !question.skippable}
            onPress={() => {
              completeStep(index + 1);
              if (last) void submitPlan();
              else goTo(index + 1);
            }}
          />
        </View>
      </View>

      {/* 🔴 날짜가 없으면 여기서 막는다. 서버가 어차피 거절하는데, 그 거절은 열 개를 다
          답한 뒤에 서버 말투로 온다. 🔴 <b>누르면 정하러 갈 수 있게</b> 한다 — 잠그기만 하고
          문을 안 주면 나갈 길이 없다. 날짜를 대신 지어 넣지는 않는다. */}
      {last && datesMissing ? (
        <Pressable accessibilityRole="button" onPress={goSetDates}>
          <Text accessibilityRole="alert" variant="caption" weight="bold" color={color.state.danger}>
            {tx('날짜를 아직 안 정했어요 — 눌러서 정해 주세요.', 'No dates yet — tap to choose them.')}
          </Text>
        </Pressable>
      ) : null}

      {/* 🔴 무엇이 모자란지 적는다. 단추만 잠그면 사람은 고장인 줄 알고 새로고침한다. */}
      {last && missing.length ? (
        <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>
          {txf(tx, '아직 안 답한 게 있어요: %s', 'Still missing: %s', missing.map((item) => tx(item.ko, item.en)).join(' · '))}
        </Text>
      ) : null}
      {last && hardUnknown ? (
        <Text variant="caption" color={color.state.danger}>{tx('알레르기·식단을 아직 안 알려주셨어요. 눌러서 알려주세요.', 'We still need your allergy and diet answers — tap to add them.')}</Text>
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

      {/* 답한 질문 — 누르면 그 질문으로 돌아간다. 요약이 없으면 안 그린다. */}
      <View style={styles.answeredRow}>
        {PLAN_QUESTIONS.map((item, i) => {
          // 🔴 **지나온 질문만** 적는다. 아직 안 본 질문에도 기본값이 들어 있어서(예산 10만원,
          //    이동수단 대중교통) 그대로 세면 「답했다」고 거짓말하게 된다. 사람은 그걸 보고
          //    답한 줄 알고 넘어가고, 정작 자기가 안 고른 조건으로 일정을 받는다.
          if (i >= index || !settledAt(i)) return null;
          const value = summaryOf(item.key, draft, tx, Boolean(state.skipped[item.key]), ko);
          if (!value) return null;
          return <AnsweredChip key={item.key} label={tx(item.ko, item.en)} value={value} onPress={() => goTo(i)} />;
        })}
      </View>
    </View>
  );

  return (
    <Screen scroll wide={wide} style={styles.canvas}>
      {/* 🔴 폰은 위 줄 하나에 뒤로 가기와 **홈에서 받은 칩**을 같이 둔다(시안 01b).
          걸음 수는 바로 아래 「질문 n / 10」 이 이미 말하므로 여기 또 적지 않는다.
          넓은 화면에는 위 내비가 있어서 이 줄이 없다. */}
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
          <View style={styles.phoneChips}>
            {headerChips.map((chip) => (
              <View key={chip} style={styles.phoneGivenChip}><Text variant="caption" weight="bold" numberOfLines={1}>{chip}</Text></View>
            ))}
            {/* 🔴 -1337 — 예전에는 <b>받은 것이 있을 때만</b> 이 단추를 그렸다. 그래서
                날짜가 없는 사람에게는 날짜를 정하러 갈 길이 화면에 아예 없었다 —
                막아 놓고 문을 안 준 상태였다. 지금은 없을 때도 그리고, 말만 바꾼다. */}
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
          <PlanContextColumn
            headerChips={headerChips}
            dateLine={headerChips[0] ?? tx('날짜를 아직 안 정했어요', 'No dates yet')}
            originLine={headerChips.slice(1).join(' · ')}
            tastes={tastes}
            onEditGiven={() => router.push('/')}
            onOpenTastes={() => router.push('/me?panel=preferences')}
            tx={tx}
          />
          {questionColumn}
        </View>
      ) : (
        questionColumn
      )}

      {/*
        🔴 -1334 — 닫힐 때 무엇을 골랐는지를 <b>반드시 본다.</b> 예전에는 그 값을 버리고
        닫기만 해서, 「저장하고 시작」을 눌러도 <b>시작이 안 됐다.</b> 창만 사라지고 같은
        자리에 남는데 화면은 아무 말도 안 해서, 큰 단추를 한 번 더 눌러야 하는 줄 아무도
        몰랐다.

        ✕ 로 닫은 것(DISMISSED)만 그 자리에 남는다. 건너뛰든 저장하든 가려던 곳으로 간다 —
        홈 화면이 이미 같은 규칙을 쓴다. 조건을 안 적었다고 길을 막지 않는다.
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
  split: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[8], marginTop: spacing[6] },
  questions: { flex: 1, minWidth: 0, maxWidth: 760, gap: spacing[3] },

  phoneTop: { minHeight: 44, flexDirection: 'row', alignItems: 'center', gap: spacing[2], marginBottom: spacing[3] },
  // 칩은 오른쪽으로 몰고, 자리가 모자라면 줄을 바꾼다 — 잘라 내면 어느 날짜인지 못 읽는다.
  phoneChips: { flex: 1, minWidth: 0, flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'flex-end', gap: spacing[1] },
  phoneBack: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', marginLeft: -spacing[3] },
  phoneGivenChip: { paddingHorizontal: spacing[3], paddingVertical: 6, borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  phoneGivenEdit: { marginLeft: 'auto', minHeight: 32, justifyContent: 'center' },

  stepHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3] },
  stepCopy: { flex: 1, minWidth: 0, gap: 2 },
  track: { height: 4, borderRadius: radius.full, backgroundColor: color.surface.field, overflow: 'hidden' },
  fill: { height: 4, borderRadius: radius.full, backgroundColor: color.action.secondary },

  card: { gap: spacing[4], padding: spacing[6], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  cardWide: { minHeight: 360 },
  cardPhone: { minHeight: 300 },
  cardHead: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: spacing[3] },
  cardCopy: { flex: 1, minWidth: 0, gap: spacing[1] },
  skip: { minHeight: 32, justifyContent: 'center' },

  optionGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  // 시안의 auto-fill minmax(150px,1fr) 을 RN 에서 흉내 낸다 — RN 에는 grid 가 없다.
  optionCell: { flexGrow: 1, flexBasis: 150, minWidth: 150 },

  navRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  prev: { minHeight: 48, justifyContent: 'center', paddingHorizontal: spacing[6], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  prevOff: { opacity: 0.5 },
  next: { flex: 1 },
  pressed: { opacity: 0.8 },

  answeredRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], marginTop: spacing[2] },
  consent: { gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg },

  stack: { gap: spacing[3] },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 44, paddingHorizontal: spacing[4], justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.ivory, borderWidth: 1, borderColor: color.surface.border },
  chipOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  timeRow: { flexDirection: 'row', gap: spacing[3] },
  timeField: { flex: 1, gap: spacing[1] },
  input: { minHeight: 48, paddingHorizontal: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, color: color.text.heading },
  scaleBlock: { gap: spacing[1] },
  scaleRow: { flexDirection: 'row', gap: spacing[2] },
  scaleDot: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border },
  scaleDotOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  scaleEnds: { flexDirection: 'row', justifyContent: 'space-between' },
  binaryRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3] },
  binaryLabel: { flex: 1 },
});
