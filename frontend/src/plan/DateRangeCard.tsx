// 문항 화면 안의 날짜 카드 — S15P21E201-1376.
//
// 🔴 예전에는 날짜가 없으면 「날짜 정하기」가 홈으로 보냈다. 돌아오면 문항이 1번부터였다
//    (답은 남아 있었지만 자리가 초기화됐다). 열 문항을 다 답한 사람에게 그것은 처음부터
//    다시 하라는 말이다 — 2026-09-21 실기. 날짜는 그 자리에서 고른다.
//
// 달력 칸(MonthGrid)은 홈 시작 줄의 것을 그대로 쓴다 — 같은 달력이 두 벌이면 하나만 고쳐진다.
import { useMemo, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { MonthGrid } from '@/home/PlanStartBar';
import { EMPTY_START_BAR, addDays, formatDateShort, nightCount, toDateKey } from '@/home/startBarValue';

export type DateRange = { startDate: string; endDate: string };

/** 「10.3(토) – 10.5(월) · 2박」. 날짜가 없으면 null. */
export function dateRangeLabel(value: DateRange, tx: (ko: string, en: string) => string): string | null {
  if (!value.startDate) return null;
  const end = value.endDate || value.startDate;
  const range = end !== value.startDate ? `${formatDateShort(value.startDate, tx)} – ${formatDateShort(end, tx)}` : formatDateShort(value.startDate, tx);
  const nights = nightCount(value.startDate, end);
  return `${range} · ${nights > 0 ? tx(`${nights}박`, `${nights} nights`) : tx('당일치기', 'Day trip')}`;
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
  const [monthOffset, setMonthOffset] = useState(0);
  const month = useMemo(() => {
    const base = new Date(today.getFullYear(), today.getMonth() + monthOffset, 1);
    return { year: base.getFullYear(), month: base.getMonth() };
  }, [today, monthOffset]);
  const complete = Boolean(value.startDate && value.endDate);
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
            <Text variant="caption" weight="bold">{nights === 0 ? tx('당일치기', 'Day trip') : tx(`${nights}박 ${nights + 1}일`, `${nights} nights`)}</Text>
          </Pressable>
        ))}
      </View>

      <View style={styles.monthNav}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 달', 'Previous month')} disabled={monthOffset <= 0} onPress={() => setMonthOffset((n) => n - 1)} style={[styles.navButton, monthOffset <= 0 && styles.navOff]}>
          <Text weight="bold" color={color.text.heading}>‹</Text>
        </Pressable>
        <View style={styles.monthBody}>
          <MonthGrid {...month} value={{ ...EMPTY_START_BAR, startDate: value.startDate, endDate: value.endDate }} today={todayKey} onPick={pick} tx={tx} />
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('다음 달', 'Next month')} disabled={monthOffset >= 11} onPress={() => setMonthOffset((n) => n + 1)} style={[styles.navButton, monthOffset >= 11 && styles.navOff]}>
          <Text weight="bold" color={color.text.heading}>›</Text>
        </Pressable>
      </View>

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
