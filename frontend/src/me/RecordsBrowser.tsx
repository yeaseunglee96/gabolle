// 마이페이지 「기록」 — 격자 | 달력 보기, 지역·#태그 칩으로 거르기 (S15P21E201-1444).
//
// 🔴 왜 달력인가 — 격자는 «무엇을 썼는지»는 보여 주지만 «언제»는 못 보여 준다. 기록이
//    쌓이면 「그때 어디 갔더라」를 되찾을 길이 위로 끝없이 스크롤하는 것뿐이었다.
//    달력은 그날 첫 사진을 칸에 넣는다 — 날짜를 훑는 것이 곧 여행을 훑는 것이 된다.
//
// 🔴 태그는 서버에 없다. 본문의 #해시태그를 프론트가 뽑는다(recordsBrowse.ts). 폰과 넓은
//    화면이 같은 부품을 쓴다 — 두 벌로 만들면 한쪽만 고쳐진다.
import { useMemo, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { Button } from '@/components/Button';
import { PencilIcon } from '@/components/PencilIcon';
import { Text } from '@/components/Text';
import { formatDayHeading } from '@/i18n/datetime';
import { color, radius, spacing } from '@/design/tokens';
import { RecordCard } from '@/me/RecordCard';
import { filterStories, groupByDay, latestMonth, monthCells, regionsOf, shiftMonth, tagsOf, type RecordsFilter } from '@/me/recordsBrowse';
import type { StoryDto } from '@/social/stories';
import { regionText } from '@/social/districtNames';

type View_ = 'grid' | 'calendar';

export function RecordsBrowser({
  stories, tx, locale, onOpen, onCompose, cardWidth, testID,
}: {
  stories: StoryDto[];
  tx: (ko: string, en: string) => string;
  locale: string;
  onOpen: (story: StoryDto) => void;
  /** 「새 기록 남기기」 타일. 남의 프로필에는 없다 — 안 주면 안 그린다. */
  onCompose?: () => void;
  /** 넓은 화면은 4열이라 폭을 밖에서 준다. 안 주면 폰 격자의 2열. */
  cardWidth?: number;
  testID?: string;
}) {
  const [view, setView] = useState<View_>('grid');
  const [filter, setFilter] = useState<RecordsFilter>({ region: null, tag: null });
  const regions = useMemo(() => regionsOf(stories), [stories]);
  const tags = useMemo(() => tagsOf(stories), [stories]);
  const shown = useMemo(() => filterStories(stories, filter), [stories, filter]);
  const filtering = Boolean(filter.region || filter.tag);

  return (
    <View testID={testID} style={styles.root}>
      <View style={styles.toolbar}>
        <View accessibilityRole="tablist" style={styles.viewSwitch}>
          {(['grid', 'calendar'] as const).map((key) => (
            <Pressable key={key} accessibilityRole="tab" accessibilityState={{ selected: view === key }} onPress={() => setView(key)} style={[styles.viewItem, view === key && styles.viewItemOn]}>
              <Text variant="caption" weight="bold" color={view === key ? color.text.onAction : color.text.body}>{key === 'grid' ? tx('격자', 'Grid') : tx('달력', 'Calendar')}</Text>
            </Pressable>
          ))}
        </View>
        {filtering ? (
          <Pressable accessibilityRole="button" onPress={() => setFilter({ region: null, tag: null })} hitSlop={8}>
            <Text variant="caption" weight="bold" color={color.action.outline}>{tx('모두 보기', 'Show all')}</Text>
          </Pressable>
        ) : null}
      </View>

      {/* 지역·태그 칩 — 하나도 없으면 줄 자체를 안 그린다. 빈 줄은 「뭔가 빠졌나」로 읽힌다. */}
      {regions.length + tags.length > 0 ? (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
          {regions.map((region) => {
            const on = filter.region === region;
            return <Chip key={'r:' + region} label={regionText(region, tx)} on={on} onPress={() => setFilter((f) => ({ ...f, region: on ? null : region }))} />;
          })}
          {tags.map((tag) => {
            const on = filter.tag === tag;
            return <Chip key={'t:' + tag} label={'#' + tag} on={on} onPress={() => setFilter((f) => ({ ...f, tag: on ? null : tag }))} />;
          })}
        </ScrollView>
      ) : null}

      {filtering && shown.length === 0 ? (
        <View style={styles.none}>
          <Text weight="bold">{tx('이 조건에 맞는 기록이 없어요', 'No records match these filters')}</Text>
          <Button label={tx('모두 보기', 'Show all')} variant="tertiary" onPress={() => setFilter({ region: null, tag: null })} containerStyle={styles.noneCta} />
        </View>
      ) : view === 'grid' ? (
        <View style={styles.grid}>
          {shown.map((story) => <RecordCard key={story.id} story={story} width={cardWidth} onPress={() => onOpen(story)} tx={tx} />)}
          {onCompose && !filtering ? (
            <Pressable accessibilityRole="button" onPress={onCompose} style={[styles.recordNew, cardWidth ? { width: cardWidth } : null]}>
              <View style={styles.recordNewIcon}><PencilIcon tint={color.text.onAction} size={18} /></View>
              <Text weight="bold" numberOfLines={1}>{tx('새 기록 남기기', 'Write a record')}</Text>
              <Text variant="caption" color={color.text.muted} numberOfLines={1}>{tx('사진 3장까지', 'Up to 3 photos')}</Text>
            </Pressable>
          ) : null}
        </View>
      ) : (
        <RecordsCalendar stories={shown} tx={tx} locale={locale} onOpen={onOpen} cardWidth={cardWidth} />
      )}
    </View>
  );
}

function Chip({ label, on, onPress }: { label: string; on: boolean; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: on }} onPress={onPress} style={[styles.chip, on && styles.chipOn]}>
      <Text variant="caption" weight="bold" color={on ? color.text.onAction : color.text.body}>{label}</Text>
    </Pressable>
  );
}

/** 「2026년 9월」 · 「September 2026」 · 「2026年9月」. Intl 이 그 로케일을 모르면 숫자로. */
function monthTitle(year: number, month0: number, locale: string): string {
  try {
    if (Intl.DateTimeFormat.supportedLocalesOf([locale]).length > 0) return new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'long' }).format(new Date(year, month0, 1));
  }
  catch { /* 아래 숫자로 */ }
  return `${year}. ${month0 + 1}.`;
}

/** 일~토 — 그 로케일의 짧은 요일. 2026-08-30 이 일요일이다. */
function weekdayLabels(locale: string): string[] {
  try {
    if (Intl.DateTimeFormat.supportedLocalesOf([locale]).length > 0) {
      const format = new Intl.DateTimeFormat(locale, { weekday: 'short' });
      return [0, 1, 2, 3, 4, 5, 6].map((i) => format.format(new Date(2026, 7, 30 + i)));
    }
  }
  catch { /* 아래 영어로 */ }
  return ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
}

function RecordsCalendar({ stories, tx, locale, onOpen, cardWidth }: { stories: StoryDto[]; tx: (ko: string, en: string) => string; locale: string; onOpen: (story: StoryDto) => void; cardWidth?: number }) {
  const [month, setMonth] = useState(() => latestMonth(stories));
  const [selected, setSelected] = useState<string | null>(null);
  const byDay = useMemo(() => groupByDay(stories), [stories]);
  const cells = useMemo(() => monthCells(month.year, month.month0), [month]);
  const weekdays = useMemo(() => weekdayLabels(locale), [locale]);
  const monthCount = cells.reduce((sum, cell) => sum + (cell.inMonth ? (byDay.get(cell.key)?.length ?? 0) : 0), 0);
  const dayStories = selected ? byDay.get(selected) ?? [] : null;

  const dayPanel = dayStories === null ? (
        <Text variant="caption" color={color.text.muted} style={styles.hint}>{tx('날짜를 누르면 그날 기록이 나와요', 'Tap a day to see its records')}</Text>
      ) : dayStories.length === 0 ? (
        <Text variant="caption" color={color.text.muted} style={styles.hint}>{tx('이날은 기록이 없어요', 'No records on this day')}</Text>
      ) : (
        <View style={styles.dayList}>
          <Text weight="bold">{formatDayHeading(selected ?? '', locale) ?? selected}</Text>
          <View style={styles.grid}>
          {dayStories.map((story) => <RecordCard key={story.id} story={story} width={cardWidth} onPress={() => onOpen(story)} tx={tx} />)}
          </View>
        </View>
      );

  return (
    <View style={cardWidth ? styles.calendarRow : undefined}>
      <View style={styles.calendar}>
        <View style={styles.monthRow}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('지난달', 'Previous month')} onPress={() => { setMonth((m) => shiftMonth(m, -1)); setSelected(null); }} style={styles.monthNav}><Text weight="bold">‹</Text></Pressable>
        <View style={styles.monthTitle}>
          <Text weight="bold">{monthTitle(month.year, month.month0, locale)}</Text>
          <Text variant="caption" color={color.text.muted}>{monthCount === 0 ? tx('이달은 기록이 없어요', 'No records this month') : tx(`기록 ${monthCount}개`, `${monthCount} records`)}</Text>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('다음달', 'Next month')} onPress={() => { setMonth((m) => shiftMonth(m, 1)); setSelected(null); }} style={styles.monthNav}><Text weight="bold">›</Text></Pressable>
      </View>
      <View style={styles.weekRow}>
        {weekdays.map((label, i) => <Text key={label + i} variant="caption" weight="bold" color={i === 0 ? color.action.outline : color.text.muted} style={styles.weekday}>{label}</Text>)}
      </View>
      <View style={styles.cells}>
        {cells.map((cell) => {
          const list = byDay.get(cell.key) ?? [];
          const cover = list.find((s) => s.images[0]?.url)?.images[0]?.url ?? null;
          const on = selected === cell.key;
          return (
            <Pressable
              key={cell.key}
              accessibilityRole="button"
              accessibilityLabel={list.length ? `${cell.key} · ${list.length}` : cell.key}
              accessibilityState={{ selected: on }}
              disabled={!cell.inMonth}
              onPress={() => setSelected(on ? null : cell.key)}
              style={[styles.cell, !cell.inMonth && styles.cellOut, on && styles.cellOn]}
            >
              {cover ? <Image source={{ uri: cover }} resizeMode="cover" accessibilityLabel="" style={styles.cellCover} /> : null}
              {list.length > 0 && !cover ? <View style={styles.cellDot} /> : null}
              <Text variant="caption" weight={list.length ? 'bold' : 'regular'} color={cover ? color.text.onAction : cell.inMonth ? color.text.heading : color.text.muted} style={[styles.cellDay, cover && styles.cellDayOnCover]}>{cell.day}</Text>
              {list.length > 1 ? <View style={styles.cellBadge}><Text variant="util" weight="bold" color={color.text.onAction}>{list.length}</Text></View> : null}
            </Pressable>
          );
        })}
      </View>
      </View>
      {cardWidth ? <View style={styles.side}>{dayPanel}</View> : dayPanel}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { marginTop: spacing[3], gap: spacing[3] },
  toolbar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] },
  viewSwitch: { flexDirection: 'row', padding: 2, borderRadius: radius.full, backgroundColor: color.surface.soft },
  viewItem: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' },
  viewItemOn: { backgroundColor: color.brand.navy },
  chips: { flexDirection: 'row', gap: spacing[1] },
  chip: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, justifyContent: 'center', borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  chipOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  none: { alignItems: 'center', gap: spacing[2], paddingVertical: spacing[6] },
  noneCta: { alignSelf: 'center' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3] },
  recordNew: {
    width: '48%', borderRadius: radius.lg, borderWidth: 1, borderStyle: 'dashed', borderColor: color.surface.field,
    alignItems: 'center', justifyContent: 'center', gap: spacing[1], padding: spacing[4], minHeight: 160,
  },
  recordNewIcon: { width: 40, height: 40, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.primary },
  // 넓은 화면에서 7칸이 화면 폭을 다 먹으면 칸 하나가 180px 이 된다 — 달력이 아니라 사진첩이 된다. 폰 폭 정도로 묶는다.
  calendar: { gap: spacing[2], width: '100%', maxWidth: 520 },
  calendarRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[6], flexWrap: 'wrap' },
  side: { flex: 1, minWidth: 260 },
  monthRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  monthNav: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.soft },
  monthTitle: { alignItems: 'center', gap: 2 },
  weekRow: { flexDirection: 'row' },
  weekday: { flex: 1, textAlign: 'center' },
  cells: { flexDirection: 'row', flexWrap: 'wrap' },
  // 🔴 7칸이 정확히 한 줄 — gap 을 쓰면 폭 계산이 어긋나 마지막 칸이 다음 줄로 떨어진다. 칸 안 여백으로 띄운다.
  cell: { width: `${100 / 7}%`, aspectRatio: 1, padding: 3, borderRadius: radius.md, borderWidth: 2, borderColor: 'transparent' },
  cellOut: { opacity: 0.35 },
  // 고른 날은 테두리로 — 사진이 칸을 다 채우면 배경색으로는 티가 안 난다.
  cellOn: { borderColor: color.brand.navy, backgroundColor: color.surface.soft },
  cellCover: { position: 'absolute', top: 1, left: 1, right: 1, bottom: 1, borderRadius: radius.sm, zIndex: 0 },
  cellDay: { position: 'absolute', top: 6, left: 0, right: 0, textAlign: 'center' },
  cellDayOnCover: { textShadowColor: 'rgba(0,0,0,0.6)', textShadowRadius: 4 },
  cellDot: { position: 'absolute', bottom: 8, alignSelf: 'center', width: 6, height: 6, borderRadius: radius.full, backgroundColor: color.brand.navy },
  cellBadge: { position: 'absolute', right: 4, bottom: 4, minWidth: 18, height: 18, paddingHorizontal: 4, borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' },
  hint: { textAlign: 'center', paddingVertical: spacing[3] },
  dayList: { gap: spacing[2], marginTop: spacing[2] },
});
