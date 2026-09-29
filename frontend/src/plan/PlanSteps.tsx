// 여행 만들기 — 네 단계 + 확인 표(UI 개선 캔버스 ⑤).
//
// 🔴 전에는 질문 일곱을 하나씩 물었는데, 「필수는 셋뿐」이라 적고 날짜·출발지·숙소는 화면 위 칩과 홈 시트에 흩어져 있었다.
//    마지막에야 단추가 잠긴 이유를 알았고, 출발지를 고르면 홈으로 튕겨 나갔다(n5 메모, 직접 눌러 봄).
//    이제 묻는 것은 그대로 두고 «한 화면에 한 가지 덩어리»로 묶는다 —
//      1 언제·누구와 → 2 어디서 출발·어떻게 다닐지 → 3 어디로·얼마나 → 4 어떤 여행(선택) → 이대로 만들까요?(확인 표)
//    이동 보조·꼭 가고 싶은 곳은 확인 표에서 고친다 — 대부분 「없음」이라 질문 한 장씩을 쓰지 않는다.
//
// 🔴 판정(무엇이 필수인가 · 무엇을 보내나)은 이 파일이 하지 않는다. 부르는 쪽(questions.tsx)이 PLAN_QUESTIONS 와
//    서버 요구(날짜·출발지·숙소)로 정해서 넘긴다 — 판정이 두 벌이 되면 단추는 열렸는데 서버가 거절하는 화면이 된다.
import { useState } from 'react';
import { Image, Pressable, StyleSheet, TextInput, View } from 'react-native';

import { GabolleMascot } from '@/components/DongbaekMascot';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { lodgingAreaNote } from '@/home/PlanStartBar';
import { formatDateShort, nightCount, placeEnglishOf, startBarPlaceName } from '@/home/startBarValue';
import { txf } from '@/i18n/format';
import type { LanguageCode } from '@/i18n/languages';
import { budgetForDaily, tripDayCount } from '@/plan/budgetDefault';
import { conditionLabels } from '@/plan/conditionLabels';
import { DateRangeCard } from '@/plan/DateRangeCard';
import { maskTimeInput } from '@/plan/inputMasks';
import { MustVisitSearch } from '@/plan/MustVisitSearch';
import { MAJOR_BUSAN_ORIGINS, RECOMMENDED_LODGING_AREAS, lodgingSnapshotOf, type OriginCandidate } from '@/plan/origins';
import { AREA_OPTIONS, CATEGORY_IMAGES, CATEGORY_OPTIONS, PACE_OPTIONS, TRANSPORT_OPTIONS, effectOf, type PlanOption } from '@/plan/planOptions';
import { dayWindowIssue } from '@/plan/planQuestions';
import { OptionCard } from '@/plan/PlanStepperParts';
import type { PlanDraft } from '@/plan/PlanProvider';

type Tx = (ko: string, en: string) => string;

/** 네 단계 + 확인 표. 자리는 0~4 다(4 = 확인 표). */
export const PLAN_STEP_COUNT = 4;
export const REVIEW_STEP = 4;

/** 단계마다 「다 채웠나」 — 부르는 쪽이 서버 요구로 계산해 넘긴다. */
export type StepReadiness = {
  datesMissing: boolean;
  /** 출발일만 찍었다 — 「돌아오는 날을 골라 주세요」. */
  endMissing: boolean;
  originMissing: boolean;
  lodgingMissing: boolean;
  transportMissing: boolean;
  /** 하루 시간을 못 읽거나 끝이 시작보다 이르다(dayWindowIssue). */
  hoursInvalid: boolean;
  areasMissing: boolean;
  budgetMissing: boolean;
};

/** 이 단계를 넘어갈 수 있나 — 선택 단계(4)와 확인 표는 늘 된다. */
export function stepComplete(step: number, r: StepReadiness): boolean {
  switch (step) {
    case 0: return !r.datesMissing;
    case 1: return !r.originMissing && !r.lodgingMissing && !r.transportMissing && !r.hoursInvalid;
    case 2: return !r.areasMissing && !r.budgetMissing;
    default: return true;
  }
}

/** 처음으로 덜 채운 필수 단계 — 저장해 둔 자리가 그 뒤여도 여기서 멈춘다(빈 필수 단계를 건너뛴 채 확인 표에 서지 않게). */
export function firstIncompleteStep(r: StepReadiness): number {
  for (let step = 0; step < 3; step += 1) if (!stepComplete(step, r)) return step;
  return REVIEW_STEP;
}

/** 잠긴 「다음」이 말할 이유. 넘어갈 수 있으면 null. */
export function stepBlocker(step: number, r: StepReadiness, tx: Tx): string | null {
  if (step === 0) {
    if (r.endMissing) return tx('돌아오는 날을 골라 주세요', 'Pick the day you come back');
    if (r.datesMissing) return tx('여행 날짜를 골라 주세요', 'Pick your trip dates');
  }
  if (step === 1) {
    if (r.originMissing) return tx('출발지를 골라 주세요', 'Pick a starting point');
    if (r.lodgingMissing) return tx('숙소를 골라 주세요', 'Pick where you will stay');
    if (r.transportMissing) return tx('이동수단을 골라 주세요', 'Pick how you will get around');
    if (r.hoursInvalid) return tx('하루 여행 시간을 확인해 주세요', 'Check your daily hours');
  }
  if (step === 2) {
    if (r.areasMissing) return tx('지역을 하나 이상 골라 주세요', 'Pick at least one area');
    if (r.budgetMissing) return tx('예산을 정해 주세요', 'Set a budget');
  }
  return null;
}

const labelOf = (option: PlanOption, tx: Tx) => tx(option[1], option[2]);
const subOf = (option: PlanOption, tx: Tx) => tx(option[3], option[4]);
const won = (value: number, tx: Tx) => tx(`${(value / 10000).toLocaleString()}만원`, `₩${value.toLocaleString()}`);
const DAILY_BUDGET_PRESETS = [
  { perDay: 30000, ko: '알뜰', en: 'Frugal' },
  { perDay: 50000, ko: '보통', en: 'Standard' },
  { perDay: 80000, ko: '넉넉', en: 'Relaxed' },
] as const;
const BUDGET_UNIT = 10000;
const MAX_PEOPLE = 9;
/** 출발지 없는 사람에게 먼저 내미는 곳 — 여행을 시작하는 자리(역·공항·터미널). 나머지는 「다른 곳 찾기」. */
const ORIGIN_SHORTLIST = ['major-busan-station', 'major-gimhae-airport', 'major-seomyeon', 'major-haeundae'];

export type PlanStepsProps = {
  step: number;
  draft: PlanDraft;
  update: (patch: Partial<PlanDraft>) => void;
  tx: Tx;
  language: LanguageCode;
  readiness: StepReadiness;
  /** 4단계(선택)를 건너뛰었나 — 확인 표에 「건너뜀」이라 적는다. */
  styleSkipped: boolean;
  /** 이동 보조 칸의 자료 부족 안내(S15P21E201-1855). 못 물어봤으면 null. */
  accessibilityCounts: { totalPlaceCount: number; placeCount: number } | null;
  goTo: (step: number) => void;
  onSkipStyle: () => void;
  /** 출발지·숙소 검색 시트(PlanStartBar) — 이름으로 찾을 때. */
  onSearchPlace: (section: 'origin' | 'lodging') => void;
  onEditConditions: () => void;
  onOpenMenuScan: () => void;
};

// ── 1. 언제 · 누구와 ────────────────────────────────────────────────────────

function WhenStep({ draft, update, tx }: Pick<PlanStepsProps, 'draft' | 'update' | 'tx'>) {
  const people = (field: 'adults' | 'children', delta: number) => {
    const next = { adults: draft.adults, children: draft.children, [field]: draft[field] + delta };
    // 어른은 최소 한 명(서버 규칙), 합쳐 아홉까지 — 홈 시작 바와 같다.
    if (next.adults < 1 || next.children < 0 || next.adults + next.children > MAX_PEOPLE) return;
    update({ adults: next.adults, children: next.children, travelers: next.adults + next.children });
  };
  return (
    <View style={styles.stack}>
      <DateRangeCard embedded value={{ startDate: draft.startDate, endDate: draft.endDate }} onChange={(next) => update({ startDate: next.startDate, endDate: next.endDate })} onDone={() => undefined} tx={tx} />
      <View style={styles.card}>
        {([['adults', tx('성인', 'Adults'), tx('만 13세 이상 · 최소 1명', 'Age 13+ · at least 1'), 1], ['children', tx('어린이', 'Children'), tx('만 2~12세', 'Age 2–12'), 0]] as const).map(([field, label, hint, min], index) => (
          <View key={field} style={[styles.personRow, index > 0 && styles.rowDivider]}>
            <View style={styles.grow}>
              <Text weight="bold">{label}</Text>
              <Text variant="caption" color={color.text.muted}>{hint}</Text>
            </View>
            <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 줄이기', 'Fewer %s', label)} disabled={draft[field] <= min} onPress={() => people(field, -1)} style={[styles.roundButton, draft[field] <= min && styles.dim]}>
              <Text variant="title" weight="bold">−</Text>
            </Pressable>
            <Text variant="title" weight="bold" style={styles.count}>{draft[field]}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 늘리기', 'More %s', label)} disabled={draft.adults + draft.children >= MAX_PEOPLE} onPress={() => people(field, 1)} style={[styles.roundButton, draft.adults + draft.children >= MAX_PEOPLE && styles.dim]}>
              <Text variant="title" weight="bold">+</Text>
            </Pressable>
          </View>
        ))}
      </View>
    </View>
  );
}

// ── 2. 어디서 출발해서 · 어떻게 다닐까요 ─────────────────────────────────────

function HowStep({ draft, update, tx, language, onSearchPlace }: Pick<PlanStepsProps, 'draft' | 'update' | 'tx' | 'language' | 'onSearchPlace'>) {
  const [editingHours, setEditingHours] = useState(false);
  const nights = draft.startDate && draft.endDate ? nightCount(draft.startDate, draft.endDate) : 0;
  const hasOrigin = draft.originLat !== null && draft.originLng !== null;
  const hasLodging = draft.lodgingLat !== null && draft.lodgingLng !== null;
  const pickOrigin = (candidate: OriginCandidate) => update({ origin: candidate.name, originEnglish: placeEnglishOf(candidate), originLat: candidate.lat, originLng: candidate.lng });
  // 추천 동네는 장소 스냅샷을 싣지 않는다(lodgingSnapshotOf — 동네는 장소가 아니다). 홈 시작 바와 같은 값이다.
  const pickLodging = (candidate: OriginCandidate) => update({ lodging: candidate.name, lodgingEnglish: placeEnglishOf(candidate), lodgingLat: candidate.lat, lodgingLng: candidate.lng, lodgingPlace: lodgingSnapshotOf(candidate) });
  const pickedArea = RECOMMENDED_LODGING_AREAS.find((area) => hasLodging && area.name === draft.lodging && area.lat === draft.lodgingLat);
  const issue = dayWindowIssue(draft);
  const shortlist = ORIGIN_SHORTLIST.map((id) => MAJOR_BUSAN_ORIGINS.find((origin) => origin.externalId === id)).filter((origin): origin is OriginCandidate => Boolean(origin));
  return (
    <View style={styles.stack}>
      <View style={styles.card}>
        {/* 출발지 */}
        <View style={styles.legRow}>
          <View style={styles.legDot} />
          <View style={styles.grow}>
            <Text variant="caption" color={color.text.muted}>{tx('출발지', 'Starting point')}</Text>
            {hasOrigin ? <Text weight="bold" numberOfLines={1}>{startBarPlaceName(draft.origin, draft.originEnglish, language)}</Text> : <Text weight="bold" color={color.text.muted}>{tx('어디서 출발하세요?', 'Where do you start?')}</Text>}
          </View>
          <Pressable accessibilityRole="button" onPress={() => onSearchPlace('origin')} style={styles.textButton}>
            <Text variant="caption" weight="bold" color={color.text.muted}>{hasOrigin ? tx('바꾸기', 'Change') : tx('다른 곳 찾기', 'Search')}</Text>
          </Pressable>
        </View>
        {!hasOrigin ? (
          <View style={[styles.chips, styles.indent]}>
            {shortlist.map((origin) => (
              <Pressable key={origin.externalId} accessibilityRole="button" onPress={() => pickOrigin(origin)} style={({ pressed }) => [styles.chip, pressed && styles.pressed]}>
                <Text variant="caption" weight="bold" numberOfLines={1}>{startBarPlaceName(origin.name, placeEnglishOf(origin), language)}</Text>
              </Pressable>
            ))}
          </View>
        ) : null}
        {/* 숙소 — 1박 이상이면 필요하다(S15P21E201-1584). 당일치기면 묻지 않는다. */}
        <View style={styles.legRow}>
          <View style={[styles.legDot, styles.legDotHollow]} />
          <View style={styles.grow}>
            <Text variant="caption" color={color.text.muted}>
              {nights > 0 ? txf(tx, '숙소 · %s박이라 필요해요', 'Stay · needed for %s night(s)', nights) : tx('숙소 · 당일치기라 안 골라도 돼요', 'Stay · not needed for a day trip')}
            </Text>
            {hasLodging ? <Text weight="bold" numberOfLines={1}>{startBarPlaceName(draft.lodging, draft.lodgingEnglish, language)}</Text> : null}
          </View>
          {nights > 0 || hasLodging ? (
            <Pressable accessibilityRole="button" onPress={() => onSearchPlace('lodging')} style={styles.textButton}>
              <Text variant="caption" weight="bold" color={color.text.muted}>{hasLodging ? tx('바꾸기', 'Change') : tx('이름으로 찾기', 'Search by name')}</Text>
            </Pressable>
          ) : null}
        </View>
        {nights > 0 && (!hasLodging || pickedArea) ? (
          <View style={[styles.stackTight, styles.indent]}>
            <Text variant="caption" color={color.text.muted}>{tx('아직 안 정했다면 동네만 골라도 돼요', 'Not booked yet? Just pick a neighborhood')}</Text>
            <View style={styles.chips}>
              {RECOMMENDED_LODGING_AREAS.map((area) => {
                const selected = pickedArea?.externalId === area.externalId;
                return (
                  <Pressable key={area.externalId} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => pickLodging(area)} style={({ pressed }) => [styles.chip, selected && styles.chipOn, pressed && styles.pressed]}>
                    <Text variant="caption" weight="bold" numberOfLines={1} color={selected ? color.text.onAction : color.text.heading}>{startBarPlaceName(area.name, placeEnglishOf(area), language)}</Text>
                  </Pressable>
                );
              })}
            </View>
            {pickedArea ? <Text variant="caption" color={color.text.body}>{lodgingAreaNote(pickedArea, tx)}</Text> : null}
          </View>
        ) : null}
      </View>

      {/* 이동수단 */}
      <View style={styles.stackTight}>
        <View style={styles.labelRow}><Text weight="bold">{tx('이동수단', 'Getting around')}</Text><View style={styles.requiredTag}><Text variant="micro" weight="bold">{tx('필수', 'Required')}</Text></View></View>
        <View accessibilityRole="radiogroup" style={styles.chips}>
          {TRANSPORT_OPTIONS.map((option) => {
            const selected = draft.transport === option[0];
            return (
              <Pressable key={option[0]} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => update({ transport: option[0] as PlanDraft['transport'] })} style={({ pressed }) => [styles.chipOnCanvas, selected && styles.chipOn, pressed && styles.pressed]}>
                <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.heading}>{labelOf(option, tx)}</Text>
              </Pressable>
            );
          })}
        </View>
      </View>

      {/* 하루 여행 시간 — 대부분 기본값(09:00–21:00)이라 한 줄로 두고 「바꾸기」로 편다. */}
      <View style={styles.card}>
        <View style={styles.legRow}>
          <View style={styles.grow}>
            <View style={styles.labelRow}><Text variant="caption" color={color.text.muted}>{tx('하루 여행 시간', 'Daily hours')}</Text><View style={styles.requiredTag}><Text variant="micro" weight="bold">{tx('필수', 'Required')}</Text></View></View>
            <Text weight="bold">{`${draft.dayStartTime || '--:--'} – ${draft.dayEndTime || '--:--'}`}</Text>
          </View>
          <Pressable accessibilityRole="button" accessibilityState={{ expanded: editingHours }} onPress={() => setEditingHours((open) => !open)} style={styles.textButton}>
            <Text variant="caption" weight="bold" color={color.text.muted}>{editingHours ? tx('닫기', 'Done') : tx('바꾸기', 'Change')}</Text>
          </Pressable>
        </View>
        {editingHours || issue ? (
          <View style={styles.stackTight}>
            <View style={styles.timeRow}>
              {([['dayStartTime', '시작', 'Start', '09:00'], ['dayEndTime', '종료', 'End', '21:00']] as const).map(([field, k, e, placeholder]) => (
                <View key={field} style={styles.grow}>
                  <Text variant="caption" color={color.text.muted}>{tx(k, e)}</Text>
                  <TextInput value={draft[field]} onChangeText={(value) => update({ [field]: maskTimeInput(value) } as Partial<PlanDraft>)} keyboardType="number-pad" maxLength={5} placeholder={placeholder} placeholderTextColor={color.text.muted} accessibilityLabel={tx(k, e)} style={styles.input} />
                </View>
              ))}
            </View>
            {issue === 'FORMAT' ? <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{tx('시각을 09:00 처럼 네 자리로 적어 주세요.', 'Enter the time as four digits, like 09:00.')}</Text> : null}
            {issue === 'ORDER' ? <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{tx('종료 시각은 시작 시각보다 늦어야 해요.', 'The end time must be later than the start time.')}</Text> : null}
          </View>
        ) : null}
      </View>
    </View>
  );
}

// ── 3. 어디로 가고 · 얼마나 쓸까요 ────────────────────────────────────────────

function WhereStep({ draft, update, tx }: Pick<PlanStepsProps, 'draft' | 'update' | 'tx'>) {
  const total = draft.budgetKrw ?? 0;
  const days = tripDayCount(draft.startDate, draft.endDate);
  const [typing, setTyping] = useState<string | null>(null);
  const toggleArea = (code: string) => update({ travelAreas: draft.travelAreas.includes(code) ? draft.travelAreas.filter((item) => item !== code) : [...draft.travelAreas, code] });
  const commitTyped = () => {
    if (typing === null) return;
    const manwon = Number(typing.replace(/[^0-9]/g, ''));
    if (Number.isFinite(manwon) && manwon >= 1) update({ budgetKrw: manwon * BUDGET_UNIT });
    setTyping(null);
  };
  return (
    <View style={styles.stack}>
      <Text variant="caption" weight="bold" color={color.text.muted}>{tx('지역 · 여러 곳 골라도 돼요', 'Areas · pick as many as you like')}</Text>
      <View style={styles.grid2}>
        {AREA_OPTIONS.map((option) => (
          <View key={option[0]} style={styles.cell2}>
            <OptionCard label={labelOf(option, tx)} sub={subOf(option, tx)} selected={draft.travelAreas.includes(option[0])} onPress={() => toggleArea(option[0])} />
          </View>
        ))}
      </View>
      <EffectNote text={effectOf('areas', draft, tx)} tx={tx} />

      <View style={styles.card}>
        <View style={styles.rowBetween}>
          <Text weight="bold">{tx('총예산', 'Total budget')}</Text>
          <Text variant="caption" color={color.text.muted}>{txf(tx, '숙박비 빼고 · %s명 · %s일', 'Excl. stays · %s people · %s days', Math.max(1, draft.adults + draft.children), days)}</Text>
        </View>
        <View accessibilityRole="radiogroup" style={styles.presets}>
          {DAILY_BUDGET_PRESETS.map((preset) => {
            const presetTotal = budgetForDaily(draft, preset.perDay);
            const selected = total === presetTotal;
            return (
              <Pressable key={preset.perDay} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => update({ budgetKrw: presetTotal })} style={({ pressed }) => [styles.preset, selected && styles.chipOn, pressed && styles.pressed]}>
                <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.heading}>{tx(preset.ko, preset.en)}</Text>
                <Text weight="bold" color={selected ? color.text.onAction : color.text.heading}>{won(presetTotal, tx)}</Text>
                <Text variant="micro" color={selected ? color.text.onDarkMuted : color.text.muted}>{txf(tx, '1인 하루 %s', '%s / person / day', won(preset.perDay, tx))}</Text>
              </Pressable>
            );
          })}
        </View>
        <View style={styles.rowBetween}>
          {/* 직접 적기 — 만원 단위로 적는다. 1만원 밑은 서버가 안 받는다. */}
          <View style={styles.typeRow}>
            <Text variant="caption" color={color.text.muted}>{tx('직접 적기', 'Enter amount')}</Text>
            <TextInput
              accessibilityLabel={tx('총예산 직접 적기 (만원)', 'Enter total budget')}
              value={typing ?? String(Math.round(total / BUDGET_UNIT))}
              onFocus={() => setTyping(String(Math.round(total / BUDGET_UNIT)))}
              onChangeText={(value) => setTyping(value.replace(/[^0-9]/g, '').slice(0, 4))}
              onBlur={commitTyped}
              onSubmitEditing={commitTyped}
              keyboardType="number-pad"
              style={styles.budgetInput}
            />
            <Text variant="caption" weight="bold">{tx('만원', '× ₩10,000')}</Text>
          </View>
          <View style={styles.chips}>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('예산 1만원 줄이기', 'Lower budget by ₩10,000')} disabled={total <= BUDGET_UNIT} onPress={() => update({ budgetKrw: Math.max(BUDGET_UNIT, total - BUDGET_UNIT) })} style={[styles.chip, total <= BUDGET_UNIT && styles.dim]}>
              <Text variant="caption" weight="bold">{tx('−1만', '−10k')}</Text>
            </Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('예산 1만원 늘리기', 'Raise budget by ₩10,000')} onPress={() => update({ budgetKrw: total + BUDGET_UNIT })} style={styles.chip}>
              <Text variant="caption" weight="bold">{tx('+1만', '+10k')}</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </View>
  );
}

// ── 4. 어떤 여행이 좋아요 (선택) ─────────────────────────────────────────────

function StyleStep({ draft, update, tx }: Pick<PlanStepsProps, 'draft' | 'update' | 'tx'>) {
  const picked = draft.preferences;
  const toggle = (code: string) => {
    if (picked.includes(code)) update({ preferences: picked.filter((item) => item !== code) });
    else if (picked.length < 3) update({ preferences: [...picked, code] });
  };
  const six = CATEGORY_OPTIONS.filter(([code]) => code !== 'FESTIVAL_EVENT');
  const festival = CATEGORY_OPTIONS.find(([code]) => code === 'FESTIVAL_EVENT');
  const full = picked.length >= 3;
  return (
    <View style={styles.stack}>
      <View style={styles.rowBetween}>
        <Text weight="bold">{tx('여행 카테고리', 'Trip categories')}</Text>
        <Text variant="caption" weight="bold" color={full ? color.text.heading : color.text.body}>{txf(tx, '최대 3개 · %s / 3', 'Up to 3 · %s / 3', picked.length)}</Text>
      </View>
      {/* 여섯은 3×2 로 한눈에 — 옆으로 넘기지 않는다. 축제는 «여행 날짜에 여는 것만» 들어가는 다른 성격이라 아래에 넓게 따로. */}
      <View style={styles.grid3}>
        {six.map((option) => (
          <View key={option[0]} style={styles.cell3}>
            <OptionCard image={CATEGORY_IMAGES[option[0]]} label={labelOf(option, tx)} sub="" selected={picked.includes(option[0])} disabled={full && !picked.includes(option[0])} onPress={() => toggle(option[0])} />
          </View>
        ))}
      </View>
      {festival ? (
        <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: picked.includes(festival[0]), disabled: full && !picked.includes(festival[0]) }} disabled={full && !picked.includes(festival[0])} onPress={() => toggle(festival[0])} style={({ pressed }) => [styles.festival, picked.includes(festival[0]) && styles.festivalOn, full && !picked.includes(festival[0]) && styles.dim, pressed && styles.pressed]}>
          {CATEGORY_IMAGES[festival[0]] ? <Image source={CATEGORY_IMAGES[festival[0]]} resizeMode="cover" accessibilityLabel="" style={styles.festivalImage} /> : null}
          <View style={styles.grow}>
            <Text weight="bold">{labelOf(festival, tx)}</Text>
            <Text variant="caption" color={color.text.muted}>{subOf(festival, tx)}</Text>
          </View>
          {picked.includes(festival[0]) ? <View style={styles.checkDot}><Text variant="micro" weight="bold" color={color.text.onAction}>✓</Text></View> : null}
        </Pressable>
      ) : null}

      <Text weight="bold" style={styles.sectionGap}>{tx('여행 기분', 'Trip pace')}</Text>
      <View accessibilityRole="radiogroup" style={styles.segment}>
        {PACE_OPTIONS.map((option) => {
          const selected = draft.paceLevel === option[0];
          return (
            <Pressable key={option[0]} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => update({ paceLevel: option[0] as PlanDraft['paceLevel'] })} style={({ pressed }) => [styles.segmentItem, selected && styles.segmentOn, pressed && styles.pressed]}>
              <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.heading}>{labelOf(option, tx)}</Text>
              <Text variant="micro" color={selected ? color.text.onDarkMuted : color.text.muted} numberOfLines={2} style={styles.center}>{tx(option[3].split(' · ')[0], option[4].split(' · ')[0])}</Text>
            </Pressable>
          );
        })}
      </View>
      <EffectNote text={effectOf(draft.preferences.length ? 'cats' : 'pace', draft, tx)} tx={tx} />
    </View>
  );
}

function EffectNote({ text, tx }: { text: string | null; tx: Tx }) {
  if (!text) return null;
  return (
    <View style={styles.effect}>
      <GabolleMascot state="open" still style={styles.effectMascot} />
      <View style={styles.grow}>
        <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('이렇게 반영돼요', 'What this changes')}</Text>
        <Text variant="caption" color={color.text.heading}>{text}</Text>
      </View>
    </View>
  );
}

// ── 확인 표 — 이대로 만들까요? ───────────────────────────────────────────────

function ReviewStep({ draft, update, tx, language, styleSkipped, accessibilityCounts, goTo, onEditConditions, onOpenMenuScan }: PlanStepsProps) {
  const [mustOpen, setMustOpen] = useState(false);
  const nights = draft.startDate && draft.endDate ? nightCount(draft.startDate, draft.endDate) : 0;
  const labels = (list: readonly PlanOption[], codes: string[]) => list.filter(([code]) => codes.includes(code)).map((option) => labelOf(option, tx)).join(' · ');
  const transport = TRANSPORT_OPTIONS.find(([code]) => code === draft.transport);
  const pace = PACE_OPTIONS.find(([code]) => code === draft.paceLevel);
  const people = [txf(tx, '성인 %s', 'Adults %s', draft.adults), draft.children ? txf(tx, '어린이 %s', 'Children %s', draft.children) : null].filter(Boolean).join(' · ');
  const nightsLabel = nights > 0 ? tx(`${nights}박 ${nights + 1}일`, `${nights} night${nights === 1 ? '' : 's'}`) : tx('당일치기', 'Day trip');
  const kept = conditionLabels(draft, tx, { withAids: false });
  const rows: { key: string; label: string; value: string; step: number }[] = [
    { key: 'origin', label: tx('출발', 'From'), value: startBarPlaceName(draft.origin, draft.originEnglish, language) || '—', step: 1 },
    ...(nights > 0 || draft.lodging ? [{ key: 'lodging', label: tx('숙소', 'Stay'), value: startBarPlaceName(draft.lodging, draft.lodgingEnglish, language) || '—', step: 1 }] : []),
    { key: 'dates', label: tx('날짜', 'Dates'), value: draft.startDate ? (draft.endDate && draft.endDate !== draft.startDate ? `${formatDateShort(draft.startDate, tx)} – ${formatDateShort(draft.endDate, tx)}` : formatDateShort(draft.startDate, tx)) : '—', step: 0 },
    { key: 'people', label: tx('기간 · 인원', 'Length · people'), value: `${nightsLabel} · ${people}`, step: 0 },
    { key: 'areas', label: tx('지역', 'Areas'), value: labels(AREA_OPTIONS, draft.travelAreas) || '—', step: 2 },
    { key: 'budget', label: tx('총예산', 'Budget'), value: draft.budgetKrw ? `${won(draft.budgetKrw, tx)} · ${tx('숙박 빼고', 'excl. stays')}` : '—', step: 2 },
    { key: 'move', label: tx('여행 기분 · 이동', 'Pace · transport'), value: [pace ? labelOf(pace, tx) : styleSkipped ? tx('건너뜀', 'Skipped') : null, transport ? labelOf(transport, tx) : null].filter(Boolean).join(' · ') || '—', step: pace || styleSkipped ? 3 : 1 },
    { key: 'hours', label: tx('하루 여행 시간', 'Daily hours'), value: `${draft.dayStartTime} – ${draft.dayEndTime}`, step: 1 },
    { key: 'cats', label: tx('카테고리', 'Categories'), value: labels(CATEGORY_OPTIONS, draft.preferences) || (styleSkipped ? tx('건너뜀', 'Skipped') : tx('없음', 'None')), step: 3 },
  ];
  return (
    <View style={styles.stack}>
      {/* 승차권 미리보기 — 칸을 누르면 그 단계로 가서 고친다. */}
      <View style={styles.pass}>
        <View style={styles.passHead}><Text variant="micro" weight="bold" color={color.text.onDarkMuted} style={styles.passTitle}>TRIP PASS · {tx('미리보기', 'Preview')}</Text></View>
        {rows.map((row, index) => (
          <Pressable key={row.key} accessibilityRole="button" accessibilityLabel={txf(tx, '%s 고치기', 'Edit %s', row.label)} onPress={() => goTo(row.step)} style={({ pressed }) => [styles.passRow, index > 0 && styles.rowDivider, pressed && styles.pressed]}>
            <Text variant="caption" color={color.text.muted} style={styles.passLabel}>{row.label}</Text>
            <Text variant="caption" weight="bold" style={styles.passValue}>{row.value}</Text>
          </Pressable>
        ))}
      </View>

      {/* 내 여행 조건 — 로그인 뒤 한 번 저장하면 다시 안 묻는다. 여기서는 묻지 않고 보여 주고 「바꾸기」만. */}
      <View style={styles.card}>
        <View style={styles.legRow}>
          <View style={styles.grow}>
            <Text variant="caption" color={color.text.muted}>{tx('내 여행 조건 · 저장해 둔 값', 'My travel conditions · saved')}</Text>
            {/* 조건마다 한 알 — 한 줄로 이으면 일본어·중국어에서 낱말 가운데가 끊겼다(「急 / な坂道」). */}
            {kept.length ? (
              <View style={styles.tags}>
                {kept.map((label) => <View key={label} style={styles.tag}><Text variant="caption" weight="bold" numberOfLines={1}>{label}</Text></View>)}
              </View>
            ) : <Text weight="bold">{tx('따로 정한 조건 없음', 'No conditions set')}</Text>}
          </View>
          <Pressable accessibilityRole="button" onPress={onEditConditions} style={styles.textButton}>
            <Text variant="caption" weight="bold" color={color.text.muted}>{tx('바꾸기', 'Change')}</Text>
          </Pressable>
        </View>
        {/* 🔴 알레르기는 일부러 일정 조건에서 뺐다(S15P21E201-1497) — 장소 자료에 알레르기 표식이 없고, 추정이 틀리면 사람이 다친다. */}
        <Pressable accessibilityRole="link" onPress={onOpenMenuScan} style={styles.allergy}>
          <Text variant="caption" color={color.text.muted}>{tx('알레르기는 일정 조건에 넣지 않아요 — 식당에서 메뉴판으로 확인해요', 'Allergies are not part of the plan — check the menu at each restaurant')}</Text>
          <Text variant="caption" weight="bold" color={color.text.heading}>{tx('메뉴판 읽기 ›', 'Menu reader ›')}</Text>
        </Pressable>
      </View>

      {/* 이동 보조 — 여행마다 달라서 이 여행에만 저장한다. */}
      <View style={styles.card}>
        <Text variant="caption" color={color.text.muted}>{tx('이번 여행 이동 보조', 'Mobility aids for this trip')}</Text>
        <View style={styles.chips}>
          {([['wheelchair', '휠체어', 'Wheelchair'], ['stroller', '유아차', 'Stroller']] as const).map(([field, ko, en]) => {
            const on = draft[field] === true;
            return (
              <Pressable key={field} accessibilityRole="checkbox" accessibilityState={{ checked: on }} onPress={() => update({ [field]: !on } as Partial<PlanDraft>)} style={({ pressed }) => [styles.chip, on && styles.chipOn, pressed && styles.pressed]}>
                <Text variant="caption" weight="bold" color={on ? color.text.onAction : color.text.heading}>{tx(ko, en)}</Text>
              </Pressable>
            );
          })}
        </View>
        {(draft.wheelchair || draft.stroller) && accessibilityCounts ? (
          <Text variant="caption" color={color.text.muted}>{txf(tx, '지금 접근성을 확인한 곳은 %s곳 중 %s곳이에요. 고르시면 나머지는 「아직 확인되지 않았어요」로 나와요 — 못 간다는 뜻은 아니에요.', 'Of %s places, we have checked access for %s so far. The rest will show as "not checked yet" — that does not mean you cannot go.', accessibilityCounts.totalPlaceCount, accessibilityCounts.placeCount)}</Text>
        ) : null}
      </View>

      {/* 꼭 가고 싶은 곳 */}
      <View style={styles.card}>
        <View style={styles.legRow}>
          <View style={styles.grow}>
            <Text variant="caption" color={color.text.muted}>{tx('꼭 가고 싶은 곳', 'Must-visit places')}</Text>
            <Text weight="bold">{draft.mustVisitPlaces.length ? draft.mustVisitPlaces.map((place) => (language === 'ko' ? place.nameKo : place.nameEn ?? place.nameKo)).join(' · ') : tx('없음', 'None')}</Text>
          </View>
          <Pressable accessibilityRole="button" accessibilityState={{ expanded: mustOpen }} onPress={() => setMustOpen((open) => !open)} style={styles.textButton}>
            <Text variant="caption" weight="bold" color={color.text.muted}>{mustOpen ? tx('닫기', 'Done') : tx('더하기', 'Add')}</Text>
          </Pressable>
        </View>
        {mustOpen ? <MustVisitSearch picked={draft.mustVisitPlaces} onChange={(next) => update({ mustVisitPlaces: next })} tx={tx} ko={language === 'ko'} /> : null}
      </View>
    </View>
  );
}

// ── 머리 · 몸 ─────────────────────────────────────────────────────────────

export function planStepTitle(step: number, tx: Tx): { eyebrow: string; title: string } {
  switch (step) {
    case 0: return { eyebrow: tx('필수', 'Required'), title: tx('언제, 누구와 가세요?', 'When, and with whom?') };
    case 1: return { eyebrow: tx('필수', 'Required'), title: tx('어디서 출발해서, 어떻게 다닐까요?', 'Where do you start, and how will you get around?') };
    case 2: return { eyebrow: tx('필수', 'Required'), title: tx('어디로 가고, 얼마나 쓸까요?', 'Where to, and how much to spend?') };
    case 3: return { eyebrow: tx('선택 · 건너뛰어도 돼요', 'Optional · you can skip'), title: tx('어떤 여행이 좋아요?', 'What kind of trip?') };
    default: return { eyebrow: tx('확인', 'Review'), title: tx('이대로 만들까요?', 'Build it like this?') };
  }
}

/** 단계의 몸 — 머리(단계 표시·제목)와 아래 단추는 부르는 쪽이 그린다(폰·넓은 화면이 모양이 달라서). */
export function PlanStepBody(props: PlanStepsProps) {
  switch (props.step) {
    case 0: return <WhenStep {...props} />;
    case 1: return <HowStep {...props} />;
    case 2: return <WhereStep {...props} />;
    case 3: return <StyleStep {...props} />;
    default: return <ReviewStep {...props} />;
  }
}

/** 네 칸 진행 막대 — 확인 표에서는 넷 다 찬다. */
export function StepBar({ step, tx }: { step: number; tx: Tx }) {
  const filled = Math.min(step + 1, PLAN_STEP_COUNT);
  return (
    <View accessibilityRole="progressbar" accessibilityLabel={txf(tx, '%s / 4 단계', 'Step %s of 4', filled)} style={styles.bar}>
      {Array.from({ length: PLAN_STEP_COUNT }, (_, index) => <View key={index} style={[styles.barCell, index < filled && styles.barCellOn]} />)}
    </View>
  );
}


const styles = StyleSheet.create({
  stack: { gap: spacing[3] },
  stackTight: { gap: spacing[2] },
  grow: { flex: 1, minWidth: 0, gap: 2 },
  center: { textAlign: 'center' },
  card: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  rowBetween: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], flexWrap: 'wrap' },
  rowDivider: { borderTopWidth: 1, borderTopColor: color.surface.border },
  personRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 56 },
  roundButton: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  count: { width: 24, textAlign: 'center' },
  dim: { opacity: 0.4 },
  pressed: { opacity: 0.8 },
  legRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 44 },
  legDot: { width: 12, height: 12, borderRadius: radius.full, backgroundColor: color.action.secondary, marginHorizontal: 4 },
  legDotHollow: { backgroundColor: 'transparent', borderWidth: 2.5, borderColor: color.action.secondary },
  indent: { marginLeft: 32 },
  textButton: { minHeight: 44, justifyContent: 'center', paddingLeft: spacing[2] },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { maxWidth: '100%', minHeight: 36, justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.soft },
  chipOnCanvas: { maxWidth: '100%', minHeight: 36, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card },
  chipOn: { backgroundColor: color.action.secondary },
  labelRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  requiredTag: { paddingHorizontal: 7, paddingVertical: 1, borderRadius: radius.full, backgroundColor: color.surface.soft },
  timeRow: { flexDirection: 'row', gap: spacing[3] },
  input: { minHeight: 48, paddingHorizontal: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, color: color.text.heading },
  grid2: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  cell2: { width: '48.5%' },
  grid3: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  cell3: { flexBasis: '30%', flexGrow: 1, minWidth: 0 },
  presets: { flexDirection: 'row', gap: spacing[2] },
  preset: { flex: 1, minWidth: 0, minHeight: 72, gap: 2, padding: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  typeRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  budgetInput: { width: 64, minHeight: 36, paddingHorizontal: spacing[2], borderBottomWidth: 1.5, borderBottomColor: color.surface.field, color: color.text.heading, fontWeight: '700', textAlign: 'right' },
  festival: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 76, paddingRight: spacing[3], borderRadius: radius.md, overflow: 'hidden', backgroundColor: color.surface.card, borderWidth: 1.5, borderColor: 'transparent' },
  festivalOn: { borderColor: color.action.outline },
  festivalImage: { width: 112, height: 76 },
  checkDot: { width: 22, height: 22, borderRadius: radius.full, backgroundColor: color.action.outline, alignItems: 'center', justifyContent: 'center' },
  sectionGap: { marginTop: spacing[2] },
  segment: { flexDirection: 'row', gap: 4, padding: 4, borderRadius: radius.lg, backgroundColor: color.surface.soft },
  segmentItem: { flex: 1, minWidth: 0, minHeight: 56, alignItems: 'center', justifyContent: 'center', gap: 2, paddingHorizontal: 4, borderRadius: radius.md },
  segmentOn: { backgroundColor: color.action.secondary },
  effect: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingVertical: spacing[2], paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft },
  effectMascot: { width: 28, height: 28 },
  pass: { borderRadius: radius.lg, overflow: 'hidden', backgroundColor: color.surface.card },
  passHead: { paddingVertical: spacing[2], paddingHorizontal: spacing[4], backgroundColor: color.brand.navy },
  passTitle: { letterSpacing: 1.2 },
  passRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 44, paddingHorizontal: spacing[4] },
  passLabel: { flexShrink: 0, maxWidth: '48%' },
  passValue: { flex: 1, textAlign: 'right' },
  tags: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 4 },
  tag: { maxWidth: '100%', paddingHorizontal: spacing[2], paddingVertical: 4, borderRadius: radius.full, backgroundColor: color.surface.soft },
  allergy: { minHeight: 36, justifyContent: 'center' },
  bar: { flexDirection: 'row', gap: 4 },
  barCell: { flex: 1, height: 4, borderRadius: radius.full, backgroundColor: color.surface.field },
  barCellOn: { backgroundColor: color.action.secondary },
});
