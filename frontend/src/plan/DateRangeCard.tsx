// 문항 화면 안의 날짜 카드 — S15P21E201-1376.
//
// 🔴 예전에는 날짜가 없으면 「날짜 정하기」가 홈으로 보냈다. 돌아오면 문항이 1번부터였다
//    (답은 남아 있었지만 자리가 초기화됐다). 열 문항을 다 답한 사람에게 그것은 처음부터
//    다시 하라는 말이다 — 2026-09-21 실기. 날짜는 그 자리에서 고른다.
//
// 달력 칸(MonthGrid)은 홈 시작 줄의 것을 그대로 쓴다 — 같은 달력이 두 벌이면 하나만 고쳐진다.
import { useMemo, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import Svg, { Path } from 'react-native-svg';

import { GabolleMascot } from '@/components/DongbaekMascot';

import { Text } from '@/components/Text';
import { txf } from '@/i18n/format';
import { color, radius, spacing } from '@/design/tokens';
import { MonthGrid } from '@/home/PlanStartBar';
import { MonthPicker } from '@/home/MonthPicker';
import { MAX_MONTH_OFFSET, monthOffsetOf } from '@/home/monthJump';
import { EMPTY_START_BAR, MAX_TRIP_NIGHTS, addDays, formatDateShort, nightCount, toDateKey } from '@/home/startBarValue';


export type DateRange = { startDate: string; endDate: string };

/** 「10.3(토) – 10.5(월) · 2박」. 날짜가 없으면 null. */
export function dateRangeLabel(value: DateRange, tx: (ko: string, en: string) => string): string | null {
  if (!value.startDate) return null;
  const end = value.endDate || value.startDate;
  const range = end !== value.startDate ? `${formatDateShort(value.startDate, tx)} – ${formatDateShort(end, tx)}` : formatDateShort(value.startDate, tx);
  const nights = nightCount(value.startDate, end);
  return `${range} · ${nights > 0 ? tx(`${nights}박`, `${nights} ${nights === 1 ? 'night' : 'nights'}`) : tx('당일치기', 'Day trip')}`;
}

export function DateRangeCard({ value, onChange, onDone, tx, today = new Date() }: {
  value: DateRange;
  onChange: (next: DateRange) => void;
  /** 「이 날짜로」 — 고른 것을 확정하고 카드를 접는다. 양쪽 날짜가 있을 때만 켜진다. */
  onDone: () => void;
  tx: (ko: string, en: string) => string;
  today?: Date;
}) {
  const todayKey = toDateKey(today);
  // 이미 고른 출발일이 있으면 그 달부터 연다 — S15P21E201-1539. 이번 달부터 열면 고른 날을 보려고 › 를 또 눌러야 했다.
  const [monthOffset, setMonthOffset] = useState(() => monthOffsetOf(value.startDate, today));
  const [monthPickerOpen, setMonthPickerOpen] = useState(false);
  const month = useMemo(() => {
    const base = new Date(today.getFullYear(), today.getMonth() + monthOffset, 1);
    return { year: base.getFullYear(), month: base.getMonth() };
  }, [today, monthOffset]);
  const complete = Boolean(value.startDate && value.endDate);
  // 출발일만 찍은 동안 — 8일째 뒤는 못 누른다. 「○일까지」를 글로도 적는다(제약을 먼저 보여준다, UI 캔버스 ⑤).
  const pickingEnd = Boolean(value.startDate && !value.endDate);
  const lastPickable = pickingEnd ? addDays(value.startDate, MAX_TRIP_NIGHTS) : undefined;
  const label = dateRangeLabel(value, tx);

  // 첫 탭은 출발일, 두 번째 탭은 귀환일. 앞선 날짜를 다시 찍으면 처음부터 — 홈 시작 줄과 같은 규칙.
  const pick = (key: string) => {
    if (!value.startDate || value.endDate || key < value.startDate) onChange({ startDate: key, endDate: '' });
    else onChange({ startDate: value.startDate, endDate: key });
  };
  const quick = (nights: number) => {
    const start = value.startDate && value.startDate >= todayKey ? value.startDate : todayKey;
    onChange({ startDate: start, endDate: addDays(start, nights) });
  };

  return (
    <View style={styles.card}>
      <View style={styles.head}>
        <View style={styles.headText}>
          <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('필수', 'Required')}</Text>
          <Text variant="title" weight="bold" color={color.text.heading}>{tx('언제 가세요?', 'When are you going?')}</Text>
        </View>
        <Text variant="caption" weight="bold" color={label ? color.text.heading : color.text.muted} numberOfLines={1} style={styles.summary}>
          {label ?? tx('출발일 → 귀환일 순서로 눌러요', 'Tap departure, then return')}
        </Text>
      </View>

      <View style={styles.chipRow}>
        {[0, 1, 2, 3].map((nights) => (
          <Pressable key={nights} accessibilityRole="button" onPress={() => quick(nights)} style={({ pressed }) => [styles.chip, pressed && styles.pressed]}>
            <Text variant="caption" weight="bold">{nights === 0 ? tx('당일치기', 'Day trip') : tx(`${nights}박 ${nights + 1}일`, `${nights} ${nights === 1 ? 'night' : 'nights'}`)}</Text>
          </Pressable>
        ))}
      </View>

      <View style={styles.monthNav}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 달', 'Previous month')} disabled={monthOffset <= 0} onPress={() => setMonthOffset((n) => n - 1)} style={[styles.navButton, monthOffset <= 0 && styles.navOff]}>
          <Text weight="bold" color={color.text.heading}>‹</Text>
        </Pressable>
        <View style={styles.monthBody}>
          {monthPickerOpen
            ? <MonthPicker today={today} selected={monthOffset} onPick={(offset) => { setMonthOffset(offset); setMonthPickerOpen(false); }} tx={tx} />
            : <MonthGrid {...month} value={{ ...EMPTY_START_BAR, startDate: value.startDate, endDate: value.endDate }} today={todayKey} onPick={pick} tx={tx} onPressTitle={() => setMonthPickerOpen(true)} maxDate={lastPickable} />}
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('다음 달', 'Next month')} disabled={monthOffset >= MAX_MONTH_OFFSET} onPress={() => setMonthOffset((n) => Math.min(MAX_MONTH_OFFSET, n + 1))} style={[styles.navButton, monthOffset >= MAX_MONTH_OFFSET && styles.navOff]}>
          <Text weight="bold" color={color.text.heading}>›</Text>
        </Pressable>
      </View>

      {lastPickable ? (
        <Text variant="caption" color={color.text.muted}>{txf(tx, '돌아오는 날은 %s까지 고를 수 있어요 (최대 7박)', 'You can come back as late as %s (up to 7 nights)', formatDateShort(lastPickable, tx))}</Text>
      ) : null}
      <Pressable accessibilityRole="button" accessibilityState={{ disabled: !complete }} disabled={!complete} onPress={onDone} style={[styles.done, !complete && styles.doneOff]}>
        <Text weight="bold" color={color.text.onAction}>{complete && label ? `${label} · ${tx('이 날짜로', 'Use these dates')}` : tx('귀환일까지 골라 주세요', 'Pick the return date too')}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, borderWidth: 1.5, borderColor: color.action.secondary, backgroundColor: color.surface.card },
  head: { gap: spacing[1] },
  headText: { gap: 2 },
  summary: {},
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 36, justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.soft },
  monthNav: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[1] },
  monthBody: { flex: 1 },
  navButton: { width: 32, height: 40, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full },
  navOff: { opacity: 0.3 },
  done: { minHeight: 48, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.action.primary },
  doneOff: { backgroundColor: color.surface.field },
  pressed: { opacity: 0.8 },
});

/**
 * 여행 만들기 1단계의 날짜 고르기(UI 캔버스 ⑤ PlanStep1After) — 빠른 칩은 카드 밖, 카드 안은 달 제목(왼쪽)·화살표(오른쪽)·달력·동백이 안내.
 * 🔴 규칙(첫 탭 출발 · 둘째 탭 귀환 · 최대 7박)은 위 DateRangeCard 와 같다 — 달력 칸(MonthGrid)도 같은 것을 쓴다.
 */
export function DateRangePicker({ value, onChange, tx, today = new Date() }: {
  value: DateRange;
  onChange: (next: DateRange) => void;
  tx: (ko: string, en: string) => string;
  today?: Date;
}) {
  const todayKey = toDateKey(today);
  const [monthOffset, setMonthOffset] = useState(() => monthOffsetOf(value.startDate, today));
  const [monthPickerOpen, setMonthPickerOpen] = useState(false);
  const month = useMemo(() => {
    const base = new Date(today.getFullYear(), today.getMonth() + monthOffset, 1);
    return { year: base.getFullYear(), month: base.getMonth() };
  }, [today, monthOffset]);
  const pickingEnd = Boolean(value.startDate && !value.endDate);
  const lastPickable = pickingEnd ? addDays(value.startDate, MAX_TRIP_NIGHTS) : undefined;
  const pick = (key: string) => {
    if (!value.startDate || value.endDate || key < value.startDate) onChange({ startDate: key, endDate: '' });
    else onChange({ startDate: value.startDate, endDate: key });
  };
  const quick = (nights: number) => {
    const start = value.startDate && value.startDate >= todayKey ? value.startDate : todayKey;
    onChange({ startDate: start, endDate: addDays(start, nights) });
  };
  const quickNights = value.startDate && value.endDate ? nightCount(value.startDate, value.endDate) : null;
  const canPrev = monthOffset > 0;
  const canNext = monthOffset < MAX_MONTH_OFFSET;
  return (
    <View style={pickerStyles.stack}>
      <View style={pickerStyles.chipRow}>
        {[0, 1, 2, 3].map((nights) => {
          const on = quickNights === nights;
          return (
            <Pressable key={nights} accessibilityRole="button" accessibilityState={{ selected: on }} onPress={() => quick(nights)} style={({ pressed }) => [pickerStyles.chip, on && pickerStyles.chipOn, pressed && pickerStyles.pressed]}>
              <Text variant="caption" weight="bold" color={on ? color.text.onAction : color.text.heading} numberOfLines={1}>{nights === 0 ? tx('당일치기', 'Day trip') : tx(`${nights}박 ${nights + 1}일`, `${nights} ${nights === 1 ? 'night' : 'nights'}`)}</Text>
            </Pressable>
          );
        })}
      </View>
      <View style={pickerStyles.card}>
        <View style={pickerStyles.head}>
          <Pressable accessibilityRole="button" accessibilityState={{ expanded: monthPickerOpen }} accessibilityLabel={tx('달 바로 고르기', 'Jump to a month')} onPress={() => setMonthPickerOpen((open) => !open)} style={pickerStyles.title}>
            <Text weight="bold">{tx(`${month.year}년 ${month.month + 1}월`, `${month.month + 1}/${month.year}`)}</Text>
            <Text variant="micro" color={color.text.muted}>{monthPickerOpen ? '▴' : '▾'}</Text>
          </Pressable>
          <View style={pickerStyles.arrows}>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 달', 'Previous month')} disabled={!canPrev} onPress={() => setMonthOffset((n) => n - 1)} style={pickerStyles.arrow}>
              <Svg width={18} height={18} viewBox="0 0 24 24" fill="none" stroke={canPrev ? color.text.heading : color.surface.field} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round"><Path d="M15 5l-7 7 7 7" /></Svg>
            </Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('다음 달', 'Next month')} disabled={!canNext} onPress={() => setMonthOffset((n) => Math.min(MAX_MONTH_OFFSET, n + 1))} style={pickerStyles.arrow}>
              <Svg width={18} height={18} viewBox="0 0 24 24" fill="none" stroke={canNext ? color.text.heading : color.surface.field} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round"><Path d="M9 5l7 7-7 7" /></Svg>
            </Pressable>
          </View>
        </View>
        {monthPickerOpen
          ? <MonthPicker today={today} selected={monthOffset} onPick={(offset) => { setMonthOffset(offset); setMonthPickerOpen(false); }} tx={tx} />
          : <MonthGrid {...month} hideTitle value={{ ...EMPTY_START_BAR, startDate: value.startDate, endDate: value.endDate }} today={todayKey} onPick={pick} tx={tx} maxDate={lastPickable} />}
        {/* 동백이 안내 — 지금 무엇을 누를 차례인지. 다 골랐으면 아래 요약 줄이 대신 말한다. */}
        {!value.endDate ? (
          <View accessibilityRole="text" style={pickerStyles.hint}>
            <GabolleMascot state="open" still style={pickerStyles.hintMascot} />
            {lastPickable ? (
              <Text variant="caption" color={color.text.body} style={pickerStyles.grow}>
                <Text variant="caption" weight="bold">{tx('돌아오는 날을 눌러요.', 'Now tap the day you come back.')}</Text>
                {' '}{txf(tx, '여행은 최대 7박 8일이라 %s까지 고를 수 있어요.', 'Trips are up to 7 nights, so you can pick until %s.', formatDateShort(lastPickable, tx))}
              </Text>
            ) : (
              <Text variant="caption" color={color.text.body} style={pickerStyles.grow}>
                <Text variant="caption" weight="bold">{tx('출발하는 날을 눌러요.', 'Tap the day you leave.')}</Text>
                {' '}{tx('그다음 돌아오는 날을 누르면 돼요.', 'Then tap the day you come back.')}
              </Text>
            )}
          </View>
        ) : null}
      </View>
    </View>
  );
}

const pickerStyles = StyleSheet.create({
  stack: { gap: spacing[3] },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 36, justifyContent: 'center', paddingHorizontal: 14, borderRadius: radius.chip, backgroundColor: color.surface.card },
  chipOn: { backgroundColor: color.action.secondary },
  card: { gap: 4, padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card },
  head: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 4, paddingBottom: 2 },
  title: { flexDirection: 'row', alignItems: 'center', gap: 6, minHeight: 36 },
  arrows: { flexDirection: 'row', gap: 4 },
  arrow: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center' },
  hint: { flexDirection: 'row', alignItems: 'center', gap: 10, marginTop: spacing[2], paddingVertical: 10, paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  hintMascot: { width: 28, height: 28 },
  grow: { flex: 1, minWidth: 0 },
  pressed: { opacity: 0.8 },
});
