// 여행 페이지 — 넓은 화면 (추천 코스 + 일정 통합, 1단계) · S15P21E201-1535
//
// 시안: frontend/docs/design_handoff_trip_page/ (README · design/TripPageDesktop.dc.html · 스크린샷 desktop-*.png)
//
// 🔴 넓은 화면은 **정보 제공 중심**이다(시안 README). 출발·도착 찍기 같은 진행은 없다 — 그건 폰
//    (여행 중에 여는 화면)의 몫이다. 장소를 열로 나란히 견주고 지도는 오른쪽에 둔다.
//
// 🔴 이 화면은 두 입구에서 열린다(tripPageData.ts). 추천에서 오면 확정 전, 일정에서 오면 확정 후다.
//    「코스 A로 확정」은 새 서버 동작이 아니다 — 코스마다 이미 일정이 만들어져 있고, 확정은 그 일정을
//    «내 일정으로 여는 것»이다(recommendations.tsx 의 build 와 같은 길, 이름 묻기 포함).
//
// 🔴 편집(순서·고정·제외·다시 계산·되돌리기)은 여기서 안 한다. 시안의 넓은 화면에 그 자리가 없다.
//    대신 ⋯ 의 「일정 편집」이 지금까지의 일정 화면을 그대로 연다(?classic=1) — 기능을 잃지 않는다.
import { Fragment, useEffect, useRef, useState } from 'react';
import { Animated, Easing, Image, Pressable, ScrollView, StyleSheet, View, useWindowDimensions } from 'react-native';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { PlaceVisual } from '@/components/PlaceVisual';
import { Skeleton } from '@/components/Skeleton';
import { StopName } from '@/components/StopName';
import { Text } from '@/components/Text';
import { color, desktopGutter, radius, spacing } from '@/design/tokens';
import { stopNameForLanguage, stopNameParts } from '@/discovery/romanize';
import { useI18n } from '@/i18n';
import { formatDayHeading } from '@/i18n/datetime';
import { txf } from '@/i18n/format';
import { koreanSubject } from '@/i18n/korean';
import { localizeMessage } from '@/i18n/messages';
import { PLACE_CATEGORY_LABELS } from '@/discovery/placeCategoryLabels';
import { RouteMap } from '@/map/RouteMap';
import { courseLetter } from '@/plan/CourseCard';
import { stopClock, type DayStart, type ItineraryItemDto } from '@/plan/itinerary';
import { formatTravelLabel, totalTravelMinutes } from '@/plan/itinerarySummary';
import { legRouteParams } from '@/trip/page/legRoute';
import { LegRow } from '@/trip/page/LegRow';
import { categoryGlyph, type PlacePhoto } from '@/plan/placePhotos';
import { pickReasonLine } from '@/plan/recommendations';
import { canConfirmCourse, type TripCourse } from '@/plan/tripCourses';
import { localDateKey } from '@/plan/tripProgress';
import { isSkippedToday } from '@/trip/page/emptyDay';
import { humanTripTitle } from '@/trip/tripNaming';
import { TripNameSheet } from '@/trip/TripNameSheet';

import { TripBudgetCard } from './TripBudgetCard';
import type { TripPageSource } from './tripPageData';
import { formatDuration, formatManwon, freeTimeMinutes, stayMinutes } from './tripPageModel';
import { useTripPage } from './useTripPage';
import { DayReturnRow } from './DayReturnRow';
import { DayStartRow } from './DayStartRow';
import { FreeTimeRow } from './FreeTimeRow';
import { RouteColorLegend } from '@/map/RouteColorLegend';
import { TripOverlay, type TripOverlayKind } from './TripOverlay';
import { TripInvitePanel } from '@/trip/TripInvitePanel';
import { TripReadLinkPanel } from '@/trip/TripReadLinkPanel';
import { DropdownMenu, useDropdownMenu, type DropdownMenuItem } from '@/components/DropdownMenu';
import { TripWeatherPanel } from '@/trip/TripWeatherPanel';
import { weatherDayFor } from '@/trip/weatherDay';
import { localToday } from '@/plan/tripBasics';
import { StoryComposeForm } from '@/social/StoryComposeForm';
import { ImpressionView, useImpressionTracker } from '@/analytics/impressions';
import { itemOtherName } from '@/plan/itineraryItemName';

type Tx = (ko: string, en: string) => string;
type Layout = 'cards' | 'map';

/** 시안 값 — 코스 알약 한 칸 220, 보기 전환 한 칸 132, 지도 칸 440. 토큰에 없는 «칸 크기» 라 여기 둔다. */
const COURSE_SLOT = 220;
const VIEW_SLOT = 132;
const MAP_WIDTH = 440;
/** 이보다 좁으면 지도를 카드 아래로 쌓는다 — 1100 에서도 일본어 요약 카드가 「分かりませ / ん」으로 꺾였다. 1280 PC 는 옆 그대로 */
const STACK_BELOW = 1240;
/** 쌓았을 때 지도 높이 — 카드를 보며 지도도 한눈에 */
const STACKED_MAP_HEIGHT = 420;
/**
 * 장소 카드 한 장의 최대 폭. 시안(1440)에서 카드는 약 215 다 — 그보다 조금 넉넉히 두고 거기서 멈춘다.
 * 🔴 상한이 없으면 넓은 모니터(2900)에서 카드가 680 까지 부풀고 지도는 440 에 남아, 지도가 «옆의 작은 띠» 가 된다
 *    (2026-09-23 사용자 지적). 남는 폭은 지도가 가져간다 — 사용자가 고른 방식이다.
 */
const CARD_MAX_WIDTH = 260;
const BIG_LIST_WIDTH = 320;
/**
 * 지도 위 왼쪽 위에 요약 칩과 색 범례가 떠 있다. 범례 아래끝이 지도 위에서 약 121 이고, 지도를 맞출 때 위에 60 을 따로 두므로 그만큼을 뺀 값을
 * 위 여백에 더한다 — 안 더하면 가장 북쪽 정차지가 범례 밑에 숨는다. 범례가 없을 때는 더하지 않는다.
 */
const LEGEND_COVER = 80;
/** 시안의 미끄러짐 — 코스 360ms · 보기 전환 320ms, 같은 곡선. */
const SLIDE = Easing.bezier(0.2, 0.8, 0.2, 1);

export function TripPageDesktop({ source, askName = false }: { source: TripPageSource; askName?: boolean }) {
  const router = useRouter();
  const { accessToken } = useAuth();
  const { tx, locale, language } = useI18n();
  // 🔴 좁은 데스크톱(세로로 든 탭 800 · 폴드 펼침 가로)은 지도를 카드 «아래»로 — 옆에 두면 지도가 최소 440 을 지키고
  //    남는 자리까지 나눠 가져 왼쪽 열이 130 남짓이 됐다. 카드가 한 글자 폭이 되어 「미 / 정」「Cost unk / nown」처럼
  //    모든 낱말이 꺾였다(전체 점검 2026-10-02 — 1940 이후 세로 탭이 데스크톱 판이 되면서 드러났다).
  const { width: windowWidth } = useWindowDimensions();
  const stacked = windowWidth < STACK_BELOW;
  const { height: windowHeight } = useWindowDimensions();

  // 불러오기·세기는 폰과 같이 쓴다(useTripPage). 여기 남은 것은 넓은 화면에만 있는 상태다.
  const {
    page, load, courses, course, courseIndex, setCourseIndex, confirmed, setConfirmed, tripId,
    itinerary, setItinerary, loaded, dayIndex, setDayIndex, items, selectedId, setSelectedId, photos, pace,
    map, routes, routeLegend, points, anyEstimatedLine, travelTotal, budget, atRisk, allEstimated, title, headSub, confirm, confirming,
  } = useTripPage(source);
  // 추천 노출 — 카드가 실제로 화면에 보일 때만 보낸다(S15P21E201-1696). 확정 전에는 코스를 고르는 중이다.
  const impressions = useImpressionTracker({ accessToken, sourceScreen: confirmed ? 'TRIP_ITINERARY' : 'TRIP_COURSES', active: page?.state === 'ready' });
  // 그날 첫 곳은 어디서 오나 — 둘째 날부터는 숙소다(S15P21E201-1580). 서버가 안 알려 주면(옛 응답) 출발지.
  const startKind: DayStart['kind'] = loaded?.days[dayIndex]?.start?.kind ?? 'ORIGIN';
  // 장소 카드 줄 아래의 「길 보기」 — 고른 카드로 들어오는 구간(S15P21E201-1831).
  const selectedLeg = (() => {
    const index = items.findIndex((each) => each.id === selectedId);
    if (index < 0) return null;
    const params = legRouteParams(items, index, loaded?.days[dayIndex]?.start, (each) => each.title, tx);
    return params ? { item: items[index], params, label: formatTravelLabel(items[index], tx, index === 0 && startKind) } : null;
  })();
  const [layout, setLayout] = useState<Layout>('cards');
  // 🔴 ⋯ 메뉴는 공용 DropdownMenu(창)다(S15P21E201-1593). 전에는 화면 위에 직접 그린 판이라 닫는 길이 「⋯ 다시 누르기」뿐이었다 —
  //    메뉴를 연 채 「동행 초대」를 누르면 메뉴가 초대 창 위에 남고 Escape 로도 안 닫혔다(넓은 화면 실측).
  //    창 방식은 바깥을 누르거나 Escape(onRequestClose)로 닫히고, 열린 동안 뒤의 알약은 눌리지 않는다.
  const menu = useDropdownMenu();
  // 🔴 지도의 경사·그늘 «겹» 칩은 뺐다(S15P21E201-1896) — 경로 선 자체를 고른 조건(경사 피하기·그늘 우선)의 색으로 칠하고,
  //    그 색의 뜻은 지도 위 범례(RouteColorLegend)가 알려 준다.
  const [overlay, setOverlay] = useState<TripOverlayKind | null>(null);
  const [naming, setNaming] = useState(askName);
  const [leftHeight, setLeftHeight] = useState(0);
  const [gridWidth, setGridWidth] = useState(0);
  /** 스크롤 칸이 보여 주는 높이와, 그 안에서 본문이 시작하는 자리 — 지도를 «화면 아래까지» 늘리는 데 쓴다. */
  const [viewportHeight, setViewportHeight] = useState(0);
  const [bodyTop, setBodyTop] = useState(0);

  if (!page) return <LoadingState tx={tx} />;
  if (page.state === 'error') return <ErrorState message={localizeMessage(tx, page.message)} onRetry={() => void load()} tx={tx} />;

  const mapSummary = [
    txf(tx, '장소 %s곳', '%s places', items.length),
    totalTravelMinutes(items) > 0 ? txf(tx, '이동 %s분', '%s min travel', totalTravelMinutes(items)) : null,
  ].filter(Boolean).join(' · ');
  const bigMapHeight = Math.max(520, windowHeight - 260);
  // 시안 3a 의 지도는 카드 위끝에서 **화면 아래까지** 내려온다(아래 여백 = 본문 아래 여백). 카드 열이 그보다 길면
  // 카드 열에 맞춘다. 둘 다 모르는 첫 그림에서는 440.
  // 본문 위 여백(styles.body 의 paddingTop)까지 빼야 딱 맞는다 — 안 빼면 4px 넘쳐 쓸데없는 스크롤이 생긴다.
  const fillHeight = viewportHeight && bodyTop ? viewportHeight - bodyTop - spacing[1] - spacing[8] : 0;
  const cardsMapHeight = Math.max(MAP_WIDTH, leftHeight, fillHeight);

  const mapPanel = (height: number) => (
    <View style={[styles.mapPanel, { height }]}>
      {map.stops.length ? (
        <RouteMap stops={map.stops} selectedId={selectedId} onSelect={setSelectedId} routes={routes} points={points} height={height} focusSelected topInset={routeLegend ? LEGEND_COVER : 0} />
      ) : (
        <View style={styles.mapEmpty}><Text variant="caption" color={color.text.muted}>{tx('장소의 좌표가 아직 없어 지도에 그릴 수 없어요.', 'These places have no coordinates yet, so the map is empty.')}</Text></View>
      )}
      <View pointerEvents="none" style={styles.mapSummary}><Text variant="caption" weight="bold" numberOfLines={1}>{mapSummary}</Text></View>
      {map.stops.length ? <RouteColorLegend legend={routeLegend} tx={tx} style={styles.mapLayers} /> : null}
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={layout === 'map' ? tx('장소 카드로 보기', 'Show place cards') : tx('큰 지도로 보기', 'Show the big map')}
        onPress={() => setLayout(layout === 'map' ? 'cards' : 'map')}
        style={({ pressed }) => [styles.mapExpand, pressed && styles.pressed]}
      >
        <Text weight="bold">{layout === 'map' ? '⤡' : '⤢'}</Text>
      </Pressable>
    </View>
  );

  const menuItems: DropdownMenuItem[] = [
    ...(tripId ? [{ key: 'rename', label: tx('이름 바꾸기', 'Rename'), onPress: () => setNaming(true) }] : []),
    ...(course?.itineraryId ? [{
      key: 'edit',
      label: tx('일정 편집', 'Edit itinerary'),
      hint: tx('순서·고정·제외·다시 계산', 'Order, pin, remove, recalculate'),
      onPress: () => router.push(`/trips/${course.itineraryId}/itinerary?classic=1`),
    }] : []),
  ];
  return (
    <View style={styles.shell}>
      <ScrollView style={styles.scroll} contentContainerStyle={styles.content} onLayout={(event) => setViewportHeight(Math.round(event.nativeEvent.layout.height))}>
        {/* ── 머리 — ‹ · 제목 + 요약 · 동행 초대 · 날씨 · ⋯ ───────────────────── */}
        <View style={styles.head}>
          <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => (router.canGoBack() ? router.back() : router.replace('/trips'))} style={({ pressed }) => [styles.circle44, pressed && styles.pressed]}>
            <Text variant="title" weight="bold">‹</Text>
          </Pressable>
          <View style={styles.headCopy}>
            <Text variant="title" weight="bold" numberOfLines={1}>{title}</Text>
            {headSub ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{headSub}</Text> : null}
          </View>
          {/* 단추 묶음 — 좁은 데스크톱 판(갤럭시 탭 세로 753 · 일본어처럼 단추 글이 긴 언어)에서는 묶음째 다음 줄로 내려간다.
              전에는 한 줄에 다 넣으려고 제목이 「햇살 가…」로 줄었다(S15P21E201-1953). */}
          <View style={styles.headActions}>
          {/* 화면을 옮기지 않고 창으로 연다 — 동행 초대는 가운데 창, 날씨는 오른쪽 서랍(S15P21E201-1561). */}
          {tripId ? <HeadPill label={tx('동행 초대', 'Invite')} active={overlay === 'invite'} onPress={() => setOverlay('invite')} /> : null}
          {/* 「공유」는 동행 초대 창 맨 아래에 숨어 있던 읽기 전용 링크만 담은 창, 「기록 남기기」는 이 여행을 단 글쓰기(S15P21E201-1593).
              「기록 남기기」도 가운데 창으로 연다 — 전에는 이것만 글쓰기 화면으로 넘어가 여행 화면이 끊겼다(S15P21E201-1760). */}
          {tripId ? <HeadPill label={tx('공유', 'Share')} active={overlay === 'share'} onPress={() => setOverlay('share')} /> : null}
          {tripId ? <HeadPill label={tx('기록 남기기', 'Write a record')} active={overlay === 'record'} onPress={() => setOverlay('record')} /> : null}
          {tripId ? <HeadPill label={tx('날씨', 'Weather')} active={overlay === 'weather'} onPress={() => setOverlay('weather')} /> : null}
          {/* 여행 돈(S15P21E201-1935) — 화면으로 연다 */}
          {tripId ? <HeadPill label={tx('여행 돈', 'Trip money')} active={false} onPress={() => router.push(`/${tripId}/money` as never)} /> : null}
          <Pressable ref={menu.buttonRef} accessibilityRole="button" accessibilityLabel={tx('더 보기', 'More')} accessibilityState={{ expanded: menu.open }} onPress={menu.openMenu} style={({ pressed }) => [styles.circle40, pressed && styles.pressed]}>
            <Text weight="bold">⋯</Text>
          </Pressable>
          </View>
        </View>

        {/* ── 코스 줄 — 추천 코스 알약 · 확정 · (오른쪽) 장소 카드 | 큰 지도 ───────── */}
        <View style={styles.courseRow}>
          <Text variant="caption" weight="bold" color={color.text.muted} style={styles.noShrink}>{tx('추천 코스', 'Courses')}</Text>
          {confirmed && course ? (
            <>
              <View style={styles.confirmedPill}>
                <Text variant="caption" weight="bold" color={color.state.success}>{txf(tx, '✓ 코스 %s 확정', '✓ Course %s confirmed', courseLetter(courseIndex))}</Text>
                {course.summary.costKrw !== null ? <Text variant="caption" color={color.text.muted}>{` · ${txf(tx, '%s 예상', '%s est.', formatManwon(course.summary.costKrw, tx))}`}</Text> : null}
              </View>
              {courses.length > 1 ? (
                <Pressable accessibilityRole="button" onPress={() => setConfirmed(false)} style={({ pressed }) => [styles.textButton, pressed && styles.pressed]}>
                  <Text variant="caption" weight="bold" color={color.text.muted}>{tx('코스 바꾸기', 'Change course')}</Text>
                </Pressable>
              ) : null}
            </>
          ) : (
            <>
              <CoursePill courses={courses} index={courseIndex} onPick={setCourseIndex} tx={tx} />
              {course ? (
                <Pressable
                  accessibilityRole="button"
                  accessibilityState={{ busy: confirming, disabled: confirming || !canConfirmCourse(course) }}
                  disabled={confirming || !canConfirmCourse(course)}
                  onPress={() => void confirm(course)}
                  style={({ pressed }) => [styles.confirmButton, (pressed || confirming) && styles.pressed]}
                >
                  <Text variant="caption" weight="bold" color={color.text.onAction}>{txf(tx, '코스 %s로 확정', 'Confirm course %s', courseLetter(courseIndex))}</Text>
                </Pressable>
              ) : null}
            </>
          )}
          {/* 🔴 한 안뿐일 때 코스 비교가 준비 중이라고 말하던 줄을 뺐다 — 발표·심사에서 덜 만든 것처럼 보였다(S15P21E201-1662, 사용자 결정). */}
          <ViewSwitch layout={layout} onChange={setLayout} tx={tx} />
        </View>

        {/* 여러 날이면 일차를 고른다 — 시안은 하루짜리라 이 줄이 없다. 하루짜리에는 안 그린다. */}
        {loaded && loaded.days.length > 1 ? (
          <View style={styles.dayRow}>
            {loaded.days.map((day, index) => (
              <Pressable key={`${day.date}-${index}`} accessibilityRole="tab" accessibilityState={{ selected: index === dayIndex }} onPress={() => setDayIndex(index)} style={[styles.dayChip, index === dayIndex && styles.dayChipOn]}>
                <Text variant="caption" weight="bold" color={index === dayIndex ? color.text.onAction : color.text.heading}>{formatDayHeading(day.date, locale) ?? txf(tx, '%s일차', 'Day %s', index + 1)}</Text>
              </Pressable>
            ))}
          </View>
        ) : null}

        {/* ── 본문 ───────────────────────────────────────────────────────────── */}
        {!loaded ? (
          itinerary?.message
            ? <ErrorState message={localizeMessage(tx, itinerary.message)} onRetry={() => void load()} tx={tx} />
            : <View key="loading" style={styles.body}><View style={styles.left}><Skeleton height={420} radius={radius.lg} /></View><Skeleton width={MAP_WIDTH} height={420} radius={radius.lg} /></View>
        ) : layout === 'cards' ? (
          // 🔴 세 갈래(불러오는 중 · 장소 카드 · 큰 지도)에 key 를 따로 준다. 안 주면 React 가 **같은 자리의 같은 View 를
          //    다시 쓰고**, 웹의 onLayout 은 View 가 처음 생길 때만 크기 재기를 건다 — 뼈대 때 onLayout 이 없던 View 를
          //    물려받으면 카드 열 높이가 끝내 안 들어와 지도가 440 에 갇혔다(2026-09-23 사용자 지적, 배포본 재현).
          <View key="cards" style={[styles.body, stacked && styles.bodyStacked]} onLayout={(event) => setBodyTop(Math.round(event.nativeEvent.layout.y))}>
            <View style={[styles.left, stacked ? styles.leftStacked : styles.leftCapped]} onLayout={(event) => setLeftHeight(Math.round(event.nativeEvent.layout.height))}>
              {/* 하루 시작 — 첫날은 출발지, 둘째 날부터는 숙소에서 (S15P21E201-1580) */}
              <DayStartRow start={loaded?.days[dayIndex]?.start} tx={tx} />
              <View style={styles.grid} onLayout={(event) => setGridWidth(Math.round(event.nativeEvent.layout.width))}>
                {gridWidth > 0 ? items.map((item, index) => (
                  <ImpressionView key={item.id} tracker={impressions} placeId={item.placeId} requestId={item.requestId}>
                  <PlaceCard
                    key={item.id}
                    item={item}
                    startKind={startKind}
                    index={index}
                    items={items}
                    width={Math.floor((gridWidth - spacing[3] * (cardColumns(gridWidth, stacked) - 1)) / cardColumns(gridWidth, stacked))}
                    photo={photos[item.placeId] ?? null}
                    selected={item.id === selectedId}
                    risky={pace?.atRiskItemIds.includes(item.id) ?? false}
                    onPress={() => setSelectedId(item.id)}
                    tx={tx}
                    locale={locale}
                  />
                  </ImpressionView>
                )) : null}
                {/* 늦게 만들어 비운 오늘(백엔드 !1734) — 「아직」이라고 하지 않고 내일로 이끈다(S15P21E201-1739). */}
                {items.length === 0 ? (loaded && isSkippedToday({ days: loaded.days, dayIndex, today: localDateKey(new Date()) })
                  ? <View style={styles.rowCenter}><Text variant="caption" weight="bold">{tx('오늘은 늦어서 내일부터 짰어요.', 'It was too late for today, so your trip starts tomorrow.')}</Text><Pressable accessibilityRole="button" onPress={() => setDayIndex(dayIndex + 1)}><Text variant="caption" weight="bold" color={color.action.primary}>{tx('내일 일정 보기 ›', "See tomorrow's plan ›")}</Text></Pressable></View>
                  : <Text variant="caption" color={color.text.muted}>{tx('이 날에는 아직 장소가 없어요.', 'No places for this day yet.')}</Text>) : null}
              </View>
              {/* 고른 카드로 들어오는 구간의 길 — 카드 전체가 버튼이라 그 안에 버튼을 또 둘 수 없어 카드 줄 아래에 둔다(S15P21E201-1831). */}
              {/* 폰과 같은 이동 칸(UI 캔버스 ㉓-2b, S15P21E201-1887) — 테두리 있는 누르는 칸 + 짙은 「길 안내 ›」. */}
              {selectedLeg ? (
                <View style={styles.selectedLeg}>
                  <LegRow
                    destName={stopNameForLanguage(selectedLeg.item.title, itemOtherName(selectedLeg.item, photos[selectedLeg.item.placeId], language), language)}
                    label={selectedLeg.label}
                    transit={typeof selectedLeg.item.travelFareKrw === 'number' && selectedLeg.item.travelFareKrw > 0}
                    route={selectedLeg.params}
                    now={false}
                    onOpen={() => router.push({ pathname: '/route-detail', params: selectedLeg.params })}
                    accessToken={accessToken}
                    language={language}
                    tx={tx}
                  />
                </View>
              ) : null}
              {/* 하루 끝 — 숙소(마지막 날은 출발지)로 돌아가기 (S15P21E201-1566) */}
              <DayReturnRow leg={loaded?.days[dayIndex]?.returnLeg} tx={tx} />
              <View style={styles.summaryRow}>
                <TripBudgetCard budget={budget} style={styles.summaryFlexBudget} />
                <View style={[styles.summaryCard, styles.summaryFlex1]}>
                  <Text variant="caption" weight="bold" color={color.text.muted}>{tx('이동', 'Travel')}</Text>
                  <Text variant="display" weight="bold">{travelTotal > 0 ? txf(tx, '%s분', '%s min', travelTotal) : tx('아직 몰라요', 'Not known yet')}</Text>
                  {/* 🔴 모르는 것을 지우지 않고 «모른다» 고 적는다 — 도보는 대중교통 여행에서 서버가 안 잰다(itinerary.tsx 주석).
                      「대중교통 안내 아직 없어요」는 지웠다 — 구간마다 「길 보기」가 버스·지하철 단계를 보여준다(S15P21E201-1831). */}
                  <Text variant="caption" color={color.text.muted}>{tx('걷는 거리는 아직 안 셌어요 · 버스·지하철은 「길 안내」에서', 'Walking distance isn’t counted yet · bus and metro under Directions')}</Text>
                </View>
                <View style={[styles.summaryCard, styles.summaryFlex1]}>
                  <Text variant="caption" weight="bold" color={color.text.muted}>{tx('확인할 것', 'Check')}</Text>
                  {atRisk.length ? (
                    <Text weight="bold" color={color.state.danger}>
                      {atRisk.length === 1
                        ? txf(tx, `%s${koreanSubject(atRisk[0].title)} 하루를 넘길 수 있어요`, '%s may run past the day', stopNameForLanguage(atRisk[0].title, itemOtherName(atRisk[0], photos[atRisk[0].placeId], language), language))
                        : txf(tx, '%s곳이 하루를 넘길 수 있어요', '%s places may run past the day', atRisk.length)}
                    </Text>
                  ) : (
                    // 🔴 모르면 초록(괜찮다는 색)으로 칠하지 않는다(S15P21E201-1670). 모를 때 적던 「아직 몰라요」는 할 일이 없는
                    //    문장이라 폰 위험 띠처럼 빼고, 무엇을 모르는지만 흐리게 적는다(UI 캔버스 ④).
                    <Text weight="bold" color={pace ? color.state.success : color.text.muted}>{pace ? tx('하루 안에 여유 있게 끝나요', 'The day ends comfortably') : tx('여행 당일 이동 기록이 쌓이면 알려 드려요', 'We will tell you once trip-day records come in')}</Text>
                  )}
                  {allEstimated ? <Text variant="caption" color={color.text.muted}>{tx('기록이 적어 추정값이에요. 모든 장소가 「추정」 상태예요.', 'Few records yet, so these are estimates. Every place is marked “estimated”.')}</Text> : null}
                </View>
              </View>
            </View>
            {/* 지도는 왼쪽 열 높이와 화면 아래까지 중 긴 쪽 — 둘 다 «재서» 맞춘다. 숫자로 박으면 카드가 두 줄일 때 지도가 짧다. */}
            <View style={stacked ? styles.mapStacked : styles.mapColumn}>{mapPanel(stacked ? STACKED_MAP_HEIGHT : cardsMapHeight)}</View>
          </View>
        ) : (
          <View key="map" style={styles.body}>
            <View style={styles.bigList}>
              {items.map((item, index) => {
                const photo = photos[item.placeId] ?? null;
                const leg = formatTravelLabel(item, tx, index === 0 && startKind);
                // 들어오는 구간을 경로 상세로 — 대중교통·택시·도보를 나란히 본다(S15P21E201-1831). 좌표를 모르면 글자만.
                const legParams = legRouteParams(items, index, loaded?.days[dayIndex]?.start, (each) => each.title, tx);
                return (
                  <Fragment key={item.id}>
                    {/* 앞 곳과 이 곳 사이에 남는 시간 — 「자유 시간 · 50분」(S15P21E201-1668). 모르거나 30분이 안 되면 안 그린다. */}
                    {index > 0 ? <FreeTimeRow minutes={freeTimeMinutes(items, index - 1)} tx={tx} style={styles.bigFree} /> : null}
                    {legParams ? (
                      <View style={styles.bigLeg}>
                        <LegRow
                          destName={stopNameForLanguage(item.title, itemOtherName(item, photo, language), language)}
                          label={leg}
                          transit={typeof item.travelFareKrw === 'number' && item.travelFareKrw > 0}
                          route={legParams}
                          now={false}
                          onOpen={() => router.push({ pathname: '/route-detail', params: legParams })}
                          accessToken={accessToken}
                          language={language}
                          tx={tx}
                        />
                      </View>
                    ) : leg ? <Text variant="caption" color={color.text.muted} style={styles.bigLegText}>{leg}</Text> : null}
                    <ImpressionView tracker={impressions} placeId={item.placeId} requestId={item.requestId}>
                    <Pressable accessibilityRole="button" accessibilityState={{ selected: item.id === selectedId }} onPress={() => setSelectedId(item.id)} style={[styles.bigRow, item.id === selectedId && styles.selectedBorder]}>
                      <View style={styles.bigThumb}>
                        {photo?.photoUrl ? <Image source={{ uri: photo.photoUrl }} resizeMode="cover" style={styles.fill} accessibilityLabel="" /> : <Text variant="title">{categoryGlyph(photo?.category)}</Text>}
                      </View>
                      <View style={styles.bigCopy}>
                        <View style={styles.rowCenter}><NumberDot n={index + 1} size={20} /><StopName weight="bold" title={item.title} nameEn={itemOtherName(item, photo, language)} /></View>
                        <Text variant="caption" color={color.text.muted} numberOfLines={1}>{[stopClock(item.startsAt), legParams ? null : leg].filter(Boolean).join(' · ')}</Text>
                      </View>
                    </Pressable>
                    </ImpressionView>
                  </Fragment>
                );
              })}
              {anyEstimatedLine ? <View style={styles.lineNote}><Text variant="caption" color={color.text.body}>{tx('옅은 선은 어림한 길이라 실제로 가는 길과 다를 수 있어요. 이동 시간도 어림값이에요.', 'Faded lines are estimates and may differ from the way you actually go. Travel times are estimates too.')}</Text></View> : null}
            </View>
            <View style={styles.bigMap}>{mapPanel(bigMapHeight)}</View>
          </View>
        )}
      </ScrollView>

      {naming && tripId ? (
        <TripNameSheet
          tripId={tripId}
          currentTitle={humanTripTitle(loaded?.title)}
          dateLabel={headSub || null}
          accessToken={accessToken}
          onClose={() => setNaming(false)}
          onSaved={(saved) => {
            setNaming(false);
            // 다시 부르지 않고 제목만 바꾼다 — 다시 부르면 잠깐 옛 이름이 보여 「안 바뀌었다」로 읽힌다(itinerary.tsx 와 같은 이유).
            if (saved) setItinerary((prev) => (prev?.value ? { ...prev, value: { ...prev.value, title: saved } } : prev));
          }}
        />
      ) : null}

      {tripId ? (
        <TripOverlay visible={overlay !== null} shape={overlay === 'weather' ? 'drawer' : 'center'} onClose={() => setOverlay(null)}>
          {overlay === 'invite' ? <TripInvitePanel tripId={tripId} onNavigate={() => setOverlay(null)} /> : null}
          {overlay === 'share' && tripId ? <TripReadLinkPanel tripId={tripId} /> : null}
          {overlay === 'record' ? <>
            {/* 다른 창은 제목을 스스로 그린다 — 글쓰기는 화면·폰 창이 제목을 그리므로 여기서 단다. */}
            <Text variant="display" weight="bold" style={styles.recordTitle}>{tx('기록 남기기', 'Write a record')}</Text>
            <StoryComposeForm variant="panel" tripId={tripId} onClose={() => setOverlay(null)} />
          </> : null}
          {overlay === 'weather' ? <TripWeatherPanel date={loaded ? (weatherDayFor(loaded.days, localToday())?.date ?? null) : undefined} items={loaded ? weatherDayFor(loaded.days, localToday())?.items : undefined} /> : null}
        </TripOverlay>
      ) : null}
      <DropdownMenu visible={menu.open} anchor={menu.anchor} items={menuItems} onClose={menu.close} />
    </View>
  );
}

// ── 부품 ────────────────────────────────────────────────────────────────────

/** 시안: 열린 알약은 #191919 바탕 · 흰 글자. */
function HeadPill({ label, active = false, onPress }: { label: string; active?: boolean; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" accessibilityState={{ expanded: active }} onPress={onPress} style={({ pressed }) => [styles.headPill, active && styles.headPillOn, pressed && styles.pressed]}>
      <Text variant="caption" weight="bold" color={active ? color.text.onAction : undefined}>{label}</Text>
    </Pressable>
  );
}

function NumberDot({ n, size = 24 }: { n: number; size?: number }) {
  return (
    <View style={[styles.numberDot, { width: size, height: size }]}>
      <Text variant="micro" weight="bold" color={color.text.onAction}>{n}</Text>
    </View>
  );
}

/**
 * 추천 코스 알약 — 오는 코스 수만큼만 칸을 만든다. 🔴 셋을 그리고 둘을 비워 두지 않는다 —
 * 빈 칸은 눌러도 아무 일이 없어 고장으로 읽힌다(서버가 지금은 한 안만 준다).
 */
function CoursePill({ courses, index, onPick, tx }: { courses: TripCourse[]; index: number; onPick: (index: number) => void; tx: Tx }) {
  const x = useRef(new Animated.Value(index * COURSE_SLOT)).current;
  useEffect(() => {
    Animated.timing(x, { toValue: index * COURSE_SLOT, duration: 360, easing: SLIDE, useNativeDriver: false }).start();
  }, [index, x]);
  return (
    <View accessibilityRole="tablist" style={styles.track}>
      <Animated.View style={[styles.courseIndicator, { transform: [{ translateX: x }] }]} />
      {courses.map((entry, i) => {
        const on = i === index;
        return (
          <Pressable key={entry.id} accessibilityRole="tab" accessibilityState={{ selected: on }} onPress={() => onPick(i)} style={styles.courseSlot}>
            <Text variant="caption" weight="bold" numberOfLines={1} color={on ? color.text.onAction : color.text.body}>
              {`${on ? '✓ ' : ''}${txf(tx, '코스 %s', 'Course %s', courseLetter(i))}`}
              {entry.summary.costKrw !== null ? <Text variant="caption" color={on ? color.text.onDarkMuted : color.text.body}>{` · ${txf(tx, '%s 예상', '%s est.', formatManwon(entry.summary.costKrw, tx))}`}</Text> : null}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

/** 「장소 카드 | 큰 지도」 — 초대·날씨를 열어도 이 선택은 그대로다(시안 README Interactions). */
function ViewSwitch({ layout, onChange, tx }: { layout: Layout; onChange: (next: Layout) => void; tx: Tx }) {
  const x = useRef(new Animated.Value(layout === 'map' ? VIEW_SLOT : 0)).current;
  useEffect(() => {
    Animated.timing(x, { toValue: layout === 'map' ? VIEW_SLOT : 0, duration: 320, easing: SLIDE, useNativeDriver: false }).start();
  }, [layout, x]);
  return (
    <View accessibilityRole="tablist" style={[styles.track, styles.viewSwitch]}>
      <Animated.View style={[styles.viewIndicator, { transform: [{ translateX: x }] }]} />
      {(['cards', 'map'] as const).map((key) => (
        <Pressable key={key} accessibilityRole="tab" accessibilityState={{ selected: layout === key }} onPress={() => onChange(key)} style={styles.viewSlot}>
          <Text variant="caption" weight="bold" color={layout === key ? color.text.onAction : color.text.body}>{key === 'cards' ? `▦ ${tx('장소 카드', 'Place cards')}` : `⌖ ${tx('큰 지도', 'Big map')}`}</Text>
        </Pressable>
      ))}
    </View>
  );
}

/** 한 줄 카드 수 — 넓은 판은 넷. 쌓았을 때는 폭에 맞춰 넷·셋·둘(카드 하나가 150 아래로 줄지 않게) */
export function cardColumns(gridWidth: number, stacked: boolean): number {
  if (!stacked) return 4;
  if (gridWidth >= 640) return 4;
  return gridWidth >= 460 ? 3 : 2;
}

function PlaceCard({ item, startKind, index, items, width, photo, selected, risky, onPress, tx, locale }: {
  item: ItineraryItemDto; startKind: DayStart['kind']; index: number; items: ItineraryItemDto[]; width: number; photo: PlacePhoto | null;
  selected: boolean; risky: boolean; onPress: () => void; tx: Tx; locale: string;
}) {
  const label = photo?.category ? PLACE_CATEGORY_LABELS[photo.category] : undefined;
  const category = label ? tx(label[0], label[1]) : null;
  const { language } = useI18n();
  const nameParts = stopNameParts(item.title, photo?.nameEn, language);
  const leg = formatTravelLabel(item, tx, index === 0 && startKind);
  const reason = pickReasonLine(item.reasonCodes);
  const stay = stayMinutes(items, index);
  const free = freeTimeMinutes(items, index);
  const last = index === items.length - 1;
  return (
    <Pressable accessibilityRole="button" accessibilityState={{ selected }} onPress={onPress} style={[styles.card, { width }, selected && styles.selectedBorder]}>
      <View style={styles.rowCenter}>
        <NumberDot n={index + 1} />
        <Text weight="bold">{stopClock(item.startsAt) ?? tx('미정', 'TBD')}</Text>
        {category ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{category}</Text> : null}
      </View>
      <View style={styles.cardName}>
        <Text variant="title" weight="bold" numberOfLines={2}>{item.title}</Text>
        {/* 영어(일·중) 화면 — 한글 아래에 영어 이름이나 로마자 한 줄(S15P21E201-1735). 넘치면 이 줄만 줄임표로 자른다 — 한글은 안 자른다. */}
        {nameParts.other ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{nameParts.other}</Text> : null}
        {leg ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{leg}</Text> : null}
        {/* 왜 이 곳인지 한 줄 — 없으면 줄째 안 그린다(S15P21E201-1645). */}
        {reason ? <Text variant="caption" color={color.text.body} numberOfLines={1}>{reason}</Text> : null}
      </View>
      <View style={styles.cardImage}>
        {/* 🔴 서버 사진이 있으면 PlaceVisual 로 그린다 — 출처 띠(공공누리 등)를 빼면 이용 조건 위반이다. */}
        {photo?.photoUrl ? <PlaceVisual name={item.title} address={null} photoUrl={photo.photoUrl} photoSource={photo.photoSource} photoLicense={photo.photoLicense} style={styles.fill} /> : <Text style={styles.glyph}>{categoryGlyph(photo?.category)}</Text>}
        <View style={styles.cardChips}>
          {item.dataStatus === 'ESTIMATED' ? <View style={styles.chipEstimated}><Text variant="micro" weight="bold" color={color.state.warning}>{tx('추정', 'Estimated')}</Text></View> : null}
          {risky ? <View style={styles.chipRisk}><Text variant="micro" weight="bold" color={color.state.danger}>{tx('하루 넘길 위험', 'May run past the day')}</Text></View> : null}
        </View>
      </View>
      <View style={styles.cardMoney}>
        <Text weight="bold">{typeof item.estimatedCostKrw !== 'number' ? tx('비용 미정', 'Cost unknown') : item.estimatedCostKrw === 0 ? tx('무료', 'Free') : txf(tx, '%s원', '₩%s', item.estimatedCostKrw.toLocaleString(locale))}</Text>
        <Text variant="caption" color={color.text.body}>{last ? tx('마지막 장소', 'Last stop') : stay !== null ? txf(tx, '머무름 약 %s', 'Stay about %s', formatDuration(stay, tx)) : tx('머무름 시간 모름', 'Stay time unknown')}</Text>
        {/* 이 곳을 떠나 다음 곳에 가기까지 남는 시간(S15P21E201-1668). 머무름과 따로 적는다 — 섞으면 머무름이 길어 보인다. */}
        <FreeTimeRow minutes={free} tx={tx} />
      </View>
    </Pressable>
  );
}

function LoadingState({ tx }: { tx: Tx }) {
  return (
    <View accessibilityLabel={tx('여행을 불러오고 있어요', 'Loading your trip')} style={[styles.shell, styles.content]}>
      <Skeleton height={56} radius={radius.lg} />
      <View style={styles.body}><View style={styles.left}><Skeleton height={420} radius={radius.lg} /></View><Skeleton width={MAP_WIDTH} height={420} radius={radius.lg} /></View>
    </View>
  );
}

function ErrorState({ message, onRetry, tx }: { message: string; onRetry: () => void; tx: Tx }) {
  return (
    <View style={[styles.shell, styles.content]}>
      <View style={styles.errorCard}>
        <Text variant="title" weight="bold">{tx('여행을 불러오지 못했어요', 'Could not load the trip')}</Text>
        <Text color={color.text.body}>{message}</Text>
        <Pressable accessibilityRole="button" onPress={onRetry} style={({ pressed }) => [styles.headPill, styles.retry, pressed && styles.pressed]}>
          <Text variant="caption" weight="bold">{tx('다시 시도', 'Try again')}</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.canvas },
  scroll: { flex: 1 },
  // 시안: 머리 padding 20 40 12 · 코스 줄 0 40 16 · 본문 0 40 32. 좌우 40 은 desktopGutter.
  content: { paddingHorizontal: desktopGutter, paddingTop: 20, paddingBottom: spacing[8], gap: spacing[3] },
  pressed: { opacity: 0.72 },
  // 창 오른쪽 위 닫기(✕ — 창 끝에서 16+40)와 겹치지 않게 비운다. 창 속 여백 24 에 32 를 더하면 56.
  recordTitle: { marginBottom: spacing[4], paddingRight: spacing[8] },
  shrink: { flexShrink: 1 },
  fill: { width: '100%', height: '100%' },
  rowCenter: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },

  // 🔴 좁으면 단추 묶음이 다음 줄로 — 제목 칸이 360 아래로 줄지 않는다(S15P21E201-1953, 240→360 은 S15P21E201-1965: 탭 세로에서 날짜 제목이 잘렸다).
  head: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: spacing[4], zIndex: 5 },
  headCopy: { flexGrow: 1, flexShrink: 1, flexBasis: 360, minWidth: 0, gap: 2 },
  headActions: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: spacing[4], marginLeft: 'auto' },
  noShrink: { flexShrink: 0 },
  circle44: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  circle40: { width: 40, height: 40, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  headPill: { minHeight: 40, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  headPillOn: { backgroundColor: color.action.secondary },

  // 좁으면 줄을 바꾼다 — 안 바꾸면 「おすすめコース」가 한 글자씩 세로로 찌그러졌다(S15P21E201-1953).
  courseRow: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: spacing[2], paddingTop: spacing[2] },
  track: { flexDirection: 'row', padding: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
  courseIndicator: { position: 'absolute', top: spacing[1], bottom: spacing[1], left: spacing[1], width: COURSE_SLOT, borderRadius: radius.full, backgroundColor: color.brand.navy },
  courseSlot: { width: COURSE_SLOT, minHeight: 40, paddingHorizontal: spacing[3], alignItems: 'center', justifyContent: 'center', borderRadius: radius.full },
  // 🔴 이 화면의 동백 채움은 이 단추 하나다(tokens 규칙 1). 코스 알약·보기 전환의 고른 칸은 먹색이다.
  confirmButton: { minHeight: 40, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.action.primary, justifyContent: 'center' },
  confirmedPill: { flexDirection: 'row', alignItems: 'center', minHeight: 40, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card },
  textButton: { minHeight: 40, paddingHorizontal: spacing[3], justifyContent: 'center' },
  viewSwitch: { marginLeft: 'auto' },
  viewIndicator: { position: 'absolute', top: spacing[1], bottom: spacing[1], left: spacing[1], width: VIEW_SLOT, borderRadius: radius.full, backgroundColor: color.action.secondary },
  viewSlot: { width: VIEW_SLOT, minHeight: 36, alignItems: 'center', justifyContent: 'center' },

  dayRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  dayChip: { minHeight: 36, paddingHorizontal: spacing[4], justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.card },
  dayChipOn: { backgroundColor: color.action.secondary },

  body: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[6], paddingTop: spacing[1] },
  left: { flex: 1, minWidth: 0, gap: spacing[4] },
  // 카드 열은 카드 넷이 CARD_MAX_WIDTH 에 닿는 폭에서 멈춘다. 그보다 좁으면(시안 1440) 줄어들고 지도는 440 을 지킨다.
  leftCapped: { flexGrow: 1, flexShrink: 1, flexBasis: CARD_MAX_WIDTH * 4 + spacing[3] * 3, maxWidth: CARD_MAX_WIDTH * 4 + spacing[3] * 3 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3] },
  // 지도는 440 아래로 안 줄고, 카드 열이 멈춘 뒤 남는 폭을 전부 가져간다.
  bodyStacked: { flexDirection: 'column', alignItems: 'stretch' },
  // 🔴 flex:1(기본 0) 을 풀어야 한다 — 세로로 쌓으면 그 값이 높이를 0 으로 줄여 지도가 카드 위에 겹쳤다
  leftStacked: { flexGrow: 0, flexShrink: 0, flexBasis: 'auto', width: '100%' },
  mapStacked: { width: '100%' },
  mapColumn: { flexGrow: 1, flexShrink: 0, flexBasis: MAP_WIDTH, minWidth: MAP_WIDTH },

  // 카드 — 흰색 radius 20 padding 10, 고른 것은 붉은 2px 선(시안). 안 고른 것도 2px 자리를 흰색으로 둬 흔들리지 않게.
  card: { padding: 10, gap: 10, borderRadius: radius.lg, borderWidth: 2, borderColor: color.surface.card, backgroundColor: color.surface.card },
  selectedBorder: { borderColor: color.action.outline },
  numberDot: { borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' },
  cardName: { gap: spacing[1], minHeight: 48 },
  cardImage: { aspectRatio: 1, borderRadius: radius.md, overflow: 'hidden', backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' },
  glyph: { fontSize: 44, lineHeight: 52 },
  cardChips: { position: 'absolute', left: spacing[2], top: spacing[2], flexDirection: 'row', gap: spacing[1] },
  chipEstimated: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, backgroundColor: color.state.warningBg },
  chipRisk: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, backgroundColor: color.state.dangerBg },
  cardMoney: { gap: 2 },

  summaryRow: { flexDirection: 'row', alignItems: 'stretch', gap: spacing[3] },
  summaryCard: { padding: spacing[4], gap: 6, borderRadius: radius.lg, backgroundColor: color.surface.card },
  summaryFlexBudget: { flex: 1.2 },
  summaryFlex1: { flex: 1 },

  mapPanel: { borderRadius: radius.lg, overflow: 'hidden', backgroundColor: color.surface.soft },
  mapEmpty: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[6] },
  mapSummary: {
    position: 'absolute', left: spacing[3], top: spacing[3], paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.card,
    shadowColor: color.brand.navy, shadowOpacity: 0.08, shadowRadius: 8, shadowOffset: { width: 0, height: 2 }, elevation: 2,
  },
  // 색 범례 — 요약 칩 아래 왼쪽 위. 🔴 왼쪽 아래에 두지 않는다: 카카오 지도가 그 자리에 로고와 축척을 그리고, 가리면 이용약관을 어긴다.
  mapLayers: { position: 'absolute', left: spacing[3], right: spacing[3], top: spacing[3] + 34 + spacing[2] },
  mapExpand: {
    position: 'absolute', right: spacing[3], top: spacing[3], width: 40, height: 40, borderRadius: radius.md, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center',
    shadowColor: color.brand.navy, shadowOpacity: 0.08, shadowRadius: 8, shadowOffset: { width: 0, height: 2 }, elevation: 2,
  },

  bigList: { width: BIG_LIST_WIDTH, gap: spacing[2] },
  bigFree: { marginLeft: spacing[3] },
  selectedLeg: { flexDirection: 'row', alignSelf: 'stretch' },
  bigLeg: { flexDirection: 'row', marginLeft: spacing[6], alignSelf: 'stretch' },
  bigLegText: { marginLeft: spacing[6] },
  bigRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.lg, borderWidth: 2, borderColor: color.surface.card, backgroundColor: color.surface.card },
  bigThumb: { width: 48, height: 48, borderRadius: radius.md, overflow: 'hidden', backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' },
  bigCopy: { flex: 1, minWidth: 0, gap: 2 },
  lineNote: { marginTop: spacing[1], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  bigMap: { flex: 1, minWidth: 0 },

  errorCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  retry: { alignSelf: 'flex-start', borderWidth: 1, borderColor: color.surface.border },
});
