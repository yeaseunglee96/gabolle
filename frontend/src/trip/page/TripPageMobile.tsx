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
import { AccessibilityInfo, Animated, BackHandler, Easing, Image, Platform, Pressable, ScrollView, StyleSheet, View, type ImageSourcePropType } from 'react-native';
import { useRouter } from 'expo-router';
import Reanimated, { Easing as REasing, Extrapolation, interpolate, interpolateColor, ReduceMotion, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { ExcludeConfirmModal } from '@/components/ExcludeConfirmModal';
import { Skeleton } from '@/components/Skeleton';
import { settleSheetHeight, useSheetDrag } from '@/components/sheetDrag';
import { MAX_CONTENT_WIDTH } from '@/components/Screen';
import { StopName } from '@/components/StopName';
import { BAR_MAX_WIDTH, TAB_BAR_HEIGHT, tabBarBottomMargin } from '@/components/TabBar';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { LockToggle } from '@/components/LockToggle';
import { Text } from '@/components/Text';
import { TripActionIcon, type TripActionKind } from '@/components/TripActionIcon';
import { color, radius, spacing } from '@/design/tokens';
import { PLACE_CATEGORY_LABELS } from '@/discovery/placeCategoryLabels';
import { stopNameForLanguage } from '@/discovery/romanize';
import { useI18n } from '@/i18n';
import { formatClock, formatDayHeading, formatWeekdayShort } from '@/i18n/datetime';
import { txf } from '@/i18n/format';
import { koreanSubject, koreanToward } from '@/i18n/korean';
import { localizeMessage } from '@/i18n/messages';
import { useLayout } from '@/layout/useLayout';
import { RouteMap } from '@/map/RouteMap';
import { courseLetter } from '@/plan/CourseCard';
import { NowCard } from '@/plan/NowCard';
import { useLocationGate } from '@/personalization/useLocationGate';
import type { ExcludeReason } from '@/components/ExcludeConfirmModal';
import { ImpressionView, useImpressionTracker } from '@/analytics/impressions';
import {
  pollItineraryJob, recordItineraryItemActual, removeItineraryItem, setItineraryItemLocked,
  type DayStart, type ItineraryItemDto, type ItineraryPaceItemDto, stopClock,
} from '@/plan/itinerary';
import { formatTravelLabel, totalTravelMinutes } from '@/plan/itinerarySummary';
import { LegRow } from '@/trip/page/LegRow';
import { GuideCallout } from '@/onboarding/GuideCallout';
import { markScreenGuideUsed, takeScreenGuide } from '@/onboarding/firstRun';
import { legRouteParams, type LegRouteParams } from '@/trip/page/legRoute';
import type { LanguageCode } from '@/i18n/languages';
import { categoryGlyph, type PlacePhoto } from '@/plan/placePhotos';
import { pickReasonLine } from '@/plan/recommendations';
import { canConfirmCourse, type TripCourse } from '@/plan/tripCourses';
import { drift, isToday, localDateKey, saysStartsIn, stepStates, type StepState } from '@/plan/tripProgress';
import { isSkippedToday } from '@/trip/page/emptyDay';
import { CONTENT_IN, morphSize, TABS_OUT } from '@/trip/page/sheetMorph';
import { humanTripTitle } from '@/trip/tripNaming';
import { TripNameSheet } from '@/trip/TripNameSheet';

import { TripBudgetCard } from './TripBudgetCard';
import type { TripPageSource } from './tripPageData';
import { formatManwon, freeTimeMinutes } from './tripPageModel';
import { useTripPage } from './useTripPage';
import { useTripProgress } from './useTripProgress';
import { remainingMeters, stepAway, stepDwell, usableFix, type Away, type Dwell } from './autoArrival';
import { ActualTimeSheet } from './ActualTimeSheet';
import { arrivedAtOf, clockOf, isoWithOffset, startOfLocalDay, stayingStopId } from './actualTime';
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
import { StoryComposeForm } from '@/social/StoryComposeForm';
import { otherNameFor } from '@/discovery/localNames';

type Tx = (ko: string, en: string) => string;
type Panel = 'trip' | 'collapsed';
/** 창 바탕 — 웹은 흐림과 함께 쓰는 유리색, 폰은 흐림이 없어 불투명(S15P21E201-1796). */
const SHEET_BG = Platform.OS === 'web' ? color.surface.sheetGlass : color.surface.card;

/**
 * 창이 열렸을 때 위에 남기는 지도 높이(안전영역 아래부터). 시안 390×844 에서 창 위끝이 236, 상태 줄이 47 이다.
 * 🔴 기기 높이에 비례시키지 않는다 — 지도에서 보고 싶은 것은 «고른 한 곳» 이고 그 크기는 기기와 무관하다.
 *    다만 가로로 돌린 폰처럼 화면이 낮으면 창이 너무 작아지므로 높이의 30% 를 넘기지 않는다.
 */
const MAP_PEEK = 190;
/** 접었을 때 탭바 위에 뜨는 정차지 카드 폭(시안 150). */
const STRIP_CARD = 150;
// 🔴 카드 줄이 멈추는 한 칸 = 카드 폭 + 카드 사이 간격(stripInner 의 gap) (S15P21E201-1800).
//    좌우 안쪽 여백(paddingHorizontal)은 0 번 자리에도 똑같이 있으므로 배수 자리가 곧 카드 시작점이다.
const STRIP_STEP = STRIP_CARD + spacing[2];
/** 타임라인 왼쪽 기둥 폭(시안 그리드 48px | 1fr). */
const RAIL = 48;
const THUMB = 64;
/** 첫 칸 기둥의 위 여백 — 날짜 표시가 이만큼 내려와 선다. */
const RAIL_FIRST_TOP = 6;
/**
 * 창의 손잡이 줄 높이. 시안은 24 인데 36 으로 키웠다(S15P21E201-1787) — 잡고 끌어 내리는 자리라 24 로는 손가락이 자주 빗나갔다.
 * 보이는 막대(36×4)는 그대로다.
 */
const HANDLE = 36;
/** 손잡이를 끌어 창을 낮출 수 있는 가장 낮은 높이. 이보다 낮게 내리면 접는다 — 손잡이·제목 한 줄은 남아야 창으로 읽힌다. */
const MIN_OPEN = TAB_BAR_HEIGHT * 3;
/** 시안의 곡선 — 미끄러짐·접힘은 부드러운 곡선. */
const SLIDE = Easing.bezier(0.2, 0.8, 0.2, 1);
/**
 * 탭바 ↔ 창 움직임 (S15P21E201-1756, 사용자 요청). 🔴 펴고 접을 때 «같은 것»을 쓴다 — 튀는 곡선(overshoot)은 쓰지 않는다.
 * -1607 은 JS 쪽에서 매 프레임 높이를 바꿔 폰에서 버벅이자 «다 자란 창을 아래에서 밀어 올리기»로 바꿨는데, 그러면
 * 떠 있는 막대가 커지는 게 아니라 화면이 밑에서 올라와 막대를 띄워 둔 뜻이 사라진다(사용자). 이제는 막대의 폭·높이를
 * Reanimated 가 폰 쪽(UI 스레드)에서 키운다 — JS 가 바빠도 끊기지 않는다.
 */
const GROW = { duration: 380, easing: REasing.bezier(0.2, 0.8, 0.2, 1) } as const;
/** 움직임 줄이기를 켠 사람에게는 늘어나는 대신 이 시간 동안 서서히 나타난다. */
const FADE_MS = 220;
/** 창 안에서 일정 ↔ 동행 초대·공유·날씨가 바뀔 때 (S15P21E201-1627). 들어오는 쪽이 이만큼 옆에서 미끄러져 온다. */
const SWAP_SHIFT = 24;
const SWAP_MOTION = { duration: 240, easing: SLIDE, useNativeDriver: true } as const;

const TAB_ICONS: Record<'home' | 'feed' | 'map', ImageSourcePropType> = {
  home: require('../../../assets/icons/home/home.png'),
  feed: require('../../../assets/icons/home/heart.png'),
  map: require('../../../assets/icons/home/map.png'),
};

export function TripPageMobile({ source, askName = false }: { source: TripPageSource; askName?: boolean }) {
  const router = useRouter();
  const { accessToken } = useAuth();
  const { tx, locale, language } = useI18n();
  const insets = useSafeAreaInsets();
  const { width, height } = useLayout();

  const {
    page, load, courses, course, courseIndex, setCourseIndex, confirmed, setConfirmed, tripId,
    itinerary, setItinerary, loaded, reloadItinerary, dayIndex, setDayIndex, items, selectedId, setSelectedId,
    photos, pace, reloadPace, map, routes, points, budget, atRisk, allEstimated, title, headSub, confirm, confirming,
  } = useTripPage(source);
  // 영어(일·중) 화면의 장소 이름 — 「돈반 (Donban)」, 영어 이름이 있으면 영어 먼저(S15P21E201-1735). 한국어는 제목 그대로.
  // 일본어·중국어는 관광공사 번역 이름이 먼저(S15P21E201-1860) — 사진 조회에 함께 실려 온다.
  const nameOf = (item: ItineraryItemDto) => stopNameForLanguage(item.title, otherNameFor(photos[item.placeId]?.nameEn, photos[item.placeId]?.localNames, language), language);

  const [panel, setPanel] = useState<Panel>('trip');
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

  // 코스·일차가 바뀌면 펼친 카드와 알림을 닫는다. 열린 채로 내용만 갈리면 무엇이 펼쳐졌는지 모른다.
  useEffect(() => { setExpandedId(null); setNotice(null); }, [course?.id, dayIndex]);

  // ── 진행 (「지금」 카드) — 확정한 일정에서만 ────────────────────────────────
  const progressId = confirmed ? loaded?.id ?? null : null;
  const run = useTripProgress(progressId);
  // 🔴 위치는 동의가 있을 때만 읽는다(S15P21E201-1691). 「출발」을 처음 누를 때 한 번 묻는다 — 거절해도 출발은 되고, 도착은 손으로 찍는다.
  const locationGate = useLocationGate(accessToken);
  // 추천 노출 — 카드가 실제로 화면에 보일 때만 보낸다(S15P21E201-1696). 확정 전에는 코스를 고르는 중이다.
  const impressions = useImpressionTracker({ accessToken, sourceScreen: confirmed ? 'TRIP_ITINERARY' : 'TRIP_COURSES', active: page?.state === 'ready' });
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

  // ── 머무는 곳 · 출발 (S15P21E201-1690) ─────────────────────────────────────
  // 🔴 서버는 도착과 출발이 «둘 다» 있어야 「다녀옴」(visited)이다. 도착만 적힌 곳이 지금 머무는 곳이다.
  //    방금 이 화면에서 출발을 적은 곳은 속도(pace)를 다시 받기 전까지 여기(departedHere)로 센다.
  const [departedHere, setDepartedHere] = useState<ReadonlySet<string>>(() => new Set());
  const [lastDeparture, setLastDeparture] = useState<{ id: string; arrivedAt: string; at: string } | null>(null);
  const [timeEdit, setTimeEdit] = useState<'arrival' | 'departure' | null>(null);
  const [savingTime, setSavingTime] = useState(false);
  // 방금 고친 도착 — 진행 기록을 다시 받기 전에 「출발 찍기」를 누르면 고치기 전 도착이 실렸다(로컬 캡처에서 잡음).
  const [arrivalFix, setArrivalFix] = useState<{ id: string; at: string } | null>(null);
  useEffect(() => { setDepartedHere(new Set()); setLastDeparture(null); setArrivalFix(null); }, [progressId]);
  const departedIds = useMemo(
    () => new Set([...(pace?.items ?? []).filter((entry) => entry.visited).map((entry) => entry.itemId), ...departedHere]),
    [pace, departedHere],
  );
  // 🔴 손으로 찍는 것은 오늘 방문지만(조율 세션 결정). 서버는 날짜를 일부러 검사하지 않는다 — 앱이 지킨다.
  const todayDay = isToday(day?.date, localDateKey(new Date()));

  // ── 모양 ──────────────────────────────────────────────────────────────────
  const bottomMargin = tabBarBottomMargin(insets.bottom);
  const sheetTop = insets.top + Math.min(MAP_PEEK, Math.round(height * 0.3));
  const sheetHeight = Math.max(TAB_BAR_HEIGHT * 4, height - bottomMargin - sheetTop);
  // 🔴 지도는 줄이지 않는다(S15P21E201-1607). 전에는 창이 열리면 지도 칸을 창 위만큼으로 줄여서, 여닫을 때마다
  //    지도가 다시 가운데를 잡으며 튀었다. 지금은 늘 화면 전체이고 창이 그 위에 겹친다. 창에 가린 높이(mapCovered)만
  //    지도에 알려서, 고른 곳을 보이는 부분의 가운데로 옮기게 한다(RouteMap 의 bottomInset).
  const mapHeight = height;
  const [stripHeight, setStripHeight] = useState(0);
  // 손잡이를 끌어 정한 창 높이(S15P21E201-1787). null 이면 다 편 높이다. 지도에 알리는 값이라 손을 뗀 뒤에만 바뀐다.
  const [resizedHeight, setResizedHeight] = useState<number | null>(null);
  const mapCovered = panel === 'trip'
    ? (resizedHeight !== null ? resizedHeight + bottomMargin : height - sheetTop)
    : bottomMargin + TAB_BAR_HEIGHT + spacing[2] + stripHeight;
  // 지도 위쪽도 가려져 있다 — 상태바, 그 아래 「장소 N곳」 요약(높이 40 자리)과 「경사/그늘」 칩(32). 지도 칸은 모서리를
  // 숨기려 radius.lg 만큼 화면 위로 올라가 있어 그것도 더한다(S15P21E201-1754). 칩 아래 풀이 줄은 켰을 때만 떠서 셈하지 않는다.
  const mapTopCovered = radius.lg + insets.top + spacing[2] + 40 + 32;
  // 🔴 «지도 보기»로 접는 순간에만 지도를 다시 맞춘다(S15P21E201-1754). 창이 열린 채 맞춘 큰 아래 여백이 남아 경로가
  //    화면 위 15% 에 몰렸다. 창을 열 때는 null 이라 안 맞춘다 — 여닫을 때마다 튀지 않게(S15P21E201-1607).
  //    카드 줄 높이가 재어지면 한 번 더 맞춘다(접은 직후 한 번뿐이다).
  //    창을 끌어 낮췄을 때도 맞춘다 — 창을 낮춘 것은 지도를 더 보려는 것이라, 드러난 곳에 경로를 다시 놓아야 한다(S15P21E201-1787).
  const mapRefitKey = panel === 'collapsed' ? `collapsed:${stripHeight}` : resizedHeight !== null ? `open:${resizedHeight}` : null;

  // 🔴 창 안의 내용이 바뀔 때 한 프레임에 툭 바뀌지 않게 한다 (S15P21E201-1627). 창은 부드럽게 오르는데 안만 툭
  //    바뀌면 다른 화면으로 튄 것처럼 읽혔다. 들어가는 판은 오른쪽에서, 일정으로 돌아올 때는 왼쪽에서 온다.
  const swap = useRef(new Animated.Value(1)).current;
  const swapFrom = useRef(new Animated.Value(SWAP_SHIFT)).current;
  const [reduceMotion, setReduceMotion] = useState(false);
  useEffect(() => {
    let alive = true;
    void AccessibilityInfo.isReduceMotionEnabled().then((on) => { if (alive) setReduceMotion(on); });
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    return () => { alive = false; sub.remove(); };
  }, []);
  // ── 탭바 = 창 (S15P21E201-1756) — 떠 있는 막대가 제자리에서 늘어나 창이 되고, 접으면 거꾸로 줄어 막대가 된다 ──
  //    바닥 여백과 모서리(20)는 막대와 창이 같이 쓴다 — 폭·높이만 자라므로 밑에서 밀려 올라오지 않는다.
  const barWidth = Math.min(Math.max(0, width - spacing[4] * 2), BAR_MAX_WIDTH);
  // 🔴 창 폭은 361(탭바 시트 폭)에 묶지 않는다 (S15P21E201-1627) — 폴드 펼침 세로(673)에서 가운데 361 로 떠 일정이 좁게
  //    줄바꿈됐다. 화면 폭을 따라가되 읽기 좋은 폭(720)에서 멈춘다.
  const sheetWidth = Math.min(Math.max(0, width - spacing[4] * 2), MAX_CONTENT_WIDTH);
  /** 0 = 막대, 1 = 창. 처음은 창이 펴진 채다(panel 의 처음 값). */
  const grow = useSharedValue(panel === 'trip' ? 1 : 0);
  /** 움직임 줄이기일 때만 쓴다 — 늘어나는 대신 서서히 나타난다. */
  const fade = useSharedValue(1);
  /** 편 창의 높이 — 손잡이를 끄는 동안 손가락을 따라간다(S15P21E201-1787). 처음과 접은 뒤에는 다 편 높이다. */
  const openHeight = useSharedValue(sheetHeight);
  useEffect(() => { openHeight.value = sheetHeight; setResizedHeight(null); }, [sheetHeight, openHeight]);
  // 🔴 누르면 움직임부터 시작하고, 창 상태(panel)는 움직임이 «끝난 뒤» 바꾼다(S15P21E201-1763, 사용자: 「갤럭시 크롬에서는 좀 버벅인다」).
  //    panel 이 바뀌면 여행 화면 전체(일정 목록까지)를 다시 그린다. 전에는 상태를 먼저 바꾸고 그 뒤에 움직였는데, CPU 6배 느림
  //    실측으로 누른 뒤 움직이기까지 240~350ms 였고 거의 전부 그 다시 그리기였다 — 웹에서는 Reanimated 도 같은 주 스레드에서 돌아
  //    그 일이 끝나야 움직였다(데스크톱 1배는 약 35ms 라 부드러워 보였다). 이제 움직이는 동안에는 창 크기만 바뀐다. 모양은 그대로다.
  //    움직이다 반대로 누르면 앞의 움직임은 끝나지 않은(finished=false) 채 멈추고, 나중 것이 끝날 때 그 상태가 들어간다.
  const changePanel = (next: Panel) => {
    const target = next === 'trip' ? 1 : 0;
    // 🔴 Reanimated 는 시스템 「움직임 줄이기」가 켜져 있으면 모든 움직임을 곧바로 끝낸다(기본값 ReduceMotion.System) —
    //    그러면 대신 쓰려던 서서히 나타나기도 없어진다(웹 실측). 나타나기는 줄인 움직임 그 자체라 이것만 Never 로 돌린다.
    if (reduceMotion) { grow.value = target; fade.value = 0; fade.value = withTiming(1, { duration: FADE_MS, reduceMotion: ReduceMotion.Never }); setPanel(next); return; }
    grow.value = withTiming(target, GROW, (finished) => {
      'worklet';
      if (finished) scheduleOnRN(setPanel, next);
    });
  };
  const shellStyle = useAnimatedStyle(() => ({
    ...morphSize(grow.value, { width: barWidth, height: TAB_BAR_HEIGHT }, { width: sheetWidth, height: openHeight.value }),
    // 막대는 흰색, 창은 뒤의 지도가 비치는 유리색(S15P21E201-1627) — 자라면서 바뀐다.
    // 🔴 유리색은 웹만이다(S15P21E201-1796). 폰에는 흐림(backdropFilter)이 없어 지도가 글자 뒤로 그대로 비쳤다 — 폰은 불투명 흰색.
    backgroundColor: interpolateColor(grow.value, [0, 1], [color.surface.card, SHEET_BG]),
    opacity: fade.value,
  }), [barWidth, sheetWidth]);
  const tabsStyle = useAnimatedStyle(() => ({ opacity: interpolate(grow.value, [0, TABS_OUT], [1, 0], Extrapolation.CLAMP) }));
  // 창 속 높이도 편 창 높이를 따라간다 — 속은 바닥에 붙어 있어서, 창만 낮추면 손잡이와 제목이 위로 잘려 나간다.
  const sheetInnerStyle = useAnimatedStyle(() => ({ height: openHeight.value, opacity: interpolate(grow.value, [CONTENT_IN[0], CONTENT_IN[1]], [0, 1], Extrapolation.CLAMP) }));
  // 정차지 카드 줄은 접힌 막대 위에만 있다 — 펴기 시작하면 곧바로 숨긴다. 창 상태는 움직임이 끝나야 바뀌므로(changePanel)
  // 그것만 보면 커지는 창 양옆으로 끝까지 비쳤다(S15P21E201-1763 사진). 접을 때는 다 접힌 뒤에 나타난다.
  const stripStyle = useAnimatedStyle(() => ({ opacity: grow.value > 0 ? 0 : 1 }));
  const lastOverlay = useRef<TripOverlayKind | null>(null);
  useEffect(() => {
    const was = lastOverlay.current;
    lastOverlay.current = overlay;
    if (was === overlay || reduceMotion) return;
    swapFrom.setValue(overlay ? SWAP_SHIFT : -SWAP_SHIFT);
    swap.setValue(0);
    Animated.timing(swap, { toValue: 1, ...SWAP_MOTION }).start();
  }, [overlay, reduceMotion, swap, swapFrom]);
  const swapStyle = {
    flex: 1,
    opacity: swap,
    transform: [{ translateX: Animated.multiply(swapFrom, Animated.subtract(1, swap)) }],
  };
  // 창을 접으면 창 안에 열어 둔 판(동행 초대 등)도 닫는다 — 다시 펴면 일정이 보여야 한다.
  useEffect(() => { if (panel === 'collapsed') setOverlay(null); }, [panel]);
  // 접으면 끌어 정한 높이도 잊는다 — 「일정 펼치기」는 늘 다 편 창을 연다.
  useEffect(() => { if (panel === 'collapsed') { openHeight.value = sheetHeight; setResizedHeight(null); } }, [panel, sheetHeight, openHeight]);
  // 손잡이 끌기(S15P21E201-1787) — 조금 내리면 그 높이에 멈추고(지도가 더 보인다), 많이 내리거나 빠르게 쓸면 접는다. 누르면 지금처럼 접힌다.
  const dragFrom = useRef(sheetHeight);
  const handleDrag = useSheetDrag({
    onStart: () => { dragFrom.current = openHeight.value; },
    onMove: (dy) => { openHeight.value = Math.min(sheetHeight, Math.max(TAB_BAR_HEIGHT, dragFrom.current - dy)); },
    onEnd: (dy, vy) => {
      const settled = settleSheetHeight(dragFrom.current, dy, vy, { min: MIN_OPEN, max: sheetHeight });
      if (settled.collapse) { changePanel('collapsed'); return; }
      openHeight.value = withTiming(settled.height, { duration: 160, easing: REasing.out(REasing.quad) });
      setResizedHeight(settled.height >= sheetHeight ? null : settled.height);
    },
  });
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
    // 진행 기록도 다시 받는다 — 머무는 곳(도착은 적혔고 출발은 아직)을 이 도착으로 안다.
    if (next.state === 'success') { setItinerary({ id: next.itinerary.id, value: next.itinerary, message: null }); reloadPace(); run.refresh(); }
    else setNotice(next.message);
  };

  // 출발 — 🔴 이미 적힌 도착을 함께 싣는다. 보낸 것이 그 방문지의 최종 상태라서 출발만 보내면 도착이 지워진다(07 계약).
  //    예전 일정 화면은 여기에 서버의 «예측» 도착을 실었다 — 실제 도착이 예측값으로 덮였다.
  const departingRef = useRef(false);
  const recordDeparture = async (item: ItineraryItemDto, arrivedAt: string, how: 'auto' | 'manual') => {
    if (!loaded || departingRef.current) return;
    departingRef.current = true;
    if (how === 'manual') { setBusyId(item.id); setNotice(null); }
    // 출발은 도착보다 이를 수 없다(서버 400) — 기기 시계가 어긋나 있어도 도착 시각 아래로는 안 보낸다.
    const at = isoWithOffset(Math.max(Date.now(), Date.parse(arrivedAt) || 0));
    const next = await recordItineraryItemActual({ itineraryId: loaded.id, itemId: item.id, arrivedAt, departedAt: at, accessToken });
    departingRef.current = false;
    if (how === 'manual') setBusyId(null);
    if (next.state === 'success') {
      setItinerary({ id: next.itinerary.id, value: next.itinerary, message: null });
      setDepartedHere((current) => new Set(current).add(item.id));
      setLastDeparture({ id: item.id, arrivedAt, at });
      reloadPace();
    } else if (how === 'manual') setNotice(next.message);
  };

  // 적은 시각 고치기 — 도착(머무는 곳) 또는 방금 적은 출발.
  const saveTime = async (ms: number) => {
    if (!loaded || !timeEdit) return;
    const target = timeEdit === 'arrival'
      ? (stayItem ? { id: stayItem.id, arrivedAt: isoWithOffset(ms), departedAt: null } : null)
      : (lastDeparture ? { id: lastDeparture.id, arrivedAt: lastDeparture.arrivedAt, departedAt: isoWithOffset(ms) } : null);
    if (!target) { setTimeEdit(null); return; }
    setSavingTime(true); setNotice(null);
    const next = await recordItineraryItemActual({ itineraryId: loaded.id, itemId: target.id, arrivedAt: target.arrivedAt, departedAt: target.departedAt, accessToken });
    setSavingTime(false);
    setTimeEdit(null);
    if (next.state !== 'success') { setNotice(next.message); return; }
    setItinerary({ id: next.itinerary.id, value: next.itinerary, message: null });
    if (target.departedAt && lastDeparture) setLastDeparture({ ...lastDeparture, at: target.departedAt });
    else setArrivalFix({ id: target.id, at: target.arrivedAt });
    run.refresh();
    reloadPace();
  };

  const exclude = async (item: ItineraryItemDto, reason: ExcludeReason | null) => {
    if (!loaded) return;
    setExcludingId(item.id); setNotice(null);
    // 고른 이유(S15P21E201-1695). 건너뛰면 칸을 비운다.
    const accepted = await removeItineraryItem({ itineraryId: loaded.id, itemId: item.id, baseVersion: loaded.version, operationalReason: reason ?? undefined, accessToken });
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
  // 🔴 「순서 수정」은 순서 수정 상태로 연다(S15P21E201-1867). 전에는 편집 화면만 열려서 거기서 「순서 수정」을 한 번 더 눌러야 했다.
  const openReorder = () => {
    if (course?.itineraryId) router.push(`/trips/${course.itineraryId}/itinerary?classic=1&reorder=1`);
  };
  const goBack = () => (router.canGoBack() ? router.back() : router.replace('/trips'));

  // ── 「지금」 카드의 글 — itinerary.tsx 와 같은 규칙 ──────────────────────────────
  const progress = run.progress;
  const currentStop = items[Math.min(progress.currentStopIndex, Math.max(0, items.length - 1))] ?? null;
  const nowDriftValue = drift(new Date().toISOString(), currentStop?.startsAt ?? null);
  // 지금 머무는 곳 — 「출발」 모드이거나 오늘 일정을 다 돈 뒤(마지막 곳의 출발을 적을 수 있게).
  const stayId = progress.status === 'RUNNING' || progress.status === 'DONE' ? stayingStopId(stopIds, progress.outcomes, departedIds) : null;
  // 지금 향하는 곳 하나 — 머무는 중이 아니면 「지금」 칸, 머무는 중이거나 아직 출발 전이면 그다음 칸(UI 캔버스 ⑧).
  // 이동 칸 첫 안내(UI 캔버스 ㉓-4, S15P21E201-1890) — 누를 수 있는 첫 이동 칸 위에 한 번. 좌표를 몰라 누를 수 없는 칸만
  // 있는 날에는 가리킬 것이 없으니 안 띄운다(표시도 안 쓴다 — 다음 날 누를 수 있는 칸에서 뜬다).
  const firstLegIndex = items.findIndex((_, index) => legRouteParams(items, index, day?.start, nameOf, tx) !== null);
  const [legGuide, setLegGuide] = useState(false);
  const legGuideAsked = useRef(false);
  useEffect(() => {
    if (firstLegIndex < 0 || legGuideAsked.current) return;
    legGuideAsked.current = true;
    let alive = true;
    void takeScreenGuide('legs').then((show) => { if (alive && show) setLegGuide(true); });
    return () => { alive = false; };
  }, [firstLegIndex]);
  const aimIndex = (() => {
    const heading = steps.findIndex((step, index) => step === 'current' && items[index]?.id !== stayId);
    return heading >= 0 ? heading : steps.findIndex((step) => step === 'next');
  })();
  const stayItem = stayId ? items.find((item) => item.id === stayId) ?? null : null;
  const stayArrivedAt = stayId ? (arrivalFix?.id === stayId ? arrivalFix.at : arrivedAtOf(progress.outcomes, stayId)) : null;
  const lastDepartureItem = lastDeparture ? items.find((item) => item.id === lastDeparture.id) ?? null : null;

  // ── 내 위치 · 자동 도착 (S15P21E201-1568) ────────────────────────────────────
  // 출발(RUNNING) 동안만 위치를 따라간다. 지금 향하는 곳 50m 안에 2분 머물면 도착으로 적는다(autoArrival.ts).
  const live = useLiveLocation(progress.status === 'RUNNING' && locationGate.consent === true);
  // 위치를 안 쓰는 중인가 — 거절했거나, 묻기 전인데 이미 출발한 일정(다른 기기에서 출발 등)이다. 카드가 그렇다고 말한다.
  const locationOff = locationGate.consent === false || (progress.status === 'RUNNING' && locationGate.consent !== true);
  const target = currentStop && typeof currentStop.lat === 'number' && typeof currentStop.lng === 'number'
    ? { id: currentStop.id, latitude: currentStop.lat, longitude: currentStop.lng } : null;
  const dwellRef = useRef<Dwell>(null);
  // 자동 출발(S15P21E201-1690) — 머무는 곳 반경 밖에 도착과 같은 시간(2분) 있으면. 알아챈 시각으로 적는다(조율 세션 결정 —
  // 자동 도착도 알아챈 시각이라, 같은 방식이면 머문 시간이 맞다).
  const awayRef = useRef<Away>(null);
  const stayTarget = stayItem && typeof stayItem.lat === 'number' && typeof stayItem.lng === 'number'
    ? { id: stayItem.id, latitude: stayItem.lat, longitude: stayItem.lng } : null;
  const stayTargetRef = useRef(stayTarget);
  stayTargetRef.current = stayTarget;
  const departRef = useRef<() => void>(() => undefined);
  departRef.current = () => { if (stayItem && stayArrivedAt) void recordDeparture(stayItem, stayArrivedAt, 'auto'); };
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
      const stamped = fix && atNow ? { ...fix, at: Date.now() } : fix;
      const step = stepDwell(dwellRef.current, stamped, targetRef.current);
      dwellRef.current = step.dwell;
      if (step.arrive && targetRef.current) {
        dwellRef.current = null;
        arriveRef.current(stopIds, targetRef.current.id, 'auto');
      }
      const away = stepAway(awayRef.current, stamped, stayTargetRef.current);
      awayRef.current = away.away;
      if (away.depart) { awayRef.current = null; departRef.current(); }
    };
    check(false);
    const timer = setInterval(() => check(true), 15_000);
    return () => clearInterval(timer);
  }, [progress.status, live.fix, stopIds]);
  // 위치를 믿을 수 있나 — 카드의 안내 문구가 쓴다. 「도착 찍기」는 이제 「출발」 모드에서 늘 보인다(S15P21E201-1690).
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
  const nowTitle = stayItem
    ? txf(tx, '%s에 머무는 중', 'At %s', nameOf(stayItem))
    : progress.status === 'DONE'
    ? tx('오늘 일정을 다 돌았어요', 'You finished today')
    : progress.status === 'RUNNING' && currentStop
      ? txf(tx, `%s${koreanToward(currentStop.title)} 이동 중`, 'Heading to %s', nameOf(currentStop))
      : currentStop ? txf(tx, '다음은 %s', 'Next: %s', nameOf(currentStop)) : tx('오늘 갈 곳이 없어요', 'Nothing planned today');
  const doneCount = stopIds.filter((id) => progress.outcomes[id]).length;
  // 🔴 남은 거리 — 위치를 믿을 수 있을 때만(S15P21E201-1568). 모르면 안 적는다.
  const leftM = progress.status === 'RUNNING' ? remainingMeters(live.fix, target) : null;
  const leftText = leftM == null ? null
    : leftM >= 1000 ? txf(tx, '남은 거리 %skm', '%s km to go', (leftM / 1000).toFixed(1)) : txf(tx, '남은 거리 %sm', '%s m to go', leftM);
  // 🔴 출발 전에는 「첫 곳까지 얼마」를 적는다(시안 4a 「출발지에서 50분」). 없는 안내를 지어내지 않는다 —
  //    구간 시간을 모르면 아무것도 안 적는다.
  const nowDetail = progress.status === 'PLANNED'
    ? (items[0] ? formatTravelLabel(items[0], tx, startKind) : null)
    : txf(tx, '%s곳 중 %s곳 다녀옴', '%s stops · %s done', items.length, doneCount)
      + (progress.status !== 'DONE' && stopClock(currentStop?.startsAt) ? ` · ${txf(tx, '다음 %s', 'next %s', stopClock(currentStop?.startsAt) ?? '')}` : '')
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
              onPress={() => setConfirmed(false)}
              style={({ pressed }) => [styles.confirmedChip, pressed && styles.pressed]}
            >
              <Text variant="micro" weight="bold" color={color.state.success}>{txf(tx, '✓ 코스 %s 확정', '✓ Course %s confirmed', courseLetter(courseIndex))}</Text>
              {/* 🔴 안이 하나뿐이면 「바꾸기」를 적지 않는다 — 바꿀 곳이 없는데 누르라고 하면 고장으로 읽힌다. */}
              {courses.length > 1 ? <Text variant="micro" weight="bold" color={color.text.muted}>{` · ${tx('바꾸기', 'Change')}`}</Text> : null}
            </Pressable>
          ) : null}
        </View>
        {ready ? (
          <Pressable ref={menu.buttonRef} accessibilityRole="button" accessibilityLabel={tx('더 보기', 'More')} accessibilityState={{ expanded: menu.open }} onPress={menu.openMenu} style={({ pressed }) => [styles.circle44, pressed && styles.pressed]}>
            <Text variant="title" weight="bold">⋯</Text>
          </Pressable>
        ) : null}
      </View>

      {/* 도구 한 줄 — 지도 보기(= 창 접기) · 동행 초대 · 공유 · 기록 남기기 · 날씨. 알약 다섯이 두 줄로 쌓여 일정을 밀어내던 것을
          아이콘 한 줄로 줄였다(UI 캔버스 ④). 창은 화면을 옮기지 않고 아래 시트로 연다(S15P21E201-1561).
          「공유」는 읽기 전용 링크만 담은 창, 「기록 남기기」는 이 여행을 단 글쓰기다(S15P21E201-1593) — 넷 다 창 안에서 연다(S15P21E201-1760). */}
      {ready ? (
        <View style={styles.actions}>
          <ActionTile icon="map" label={tx('지도 보기', 'View map')} onPress={() => changePanel('collapsed')} />
          {tripId ? <ActionTile icon="invite" label={tx('동행 초대', 'Invite')} onPress={() => setOverlay('invite')} /> : null}
          {tripId ? <ActionTile icon="share" label={tx('공유', 'Share')} onPress={() => setOverlay('share')} /> : null}
          {tripId ? <ActionTile icon="record" label={tx('기록 남기기', 'Write a record')} onPress={() => setOverlay('record')} /> : null}
          {tripId ? <ActionTile icon="weather" label={tx('날씨', 'Weather')} onPress={() => setOverlay('weather')} /> : null}
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
                locationOff={locationOff}
                title={nowTitle}
                detail={nowDetail}
                clock={new Date().toTimeString().slice(0, 5)}
                driftText={nowDrift}
                progress={nowRatio}
                showManualArrival={progress.status === 'RUNNING'}
                record={stayItem && stayArrivedAt
                  ? { text: txf(tx, '도착 %s', 'Arrived %s', clockOf(stayArrivedAt)), onEdit: () => setTimeEdit('arrival') }
                  : lastDeparture && lastDepartureItem
                    ? { text: txf(tx, '%s 출발 %s', 'Left %s at %s', nameOf(lastDepartureItem), clockOf(lastDeparture.at)), onEdit: () => setTimeEdit('departure') }
                    : null}
                onStart={() => void locationGate.ensure().then(() => run.start())}
                onPause={run.pause}
                onArrive={() => currentStop && run.arrive(stopIds, currentStop.id)}
                onDepart={stayItem && stayArrivedAt ? () => void recordDeparture(stayItem, stayArrivedAt, 'manual') : null}
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
            confirming={confirming}
            onPick={setCourseIndex}
            onConfirm={() => course && void confirm(course)}
            nameOf={nameOf}
            tx={tx}
          />

          {loaded ? <RiskStrip atRisk={atRisk} known={pace !== null} estimated={paceEstimated} nameOf={nameOf} tx={tx} /> : null}

          {!loaded ? (
            itinerary?.message
              ? <ErrorCard message={localizeMessage(tx, itinerary.message)} onRetry={() => void load()} tx={tx} />
              : <SheetSkeleton />
          ) : items.length === 0 ? (
            // 늦게 만들어 비운 오늘(백엔드 !1734 — 20:31 뒤 여러 날 여행은 첫날 0곳) — 「아직」이라고 하지 않는다(S15P21E201-1739).
            loaded && isSkippedToday({ days: loaded.days, dayIndex, today: localDateKey(new Date()) }) ? (
              <View style={styles.emptyCard}>
                <Text variant="caption" weight="bold">{tx('오늘은 늦어서 내일부터 짰어요.', 'It was too late for today, so your trip starts tomorrow.')}</Text>
                <Pressable accessibilityRole="button" onPress={() => setDayIndex(dayIndex + 1)} style={({ pressed }) => [styles.emptyAction, pressed && styles.pressed]}>
                  <Text variant="caption" weight="bold" color={color.action.primary}>{tx('내일 일정 보기 ›', "See tomorrow's plan ›")}</Text>
                </Pressable>
              </View>
            ) : <View style={styles.emptyCard}><Text variant="caption" color={color.text.muted}>{tx('이 날에는 아직 장소가 없어요.', 'No places for this day yet.')}</Text></View>
          ) : (
            <View>
              {/* 하루 시작 — 첫날은 출발지, 둘째 날부터는 숙소에서 (S15P21E201-1580) */}
              <DayStartRow start={day?.start} tx={tx} />
              {items.map((item, index) => (
                <ImpressionView key={item.id} tracker={impressions} placeId={item.placeId} requestId={item.requestId}>
                <TimelineStop
                  key={item.id}
                  item={item}
                  startKind={startKind}
                  index={index}
                  last={index === items.length - 1}
                  freeBefore={index > 0 ? freeTimeMinutes(items, index - 1) : null}
                  date={index === 0 ? day?.date ?? null : null}
                  photo={photos[item.placeId] ?? null}
                  name={nameOf(item)}
                  step={progressId ? (item.id === stayId ? 'staying' : steps[index]) : undefined}
                  aim={Boolean(progressId) && item.id !== stayId && index === aimIndex}
                  risky={pace?.atRiskItemIds.includes(item.id) ?? false}
                  pace={paceByItemId.get(item.id)}
                  paceEstimated={paceEstimated}
                  expanded={expandedId === item.id}
                  canEdit={canEdit}
                  busy={busyId === item.id}
                  excluding={excludingId === item.id}
                  onToggle={() => { setSelectedId(item.id); setExpandedId((open) => (open === item.id ? null : item.id)); }}
                  onLock={() => void toggleLock(item)}
                  onArrive={canEdit && todayDay && !arrivedAtOf(progress.outcomes, item.id) ? () => void recordArrival(item) : null}
                  onExclude={() => setExcludeTarget(item)}
                  onOpenPlace={() => router.push(`/place/${item.placeId}`)}
                  legRoute={legRouteParams(items, index, day?.start, nameOf, tx)}
                  onOpenLeg={(() => {
                    // 들어오는 구간을 경로 상세로 — 대중교통·택시·도보를 나란히 본다(S15P21E201-1831).
                    const params = legRouteParams(items, index, day?.start, nameOf, tx);
                    // 이동 칸을 한 번 눌러 본 사람에게는 첫 안내를 다시 띄우지 않는다(㉔-5 규칙).
                    return params ? () => { setLegGuide(false); void markScreenGuideUsed('legs'); router.push({ pathname: '/route-detail', params }); } : null;
                  })()}
                  legGuide={legGuide && index === firstLegIndex}
                  onLegGuideDone={() => setLegGuide(false)}
                  accessToken={accessToken}
                  language={language}
                  tx={tx}
                  locale={locale}
                />
                </ImpressionView>
              ))}
              {/* 하루 끝 — 숙소(마지막 날은 출발지)로 돌아가기 (S15P21E201-1566) */}
              <DayReturnRow leg={day?.returnLeg} tx={tx} />
            </View>
          )}

          {loaded ? <TripBudgetCard budget={budget} style={styles.budget} /> : null}
          {canEdit && items.length > 1 ? (
            <Pressable accessibilityRole="button" onPress={openReorder} style={({ pressed }) => [styles.reorder, pressed && styles.pressed]}>
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

  // 🔴 여행이나 일정을 못 불러왔으면 지도와 창을 그리지 않는다(S15P21E201-1660, 사용자 결정). 전에는 같은 겹친 구조 그대로라
  //    위 4분의 3 이 빈 회색 지도 자리이고, 아래 창 안에 작은 오류 카드·작은 알약만 있었다. 가운데에 오류와 큰 「다시 시도」.
  const failedMessage = page?.state === 'error' ? page.message : !loaded && itinerary?.message ? itinerary.message : null;
  if (failedMessage) {
    return (
      <View style={[styles.failedShell, { paddingTop: insets.top + spacing[3], paddingBottom: insets.bottom + spacing[4] }]}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로', 'Back')} onPress={goBack} style={({ pressed }) => [styles.circle44, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹</Text>
        </Pressable>
        <View style={styles.failedCenter}>
          <View accessibilityRole="alert" style={styles.failedCard}>
            <Text variant="title" weight="bold">{tx('여행을 불러오지 못했어요', 'Could not load the trip')}</Text>
            <Text color={color.text.body}>{localizeMessage(tx, failedMessage)}</Text>
            <Button label={tx('다시 시도', 'Try again')} onPress={() => void load()} />
          </View>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.shell}>
      {/* ── 바탕 지도 ─────────────────────────────────────────────────────────── */}
      <View style={[styles.mapClip, { height: mapHeight }]}>
        {map.stops.length ? (
          // 🔴 지도 부품은 둥근 테두리 칸으로 그려진다. 바탕으로 쓰려면 모서리를 화면 밖으로 밀어낸다.
          <View style={styles.mapBleed}>
            <RouteMap stops={map.stops} selectedId={selectedId} onSelect={setSelectedId} routes={mapRoutes} points={points} currentLocation={usableFix(live.fix) ? { latitude: live.fix.latitude, longitude: live.fix.longitude } : null} height={mapHeight + radius.lg * 2} focusSelected bottomInset={mapCovered} topInset={mapTopCovered} refitKey={mapRefitKey} />
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
        <MobilityLayerToggle value={layerKind} onChange={setLayerKind} layer={mobility} tx={tx} stepFree={loaded?.stepFree === true} style={[styles.mapLayers, { top: insets.top + spacing[2] + 40 }]} />
      ) : null}

      {/* ── 접었을 때 — 탭바 위 정차지 카드 줄 (시안 4b) ──────────────────────────── */}
      {panel === 'collapsed' && items.length ? (
        <Reanimated.ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={[styles.strip, { bottom: bottomMargin + TAB_BAR_HEIGHT + spacing[2] }, stripStyle]}
          onLayout={(event) => setStripHeight(Math.ceil(event.nativeEvent.layout.height))}
          contentContainerStyle={styles.stripInner}
          // 🔴 손을 떼면 카드 «한 장» 단위로 선다 — 전에는 멈출 자리가 없어 카드가 반쯤 잘린 채 섰다 (S15P21E201-1800).
          snapToInterval={STRIP_STEP}
          snapToAlignment="start"
          decelerationRate="fast"
        >
          {items.map((item, index) => (
            <ImpressionView key={item.id} tracker={impressions} placeId={item.placeId} requestId={item.requestId}>
            <Pressable key={item.id} accessibilityRole="button" accessibilityState={{ selected: item.id === selectedId }} onPress={() => setSelectedId(item.id)} style={[styles.stripCard, item.id === selectedId && styles.stripCardOn]}>
              <View style={styles.rowCenter}>
                <View style={styles.numberDot}><Text variant="micro" weight="bold" color={color.text.onAction}>{index + 1}</Text></View>
                <Text variant="caption" weight="bold" color={color.text.muted}>{stopClock(item.startsAt) ?? tx('미정', 'TBD')}</Text>
              </View>
              <StopName weight="bold" title={item.title} nameEn={photos[item.placeId]?.nameEn} />
              {formatTravelLabel(item, tx, index === 0 && startKind) ? <Text variant="micro" color={color.text.muted} numberOfLines={1}>{formatTravelLabel(item, tx, index === 0 && startKind)}</Text> : null}
            </Pressable>
            </ImpressionView>
          ))}
        </Reanimated.ScrollView>
      ) : null}

      {/* ── 탭바 = 창 (S15P21E201-1756) — 막대 하나가 제자리에서 늘어나 창이 되고, 접으면 줄어 막대로 돌아온다. ──
          속(탭 줄 · 창 속)은 다 자란 크기로 바닥에 붙여 둔다 — 자라는 동안 줄바꿈이 다시 일어나지 않고, 막대가 커지며 드러낸다.
          탭 줄은 자라기 시작하면 곧 흐려지고, 창 속은 커지는 끝 무렵 나타난다(sheetMorph). */}
      <View pointerEvents="box-none" style={[styles.dock, styles.sheetDock, { paddingBottom: bottomMargin }]}>
        <Reanimated.View style={[styles.bar, styles.sheet, styles.morphShell, shellStyle]}>
        {/* 접힌 탭 줄 — 홈 · 피드 · 일정 펼치기 · 내 여행 · 뒤로 (시안 4b) */}
        <Reanimated.View
          pointerEvents={panel === 'collapsed' ? 'auto' : 'none'}
          aria-hidden={panel !== 'collapsed' || undefined}
          style={[styles.shellLayer, { width: barWidth, height: TAB_BAR_HEIGHT, marginLeft: -barWidth / 2 }, tabsStyle]}
        >
          <View style={styles.tabRow}>
            <TabSlot label={tx('홈', 'Home')} icon={TAB_ICONS.home} onPress={() => router.replace('/home')} />
            <TabSlot label={tx('피드', 'Feed')} icon={TAB_ICONS.feed} onPress={() => router.replace('/feed')} />
            <TabSlot label={tx('일정 펼치기', 'Show itinerary')} strong onPress={() => changePanel('trip')}>
              <View style={styles.expandCircle}><View style={styles.chevronUp} /></View>
            </TabSlot>
            <TabSlot label={tx('내 여행', 'My trips')} icon={TAB_ICONS.map} selected onPress={() => router.replace('/trips')} />
            <TabSlot label={tx('뒤로', 'Back')} strong onPress={goBack}>
              <View style={styles.backCircle}><View style={styles.chevronLeft} /></View>
            </TabSlot>
          </View>
        </Reanimated.View>

        {/* 창 속 — 지도는 그 뒤에 그대로 있다 */}
        <Reanimated.View
          pointerEvents={panel === 'trip' ? 'auto' : 'none'}
          aria-hidden={panel !== 'trip' || undefined}
          style={[styles.shellLayer, { width: sheetWidth, marginLeft: -sheetWidth / 2 }, sheetInnerStyle]}
        >
          <View {...handleDrag}>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('일정 접기', 'Hide itinerary')} onPress={() => changePanel('collapsed')} style={styles.handleZone}>
              <View style={styles.handle} />
            </Pressable>
          </View>
          {/* 🔴 동행 초대·공유·날씨·기록 남기기는 창 «안에서» 내용만 바꾼다(S15P21E201-1607). 전에는 창 위에 아래 판이 하나 더 올라와
              두 겹이 됐다.
              정정(2026-09-26, S15P21E201-1760): 「기록 남기기」는 글쓰기 화면으로 이동했었다(-1607 때 사용자 결정). 그러면 지도와
              창이 통째로 사라졌다가 돌아오면 여행 화면이 처음부터 다시 그려져, 사용자가 「기록 남기기만 연속성이 유지가 안 된다」고 했다.
              이제 같은 글쓰기(StoryComposeForm)를 창 안에서 연다. 쓰다 닫아도 글은 기기에 남아, 다시 열면 이어 쓴다. */}
          <Animated.View style={swapStyle}>
          {overlay && tripId ? (
            <View style={styles.sheetScroll}>
              <View style={styles.overlayHead}>
                <Pressable accessibilityRole="button" accessibilityLabel={tx('일정으로 돌아가기', 'Back to itinerary')} onPress={() => setOverlay(null)} style={({ pressed }) => [styles.circle44, pressed && styles.pressed]}>
                  <View style={styles.chevronLeft} />
                </Pressable>
                <Text variant="title" weight="bold" numberOfLines={1} style={styles.shrink}>
                  {overlay === 'invite' ? tx('동행 초대', 'Invite') : overlay === 'share' ? tx('공유', 'Share') : overlay === 'record' ? tx('기록 남기기', 'Write a record') : tx('날씨', 'Weather')}
                </Text>
              </View>
              {/* 글쓰기가 들어오면서 입력칸이 생겼다 — 글쓰기 화면(Screen)처럼 키보드가 떠 있어도 목록을 누를 수 있게 하고,
                  iOS 는 키보드에 가린 만큼 스스로 굴러 올린다(안드로이드는 app.json 의 pan 이 화면째 민다). */}
              <ScrollView style={styles.sheetScroll} contentContainerStyle={styles.sheetContent} showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled" automaticallyAdjustKeyboardInsets>
                {overlay === 'invite' ? <TripInvitePanel tripId={tripId} onNavigate={() => setOverlay(null)} /> : null}
                {overlay === 'share' ? <TripReadLinkPanel tripId={tripId} /> : null}
                {overlay === 'record' ? <StoryComposeForm variant="panel" tripId={tripId} onClose={() => setOverlay(null)} /> : null}
                {overlay === 'weather' ? <TripWeatherPanel date={loaded ? (loaded.days[0]?.date ?? null) : undefined} items={loaded?.days[0]?.items} /> : null}
              </ScrollView>
            </View>
          ) : tripContent}
          </Animated.View>
        </Reanimated.View>
        </Reanimated.View>
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
        onConfirm={(reason) => {
          const target = excludeTarget;
          setExcludeTarget(null);
          if (target) void exclude(target, reason);
        }}
      />
      <DropdownMenu visible={menu.open} anchor={menu.anchor} items={menuItems} onClose={menu.close} />
      {locationGate.sheet}
      <ActualTimeSheet
        visible={timeEdit !== null}
        kind={timeEdit ?? 'arrival'}
        placeTitle={(timeEdit === 'departure' ? lastDepartureItem?.title : stayItem?.title) ?? ''}
        currentMs={timeEdit === 'departure' && lastDeparture ? Date.parse(lastDeparture.at) : stayArrivedAt ? Date.parse(stayArrivedAt) : 0}
        minMs={timeEdit === 'departure' && lastDeparture ? Date.parse(lastDeparture.arrivedAt) : startOfLocalDay(Date.now())}
        busy={savingTime}
        onCancel={() => setTimeEdit(null)}
        onSave={(ms) => void saveTime(ms)}
        tx={tx}
      />
    </View>
  );
}

// ── 부품 ────────────────────────────────────────────────────────────────────

function ActionTile({ icon, label, onPress }: { icon: TripActionKind; label: string; onPress: () => void }) {
  return (
    // 🔴 글자를 두 줄까지 둔다 — 일본어 「同行者を招待」처럼 긴 말이 한 줄이면 「同行者を…」로 잘린다.
    <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} style={({ pressed }) => [styles.actionTile, pressed && styles.pressed]}>
      <View style={styles.actionIcon}><TripActionIcon kind={icon} tint={color.text.heading} /></View>
      <Text variant="micro" weight="bold" color={color.text.body} numberOfLines={2} style={styles.actionLabel}>{label}</Text>
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
function CourseCardMobile({ open, courses, index, full, estimated, items, confirming, onPick, onConfirm, nameOf, tx }: {
  open: boolean; courses: TripCourse[]; index: number; full: boolean; estimated: boolean; items: ItineraryItemDto[];
  confirming: boolean; onPick: (index: number) => void; onConfirm: () => void; nameOf: (item: ItineraryItemDto) => string; tx: Tx;
}) {
  const shown = useRef(new Animated.Value(open ? 1 : 0)).current;
  // 🔴 「코스 A로 확정」 줄은 처음부터 펼친다(S15P21E201-1670, 사용자 결정 — 시안 Interactions 와 다르다). 시안은 알약을
  //    눌러야 펼쳤는데, 처음 온 사람은 확정 단추가 있는 줄 몰랐다. 첫 코스가 골라진 채로 시작한다.
  const confirmRow = useRef(new Animated.Value(open ? 1 : 0)).current;
  const x = useRef(new Animated.Value(0)).current;
  const [cardHeight, setCardHeight] = useState(0);
  const [trackWidth, setTrackWidth] = useState(0);
  const slot = courses.length ? Math.max(0, (trackWidth - spacing[1] * 2) / courses.length) : 0;

  useEffect(() => {
    Animated.timing(shown, { toValue: open ? 1 : 0, duration: 420, easing: SLIDE, useNativeDriver: false }).start();
  }, [open, shown]);
  useEffect(() => {
    Animated.timing(confirmRow, { toValue: open ? 1 : 0, duration: 380, easing: SLIDE, useNativeDriver: false }).start();
  }, [open, confirmRow]);
  useEffect(() => {
    Animated.timing(x, { toValue: index * slot, duration: 360, easing: SLIDE, useNativeDriver: false }).start();
  }, [index, slot, x]);

  const course = courses[index] ?? null;
  if (!courses.length) return null;
  const routeLine = items.map((item) => nameOf(item)).join(' → ');

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
        {/* 🔴 한 안뿐일 때 코스 비교가 준비 중이라고 말하던 줄을 뺐다 — 발표·심사에서 덜 만든 것처럼 보였다(S15P21E201-1662, 사용자 결정). */}
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

/**
 * 위험 띠 — 하루를 넘길 곳이 있으면 연분홍에 빨간 글자, 없으면 초록.
 * 🔴 모르면 띠를 안 그린다(UI 캔버스 ④). 「하루 안에 끝나는지 아직 몰라요」는 할 일이 없는 문장이라
 *    일정 맨 위를 차지할 이유가 없다. 초록(괜찮다)으로 칠하지 않는 규칙(S15P21E201-1670)은 그대로다.
 */
function RiskStrip({ atRisk, known, estimated, nameOf, tx }: { atRisk: ItineraryItemDto[]; known: boolean; estimated: boolean; nameOf: (item: ItineraryItemDto) => string; tx: Tx }) {
  const risky = atRisk.length > 0;
  if (!risky && !known) return null;
  const text = risky
    ? atRisk.length === 1
      ? txf(tx, `%s${koreanSubject(atRisk[0].title)} 하루를 넘길 수 있어요`, '%s may run past the day', nameOf(atRisk[0]))
      : txf(tx, '%s곳이 하루를 넘길 수 있어요', '%s places may run past the day', atRisk.length)
    : tx('하루 안에 여유 있게 끝나요', 'The day ends comfortably');
  const tone = risky ? color.state.danger : color.state.success;
  return (
    <View style={[styles.risk, risky ? styles.riskBad : styles.riskOk]}>
      <Text variant="caption" weight="bold" color={tone} style={styles.shrink}>{text}</Text>
      {known && estimated ? <Text variant="caption" color={tone}>{`· ${tx('추정값', 'estimate')}`}</Text> : null}
    </View>
  );
}

function TimelineStop({ item, name, startKind, index, last, freeBefore, date, photo, step, aim = false, risky, pace, paceEstimated, expanded, canEdit, busy, excluding, onToggle, onLock, onArrive, onExclude, onOpenPlace, onOpenLeg, legRoute, legGuide = false, onLegGuideDone, accessToken, language, tx, locale }: {
  item: ItineraryItemDto; /** 화면에 적을 장소 이름 — 영어면 로마자가 붙는다(S15P21E201-1735). */ name: string; startKind: DayStart['kind']; index: number; last: boolean; freeBefore: number | null; date: string | null; photo: PlacePhoto | null; step?: StepState;
  /** 지금 향하는 곳 — 빨간 고리와 「다음」 표. 한 날에 한 곳뿐이다. */ aim?: boolean; risky: boolean;
  pace?: ItineraryPaceItemDto; paceEstimated: boolean; expanded: boolean; canEdit: boolean; busy: boolean; excluding: boolean;
  /** 오늘 방문지이고 아직 도착이 안 적혔을 때만 — 아니면 null 이고 「도착 찍기」를 안 그린다(S15P21E201-1690). */
  onToggle: () => void; onLock: () => void; onArrive: (() => void) | null; onExclude: () => void; onOpenPlace: () => void;
  /** 들어오는 구간을 경로 상세로 연다. 앞 곳이나 이 곳의 좌표를 모르면 null — 누를 수 없는 글자로 둔다(legRoute.ts). */
  onOpenLeg: (() => void) | null;
  /** 들어오는 구간의 좌표 — 펼친 이동 칸이 경로를 물을 때 쓴다(LegRow). */ legRoute: LegRouteParams | null;
  /** 이 칸 위에 이동 칸 첫 안내를 띄운다(한 날에 한 칸) */ legGuide?: boolean; onLegGuideDone?: () => void;
  accessToken: string | null; language: LanguageCode; tx: Tx; locale: string;
}) {
  const leg = formatTravelLabel(item, tx, index === 0 && startKind);
  const reason = pickReasonLine(item.reasonCodes);
  const done = step === 'done';
  // 지금 머무는 곳(도착만 적혔고 그 뒤 도착이 없는 곳) — 「✓ 다녀옴」 대신 「머무는 중」(S15P21E201-1690, 조율 세션 결정).
  const staying = step === 'staying';
  const label = photo?.category ? PLACE_CATEGORY_LABELS[photo.category] : undefined;
  // 🔴 값이 없는 칸은 만들지 않는다 — 「비용 미정」을 줄마다 적으면 빈 칸이 화면에서 제일 눈에 띈다(itinerary.tsx 와 같은 규칙).
  const meta = [
    stopClock(item.startsAt),
    label ? tx(label[0], label[1]) : null,
    typeof item.estimatedCostKrw === 'number' ? (item.estimatedCostKrw === 0 ? tx('무료', 'Free') : txf(tx, '%s원', '₩%s', item.estimatedCostKrw.toLocaleString(locale))) : null,
  ].filter(Boolean).join(' · ');
  const weekday = date ? formatWeekdayShort(date, locale) : null;
  const dayNumber = date ? Number(date.slice(8, 10)) : NaN;
  // 날짜 표시의 높이 — 첫 칸의 세로선은 그 아래에서 시작한다(아래 railBelowDate).
  const [markHeight, setMarkHeight] = useState(0);

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
      {/* 구간 줄을 누르면 무엇을 타는지·택시로 얼마인지 본다(S15P21E201-1831) — 전에는 「이동 25분」 글자뿐이었다. */}
      {legGuide && onOpenLeg ? (
        <GuideCallout
          pointDown
          title={tx('장소 사이 이 칸이 길 안내예요', 'This row between places is your directions')}
          body={tx('누르면 무엇을 타는지, 어디서 내리는지까지 알려 드려요.', 'Tap it to see what to ride and where to get off.')}
          onDone={() => onLegGuideDone?.()}
          style={styles.legGuide}
        />
      ) : null}
      {index > 0 || leg || onOpenLeg ? (
        <View style={onOpenLeg ? styles.legRowTall : styles.legRow}>
          <View style={styles.legRail}>{index > 0 ? <View style={[styles.railLine, styles.railFull]} /> : null}</View>
          {/* 이동 칸 — 테두리 있는 누르는 칸, 지금 가는 구간은 걸음마다 펼친다(UI 캔버스 ㉓-2b, S15P21E201-1884). */}
          <LegRow
            destName={name}
            label={leg}
            transit={typeof item.travelFareKrw === 'number' && item.travelFareKrw > 0}
            route={legRoute}
            now={aim}
            onOpen={onOpenLeg}
            accessToken={accessToken}
            language={language}
            tx={tx}
          />
        </View>
      ) : null}
      <View style={styles.stopRow}>
        <View style={[styles.rail, index === 0 ? styles.railFirst : styles.railCenter]}>
          {/* 🔴 세로선은 칸마다 «점에서 점까지» 조각으로 긋는다. 한 줄을 통째로 깔고 위아래를 숫자로 자르면
              카드 높이가 달라질 때(두 줄 이름·위험 표시) 첫 날짜 위나 마지막 점 아래로 선 끝이 삐져나온다. */}
          {index > 0 ? <View style={[styles.railLine, styles.railTopHalf]} /> : null}
          {!last ? <View style={[styles.railLine, index === 0 ? [styles.railBelowDate, { top: RAIL_FIRST_TOP + markHeight }] : styles.railBottomHalf]} /> : null}
          {index === 0 && Number.isFinite(dayNumber) ? (
            <View style={styles.dateMark} onLayout={(event) => setMarkHeight(Math.ceil(event.nativeEvent.layout.height))}>
              {weekday ? <Text variant="caption" weight="bold">{weekday}</Text> : null}
              {/* 🔴 동백 채움이 「코스 A로 확정」과 같이 보인다 — tokens 규칙 1(화면당 하나)의 예외다. 회색으로 바꾸지 마라.
                  시안이 이 원을 동백으로 그렸고, 짙은 회색 판과 나란히 본 뒤 사용자가 시안대로 가기로 정했다
                  (2026-09-23, S15P21E201-1535). 되돌릴 때는 action.secondary 로 내린다. */}
              <View style={styles.dateCircle}><Text weight="bold" color={color.text.onAction}>{dayNumber}</Text></View>
            </View>
          ) : done ? (
            <View style={styles.dotDone}><Text variant="micro" weight="bold" color={color.text.onAction}>✓</Text></View>
          ) : staying ? (
            // 머무는 곳은 동백이가 서 있다 — 탑승 중 사다리의 「지금 여기」와 같은 표시다.
            <View style={styles.dotHere}><GabolleMascot state="open" still style={styles.dotMascot} /></View>
          ) : aim ? (
            <View style={styles.dotAim} />
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
                <Text weight="bold" style={styles.shrink}>{name}</Text>
                {done ? <View style={styles.chipDone}><Text variant="micro" weight="bold" color={color.state.success}>{tx('✓ 다녀옴', '✓ Visited')}</Text></View> : null}
                {aim ? <View style={styles.chipAim}><Text variant="micro" weight="bold" color={color.text.heading}>{tx('다음 갈 곳', 'Up next')}</Text></View> : null}
                {staying ? <View style={styles.chipStaying}><Text variant="micro" weight="bold" color={color.text.body}>{tx('머무는 중', 'Here now')}</Text></View> : null}
              </View>
              {meta ? <Text variant="caption" color={color.text.muted}>{meta}</Text> : null}
              {/* 왜 이 곳인지 하나 — 없으면 줄째 안 그린다(S15P21E201-1645). 폰 카드는 글자 칸이 좁아 한 줄이면
                  「다른 곳보다 거리가 돋…」처럼 핵심 말이 잘려서 두 줄까지 둔다. */}
              {reason ? <Text variant="caption" color={color.text.body} numberOfLines={2}>{reason}</Text> : null}
              {risky ? <Text variant="micro" weight="bold" color={color.state.danger}>{tx('하루 넘길 위험', 'May run past the day')}</Text> : null}
            </Pressable>
            <LockToggle compact locked={item.locked} name={item.title} busy={busy} onPress={canEdit ? onLock : undefined} tx={tx} />
          </View>
          {expanded ? (
            <>
            <View style={styles.stopDetail}>
              <Text variant="caption" weight="bold" color={pace?.atRisk ? color.state.danger : pace?.visited ? color.state.success : color.text.muted} style={styles.grow}>
                {pace?.visited
                  ? txf(tx, '도착 %s', 'Arrived %s', pace.predictedArrival ? formatClock(pace.predictedArrival, locale) : '--:--')
                  : txf(tx, '예상 도착 %s%s', 'Est. arrival %s%s', pace?.predictedArrival ? formatClock(pace.predictedArrival, locale) : stopClock(item.startsAt) ?? tx('미정', 'TBD'), paceEstimated ? tx(' (추정)', ' (est.)') : '')}
              </Text>
              {canEdit && onArrive && !pace?.visited ? (
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
            {/* 일정에서 장소 상세로 가는 유일한 길 — 카드 자체는 펼치기에 쓴다(S15P21E201-1733).
                위 줄(예상 도착 · 도착 찍기 · 제외)에 넣으면 폰 390 에서 「예상 도착」이 한 글자씩 세로로 눌려 따로 한 줄. */}
            <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 상세 보기', 'Open details for %s', item.title)} onPress={onOpenPlace} style={({ pressed }) => [styles.detailButton, styles.placeLink, pressed && styles.pressed]}>
              <Text variant="caption" weight="bold">{tx('자세히 보기 ›', 'Place details ›')}</Text>
            </Pressable>
            </>
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
  failedShell: { flex: 1, backgroundColor: color.canvas, paddingHorizontal: spacing[4] },
  failedCenter: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  failedCard: { width: '100%', maxWidth: 420, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
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
  // 막대 = 창 껍데기(S15P21E201-1756). 크기는 shellStyle 이 매 프레임 준다. 속은 넘치는 만큼 가려진다(bar 의 overflow hidden).
  morphShell: { position: 'relative' },
  // 껍데기 속 두 겹(탭 줄 · 창 속) — 바닥 가운데에 다 자란 크기로 붙여 둔다. 자라는 껍데기가 이것을 드러낸다.
  shellLayer: { position: 'absolute', bottom: 0, left: '50%' },
  // 🔴 창 뒤로 지도가 비친다 (S15P21E201-1627) — 탭바가 늘어난 것이지 지도 위에 판을 하나 덮은 것이 아니다.
  //    웹은 뒤를 흐려 글자가 지도 선과 겹쳐 읽히지 않게 한다. 네이티브는 흐림 없이 비침만(새 네이티브 모듈 없이).
  sheet: { backgroundColor: SHEET_BG, ...(Platform.OS === 'web' ? ({ backdropFilter: 'blur(18px) saturate(1.2)' } as object) : null) },
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
  actions: { flexDirection: 'row', gap: spacing[1] },
  actionTile: { flex: 1, minWidth: 0, minHeight: 44, alignItems: 'center', gap: 4, paddingVertical: spacing[1] },
  actionIcon: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  actionLabel: { textAlign: 'center' },
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
  legLink: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 32 },
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

  // 카드 타임라인 — 그리드 48 | 1fr, 왼쪽 세로선 2px(가운데)
  legRow: { height: 32, flexDirection: 'row', alignItems: 'center', gap: spacing[2] + spacing[1] },
  // 누르는 이동 칸이 들어가면 높이를 칸에 맡긴다 — 32px 줄에는 테두리 칸이 안 들어간다.
  // 이동 칸 첫 안내 — 이동 칸과 같은 줄에서 시작해 꼬리가 칸을 가리킨다(세로선 칸만큼 비킨다).
  legGuide: { marginLeft: RAIL + spacing[2] + spacing[1], marginTop: spacing[2], marginBottom: spacing[1] },
  legRowTall: { minHeight: 32, flexDirection: 'row', alignItems: 'stretch', gap: spacing[2] + spacing[1], paddingVertical: spacing[2] },
  legRail: { width: RAIL, alignSelf: 'stretch' },
  railLine: { position: 'absolute', left: RAIL / 2 - 1, width: 2, backgroundColor: color.surface.field },
  railFull: { top: 0, bottom: 0 },
  railTopHalf: { top: 0, height: '50%' },
  railBottomHalf: { top: '50%', bottom: 0 },
  // 첫 칸은 점이 아니라 날짜 표시가 위에 붙는다 — 선은 날짜 표시 «아래»에서 시작한다(top 은 잰 높이로 준다).
  // 🔴 전에는 선을 날짜 뒤로 지나가게 두고 날짜에 바탕색(canvas) 칸을 깔아 가렸다. 창이 반투명이 되자(S15P21E201-1627)
  //    그 칸이 지도 위에 회색 네모로 드러났다.
  railBelowDate: { bottom: 0 },
  stopRow: { flexDirection: 'row', gap: spacing[2] },
  rail: { width: RAIL, alignItems: 'center' },
  railFirst: { justifyContent: 'flex-start', paddingTop: RAIL_FIRST_TOP },
  railCenter: { justifyContent: 'center' },
  dateMark: { alignItems: 'center', gap: spacing[1], paddingTop: 2, paddingBottom: spacing[1] },
  // 🔴 사용자 결정으로 시안대로 동백이다 — 위 JSX 주석을 읽어라.
  dateCircle: { width: 32, height: 32, borderRadius: radius.full, backgroundColor: color.action.primary, alignItems: 'center', justifyContent: 'center' },
  // 점 — 바탕색 4px 고리로 세로선을 끊는다(시안 box-shadow 0 0 0 4px #F5F5F7).
  dot: { width: 18, height: 18, borderRadius: radius.full, borderWidth: 4, borderColor: color.canvas, backgroundColor: color.surface.field },
  dotDone: { width: 26, height: 26, borderRadius: radius.full, borderWidth: 4, borderColor: color.canvas, backgroundColor: color.state.success, alignItems: 'center', justifyContent: 'center' },
  // 🔴 고리 색은 state.dot — 글자가 아니라 점이라 쓸 수 있다(tokens 규칙). 바탕색 고리로 세로선을 끊는 것은 다른 점과 같다.
  dotAim: { width: 20, height: 20, borderRadius: radius.full, borderWidth: 4, borderColor: color.state.dot, backgroundColor: color.canvas },
  dotHere: { width: 32, height: 32, borderRadius: radius.full, backgroundColor: color.canvas, alignItems: 'center', justifyContent: 'center' },
  dotMascot: { width: 28, height: 28 },
  stopCard: { flex: 1, minWidth: 0, padding: 10, gap: 10, borderRadius: radius.lg, backgroundColor: color.surface.card },
  stopHead: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  thumb: { width: THUMB, height: THUMB, borderRadius: radius.md, overflow: 'hidden', backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' },
  glyph: { fontSize: 28, lineHeight: 34 },
  stopCopy: { flex: 1, minWidth: 0, gap: spacing[1] },
  titleLine: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 6 },
  chipDone: { paddingHorizontal: 6, paddingVertical: 1, borderRadius: radius.full, backgroundColor: color.state.successBg },
  chipAim: { paddingHorizontal: 6, paddingVertical: 1, borderRadius: radius.full, borderWidth: 1, borderColor: color.state.dot },
  chipStaying: { paddingHorizontal: 6, paddingVertical: 1, borderRadius: radius.full, backgroundColor: color.surface.soft },
  stopDetail: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingTop: 10, borderTopWidth: 1, borderTopColor: color.surface.border },
  detailButton: { minHeight: 40, paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint, justifyContent: 'center' },
  placeLink: { alignSelf: 'flex-start', marginTop: spacing[2] },
  excludeButton: { backgroundColor: color.state.dangerBg },
  emptyCard: { padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  emptyAction: { minHeight: 40, marginTop: spacing[2], paddingHorizontal: spacing[3], justifyContent: 'center' },

  budget: { gap: 10, marginTop: spacing[2] },
  reorder: { minHeight: 48, borderRadius: radius.md, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },

  skeleton: { gap: spacing[3] },
  errorCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  retry: { flex: 0, alignSelf: 'flex-start', paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.surface.border },
});
