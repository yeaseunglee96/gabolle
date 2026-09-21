// 여행 조건 — **한 번에 하나씩** 묻는다 (시안 ①, 인계 §2).
//
// 🔴 전에는 답한 질문이 한 줄로 접히며 아래로 쌓였다. 열 개를 다 답하면 화면이 길어지고,
//    「지금 무엇을 묻고 있는지」가 스크롤 어딘가로 밀렸다. 이제는 카드 하나만 남고 나머지는
//    위의 점과 아래의 요약 칩으로만 보인다.
//
// 🔴 넓은 화면은 두 기둥이다. 왼쪽은 **답하는 근거**(홈에서 받은 것 · 계정에 기억된 취향),
//    오른쪽은 질문. 근거를 안 보여 주면 사람은 같은 것을 또 묻는 줄 알고 되돌아간다.
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
import { CONFLICT_LABEL_PAIR, conflictingFoodCode, FOODS } from '@/plan/foodConflicts';
import { MustVisitSearch } from '@/plan/MustVisitSearch';
import { PlanContextColumn } from '@/plan/PlanContextColumn';
import { EffectBand, OptionCard, StepDots } from '@/plan/PlanStepperParts';
import { DateRangeCard, dateRangeLabel } from '@/plan/DateRangeCard';
import { clearQuestionState, loadQuestionState, saveQuestionState } from '@/plan/questionState';
import {
  AREA_OPTIONS, ATMOSPHERE_OPTIONS, CATEGORY_OPTIONS, FOOD_SUBTITLES, PACE_OPTIONS, TRANSPORT_OPTIONS,
  effectOf, type PlanOption,
} from '@/plan/planOptions';
import {
  INITIAL_QUESTION_STATE, PLAN_PAGES, PLAN_QUESTIONS, pageMissing,
  type PlanQuestion, type QuestionKey, type QuestionState,
} from '@/plan/planQuestions';
import { startBarChips } from '@/home/startBarValue';
import { assistantPrefillPatch } from '@/plan/assistantPrefill';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';
import { koreanObject } from '@/i18n/korean';

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

  // 🔴 자리는 «장»이다 — S15P21E201-1377. 예전 저장값(질문 번호 0~9)이 남아 있어도 장 수 안으로 잘린다.
  const index = Math.min(state.open, PLAN_PAGES.length - 1);
  const page = PLAN_PAGES[index];
  const last = index === PLAN_PAGES.length - 1;

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
  // 🔴 출발지가 없으면 서버가 일정을 안 만들어 준다 — S15P21E201-1342.
  //    서버의 TripConditionRules 가 originLat 을 요구한다. 예전에는 이것을 여기서 안 봐서,
  //    필수 질문을 다 답하고 「만들기」를 누른 «뒤에야» 막혔다. 그것도 서버 원문으로.
  //    이 앱에서 출발지를 채우는 곳은 홈 시작 바 하나뿐이다(PlanStartBar).
  const originMissing = draft.originLat === null || draft.originLng === null;

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
  // 🔴 예전에는 홈의 시작 줄로 보냈다(S15P21E201-1350). 돌아오면 문항이 1번부터라 열 개를 다 답한
  //    사람이 처음부터 다시 했다(2026-09-21 실기). 이제 이 화면 안의 달력 카드를 편다.
  const goSetDates = () => setDatesOpen(true);

  /**
   * 🔴 <b>출발지를 고르러 간다 — S15P21E201-1342.</b>
   *
   * <p>날짜처럼 이 화면 안에서 고르게 하는 것이 더 낫다. 그렇게 안 한 이유는 하나다 —
   * 출발지 고르기는 <b>검색</b>이 붙어 있고(서버 질의·디바운스·추천 목록) 그 코드는
   * {@code PlanStartBar} 안에 있다. 여기에 한 벌 더 만들면 같은 규칙이 두 곳에 생기고
   * 한쪽만 고쳐지는 날이 온다.
   *
   * <p>대신 시작 바를 <b>출발지 칸이 열린 채로</b> 연다({@code ?edit=origin}). 지금까지 넣은
   * 값도 함께 실려 간다. 「일정 물어보기」를 누르면 홈이 다시 {@code /plan} 으로 돌려보내므로
   * 왕복이 닫힌다 — 그냥 홈으로 튕기던 옛 동작(-1350)과 다른 점이 그것이다.
   */
  const goPickOrigin = () => router.push({ pathname: wide ? '/' : '/home', params: { edit: 'origin' } });

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

  const goTo = (next: number) => setState((prev) => ({ ...prev, open: Math.max(0, Math.min(PLAN_PAGES.length - 1, next)), editing: null }));
  /** 선택 장을 통째로 건너뛴다 — 그 장의 질문을 「건너뜀」으로 적고 다음 장으로. */
  const skipPage = () => {
    setState((prev) => ({ ...prev, skipped: { ...prev.skipped, ...Object.fromEntries(page.questions.map((item) => [item.key, true])) } }));
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

  // 🔴 「지나온 질문」만 센다 — 아직 안 본 질문에도 기본값이 들어 있어서(예산 10만원, 이동수단 대중교통)
  //    그대로 세면 「답했다」고 거짓말하게 된다.
  const pagesDone = PLAN_PAGES.slice(0, index).length;
  const readyToBuild = missing.length === 0 && !datesMissing && !originMissing;
  const showDateCard = datesOpen ?? datesMissing;
  const dateLabel = dateRangeLabel({ startDate: draft.startDate, endDate: draft.endDate }, tx);
  // 지나온 장의 답을 한 줄로 — 「기본 · 해운대 · 10만원 · 대중교통」. 누르면 그 장으로.
  const answeredAbove = PLAN_PAGES.slice(0, index).map((item, i) => ({
    item, i,
    value: item.questions.map((question) => (question.answered(draft) && !state.skipped[question.key] ? summaryOf(question.key, draft, tx, false, ko) : '')).filter(Boolean).join(' · '),
  }));
  const pageBlocked = pageMissing(page, draft);
  const pageCanAdvance = pageBlocked.length === 0;
  const nextPages = PLAN_PAGES.slice(index + 1);

  const questionColumn = (
    <View style={styles.questions}>
      <View style={styles.stepHead}>
        <View style={styles.stepCopy}>
          <Text variant="caption" weight="bold" color={color.text.eyebrow}>
            {txf(tx, '%s / %s · %s', '%s / %s · %s', index + 1, PLAN_PAGES.length, tx(page.ko, page.en))}
          </Text>
          <Text variant="title" weight="bold">{index === 0 ? tx('이것만 답하면 일정이 나와요', 'Answer these and your plan is ready') : tx(page.subKo, page.subEn)}</Text>
        </View>
        <StepDots
          total={PLAN_PAGES.length}
          required={1}
          index={index}
          settled={(i) => i < index}
          onJump={(i) => { if (i <= index || pageCanAdvance) goTo(i); }}
          label={(i) => tx(PLAN_PAGES[i].ko, PLAN_PAGES[i].en)}
        />
      </View>
      <View style={styles.track}>
        <View style={[styles.fill, { width: `${Math.round((pagesDone / PLAN_PAGES.length) * 100)}%` }]} />
      </View>
      <Text variant="caption" color={color.text.muted}>
        {index === 0
          ? tx('1장만 채우면 만들 수 있어요 · 2·3장은 선택이에요', 'Page 1 is all you need · pages 2 and 3 are optional')
          : readyToBuild
            ? tx('이제 만들 수 있어요 · 이 장은 답할수록 일정이 취향에 가까워져요', 'You can build now · this page tunes the plan to your taste')
            : originMissing
              ? tx('필수 질문은 다 답했어요 · 출발지만 고르면 만들 수 있어요', 'Required questions done · just pick a starting point to build')
              : tx('필수 질문은 다 답했어요 · 날짜만 정하면 만들 수 있어요', 'Required questions done · just pick your dates to build')}
      </Text>

      {/* 🔴 출발지 — 없으면 여기서 짚어 준다(S15P21E201-1342). 예전에는 묻는 자리가
          아예 없어서, 다 답하고 「만들기」를 누른 뒤에야 서버 원문으로 막혔다. */}
      {originMissing ? (
        <Pressable accessibilityRole="button" onPress={goPickOrigin} style={({ pressed }) => [styles.originAsk, pressed && styles.pressed]}>
          <Text variant="body" weight="bold" color={color.text.heading}>{tx('어디에서 출발하세요?', 'Where are you starting from?')}</Text>
          <Text variant="caption" color={color.text.muted}>{tx('출발지를 골라야 일정을 만들 수 있어요 · 눌러서 고르기', 'We need a starting point to build your trip · tap to choose')}</Text>
        </Pressable>
      ) : null}

      {/* 날짜 — 문항 화면 안에서 고른다(S15P21E201-1376). 없으면 펼친 카드, 있으면 ✓ 줄. */}
      {showDateCard ? (
        <DateRangeCard
          value={{ startDate: draft.startDate, endDate: draft.endDate }}
          onChange={(next) => update({ startDate: next.startDate, endDate: next.endDate })}
          onDone={() => setDatesOpen(false)}
          tx={tx}
        />
      ) : null}
      {answeredAbove.length || (!showDateCard && dateLabel) || !originMissing ? (
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
          {answeredAbove.map(({ item, i, value }) => (
            <Pressable key={item.key} accessibilityRole="button" accessibilityLabel={txf(tx, '%s 수정', 'Edit %s', tx(item.ko, item.en))} onPress={() => goTo(i)} style={({ pressed }) => [styles.answeredRowItem, pressed && styles.pressed]}>
              <View style={styles.answeredCheck}><Text variant="micro" weight="bold" color={color.text.onAction}>✓</Text></View>
              <View style={styles.answeredBody}>
                <Text variant="micro" color={color.text.muted} numberOfLines={1}>{txf(tx, '%s장 · %s', 'Page %s · %s', i + 1, tx(item.ko, item.en))}</Text>
                <Text variant="caption" weight="bold" numberOfLines={1}>{value || tx('건너뜀', 'Skipped')}</Text>
              </View>
              <Text variant="caption" weight="bold" color={color.text.muted}>{tx('수정', 'Edit')}</Text>
            </Pressable>
          ))}
        </View>
      ) : null}

      {/* 🔴 1장을 막 끝낸 자리(2장 머리)에서는 갈림길을 카드 «위»에 크게 — S15P21E201-1376. */}
      {index === 1 && readyToBuild ? (
        <View style={styles.forkCard}>
          <Text variant="title" weight="bold" color={color.text.heading}>{tx('필수는 끝! 지금 만들 수 있어요', 'Required part done — you can build now')}</Text>
          <Text variant="caption" color={color.text.muted}>{tx('아래는 선택이에요. 답할수록 일정이 취향에 가까워지고, 언제든 「지금 이대로 만들기」를 눌러도 돼요.', 'Everything below is optional. Each answer tunes the plan closer to you — and you can build any time.')}</Text>
          <Pressable accessibilityRole="button" accessibilityState={{ busy: job?.state === 'submitting' }} disabled={job?.state === 'submitting'} onPress={() => { completeStep(PLAN_QUESTIONS.length); void submitPlan(); }} style={({ pressed }) => [styles.forkPrimary, pressed && styles.pressed]}>
            <Text weight="bold" color={color.action.primary}>{job?.state === 'submitting' ? tx('만드는 중…', 'Building…') : tx('지금 이대로 만들기 →', 'Build with what I have →')}</Text>
          </Pressable>
        </View>
      ) : null}

      {/* 🔴 한 장에 그 장의 질문을 모두 — S15P21E201-1377. key 에 장 열쇠를 줘서 장이 바뀌면 스크롤·포커스가 따라오지 않는다. */}
      <View key={page.key} style={styles.pageStack}>
        {page.questions.map((question, qi) => {
          const effect = effectOf(question.key, draft, tx);
          return (
            <View key={question.key} style={[styles.card, wide ? styles.cardWide : styles.cardPhone]}>
              <View style={styles.cardHead}>
                <View style={styles.cardCopy}>
                  <Text variant="caption" weight="bold" color={color.text.eyebrow}>
                    {txf(tx, '%s · %s', '%s · %s', qi + 1, question.skippable ? tx('선택', 'Optional') : tx('필수', 'Required'))}
                  </Text>
                  <Text variant="title" weight="bold">{tx(question.ko, question.en)}</Text>
                  <Text color={color.text.muted}>{tx(question.hintKo, question.hintEn)}</Text>
                </View>
              </View>
              {body(question)}
              {effect ? <EffectBand text={effect} tx={tx} /> : null}
            </View>
          );
        })}
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
              : index === 0
                ? pageCanAdvance ? tx('취향도 알려주기 →', 'Add my taste →') : txf(tx, `%s${koreanObject(pageBlocked[0]?.ko ?? '')} 골라 주세요`, 'Pick %s first', tx(pageBlocked[0]?.ko ?? '', pageBlocked[0]?.en ?? ''))
                : tx('다음', 'Next')}
            disabled={last ? missing.length > 0 || datesMissing || originMissing || job?.state === 'submitting' : !pageCanAdvance}
            onPress={() => {
              completeStep(page.questions.length * (index + 1));
              if (last) void submitPlan();
              else goTo(index + 1);
            }}
          />
        </View>
      </View>
      {/* 선택 장은 통째로 건너뛸 수 있다 — 질문마다 건너뛰기를 누르게 하지 않는다. */}
      {index > 0 && !last ? (
        <Pressable accessibilityRole="button" onPress={skipPage} style={styles.skipPage}>
          <Text variant="caption" weight="bold" color={color.action.secondary}>{tx('이 장은 건너뛰기', 'Skip this page')}</Text>
        </Pressable>
      ) : null}

      {/* 🔴 필수는 답했는데 날짜가 없으면 — 이유를 말하고 위 달력으로(S15P21E201-1372). */}
      {missing.length === 0 && datesMissing ? (
        <Pressable accessibilityRole="button" onPress={goSetDates} style={({ pressed }) => [styles.buildNow, pressed && styles.pressed]}>
          <Text weight="bold" color={color.text.heading}>{tx('날짜만 정하면 바로 만들 수 있어요 ↑', 'Pick your dates above and build right away ↑')}</Text>
          <Text variant="caption" color={color.text.muted}>{tx('필수 질문은 다 답했어요 · 위 달력에서 날짜를 골라 주세요', 'Required questions done · pick the dates in the calendar above')}</Text>
        </Pressable>
      ) : null}
      {readyToBuild && !last && index !== 1 ? (
        <Pressable accessibilityRole="button" accessibilityState={{ busy: job?.state === 'submitting' }} disabled={job?.state === 'submitting'} onPress={() => { completeStep(PLAN_QUESTIONS.length); void submitPlan(); }} style={({ pressed }) => [styles.buildNow, pressed && styles.pressed]}>
          <Text weight="bold" color={color.text.heading}>{job?.state === 'submitting' ? tx('만드는 중…', 'Building…') : tx('지금 이대로 만들기 →', 'Build with what I have →')}</Text>
          <Text variant="caption" color={color.text.muted}>{tx('나머지는 나중에 일정에서 고칠 수 있어요', 'You can fine-tune the rest on the itinerary later')}</Text>
        </Pressable>
      ) : null}

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

      {/* 남은 장을 흐리게 — 무엇이 얼마나 남았는지 알면 길지 않다. */}
      {nextPages.length ? (
        <View style={styles.upcoming}>
          <Text variant="micro" weight="bold" color={color.text.eyebrow}>{tx('다음 장', 'Coming up')}</Text>
          {nextPages.map((item, offset) => {
            const i = index + 1 + offset;
            return (
              <Pressable key={item.key} accessibilityRole="button" disabled={!pageCanAdvance} onPress={() => goTo(i)} style={({ pressed }) => [styles.upcomingRow, pressed && styles.pressed]}>
                <View style={styles.upcomingNo}><Text variant="micro" weight="bold" color={color.text.muted}>{i + 1}</Text></View>
                <Text variant="caption" color={color.text.muted} numberOfLines={1} style={styles.upcomingLabel}>{tx(item.ko, item.en)} · {item.questions.map((q) => tx(q.ko, q.en)).join(' · ')}</Text>
                <Text variant="micro" color={color.text.muted}>{tx('선택', 'optional')}</Text>
              </Pressable>
            );
          })}
        </View>
      ) : null}
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
          {/* 🔴 칩은 줄을 바꾸지 않는다 — 셋(출발·날짜·인원)이 뒤로 가기와 「수정」 사이에서 두 줄로 깨져
              「성인 1」이 따로 놀았다(2026-09-21 실기, S15P21E201-1401). 넘치면 가로로 밀어 본다. */}
          <ScrollView horizontal showsHorizontalScrollIndicator={false} keyboardShouldPersistTaps="handled" style={styles.phoneChipsScroll} contentContainerStyle={styles.phoneChips}>
            {headerChips.map((chip) => (
              <View key={chip} style={styles.phoneGivenChip}><Text variant="caption" weight="bold" numberOfLines={1}>{chip}</Text></View>
            ))}
          </ScrollView>
          <View>
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
  pageStack: { gap: spacing[3] },
  skipPage: { alignSelf: 'center', minHeight: 40, justifyContent: 'center', paddingHorizontal: spacing[3] },
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
  forkCard: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.tint },
  // 동백 채움은 화면에 하나(「다음」)뿐이라 여기는 붉은 선 — tools/check-palette.mjs 의 규칙.
  forkPrimary: { marginTop: spacing[1], minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, borderWidth: 1.5, borderColor: color.action.primary, backgroundColor: color.surface.card },
  buildNow: { gap: 2, alignItems: 'center', paddingVertical: spacing[3], paddingHorizontal: spacing[4], borderRadius: radius.md, borderWidth: 1.5, borderColor: color.action.secondary, backgroundColor: color.surface.card },
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
  scaleBlock: { gap: spacing[1] },
  scaleRow: { flexDirection: 'row', gap: spacing[2] },
  scaleDot: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border },
  scaleDotOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  scaleEnds: { flexDirection: 'row', justifyContent: 'space-between' },
  binaryRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3] },
  binaryLabel: { flex: 1 },
});
