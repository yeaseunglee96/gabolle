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
import Svg, { Circle, Path } from 'react-native-svg';

import { GabolleMascot } from '@/components/DongbaekMascot';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { lodgingAreaNote } from '@/home/PlanStartBar';
import { formatDateShort, nightCount, placeEnglishOf, startBarPlaceName, startBarPlaceShortName } from '@/home/startBarValue';
import { txf } from '@/i18n/format';
import type { LanguageCode } from '@/i18n/languages';
import { budgetForDaily, tripDayCount } from '@/plan/budgetDefault';
import { conditionLabels } from '@/plan/conditionLabels';
import { DateRangePicker } from '@/plan/DateRangeCard';
import { maskTimeInput } from '@/plan/inputMasks';
import { MustVisitSearch } from '@/plan/MustVisitSearch';
import { MAJOR_BUSAN_ORIGINS, RECOMMENDED_LODGING_AREAS, lodgingSnapshotOf, type OriginCandidate } from '@/plan/origins';
import { AREA_OPTIONS, CATEGORY_IMAGES, CATEGORY_OPTIONS, PACE_OPTIONS, TRANSPORT_OPTIONS, effectOf, paceSubtitle, type PlanOption } from '@/plan/planOptions';
import { dayWindowIssue } from '@/plan/planQuestions';
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

// ── 작은 부품 ─────────────────────────────────────────────────────────────

/** 「필수」 작은 표 — 이동수단·하루 여행 시간 옆. */
function RequiredTag({ tx }: { tx: Tx }) {
  return <View style={styles.requiredTag}><Text variant="micro" weight="bold">{tx('필수', 'Required')}</Text></View>;
}

/** 칸 오른쪽 작은 › — 누르면 고치는 칸이라는 표시(확인 표). */
function Chevron({ size = 11 }: { size?: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color.text.muted} strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round">
      <Path d="M9 5l7 7-7 7" />
    </Svg>
  );
}

/** 동백이 안내 — 「이렇게 반영돼요」. */
function EffectNote({ text, tx }: { text: string | null; tx: Tx }) {
  if (!text) return null;
  return (
    <View style={styles.note}>
      <GabolleMascot state="open" still style={styles.noteMascot} />
      <Text variant="caption" color={color.text.body} style={styles.grow}>
        <Text variant="caption" weight="bold">{tx('이렇게 반영돼요', 'What this changes')}</Text>{'  '}{text}
      </Text>
    </View>
  );
}

/** 칩 — 바탕이 흰 카드 위면 연회색, 캔버스 위면 흰색(시안). 고르면 짙은 회색. */
function Chip({ label, selected, onCanvas = false, onPress, role = 'radio' }: { label: string; selected: boolean; onCanvas?: boolean; onPress: () => void; role?: 'radio' | 'checkbox' | 'button' }) {
  return (
    <Pressable accessibilityRole={role} accessibilityState={role === 'checkbox' ? { checked: selected } : { selected }} onPress={onPress} style={({ pressed }) => [styles.chip, onCanvas ? styles.chipCanvas : styles.chipCard, selected && styles.chipOn, pressed && styles.pressed]}>
      <Text variant="caption" weight="bold" numberOfLines={1} color={selected ? color.text.onAction : color.text.heading}>{label}</Text>
    </Pressable>
  );
}

// ── 1. 언제 · 누구와 ────────────────────────────────────────────────────────

function WhenStep({ draft, update, tx }: Pick<PlanStepsProps, 'draft' | 'update' | 'tx'>) {
  const people = (field: 'adults' | 'children', delta: number) => {
    const next = { adults: draft.adults, children: draft.children, [field]: draft[field] + delta };
    // 어른은 최소 한 명(서버 규칙), 합쳐 아홉까지 — 홈 시작 바와 같다.
    if (next.adults < 1 || next.children < 0 || next.adults + next.children > MAX_PEOPLE) return;
    update({ adults: next.adults, children: next.children, travelers: next.adults + next.children });
  };
  const full = draft.adults + draft.children >= MAX_PEOPLE;
  return (
    <View style={styles.stack}>
      <DateRangePicker value={{ startDate: draft.startDate, endDate: draft.endDate }} onChange={(next) => update({ startDate: next.startDate, endDate: next.endDate })} tx={tx} />
      <View style={styles.peopleCard}>
        {([['adults', tx('성인', 'Adults'), tx('만 13세 이상 · 최소 1명', 'Age 13+ · at least 1'), 1], ['children', tx('어린이', 'Children'), tx('만 2~12세', 'Age 2–12'), 0]] as const).map(([field, label, hint, min], index) => {
          const canLess = draft[field] > min;
          return (
            <View key={field} style={[styles.personRow, index > 0 && styles.divider]}>
              <View style={styles.grow}>
                <Text weight="bold">{label}</Text>
                <Text variant="caption" color={color.text.muted}>{hint}</Text>
              </View>
              <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 줄이기', 'Fewer %s', label)} disabled={!canLess} onPress={() => people(field, -1)} style={styles.stepper}>
                <Text variant="title" color={canLess ? color.text.heading : color.surface.field}>−</Text>
              </Pressable>
              <Text variant="title" weight="bold" style={styles.count}>{draft[field]}</Text>
              <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 늘리기', 'More %s', label)} disabled={full} onPress={() => people(field, 1)} style={styles.stepper}>
                <Text variant="title" color={full ? color.surface.field : color.text.heading}>+</Text>
              </Pressable>
            </View>
          );
        })}
      </View>
    </View>
  );
}

/** 1단계 바닥 요약 — 「출발 10월 9일 (금) · 성인 2」 / 「10.9(금) – 10.11(일) · 2박 · 성인 2」. 날짜가 없으면 null. */
export function whenSummary(draft: PlanDraft, tx: Tx): { lead: string; bold: string; rest: string } | null {
  if (!draft.startDate) return null;
  const people = [txf(tx, '성인 %s', 'Adults %s', draft.adults), draft.children ? txf(tx, '어린이 %s', 'Children %s', draft.children) : null].filter(Boolean).join(' · ');
  if (!draft.endDate) return { lead: tx('출발', 'Leaving'), bold: formatDateShort(draft.startDate, tx), rest: people };
  const nights = nightCount(draft.startDate, draft.endDate);
  const range = draft.endDate !== draft.startDate ? `${formatDateShort(draft.startDate, tx)} – ${formatDateShort(draft.endDate, tx)}` : formatDateShort(draft.startDate, tx);
  return { lead: '', bold: range, rest: [nights > 0 ? tx(`${nights}박 ${nights + 1}일`, `${nights} night${nights === 1 ? '' : 's'}`) : tx('당일치기', 'Day trip'), people].join(' · ') };
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
  // 출발지 둘째 줄 — 주요 출발지면 그 주소(시안: 「부산역 부산 동구 중앙대로 206」). 모르면 안 적는다(지어내지 않는다).
  const originKnown = hasOrigin ? MAJOR_BUSAN_ORIGINS.find((origin) => origin.lat === draft.originLat && origin.lng === draft.originLng) : undefined;
  const issue = dayWindowIssue(draft);
  const shortlist = ORIGIN_SHORTLIST.map((id) => MAJOR_BUSAN_ORIGINS.find((origin) => origin.externalId === id)).filter((origin): origin is OriginCandidate => Boolean(origin));
  const needsLodging = nights > 0;
  return (
    <View style={styles.stack}>
      <View style={styles.legCard}>
        {/* 출발지 */}
        <View style={styles.legRow}>
          <View style={styles.legDot} />
          <View style={styles.grow}>
            <Text variant="micro" color={color.text.muted}>{tx('출발지', 'Starting point')}</Text>
            {hasOrigin ? (
              <Text weight="bold" numberOfLines={1}>
                {startBarPlaceName(draft.origin, draft.originEnglish, language)}
                {originKnown && language === 'ko' ? <Text variant="caption" color={color.text.muted}>{'  '}{originKnown.address}</Text> : null}
              </Text>
            ) : <Text weight="bold" color={color.text.muted}>{tx('어디서 출발하세요?', 'Where do you start?')}</Text>}
          </View>
          <Pressable accessibilityRole="button" onPress={() => onSearchPlace('origin')} style={styles.textButton}>
            <Text weight="bold" color={color.text.muted}>{hasOrigin ? tx('바꾸기', 'Change') : tx('다른 곳 찾기', 'Search')}</Text>
          </Pressable>
        </View>
        {!hasOrigin ? (
          <View style={[styles.chips, styles.indent, styles.below]}>
            {shortlist.map((origin) => <Chip key={origin.externalId} label={startBarPlaceName(origin.name, placeEnglishOf(origin), language)} selected={false} role="button" onPress={() => pickOrigin(origin)} />)}
          </View>
        ) : null}
        <View style={styles.legLine} />
        {/* 숙소 — 1박 이상이면 필요하다(S15P21E201-1584). 당일치기면 묻지 않는다. */}
        <View style={styles.legRowTop}>
          <View style={[styles.legDot, styles.legDotHollow, styles.legDotTop]} />
          <View style={[styles.grow, styles.lodgingColumn]}>
            <Text variant="micro" color={color.text.muted}>
              {needsLodging ? txf(tx, '숙소 · %s박이라 필요해요', 'Stay · needed for %s night(s)', nights) : tx('숙소 · 당일치기라 안 골라도 돼요', 'Stay · not needed for a day trip')}
            </Text>
            {needsLodging || hasLodging ? (
              // 이름으로 찾기 — 누르면 검색 시트(시작 바와 같은 검색). 고른 실제 숙소는 이 칸에 이름으로 남는다.
              <Pressable accessibilityRole="search" accessibilityLabel={tx('숙소 검색', 'Search stays')} onPress={() => onSearchPlace('lodging')} style={({ pressed }) => [styles.searchField, pressed && styles.pressed]}>
                <Svg width={18} height={18} viewBox="0 0 24 24" fill="none" stroke={color.text.muted} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round"><Circle cx={11} cy={11} r={6} /><Path d="M20 20l-4.5-4.5" /></Svg>
                {hasLodging && !pickedArea
                  ? <Text weight="bold" numberOfLines={1} style={styles.grow}>{startBarPlaceName(draft.lodging, draft.lodgingEnglish, language)}</Text>
                  : <Text color={color.text.muted} numberOfLines={1} style={styles.grow}>{tx('숙소 이름으로 찾기', 'Search stays by name')}</Text>}
              </Pressable>
            ) : null}
            {needsLodging ? (
              <>
                <Text variant="micro" color={color.text.muted}>{tx('아직 안 정했다면 동네만 골라도 돼요', 'Not booked yet? Just pick a neighborhood')}</Text>
                <View style={styles.chips}>
                  {RECOMMENDED_LODGING_AREAS.map((area) => <Chip key={area.externalId} label={startBarPlaceName(area.name, placeEnglishOf(area), language)} selected={pickedArea?.externalId === area.externalId} onPress={() => pickLodging(area)} />)}
                </View>
                {pickedArea ? <Text variant="micro" color={color.text.body}>{`${startBarPlaceName(pickedArea.name, placeEnglishOf(pickedArea), language)} — ${lodgingAreaNote(pickedArea, tx)}`}</Text> : null}
              </>
            ) : null}
          </View>
        </View>
      </View>

      {/* 이동수단 */}
      <View style={styles.stackTight}>
        <View style={styles.labelRow}><Text variant="caption" weight="bold">{tx('이동수단', 'Getting around')}</Text><RequiredTag tx={tx} /></View>
        <View accessibilityRole="radiogroup" style={styles.chips}>
          {TRANSPORT_OPTIONS.map((option) => <Chip key={option[0]} label={labelOf(option, tx)} selected={draft.transport === option[0]} onCanvas onPress={() => update({ transport: option[0] as PlanDraft['transport'] })} />)}
        </View>
      </View>

      {/* 하루 여행 시간 — 대부분 기본값(09:00–21:00)이라 한 줄로 두고 「바꾸기」로 편다. */}
      <View style={styles.hoursCard}>
        <View style={styles.hoursRow}>
          <View style={styles.grow}>
            <View style={styles.labelRow}><Text variant="micro" color={color.text.muted}>{tx('하루 여행 시간', 'Daily hours')}</Text><RequiredTag tx={tx} /></View>
            <Text weight="bold">{`${draft.dayStartTime || '--:--'} – ${draft.dayEndTime || '--:--'}`}</Text>
          </View>
          <Pressable accessibilityRole="button" accessibilityState={{ expanded: editingHours }} onPress={() => setEditingHours((open) => !open)} style={styles.textButton}>
            <Text weight="bold" color={color.text.muted}>{editingHours ? tx('닫기', 'Done') : tx('바꾸기', 'Change')}</Text>
          </Pressable>
        </View>
        {editingHours || issue ? (
          <View style={[styles.stackTight, styles.hoursEdit]}>
            <View style={styles.timeRow}>
              {([['dayStartTime', '시작', 'Start', '09:00'], ['dayEndTime', '종료', 'End', '21:00']] as const).map(([field, k, e, placeholder]) => (
                <View key={field} style={styles.grow}>
                  <Text variant="micro" color={color.text.muted}>{tx(k, e)}</Text>
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
  const party = [txf(tx, '성인 %s', 'Adults %s', draft.adults), draft.children ? txf(tx, '어린이 %s', 'Children %s', draft.children) : null].filter(Boolean).join(' · ');
  return (
    <View style={styles.stack}>
      <View style={styles.stackTight}>
        <Text variant="caption" weight="bold" color={color.text.muted}>{tx('지역 · 여러 곳 골라도 돼요', 'Areas · pick as many as you like')}</Text>
        <View style={styles.grid2}>
          {AREA_OPTIONS.map((option) => {
            const on = draft.travelAreas.includes(option[0]);
            return (
              <Pressable key={option[0]} accessibilityRole="checkbox" accessibilityState={{ checked: on }} onPress={() => toggleArea(option[0])} style={({ pressed }) => [styles.areaCard, on && styles.areaCardOn, pressed && styles.pressed]}>
                <Text weight="bold" numberOfLines={1} color={on ? color.text.onAction : color.text.heading}>{labelOf(option, tx)}</Text>
                <Text variant="caption" numberOfLines={2} color={on ? color.text.onDarkMuted : color.text.muted}>{subOf(option, tx)}</Text>
              </Pressable>
            );
          })}
        </View>
        <EffectNote text={effectOf('areas', draft, tx)} tx={tx} />
      </View>

      <View style={styles.budgetCard}>
        <View style={styles.rowBetween}>
          <Text weight="bold">{tx('총예산', 'Total budget')}</Text>
          <Text variant="micro" color={color.text.muted}>{txf(tx, '숙박비 빼고 · %s · %s일', 'Excl. stays · %s · %s days', party, days)}</Text>
        </View>
        <View accessibilityRole="radiogroup" style={styles.presets}>
          {DAILY_BUDGET_PRESETS.map((preset) => {
            const presetTotal = budgetForDaily(draft, preset.perDay);
            const selected = total === presetTotal;
            return (
              <Pressable key={preset.perDay} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => update({ budgetKrw: presetTotal })} style={({ pressed }) => [styles.preset, selected && styles.presetOn, pressed && styles.pressed]}>
                <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.heading}>{tx(preset.ko, preset.en)}</Text>
                <Text weight="bold" style={styles.presetAmount} color={selected ? color.text.onAction : color.text.heading}>{won(presetTotal, tx)}</Text>
                <Text variant="micro" color={selected ? color.text.onDarkMuted : color.text.muted}>{txf(tx, '1인 하루 %s', '%s / person / day', tx(`${preset.perDay / 10000}만`, `₩${preset.perDay.toLocaleString()}`))}</Text>
              </Pressable>
            );
          })}
        </View>
        <View style={styles.rowBetween}>
          {/* 직접 적기 — 누르면 만원 단위로 적는다. 1만원 밑은 서버가 안 받는다. */}
          <View style={styles.typeRow}>
            <Text variant="caption" color={color.text.muted}>{tx('직접 적기', 'Enter amount')}</Text>
            {typing === null ? (
              <Pressable accessibilityRole="button" accessibilityLabel={tx('총예산 직접 적기 (만원)', 'Enter total budget')} onPress={() => setTyping(String(Math.round(total / BUDGET_UNIT)))} style={styles.typedValue}>
                <Text weight="bold">{won(total, tx)}</Text>
              </Pressable>
            ) : (
              <>
                <TextInput autoFocus accessibilityLabel={tx('총예산 직접 적기 (만원)', 'Enter total budget')} value={typing} onChangeText={(value) => setTyping(value.replace(/[^0-9]/g, '').slice(0, 4))} onBlur={commitTyped} onSubmitEditing={commitTyped} keyboardType="number-pad" style={styles.budgetInput} />
                <Text variant="caption" weight="bold">{tx('만원', '× ₩10,000')}</Text>
              </>
            )}
          </View>
          <View style={styles.chipsTight}>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('예산 1만원 줄이기', 'Lower budget by ₩10,000')} disabled={total <= BUDGET_UNIT} onPress={() => update({ budgetKrw: Math.max(BUDGET_UNIT, total - BUDGET_UNIT) })} style={[styles.chip, styles.chipCard, total <= BUDGET_UNIT && styles.dim]}>
              <Text variant="caption" weight="bold">{tx('−1만', '−10k')}</Text>
            </Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('예산 1만원 늘리기', 'Raise budget by ₩10,000')} onPress={() => update({ budgetKrw: total + BUDGET_UNIT })} style={[styles.chip, styles.chipCard]}>
              <Text variant="caption" weight="bold">{tx('+1만', '+10k')}</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </View>
  );
}

// ── 4. 어떤 여행이 좋아요 (선택) ─────────────────────────────────────────────

function PhotoCard({ source, label, selected, disabled, onPress, wide = false, sub }: { source: number | undefined; label: string; selected: boolean; disabled: boolean; onPress: () => void; wide?: boolean; sub?: string }) {
  return (
    <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: selected, disabled }} disabled={disabled} onPress={onPress} style={({ pressed }) => [wide ? styles.festival : styles.photoCard, selected && styles.photoOn, disabled && styles.dim, pressed && styles.pressed]}>
      {source ? <Image source={source} resizeMode="cover" accessibilityLabel="" style={wide ? styles.festivalImage : styles.photo} /> : null}
      {selected ? <View style={[styles.check, wide && styles.checkWide]}><Text variant="micro" weight="bold" color={color.text.onAction}>✓</Text></View> : null}
      {wide ? (
        <View style={styles.grow}>
          <Text variant="caption" weight="bold" color={color.text.heading}>{label}</Text>
          {sub ? <Text variant="micro" color={color.text.muted}>{sub}</Text> : null}
        </View>
      ) : <Text variant="caption" weight="bold" color={color.text.heading} numberOfLines={1} style={styles.photoLabel}>{label}</Text>}
    </Pressable>
  );
}

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
      <View style={styles.stackTight}>
        <View style={styles.rowBetween}>
          <Text variant="caption" weight="bold">{tx('여행 카테고리', 'Trip categories')}</Text>
          <Text variant="caption" weight="bold" color={color.text.body}>{txf(tx, '최대 3개 · %s / 3', 'Up to 3 · %s / 3', picked.length)}</Text>
        </View>
        {/* 여섯은 3×2 로 한눈에 — 옆으로 넘기지 않는다. 축제는 «여행 날짜에 여는 것만» 들어가는 다른 성격이라 아래에 넓게 따로. */}
        <View style={styles.grid3}>
          {six.map((option) => (
            <View key={option[0]} style={styles.cell3}>
              <PhotoCard source={CATEGORY_IMAGES[option[0]]} label={labelOf(option, tx)} selected={picked.includes(option[0])} disabled={full && !picked.includes(option[0])} onPress={() => toggle(option[0])} />
            </View>
          ))}
        </View>
        {festival ? <PhotoCard wide source={CATEGORY_IMAGES[festival[0]]} label={labelOf(festival, tx)} sub={subOf(festival, tx)} selected={picked.includes(festival[0])} disabled={full && !picked.includes(festival[0])} onPress={() => toggle(festival[0])} /> : null}
      </View>

      <View style={styles.stackTight}>
        <Text variant="caption" weight="bold">{tx('여행 기분', 'Trip pace')}</Text>
        <View accessibilityRole="radiogroup" style={styles.segment}>
          {PACE_OPTIONS.map((option) => {
            const selected = draft.paceLevel === option[0];
            return (
              <Pressable key={option[0]} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => update({ paceLevel: option[0] as PlanDraft['paceLevel'] })} style={({ pressed }) => [styles.segmentItem, selected && styles.segmentOn, pressed && styles.pressed]}>
                <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.heading}>{labelOf(option, tx)}</Text>
                <Text variant="micro" numberOfLines={1} color={selected ? color.text.onDarkMuted : color.text.body} style={styles.center}>{paceSubtitle(option, tx)}</Text>
              </Pressable>
            );
          })}
        </View>
      </View>
    </View>
  );
}

// ── 확인 표 — 이대로 만들까요? ───────────────────────────────────────────────

/** 승차권 칸 하나 — 누르면 그 단계로 가서 고친다. */
function PassCell({ label, value, note, onPress, tx }: { label: string; value: string | string[]; note?: string; onPress: () => void; tx: Tx }) {
  // 값이 여럿이면(「균형 있게 · 대중교통」) 한 덩어리씩 — 줄은 「 · 」에서만 바뀐다. 한 줄로 이으면 일본어에서 「公共交 / 通機関」처럼 낱말 가운데가 끊겼다.
  const parts = (Array.isArray(value) ? value : [value]).filter(Boolean);
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 고치기', 'Edit %s', label)} onPress={onPress} style={({ pressed }) => [styles.passCell, pressed && styles.pressed]}>
      <View style={styles.passCellLabel}><Text variant="micro" color={color.text.muted}>{label}</Text><Chevron /></View>
      <View style={styles.keptRow}>
        {(parts.length ? parts : ['—']).map((part, index, all) => <Text key={`${part}-${index}`} weight="bold" style={styles.keptItem}>{index < all.length - 1 ? `${part} ·` : part}</Text>)}
        {note ? <Text variant="micro" color={color.text.muted} style={styles.cellNote}>{note}</Text> : null}
      </View>
    </Pressable>
  );
}

/** 승차권 아래 줄 — 「이동 보조 없음 ›」. 누르면 그 자리에서 편다. */
function PassRow({ label, value, open, onPress }: { label: string; value: string; open: boolean; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" accessibilityState={{ expanded: open }} onPress={onPress} style={({ pressed }) => [styles.passRow, pressed && styles.pressed]}>
      <Text variant="caption" color={color.text.body} style={styles.grow}>{label}</Text>
      <Text variant="caption" color={color.text.muted} numberOfLines={1} style={styles.passRowValue}>{value}</Text>
      <View style={open ? styles.chevronOpen : undefined}><Chevron size={14} /></View>
    </Pressable>
  );
}

function ReviewStep({ draft, update, tx, language, styleSkipped, accessibilityCounts, goTo, onEditConditions, onOpenMenuScan }: PlanStepsProps) {
  const [open, setOpen] = useState<'aids' | 'must' | null>(null);
  const toggle = (key: 'aids' | 'must') => setOpen((current) => (current === key ? null : key));
  const nights = draft.startDate && draft.endDate ? nightCount(draft.startDate, draft.endDate) : 0;
  const labelList = (list: readonly PlanOption[], codes: string[]) => list.filter(([code]) => codes.includes(code)).map((option) => labelOf(option, tx));
  const transport = TRANSPORT_OPTIONS.find(([code]) => code === draft.transport);
  const pace = PACE_OPTIONS.find(([code]) => code === draft.paceLevel);
  const peopleParts = [txf(tx, '성인 %s', 'Adults %s', draft.adults), draft.children ? txf(tx, '어린이 %s', 'Children %s', draft.children) : ''].filter(Boolean);
  const nightsLabel = nights > 0 ? tx(`${nights}박 ${nights + 1}일`, `${nights} night${nights === 1 ? '' : 's'}`) : tx('당일치기', 'Day trip');
  const kept = conditionLabels(draft, tx, { withAids: false });
  const aids = [draft.wheelchair ? tx('휠체어', 'Wheelchair') : null, draft.stroller ? tx('유아차', 'Stroller') : null, draft.luggage ? tx('큰 짐', 'Large luggage') : null].filter(Boolean).join(' · ');
  const must = draft.mustVisitPlaces.map((place) => (language === 'ko' ? place.nameKo : place.nameEn ?? place.nameKo)).join(' · ');
  const dates = draft.startDate ? (draft.endDate && draft.endDate !== draft.startDate ? `${formatDateShort(draft.startDate, tx)} – ${formatDateShort(draft.endDate, tx)}` : formatDateShort(draft.startDate, tx)) : '—';
  // 알레르기 안내 한 문장 안의 「메뉴판 읽기 ›」 — 번역문을 %s 에서 갈라 그 자리에 누르는 글자를 넣는다(언어마다 어순이 다르다).
  const [allergyBefore, allergyAfter = ''] = tx('알레르기는 일정 조건에 넣지 않아요 — 식당에서 %s로 확인해요', 'Allergies are not part of the plan — at restaurants, check with %s').split('%s');
  const to = nights > 0 || draft.lodging
    // 큰 글자 자리라 짧은 이름만(「Haeundae」) — 한글·읽는 법까지 붙이면 폰 폭에서 「Haeunda…」로 잘렸다.
    ? { label: tx('숙소', 'Stay'), value: startBarPlaceShortName(draft.lodging, draft.lodgingEnglish, language) || '—' }
    : { label: tx('기간', 'Length'), value: tx('당일치기', 'Day trip') };
  return (
    <View style={styles.stack}>
      <View>
        {/* 승차권 위쪽 — 출발 → 숙소, 그리고 칸마다 누르면 고친다. */}
        <View style={styles.passTop}>
          <View style={styles.rowBetween}>
            <Image source={LOGO} resizeMode="contain" accessibilityLabel="GABOLLE" style={styles.passLogo} />
            <Text variant="micro" weight="bold" color={color.text.muted} style={styles.passMark}>TRIP PASS · {tx('미리보기', 'Preview')}</Text>
          </View>
          <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 고치기', 'Edit %s', tx('출발지', 'Starting point'))} onPress={() => goTo(1)} style={styles.route}>
            <View style={styles.routeEnd}>
              <Text variant="micro" color={color.text.muted}>{tx('출발', 'From')}</Text>
              <Text variant="display" weight="bold" numberOfLines={1} style={styles.routeName}>{startBarPlaceShortName(draft.origin, draft.originEnglish, language) || '—'}</Text>
            </View>
            <View style={styles.routeLine}>
              <View style={styles.routeDash} />
              <Svg width={16} height={16} viewBox="0 0 24 24" fill="none" stroke={color.text.heading} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round"><Path d="M3 13l18-6-6 14-3-6z" /></Svg>
              <View style={styles.routeDash} />
            </View>
            <View style={[styles.routeEnd, styles.routeEndRight]}>
              <Text variant="micro" color={color.text.muted}>{to.label}</Text>
              <Text variant="display" weight="bold" numberOfLines={1} style={[styles.routeName, styles.right]}>{to.value}</Text>
            </View>
          </Pressable>
          <View style={styles.passDivider} />
          <View style={styles.passGrid}>
            <PassCell tx={tx} label={tx('날짜', 'Dates')} value={dates} onPress={() => goTo(0)} />
            <PassCell tx={tx} label={tx('기간 · 인원', 'Length · people')} value={[nightsLabel, ...peopleParts]} onPress={() => goTo(0)} />
            <PassCell tx={tx} label={tx('지역', 'Areas')} value={labelList(AREA_OPTIONS, draft.travelAreas)} onPress={() => goTo(2)} />
            <PassCell tx={tx} label={tx('총예산', 'Budget')} value={draft.budgetKrw ? won(draft.budgetKrw, tx) : '—'} note={draft.budgetKrw ? tx('숙박 빼고', 'excl. stays') : undefined} onPress={() => goTo(2)} />
            <PassCell tx={tx} label={tx('여행 기분 · 이동', 'Pace · transport')} value={[pace ? labelOf(pace, tx) : styleSkipped ? tx('건너뜀', 'Skipped') : '', transport ? labelOf(transport, tx) : '']} onPress={() => goTo(pace || styleSkipped ? 3 : 1)} />
            <PassCell tx={tx} label={tx('하루 여행 시간', 'Daily hours')} value={`${draft.dayStartTime} – ${draft.dayEndTime}`} onPress={() => goTo(1)} />
          </View>
          <PassCell tx={tx} label={tx('카테고리', 'Categories')} value={draft.preferences.length ? labelList(CATEGORY_OPTIONS, draft.preferences) : (styleSkipped ? tx('건너뜀', 'Skipped') : tx('없음', 'None'))} onPress={() => goTo(3)} />
        </View>

        {/* 절취선 — 양 끝에 반달 홈. */}
        <View style={styles.tear}>
          <View style={[styles.notch, styles.notchLeft]} />
          <View style={styles.tearDash} />
          <View style={[styles.notch, styles.notchRight]} />
        </View>

        {/* 승차권 아래쪽 — 저장해 둔 조건 · 알레르기 · 이동 보조 · 꼭 가고 싶은 곳. */}
        <View style={styles.passStub}>
          <Pressable accessibilityRole="button" onPress={onEditConditions} style={({ pressed }) => [styles.conditionRow, pressed && styles.pressed]}>
            <View style={styles.grow}>
              <Text variant="micro" weight="bold" color={color.text.muted}>{tx('내 여행 조건 · 저장해 둔 값', 'My travel conditions · saved')}</Text>
              {/* 조건마다 한 덩어리 — 한 줄로 이으면 일본어·중국어에서 낱말 가운데가 끊겼다(「急 / な坂道」). 줄은 「 · 」에서만 바뀐다. */}
              {kept.length ? (
                <View style={styles.keptRow}>
                  {kept.map((label, index) => <Text key={label} weight="bold" style={styles.keptItem}>{index < kept.length - 1 ? `${label} ·` : label}</Text>)}
                </View>
              ) : <Text weight="bold">{tx('따로 정한 조건 없음', 'No conditions set')}</Text>}
            </View>
            <Text variant="caption" weight="bold" color={color.text.muted} style={styles.conditionChange}>{tx('바꾸기', 'Change')}</Text>
          </Pressable>
          {/* 🔴 알레르기는 일부러 일정 조건에서 뺐다(S15P21E201-1497) — 장소 자료에 알레르기 표식이 없고, 추정이 틀리면 사람이 다친다. */}
          <View style={styles.allergy}>
            <Text variant="micro" color={color.text.body}>
              {allergyBefore}
              <Text variant="micro" weight="bold" color={color.text.heading} accessibilityRole="link" onPress={onOpenMenuScan}>{tx('메뉴판 읽기 ›', 'Menu reader ›').replace(/ /g, ' ')}</Text>
              {allergyAfter}
            </Text>
          </View>

          <PassRow label={tx('이동 보조', 'Mobility aids')} value={aids || tx('없음', 'None')} open={open === 'aids'} onPress={() => toggle('aids')} />
          {open === 'aids' ? (
            <View style={styles.expand}>
              <View style={styles.chips}>
                {/* 큰 짐은 서버가 경사로만 가른다 — 「확인 안 됨」 안내는 휠체어·유아차에만 붙는다(아래 문구도 그렇게 말한다). */}
                {([['wheelchair', '휠체어', 'Wheelchair'], ['stroller', '유아차', 'Stroller'], ['luggage', '큰 짐', 'Large luggage']] as const).map(([field, ko, en]) => (
                  <Chip key={field} role="checkbox" label={tx(ko, en)} selected={draft[field] === true} onPress={() => update({ [field]: !(draft[field] === true) } as Partial<PlanDraft>)} />
                ))}
              </View>
              {/* 🔴 고르기 «전에» 알린다 — 고른 뒤에야 뜨면 이미 정한 사람에게 늦은 말이다(S15P21E201-1869, !1888 의 뜻). */}
              {accessibilityCounts ? (
                <Text variant="micro" color={color.text.muted}>{txf(tx, '지금 접근성을 확인한 곳은 %s곳 중 %s곳이에요. 휠체어·유아차를 고르시면 나머지는 「아직 확인되지 않았어요」로 나와요 — 못 간다는 뜻은 아니에요.', 'Of %s places, we have checked access for %s so far. With a wheelchair or stroller, the rest will show as "not checked yet" — that does not mean you cannot go.', accessibilityCounts.totalPlaceCount, accessibilityCounts.placeCount)}</Text>
              ) : null}
            </View>
          ) : null}
          <PassRow label={tx('꼭 가고 싶은 곳', 'Must-visit places')} value={must || tx('없음', 'None')} open={open === 'must'} onPress={() => toggle('must')} />
          {open === 'must' ? (
            <View style={styles.expand}>
              <MustVisitSearch picked={draft.mustVisitPlaces} onChange={(next) => update({ mustVisitPlaces: next })} tx={tx} ko={language === 'ko'} language={language} />
            </View>
          ) : null}
        </View>
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
    default: return { eyebrow: '', title: tx('이대로 만들까요?', 'Build it like this?') };
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

const LOGO = require('../../assets/brand/gabolle-logo-hd.png');

const styles = StyleSheet.create({
  stack: { gap: spacing[4] },
  stackTight: { gap: spacing[2] },
  grow: { flex: 1, minWidth: 0 },
  center: { textAlign: 'center' },
  right: { textAlign: 'right' },
  dim: { opacity: 0.4 },
  pressed: { opacity: 0.8 },
  rowBetween: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], flexWrap: 'wrap' },
  divider: { borderTopWidth: 1, borderTopColor: color.surface.border },
  labelRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  requiredTag: { paddingHorizontal: 7, paddingVertical: 1, borderRadius: radius.full, backgroundColor: color.surface.soft },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  chipsTight: { flexDirection: 'row', gap: 6 },
  chip: { maxWidth: '100%', minHeight: 36, justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.chip },
  chipCard: { backgroundColor: color.surface.tint },
  chipCanvas: { backgroundColor: color.surface.card, paddingHorizontal: 14 },
  chipOn: { backgroundColor: color.action.secondary },
  textButton: { minHeight: 44, justifyContent: 'center', paddingLeft: spacing[2] },
  note: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: spacing[2], paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  noteMascot: { width: 28, height: 28 },

  // 1단계
  peopleCard: { paddingVertical: 2, paddingHorizontal: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  personRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 52 },
  stepper: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' },
  count: { width: 20, textAlign: 'center' },

  // 2단계
  legCard: { paddingTop: 4, paddingBottom: 14, paddingHorizontal: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  legRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 56 },
  legRowTop: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3], paddingTop: 6 },
  legDot: { width: 12, height: 12, borderRadius: radius.full, backgroundColor: color.action.secondary, marginHorizontal: 4 },
  legDotHollow: { backgroundColor: 'transparent', borderWidth: 2.5, borderColor: color.action.secondary },
  legDotTop: { marginTop: 3 },
  legLine: { marginLeft: 9, width: 0, height: 10, borderLeftWidth: 2, borderStyle: 'dashed', borderColor: color.surface.field },
  indent: { marginLeft: 32 },
  below: { marginBottom: 6 },
  lodgingColumn: { gap: spacing[2] },
  searchField: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 44, paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  hoursCard: { paddingHorizontal: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  hoursRow: { flexDirection: 'row', alignItems: 'center', minHeight: 56 },
  hoursEdit: { paddingBottom: spacing[3] },
  timeRow: { flexDirection: 'row', gap: spacing[3] },
  input: { minHeight: 44, marginTop: 4, paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint, color: color.text.heading },

  // 3단계
  grid2: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  areaCard: { flexBasis: '45%', flexGrow: 1, minWidth: 0, minHeight: 64, justifyContent: 'center', gap: 2, padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card },
  areaCardOn: { backgroundColor: color.brand.navy },
  budgetCard: { gap: 10, paddingVertical: 14, paddingHorizontal: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  presets: { flexDirection: 'row', gap: 6 },
  preset: { flex: 1, minWidth: 0, minHeight: 72, gap: 2, padding: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' },
  presetOn: { backgroundColor: color.brand.navy },
  presetAmount: { fontSize: 17 },
  typeRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  typedValue: { minHeight: 36, justifyContent: 'center', borderBottomWidth: 1.5, borderBottomColor: color.surface.field },
  budgetInput: { width: 56, minHeight: 36, paddingHorizontal: 4, borderBottomWidth: 1.5, borderBottomColor: color.action.secondary, color: color.text.heading, fontWeight: '700', textAlign: 'right' },

  // 4단계
  grid3: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  cell3: { flexBasis: '30%', flexGrow: 1, minWidth: 0 },
  photoCard: { borderRadius: radius.md, overflow: 'hidden', backgroundColor: color.surface.card, borderWidth: 1.5, borderColor: 'transparent' },
  photoOn: { borderColor: color.action.outline },
  photo: { width: '100%', height: 70 },
  photoLabel: { paddingTop: spacing[2], paddingHorizontal: 10, paddingBottom: 10 },
  check: { position: 'absolute', top: 6, right: 6, width: 22, height: 22, borderRadius: radius.full, backgroundColor: color.action.outline, alignItems: 'center', justifyContent: 'center' },
  checkWide: { top: 27, right: spacing[3] },
  festival: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], height: 76, borderRadius: radius.md, overflow: 'hidden', backgroundColor: color.surface.card, borderWidth: 1.5, borderColor: 'transparent' },
  festivalImage: { width: 112, height: 76 },
  segment: { flexDirection: 'row', gap: 4, padding: 4, borderRadius: radius.chip, backgroundColor: color.surface.soft },
  segmentItem: { flex: 1, minWidth: 0, minHeight: 56, alignItems: 'center', justifyContent: 'center', gap: 2, paddingHorizontal: 4, borderRadius: radius.md },
  segmentOn: { backgroundColor: color.action.secondary },

  // 확인 표 — 승차권
  passTop: { paddingTop: spacing[4], paddingHorizontal: 20, paddingBottom: 10, borderTopLeftRadius: 16, borderTopRightRadius: 16, borderBottomLeftRadius: 6, borderBottomRightRadius: 6, backgroundColor: color.surface.card },
  passLogo: { width: 110, height: 20 },
  passMark: { letterSpacing: 1.2 },
  route: { flexDirection: 'row', alignItems: 'flex-end', marginTop: spacing[4], gap: spacing[3] },
  routeEnd: { flexShrink: 1, minWidth: 0, gap: 2, maxWidth: '42%' },
  routeEndRight: { alignItems: 'flex-end' },
  routeName: { fontSize: 24, lineHeight: 30 },
  routeLine: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 4, marginBottom: 7 },
  routeDash: { flex: 1, height: 0, borderTopWidth: 2, borderStyle: 'dashed', borderColor: color.surface.field },
  passDivider: { height: 1, marginTop: 14, marginBottom: 4, backgroundColor: color.surface.border },
  passGrid: { flexDirection: 'row', flexWrap: 'wrap', columnGap: spacing[4] },
  passCell: { flexBasis: '44%', flexGrow: 1, minWidth: 0, gap: 2, paddingVertical: spacing[2] },
  passCellLabel: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  chevronOpen: { transform: [{ rotate: '90deg' }] },
  tear: { height: 18, marginHorizontal: spacing[2], justifyContent: 'center', backgroundColor: color.surface.card },
  tearDash: { marginHorizontal: spacing[3], height: 0, borderTopWidth: 2, borderStyle: 'dashed', borderColor: color.surface.field },
  notch: { position: 'absolute', top: 0, width: 18, height: 18, borderRadius: radius.full, backgroundColor: color.canvas },
  notchLeft: { left: -17 },
  notchRight: { right: -17 },
  passStub: { paddingTop: 6, paddingHorizontal: 20, paddingBottom: spacing[2], borderTopLeftRadius: 6, borderTopRightRadius: 6, borderBottomLeftRadius: 16, borderBottomRightRadius: 16, backgroundColor: color.surface.card },
  conditionRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2], paddingTop: spacing[2], paddingBottom: 6 },
  conditionChange: { paddingTop: 16 },
  keptRow: { flexDirection: 'row', flexWrap: 'wrap', columnGap: 4 },
  keptItem: { maxWidth: '100%' },
  cellNote: { alignSelf: 'flex-end', paddingBottom: 2 },
  allergy: { paddingVertical: 6, paddingHorizontal: 10, marginBottom: 2, borderRadius: 10, backgroundColor: color.surface.tint },
  passRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 44 },
  passRowValue: { maxWidth: '55%' },
  expand: { gap: spacing[2], paddingBottom: spacing[2] },

  bar: { flexDirection: 'row', gap: 4 },
  barCell: { flex: 1, height: 4, borderRadius: radius.full, backgroundColor: color.surface.field },
  barCellOn: { backgroundColor: color.action.secondary },
});
