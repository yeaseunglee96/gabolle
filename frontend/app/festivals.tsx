import { useCallback, useEffect, useMemo, useState } from 'react';
import { localDateKey } from '@/plan/tripProgress';
import { Image, Pressable, StyleSheet, View } from 'react-native';
import Svg, { Path, Rect } from 'react-native-svg';
import { useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { AddPlaceToItineraryModal } from '@/components/AddPlaceToItineraryModal';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { PhotoCredit } from '@/components/PhotoCredit';
import { Text } from '@/components/Text';
import { useAuth } from '@/auth/AuthProvider';
import { Eyebrow } from '@/components/Eyebrow';
import { MonthGrid } from '@/home/PlanStartBar';
import { MAX_MONTH_OFFSET } from '@/home/monthJump';
import { EMPTY_START_BAR, addDays } from '@/home/startBarValue';
import { color, radius, spacing } from '@/design/tokens';
import { festivalDisplayTitle, getFestivals, type Festival } from '@/discovery/festivals';
import { formatFeatureSlot, photoLabels } from '@/discovery/places';
import { useI18n } from '@/i18n';
import { romanizeKorean } from '@/discovery/romanize';
import { formatDayHeading } from '@/i18n/datetime';
import { localizeMessage } from '@/i18n/messages';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { localNameFor } from '@/discovery/localNames';

type SortMode = 'soon' | 'name';
/** 기간을 어떻게 골랐나 — 한 달(오늘부터 30일) · 이번 주말 · 달력에서 직접. */
type RangeMode = 'month' | 'weekend' | 'custom';

function dateInputValue(offsetDays = 0) {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  return localDateKey(date);
}

/**
 * 이번 주말 — 토·일. 오늘이 토요일이면 오늘부터, 일요일이면 오늘 하루.
 * 🔴 주중에는 «다가오는» 토·일이다. 지난 주말을 보여주면 이미 끝난 축제가 섞인다.
 */
export function weekendRange(today: Date): { from: string; to: string } {
  const key = localDateKey(today);
  const day = today.getDay(); // 0 일 … 6 토
  if (day === 0) return { from: key, to: key };
  const saturday = addDays(key, 6 - day);
  return { from: saturday, to: addDays(saturday, 1) };
}

/** 「9월 26일 (토) – 9월 27일 (일)」 — 내 여행 목록과 같은 모양(S15P21E201-1679). 받은 글자(2026-09-26)는 사람이 읽는 말이 아니다. */
function festivalDates(festival: Festival, locale: string): string {
  const start = formatDayHeading(festival.startDate, locale) ?? festival.startDate;
  if (!festival.endDate || festival.endDate === festival.startDate) return start;
  return `${start} – ${formatDayHeading(festival.endDate, locale) ?? festival.endDate}`;
}

export default function Festivals() {
  const router = useRouter();
  const { tx, locale, language } = useI18n();
  const { width } = useLayout();
  const { accessToken } = useAuth();
  const [from, setFrom] = useState(() => dateInputValue());
  const [to, setTo] = useState(() => dateInputValue(30));
  // 🔴 전에는 「YYYY-MM-DD」 칸 두 개에 숫자를 쳐서 넣고 「이 기간으로 조회」를 눌러야 했다(UI 캔버스 ⑪).
  //    폰에서 날짜를 타자로 치는 사람은 없다 — 자주 쓰는 두 기간은 한 번 누르면 바로 찾고, 나머지는 달력에서 고른다.
  const [mode, setMode] = useState<RangeMode>('month');
  // 달력에서 출발일만 찍은 상태 — 두 번째로 찍어야 기간이 된다(여행 만들기 달력과 같은 규칙).
  const [pendingStart, setPendingStart] = useState<string | null>(null);
  const [monthOffset, setMonthOffset] = useState(0);
  // 기간을 다 고르면 달력을 접는다 — 펼친 채면 결과가 한 화면 아래로 밀린다. 「날짜 고르기」를 다시 누르면 펼친다.
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [sort, setSort] = useState<SortMode>('soon');
  const [festivals, setFestivals] = useState<Festival[]>([]);
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [errorMessage, setErrorMessage] = useState('');
  // — 이 축제를 내 일정에 더한다. 로그인 안 했으면 모달을 열지 않고
  // 바로 로그인으로 보낸다 — 모달 안에서 물어도 결국 로그인해야 하는 것은 같다.
  const [addPlaceId, setAddPlaceId] = useState<string | null>(null);
  const load = useCallback(async (start = from, end = to) => {
    const controller = new AbortController();
    setState('loading');
    setErrorMessage('');
    try {
      setFestivals(await getFestivals(start, end, controller.signal));
      setState('ready');
    } catch (cause) {
      setFestivals([]);
      setErrorMessage(cause instanceof ApiClientError ? cause.message : tx('축제 정보를 불러오지 못했어요.', 'Could not load festival information.'));
      setState('error');
    }
    return () => controller.abort();
  }, [from, to]);

  const applyRange = (start: string, end: string) => { setFrom(start); setTo(end); void load(start, end); };
  const chooseMode = (next: RangeMode) => {
    setMode(next);
    setPendingStart(null);
    setCalendarOpen(next === 'custom');
    if (next === 'month') applyRange(dateInputValue(), dateInputValue(30));
    else if (next === 'weekend') { const w = weekendRange(new Date()); applyRange(w.from, w.to); }
  };
  const pickDay = (key: string) => {
    if (!pendingStart || key < pendingStart) { setPendingStart(key); return; }
    setPendingStart(null);
    setCalendarOpen(false);
    applyRange(pendingStart, key);
  };
  const todayKey = dateInputValue();
  const calendarMonth = useMemo(() => {
    const base = new Date();
    base.setDate(1);
    base.setMonth(base.getMonth() + monthOffset);
    return { year: base.getFullYear(), month: base.getMonth() };
  }, [monthOffset]);
  const rangeText = pendingStart
    ? `${formatDayHeading(pendingStart, locale) ?? pendingStart} – ${tx('끝나는 날을 골라 주세요', 'Pick the last day')}`
    : from === to ? formatDayHeading(from, locale) ?? from : `${formatDayHeading(from, locale) ?? from} – ${formatDayHeading(to, locale) ?? to}`;
  const modes: { key: RangeMode; label: string }[] = [
    { key: 'month', label: tx('한 달', 'Next 30 days') },
    { key: 'weekend', label: tx('이번 주말', 'This weekend') },
    { key: 'custom', label: tx('날짜 고르기', 'Pick dates') },
  ];

  useEffect(() => { void load(); }, []); // 첫 화면의 기본 기간만 자동 조회한다.

  const sorted = useMemo(() => [...festivals].sort((a, b) => sort === 'soon'
    ? a.startDate.localeCompare(b.startDate)
    : festivalDisplayTitle(a).localeCompare(festivalDisplayTitle(b), 'ko')), [festivals, sort]);

  return <Screen scroll wide style={styles.screen}>
    <View style={styles.topBar}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}><Text variant="title" weight="bold">‹</Text></Pressable>
      <BrandLogoLink href="/home" imageStyle={styles.logo} />
      <View style={styles.spacer} />
    </View>
    <View style={styles.heading}><Eyebrow>{tx('부산 축제', 'Busan festival')}</Eyebrow><Text variant="display" weight="bold">{tx('여행 날짜에 열리는 축제', 'Festivals during your trip dates')}</Text><Text color={color.text.body}>{tx('선택한 기간에 실제로 열리는 축제만 보여드려요.', 'We only show festivals actually running in the period you pick.')}</Text></View>

    <View style={[styles.filterCard, isAtLeast(width, 'md') && styles.filterCardWide]}>
      <View style={styles.chipRow} accessibilityRole="radiogroup">
        {modes.map((item) => (
          <Pressable key={item.key} accessibilityRole="radio" accessibilityState={{ selected: mode === item.key, expanded: item.key === 'custom' ? calendarOpen : undefined }} onPress={() => chooseMode(item.key)} style={({ pressed }) => [styles.sortButton, mode === item.key && styles.sortSelected, pressed && styles.pressed]}>
            <Text variant="caption" weight="bold" color={mode === item.key ? color.text.onAction : color.text.heading}>{item.label}</Text>
          </Pressable>
        ))}
      </View>
      <View style={styles.rangeLine} accessibilityLiveRegion="polite">
        <Svg width={18} height={18} viewBox="0 0 24 24" fill="none">
          <Rect x={4} y={5} width={16} height={15} rx={2} stroke={color.text.heading} strokeWidth={1.8} />
          <Path d="M4 10h16M9 3v4M15 3v4" stroke={color.text.heading} strokeWidth={1.8} strokeLinecap="round" />
        </Svg>
        <Text weight="bold" style={styles.shrink}>{rangeText}</Text>
      </View>
      {mode === 'custom' && calendarOpen ? (
        <View style={styles.monthNav}>
          <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 달', 'Previous month')} disabled={monthOffset <= 0} onPress={() => setMonthOffset((n) => Math.max(0, n - 1))} style={[styles.navButton, monthOffset <= 0 && styles.navOff]}>
            <Text weight="bold">‹</Text>
          </Pressable>
          <View style={styles.monthBody}>
            <MonthGrid {...calendarMonth} value={{ ...EMPTY_START_BAR, startDate: pendingStart ?? from, endDate: pendingStart ? '' : to }} today={todayKey} onPick={pickDay} tx={tx} />
          </View>
          <Pressable accessibilityRole="button" accessibilityLabel={tx('다음 달', 'Next month')} disabled={monthOffset >= MAX_MONTH_OFFSET} onPress={() => setMonthOffset((n) => Math.min(MAX_MONTH_OFFSET, n + 1))} style={[styles.navButton, monthOffset >= MAX_MONTH_OFFSET && styles.navOff]}>
            <Text weight="bold">›</Text>
          </Pressable>
        </View>
      ) : null}
    </View>

    <View style={styles.sortRow} accessibilityRole="radiogroup">
      {([{ key: 'soon', label: tx('곧 시작순', 'Starting soon') }, { key: 'name', label: tx('이름순', 'By name') }] as const).map((item) => <Pressable key={item.key} accessibilityRole="radio" accessibilityState={{ selected: sort === item.key }} onPress={() => setSort(item.key)} style={[styles.sortButton, sort === item.key && styles.sortSelected]}><Text variant="caption" weight="bold" color={sort === item.key ? color.text.onAction : color.text.heading}>{item.label}</Text></Pressable>)}
    </View>

    {state === 'loading' && <View accessibilityLiveRegion="polite" style={styles.stateCard}><Text variant="title" weight="bold">{tx('축제를 확인하고 있어요', 'Checking festivals')}</Text><Text color={color.text.body}>{tx('선택한 기간과 부산 지역을 기준으로 조회합니다.', 'Searching based on your selected period and the Busan area.')}</Text></View>}
    {state === 'error' && <View accessibilityRole="alert" style={styles.stateCard}><Text variant="title" weight="bold">{tx('불러오지 못했습니다', 'Could not load')}</Text><Text color={color.text.body}>{localizeMessage(tx, errorMessage)}</Text><Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void load()} /></View>}
    {state === 'ready' && sorted.length === 0 && <View style={styles.stateCard}><Text variant="title" weight="bold">{tx('이 기간에 열리는 축제가 없습니다', 'No festivals run during this period')}</Text><Text color={color.text.body}>{tx('날짜를 바꿔 다시 조회해 보세요. 기간과 무관한 축제는 대신 보여드리지 않아요.', "Try different dates. We don't show festivals outside the period instead.")}</Text>
      {/* 빈 화면에서 나갈 길 — 기간 정보가 없는 축제도 로컬 탐색의 축제 갈래에는 있다(S15P21E201-1372). */}
      <Pressable accessibilityRole="button" onPress={() => router.push({ pathname: '/explore', params: { facet: 'FESTIVAL' } })} style={({ pressed }) => [styles.exploreLink, pressed && styles.pressed]}>
        <Text weight="bold" color={color.text.accent}>{tx('부산 전체 축제 장소 둘러보기 →', 'Browse all festival places in Busan →')}</Text>
      </Pressable>
    </View>}
    {state === 'ready' && sorted.length > 0 && <View style={styles.grid}>{sorted.map((festival) => {
      // 사진이 그 축제를 찍은 것이 아닐 수 있다 — 대부분은 열리는 장소 사진이다.
      // 그대로 두면 「이 축제가 이렇게 생겼구나」로 읽힌다.
      const photo = photoLabels(festival, tx);
      return <View key={festival.placeId} style={[styles.card, isAtLeast(width, 'md') && styles.cardWide]}>
        {festival.photoUrl ? <View>
          <Image source={{ uri: festival.photoUrl }} resizeMode="cover" style={styles.image} />
          {photo.badge && <View style={styles.photoBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>{photo.badge}</Text></View>}
        </View> : <View style={styles.imageFallback}>{/* 사진이 없으면 둘러보기와 같은 📍 그림(S15P21E201-1679) — 전에는 「GABOLLE」 글자만 있었다. */}<Text variant="title" color={color.text.muted}>📍</Text></View>}
        <View style={styles.cardBody}>
          <View style={styles.cardTopRow}>
            <Text variant="caption" weight="bold" color={color.text.eyebrow}>{festivalDates(festival, locale)}</Text>
          </View>
          <Text variant="title" weight="bold">{localNameFor(festival.localNames, language) ?? tx(festivalDisplayTitle(festival), festival.nameEn ?? festivalDisplayTitle(festival))}</Text>{/* 번역 이름이 없는 축제는 읽는 법을 곁들인다(S15P21E201-1867). */}{language !== 'ko' && !localNameFor(festival.localNames, language) && !festival.nameEn && romanizeKorean(festivalDisplayTitle(festival)) ? <Text variant="caption" color={color.text.body}>{romanizeKorean(festivalDisplayTitle(festival))}</Text> : null}<Text color={color.text.body}>{festival.address}</Text><Text variant="caption" color={color.text.muted}>{formatFeatureSlot(festival.priceLevel, tx) ?? tx('입장료 정보 확인 필요', 'Admission fee info not available yet')}</Text>
          {festival.photoUrl && photo.credit ? <PhotoCredit credit={photo.credit} licenseUrl={photo.licenseUrl} variant="caption" color={color.text.muted} /> : null}
          <Pressable accessibilityRole="button" onPress={() => accessToken ? setAddPlaceId(festival.placeId) : router.push({ pathname: '/sign-in', params: { returnTo: '/festivals' } })} style={({ pressed }) => [styles.addButton, pressed && styles.pressed]}>
            <Text variant="caption" weight="bold" color={color.action.outline}>{tx('+ 내 일정에 추가', '+ Add to my itinerary')}</Text>
          </Pressable>
        </View>
      </View>;
    })}</View>}
    <AddPlaceToItineraryModal visible={addPlaceId != null} placeId={addPlaceId ?? ''} onClose={() => setAddPlaceId(null)} />
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.canvas },
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] }, logo: { width: 154, height: 28 }, spacer: { width: 44 },
  heading: { gap: spacing[2], marginTop: spacing[4], marginBottom: spacing[6] },
  filterCard: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card }, filterCardWide: { maxWidth: 520 },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  rangeLine: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, shrink: { flexShrink: 1 },
  monthNav: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[1] }, monthBody: { flex: 1 },
  navButton: { width: 32, height: 40, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full }, navOff: { opacity: 0.3 },
  sortRow: { flexDirection: 'row', gap: spacing[2], marginVertical: spacing[4] }, sortButton: { minHeight: 40, justifyContent: 'center', paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, backgroundColor: color.surface.card }, sortSelected: { borderColor: color.action.secondary, backgroundColor: color.action.secondary },
  stateCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  exploreLink: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[4] }, card: { width: '100%', overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card }, cardWide: { width: '48%' }, image: { width: '100%', height: 180 }, imageFallback: { height: 180, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.soft }, cardBody: { gap: spacing[2], padding: spacing[4] },
  cardTopRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  photoBadge: { position: 'absolute', top: spacing[2], left: spacing[2], paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: 'rgba(25,25,25,0.78)' },
  addButton: { alignSelf: 'flex-start', minHeight: 36, justifyContent: 'center', paddingHorizontal: spacing[3], marginTop: spacing[1], borderRadius: radius.full, borderWidth: 1, borderColor: color.action.outline },
});
