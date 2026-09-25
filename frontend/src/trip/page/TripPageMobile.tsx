// 여행 페이지 — 폰 (추천 코스 + 일정 통합, 2단계) · S15P21E201-1535
//
// 시안: frontend/docs/design_handoff_trip_page/ (README 「모바일」 절 · design/TripPageMobile.dc.html · 스크린샷 mobile-*.png)
//
// 🔴 폰은 **여행 중에 여는 화면**이다(시안 README). 지도가 바탕이고, 그 위에 «탭바가 늘어난 창»이 올라온다.
//    창을 접으면 보통 탭바로 돌아가되 가운데가 「일정 펼치기」, 끝이 「뒤로」가 된다 — 이 화면에는
//    왼쪽 위 뒤로 단추가 없다. 모든 이동이 아래 막대에서 일어난다.
//
// 🔴 탭바 부품(TabBar)을 쓰지 않고 같은 수치로 따로 그린다. 추천 화면의 «코스 바» 와 같은 이유다 —
//    탭 칸 둘(가운데·끝)이 다른 것으로 바뀌고 창의 바탕색도 다르다(일정 = canvas, 접힘 = 흰색).
//    폭·높이·곡선은 TabBar 의 것을 그대로 가져온다. 한 앱 안에서 자라는 모양이 둘이면 같은 것으로 안 읽힌다.
//
// 🔴 이번 단계에 **없는 것** (시안에는 있다):
//    · 타임라인을 내려가는 내 위치 점 — 4단계다. 「지금」 카드의 출발·중지·건너뛰기는 지금도 된다.
//    · 지도 위 고른 곳의 붉은 맥동 링과 이름표 — 지도 부품(RouteMap)은 고른 표식을 키우기만 한다(1단계와 같다).
import { useEffect, useMemo, useRef, useState } from 'react';
import { Animated, BackHandler, Easing, Image, Platform, Pressable, ScrollView, StyleSheet, View, type ImageSourcePropType } from 'react-native';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useAuth } from '@/auth/AuthProvider';
import { ExcludeConfirmModal } from '@/components/ExcludeConfirmModal';
import { Skeleton } from '@/components/Skeleton';
import { BAR_MAX_WIDTH, SHEET_MAX_WIDTH, TAB_BAR_HEIGHT, tabBarBottomMargin } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { PLACE_CATEGORY_LABELS } from '@/discovery/placeCategoryLabels';
import { useI18n } from '@/i18n';
import { formatClock, formatDayHeading, formatWeekdayShort } from '@/i18n/datetime';
import { txf } from '@/i18n/format';
import { koreanSubject, koreanToward } from '@/i18n/korean';
import { localizeMessage } from '@/i18n/messages';
import { useLayout } from '@/layout/useLayout';
import { RouteMap } from '@/map/RouteMap';
import { courseLetter } from '@/plan/CourseCard';
import { NowCard } from '@/plan/NowCard';
import {
  pollItineraryJob, recordItineraryItemActual, removeItineraryItem, setItineraryItemLocked,
  type DayStart, type ItineraryItemDto, type ItineraryPaceItemDto,
} from '@/plan/itinerary';
import { formatTravelLabel, totalTravelMinutes } from '@/plan/itinerarySummary';
import { categoryGlyph, type PlacePhoto } from '@/plan/placePhotos';
import { canConfirmCourse, type TripCourse } from '@/plan/tripCourses';
import { drift, isToday, localDateKey, needsManualArrival, saysStartsIn, stepStates, type StepState } from '@/plan/tripProgress';
import { humanTripTitle } from '@/trip/tripNaming';
import { TripNameSheet } from '@/trip/TripNameSheet';

import { TripBudgetCard } from './TripBudgetCard';
import type { TripPageSource } from './tripPageData';
import { formatManwon, freeTimeMinutes } from './tripPageModel';
import { useTripPage } from './useTripPage';
import { useTripProgress } from './useTripProgress';
import { remainingMeters, stepDwell, usableFix, type Dwell } from './autoArrival';
import { useLiveLocation } from './useLiveLocation';
import { MobilityLayerToggle } from '@/map/MobilityLayerToggle';
import { useMobilityLayer, type MobilityLayerKind } from '@/map/mobilityLayers';
import { DayReturnRow } from './DayReturnRow';
import { DayStartRow } from './DayStartRow';
import { FreeTimeRow } from './FreeTimeRow';
import type { TripOverlayKind } from './TripOverlay';
import { TripInvitePanel } from '@/trip/TripInvitePanel';
import { TripReadLinkPanel } from '@/trip/TripReadLinkPanel';
import { DropdownMenu, useDropdownMenu, type DropdownMenuItem } from '@/components/DropdownMenu';
import { TripWeatherPanel } from '@/trip/TripWeatherPanel';

type Tx = (ko: string, en: string) => string;
type Panel = 'trip' | 'collapsed';

/**
 * 창이 열렸을 때 위에 남기는 지도 높이(안전영역 아래부터). 시안 390×844 에서 창 위끝이 236, 상태 줄이 47 이다.
 * 🔴 기기 높이에 비례시키지 않는다 — 지도에서 보고 싶은 것은 «고른 한 곳» 이고 그 크기는 기기와 무관하다.
 *    다만 가로로 돌린 폰처럼 화면이 낮으면 창이 너무 작아지므로 높이의 30% 를 넘기지 않는다.
 */
const MAP_PEEK = 190;
/** 접었을 때 탭바 위에 뜨는 정차지 카드 폭(시안 150). */
const STRIP_CARD = 150;
/** 타임라인 왼쪽 기둥 폭(시안 그리드 48px | 1fr). */
const RAIL = 48;
const THUMB = 64;
/** 창의 손잡이 줄 높이(시안 24). */
const HANDLE = 24;
/** 시안의 곡선 — 미끄러짐·접힘은 부드러운 곡선. */
const SLIDE = Easing.bezier(0.2, 0.8, 0.2, 1);
/**
 * 창이 오르내리는 움직임 (S15P21E201-1607). 🔴 올라갈 때와 내려갈 때 «같은 것»을 쓴다.
 * 전에는 튀는 곡선(overshoot)으로 창의 높이·폭·바탕색을 JS 쪽에서 매 프레임 바꿔서, 폰에서 버벅이고 끝에서 출렁였다.
 * 지금은 다 자란 판을 translateY 로만 밀어 올리므로 네이티브 드라이버로 돈다.
 */
const SHEET_MOTION = { duration: 360, easing: SLIDE, useNativeDriver: true } as const;

const TAB_ICONS: Record<'home' | 'feed' | 'map', ImageSourcePropType> = {
  home: require('../../../assets/icons/home/home.png'),
  feed: require('../../../assets/icons/home/heart.png'),
  map: require('../../../assets/icons/home/map.png'),
};

export function TripPageMobile({ source, askName = false }: { source: TripPageSource; askName?: boolean }) {
  const router = useRouter();
  const { accessToken } = useAuth();
  const { tx, locale } = useI18n();
  const insets = useSafeAreaInsets();
  const { width, height } = useLayout();

  const {
    page, load, courses, course, courseIndex, setCourseIndex, confirmed, setConfirmed, tripId,
    itinerary, setItinerary, loaded, reloadItinerary, dayIndex, setDayIndex, items, selectedId, setSelectedId,
    photos, pace, reloadPace, map, routes, points, budget, atRisk, allEstimated, title, headSub, confirm, confirming,
  } = useTripPage(source);

  const [panel, setPanel] = useState<Panel>('trip');
  /** 코스 알약을 눌렀나 — 눌러야 「코스 A로 확정」 줄이 펼쳐진다(시안 Interactions). */
  const [touched, setTouched] = useState(false);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  // ⋯ 메뉴는 공용 DropdownMenu(창)다 — 바깥을 누르거나 Escape 로 닫힌다(S15P21E201-1593). 전에는 본문 사이에 끼어드는 판이라
  //    닫는 길이 「⋯ 다시 누르기」뿐이었고, 연 채로 다른 창을 열면 그대로 남았다.
  const menu = useDropdownMenu();
  // 지도의 경사·그늘 겹(S15P21E201-1569).
  const [layerKind, setLayerKind] = useState<MobilityLayerKind | null>(null);
  const mobility = useMobilityLayer(layerKind, map.stops);
  const mapRoutes = useMemo(() => [...mobility.lines, ...routes], [mobility.lines, routes]);
  const [overlay, setOverlay] = useState<TripOverlayKind | null>(null);
  const [naming, setNaming] = useState(askName);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [excludeTarget, setExcludeTarget] = useState<ItineraryItemDto | null>(null);
  const [excludingId, setExcludingId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // 🔴 안이 하나뿐이면 고를 것이 없다 — 알약을 눌러야만 확정 줄이 나오면 그 한 안을 확정할 길이 안 보인다.
  useEffect(() => { if (courses.length === 1) setTouched(true); }, [courses.length]);
  // 코스·일차가 바뀌면 펼친 카드와 알림을 닫는다. 열린 채로 내용만 갈리면 무엇이 펼쳐졌는지 모른다.
  useEffect(() => { setExpandedId(null); setNotice(null); }, [course?.id, dayIndex]);

  // ── 진행 (「지금」 카드) — 확정한 일정에서만 ────────────────────────────────
  const progressId = confirmed ? loaded?.id ?? null : null;
  const run = useTripProgress(progressId);
  const day = loaded?.days[dayIndex];
  // 그날 첫 곳은 어디서 오나 — 둘째 날부터는 숙소다(S15P21E201-1580). 서버가 안 알려 주면(옛 응답) 출발지.
  const startKind: DayStart['kind'] = day?.start?.kind ?? 'ORIGIN';
  const stopIds = useMemo(() => items.map((item) => item.id), [items]);
  const steps = stepStates(stopIds, run.progress);
  // 🔴 오늘 날짜의 일차에서만 그린다 — 지난 날짜나 다음 날짜에 「출발」이 있으면 거짓이다(itinerary.tsx 와 같은 규칙).
  const showNow = Boolean(progressId && items.length && isToday(day?.date, localDateKey(new Date())));
  const canEdit = confirmed && loaded?.canEdit !== false;
  const paceByItemId = useMemo(() => new Map((pace?.items ?? []).map((entry) => [entry.itemId, entry] as const)), [pace]);
  const paceEstimated = pace?.paceFactor == null;

  // ── 모양 ──────────────────────────────────────────────────────────────────
  const bottomMargin = tabBarBottomMargin(insets.bottom);
  const sheetTop = insets.top + Math.min(MAP_PEEK, Math.round(height * 0.3));
  const sheetHeight = Math.max(TAB_BAR_HEIGHT * 4, height - bottomMargin - sheetTop);
  // 🔴 지도는 줄이지 않는다(S15P21E201-1607). 전에는 창이 열리면 지도 칸을 창 위만큼으로 줄여서, 여닫을 때마다
  //    지도가 다시 가운데를 잡으며 튀었다. 지금은 늘 화면 전체이고 창이 그 위에 겹친다. 창에 가린 높이(mapCovered)만
  //    지도에 알려서, 고른 곳을 보이는 부분의 가운데로 옮기게 한다(RouteMap 의 bottomInset).
  const mapHeight = height;
  const [stripHeight, setStripHeight] = useState(0);
  const mapCovered = panel === 'trip' ? height - sheetTop : bottomMargin + TAB_BAR_HEIGHT + spacing[2] + stripHeight;

  // 창은 다 자란 크기로 깔아 두고 밀어 올린다 — 높이를 매 프레임 바꾸지 않는다.
  const shown = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    Animated.timing(shown, { toValue: panel === 'trip' ? 1 : 0, ...SHEET_MOTION }).start();
  }, [panel, shown]);
  // 창을 접으면 창 안에 열어 둔 판(동행 초대 등)도 닫는다 — 다시 펴면 일정이 보여야 한다.
  useEffect(() => { if (panel === 'collapsed') setOverlay(null); }, [panel]);
  // 창 안의 판은 뒤로 가기(안드로이드)·Escape(웹)로도 닫힌다 — 전에 모달이 하던 일이다.
  useEffect(() => {
    if (!overlay) return undefined;
    if (Platform.OS === 'web') {
      if (typeof window === 'undefined') return undefined;
      const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setOverlay(null); };
      window.addEventListener('keydown', onKey);
      return () => window.removeEventListener('keydown', onKey);
    }
    const sub = BackHandler.addEventListener('hardwareBackPress', () => { setOverlay(null); return true; });
    return () => sub.remove();
  }, [overlay]);

  const dayTravel = totalTravelMinutes(items);
  const mapSummary = [
    txf(tx, '장소 %s곳', '%s places', items.length),
    dayTravel > 0 ? txf(tx, '이동 %s분', '%s min travel', dayTravel) : null,
  ].filter(Boolean).join(' · ');

  // ── 편집 — 고정 · 도착 기록 · 제외 (확정한 일정에서만) ─────────────────────────
  const toggleLock = async (item: ItineraryItemDto) => {
    if (!loaded || busyId) return;
    setBusyId(item.id); setNotice(null);
    const next = await setItineraryItemLocked({ itineraryId: loaded.id, itemId: item.id, locked: !item.locked, baseVersion: loaded.version, accessToken });
    setBusyId(null);
    if (next.state === 'success') setItinerary({ id: next.itinerary.id, value: next.itinerary, message: null });
    else setNotice(next.message);
  };

  const recordArrival = async (item: ItineraryItemDto) => {
    if (!loaded || busyId) return;
    setBusyId(item.id); setNotice(null);
    const next = await recordItineraryItemActual({ itineraryId: loaded.id, itemId: item.id, arrivedAt: new Date().toISOString(), departedAt: null, accessToken });
    setBusyId(null);
    // 🔴 도착 기록은 일정 판을 안 올린다 — 예상 도착을 따로 다시 받는다(useTripPage 의 paceNonce).
    if (next.state === 'success') { setItinerary({ id: next.itinerary.id, value: next.itinerary, message: null }); reloadPace(); }
    else setNotice(next.message);
  };

  const exclude = async (item: ItineraryItemDto) => {
    if (!loaded) return;
    setExcludingId(item.id); setNotice(null);
    const accepted = await removeItineraryItem({ itineraryId: loaded.id, itemId: item.id, baseVersion: loaded.version, accessToken });
    if (accepted.state === 'accepted') {
      // 제외는 서버가 새 일정을 «안 돌려준다» — 작업이 끝나기를 기다렸다가 다시 받는다(itinerary.tsx 의 runJob 과 같다).
      for (;;) {
        const poll = await pollItineraryJob(accepted.jobId, accessToken);
        if (poll.state === 'pending') { await new Promise((resolve) => setTimeout(resolve, 2000)); continue; }
        if (poll.state === 'succeeded') { setExpandedId(null); reloadItinerary(); } else setNotice(poll.message);
        break;
      }
    } else setNotice(accepted.message);
    setExcludingId(null);
  };

  const openClassic = () => {
    if (course?.itineraryId) router.push(`/trips/${course.itineraryId}/itinerary?classic=1`);
  };
  const goBack = () => (router.canGoBack() ? router.back() : router.replace('/trips'));

  // ── 「지금」 카드의 글 — itinerary.tsx 와 같은 규칙 ──────────────────────────────
  const progress = run.progress;
  const currentStop = items[Math.min(progress.currentStopIndex, Math.max(0, items.length - 1))] ?? null;
  const nowDriftValue = drift(new Date().toISOString(), currentStop?.startsAt ?? null);

  // ── 내 위치 · 자동 도착 (S15P21E201-1568) ────────────────────────────────────
  // 출발(RUNNING) 동안만 위치를 따라간다. 지금 향하는 곳 50m 안에 2분 머물면 도착으로 적는다(autoArrival.ts).
  const live = useLiveLocation(progress.status === 'RUNNING');
  const target = currentStop && typeof currentStop.lat === 'number' && typeof currentStop.lng === 'number'
    ? { id: currentStop.id, latitude: currentStop.lat, longitude: currentStop.lng } : null;
  const dwellRef = useRef<Dwell>(null);
  const liveFixRef = useRef(live.fix);
  liveFixRef.current = live.fix;
  const targetRef = useRef(target);
  targetRef.current = target;
  const arriveRef = useRef(run.arrive);
  arriveRef.current = run.arrive;
  useEffect(() => {
    if (progress.status !== 'RUNNING') { dwellRef.current = null; return undefined; }
    // 가만히 있으면 기기가 새 위치를 안 줄 수 있다 — 마지막 위치로 15초마다 다시 센다(그 사이 거기 있었다고 본다).
    const check = (atNow: boolean) => {
      const fix = liveFixRef.current;
      const step = stepDwell(dwellRef.current, fix && atNow ? { ...fix, at: Date.now() } : fix, targetRef.current);
      dwellRef.current = step.dwell;
      if (step.arrive && targetRef.current) {
        dwellRef.current = null;
        arriveRef.current(stopIds, targetRef.current.id, 'auto');
      }
    };
    check(false);
    const timer = setInterval(() => check(true), 15_000);
    return () => clearInterval(timer);
  }, [progress.status, live.fix, stopIds]);
  // 위치를 믿을 수 있을 때만 「도착」 단추를 숨긴다. 거부·꺼짐·부정확이면 손으로 적게 둔다.
  const gpsUsable = live.state === 'on' && usableFix(live.fix);
  const driftSpan = nowDriftValue
    ? nowDriftValue.minutes >= 60
      ? nowDriftValue.minutes % 60 === 0
        ? txf(tx, '%s시간', '%s h', Math.floor(nowDriftValue.minutes / 60))
        : txf(tx, '%s시간 %s분', '%s h %s min', Math.floor(nowDriftValue.minutes / 60), nowDriftValue.minutes % 60)
      : txf(tx, '%s분', '%s min', nowDriftValue.minutes)
    : null;
  const nowDrift = nowDriftValue && driftSpan
    ? saysStartsIn(progress.status, nowDriftValue)
      ? txf(tx, '%s 뒤 시작', 'starts in %s', driftSpan)
      : txf(tx, '예정보다 %s %s', '%s %s', driftSpan, nowDriftValue.early ? tx('빠름', 'early') : tx('늦음', 'late'))
    : null;
  const nowTitle = progress.status === 'DONE'
    ? tx('오늘 일정을 다 돌았어요', 'You finished today')
    : progress.status === 'RUNNING' && currentStop
      ? txf(tx, `%s${koreanToward(currentStop.title)} 이동 중`, 'Heading to %s', currentStop.title)
      : currentStop ? txf(tx, '다음은 %s', 'Next: %s', currentStop.title) : tx('오늘 갈 곳이 없어요', 'Nothing planned today');
  const doneCount = stopIds.filter((id) => progress.outcomes[id]).length;
  // 🔴 남은 거리 — 위치를 믿을 수 있을 때만(S15P21E201-1568). 모르면 안 적는다.
  const leftM = progress.status === 'RUNNING' ? remainingMeters(live.fix, target) : null;
  const leftText = leftM == null ? null
    : leftM >= 1000 ? txf(tx, '남은 거리 %skm', '%s km to go', (leftM / 1000).toFixed(1)) : txf(tx, '남은 거리 %sm', '%s m to go', leftM);
  // 🔴 출발 전에는 「첫 곳까지 얼마」를 적는다(시안 4a 「출발지에서 50분」). 없는 안내를 지어내지 않는다 —
  //    구간 시간을 모르면 아무것도 안 적는다.
  const nowDetail = progress.status === 'PLANNED'
    ? (items[0] ? formatTravelLabel(items[0], tx, startKind) : null)
    : txf(tx, '%s곳 중 %s곳 다녀옴', '%s of %s stops done', items.length, doneCount)
      + (progress.status !== 'DONE' && currentStop ? ` · ${txf(tx, '다음 %s', 'next %s', currentStop.startsAt.slice(11, 16))}` : '')
      + (leftText ? ` · ${leftText}` : '');
  const nowRatio = items.length && progress.status !== 'PLANNED' ? doneCount / items.length : null;

  // ── 조각 ─────────────────────────────────────────────────────────────────
  const ready = page?.state === 'ready';

  const tripContent = (
    <ScrollView style={styles.sheetScroll} contentContainerStyle={styles.sheetContent} showsVerticalScrollIndicator={false}>
      {/* 머리 — 제목 · 요약 · (확정했으면) 「✓ 코스 A 확정 · 바꾸기」 · ⋯ */}
      <View style={styles.head}>
        <View style={styles.headCopy}>
          <Text variant="display" weight="bold" numberOfLines={2}>{ready && loaded ? title : tx('여행', 'Trip')}</Text>
          {headSub ? <Text variant="caption" color={color.text.muted}>{headSub}</Text> : null}
          {confirmed && course ? (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={tx('코스 바꾸기', 'Change course')}
              disabled={courses.length < 2}
              onPress={() => { setConfirmed(false); setTouched(true); }}
              style={({ pressed }) => [styles.confirmedChip, pressed && styles.pressed]}
            >
              <Text variant="micro" weight="bold" color={color.state.success}>{txf(tx, '✓ 코스 %s 확정', '✓ Course %s confirmed', courseLetter(courseIndex))}</Text>
              {/* 🔴 안이 하나뿐이면 「바꾸기」를 적지 않는다 — 바꿀 곳이 없는데 누르라고 하면 고장으로 읽힌다. */}
              {courses.length > 1 ? <Text variant="micro" weight="bold" color={color.text.inactiveTab}>{` · ${tx('바꾸기', 'Change')}`}</Text> : null}
            </Pressable>
          ) : null}
        </View>
        {ready ? (
          <Pressable ref={menu.buttonRef} accessibilityRole="button" accessibilityLabel={tx('더 보기', 'More')} accessibilityState={{ expanded: menu.open }} onPress={menu.openMenu} style={({ pressed }) => [styles.circle44, pressed && styles.pressed]}>
            <Text variant="title" weight="bold">⋯</Text>
          </Pressable>
        ) : null}
      </View>

      {/* 알약 — 지도 보기(= 창 접기) · 동행 초대 · 공유 · 기록 남기기 · 날씨. 창은 화면을 옮기지 않고 아래 시트로 연다(S15P21E201-1561).
          「공유」는 읽기 전용 링크만 담은 창, 「기록 남기기」는 이 여행을 단 글쓰기다(S15P21E201-1593). */}
      {ready ? (
        <View style={styles.actions}>
          <ActionPill label={tx('지도 보기', 'View map')} onPress={() => setPanel('collapsed')} />
          {tripId ? <ActionPill label={tx('동행 초대', 'Invite')} onPress={() => setOverlay('invite')} /> : null}
          {tripId ? <ActionPill label={tx('공유', 'Share')} onPress={() => setOverlay('share')} /> : null}
          {tripId ? <ActionPill label={tx('기록 남기기', 'Write a record')} onPress={() => router.push(`/feed/compose?tripId=${encodeURIComponent(tripId)}`)} /> : null}
          {tripId ? <ActionPill label={tx('날씨', 'Weather')} onPress={() => setOverlay('weather')} /> : null}
        </View>
      ) : null}

      {/* 여러 날이면 일차를 고른다 — 시안은 하루짜리라 이 줄이 없다. */}
      {loaded && loaded.days.length > 1 ? (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.dayRow}>
          {loaded.days.map((entry, index) => (
            <Pressable key={`${entry.date}-${index}`} accessibilityRole="tab" accessibilityState={{ selected: index === dayIndex }} onPress={() => setDayIndex(index)} style={[styles.dayChip, index === dayIndex && styles.dayChipOn]}>
              <Text variant="caption" weight="bold" color={index === dayIndex ? color.text.onAction : color.text.heading}>{formatDayHeading(entry.date, locale) ?? txf(tx, '%s일차', 'Day %s', index + 1)}</Text>
            </Pressable>
          ))}
        </ScrollView>
      ) : null}

      {notice ? <View accessibilityRole="alert" style={styles.notice}><Text variant="caption" color={color.text.body}>{localizeMessage(tx, notice)}</Text></View> : null}
      {confirmed && loaded && loaded.canEdit === false ? <View style={styles.notice}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('보기 전용 — 이 일정을 편집할 권한이 없어요.', "View only — you don't have permission to edit this itinerary.")}</Text></View> : null}

      {!page ? <SheetSkeleton /> : page.state === 'error' ? (
        <ErrorCard message={localizeMessage(tx, page.message)} onRetry={() => void load()} tx={tx} />
      ) : (
        <>
          {showNow ? (
            <View style={styles.nowWrap}>
              <NowCard
                status={progress.status}
                gpsUsable={gpsUsable}
                title={nowTitle}
                detail={nowDetail}
                clock={new Date().toTimeString().slice(0, 5)}
                driftText={nowDrift}
                progress={nowRatio}
                showManualArrival={needsManualArrival(progress.status, gpsUsable)}
                onStart={run.start}
                onPause={run.pause}
                onArrive={() => currentStop && run.arrive(stopIds, currentStop.id)}
                onSkip={() => currentStop && run.skip(stopIds, currentStop.id)}
                tx={tx}
              />
              {/* 🔴 기기에만 남는 판에서는 그 사실을 적는다. 안 적으면 사용자는 어디서나 이어지는 줄 안다. */}
              {run.deviceOnly ? <Text variant="caption" color={color.text.muted}>{tx('진행 상태는 이 기기에만 저장돼요. 다른 기기에서는 아직 안 보여요.', 'Progress is saved on this device only — it does not show on your other devices yet.')}</Text> : null}
              {run.error ? <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{run.error}</Text> : null}
            </View>
          ) : null}

          <CourseCardMobile
            open={!confirmed}
            courses={courses}
            index={courseIndex}
            full={page.full}
            estimated={allEstimated}
            items={items}
            touched={touched}
            confirming={confirming}
            onPick={(index) => { setCourseIndex(index); setTouched(true); }}
            onConfirm={() => course && void confirm(course)}
            tx={tx}
          />

          {loaded ? <RiskStrip atRisk={atRisk} known={pace !== null} estimated={paceEstimated} tx={tx} /> : null}

          {!loaded ? (
            itinerary?.message
              ? <ErrorCard message={localizeMessage(tx, itinerary.message)} onRetry={() => void load()} tx={tx} />
              : <SheetSkeleton />
          ) : items.length === 0 ? (
            <View style={styles.emptyCard}><Text variant="caption" color={color.text.muted}>{tx('이 날에는 아직 장소가 없어요.', 'No places for this day yet.')}</Text></View>
          ) : (
            <View>
              {/* 하루 시작 — 첫날은 출발지, 둘째 날부터는 숙소에서 (S15P21E201-1580) */}
              <DayStartRow start={day?.start} tx={tx} />
              {items.map((item, index) => (
                <TimelineStop
                  key={item.id}
                  item={item}
                  startKind={startKind}
                  index={index}
                  last={index === items.length - 1}
                  freeBefore={index > 0 ? freeTimeMinutes(items, index - 1) : null}
                  date={index === 0 ? day?.date ?? null : null}
                  photo={photos[item.placeId] ?? null}
                  step={progressId ? steps[index] : undefined}
                  risky={pace?.atRiskItemIds.includes(item.id) ?? false}
                  pace={paceByItemId.get(item.id)}
                  paceEstimated={paceEstimated}
                  expanded={expandedId === item.id}
                  canEdit={canEdit}
                  busy={busyId === item.id}
                  excluding={excludingId === item.id}
                  onToggle={() => { setSelectedId(item.id); setExpandedId((open) => (open === item.id ? null : item.id)); }}
                  onLock={() => void toggleLock(item)}
                  onArrive={() => void recordArrival(item)}
                  onExclude={() => setExcludeTarget(item)}
                  tx={tx}
                  locale={locale}
                />
              ))}
              {/* 하루 끝 — 숙소(마지막 날은 출발지)로 돌아가기 (S15P21E201-1566) */}
              <DayReturnRow leg={day?.returnLeg} tx={tx} />
            </View>
          )}

          {loaded ? <TripBudgetCard budget={budget} style={styles.budget} /> : null}
          {canEdit && items.length > 1 ? (
            <Pressable accessibilityRole="button" onPress={openClassic} style={({ pressed }) => [styles.reorder, pressed && styles.pressed]}>
              <Text weight="bold">{tx('순서 수정', 'Reorder')}</Text>
            </Pressable>
          ) : null}
        </>
      )}
    </ScrollView>
  );

  const menuItems: DropdownMenuItem[] = [
    ...(tripId ? [{ key: 'rename', label: tx('이름 바꾸기', 'Rename'), onPress: () => setNaming(true) }] : []),
    ...(course?.itineraryId ? [{ key: 'edit', label: tx('일정 편집', 'Edit itinerary'), hint: tx('순서·고정·제외·다시 계산', 'Order, pin, remove, recalculate'), onPress: openClassic }] : []),
  ];

  return (
    <View style={styles.shell}>
      {/* ── 바탕 지도 ─────────────────────────────────────────────────────────── */}
      <View style={[styles.mapClip, { height: mapHeight }]}>
        {map.stops.length ? (
          // 🔴 지도 부품은 둥근 테두리 칸으로 그려진다. 바탕으로 쓰려면 모서리를 화면 밖으로 밀어낸다.
          <View style={styles.mapBleed}>
            <RouteMap stops={map.stops} selectedId={selectedId} onSelect={setSelectedId} routes={mapRoutes} points={points} currentLocation={usableFix(live.fix) ? { latitude: live.fix.latitude, longitude: live.fix.longitude } : null} height={mapHeight + radius.lg * 2} focusSelected bottomInset={mapCovered} />
          </View>
        ) : loaded ? (
          <View style={[styles.mapEmpty, { paddingTop: insets.top }]}>
            <Text variant="caption" color={color.text.muted}>{tx('장소의 좌표가 아직 없어 지도에 그릴 수 없어요.', 'These places have no coordinates yet, so the map is empty.')}</Text>
          </View>
        ) : null}
      </View>
      {loaded ? (
        <View pointerEvents="none" style={[styles.mapSummary, { top: insets.top + spacing[2] }]}>
          <Text variant="caption" weight="bold" numberOfLines={1}>{mapSummary}</Text>
        </View>
      ) : null}
      {loaded && map.stops.length ? (
        <MobilityLayerToggle value={layerKind} onChange={setLayerKind} basis={mobility.basis} tx={tx} style={[styles.mapLayers, { top: insets.top + spacing[2] + 40 }]} />
      ) : null}

      {/* ── 접었을 때 — 탭바 위 정차지 카드 줄 (시안 4b) ──────────────────────────── */}
      {panel === 'collapsed' && items.length ? (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={[styles.strip, { bottom: bottomMargin + TAB_BAR_HEIGHT + spacing[2] }]}
          onLayout={(event) => setStripHeight(Math.ceil(event.nativeEvent.layout.height))}
          contentContainerStyle={styles.stripInner}
        >
          {items.map((item, index) => (
            <Pressable key={item.id} accessibilityRole="button" accessibilityState={{ selected: item.id === selectedId }} onPress={() => setSelectedId(item.id)} style={[styles.stripCard, item.id === selectedId && styles.stripCardOn]}>
              <View style={styles.rowCenter}>
                <View style={styles.numberDot}><Text variant="micro" weight="bold" color={color.text.onAction}>{index + 1}</Text></View>
                <Text variant="caption" weight="bold" color={color.text.muted}>{item.startsAt.slice(11, 16)}</Text>
              </View>
              <Text weight="bold" numberOfLines={1}>{item.title}</Text>
              {formatTravelLabel(item, tx, index === 0 && startKind) ? <Text variant="micro" color={color.text.muted} numberOfLines={1}>{formatTravelLabel(item, tx, index === 0 && startKind)}</Text> : null}
            </Pressable>
          ))}
        </ScrollView>
      ) : null}

      {/* ── 접힌 탭 줄 — 홈 · 피드 · 일정 펼치기 · 내 여행 · 뒤로 (시안 4b). 늘 제자리이고, 창이 올라오면 그 밑에 깔린다. ── */}
      <View pointerEvents="box-none" style={[styles.dock, { paddingBottom: bottomMargin }]}>
        <View
          pointerEvents={panel === 'collapsed' ? 'auto' : 'none'}
          aria-hidden={panel !== 'collapsed' || undefined}
          style={[styles.bar, { width: Math.max(0, width - spacing[4] * 2), maxWidth: BAR_MAX_WIDTH, height: TAB_BAR_HEIGHT, backgroundColor: color.surface.card }]}
        >
          <View style={styles.tabRow}>
            <TabSlot label={tx('홈', 'Home')} icon={TAB_ICONS.home} onPress={() => router.replace('/home')} />
            <TabSlot label={tx('피드', 'Feed')} icon={TAB_ICONS.feed} onPress={() => router.replace('/feed')} />
            <TabSlot label={tx('일정 펼치기', 'Show itinerary')} strong onPress={() => setPanel('trip')}>
              <View style={styles.expandCircle}><View style={styles.chevronUp} /></View>
            </TabSlot>
            <TabSlot label={tx('내 여행', 'My trips')} icon={TAB_ICONS.map} selected onPress={() => router.replace('/trips')} />
            <TabSlot label={tx('뒤로', 'Back')} strong onPress={goBack}>
              <View style={styles.backCircle}><View style={styles.chevronLeft} /></View>
            </TabSlot>
          </View>
        </View>
      </View>

      {/* ── 창 — 다 자란 크기로 깔아 두고 밀어 올린다(S15P21E201-1607). 지도는 그 뒤에 그대로 있다. ── */}
      <View pointerEvents="box-none" style={[styles.dock, styles.sheetDock, { paddingBottom: bottomMargin }]}>
        <Animated.View
          pointerEvents={panel === 'trip' ? 'auto' : 'none'}
          aria-hidden={panel !== 'trip' || undefined}
          style={[
            styles.bar,
            styles.sheet,
            {
              width: Math.max(0, width - spacing[4] * 2),
              maxWidth: SHEET_MAX_WIDTH,
              height: sheetHeight,
              // 접히면 화면 아래로 — 그림자까지 치우게 조금 더 내린다.
              transform: [{ translateY: shown.interpolate({ inputRange: [0, 1], outputRange: [sheetHeight + bottomMargin + spacing[8], 0] }) }],
            },
          ]}
        >
          <Pressable accessibilityRole="button" accessibilityLabel={tx('일정 접기', 'Hide itinerary')} onPress={() => setPanel('collapsed')} style={styles.handleZone}>
            <View style={styles.handle} />
          </Pressable>
          {/* 🔴 동행 초대·공유·날씨는 창 «안에서» 내용만 바꾼다(S15P21E201-1607). 전에는 창 위에 아래 판이 하나 더 올라와
              두 겹이 됐다. 「기록 남기기」는 글쓰기 화면으로 이동한다(사용자 결정). */}
          {overlay && tripId ? (
            <View style={styles.sheetScroll}>
              <View style={styles.overlayHead}>
                <Pressable accessibilityRole="button" accessibilityLabel={tx('일정으로 돌아가기', 'Back to itinerary')} onPress={() => setOverlay(null)} style={({ pressed }) => [styles.circle44, pressed && styles.pressed]}>
                  <View style={styles.chevronLeft} />
                </Pressable>
                <Text variant="title" weight="bold" numberOfLines={1} style={styles.shrink}>
                  {overlay === 'invite' ? tx('동행 초대', 'Invite') : overlay === 'share' ? tx('공유', 'Share') : tx('날씨', 'Weather')}
                </Text>
              </View>
              <ScrollView style={styles.sheetScroll} contentContainerStyle={styles.sheetContent} showsVerticalScrollIndicator={false}>
                {overlay === 'invite' ? <TripInvitePanel tripId={tripId} onNavigate={() => setOverlay(null)} /> : null}
                {overlay === 'share' ? <TripReadLinkPanel tripId={tripId} /> : null}
                {overlay === 'weather' ? <TripWeatherPanel date={loaded ? (loaded.days[0]?.date ?? null) : undefined} items={loaded?.days[0]?.items} /> : null}
              </ScrollView>
            </View>
          ) : tripContent}
        </Animated.View>
      </View>

      {naming && tripId ? (
        <TripNameSheet
          tripId={tripId}
          currentTitle={humanTripTitle(loaded?.title)}
          dateLabel={headSub || null}
          accessToken={accessToken}
          onClose={() => setNaming(false)}
          onSaved={(saved) => {
            setNaming(false);
            // 다시 부르지 않고 제목만 바꾼다 — 다시 부르면 잠깐 옛 이름이 보여 「안 바뀌었다」로 읽힌다.
            if (saved) setItinerary((prev) => (prev?.value ? { ...prev, value: { ...prev.value, title: saved } } : prev));
          }}
        />
      ) : null}
      <ExcludeConfirmModal
        visible={excludeTarget !== null}
        placeTitle={excludeTarget?.title ?? ''}
        busy={excludingId !== null}
        onCancel={() => setExcludeTarget(null)}
        onConfirm={() => {
          const target = excludeTarget;
          setExcludeTarget(null);
          if (target) void exclude(target);
        }}
      />
      <DropdownMenu visible={menu.open} anchor={menu.anchor} items={menuItems} onClose={menu.close} />
    </View>
  );
}

// ── 부품 ────────────────────────────────────────────────────────────────────

function ActionPill({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [styles.actionPill, pressed && styles.pressed]}>
      <Text variant="caption" weight="bold" numberOfLines={1}>{label}</Text>
    </Pressable>
  );
}

function TabSlot({ label, icon, selected = false, strong = false, onPress, children }: {
  label: string; icon?: ImageSourcePropType; selected?: boolean; strong?: boolean; onPress: () => void; children?: React.ReactNode;
}) {
  return (
    <Pressable accessibilityRole="tab" accessibilityLabel={label} accessibilityState={{ selected }} onPress={onPress} style={({ pressed }) => [styles.tab, pressed && styles.pressed]}>
      <View style={styles.tabIcon}>
        {children ?? (icon ? <Image source={icon} resizeMode="contain" style={[styles.tabImage, selected ? styles.tabImageOn : styles.tabImageOff]} /> : null)}
      </View>
      <Text variant="micro" weight={selected || strong ? 'bold' : 'regular'} color={selected || strong ? color.text.heading : color.text.inactiveTab} numberOfLines={1}>{label}</Text>
    </Pressable>
  );
}

/**
 * 추천 코스 카드 — 3분할 알약 · 경로 한 줄 · (누르면) 「코스 A로 확정」.
 * 확정하면 카드 전체가 접힌다(시안: max-height 0 · 투명 · 위 간격 −12 로 gap 상쇄, 420ms).
 *
 * 🔴 오는 코스 수만큼만 칸을 만든다 — 셋을 그리고 둘을 비워 두면 눌러도 아무 일이 없어 고장으로 읽힌다
 *    (TripPageDesktop 의 CoursePill 과 같은 규칙).
 */
function CourseCardMobile({ open, courses, index, full, estimated, items, touched, confirming, onPick, onConfirm, tx }: {
  open: boolean; courses: TripCourse[]; index: number; full: boolean; estimated: boolean; items: ItineraryItemDto[];
  touched: boolean; confirming: boolean; onPick: (index: number) => void; onConfirm: () => void; tx: Tx;
}) {
  const shown = useRef(new Animated.Value(open ? 1 : 0)).current;
  const confirmRow = useRef(new Animated.Value(open && touched ? 1 : 0)).current;
  const x = useRef(new Animated.Value(0)).current;
  const [cardHeight, setCardHeight] = useState(0);
  const [trackWidth, setTrackWidth] = useState(0);
  const slot = courses.length ? Math.max(0, (trackWidth - spacing[1] * 2) / courses.length) : 0;

  useEffect(() => {
    Animated.timing(shown, { toValue: open ? 1 : 0, duration: 420, easing: SLIDE, useNativeDriver: false }).start();
  }, [open, shown]);
  useEffect(() => {
    Animated.timing(confirmRow, { toValue: open && touched ? 1 : 0, duration: 380, easing: SLIDE, useNativeDriver: false }).start();
  }, [open, touched, confirmRow]);
  useEffect(() => {
    Animated.timing(x, { toValue: index * slot, duration: 360, easing: SLIDE, useNativeDriver: false }).start();
  }, [index, slot, x]);

  const course = courses[index] ?? null;
  if (!courses.length) return null;
  const routeLine = items.map((item) => item.title).join(' → ');

  return (
    <Animated.View
      pointerEvents={open ? 'auto' : 'none'}
      style={[styles.courseClip, {
        opacity: shown,
        marginTop: shown.interpolate({ inputRange: [0, 1], outputRange: [-spacing[3], 0] }),
        // 높이를 재기 전에는 가두지 않는다 — 0 으로 가두면 재기도 전에 사라진다.
        ...(cardHeight ? { maxHeight: shown.interpolate({ inputRange: [0, 1], outputRange: [0, cardHeight] }) } : null),
      }]}
    >
      <View style={styles.courseCard} onLayout={(event) => setCardHeight(Math.ceil(event.nativeEvent.layout.height))}>
        <View style={styles.courseHead}>
          <Text variant="caption" weight="bold" color={color.text.muted}>{tx('추천 코스', 'Courses')}</Text>
          {estimated ? <View style={styles.chipEstimated}><Text variant="micro" weight="bold" color={color.state.warning}>{tx('추정', 'Estimated')}</Text></View> : null}
        </View>
        <View accessibilityRole="tablist" style={styles.track} onLayout={(event) => setTrackWidth(event.nativeEvent.layout.width)}>
          {slot > 0 ? <Animated.View style={[styles.courseIndicator, { width: slot, transform: [{ translateX: x }] }]} /> : null}
          {courses.map((entry, i) => {
            const on = i === index;
            return (
              <Pressable key={entry.id} accessibilityRole="tab" accessibilityState={{ selected: on }} onPress={() => onPick(i)} style={styles.courseSlot}>
                <Text variant="caption" weight="bold" numberOfLines={1} color={on ? color.text.onAction : color.text.body}>{`${on ? '✓ ' : ''}${txf(tx, '코스 %s', 'Course %s', courseLetter(i))}`}</Text>
                {entry.summary.costKrw !== null ? <Text variant="micro" numberOfLines={1} color={on ? color.text.onDarkMuted : color.text.body}>{txf(tx, '%s 예상', '%s est.', formatManwon(entry.summary.costKrw, tx))}</Text> : null}
              </Pressable>
            );
          })}
        </View>
        {/* 🔴 한 안뿐인 이유를 말한다. 조용히 하나만 그리면 비교를 놓친 줄도 모른다(넓은 화면과 같은 문구). */}
        {full ? null : <Text variant="micro" color={color.text.muted} style={styles.coursePad}>{tx('세 코스 비교는 준비 중이에요', 'Comparing three courses is on the way')}</Text>}
        {routeLine ? <Text variant="caption" color={color.text.body} style={styles.coursePad}>{routeLine}</Text> : null}
        <Animated.View style={[styles.confirmClip, { opacity: confirmRow, maxHeight: confirmRow.interpolate({ inputRange: [0, 1], outputRange: [0, 64] }) }]}>
          <View style={styles.confirmRow}>
            <Text variant="micro" color={color.text.muted} style={styles.confirmHint}>{tx('확정하면 이 카드는 접혀요. 위에서 다시 바꿀 수 있어요.', 'Confirming folds this card. You can change it again from the top.')}</Text>
            {course ? (
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ busy: confirming, disabled: confirming || !canConfirmCourse(course) }}
                disabled={confirming || !canConfirmCourse(course)}
                onPress={onConfirm}
                style={({ pressed }) => [styles.confirmButton, (pressed || confirming) && styles.pressed]}
              >
                <Text weight="bold" color={color.text.onAction}>{txf(tx, '코스 %s로 확정', 'Confirm course %s', courseLetter(index))}</Text>
              </Pressable>
            ) : null}
          </View>
        </Animated.View>
      </View>
    </Animated.View>
  );
}

/** 위험 띠 — 하루를 넘길 곳이 있으면 연분홍에 빨간 글자, 없으면 초록. 🔴 모르면 「모른다」고 적는다. */
function RiskStrip({ atRisk, known, estimated, tx }: { atRisk: ItineraryItemDto[]; known: boolean; estimated: boolean; tx: Tx }) {
  const risky = atRisk.length > 0;
  const text = risky
    ? atRisk.length === 1
      ? txf(tx, `%s${koreanSubject(atRisk[0].title)} 하루를 넘길 수 있어요`, '%s may run past the day', atRisk[0].title)
      : txf(tx, '%s곳이 하루를 넘길 수 있어요', '%s places may run past the day', atRisk.length)
    : known ? tx('하루 안에 여유 있게 끝나요', 'The day ends comfortably') : tx('아직 확인 못 했어요', 'Not checked yet');
  const tone = risky ? color.state.danger : known ? color.state.success : color.text.muted;
  return (
    <View style={[styles.risk, risky ? styles.riskBad : known ? styles.riskOk : styles.riskUnknown]}>
      <Text variant="caption" weight="bold" color={tone} style={styles.shrink}>{text}</Text>
      {known && estimated ? <Text variant="caption" color={tone}>{`· ${tx('추정값', 'estimate')}`}</Text> : null}
    </View>
  );
}

function TimelineStop({ item, startKind, index, last, freeBefore, date, photo, step, risky, pace, paceEstimated, expanded, canEdit, busy, excluding, onToggle, onLock, onArrive, onExclude, tx, locale }: {
  item: ItineraryItemDto; startKind: DayStart['kind']; index: number; last: boolean; freeBefore: number | null; date: string | null; photo: PlacePhoto | null; step?: StepState; risky: boolean;
  pace?: ItineraryPaceItemDto; paceEstimated: boolean; expanded: boolean; canEdit: boolean; busy: boolean; excluding: boolean;
  onToggle: () => void; onLock: () => void; onArrive: () => void; onExclude: () => void; tx: Tx; locale: string;
}) {
  const leg = formatTravelLabel(item, tx, index === 0 && startKind);
  const done = step === 'done';
  const label = photo?.category ? PLACE_CATEGORY_LABELS[photo.category] : undefined;
  // 🔴 값이 없는 칸은 만들지 않는다 — 「비용 미정」을 줄마다 적으면 빈 칸이 화면에서 제일 눈에 띈다(itinerary.tsx 와 같은 규칙).
  const meta = [
    item.startsAt.slice(11, 16),
    label ? tx(label[0], label[1]) : null,
    typeof item.estimatedCostKrw === 'number' ? (item.estimatedCostKrw === 0 ? tx('무료', 'Free') : txf(tx, '%s원', '%s KRW', item.estimatedCostKrw.toLocaleString(locale))) : null,
  ].filter(Boolean).join(' · ');
  const weekday = date ? formatWeekdayShort(date, locale) : null;
  const dayNumber = date ? Number(date.slice(8, 10)) : NaN;

  return (
    <View>
      {/* 앞 곳을 떠나 이 곳에 오기까지 남는 시간 — 「자유 시간 · 50분」(S15P21E201-1668). 앞 곳을 떠난 뒤의 일이라
          이동 줄보다 위에 둔다. 모르거나 30분이 안 되면 안 그린다(freeTimeMinutes). */}
      {freeBefore !== null ? (
        <View style={styles.legRow}>
          <View style={styles.legRail}><View style={[styles.railLine, styles.railFull]} /></View>
          <FreeTimeRow minutes={freeBefore} tx={tx} />
        </View>
      ) : null}
      {/* 들어오는 구간 — 카드 사이 32px 줄. 첫 곳은 「출발지에서 …」. */}
      {index > 0 || leg ? (
        <View style={styles.legRow}>
          <View style={styles.legRail}>{index > 0 ? <View style={[styles.railLine, styles.railFull]} /> : null}</View>
          {leg ? <Text variant="micro" weight="bold" color={color.text.inactiveTab}>{leg}</Text> : null}
        </View>
      ) : null}
      <View style={styles.stopRow}>
        <View style={[styles.rail, index === 0 ? styles.railFirst : styles.railCenter]}>
          {/* 🔴 세로선은 칸마다 «점에서 점까지» 조각으로 긋는다. 한 줄을 통째로 깔고 위아래를 숫자로 자르면
              카드 높이가 달라질 때(두 줄 이름·위험 표시) 첫 날짜 위나 마지막 점 아래로 선 끝이 삐져나온다. */}
          {index > 0 ? <View style={[styles.railLine, styles.railTopHalf]} /> : null}
          {!last ? <View style={[styles.railLine, index === 0 ? styles.railBelowDate : styles.railBottomHalf]} /> : null}
          {index === 0 && Number.isFinite(dayNumber) ? (
            <View style={styles.dateMark}>
              {weekday ? <Text variant="caption" weight="bold">{weekday}</Text> : null}
              {/* 🔴 동백 채움이 「코스 A로 확정」과 같이 보인다 — tokens 규칙 1(화면당 하나)의 예외다. 회색으로 바꾸지 마라.
                  시안이 이 원을 동백으로 그렸고, 짙은 회색 판과 나란히 본 뒤 사용자가 시안대로 가기로 정했다
                  (2026-09-23, S15P21E201-1535). 되돌릴 때는 action.secondary 로 내린다. */}
              <View style={styles.dateCircle}><Text weight="bold" color={color.text.onAction}>{dayNumber}</Text></View>
            </View>
          ) : done ? (
            <View style={styles.dotDone}><Text variant="micro" weight="bold" color={color.text.onAction}>✓</Text></View>
          ) : (
            <View style={styles.dot} />
          )}
        </View>
        <View style={styles.stopCard}>
          <View style={styles.stopHead}>
            <View style={styles.thumb}>
              {photo?.photoUrl ? <Image source={{ uri: photo.photoUrl }} resizeMode="cover" accessibilityLabel="" style={styles.fill} /> : <Text style={styles.glyph}>{categoryGlyph(photo?.category)}</Text>}
            </View>
            {/* 펼치는 손잡이는 글 덩이에만 둔다. 카드 전체를 누르게 하면 그 안의 자물쇠가 「버튼 안의 버튼」이 된다(웹에서 안 된다). */}
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ expanded }}
              accessibilityLabel={expanded ? txf(tx, '%s 접기', 'Collapse %s', item.title) : txf(tx, '%s 자세히', 'Details for %s', item.title)}
              onPress={onToggle}
              style={styles.stopCopy}
            >
              <View style={styles.titleLine}>
                <Text weight="bold" style={styles.shrink}>{item.title}</Text>
                {done ? <View style={styles.chipDone}><Text variant="micro" weight="bold" color={color.state.success}>{tx('✓ 다녀옴', '✓ Visited')}</Text></View> : null}
              </View>
              {meta ? <Text variant="caption" color={color.text.muted}>{meta}</Text> : null}
              {risky ? <Text variant="micro" weight="bold" color={color.state.danger}>{tx('하루 넘길 위험', 'May run past the day')}</Text> : null}
            </Pressable>
            {canEdit ? (
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={item.locked ? txf(tx, '%s 고정 해제', 'Unlock %s', item.title) : txf(tx, '%s 고정', 'Lock %s', item.title)}
                accessibilityState={{ selected: item.locked, busy, disabled: busy }}
                disabled={busy}
                onPress={onLock}
                style={({ pressed }) => [styles.lock, (pressed || busy) && styles.pressed]}
              >
                <Text>{busy ? '…' : item.locked ? '🔒' : '🔓'}</Text>
              </Pressable>
            ) : item.locked ? <View style={styles.lock}><Text>🔒</Text></View> : null}
          </View>
          {expanded ? (
            <View style={styles.stopDetail}>
              <Text variant="caption" weight="bold" color={pace?.atRisk ? color.state.danger : pace?.visited ? color.state.success : color.text.muted} style={styles.grow}>
                {pace?.visited
                  ? txf(tx, '도착 %s', 'Arrived %s', pace.predictedArrival ? formatClock(pace.predictedArrival, locale) : '--:--')
                  : txf(tx, '예상 도착 %s%s', 'Est. arrival %s%s', pace?.predictedArrival ? formatClock(pace.predictedArrival, locale) : item.startsAt.slice(11, 16), paceEstimated ? tx(' (추정)', ' (est.)') : '')}
              </Text>
              {canEdit && !pace?.visited ? (
                <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 도착 찍기', 'Mark arrival at %s', item.title)} accessibilityState={{ busy }} disabled={busy} onPress={onArrive} style={({ pressed }) => [styles.detailButton, (pressed || busy) && styles.pressed]}>
                  <Text variant="caption" weight="bold">{tx('도착 찍기', 'Mark arrival')}</Text>
                </Pressable>
              ) : null}
              {canEdit ? (
                <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 제외', 'Exclude %s', item.title)} accessibilityState={{ busy: excluding }} disabled={excluding} onPress={onExclude} style={({ pressed }) => [styles.detailButton, styles.excludeButton, (pressed || excluding) && styles.pressed]}>
                  <Text variant="caption" weight="bold" color={color.state.danger}>{excluding ? tx('처리 중', 'Processing') : tx('제외', 'Remove')}</Text>
                </Pressable>
              ) : null}
            </View>
          ) : null}
        </View>
      </View>
    </View>
  );
}

function SheetSkeleton() {
  return (
    <View style={styles.skeleton}>
      <Skeleton height={120} radius={radius.lg} />
      <Skeleton height={84} radius={radius.lg} />
      <Skeleton height={84} radius={radius.lg} />
    </View>
  );
}

function ErrorCard({ message, onRetry, tx }: { message: string; onRetry: () => void; tx: Tx }) {
  return (
    <View style={styles.errorCard}>
      <Text variant="title" weight="bold">{tx('여행을 불러오지 못했어요', 'Could not load the trip')}</Text>
      <Text color={color.text.body}>{message}</Text>
      <Pressable accessibilityRole="button" onPress={onRetry} style={({ pressed }) => [styles.actionPill, styles.retry, pressed && styles.pressed]}>
        <Text variant="caption" weight="bold">{tx('다시 시도', 'Try again')}</Text>
      </Pressable>
    </View>
  );
}

const floating = {
  shadowColor: color.brand.navy, shadowOpacity: 0.12, shadowRadius: 8, shadowOffset: { width: 0, height: 2 }, elevation: 2,
} as const;

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.surface.soft, overflow: 'hidden' },
  pressed: { opacity: 0.72 },
  shrink: { flexShrink: 1 },
  grow: { flex: 1, minWidth: 0 },
  fill: { width: '100%', height: '100%' },
  rowCenter: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },

  // ── 지도 ──
  mapClip: { position: 'absolute', left: 0, right: 0, top: 0, overflow: 'hidden' },
  mapLayers: { position: 'absolute', left: spacing[4] },
  mapBleed: { marginTop: -radius.lg, marginHorizontal: -radius.lg },
  mapEmpty: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[6] },
  mapSummary: { position: 'absolute', left: spacing[4], zIndex: 5, paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.card, ...floating },

  // ── 접힘: 정차지 카드 줄 ──
  strip: { position: 'absolute', left: 0, right: 0, zIndex: 4, flexGrow: 0 },
  stripInner: { gap: spacing[2], paddingHorizontal: spacing[4], paddingVertical: spacing[2] },
  stripCard: {
    width: STRIP_CARD, gap: spacing[1], padding: spacing[3], borderRadius: radius.md, borderWidth: 2, borderColor: color.surface.card, backgroundColor: color.surface.card,
    ...floating, shadowOffset: { width: 0, height: 4 }, shadowRadius: 12,
  },
  stripCardOn: { borderColor: color.action.outline },
  numberDot: { width: 22, height: 22, borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' },

  // ── 탭바 = 창 ──
  dock: { position: 'absolute', left: 0, right: 0, bottom: 0, alignItems: 'center', zIndex: 30 },
  bar: {
    alignSelf: 'center', overflow: 'hidden', borderRadius: radius.lg,
    // 🔴 그림자는 탭바만의 예외다(tokens 규칙 3) — 이 막대가 탭바 자리다.
    shadowColor: color.brand.navy, shadowOpacity: 0.10, shadowRadius: 14, shadowOffset: { width: 0, height: -2 }, elevation: 8,
  },
  // 창은 탭 줄보다 위에 뜬다 — 같은 받침 모양, 한 층 위.
  sheetDock: { zIndex: 31 },
  sheet: { backgroundColor: color.canvas },
  overlayHead: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], paddingHorizontal: spacing[4], paddingBottom: spacing[2] },
  handleZone: { height: HANDLE, alignItems: 'center', justifyContent: 'center' },
  handle: { width: 36, height: 4, borderRadius: radius.full, backgroundColor: color.surface.field },
  sheetScroll: { flex: 1 },
  // 시안: padding 4 16 16, gap 12
  sheetContent: { paddingTop: spacing[1], paddingHorizontal: spacing[4], paddingBottom: spacing[4], gap: spacing[3] },
  tabRow: { position: 'absolute', left: 0, right: 0, bottom: 0, height: TAB_BAR_HEIGHT, flexDirection: 'row', alignItems: 'center', paddingHorizontal: spacing[2] },
  tab: { flex: 1, minHeight: 53, alignItems: 'center', justifyContent: 'center', gap: spacing[1] },
  tabIcon: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center' },
  tabImage: { width: 20, height: 20 },
  tabImageOn: { tintColor: color.text.heading },
  tabImageOff: { tintColor: color.text.muted },
  // 🔴 이 원은 보통 탭바의 「여행 만들기」 동백 원 자리다 — 접혔을 때 이 화면에서 «그다음에 할 일» 이 여기다.
  expandCircle: { width: 32, height: 32, borderRadius: 16, backgroundColor: color.action.primary, alignItems: 'center', justifyContent: 'center' },
  chevronUp: { width: 9, height: 9, marginTop: 4, borderLeftWidth: 2.5, borderTopWidth: 2.5, borderColor: color.text.onAction, transform: [{ rotate: '45deg' }] },
  backCircle: { width: 32, height: 32, borderRadius: 16, backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' },
  chevronLeft: { width: 9, height: 9, marginLeft: 4, borderLeftWidth: 2.5, borderBottomWidth: 2.5, borderColor: color.text.heading, transform: [{ rotate: '45deg' }] },

  // ── 창 내용 ──
  head: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  headCopy: { flex: 1, minWidth: 0, gap: 2 },
  confirmedChip: { alignSelf: 'flex-start', flexDirection: 'row', alignItems: 'center', marginTop: spacing[1], paddingHorizontal: 10, paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.card },
  circle44: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  // 알약이 다섯이라(S15P21E201-1593) 한 줄이면 「지도 …」처럼 잘린다 — 한 줄에 셋까지, 넘치면 다음 줄로 감긴다.
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  actionPill: { flexGrow: 1, flexBasis: '30%', minHeight: 44, paddingHorizontal: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  dayRow: { flexDirection: 'row', gap: spacing[2] },
  dayChip: { minHeight: 36, paddingHorizontal: spacing[4], justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.card },
  dayChipOn: { backgroundColor: color.action.secondary },
  notice: { padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  nowWrap: { gap: spacing[2] },

  // 추천 코스 카드
  courseClip: { overflow: 'hidden', flexShrink: 0 },
  courseCard: { padding: spacing[3], gap: 10, borderRadius: radius.lg, backgroundColor: color.surface.card },
  courseHead: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingHorizontal: spacing[1] },
  coursePad: { paddingHorizontal: spacing[1] },
  chipEstimated: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, backgroundColor: color.state.warningBg },
  track: { flexDirection: 'row', padding: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
  // 🔴 고른 칸은 먹색이다(tokens 규칙 2 — 선택에 빨강을 쓰지 않는다).
  courseIndicator: { position: 'absolute', top: spacing[1], bottom: spacing[1], left: spacing[1], borderRadius: radius.full, backgroundColor: color.brand.navy },
  courseSlot: { flex: 1, minWidth: 0, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full },
  confirmClip: { overflow: 'hidden' },
  confirmRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingTop: 2 },
  confirmHint: { flex: 1, minWidth: 0, paddingHorizontal: spacing[1] },
  // 🔴 이 화면의 동백 채움은 이 단추다(tokens 규칙 1). 접힌 탭 줄의 「일정 펼치기」 원과는 같이 안 보인다.
  confirmButton: { minHeight: 44, paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.action.primary, justifyContent: 'center' },

  // 위험 띠
  risk: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 6, paddingVertical: spacing[3], paddingHorizontal: spacing[4], borderRadius: radius.md },
  riskBad: { backgroundColor: color.state.dangerBg },
  riskOk: { backgroundColor: color.state.successBg },
  riskUnknown: { backgroundColor: color.surface.tint },

  // 카드 타임라인 — 그리드 48 | 1fr, 왼쪽 세로선 2px(가운데)
  legRow: { height: 32, flexDirection: 'row', alignItems: 'center', gap: spacing[2] + spacing[1] },
  legRail: { width: RAIL, alignSelf: 'stretch' },
  railLine: { position: 'absolute', left: RAIL / 2 - 1, width: 2, backgroundColor: color.surface.field },
  railFull: { top: 0, bottom: 0 },
  railTopHalf: { top: 0, height: '50%' },
  railBottomHalf: { top: '50%', bottom: 0 },
  // 첫 칸은 점이 아니라 날짜 표시가 위에 붙는다 — 선은 그 뒤(바탕색 칸)에서 시작해 아래로 간다.
  railBelowDate: { top: spacing[3], bottom: 0 },
  stopRow: { flexDirection: 'row', gap: spacing[2] },
  rail: { width: RAIL, alignItems: 'center' },
  railFirst: { justifyContent: 'flex-start', paddingTop: 6 },
  railCenter: { justifyContent: 'center' },
  dateMark: { alignItems: 'center', gap: spacing[1], paddingTop: 2, paddingBottom: spacing[1], backgroundColor: color.canvas },
  // 🔴 사용자 결정으로 시안대로 동백이다 — 위 JSX 주석을 읽어라.
  dateCircle: { width: 32, height: 32, borderRadius: radius.full, backgroundColor: color.action.primary, alignItems: 'center', justifyContent: 'center' },
  // 점 — 바탕색 4px 고리로 세로선을 끊는다(시안 box-shadow 0 0 0 4px #F5F5F7).
  dot: { width: 18, height: 18, borderRadius: radius.full, borderWidth: 4, borderColor: color.canvas, backgroundColor: color.surface.field },
  dotDone: { width: 26, height: 26, borderRadius: radius.full, borderWidth: 4, borderColor: color.canvas, backgroundColor: color.state.success, alignItems: 'center', justifyContent: 'center' },
  stopCard: { flex: 1, minWidth: 0, padding: 10, gap: 10, borderRadius: radius.lg, backgroundColor: color.surface.card },
  stopHead: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  thumb: { width: THUMB, height: THUMB, borderRadius: radius.md, overflow: 'hidden', backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' },
  glyph: { fontSize: 28, lineHeight: 34 },
  stopCopy: { flex: 1, minWidth: 0, gap: spacing[1] },
  titleLine: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 6 },
  chipDone: { paddingHorizontal: 6, paddingVertical: 1, borderRadius: radius.full, backgroundColor: color.state.successBg },
  lock: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center' },
  stopDetail: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingTop: 10, borderTopWidth: 1, borderTopColor: color.surface.border },
  detailButton: { minHeight: 40, paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint, justifyContent: 'center' },
  excludeButton: { backgroundColor: color.state.dangerBg },
  emptyCard: { padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },

  budget: { gap: 10, marginTop: spacing[2] },
  reorder: { minHeight: 48, borderRadius: radius.md, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },

  skeleton: { gap: spacing[3] },
  errorCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  retry: { flex: 0, alignSelf: 'flex-start', paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.surface.border },
});
