// 홈의 여행 시작 바 — 출발지 · 날짜 · 인원을 홈에서 받는다.
// 시안: docs/design_handoff_plan_flow/PlanFlow.dc.html 의 p0.
import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, ActivityIndicator, Animated, BackHandler, Easing, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { resolveTextLanguage } from '@/i18n/languages';
import { isOvernight } from '@/plan/lodgingRequired';
import { lodgingSnapshotOf, MAJOR_BUSAN_ORIGINS, RECOMMENDED_LODGING_AREAS, attachEnglishNames, searchOrigins, type OriginCandidate, type OriginSearchResult } from '@/plan/origins';
import { searchPlacesByName, type PlaceSearchItem } from '@/discovery/places';
import { stopNameForLanguage } from '@/discovery/romanize';
import { addressForLanguage } from '@/discovery/localAddress';
import { MonthPicker } from '@/home/MonthPicker';
import { MAX_MONTH_OFFSET, monthOffsetOf } from '@/home/monthJump';
import {
  EMPTY_START_BAR,
  type StartBarSection,
  START_BAR_PRESETS,
  addDays,
  askForPlanBlocker,
  canAskForPlan,
  dayCount,
  formatDateShort,
  parseDateKey,
  placeEnglishOf,
  startBarPlaceName,
  summarizeStartBar,
  toDateKey,
  type StartBarValue,
  MAX_TRIP_NIGHTS,
} from '@/home/startBarValue';
import { txf } from '@/i18n/format';

type Section = StartBarSection;

/**
 * 패널이 들어올 때 쓰는 가속 곡선 — 빨리 시작해 길게 멎는다.
 * 기본 ease-out 보다 끝이 길어서 «내려앉는» 느낌이 난다. 닫힘에는 안 쓴다 —
 * 닫는 것은 빨리 치워 주는 편이 낫다.
 */
const EASE_SOFT = Easing.bezier(0.22, 1, 0.36, 1);

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

/** 한 주가 일곱 칸. */
export const DAYS_IN_WEEK = 7;

/**
 * 🔴 <b>달력을 주 단위 줄로 묶는다 — S15P21E201-1434.</b>
 *
 * <p>전에는 칸을 한 줄에 쏟아 놓고 {@code flexWrap} 으로 접었다. 칸 폭이 {@code 100/7 %}
 * (14.2857…%)인데 React Native(Yoga)는 칸마다 픽셀 격자에 맞춰 반올림하므로, 올림이 쌓여
 * 일곱 칸 합이 부모 폭을 넘고 <b>마지막 칸이 다음 줄로 밀린다.</b> 요일 머리는 접히지 않아
 * 그대로 일곱이 서서, 실기에서 21일(월)이 「목」 칸에 있었다. 웹은 CSS 백분율이라 재현되지
 * 않는다 — 그래서 눈으로도 자동 검사로도 안 잡혔다.
 *
 * <p>줄바꿈에 기대지 않으면 이 문제가 생길 자리가 없다. 한 줄에 일곱 칸을 직접 넣고 칸은
 * 줄 폭을 나눠 갖는다({@code flex: 1}).
 *
 * <p>마지막 주는 빈 칸으로 일곱을 채운다 — 안 채우면 남은 칸들이 늘어나 그 주만 칸이 넓어진다.
 */
export function monthWeeks(year: number, month: number): Array<Array<string | null>> {
  const cells = monthCells(year, month);
  const weeks: Array<Array<string | null>> = [];
  for (let index = 0; index < cells.length; index += DAYS_IN_WEEK) {
    const week = cells.slice(index, index + DAYS_IN_WEEK);
    while (week.length < DAYS_IN_WEEK) week.push(null);
    weeks.push(week);
  }
  return weeks;
}

export function MonthGrid({
  year, month, value, today, onPick, tx, onPressTitle, titleOpen = false, maxDate, hideTitle = false,
}: {
  /** 부르는 쪽이 달 제목을 따로 그린다(여행 만들기 1단계 — 제목 왼쪽 · 화살표 오른쪽). */
  hideTitle?: boolean;
  year: number; month: number; value: StartBarValue; today: string;
  /**
   * 이 날 뒤는 못 고른다 — 출발일을 찍고 돌아오는 날을 고르는 동안(최대 7박, TripConditionRules.MAX_NIGHTS).
   * 🔴 전에는 17일 뒤도 눌리고, 끝까지 답한 뒤 서버가 거절했다(UI 캔버스 ⑤). 흐리게 + 줄을 그어 둔다.
   */
  maxDate?: string;
  onPick: (key: string) => void; tx: (ko: string, en: string) => string;
  /** 있으면 제목을 눌러 «달 바로 고르기»를 연다 — S15P21E201-1539. */
  onPressTitle?: () => void;
  titleOpen?: boolean;
}) {
  const weeks = useMemo(() => monthWeeks(year, month), [year, month]);
  const heads = WEEKDAY_HEADS_KO.map((head, index) => tx(head, WEEKDAY_HEADS_EN[index]));
  return (
    <View style={styles.month}>
      {hideTitle ? null : onPressTitle
        ? <Pressable accessibilityRole="button" accessibilityState={{ expanded: titleOpen }} onPress={onPressTitle} style={styles.monthTitleButton}>
            <Text variant="caption" weight="bold" style={styles.monthTitle}>
              {tx(`${year}년 ${month + 1}월`, `${month + 1}/${year}`)} {titleOpen ? '▴' : '▾'}
            </Text>
          </Pressable>
        : <Text variant="caption" weight="bold" style={styles.monthTitle}>
            {tx(`${year}년 ${month + 1}월`, `${month + 1}/${year}`)}
          </Text>}
      <View style={styles.weekHead}>
        {heads.map((head, index) => (
          <Text key={`${head}-${index}`} variant="caption" color={color.text.muted} style={styles.headCell}>{head}</Text>
        ))}
      </View>
      <View style={styles.grid}>
        {weeks.map((week, weekIndex) => (
        <View key={`week-${weekIndex}`} style={styles.week}>
        {week.map((key, index) => {
          if (!key) return <View key={`blank-${index}`} style={styles.cell} />;
          const past = key < today;
          const beyond = Boolean(maxDate && key > maxDate);
          const isStart = key === value.startDate;
          const isEnd = key === value.endDate;
          // 🔴 고른 기간은 한 띠로 이어진다(S15P21E201-1720). 전에는 시작·끝 칸을 칸 전체 검정 알약으로, 사이 칸을
          //    회색 네모로 그려서 셋이 끊겨 보였고, 칸이 넓은 넓은 화면에서는 시작·끝이 가로로 긴 알약이 됐다.
          //    띠는 칸 폭을 채우고(시작은 가운데부터 · 끝은 가운데까지), 시작·끝은 고정 크기 동그라미를 얹는다.
          //    줄마다 알약으로 닫는다 — 줄 처음(일요일·앞이 빈칸)은 띠 왼쪽을, 줄 끝(토요일·뒤가 빈칸)은 오른쪽을 둥글게.
          const rowStart = index === 0 || !week[index - 1];
          const rowEnd = index === week.length - 1 || !week[index + 1];
          // 끝 날이 줄 맨 앞이면(또는 시작 날이 줄 맨 끝이면) 그 칸의 띠는 동그라미 밖으로 삐져나온 반달만 남는다 — 안 그린다.
          const range = Boolean(value.startDate && value.endDate && value.startDate !== value.endDate && key >= value.startDate && key <= value.endDate)
            && !(key === value.endDate && rowStart) && !(key === value.startDate && rowEnd);
          return (
            <Pressable
              key={key}
              disabled={past || beyond}
              onPress={() => onPick(key)}
              accessibilityRole="button"
              accessibilityState={{ selected: isStart || isEnd, disabled: past || beyond }}
              accessibilityLabel={formatDateShort(key, tx)}
              style={styles.cell}
            >
              {range ? (
                <View
                  testID={`range-band-${key}`}
                  pointerEvents="none"
                  style={[styles.band, isStart && styles.bandFromCenter, isEnd && styles.bandToCenter, !isStart && rowStart && styles.bandRoundLeft, !isEnd && rowEnd && styles.bandRoundRight]}
                />
              ) : null}
              {isStart || isEnd ? <View testID={`range-dot-${key}`} pointerEvents="none" style={styles.dot} /> : null}
              <Text
                variant="caption"
                weight={isStart || isEnd ? 'bold' : 'regular'}
                // 지난 날짜를 숨기지 않고 흐리게 둔다. 사라지면 달력의 칸이 밀려서
                // 사람이 날짜를 잘못 짚는다.
                color={past || beyond ? color.text.muted : isStart || isEnd ? color.text.onAction : color.text.heading}
                style={beyond ? styles.beyondDay : undefined}
              >
                {String(parseDateKey(key)?.getDate() ?? '')}
              </Text>
            </Pressable>
          );
        })}
        </View>
        ))}
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
  /**
   * 🔴 처음부터 열어 둘 칸 — S15P21E201-1350.
   *
   * <p>열 문항 화면에서 「날짜 정하기」를 눌러 온 사람은 «날짜를 고치러» 온 것이다.
   * 접힌 바를 보여 주고 한 번 더 누르게 하면, 그 사람이 보기에는 아무 데도 안 간 것이다.
   */
  initialSection?: Section;
  /** 처음에 채워 둘 값. 안 주면 빈 바로 시작한다(홈에 처음 들어온 사람). */
  initialValue?: StartBarValue;
  /**
   * 🔴 모바일 전체 화면 시트로 그린다 — S15P21E201-1369.
   *
   * <p>알약은 홈 화면 «안»에 있고 시트는 화면 «전체»를 덮어야 한다(탭바가 그 위로
   * 미끄러져 내려가야 하므로 탭바의 형제로 올라간다). 한 부품이 두 자리에 동시에
   * 있을 수 없어서, 시트일 때는 알약을 안 그리고 시트만 그린다.
   */
  sheet?: boolean;
  /** 값을 밖에서 들고 있을 때. 알약과 시트가 같은 값을 봐야 해서 홈이 들고 있는다. */
  value?: StartBarValue;
  onChange?: (value: StartBarValue) => void;
  /** 시트의 ✕. 값은 그대로 두고 시트만 닫는다. */
  onClose?: () => void;
  /**
   * 시트 단추 글자 — 여행 만들기 질문 화면이 출발지·숙소만 고치러 열 때 「이대로 적용」(UI 캔버스 ⑤).
   * 주면 「전체 삭제」도 숨긴다 — 답하던 날짜·인원까지 지우면 안 된다. 홈은 안 주므로 「일정 물어보기」 그대로다.
   */
  submitLabel?: string;
  /**
   * 폰 알약을 눌렀을 때. 주면 알약 아래로 패널을 펼치는 대신 «이것»을 부른다 —
   * 홈이 전체 화면 시트를 연다. 안 주면 지금까지대로 아래로 펼친다.
   */
  onOpenSheet?: () => void;
};

/** 시트 카드가 펼쳐질 때 제목이 커지고 오른쪽 값이 사라지는 시간. */
const CARD_MS = 350;
/** 날짜 칸을 연 뒤 그 칸의 자리가 바뀔 때마다 따라가는 시간 — 위 칸이 접히고 달력이 다 그려질 때까지. */
const DATES_FOLLOW_MS = 800;

/**
 * 시트의 카드 한 장.
 *
 * <p>접히면 「라벨 ··· 값」 한 줄이고, 펼치면 라벨이 커지면서 오른쪽 값이 사라지고
 * 아래로 그 칸의 패널이 열린다. 한 번에 한 장만 펼쳐진다.
 *
 * <p>🔴 시안은 라벨 22px 을 말하지만 이 저장소의 글자 단계에는 22 가 없다
 * (15 · 18 · 26). 새 단계를 만들지 않기로 한 규칙을 따라 26(display)을 쓴다 —
 * 18 은 접힌 것과 너무 비슷해서 «펼쳐졌다»로 안 읽힌다.
 */
function SheetCard({
  open, label, summary, onPress, children,
}: {
  open: boolean; label: string; summary: string; onPress: () => void; children: ReactNode;
}) {
  const openIn = useRef(new Animated.Value(open ? 1 : 0)).current;
  useEffect(() => {
    Animated.timing(openIn, { toValue: open ? 1 : 0, duration: CARD_MS, easing: EASE_SOFT, useNativeDriver: false }).start();
  }, [open, openIn]);
  return (
    <View style={[styles.card, open ? styles.cardOpen : styles.cardShut]}>
      <Pressable onPress={onPress} accessibilityRole="button" accessibilityState={{ expanded: open }} style={styles.cardHead}>
        <Text variant={open ? 'display' : 'body'} weight="bold" color={open ? color.text.heading : color.text.muted}>{label}</Text>
        {/* 펼친 카드에는 오른쪽 값을 안 그린다 — 아래 패널이 그 값을 고르는 자리라
            같은 것이 두 번 나온다. 접힐 때 흐려지며 돌아온다. */}
        {open ? null : (
          <Animated.View style={{ opacity: openIn.interpolate({ inputRange: [0, 1], outputRange: [1, 0] }) }}>
            <Text weight="bold" numberOfLines={1}>{summary}</Text>
          </Animated.View>
        )}
      </Pressable>
      {open ? <View style={styles.cardBody}>{children}</View> : null}
    </View>
  );
}

/** 폰에서 한 줄에 들어가는 개수. 390 폭에서 실측한 값이다. */
const PHONE_PRESET_COUNT = 3;

/**
 * 출발지·숙소 검색 — S15P21E201-1781(고지혁 QA). 카카오(searchOrigins)는 한국어 이름만 준다.
 * 영어 화면이면 꼭 갈 곳 검색과 같은 우리 장소 목록(searchPlacesByName)도 불러 같은 장소의 영어 이름을 붙인다.
 * 우리 목록이 실패해도 카카오 결과는 그대로 낸다 — 영어 이름은 덤이다.
 */
async function searchOriginsWithNames(query: string, accessToken: string | null, signal: AbortSignal, withEnglish: boolean): Promise<OriginSearchResult> {
  if (!withEnglish) return searchOrigins(query, accessToken, signal);
  const [origins, places] = await Promise.all([
    searchOrigins(query, accessToken, signal),
    searchPlacesByName(query, signal).catch(() => [] as PlaceSearchItem[]),
  ]);
  return origins.state === 'success' ? { ...origins, items: attachEnglishNames(origins.items, places) } : origins;
}

/**
 * 추천 숙소 지역의 둘째 줄 — S15P21E201-1795(고지혁 QA). 영어 화면에도 「바다 앞 호텔·리조트가 모여 있어요」가 나왔다.
 * 추천 지역의 그 줄은 주소가 아니라 화면 문구라 번역표를 거친다. 검색 결과의 둘째 줄은 주소라 그대로 둔다 —
 * 카카오는 한국어 주소만 주고, 택시에 보여 줄 값이다.
 * 🔴 한국어 원문은 origins.ts 의 RECOMMENDED_LODGING_AREAS 와 같은 문장이다. 한쪽을 바꾸면 둘 다 바꾼다.
 */
export function lodgingAreaNote(candidate: OriginCandidate, tx: (ko: string, en: string) => string): string {
  switch (candidate.externalId) {
    case 'lodging-haeundae': return tx('바다 앞 호텔·리조트가 모여 있어요', 'Beachfront hotels and resorts');
    case 'lodging-seomyeon': return tx('교통 중심 · 어디든 가기 편해요', 'Transit hub · easy to get anywhere');
    case 'lodging-gwangalli': return tx('야경과 카페 골목', 'Night views and café streets');
    case 'lodging-nampo': return tx('시장·원도심 도보 여행', 'Markets and old-town walks');
    default: return candidate.address;
  }
}

export function PlanStartBar({
  wide, accessToken, onSubmit, today = new Date(), initialSection = null, initialValue,
  sheet = false, value: controlledValue, onChange, onClose, onOpenSheet, submitLabel,
}: PlanStartBarProps) {
  const { tx, language } = useI18n();
  const insets = useSafeAreaInsets();
  // 🔴 「영어가 아니면 한국어」로 가르면 일본어·중국어 사용자가 한국어를 본다.
  // 그 언어들은 번역표에 없는 문구가 있으면 영어로 떨어지기로 정해져 있다
  // (resolveTextLanguage). 그 규칙을 그대로 쓴다 — S15P21E201-1296.
  const ko = resolveTextLanguage(language) === 'ko';
  // 값이 두 갈래인 이유 — 모바일은 «알약»이 홈 화면 안에 있고 «시트»는 화면 전체를
  // 덮는 형제로 올라가서, 한 부품이 둘을 같이 그릴 수 없다. 그래서 값을 홈이 들고
  // 둘에게 같은 것을 준다. 데스크톱은 혼자 다 그리므로 안에서 들고 있는다.
  const [selfValue, setSelfValue] = useState<StartBarValue>(initialValue ?? EMPTY_START_BAR);
  const value = controlledValue ?? selfValue;
  const valueRef = useRef(value);
  valueRef.current = value;
  const setValue = (next: StartBarValue | ((prev: StartBarValue) => StartBarValue)) => {
    const resolved = typeof next === 'function' ? (next as (prev: StartBarValue) => StartBarValue)(valueRef.current) : next;
    if (onChange) onChange(resolved);
    else setSelfValue(resolved);
  };
  // 시트는 «펼친 채로» 열린다. 빈 카드 세 장을 보여 주고 한 번 더 누르게 하면
  // 시트를 연 사람 눈에는 아무 일도 안 일어난 것이다 — 1350 이 고친 것과 같은 실수다.
  const [section, setSection] = useState<Section>(initialSection ?? (sheet ? 'origin' : null));
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<OriginCandidate[]>([]);
  const [searching, setSearching] = useState(false);
  const [lodgingQuery, setLodgingQuery] = useState('');
  const [lodgingResults, setLodgingResults] = useState<OriginCandidate[]>([]);
  const [lodgingSearching, setLodgingSearching] = useState(false);
  // 이미 고른 출발일이 있으면 그 달부터 연다 — S15P21E201-1539. 이번 달부터 열면 고른 날을 보려고 › 를 또 눌러야 했다.
  const [monthOffset, setMonthOffset] = useState(() => monthOffsetOf(value.startDate, today));
  const [monthPickerOpen, setMonthPickerOpen] = useState(false);
  // 🔴 여행 만들기 달력(DateRangeCard)과 같은 12개월 — 넓은 화면은 두 달을 나란히 그려서 한 칸 덜 넘긴다.
  //    예전에는 끝없이 넘어가 13개월 뒤를 고를 수 있었고, 여행 만들기 달력은 그 달을 못 보였다.
  const maxOffset = wide ? MAX_MONTH_OFFSET - 1 : MAX_MONTH_OFFSET;
  const todayKey = toDateKey(today);
  const summary = summarizeStartBar(value, tx, language);
  const ready = canAskForPlan(value);
  // 🔴 단추 이름은 늘 「일정 물어보기」다 — 이름을 이유로 바꿨더니 e2e(core-journey)와 낭독기가 단추를 못 찾았다
  //    (2026-09-21 승격 파이프라인 210801). 이유는 단추 안 둘째 줄로 적는다.
  const blocker = askForPlanBlocker(value, tx);
  const abortRef = useRef<AbortController | null>(null);
  const lodgingAbortRef = useRef<AbortController | null>(null);

  // 출발지 검색 — 서버가 두 글자 미만을 거절하므로 나가기 전에 막는다.
  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length < 2) { setResults([]); setSearching(false); return; }
    const controller = new AbortController();
    abortRef.current?.abort();
    abortRef.current = controller;
    setSearching(true);
    const timer = setTimeout(async () => {
      const outcome = await searchOriginsWithNames(trimmed, accessToken, controller.signal, !ko);
      if (controller.signal.aborted) return;
      setSearching(false);
      // 실패해도 추천 목록은 그대로 둔다. 검색이 안 된다고 고를 수 없게 되면
      // 「서버가 죽으면 여행을 못 만든다」가 된다.
      setResults(outcome.state === 'success' ? outcome.items : []);
    }, 250);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [accessToken, query, ko]);

  // 숙소 검색 — 출발지와 같은 searchOrigins 를 재사용한다(design_handoff_home_lodging).
  // 검색창·중단기를 따로 두는 이유는 두 칸이 동시에 타이핑될 수 있어서다(가짓 값이 아니다).
  useEffect(() => {
    const trimmed = lodgingQuery.trim();
    if (trimmed.length < 2) { setLodgingResults([]); setLodgingSearching(false); return; }
    const controller = new AbortController();
    lodgingAbortRef.current?.abort();
    lodgingAbortRef.current = controller;
    setLodgingSearching(true);
    const timer = setTimeout(async () => {
      const outcome = await searchOriginsWithNames(trimmed, accessToken, controller.signal, !ko);
      if (controller.signal.aborted) return;
      setLodgingSearching(false);
      setLodgingResults(outcome.state === 'success' ? outcome.items : []);
    }, 250);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [accessToken, lodgingQuery, ko]);

  const pickOrigin = (candidate: OriginCandidate) => {
    // 영어 이름은 화면용으로 따로 든다 — origin 은 서버로 가는 한국어 이름 그대로다(S15P21E201-1795).
    setValue((prev) => ({ ...prev, origin: candidate.name, originEnglish: placeEnglishOf(candidate), originLat: candidate.lat, originLng: candidate.lng }));
    setQuery('');
    // 출발지를 고르면 숙소로 넘어간다 — design_handoff_home_lodging.
    setSection('lodging');
  };

  const pickLodging = (candidate: OriginCandidate) => {
    // externalId·source 까지 든다 — 좌표만 두면 서버가 숙소를 못 남긴다(S15P21E201-1536).
    setValue((prev) => ({ ...prev, lodging: candidate.name, lodgingEnglish: placeEnglishOf(candidate), lodgingLat: candidate.lat, lodgingLng: candidate.lng, lodgingPlace: lodgingSnapshotOf(candidate) }));
    setLodgingQuery('');
    setSection('dates');
  };

  /** 「숙소 아직 안 정했어요」— 탈출구. 숙소는 선택 사항이라 미정으로 두고 다음 칸으로. */
  const clearLodging = () => {
    setValue((prev) => ({ ...prev, lodging: '', lodgingEnglish: null, lodgingLat: null, lodgingLng: null, lodgingPlace: null }));
    setLodgingQuery('');
    setSection('dates');
  };

  // 출발일만 찍은 동안 8일째 뒤는 못 누른다 — 여행 만들기 달력(DateRangeCard)과 같은 최대 7박.
  const lastPickable = value.startDate && !value.endDate ? addDays(value.startDate, MAX_TRIP_NIGHTS) : undefined;
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
    if (which === 'origin') return startBarPlaceName(value.origin, value.originEnglish, language) || tx('어디서 출발해요?', 'Where from?');
    if (which === 'lodging') return startBarPlaceName(value.lodging, value.lodgingEnglish, language) || tx('어디에 머물러요?', 'Where are you staying?');
    if (which === 'dates') {
      if (!value.startDate) return tx('날짜 추가', 'Add dates');
      const days = dayCount(value.startDate, value.endDate || value.startDate);
      return `${formatDateShort(value.startDate, tx)}${value.endDate && value.endDate !== value.startDate ? ` – ${formatDateShort(value.endDate, tx)}` : ''} · ${tx(`${days}일`, `${days}d`)}`;
    }
    const total = value.adults + value.children;
    return total > 0 ? (value.children ? tx(`성인 ${value.adults} · 어린이 ${value.children}`, `${value.adults} adults · ${value.children} children`) : tx(`성인 ${value.adults}`, `${value.adults} adults`)) : tx('인원 추가', 'Add travelers');
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
  /** 칸이 바뀔 때마다 0 에서 다시 시작하는 값. 패널 «내용»만 다시 들어오게 한다. */
  const swapIn = useRef(new Animated.Value(1)).current;
  /** 시트가 아래에서 올라오는 값. */
  const sheetIn = useRef(new Animated.Value(0)).current;

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
      duration: reduceMotion ? 0 : section ? 600 : 160,
      easing: section ? EASE_SOFT : Easing.in(Easing.cubic),
      useNativeDriver: false,
    }).start();
    // 칸을 바꾸면 내용이 «매번 다시» 들어온다. 즉시 교체하면 출발지에서 날짜로 넘어간 것이
    // 바뀐 줄도 모르게 지나간다 — 같은 자리에 같은 크기의 흰 상자가 있기 때문이다.
    if (section) {
      swapIn.setValue(0);
      Animated.timing(swapIn, {
        toValue: 1,
        duration: reduceMotion ? 0 : 450,
        easing: EASE_SOFT,
        useNativeDriver: false,
      }).start();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [section, reduceMotion]);

  useEffect(() => {
    if (!sheet) return;
    Animated.timing(sheetIn, { toValue: 1, duration: reduceMotion ? 0 : 600, easing: EASE_SOFT, useNativeDriver: false }).start();
  }, [sheet, reduceMotion, sheetIn]);

  // 🔴 날짜 칸으로 넘어가면 그 칸을 화면 위로 올린다 (S15P21E201-1626). 숙소를 고르면 날짜가 펼쳐지는데
  //    스크롤이 제자리라, 작은 폰(360×640)에서는 달력 마지막 줄과 「1박 2일」 칩이 아래 버튼 줄 밑에 깔렸다.
  //    위 칸이 접히면서 자리가 바뀌므로 시각(타이머)에 기대지 않는다 — 칸을 연 뒤 잠깐 동안은 날짜 칸의 자리를
  //    «잴 때마다» 그 자리로 간다(느린 기기에서 옛 자리로 가지 않게). 이미 잰 자리가 있으면 바로 한 번 간다.
  const sheetScrollRef = useRef<ScrollView>(null);
  const datesCardY = useRef<number | null>(null);
  const followDatesUntil = useRef(0);
  const scrollToDates = () => {
    if (datesCardY.current === null || Date.now() > followDatesUntil.current) return;
    sheetScrollRef.current?.scrollTo({ y: Math.max(0, datesCardY.current - spacing[2]), animated: !reduceMotion });
  };
  useEffect(() => {
    if (!sheet || section !== 'dates') { followDatesUntil.current = 0; return; }
    followDatesUntil.current = Date.now() + DATES_FOLLOW_MS;
    scrollToDates();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sheet, section]);

  // 🔴 이 시트는 RN `<Modal>` 이 아니라 그냥 View 라서 `onRequestClose` 가 없다 — 즉
  // 안드로이드 하드웨어 뒤로가기를 이 시트가 알아서 삼켜 주지 않는다. 처리를 안 하면
  // 뒤로가기가 시트를 그대로 통과해 밑에 있는 화면(또는 앱 자체)이 뒤로 간다 —
  // app/(tabs)/me.tsx 의 패널이 겪었던 것과 같은 종류의 결함이다. 웹에는 하드웨어
  // 뒤로가기가 없으니 ESC 가 그 자리를 대신한다(같은 파일의 선례를 그대로 따른다).
  useEffect(() => {
    if (!sheet || !onClose) return;
    if (Platform.OS === 'web') {
      const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose(); };
      window.addEventListener('keydown', onKey);
      return () => window.removeEventListener('keydown', onKey);
    }
    const sub = BackHandler.addEventListener('hardwareBackPress', () => { onClose(); return true; });
    return () => sub.remove();
  }, [sheet, onClose]);

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
      {searching ? <ActivityIndicator color={color.action.primary} /> : null}
      {/* 결과가 있으면 추천이 아니라 검색 결과다(S15P21E201-1775) — 아래 목록도 그렇게 갈린다. */}
      <Text testID="origin-list-label" variant="caption" color={color.text.muted}>{results.length ? tx('검색 결과', 'Search results') : tx('추천 출발지', 'Suggested starting points')}</Text>
      {(results.length ? results : MAJOR_BUSAN_ORIGINS).map((candidate) => (
        <Pressable key={candidate.externalId} onPress={() => pickOrigin(candidate)} accessibilityRole="button" style={styles.originRow}>
          <Text weight="bold">{stopNameForLanguage(candidate.name, candidate.nameEn, language)}</Text>
          <Text variant="caption" color={color.text.muted}>{addressForLanguage(candidate, language)}</Text>
        </Pressable>
      ))}
    </View>
  );

  const lodgingPanel = (
    <View style={styles.panel}>
      <TextInput
        value={lodgingQuery}
        onChangeText={setLodgingQuery}
        placeholder={tx('숙소 이름이나 동네를 검색해 보세요', 'Search a place or neighborhood')}
        placeholderTextColor={color.text.muted}
        style={styles.search}
        accessibilityLabel={tx('숙소 검색', 'Search lodging')}
      />
      {lodgingSearching ? <ActivityIndicator color={color.action.primary} /> : null}
      <Text variant="caption" color={color.text.muted}>{tx('추천 숙소 지역', 'Suggested areas to stay')}</Text>
      {(lodgingResults.length ? lodgingResults : RECOMMENDED_LODGING_AREAS).map((candidate) => (
        <Pressable key={candidate.externalId} onPress={() => pickLodging(candidate)} accessibilityRole="button" style={styles.originRow}>
          <Text weight="bold">{stopNameForLanguage(candidate.name, candidate.nameEn, language)}</Text>
          <Text variant="caption" color={color.text.muted}>{lodgingAreaNote(candidate, tx)}</Text>
        </Pressable>
      ))}
      {/* 탈출구 — 항상 마지막. 검색 결과 중이어도 그대로 둔다, 언제든 «안 정했다」로 빠져나갈 수 있게.
          🔴 1박 이상이면 안 보인다(S15P21E201-1591) — 1박 이상은 숙소가 있어야 일정을 만든다(S15P21E201-1584).
          골라 봐야 「만들기」에서 막히는 길을 열어 두지 않는다. 날짜를 아직 안 골랐으면 모르니 그대로 둔다. */}
      {isOvernight(value.startDate, value.endDate) ? null : (
        <Pressable onPress={clearLodging} accessibilityRole="button" style={styles.originRow}>
          <Text weight="bold">{tx('숙소 아직 안 정했어요', 'Not decided yet')}</Text>
          <Text variant="caption" color={color.text.muted}>{tx('출발지 기준으로 일정을 짜요', "We'll plan around your starting point")}</Text>
        </Pressable>
      )}
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
          <Text weight="bold" color={monthOffset === 0 ? color.text.muted : color.text.heading}>‹</Text>
        </Pressable>
        <Pressable
          onPress={() => setMonthOffset((n) => Math.min(maxOffset, n + 1))}
          disabled={monthOffset >= maxOffset}
          accessibilityRole="button"
          accessibilityLabel={tx('다음 달', 'Next month')}
          style={styles.navButton}
        >
          <Text weight="bold" color={monthOffset >= maxOffset ? color.text.muted : color.text.heading}>›</Text>
        </Pressable>
      </View>
      {monthPickerOpen
        ? <MonthPicker today={today} selected={monthOffset} onPick={(offset) => { setMonthOffset(Math.min(maxOffset, offset)); setMonthPickerOpen(false); }} tx={tx} />
        : <View style={wide ? styles.monthRow : undefined}>
            <MonthGrid {...monthBase} value={value} today={todayKey} onPick={pickDate} tx={tx} onPressTitle={() => setMonthPickerOpen(true)} maxDate={lastPickable} />
            {wide ? <MonthGrid {...secondMonth} value={value} today={todayKey} onPick={pickDate} tx={tx} maxDate={lastPickable} /> : null}
          </View>}
      {/* 🔴 기간 넷은 같은 폭으로 한 줄 — 좁은 카드(넓은 화면의 가운데 칸)에서 3+1 로 꺾여 「3박 4일」 하나만 다음 줄에 남았다. */}
      <View style={styles.equalChipRow}>
        {[0, 1, 2, 3].map((nights) => (
          <Pressable
            key={nights}
            accessibilityRole="button"
            onPress={() => setValue((prev) => {
              const start = prev.startDate && prev.startDate >= todayKey ? prev.startDate : todayKey;
              return { ...prev, startDate: start, endDate: addDays(start, nights) };
            })}
            style={[styles.chip, styles.equalChip]}
          >
            <Text variant="caption" weight="bold" numberOfLines={1}>
              {nights === 0 ? tx('당일치기', 'Day trip') : tx(`${nights}박 ${nights + 1}일`, `${nights} ${nights === 1 ? 'night' : 'nights'}`)}
            </Text>
          </Pressable>
        ))}
      </View>
    </View>
  );

  const peoplePanel = (
    <View style={styles.panel}>
      {([
        { key: 'adults' as const, ko: '성인', en: 'Adults', hint: tx('만 13세 이상', 'Age 13+') },
        { key: 'children' as const, ko: '어린이', en: 'Children', hint: tx('만 2~12세', 'Age 2–12') },
      ]).map((row) => (
        <View key={row.key} style={styles.counterRow}>
          <View>
            <Text weight="bold">{tx(row.ko, row.en)}</Text>
            <Text variant="caption" color={color.text.muted}>{row.hint}</Text>
          </View>
          <View style={styles.counter}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={txf(tx, '%s 줄이기', 'Fewer %s', tx(row.ko, row.en))}
              onPress={() => setValue((prev) => ({ ...prev, [row.key]: Math.max(row.key === 'adults' ? 1 : 0, prev[row.key] - 1) }))}
              style={styles.counterButton}
            >
              <Text weight="bold">−</Text>
            </Pressable>
            <Text weight="bold" style={styles.counterValue}>{value[row.key]}</Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={txf(tx, '%s 늘리기', 'More %s', tx(row.ko, row.en))}
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

  const panel = section === 'origin' ? originPanel : section === 'lodging' ? lodgingPanel : section === 'dates' ? datePanel : section === 'people' ? peoplePanel : null;

  // 홈이 시트를 맡으면(onOpenSheet) 알약 아래 패널은 안 그린다 — 같은 것을 두 자리에
  // 그리게 된다. 시트를 안 쓰는 자리는 지금까지대로 아래로 펼친다.
  const inlinePanel = !wide && onOpenSheet ? null : panel;

  // ── 모바일 전체 화면 시트 ─────────────────────────────────────────────────
  // 홈이 탭바의 «형제»로 그린다. 그래서 여기서 화면 전체를 덮을 수 있다 —
  // 시작 바 자리에서 그리면 바의 크기 안에 갇힌다.
  if (sheet) {
    const cards = [
      { key: 'origin' as const, label: tx('출발지', 'From'), body: originPanel },
      { key: 'lodging' as const, label: tx('숙소', 'Lodging'), body: lodgingPanel },
      { key: 'dates' as const, label: tx('날짜', 'Dates'), body: datePanel },
      { key: 'people' as const, label: tx('인원', 'Travelers'), body: peoplePanel },
    ];
    return (
      <Animated.View
        style={[
          styles.sheet,
          { opacity: sheetIn, transform: [{ translateY: sheetIn.interpolate({ inputRange: [0, 1], outputRange: [40, 0] }) }] },
        ]}
      >
        {/* 🔴 위 안전영역만큼 내린다(S15P21E201-1772). 시트가 top:0 에 붙어서 ✕ 가 시계·배터리 줄 높이에 그려졌고,
            안드로이드는 그 자리 누름을 시스템이 먹어 세 번 눌러도 안 닫혔다(Play 37 실기기). 아래는 이미 insets.bottom 을 더한다. */}
        <View testID="plan-start-sheet-head" style={[styles.sheetHead, { paddingTop: spacing[4] + insets.top }]}>
          {/* ✕ 는 시트만 닫는다. 고른 값은 그대로 남아 알약에 요약으로 보인다. */}
          <Pressable onPress={onClose} accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} style={styles.sheetClose}>
            <Text weight="bold">✕</Text>
          </Pressable>
        </View>

        <ScrollView ref={sheetScrollRef} style={styles.sheetBody} contentContainerStyle={styles.sheetBodyContent} keyboardShouldPersistTaps="handled">
          {cards.map((card) => (
            <View key={card.key} onLayout={card.key === 'dates' ? (event) => { datesCardY.current = event.nativeEvent.layout.y; scrollToDates(); } : undefined}>
              <SheetCard
                open={section === card.key}
                label={card.label}
                summary={segmentLabel(card.key)}
                onPress={() => setSection(card.key)}
              >
                <Animated.View style={{ opacity: swapIn, transform: [{ translateY: swapIn.interpolate({ inputRange: [0, 1], outputRange: [10, 0] }) }] }}>
                  {card.body}
                </Animated.View>
              </SheetCard>
            </View>
          ))}
        </ScrollView>

        {/* 🔴 시트는 화면 바닥까지 덮는다(absolute bottom:0) — 안드로이드 탐색줄만큼 띄우지 않으면 「일정 물어보기」가 반쯤 가린다(실기 빌드 28, S15P21E201-1438). */}
        <View style={[styles.sheetFoot, { paddingBottom: spacing[6] + insets.bottom }]}>
          <Pressable
            onPress={() => { setValue(EMPTY_START_BAR); setSection('origin'); }}
            disabled={Boolean(submitLabel)}
            accessibilityRole="button"
            style={styles.sheetClear}
          >
            {submitLabel ? null : <Text weight="bold">{tx('전체 삭제', 'Clear all')}</Text>}
          </Pressable>
          <Pressable
            onPress={() => { if (ready) { onClose?.(); onSubmit(value); } }}
            disabled={!ready}
            accessibilityRole="button"
            style={[styles.cta, styles.sheetCta, !ready && styles.ctaOff]}
          >
            {/* 꺼져 있을 때 흰 글자를 두면 연회색 바탕에서 안 읽힌다. */}
            <Text weight="bold" color={ready ? color.text.onAction : color.text.muted}>{submitLabel ?? tx('일정 물어보기', 'Ask for a plan')}</Text>
            {blocker ? <Text variant="micro" color={color.text.muted}>{blocker}</Text> : null}
          </Pressable>
        </View>
      </Animated.View>
    );
  }

  return (
    <View style={styles.root}>
      {wide ? (
        <View style={styles.bar}>
          {/* 고른 칸을 따라다니는 강조 알약. 칸 뒤에 깔리므로 글자를 안 가린다. */}
          <Animated.View
            pointerEvents="none"
            style={[styles.highlight, { left: highlightX, width: highlightW, opacity: highlightO }]}
          />
          {(['origin', 'lodging', 'dates', 'people'] as const).map((which, index) => (
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
              style={[styles.segment, index > 0 && styles.segmentDivider, section === which && styles.segmentOpen]}
            >
              <Text variant="caption" color={color.text.muted}>
                {which === 'origin' ? tx('출발지', 'From') : which === 'lodging' ? tx('숙소', 'Lodging') : which === 'dates' ? tx('날짜', 'Dates') : tx('인원', 'Travelers')}
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
            <Text weight="bold" color={ready ? color.text.onAction : color.text.muted}>{tx('일정 물어보기', 'Ask for a plan')}</Text>
            {blocker ? <Text variant="micro" color={color.text.muted}>{blocker}</Text> : null}
          </Pressable>
        </View>
      ) : (
        <Pressable
          onPress={() => (onOpenSheet ? onOpenSheet() : setSection((prev) => (prev ? null : 'origin')))}
          accessibilityRole="button"
          accessibilityState={{ expanded: onOpenSheet ? undefined : section !== null }}
          style={styles.phonePill}
        >
          <Text weight="bold" numberOfLines={1} color={summary ? color.text.heading : color.text.muted}>
            {summary || tx('여행 계획 시작해 보세요', 'Start planning your trip')}
          </Text>
        </Pressable>
      )}

      {inlinePanel ? (
        <Animated.View
          style={[
            styles.panelShell,
            wide && styles.panelShellWide,
            {
              opacity: panelIn,
              transform: [{ translateY: panelIn.interpolate({ inputRange: [0, 1], outputRange: [-16, 0] }) }],
            },
          ]}
        >
          {!wide ? (
            <View style={styles.phoneTabs}>
              {(['origin', 'lodging', 'dates', 'people'] as const).map((which) => (
                <Pressable key={which} onPress={() => setSection(which)} accessibilityRole="button" style={[styles.phoneTab, section === which && styles.phoneTabOn]}>
                  <Text variant="caption" weight="bold" color={section === which ? color.text.onAction : color.text.heading}>
                    {which === 'origin' ? tx('출발지', 'From') : which === 'lodging' ? tx('숙소', 'Lodging') : which === 'dates' ? tx('날짜', 'Dates') : tx('인원', 'Travelers')}
                  </Text>
                </Pressable>
              ))}
              <Pressable onPress={() => setSection(null)} accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} style={styles.phoneTab}>
                <Text weight="bold">✕</Text>
              </Pressable>
            </View>
          ) : null}
          <ScrollView style={styles.panelScroll} keyboardShouldPersistTaps="handled">
            <Animated.View
              key={section ?? 'none'}
              style={{ opacity: swapIn, transform: [{ translateY: swapIn.interpolate({ inputRange: [0, 1], outputRange: [10, 0] }) }] }}
            >
              {panel}
            </Animated.View>
          </ScrollView>
          {!wide ? (
            <Pressable onPress={submit} disabled={!ready} accessibilityRole="button" style={[styles.cta, styles.ctaWide, !ready && styles.ctaOff]}>
              <Text weight="bold" color={ready ? color.text.onAction : color.text.muted}>{tx('일정 물어보기', 'Ask for a plan')}</Text>
            {blocker ? <Text variant="micro" color={color.text.muted}>{blocker}</Text> : null}
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
            <Text variant="caption" weight="bold">{tx(preset.ko, preset.en)}</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  beyondDay: { textDecorationLine: 'line-through', opacity: 0.5 },
  root: { width: '100%', gap: spacing[3], zIndex: 10 },
  // 🔴 새 배색은 「카드에 선을 두지 않는다」지만 시작 바는 예외로 붉은 선 하나를 둔다.
  // 이것이 화면의 «유일한 입력 진입점»이라, 선이 없으면 다른 카드들 사이에 묻힌다.
  // 검색 입력(styles.search)에는 넣지 않는다 — 그건 이 바 «안»의 부품이다.
  bar: {
    // maxWidth 920 — 칸이 넷(출발지·숙소·날짜·인원)이 되어 860 에서 넓혔다(design_handoff_home_lodging).
    flexDirection: 'row', alignItems: 'center', alignSelf: 'center', width: '100%', maxWidth: 920, minHeight: 72,
    padding: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.action.outline,
  },
  // 🔴 칸 넷을 같은 폭(flex:1)으로 나누지 않는다 (S15P21E201-1626). 숙소 이름·긴 날짜 구간이 「파라다이스호텔부산 오…」
  //    「10.5(월) – 10.25(일) ·…」로 잘리고, 짧은 「성인 2」 칸은 비어 남았다. 값 길이만큼 자리를 갖고, 모자라면
  //    다 같이 줄되 지금 고르는 칸은 줄지 않는다(segmentOpen). 너무 좁아지지 않게 아랫단을 둔다.
  segment: { flexGrow: 1, flexShrink: 1, flexBasis: 'auto', minWidth: 112, paddingHorizontal: spacing[4], paddingVertical: spacing[2], borderRadius: radius.full, gap: 2 },
  segmentOpen: { flexShrink: 0, maxWidth: '46%' },
  segmentDivider: { borderLeftWidth: 1, borderLeftColor: color.surface.border },
  // 고른 칸을 따라다니는 강조 알약. 칸마다 배경을 켜고 끄면 뚝뚝 끊겨 보인다
  // 하나를 깔고 자리만 옮기면 미끄러진다(에어비앤비가 그렇게 한다).
  // 글자를 가리지 않도록 칸 뒤에 깔고 pointerEvents 를 끈다.
  highlight: { position: 'absolute', top: spacing[2], bottom: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft },
  cta: { minHeight: 56, paddingHorizontal: spacing[6], borderRadius: radius.full, backgroundColor: color.action.primary, alignItems: 'center', justifyContent: 'center' },
  ctaWide: { alignSelf: 'stretch', marginTop: spacing[3] },
  ctaOff: { backgroundColor: color.surface.field },
  phonePill: {
    minHeight: 56, paddingHorizontal: spacing[4], justifyContent: 'center',
    borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.action.outline,
  },
  panelShell: { marginTop: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  panelShellWide: { alignSelf: 'center', width: '100%', maxWidth: 920 },
  // 🔴 높이를 묶지 않는다. 420 을 걸어 두면 두 달 달력이 넘쳐 스크롤이 생기고,
  // 그 스크롤이 칸 전환의 위아래 움직임까지 삼켜서 애니메이션이 안 보였다.
  // 내용 길이대로 늘어난다 — 모바일은 시트 자체가 스크롤한다.
  panelScroll: {},
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
  // 제목을 누르는 자리 — 글자만으로는 누르기 작고 누를 수 있는 줄도 모른다. 알약으로 둔다.
  monthTitleButton: { minHeight: 32, alignSelf: 'center', justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.soft },
  weekHead: { flexDirection: 'row' },
  grid: {},
  // 🔴 한 줄에 일곱 칸을 직접 넣는다. flexWrap 으로 접으면 폭 반올림 때문에 일곱째 칸이
  //    다음 줄로 밀려 날짜가 요일과 어긋난다(S15P21E201-1434).
  week: { flexDirection: 'row' },
  cell: { flex: 1, height: 40, alignItems: 'center', justifyContent: 'center' },
  headCell: { flex: 1, textAlign: 'center' },
  // 고른 기간의 띠 — 칸 폭을 채운다. 시작 칸은 가운데부터, 끝 칸은 가운데까지(S15P21E201-1720).
  // 띠와 동그라미는 같은 높이(34)다. 칸(40) 안에서 위아래 3 씩 비운다.
  band: { position: 'absolute', top: 3, bottom: 3, left: 0, right: 0, backgroundColor: color.surface.tint },
  bandFromCenter: { left: '50%' },
  bandToCenter: { right: '50%' },
  bandRoundLeft: { borderTopLeftRadius: radius.full, borderBottomLeftRadius: radius.full },
  bandRoundRight: { borderTopRightRadius: radius.full, borderBottomRightRadius: radius.full },
  // 시작·끝 동그라미 — 크기를 고정한다. 칸 전체를 칠하면 넓은 화면에서 가로로 긴 알약이 됐다.
  //    34 인 까닭: 칸 폭이 작은 폰(360)에서 29.4 · 390 에서 33.7 이라, 40 이면 옆 날짜 글자와 3 남짓밖에 안 떨어졌다.
  dot: { position: 'absolute', top: 3, left: '50%', marginLeft: -17, width: 34, height: 34, borderRadius: radius.full, backgroundColor: color.brand.navy },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: spacing[2] },
  equalChipRow: { flexDirection: 'row', gap: spacing[2] },
  equalChip: { flex: 1, minWidth: 0, alignItems: 'center', paddingHorizontal: spacing[1] },
  chip: { minHeight: 32, paddingHorizontal: spacing[3], justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.soft },
  counterRow: { minHeight: 56, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  counter: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  counterButton: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field },
  counterValue: { minWidth: 24, textAlign: 'center' },

  // ── 모바일 전체 화면 시트 ─────────────────────────────────────────────────
  // 🔴 zIndex 25 는 탭바 받침(30)보다 «낮다». 그래야 탭바가 이 위로 미끄러져 내려가는
  // 것이 보인다 — 다 내려가면 투명도 0 이라 안 보인다. 높이면 탭바가 시트 뒤에 숨어
  // 그냥 사라진 것처럼 된다.
  //
  // 🔴 여백 20 은 이 저장소의 격자(4·8·12·16·24·32)에 없다. 시안의 20 대신 24 를 쓴다 —
  // 격자를 벗어난 값을 하나 들이면 그 다음부터 아무도 격자를 안 지킨다.
  sheet: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: color.canvas, zIndex: 25 },
  sheetHead: { alignItems: 'flex-end', paddingTop: spacing[4], paddingHorizontal: spacing[6], paddingBottom: spacing[2] },
  sheetClose: {
    width: 36, height: 36, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full,
    backgroundColor: color.surface.card,
    shadowColor: color.brand.navy, shadowOpacity: 0.12, shadowRadius: 8, shadowOffset: { width: 0, height: 2 }, elevation: 3,
  },
  sheetBody: { flex: 1 },
  sheetBodyContent: { paddingHorizontal: spacing[3], paddingBottom: spacing[3], gap: spacing[3] },
  sheetFoot: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingTop: spacing[3], paddingHorizontal: spacing[6], paddingBottom: spacing[6],
    borderTopWidth: 1, borderTopColor: color.surface.border,
  },
  sheetClear: { minHeight: 44, justifyContent: 'center' },
  // 🔴 채움색을 여기서 다시 안 적는다. styles.cta 위에 얹어 «모양만» 바꾼다 —
  // 같은 파일에 동백 채움을 두 번 적으면 배색 검사가 「화면당 하나」 위반으로 잡는다.
  // 실제로는 서로 배타적(시트가 뜨면 바는 안 그려진다)이지만, 예외 목록에 적어
  // 빠져나가면 그 파일이 앞으로 이 검사에서 영영 빠진다.
  sheetCta: { minHeight: 52, borderRadius: radius.md },

  // 카드 — 선은 없고 그림자로만 뜬다. 펼친 것이 크게 떠오른다.
  card: { borderRadius: radius.lg, backgroundColor: color.surface.card },
  cardShut: { shadowColor: color.brand.navy, shadowOpacity: 0.06, shadowRadius: 3, shadowOffset: { width: 0, height: 1 }, elevation: 1 },
  cardOpen: { shadowColor: color.brand.navy, shadowOpacity: 0.14, shadowRadius: 28, shadowOffset: { width: 0, height: 8 }, elevation: 6 },
  cardHead: { minHeight: 56, paddingHorizontal: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3] },
  cardBody: { paddingHorizontal: spacing[6], paddingBottom: spacing[6] },
});
