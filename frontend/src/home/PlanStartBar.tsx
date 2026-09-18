// 홈의 여행 시작 바 — 출발지 · 날짜 · 인원을 홈에서 받는다.
// 시안: docs/design_handoff_plan_flow/PlanFlow.dc.html 의 p0.
import { useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, ActivityIndicator, Animated, Easing, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { resolveTextLanguage } from '@/i18n/languages';
import { MAJOR_BUSAN_ORIGINS, searchOrigins, type OriginCandidate } from '@/plan/origins';
import {
  EMPTY_START_BAR,
  START_BAR_PRESETS,
  addDays,
  canAskForPlan,
  dayCount,
  formatDateShort,
  parseDateKey,
  summarizeStartBar,
  toDateKey,
  type StartBarValue,
} from '@/home/startBarValue';

type Section = 'origin' | 'dates' | 'people' | null;

const WEEKDAY_HEADS_KO = ['일', '월', '화', '수', '목', '금', '토'];
const WEEKDAY_HEADS_EN = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

/** 한 달의 날짜 칸 — 앞쪽 빈칸은 null 이다. */
function monthCells(year: number, month: number): Array<string | null> {
  const first = new Date(year, month, 1);
  const days = new Date(year, month + 1, 0).getDate();
  const lead = first.getDay();
  return [
    ...Array.from({ length: lead }, () => null),
    ...Array.from({ length: days }, (_, index) => toDateKey(new Date(year, month, index + 1))),
  ];
}

function MonthGrid({
  year, month, value, today, onPick, ko,
}: {
  year: number; month: number; value: StartBarValue; today: string;
  onPick: (key: string) => void; ko: boolean;
}) {
  const cells = useMemo(() => monthCells(year, month), [year, month]);
  const heads = ko ? WEEKDAY_HEADS_KO : WEEKDAY_HEADS_EN;
  return (
    <View style={styles.month}>
      <Text variant="caption" weight="bold" style={styles.monthTitle}>
        {ko ? `${year}년 ${month + 1}월` : `${month + 1}/${year}`}
      </Text>
      <View style={styles.weekHead}>
        {heads.map((head, index) => (
          <Text key={`${head}-${index}`} variant="caption" color={color.text.muted} style={styles.headCell}>{head}</Text>
        ))}
      </View>
      <View style={styles.grid}>
        {cells.map((key, index) => {
          if (!key) return <View key={`blank-${index}`} style={styles.cell} />;
          const past = key < today;
          const isStart = key === value.startDate;
          const isEnd = key === value.endDate;
          const between = Boolean(value.startDate && value.endDate && key > value.startDate && key < value.endDate);
          return (
            <Pressable
              key={key}
              disabled={past}
              onPress={() => onPick(key)}
              accessibilityRole="button"
              accessibilityState={{ selected: isStart || isEnd, disabled: past }}
              accessibilityLabel={formatDateShort(key, ko)}
              style={[styles.cell, between && styles.cellBetween, (isStart || isEnd) && styles.cellPicked]}
            >
              <Text
                variant="caption"
                weight={isStart || isEnd ? 'bold' : 'regular'}
                // 지난 날짜를 숨기지 않고 흐리게 둔다. 사라지면 달력의 칸이 밀려서
                // 사람이 날짜를 잘못 짚는다.
                color={past ? '#c9c3ba' : isStart || isEnd ? color.text.onAction : color.text.heading}
              >
                {String(parseDateKey(key)?.getDate() ?? '')}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

export type PlanStartBarProps = {
  /** 넓은 화면이면 세 칸이 한 줄에 서고 달력이 두 달 보인다. */
  wide: boolean;
  accessToken: string | null;
  /** 「일정 물어보기」를 눌렀을 때. 채워진 값을 넘긴다. */
  onSubmit: (value: StartBarValue) => void;
  /** 오늘. 시험이 날짜에 안 흔들리게 밖에서 받는다. */
  today?: Date;
};

/** 폰에서 한 줄에 들어가는 개수. 390 폭에서 실측한 값이다. */
const PHONE_PRESET_COUNT = 3;

export function PlanStartBar({ wide, accessToken, onSubmit, today = new Date() }: PlanStartBarProps) {
  const { tx, language } = useI18n();
  // 🔴 「영어가 아니면 한국어」로 가르면 일본어·중국어 사용자가 한국어를 본다.
  // 그 언어들은 uiTranslated: false 라 번역이 없으면 영어로 떨어지기로 정해져 있다
  // (resolveTextLanguage). 그 규칙을 그대로 쓴다 — S15P21E201-1296.
  const ko = resolveTextLanguage(language) === 'ko';
  const [value, setValue] = useState<StartBarValue>(EMPTY_START_BAR);
  const [section, setSection] = useState<Section>(null);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<OriginCandidate[]>([]);
  const [searching, setSearching] = useState(false);
  const [monthOffset, setMonthOffset] = useState(0);
  const todayKey = toDateKey(today);
  const summary = summarizeStartBar(value, ko);
  const ready = canAskForPlan(value);
  const abortRef = useRef<AbortController | null>(null);

  // 출발지 검색 — 서버가 두 글자 미만을 거절하므로 나가기 전에 막는다.
  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length < 2) { setResults([]); setSearching(false); return; }
    const controller = new AbortController();
    abortRef.current?.abort();
    abortRef.current = controller;
    setSearching(true);
    const timer = setTimeout(async () => {
      const outcome = await searchOrigins(trimmed, accessToken, controller.signal);
      if (controller.signal.aborted) return;
      setSearching(false);
      // 실패해도 추천 목록은 그대로 둔다. 검색이 안 된다고 고를 수 없게 되면
      // 「서버가 죽으면 여행을 못 만든다」가 된다.
      setResults(outcome.state === 'success' ? outcome.items : []);
    }, 250);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [accessToken, query]);

  const pickOrigin = (candidate: OriginCandidate) => {
    setValue((prev) => ({ ...prev, origin: candidate.name, originLat: candidate.lat, originLng: candidate.lng }));
    setQuery('');
    setSection('dates');
  };

  const pickDate = (key: string) => {
    setValue((prev) => {
      // 첫 탭은 출발일, 두 번째 탭은 귀환일. 앞선 날짜를 다시 찍으면 처음부터 다시 고른다.
      if (!prev.startDate || prev.endDate || key < prev.startDate) return { ...prev, startDate: key, endDate: '' };
      return { ...prev, endDate: key };
    });
  };

  const monthBase = useMemo(() => {
    const base = new Date(today.getFullYear(), today.getMonth() + monthOffset, 1);
    return { year: base.getFullYear(), month: base.getMonth() };
  }, [today, monthOffset]);

  const secondMonth = useMemo(() => {
    const base = new Date(monthBase.year, monthBase.month + 1, 1);
    return { year: base.getFullYear(), month: base.getMonth() };
  }, [monthBase]);

  const segmentLabel = (which: Exclude<Section, null>) => {
    if (which === 'origin') return value.origin || tx('어디서 출발해요?', 'Where from?');
    if (which === 'dates') {
      if (!value.startDate) return tx('날짜 추가', 'Add dates');
      const days = dayCount(value.startDate, value.endDate || value.startDate);
      return `${formatDateShort(value.startDate, ko)}${value.endDate && value.endDate !== value.startDate ? ` – ${formatDateShort(value.endDate, ko)}` : ''} · ${ko ? `${days}일` : `${days}d`}`;
    }
    const total = value.adults + value.children;
    return total > 0 ? (ko ? `성인 ${value.adults}${value.children ? ` · 어린이 ${value.children}` : ''}` : `${total} travelers`) : tx('인원 추가', 'Add travelers');
  };

  // 두 가지가 움직인다.
  // · 고른 칸을 따라다니는 강조 알약 — 칸 사이를 미끄러진다
  // · 아래로 내려오는 패널 — 살짝 떠올랐다 제자리로
  const [reduceMotion, setReduceMotion] = useState(false);
  useEffect(() => {
    let alive = true;
    void AccessibilityInfo.isReduceMotionEnabled().then((on) => { if (alive) setReduceMotion(on); });
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    return () => { alive = false; sub.remove(); };
  }, []);

  /** 칸마다 잰 자리. 강조 알약이 이 값으로 움직인다. */
  const segmentBox = useRef<Record<string, { x: number; width: number }>>({});
  const highlightX = useRef(new Animated.Value(0)).current;
  const highlightW = useRef(new Animated.Value(0)).current;
  const highlightO = useRef(new Animated.Value(0)).current;
  const panelIn = useRef(new Animated.Value(0)).current;

  const moveHighlight = (which: Section) => {
    const box = which ? segmentBox.current[which] : null;
    const ms = reduceMotion ? 0 : 220;
    if (!box) {
      Animated.timing(highlightO, { toValue: 0, duration: ms, useNativeDriver: false }).start();
      return;
    }
    // 처음 켜질 때는 미끄러지지 않고 그 자리에서 뜬다 — 왼쪽 끝에서 날아오면 산만하다.
    const first = (highlightO as unknown as { _value: number })._value === 0;
    if (first) { highlightX.setValue(box.x); highlightW.setValue(box.width); }
    Animated.parallel([
      Animated.timing(highlightX, { toValue: box.x, duration: first ? 0 : ms, easing: Easing.out(Easing.cubic), useNativeDriver: false }),
      Animated.timing(highlightW, { toValue: box.width, duration: first ? 0 : ms, easing: Easing.out(Easing.cubic), useNativeDriver: false }),
      Animated.timing(highlightO, { toValue: 1, duration: ms, useNativeDriver: false }),
    ]).start();
  };

  useEffect(() => {
    moveHighlight(section);
    Animated.timing(panelIn, {
      toValue: section ? 1 : 0,
      duration: reduceMotion ? 0 : section ? 240 : 160,
      easing: section ? Easing.out(Easing.cubic) : Easing.in(Easing.cubic),
      useNativeDriver: false,
    }).start();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [section, reduceMotion]);

  const toggle = (which: Exclude<Section, null>) => setSection((prev) => (prev === which ? null : which));

  const submit = () => { if (ready) onSubmit(value); };

  const originPanel = (
    <View style={styles.panel}>
      <TextInput
        value={query}
        onChangeText={setQuery}
        placeholder={tx('출발지를 검색해 보세요', 'Search a starting point')}
        placeholderTextColor={color.text.muted}
        style={styles.search}
        accessibilityLabel={tx('출발지 검색', 'Search starting point')}
      />
      {searching ? <ActivityIndicator color={color.brand.orange} /> : null}
      <Text variant="caption" color={color.text.muted}>{tx('추천 출발지', 'Suggested starting points')}</Text>
      {(results.length ? results : MAJOR_BUSAN_ORIGINS).map((candidate) => (
        <Pressable key={candidate.externalId} onPress={() => pickOrigin(candidate)} accessibilityRole="button" style={styles.originRow}>
          <Text weight="bold">{candidate.name}</Text>
          <Text variant="caption" color={color.text.muted}>{candidate.address}</Text>
        </Pressable>
      ))}
    </View>
  );

  const datePanel = (
    <View style={styles.panel}>
      <View style={styles.monthNav}>
        <Pressable
          onPress={() => setMonthOffset((n) => Math.max(0, n - 1))}
          disabled={monthOffset === 0}
          accessibilityRole="button"
          accessibilityLabel={tx('이전 달', 'Previous month')}
          style={styles.navButton}
        >
          <Text weight="bold" color={monthOffset === 0 ? '#c9c3ba' : color.text.heading}>‹</Text>
        </Pressable>
        <Pressable onPress={() => setMonthOffset((n) => n + 1)} accessibilityRole="button" accessibilityLabel={tx('다음 달', 'Next month')} style={styles.navButton}>
          <Text weight="bold">›</Text>
        </Pressable>
      </View>
      <View style={wide ? styles.monthRow : undefined}>
        <MonthGrid {...monthBase} value={value} today={todayKey} onPick={pickDate} ko={ko} />
        {wide ? <MonthGrid {...secondMonth} value={value} today={todayKey} onPick={pickDate} ko={ko} /> : null}
      </View>
      <View style={styles.chipRow}>
        {[0, 1, 2, 3].map((nights) => (
          <Pressable
            key={nights}
            accessibilityRole="button"
            onPress={() => setValue((prev) => {
              const start = prev.startDate && prev.startDate >= todayKey ? prev.startDate : todayKey;
              return { ...prev, startDate: start, endDate: addDays(start, nights) };
            })}
            style={styles.chip}
          >
            <Text variant="caption" weight="bold">
              {nights === 0 ? tx('당일치기', 'Day trip') : ko ? `${nights}박 ${nights + 1}일` : `${nights} nights`}
            </Text>
          </Pressable>
        ))}
      </View>
    </View>
  );

  const peoplePanel = (
    <View style={styles.panel}>
      {([
        { key: 'adults' as const, ko: '성인', en: 'Adults', hint: ko ? '만 13세 이상' : 'Age 13+' },
        { key: 'children' as const, ko: '어린이', en: 'Children', hint: ko ? '만 2~12세' : 'Age 2–12' },
      ]).map((row) => (
        <View key={row.key} style={styles.counterRow}>
          <View>
            <Text weight="bold">{ko ? row.ko : row.en}</Text>
            <Text variant="caption" color={color.text.muted}>{row.hint}</Text>
          </View>
          <View style={styles.counter}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={tx(`${row.ko} 줄이기`, `Fewer ${row.en}`)}
              onPress={() => setValue((prev) => ({ ...prev, [row.key]: Math.max(row.key === 'adults' ? 1 : 0, prev[row.key] - 1) }))}
              style={styles.counterButton}
            >
              <Text weight="bold">−</Text>
            </Pressable>
            <Text weight="bold" style={styles.counterValue}>{value[row.key]}</Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={tx(`${row.ko} 늘리기`, `More ${row.en}`)}
              onPress={() => setValue((prev) => ({ ...prev, [row.key]: Math.min(20, prev[row.key] + 1) }))}
              style={styles.counterButton}
            >
              <Text weight="bold">+</Text>
            </Pressable>
          </View>
        </View>
      ))}
    </View>
  );

  const panel = section === 'origin' ? originPanel : section === 'dates' ? datePanel : section === 'people' ? peoplePanel : null;

  return (
    <View style={styles.root}>
      {wide ? (
        <View style={styles.bar}>
          {/* 고른 칸을 따라다니는 강조 알약. 칸 뒤에 깔리므로 글자를 안 가린다. */}
          <Animated.View
            pointerEvents="none"
            style={[styles.highlight, { left: highlightX, width: highlightW, opacity: highlightO }]}
          />
          {(['origin', 'dates', 'people'] as const).map((which, index) => (
            <Pressable
              key={which}
              onPress={() => toggle(which)}
              onLayout={(event) => {
                const { x, width } = event.nativeEvent.layout;
                segmentBox.current[which] = { x, width };
                if (section === which) moveHighlight(which);
              }}
              accessibilityRole="button"
              accessibilityState={{ expanded: section === which }}
              style={[styles.segment, index > 0 && styles.segmentDivider]}
            >
              <Text variant="caption" color={color.text.muted}>
                {which === 'origin' ? tx('출발지', 'From') : which === 'dates' ? tx('날짜', 'Dates') : tx('인원', 'Travelers')}
              </Text>
              <Text weight="bold" numberOfLines={1}>{segmentLabel(which)}</Text>
            </Pressable>
          ))}
          <Pressable
            onPress={submit}
            disabled={!ready}
            accessibilityRole="button"
            style={[styles.cta, !ready && styles.ctaOff]}
          >
            <Text weight="bold" color={color.text.onAction}>{tx('일정 물어보기', 'Ask for a plan')}</Text>
          </Pressable>
        </View>
      ) : (
        <Pressable
          onPress={() => setSection((prev) => (prev ? null : 'origin'))}
          accessibilityRole="button"
          accessibilityState={{ expanded: section !== null }}
          style={styles.phonePill}
        >
          <Text weight="bold" numberOfLines={1} color={summary ? color.text.heading : color.text.muted}>
            {summary || tx('여행 계획 시작해 보세요', 'Start planning your trip')}
          </Text>
        </Pressable>
      )}

      {panel ? (
        <Animated.View
          style={[
            styles.panelShell,
            wide && styles.panelShellWide,
            {
              opacity: panelIn,
              transform: [{ translateY: panelIn.interpolate({ inputRange: [0, 1], outputRange: [-8, 0] }) }],
            },
          ]}
        >
          {!wide ? (
            <View style={styles.phoneTabs}>
              {(['origin', 'dates', 'people'] as const).map((which) => (
                <Pressable key={which} onPress={() => setSection(which)} accessibilityRole="button" style={[styles.phoneTab, section === which && styles.phoneTabOn]}>
                  <Text variant="caption" weight="bold" color={section === which ? color.text.onAction : color.text.heading}>
                    {which === 'origin' ? tx('출발지', 'From') : which === 'dates' ? tx('날짜', 'Dates') : tx('인원', 'Travelers')}
                  </Text>
                </Pressable>
              ))}
              <Pressable onPress={() => setSection(null)} accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} style={styles.phoneTab}>
                <Text weight="bold">✕</Text>
              </Pressable>
            </View>
          ) : null}
          <ScrollView style={styles.panelScroll} keyboardShouldPersistTaps="handled">{panel}</ScrollView>
          {!wide ? (
            <Pressable onPress={submit} disabled={!ready} accessibilityRole="button" style={[styles.cta, styles.ctaWide, !ready && styles.ctaOff]}>
              <Text weight="bold" color={color.text.onAction}>{tx('일정 물어보기', 'Ask for a plan')}</Text>
            </Pressable>
          ) : null}
        </Animated.View>
      ) : null}

 {/* 폰에서는 한 줄에 맞춘다 (2026-09-18 지시). 390 폭에 네 개는 두 줄이 되고,
          두 번째 줄에 한 개만 남아 어색했다. 폰은 앞의 셋만 보여 준다 — 넷째(「부산역 출발」)는
          출발지 칸에서 바로 고를 수 있어 없어도 길이 막히지 않는다. 넓은 화면은 넷 다 그대로다. */}
      <View style={styles.chipRow}>
        {(wide ? START_BAR_PRESETS : START_BAR_PRESETS.slice(0, PHONE_PRESET_COUNT)).map((preset) => (
          <Pressable
            key={preset.id}
            accessibilityRole="button"
            onPress={() => setValue((prev) => ({ ...prev, ...preset.apply(today) }))}
            style={styles.chip}
          >
            <Text variant="caption" weight="bold">{ko ? preset.ko : preset.en}</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { width: '100%', gap: spacing[3], zIndex: 10 },
  bar: {
    flexDirection: 'row', alignItems: 'center', alignSelf: 'center', width: '100%', maxWidth: 860, minHeight: 72,
    padding: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border,
  },
  segment: { flex: 1, paddingHorizontal: spacing[4], paddingVertical: spacing[2], borderRadius: radius.full, gap: 2 },
  segmentDivider: { borderLeftWidth: 1, borderLeftColor: color.surface.border },
  // 고른 칸을 따라다니는 강조 알약. 칸마다 배경을 켜고 끄면 뚝뚝 끊겨 보인다
  // 하나를 깔고 자리만 옮기면 미끄러진다(에어비앤비가 그렇게 한다).
  // 글자를 가리지 않도록 칸 뒤에 깔고 pointerEvents 를 끈다.
  highlight: { position: 'absolute', top: spacing[2], bottom: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft },
  cta: { minHeight: 56, paddingHorizontal: spacing[6], borderRadius: radius.full, backgroundColor: color.brand.orange, alignItems: 'center', justifyContent: 'center' },
  ctaWide: { alignSelf: 'stretch', marginTop: spacing[3] },
  ctaOff: { backgroundColor: '#c9c3ba' },
  phonePill: {
    minHeight: 56, paddingHorizontal: spacing[4], justifyContent: 'center',
    borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border,
  },
  panelShell: { marginTop: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  panelShellWide: { alignSelf: 'center', width: '100%', maxWidth: 860 },
  panelScroll: { maxHeight: 420 },
  panel: { gap: spacing[3] },
  phoneTabs: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], marginBottom: spacing[3] },
  phoneTab: { minHeight: 36, paddingHorizontal: spacing[3], justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.soft },
  phoneTabOn: { backgroundColor: color.brand.navy },
  search: { minHeight: 48, paddingHorizontal: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, color: color.text.heading },
  originRow: { minHeight: 48, justifyContent: 'center', paddingVertical: spacing[1] },
  monthNav: { flexDirection: 'row', justifyContent: 'flex-end', gap: spacing[2] },
  navButton: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.soft },
  monthRow: { flexDirection: 'row', gap: spacing[6] },
  month: { flex: 1, gap: spacing[2] },
  monthTitle: { textAlign: 'center' },
  weekHead: { flexDirection: 'row' },
  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  cell: { width: `${100 / 7}%`, height: 40, alignItems: 'center', justifyContent: 'center' },
  headCell: { width: `${100 / 7}%`, textAlign: 'center' },
  cellBetween: { backgroundColor: color.surface.warm },
  cellPicked: { backgroundColor: color.brand.navy, borderRadius: radius.full },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: spacing[2] },
  chip: { minHeight: 32, paddingHorizontal: spacing[3], justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.soft },
  counterRow: { minHeight: 56, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  counter: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  counterButton: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field },
  counterValue: { minWidth: 24, textAlign: 'center' },
});
