// 여행 조건 한 페이지 — 질문 카드 하나에 답하면 다음이 열린다 (S15P21E201-1233).
// 시안: docs/design_handoff_plan_flow/PlanFlow.dc.html 의 p1.
//
// 🔴 전에는 조건 입력이 취향 화면 · 제약조건 화면으로 흩어져 있었고, 한 화면에 카드가
//    여럿 있어서 **어디까지 했는지가 안 보였다.** 한 번에 하나만 묻는다.
//
// 🔴 **출발지·날짜·인원은 다시 묻지 않는다.** 홈의 시작 바에서 받았다. 위의 칩 줄로만
//    보여 주고, 고치려면 그 화면으로 돌아간다.
//
// 🔴 마지막 「이 조건으로 일정 만들기」는 **확인 화면으로 보낸다.** 그 화면이 건강 관련
//    조건 동의를 받는 자리라, 여기서 건너뛰면 동의 없이 일정이 만들어진다.
import { useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';
import { usePlan, type PlanDraft } from '@/plan/PlanProvider';
import { CONFLICT_LABEL_PAIR, conflictingFoodCode, FOODS } from '@/plan/foodConflicts';
import {
  INITIAL_QUESTION_STATE,
  PLAN_QUESTIONS,
  allSettled,
  canAdvance,
  isSettled,
  nextOpenIndex,
  remainingCount,
  settledCount,
  type PlanQuestion,
  type QuestionKey,
  type QuestionState,
} from '@/plan/planQuestions';
import { summarizeStartBar } from '@/home/startBarValue';

const AREAS = [
  ['HAEUNDAE', '해운대', 'Haeundae'], ['GWANGALLI', '광안리', 'Gwangalli'], ['NAMPO', '남포동', 'Nampo-dong'],
  ['SEOMYEON', '서면', 'Seomyeon'], ['YEONGDO', '영도', 'Yeongdo'], ['SONGJEONG', '송정', 'Songjeong'],
] as const;

const CATEGORIES = [
  ['SEA_BEACH', '바다 & 해변', 'Sea & Beach'], ['CITY', '도심 탐험', 'City'], ['CAFE_HEALING', '카페 & 힐링', 'Cafe'],
  ['CULTURE_TEMPLE', '문화 & 사찰', 'Culture'], ['FOOD', '맛집 & 먹거리', 'Food'], ['NATURE_WALK', '자연 & 산책', 'Nature'],
] as const;

const ATMOSPHERES = [['LIVELY', '활기찬', 'Lively'], ['RELAXED', '여유로운', 'Relaxed'], ['SENTIMENTAL', '감성적인', 'Sentimental'], ['ROMANTIC', '낭만적인', 'Romantic']] as const;
const PACES = [['RELAXED', '여유롭게', 'Relaxed'], ['BALANCED', '균형 있게', 'Balanced'], ['PACKED', '알차게', 'Packed']] as const;
// 🔴 택시는 넣지 않는다. 초안의 이동수단 칸이 셋만 받는다 — 화면에만 넣으면
//    고른 값이 조용히 버려진다.
const TRANSPORTS = [['TRANSIT', '대중교통', 'Transit'], ['CAR', '자동차', 'Car'], ['WALK', '도보 위주', 'Mostly walking']] as const;
const BUDGET_STEPS = [10000, 30000, 50000, 100000] as const;
const SCALES = [
  { key: 'localityLevel' as const, ko: '로컬 느낌', en: 'Local feel', lowKo: '관광지', lowEn: 'Touristy', highKo: '동네', highEn: 'Neighborhood' },
  { key: 'quietLevel' as const, ko: '조용함', en: 'Quietness', lowKo: '북적임', lowEn: 'Busy', highKo: '조용함', highEn: 'Quiet' },
  { key: 'touristLevel' as const, ko: '관광지 비중', en: 'Tourist spots', lowKo: '적게', lowEn: 'Fewer', highKo: '많이', highEn: 'More' },
];

function Chip({ label, selected, disabled, onPress }: { label: string; selected: boolean; disabled?: boolean; onPress: () => void }) {
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityState={{ checked: selected, disabled }}
      disabled={disabled}
      onPress={onPress}
      style={[styles.chip, selected && styles.chipOn, disabled && styles.chipOff]}
    >
      <Text weight="bold" color={disabled ? color.text.muted : selected ? color.text.onAction : color.text.heading}>{label}</Text>
    </Pressable>
  );
}

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

/** 답한 내용을 한 줄로. 🔴 안 고른 칸은 적지 않는다 — 「미정」이 답처럼 보인다. */
function summaryOf(key: QuestionKey, draft: PlanDraft, ko: boolean, skipped: boolean): string {
  if (skipped) return ko ? '건너뜀' : 'Skipped';
  const labels = (list: readonly (readonly [string, string, string])[], picked: string[]) =>
    list.filter(([code]) => picked.includes(code)).map(([, k, e]) => (ko ? k : e)).join(' · ');
  switch (key) {
    case 'areas': return labels(AREAS, draft.travelAreas);
    case 'budget': return draft.budgetKrw ? (ko ? `${(draft.budgetKrw / 10000).toLocaleString()}만원` : `₩${draft.budgetKrw.toLocaleString()}`) : '';
    case 'move': {
      const transport = TRANSPORTS.find(([code]) => code === draft.transport);
      const hours = draft.dayStartTime && draft.dayEndTime ? `${draft.dayStartTime}–${draft.dayEndTime}` : '';
      return [hours, transport ? (ko ? transport[1] : transport[2]) : ''].filter(Boolean).join(' · ');
    }
    case 'cats': return labels(CATEGORIES, draft.preferences);
    case 'pace': {
      const pace = PACES.find(([code]) => code === draft.paceLevel);
      return pace ? (ko ? pace[1] : pace[2]) : '';
    }
    case 'moods': return labels(ATMOSPHERES, draft.atmospheres);
    case 'scales': return SCALES.filter((scale) => draft[scale.key] !== null)
      .map((scale) => `${ko ? scale.ko : scale.en} ${draft[scale.key]}`).join(' · ');
    case 'foods': return labels(FOODS, draft.foods);
    case 'aids': {
      const parts: string[] = [];
      if (draft.wheelchair) parts.push(ko ? '휠체어' : 'Wheelchair');
      if (draft.stroller) parts.push(ko ? '유아차' : 'Stroller');
      if (draft.luggage) parts.push(ko ? '큰 짐' : 'Large luggage');
      return parts.length ? parts.join(' · ') : ko ? '해당 없음' : 'None';
    }
    case 'must': return draft.mustVisitPlaces.length
      ? draft.mustVisitPlaces.map((place) => (ko ? place.nameKo : place.nameEn ?? place.nameKo)).join(' · ')
      : ko ? '없음' : 'None';
    default: return '';
  }
}

export default function PlanConditions() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { kind } = useLayout();
  const { draft, ready, update, completeStep } = usePlan();
  const ko = language !== 'en';
  const [state, setState] = useState<QuestionState>(INITIAL_QUESTION_STATE);
  // 🔴 자동 스크롤은 아직 안 넣었다. 화면 껍데기(Screen)가 스크롤 손잡이를 밖으로
  //    안 내주는데, 그걸 고치는 것은 모든 화면에 걸리는 변경이라 이 티켓의 범위 밖이다.
  //    답한 카드가 64px 짜리 한 줄로 접히므로 새 카드는 대체로 같은 자리에 온다.
  //    카드의 y 는 재 두었다 — 손잡이가 생기면 그대로 쓴다.
  const cardTops = useRef<Record<number, number>>({});

  const done = settledCount(draft, state);
  const left = remainingCount(draft, state);
  const finished = allSettled(draft, state);
  const headerSummary = useMemo(() => summarizeStartBar({
    origin: draft.origin, originLat: draft.originLat, originLng: draft.originLng,
    startDate: draft.startDate, endDate: draft.endDate,
    adults: draft.adults, children: draft.children,
  }, ko), [draft.adults, draft.children, draft.endDate, draft.origin, draft.originLat, draft.originLng, draft.startDate, ko]);

  const goNext = (index: number) => {
    const next = nextOpenIndex(draft, { ...state, open: index });
    setState((prev) => ({ ...prev, open: next, editing: null }));
  };

  const skip = (question: PlanQuestion, index: number) => {
    setState((prev) => ({ ...prev, skipped: { ...prev.skipped, [question.key]: true } }));
    goNext(index);
  };

  const toggleIn = (list: string[], code: string, max?: number) => {
    if (list.includes(code)) return list.filter((item) => item !== code);
    if (max && list.length >= max) return list;
    return [...list, code];
  };

  const body = (question: PlanQuestion) => {
    switch (question.key) {
      case 'areas':
        return <View style={styles.chips}>{AREAS.map(([code, k, e]) => (
          <Chip key={code} label={ko ? k : e} selected={draft.travelAreas.includes(code)}
            onPress={() => update({ travelAreas: toggleIn(draft.travelAreas, code) })} />
        ))}</View>;
      case 'budget':
        return (
          <View style={styles.stack}>
            <Text variant="display" weight="bold">
              {ko ? `${((draft.budgetKrw ?? 0) / 10000).toLocaleString()}만원` : `₩${(draft.budgetKrw ?? 0).toLocaleString()}`}
            </Text>
            <View style={styles.chips}>
              {BUDGET_STEPS.map((step) => (
                <Pressable key={step} accessibilityRole="button" onPress={() => update({ budgetKrw: (draft.budgetKrw ?? 0) + step })} style={styles.chip}>
                  <Text weight="bold">+{ko ? `${step / 10000}만` : `${step / 1000}k`}</Text>
                </Pressable>
              ))}
              <Pressable accessibilityRole="button" onPress={() => update({ budgetKrw: 0 })} style={styles.chip}>
                <Text weight="bold" color={color.brand.orange}>{tx('전체 지우기', 'Clear')}</Text>
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
                  <Text variant="caption" color={color.text.muted}>{ko ? k : e}</Text>
                  <TextInput
                    value={draft[field]}
                    onChangeText={(value) => update({ [field]: value } as Partial<PlanDraft>)}
                    keyboardType="number-pad"
                    maxLength={5}
                    placeholder={field === 'dayStartTime' ? '09:00' : '18:00'}
                    placeholderTextColor={color.text.muted}
                    accessibilityLabel={ko ? k : e}
                    style={styles.input}
                  />
                </View>
              ))}
            </View>
            <View style={styles.chips}>{TRANSPORTS.map(([code, k, e]) => (
              <Chip key={code} label={ko ? k : e} selected={draft.transport === code} onPress={() => update({ transport: code })} />
            ))}</View>
          </View>
        );
      case 'cats':
        return <View style={styles.chips}>{CATEGORIES.map(([code, k, e]) => (
          <Chip key={code} label={ko ? k : e} selected={draft.preferences.includes(code)}
            onPress={() => update({ preferences: toggleIn(draft.preferences, code, 3) })} />
        ))}</View>;
      case 'pace':
        return <View style={styles.chips}>{PACES.map(([code, k, e]) => (
          <Chip key={code} label={ko ? k : e} selected={draft.paceLevel === code} onPress={() => update({ paceLevel: code })} />
        ))}</View>;
      case 'moods':
        return <View style={styles.chips}>{ATMOSPHERES.map(([code, k, e]) => (
          <Chip key={code} label={ko ? k : e} selected={draft.atmospheres.includes(code)}
            onPress={() => update({ atmospheres: toggleIn(draft.atmospheres, code) })} />
        ))}</View>;
      case 'scales':
        return <View style={styles.stack}>{SCALES.map((scale) => (
          <View key={scale.key} style={styles.stack}>
            <Text weight="bold">{ko ? scale.ko : scale.en}</Text>
            <Scale
              value={draft[scale.key]}
              onChange={(level) => update({ [scale.key]: level } as Partial<PlanDraft>)}
              lowLabel={ko ? scale.lowKo : scale.lowEn}
              highLabel={ko ? scale.highKo : scale.highEn}
            />
          </View>
        ))}</View>;
      case 'foods':
        return <View style={styles.chips}>{FOODS.map(([code, k, e]) => {
          // 🔴 알레르기·식단과 부딪히는 음식은 고를 수 없게 하고 **왜인지 같이 적는다.**
          //    그냥 흐리게만 두면 사람은 「고장났나」로 읽는다.
          const conflict = conflictingFoodCode(code, draft.allergies, draft.dietTypes);
          return (
            <View key={code} style={styles.foodWrap}>
              <Chip label={ko ? k : e} selected={draft.foods.includes(code)} disabled={conflict !== null}
                onPress={() => update({ foods: toggleIn(draft.foods, code) })} />
              {conflict ? <Text variant="caption" color={color.text.muted}>{tx(...CONFLICT_LABEL_PAIR[conflict.code])}</Text> : null}
            </View>
          );
        })}</View>;
      case 'aids':
        return <View style={styles.stack}>{([
          ['wheelchair', '휠체어를 써요', 'I use a wheelchair'],
          ['stroller', '유아차가 있어요', 'I have a stroller'],
          ['luggage', '큰 짐이 있어요', 'I have large luggage'],
        ] as const).map(([field, k, e]) => (
          <View key={field} style={styles.binaryRow}>
            <Text style={styles.binaryLabel}>{ko ? k : e}</Text>
            <View style={styles.chips}>
              <Chip label={tx('예', 'Yes')} selected={draft[field] === true} onPress={() => update({ [field]: true } as Partial<PlanDraft>)} />
              <Chip label={tx('아니요', 'No')} selected={draft[field] === false} onPress={() => update({ [field]: false } as Partial<PlanDraft>)} />
            </View>
          </View>
        ))}</View>;
      case 'must':
        return (
          <View style={styles.stack}>
            {draft.mustVisitPlaces.length ? (
              <View style={styles.chips}>{draft.mustVisitPlaces.map((place) => (
                <Chip key={place.placeId} label={ko ? place.nameKo : place.nameEn ?? place.nameKo} selected
                  onPress={() => update({ mustVisitPlaces: draft.mustVisitPlaces.filter((item) => item.placeId !== place.placeId) })} />
              ))}</View>
            ) : null}
            {/* 🔴 여기서는 장소를 새로 찾지 않는다. 검색은 기존 화면에 있고, 두 벌을 두면
                한쪽만 고치는 날이 온다. 지금은 고른 것을 보여 주고 빼는 것까지만 한다. */}
            <Text variant="caption" color={color.text.muted}>
              {tx('꼭 가고 싶은 곳이 있으면 여행을 만든 뒤 일정 화면에서 더할 수 있어요.', 'You can add must-visit places from the itinerary screen after your trip is created.')}
            </Text>
          </View>
        );
      default:
        return null;
    }
  };

  if (!ready) return <Screen scroll><Text>{tx('불러오는 중이에요…', 'Loading…')}</Text></Screen>;

  return (
    
      <Screen scroll wide={kind !== 'phone'} style={styles.canvas}>
        <View style={styles.header}>
          <Text variant="display" weight="bold">{tx('여행 조건 알려주기', 'Tell us about your trip')}</Text>
          <Text color={color.text.muted}>{tx('하나씩만 답해 주세요. 답한 만큼 다음 질문이 열려요.', 'One at a time — the next question opens as you answer.')}</Text>
        </View>

        {headerSummary ? (
          <View style={styles.given}>
            <Text variant="caption" color={color.text.muted}>{tx('홈에서 받은 정보', 'From the home screen')}</Text>
            <View style={styles.givenRow}>
              <Text weight="bold" style={styles.givenText}>{headerSummary}</Text>
              <Pressable accessibilityRole="button" onPress={() => router.push('/plan')}>
                <Text variant="caption" weight="bold" color={color.brand.orange}>{tx('수정', 'Edit')}</Text>
              </Pressable>
            </View>
          </View>
        ) : null}

        <View style={styles.progressRow}>
          <Text variant="caption" color={color.text.muted}>{tx(`질문 ${Math.min(state.open + 1, PLAN_QUESTIONS.length)} / ${PLAN_QUESTIONS.length}`, `Question ${Math.min(state.open + 1, PLAN_QUESTIONS.length)} / ${PLAN_QUESTIONS.length}`)}</Text>
          <Text variant="caption" color={color.text.muted}>{tx(`남은 질문 ${left}개`, `${left} left`)}</Text>
        </View>
        <View style={styles.track}><View style={[styles.fill, { width: `${(done / PLAN_QUESTIONS.length) * 100}%` }]} /></View>

        {PLAN_QUESTIONS.map((question, index) => {
          const open = state.editing === question.key || (state.editing === null && index === state.open);
          const past = index < state.open || (state.editing !== null && state.editing !== question.key && index <= state.open);
          if (!open && !past) return null;
          const skipped = Boolean(state.skipped[question.key]);

          if (!open) {
            return (
              <View key={question.key} style={styles.doneRow} onLayout={(event) => { cardTops.current[index] = event.nativeEvent.layout.y; }}>
                <View style={[styles.tick, isSettled(question, draft, state) && styles.tickOn]}>
                  <Text variant="caption" weight="bold" color={isSettled(question, draft, state) ? color.text.onAction : color.text.muted}>✓</Text>
                </View>
                <View style={styles.doneCopy}>
                  <Text variant="caption" color={color.text.muted}>{ko ? question.ko : question.en}</Text>
                  <Text weight="bold" numberOfLines={1}>{summaryOf(question.key, draft, ko, skipped) || tx('안 고름', 'Not chosen')}</Text>
                </View>
                <Pressable accessibilityRole="button" onPress={() => setState((prev) => ({ ...prev, editing: question.key }))}>
                  <Text variant="caption" weight="bold" color={color.brand.orange}>{tx('수정', 'Edit')}</Text>
                </Pressable>
              </View>
            );
          }

          return (
            <View key={question.key} style={styles.card} onLayout={(event) => { cardTops.current[index] = event.nativeEvent.layout.y; }}>
              <View style={styles.cardHead}>
                <View style={styles.cardCopy}>
                  <Text variant="caption" weight="bold" color={color.brand.orange}>{index + 1} / {PLAN_QUESTIONS.length}</Text>
                  <Text variant="title" weight="bold">{ko ? question.ko : question.en}</Text>
                  <Text variant="caption" color={color.text.muted}>{ko ? question.hintKo : question.hintEn}</Text>
                </View>
                {question.skippable ? (
                  <Pressable accessibilityRole="button" onPress={() => skip(question, index)}>
                    <Text variant="caption" weight="bold" color={color.brand.orange}>{tx('건너뛰기', 'Skip')}</Text>
                  </Pressable>
                ) : null}
              </View>

              {body(question)}

              <Button
                label={index === PLAN_QUESTIONS.length - 1 ? tx('입력 완료', 'Done') : tx('다음', 'Next')}
                disabled={!canAdvance(question, draft)}
                onPress={() => {
                  completeStep(index + 1);
                  if (state.editing) setState((prev) => ({ ...prev, editing: null }));
                  else goNext(index);
                }}
              />
            </View>
          );
        })}

        {finished ? (
          <View style={styles.finish}>
            <Text variant="title" weight="bold">{tx('다 됐어요. 이 조건으로 일정을 만들까요?', 'All set — shall we build your itinerary?')}</Text>
            <Button label={tx('이 조건으로 일정 만들기', 'Build my itinerary')} onPress={() => router.push('/plan/confirm')} />
          </View>
        ) : null}
      </Screen>
    
  );
}

const styles = StyleSheet.create({
  // 🔴 시안의 본문 폭은 1200 이다 (PlanFlow.dc.html). Screen 의 wide 는 1440 이라 240px 넓다 (S15P21E201-1245).
  canvas: { maxWidth: 1200 },
  header: { gap: spacing[2], marginTop: spacing[6] },
  given: { gap: spacing[1], marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft },
  givenRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3] },
  givenText: { flex: 1 },
  progressRow: { flexDirection: 'row', justifyContent: 'space-between', marginTop: spacing[4] },
  track: { height: 4, marginTop: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.field, overflow: 'hidden' },
  fill: { height: 4, borderRadius: radius.full, backgroundColor: color.brand.orange },
  card: { gap: spacing[4], marginTop: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  cardHead: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: spacing[3] },
  cardCopy: { flex: 1, gap: spacing[1] },
  doneRow: { minHeight: 64, flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginTop: spacing[2], paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.soft },
  doneCopy: { flex: 1 },
  tick: { width: 28, height: 28, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.field },
  tickOn: { backgroundColor: color.state.success },
  stack: { gap: spacing[3] },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 44, paddingHorizontal: spacing[4], justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.ivory, borderWidth: 1, borderColor: color.surface.border },
  chipOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  chipOff: { backgroundColor: color.surface.subtle, borderColor: color.surface.border },
  foodWrap: { gap: 2 },
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
  finish: { gap: spacing[3], marginTop: spacing[6], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.warm },
});
