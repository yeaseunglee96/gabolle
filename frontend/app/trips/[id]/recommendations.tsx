// 코스 고르기 — **전체 일정 3안을 견준다** (시안 ③, 인계 §4).
//
// 🔴 전에는 장소 후보 목록이었다. 장소를 고르는 것과 일정을 고르는 것은 사람이 하는 판단
//    자체가 다르다 — 앞은 「여기 갈까」이고 뒤는 「이렇게 다닐까」다. 그래서 옛 「추천 일정
//    요약」 화면은 없어지고, 이 자리는 코스 비교가 된다.
//
// 🔴 필터(예산·이동 적게·휠체어)는 **없다**(인계 §7③). 조건은 ① 에서 이미 받았다. 여기서
//    또 물으면 앞에서 답한 것이 반영되지 않았다는 뜻이 된다.
import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Animated, Easing, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { LinearGradient } from 'expo-linear-gradient';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useAuth } from '@/auth/AuthProvider';
import { Screen } from '@/components/Screen';
import { Skeleton } from '@/components/Skeleton';
import { TabBar, TAB_BAR_HEIGHT, TAB_BAR_SHEET_HEIGHT, BAR_MAX_WIDTH, SHEET_MAX_WIDTH, tabBarBottomMargin } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { resolveTextLanguage } from '@/i18n/languages';
import { useLayout } from '@/layout/useLayout';
import { TripPageDesktop } from '@/trip/page/TripPageDesktop';
import { TripPageMobile } from '@/trip/page/TripPageMobile';
import { tripPageKind } from '@/trip/page/tripPageModel';
import { RouteMap } from '@/map/RouteMap';
import { CourseCard, CourseRow, courseCost, courseFacts, courseLetter } from '@/plan/CourseCard';
import { courseMapLayers, dayColor } from '@/plan/courseMap';
import { useCourseRoutePaths } from '@/map/courseRoutePaths';
import { findLatestRecommendationJob, loadRecommendationResult } from '@/plan/recommendations';
import { loadTripCourses, type TripCourse, type TripCoursesResult } from '@/plan/tripCourses';
import { shouldAskTripName, wasTripNameAsked } from '@/trip/tripNaming';
import { timeToMinutes } from '@/plan/tripBasics';
import { loadTrips } from '@/trip/trips';
import { localizeMessage } from '@/i18n/messages';

type Loaded = { state: 'loading' } | { state: 'ready'; result: TripCoursesResult };

/** 접힌 줄의 높이. 제목 한 줄 + 요약 한 줄이라 내용과 무관하게 일정하다 (시안 2절). */
const COLLAPSED_HEIGHT = 64;

/** 정차지 카드 한 장과 그 사이 연결부의 폭 — 손잡이가 한 번에 옮길 거리를 이 둘로 센다. */
const STRIP_CARD_WIDTH = 150;
const STRIP_LINK_WIDTH = 44;

/**
 * 코스 한 자리 — 카드로 펼쳐져 있거나 한 줄로 접혀 있다. **둘 사이를 잇는다.**
 *
 * 🔴 왜 둘 다 띄워 두나. 접힐 때 «펼친 높이» 를 알아야 거기서부터 줄일 수 있는데, 그
 *    높이는 카드를 실제로 그려 봐야만 안다. 접을 때 카드를 걷어내면 잴 것이 사라져서
 *    높이가 툭 끊긴다. 그래서 카드는 늘 그려 두고 «보이지 않게» 만 한다.
 *
 * 🔴 안 보이는 쪽은 눌리지도, 읽히지도 않아야 한다. 투명도만 0으로 두면 손가락이 안
 *    보이는 단추를 누르고 화면 읽기 프로그램은 둘 다 읽는다 — 탭바가 시트로 자랄 때
 *    쓰는 것과 같은 처리다.
 */
function CourseSlot({ collapsed, card, row }: { collapsed: boolean; card: React.ReactNode; row: React.ReactNode }) {
  const [cardHeight, setCardHeight] = useState(0);
  const anim = useRef(new Animated.Value(collapsed ? 0 : 1)).current;
  // 높이를 아직 못 쟀으면 애니메이션을 걸지 않는다 — 0에서 시작하면 첫 그림에서 접혔다
  // 펴지는 것처럼 보인다.
  const ready = cardHeight > 0;

  useEffect(() => {
    if (!ready) return;
    Animated.timing(anim, {
      toValue: collapsed ? 0 : 1,
      duration: 420,
      easing: Easing.bezier(0.34, 1.3, 0.64, 1),
      // 높이를 바꾸므로 네이티브 드라이버를 못 쓴다.
      useNativeDriver: false,
    }).start();
  }, [collapsed, ready, anim]);

  const height = ready
    ? anim.interpolate({ inputRange: [0, 1], outputRange: [COLLAPSED_HEIGHT, cardHeight] })
    : undefined;

  return (
    <Animated.View style={[styles.slot, ready ? { height } : null]}>
      <Animated.View
        pointerEvents={collapsed ? 'none' : 'auto'}
        accessibilityElementsHidden={collapsed}
        importantForAccessibility={collapsed ? 'no-hide-descendants' : 'auto'}
        style={{ opacity: anim }}
        onLayout={(event) => {
          const next = Math.round(event.nativeEvent.layout.height);
          // 접히는 동안에는 재지 않는다 — 줄어드는 높이를 «펼친 높이» 로 기억해 버린다.
          if (!collapsed && next > 0 && next !== cardHeight) setCardHeight(next);
        }}
      >
        {card}
      </Animated.View>
      <Animated.View
        pointerEvents={collapsed ? 'auto' : 'none'}
        accessibilityElementsHidden={!collapsed}
        importantForAccessibility={collapsed ? 'auto' : 'no-hide-descendants'}
        style={[styles.slotRow, { opacity: anim.interpolate({ inputRange: [0, 1], outputRange: [1, 0] }) }]}
      >
        {row}
      </Animated.View>
    </Animated.View>
  );
}

/**
 * 넓은 화면(1024~)은 여행 페이지 통합 화면을 연다 — 코스 고르기가 그 화면의 「추천 코스」 알약이 된다
 * (S15P21E201-1535, 시안 frontend/docs/design_handoff_trip_page/). 폰은 같은 화면의 폰 판(TripPageMobile, 2단계)이고,
 * 1024 에 못 미치는 태블릿만 아래 그대로다 — 규칙은 tripPageKind(src/trip/page/tripPageModel.ts)가 갖는다.
 */
export default function Recommendations() {
  const { width, kind } = useLayout();
  const { id, jobId } = useLocalSearchParams<{ id: string; jobId?: string }>();
  const page = id ? tripPageKind(width, kind, false) : 'classic';
  if (id && page === 'desktop') return <TripPageDesktop source={{ kind: 'trip', tripId: id, jobId: jobId ?? null }} />;
  if (id && page === 'mobile') return <TripPageMobile source={{ kind: 'trip', tripId: id, jobId: jobId ?? null }} />;
  return <RecommendationsClassic />;
}

function RecommendationsClassic() {
  const router = useRouter();
  const { accessToken } = useAuth();
  const { tx, language } = useI18n();
  const { kind } = useLayout();
  const insets = useSafeAreaInsets();
  const wide = kind !== 'phone';
  const ko = resolveTextLanguage(language) === 'ko';
  const { id, jobId } = useLocalSearchParams<{ id: string; jobId?: string }>();
  const tripId = id ?? '';

  const [loaded, setLoaded] = useState<Loaded>({ state: 'loading' });
  const [picked, setPicked] = useState<string | null>(null);
  // 🔴 코스 바는 «탭바 자리»에 서고, 눌러 «시트로 자란다». 탭바가 이미 그 동작을 갖고
  //    있어(`expanded`) 같은 시간·같은 곡선을 쓴다 — 한 앱 안에서 자라는 속도가 두 가지면
  //    같은 동작으로 안 읽힌다.
  const [sheetOpen, setSheetOpen] = useState(false);
  const [sheetDay, setSheetDay] = useState(1);
  const grow = useRef(new Animated.Value(0)).current;
  /** 넓은 화면에서 지도가 쓸 수 있는 높이 — 숫자로 적지 않고 «재서» 쓴다. */
  const [mapHeight, setMapHeight] = useState(0);
  /** 시트 안에서 지도가 쓸 수 있는 높이 — 넓은 화면과 같은 방식으로 «잰다». */
  const [sheetMapHeight, setSheetMapHeight] = useState(0);
  /** 고르기 전/후 두 벌을 바꿔 흐리는 값. 0이면 「골라 주세요」, 1이면 고른 코스. */
  const pickFade = useRef(new Animated.Value(0)).current;
  /**
   * 흐려지는 «동안» 보여 줄 코스. 고르기를 물러도(다시 눌러 해제) 마지막 것을 붙들고
   * 있어야 글자가 먼저 사라지고 칸만 남는 일이 없다.
   */
  const [shown, setShown] = useState<TripCourse | null>(null);
  const stripRef = useRef<ScrollView>(null);
  /** 스트립이 칸보다 넓은가(넘치는가) · 지금 얼마나 굴렀나 — 둘 다 재서 안다. */
  const [strip, setStrip] = useState({ view: 0, content: 0, left: 0 });
  const [saved, setSaved] = useState<Record<string, boolean>>({});

  const load = useCallback(async () => {
    setLoaded({ state: 'loading' });
    // 코스 계약이 아직 없을 때 대신 보여 줄 일정을 찾아 둔다. 생성 작업에서 온 경우
    // jobId 가 있고, 내 여행에서 들어온 경우 가장 최근 작업을 찾는다.
    let itineraryId: string | null = null;
    let job: string | null = jobId ?? null;
    if (!job && tripId) {
      const lookup = await findLatestRecommendationJob(tripId, accessToken);
      // 🔴 「없음」·「못 찾음」에는 번호 칸이 아예 없다. 있다고 치고 읽으면 undefined 가
      //    주소에 박혀 엉뚱한 자리를 부른다.
      job = 'jobId' in lookup ? lookup.jobId : null;
    }
    if (job) {
      const result = await loadRecommendationResult(job, accessToken);
      itineraryId = result.itineraryId;
    }
    setLoaded({ state: 'ready', result: await loadTripCourses(tripId, itineraryId, accessToken) });
  }, [accessToken, jobId, tripId]);

  useEffect(() => { void load(); }, [load]);

  const courses: TripCourse[] = loaded.state === 'ready' && loaded.result.state === 'success' ? loaded.result.courses : [];
  const full = loaded.state === 'ready' && loaded.result.state === 'success' ? loaded.result.full : true;

  // 🔴 처음 한 안을 미리 고르지 않는다. 고른 것처럼 보이면 사람은 견주지 않고 그냥 누른다.
  //    다만 안이 하나뿐이면 고를 것이 없으므로 그것을 고른 것으로 둔다.
  useEffect(() => {
    if (picked === null && courses.length === 1) setPicked(courses[0].id);
  }, [courses, picked]);

  const current = courses.find((course) => course.id === picked) ?? null;
  // 🔴 고른 코스가 바뀔 때만 다시 만든다. 매번 새 배열을 만들면 지도가 그때마다 다시
  //    그려지고, 실제로 그 자리에서 무한 재렌더가 났던 적이 있다(RouteMap 주석 참고).
  // 🔴 구간 경로를 먼저 받아 두고 지도에 넘긴다. 받는 동안에는 빈 값이라 지도가 **먼저
  //    직선으로 그려지고**, 경로가 오는 대로 실선으로 바뀐다. 다 받을 때까지 지도를 비워
  //    두지 않는다 — 빈 지도가 직선보다 낫지 않다.
  const courseDays = useMemo(() => courseMapLayers(current).stops.length
    ? (current?.days ?? []).map((day) => ({
        day: day.day,
        stops: day.stops
          .filter((stop) => stop.lat !== null && stop.lng !== null)
          .map((stop, index) => ({ id: `${day.day}-${index + 1}`, number: index + 1, name: stop.name, latitude: stop.lat as number, longitude: stop.lng as number })),
      }))
    : [], [current]);
  const legPaths = useCourseRoutePaths(courseDays, accessToken);
  const mapLayers = useMemo(() => courseMapLayers(current, legPaths), [current, legPaths]);
  const [selectedStopId, setSelectedStopId] = useState('');

  useEffect(() => {
    if (current) setShown(current);
    Animated.timing(pickFade, {
      toValue: current ? 1 : 0,
      duration: 200,
      easing: Easing.out(Easing.quad),
      useNativeDriver: false,
    }).start();
  }, [current, pickFade]);

  useEffect(() => {
    Animated.timing(grow, {
      toValue: sheetOpen ? 1 : 0,
      duration: 420,
      easing: Easing.bezier(0.34, 1.3, 0.64, 1),
      // 높이를 바꾸므로 네이티브 드라이버를 못 쓴다. 이 바는 화면에 하나뿐이라 괜찮다.
      useNativeDriver: false,
    }).start();
  }, [sheetOpen, grow]);

  // 고른 코스가 바뀌면 시트는 닫고 1일차로 돌아간다. 열린 채로 내용만 갈리면
  // 무엇을 보고 있는지 알 수 없다.
  useEffect(() => { setSheetOpen(false); setSheetDay(1); setSelectedStopId(''); }, [picked]);

  // 🔴 일차나 코스가 바뀌면 스트립을 **처음으로** 되돌린다. 안 그러면 3일차를 보다가
  //    1일차로 옮겼을 때 「없는 뒤쪽」을 보고 있게 된다 — 화면은 비었는데 스크롤만 가 있다.
  useEffect(() => {
    stripRef.current?.scrollTo({ x: 0, animated: false });
    setStrip((prev) => ({ ...prev, left: 0 }));
  }, [picked, sheetDay]);

  // 🔴 넓은 화면의 정차지 스트립은 «옆으로» 굴러가는데, 마우스 휠은 «아래로» 굴린다.
  //    그대로 두면 정차지가 많은 날에 뒤쪽 몇 곳을 볼 방법이 트랙패드밖에 없다.
  //    세로 휠을 가로 스크롤로 옮긴다.
  useEffect(() => {
    if (Platform.OS !== 'web') return;
    const node = (stripRef.current as unknown as { getScrollableNode?: () => HTMLElement } | null)?.getScrollableNode?.();
    if (!node) return;
    const onWheel = (event: WheelEvent) => {
      // 가로로 굴리고 있으면 브라우저에 맡긴다 — 트랙패드는 두 방향을 같이 보낸다.
      if (Math.abs(event.deltaY) <= Math.abs(event.deltaX)) return;
      node.scrollLeft += event.deltaY;
      event.preventDefault();
    };
    node.addEventListener('wheel', onWheel, { passive: false });
    return () => node.removeEventListener('wheel', onWheel);
  }, [picked, sheetDay, wide]);

  /** 한 번에 카드 두 장만큼 옮긴다 — 시안 3절. */
  const nudgeStrip = (direction: 1 | -1) => {
    const step = (STRIP_CARD_WIDTH + STRIP_LINK_WIDTH) * 2;
    const next = Math.max(0, Math.min(strip.content - strip.view, strip.left + step * direction));
    stripRef.current?.scrollTo({ x: next, animated: true });
  };

  // 시트 지도는 «고른 하루»만 그린다. 정차지 id 는 `{일차}-{번째}`, 구간 id 는
  // `day-{일차}-leg-{번째}` 라 일차로 거를 수 있다 — 색은 원래 것을 그대로 쓴다.
  const dayLayers = useMemo(() => ({
    stops: mapLayers.stops.filter((stop) => stop.id.startsWith(`${sheetDay}-`)),
    routes: mapLayers.routes.filter((route) => route.id.startsWith(`day-${sheetDay}-`)),
  }), [mapLayers, sheetDay]);

  const build = async (course: TripCourse) => {
    // 🔴 「코스를 골랐다」는 이벤트를 안 보낸다. 서버가 받는 종류가 넷으로 정해져 있고,
    //    없는 종류를 만들어 보내면 그 줄은 조용히 버려진다 — 재는 줄 알고 안 재게 된다.
    const itineraryId = course.itineraryId;
    if (!itineraryId) return;
    const target = `/trips/${itineraryId}/itinerary`;
    try {
      const [trips, alreadyAsked] = await Promise.all([loadTrips(accessToken), wasTripNameAsked(tripId)]);
      const title = trips.state === 'success' ? trips.trips.find((trip) => trip.tripId === tripId)?.title : null;
      // 코스를 고른 직후에만 이름 묻기가 열린 채로 들어간다 — 시안 ④.
      if (shouldAskTripName({ title, alreadyAsked })) { router.push(`${target}?name=1`); return; }
    } catch {
      // 물어볼지 정하다 실패하면 묻지 않고 지나간다. 일정을 보러 가는 길을 막지 않는다.
    }
    router.push(target);
  };

  const header = (
    <View style={styles.head}>
      <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('추천 코스 고르기', 'Pick a course')}</Text>
      <Text variant="hero" weight="bold" color={color.text.heading}>
        {courses.length > 1
          ? tx(`${courses.length}가지 코스`, `${courses.length} courses`)
          : tx('추천 코스', 'Your course')}
      </Text>
      {/* 🔴 한 안뿐인 이유를 말한다. 조용히 하나만 그리면 사용자는 비교를 놓친 줄도 모른다. */}
      {full ? null : (
        <Text variant="caption" color={color.text.muted}>
          {tx('지금은 만들어진 일정 하나만 보여 드려요. 세 가지 코스 비교는 준비 중이에요.',
            'Only the itinerary we built is shown for now — comparing three courses is on the way.')}
        </Text>
      )}
    </View>
  );

  const list = (
    <View style={styles.list}>
      {header}
      {loaded.state === 'loading' ? (
        <>
          <Skeleton height={200} />
          <Skeleton height={200} />
        </>
      ) : loaded.result.state !== 'success' ? (
        <View style={styles.stateCard}>
          <Text color={color.text.body}>{localizeMessage(tx, loaded.result.message)}</Text>
          <Pressable accessibilityRole="button" onPress={() => void load()} style={styles.retry}>
            <Text weight="bold" color={color.brand.navy}>{tx('다시 시도', 'Try again')}</Text>
          </Pressable>
        </View>
      ) : (
        courses.map((course, index) => (
          // 🔴 고른 뒤에는 나머지를 «한 줄» 로 접는다 — 고른 뒤에 할 일은 그 안을 들여다보는
          //    것이지 다시 견주는 것이 아니다. 지우지는 않는다(다시 고를 길을 남긴다).
          //    아직 아무것도 안 골랐을 때는 셋 다 펼쳐 둔다 — 그때는 견주는 것이 할 일이다.
          <CourseSlot
            key={course.id}
            collapsed={Boolean(picked) && courses.length > 1 && course.id !== picked}
            card={(
              <CourseCard
                course={course}
                index={index}
                selected={course.id === picked}
                saved={Boolean(saved[course.id])}
                onSelect={() => setPicked(course.id)}
                onToggleSave={() => setSaved((prev) => ({ ...prev, [course.id]: !prev[course.id] }))}
                onBuild={() => void build(course)}
                tx={tx}
                ko={ko}
              />
            )}
            row={(
              <CourseRow
                course={course}
                selected={false}
                saved={Boolean(saved[course.id])}
                onSelect={() => setPicked(course.id)}
                onToggleSave={() => setSaved((prev) => ({ ...prev, [course.id]: !prev[course.id] }))}
                tx={tx}
              />
            )}
          />
        ))
      )}
    </View>
  );

  if (wide) {
    const dayStops = current?.days.find((day) => day.day === sheetDay)?.stops ?? [];
    // 🔴 4px 은 재는 오차를 넘기기 위한 여유다. 소수점 한 자리 때문에 「넘친다」고
    //    판정하면 다 보이는 화면에 화살표가 뜬다.
    const stripOverflows = strip.content > strip.view + 4;
    const stripPages = stripOverflows && strip.view > 0 ? Math.ceil(strip.content / strip.view) : 0;
    const stripPage = strip.view > 0 ? Math.round(strip.left / strip.view) : 0;
    const stripDayIndex = Math.max(0, current?.days.findIndex((day) => day.day === sheetDay) ?? 0);
    return (
      <View style={[styles.shell, styles.shellWide]}>
        <ScrollView style={styles.listPane} contentContainerStyle={styles.listPaneInner}>{list}</ScrollView>
        {/* 오른쪽은 **지도가 주인**이다 — 시안 3절.
            🔴 전에는 위에 «코스 칩», 아래에 «코스 내용» 글상자가 있고 지도는 가운데 640px 짜리
               한 칸이었다. 코스는 이미 왼쪽 카드에서 고르고 내용도 거기 적혀 있으니, 같은 것을
               오른쪽에 또 두면 화면의 절반이 «이미 읽은 것» 이 된다. 오른쪽은 왼쪽이 못 하는
               것만 한다 — 어디를 어떤 순서로 가는지. */}
        <View style={styles.mapPane}>
          {/* 코스 칩이 아니라 **일차 칩**. 고른 코스가 없으면 고를 일차도 없다. */}
          {current && current.days.length > 1 ? (
            <View style={styles.mapLegend}>
              {current.days.map((day) => (
                <Pressable
                  key={day.day}
                  accessibilityRole="button"
                  accessibilityState={{ selected: day.day === sheetDay }}
                  onPress={() => { setSheetDay(day.day); setSelectedStopId(''); }}
                  style={[styles.legendChip, day.day === sheetDay && styles.legendChipOn]}
                >
                  <Text variant="caption" weight="bold" color={day.day === sheetDay ? color.text.onAction : color.text.heading} numberOfLines={1}>
                    {tx(`${day.day}일차`, `Day ${day.day}`)}
                  </Text>
                </Pressable>
              ))}
            </View>
          ) : null}

          {/* 🔴 지도 높이를 숫자로 적지 않는다. 남는 자리를 «재서» 그만큼 쓴다 — 640 으로
              박아 두면 큰 화면에서는 아래가 남고 작은 화면에서는 스트립이 잘린다. */}
          <View style={styles.mapArea} onLayout={(event) => setMapHeight(event.nativeEvent.layout.height)}>
            {/* 🔴 좌표가 하나라도 오면 **선으로** 그린다 (-1333). 하나도 없으면 아래처럼
                **동선을 글로** 세운다 — 좌표 없이 선을 그으면 실제로 안 가는 길을 그리게 되고,
                그건 빈 지도보다 나쁘다. 옛 서버에 붙은 앱이 그 상태다. */}
            {mapHeight > 0 && dayLayers.stops.length ? (
              <RouteMap
                stops={dayLayers.stops}
                selectedId={selectedStopId}
                onSelect={setSelectedStopId}
                routes={dayLayers.routes}
                height={mapHeight}
              />
            ) : (
              <View style={styles.mapEmpty}>
                <View style={styles.mapNote}>
                  <Text variant="caption" color={color.text.muted}>
                    {!current
                      ? tx('코스를 고르면 내용이 여기에 보여요.', 'Pick a course to see what is in it.')
                      : tx('동선 지도는 준비 중이에요. 장소의 좌표가 들어오면 여기에 선으로 그려 드려요.',
                        'The route map is on the way — we will draw it once the stops carry coordinates.')}
                  </Text>
                </View>
              </View>
            )}
            {/* 오른쪽 위 요약 — 「장소 N곳 · 이동 M분」. 지도만 남기면 이 숫자를 볼 곳이 없다. */}
            {current ? (
              <View style={styles.mapSummary}>
                <Text variant="caption" weight="bold" numberOfLines={1}>{courseFacts(current, tx)}</Text>
              </View>
            ) : null}

          {/* 아래 스트립 — 고른 일차의 정차지를 **순서대로** 옆으로 세운다.
              지도의 번호와 같은 번호를 달아, 점을 누르든 카드를 누르든 같은 곳이 켜진다.

              🔴 지도 «아래»가 아니라 **위에 뜬다**(시안 3절 `absolute bottom`). 아래에
                 두면 지도가 그만큼 짧아지는데, 이 화면에서 제일 큰 것은 지도여야 한다.
                 대신 카드가 지도를 가리지 않게 **바탕을 아래로 갈수록 덮는 그라데이션**을
                 깐다 — 위는 지도가 비쳐 보이고 아래는 카드가 또렷하다. */}
          {dayStops.length ? (
            <View style={styles.stripWrap}>
              <LinearGradient
                pointerEvents="none"
                colors={['transparent', color.surface.soft]}
                locations={[0, 0.4]}
                style={styles.stripBackdrop}
              />
            <View style={styles.stripRow}>
            <ScrollView
              ref={stripRef}
              horizontal
              showsHorizontalScrollIndicator={false}
              scrollEventThrottle={16}
              onLayout={(event) => setStrip((prev) => ({ ...prev, view: Math.round(event.nativeEvent.layout.width) }))}
              onContentSizeChange={(width) => setStrip((prev) => ({ ...prev, content: Math.round(width) }))}
              onScroll={(event) => setStrip((prev) => ({ ...prev, left: Math.round(event.nativeEvent.contentOffset.x) }))}
              style={styles.stripPane}
              contentContainerStyle={styles.strip}
            >
              {dayStops.map((stop, index) => {
                const id = `${sheetDay}-${index + 1}`;
                const on = id === selectedStopId;
                // 🔴 두 곳 사이의 «이동 시간» 은 서버가 정차지마다 주지 않는다. 시각의
                //    차로 구한다(시안도 그렇게 적었다). 한쪽이라도 시각을 모르면
                //    **숫자를 짓지 않고** 점선만 긋는다 — 0분이라고 적으면 붙어 있는
                //    곳으로 읽힌다.
                const next = dayStops[index + 1];
                const gap = stop.time && next?.time
                  ? timeToMinutes(next.time) - timeToMinutes(stop.time)
                  : Number.NaN;
                return (
                  <Fragment key={`${id}-${stop.name}`}>
                    <Pressable
                      accessibilityRole="button"
                      accessibilityState={{ selected: on }}
                      onPress={() => setSelectedStopId(on ? '' : id)}
                      // 🔴 고른 카드의 테두리는 **그날의 색**이다 — 지도의 선·마커와 같은 색이라
                    //    「이 카드가 저 점」이라는 것이 색 하나로 이어진다. 빨강을 쓰면 카드의
                    //    선택(코스 고르기)과 같은 뜻으로 읽혀 둘이 헷갈린다.
                    style={[styles.stripCard, on && { borderColor: dayColor(stripDayIndex) }]}
                    >
                      <View style={styles.stripTop}>
                        <View style={styles.stripNumber}>
                          <Text variant="caption" weight="bold" color={color.text.onAction}>{index + 1}</Text>
                        </View>
                        <Text variant="caption" weight="bold" color={color.text.muted} numberOfLines={1}>{stop.time ?? ''}</Text>
                      </View>
                      <Text weight="bold" numberOfLines={1}>{stop.name}</Text>
                    </Pressable>
                    {next ? (
                      <View style={styles.stripLink}>
                        {Number.isFinite(gap) && gap > 0 ? (
                          <Text variant="caption" weight="bold" color={color.text.muted} numberOfLines={1}>
                            {tx(`${gap}분`, `${gap} min`)}
                          </Text>
                        ) : null}
                        <View style={styles.stripDash} />
                      </View>
                    ) : null}
                  </Fragment>
                );
              })}
            </ScrollView>
            {/* 🔴 **넘칠 때만** 손잡이와 점을 그린다. 다 보이는데 화살표를 두면 «더 있다» 는
                거짓말이 된다 — 눌러도 아무 일이 안 일어나서 고장으로 읽힌다. */}
            {stripOverflows ? (
              <>
                {/* 🔴 **양끝을 흐린다.** 카드가 칸 경계에서 «잘린» 것처럼 끝나면 거기서
                    끝인 줄 안다. 흐려 두면 뒤에 더 있다는 것이 말 없이 보인다.
                    끝에 닿은 쪽은 흐리지 않는다 — 더 없는데 더 있다고 말하게 된다. */}
                {strip.left > 4 ? (
                  <LinearGradient
                    pointerEvents="none"
                    colors={[color.surface.soft, 'transparent']}
                    start={{ x: 0, y: 0 }}
                    end={{ x: 1, y: 0 }}
                    style={[styles.stripFade, styles.stripFadeLeft]}
                  />
                ) : null}
                {strip.left < strip.content - strip.view - 4 ? (
                  <LinearGradient
                    pointerEvents="none"
                    colors={['transparent', color.surface.soft]}
                    start={{ x: 0, y: 0 }}
                    end={{ x: 1, y: 0 }}
                    style={[styles.stripFade, styles.stripFadeRight]}
                  />
                ) : null}
                {strip.left > 4 ? (
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={tx('앞쪽 정차지 보기', 'Earlier stops')}
                    onPress={() => nudgeStrip(-1)}
                    style={({ pressed }) => [styles.stripArrow, styles.stripArrowLeft, pressed && styles.pressed]}
                  >
                    <Text weight="bold" color={color.text.onAction}>‹</Text>
                  </Pressable>
                ) : null}
                {strip.left < strip.content - strip.view - 4 ? (
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={tx('뒤쪽 정차지 보기', 'Later stops')}
                    onPress={() => nudgeStrip(1)}
                    style={({ pressed }) => [styles.stripArrow, styles.stripArrowRight, pressed && styles.pressed]}
                  >
                    <Text weight="bold" color={color.text.onAction}>›</Text>
                  </Pressable>
                ) : null}
              </>
            ) : null}
            </View>
            {stripOverflows ? (
              <View style={styles.stripDots}>
                {Array.from({ length: stripPages }, (unused, page) => (
                  <View key={page} style={[styles.stripDot, page === stripPage && styles.stripDotOn]} />
                ))}
              </View>
            ) : null}
            </View>
          ) : null}
          </View>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.shell}>
      <Screen scroll withTabBar>
        {/* 폰은 위 줄과 코스 칩이 **최상단에 고정**된다(인계 §4). 스크롤해도 어느 안을 보고
            있는지 안 잃어버린다. */}
        <View style={styles.phoneTop}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={tx('뒤로 가기', 'Go back')}
            onPress={() => (router.canGoBack() ? router.back() : router.replace('/trips'))}
            style={styles.phoneBack}
          >
            <Text variant="title">‹</Text>
          </Pressable>
          <Text variant="caption" weight="bold">{tx('추천 코스', 'Courses')}</Text>
          <View style={styles.phoneBack} />
        </View>
        {/* 🔴 상단 코스 칩 줄은 없앴다 — 같은 고르기를 «칩»과 «카드» 두 곳에서 받으면
            어느 쪽이 지금 고른 것인지 둘 다 봐야 안다. 고르기는 카드 한 곳에서만 받고,
            고른 것이 무엇인지는 아래 코스 바가 늘 말해 준다. */}
        {list}
        {/* 🔴 코스 바는 흐름 밖(absolute)이라 마지막 카드를 덮는다. 바는 «늘» 서 있으므로
            («고르기 전»에도 안내를 적는다) 이 자리도 늘 비운다. 높이는 탭바와 같은 한 벌을
            쓴다 — 코스 바가 탭바를 대신해 그 자리에 서기 때문이다. */}
        <View style={{ height: TAB_BAR_HEIGHT + tabBarBottomMargin(insets.bottom) + spacing[2] }} />
      </Screen>

      {/* ── 코스 바 ───────────────────────────────────────────────────────────
          🔴 **늘 서 있다.** 예전에는 고른 안이 없으면 안 그렸는데, 그러면 처음 온 사람은
             아래가 비어 있어 «무엇을 해야 하는지» 를 화면 어디서도 못 듣는다. 고르기 전에도
             자리를 지키고 「코스를 골라 주세요」 라고 적는다.
          🔴 눌러서 **시트로 자란다.** 지도를 새 화면으로 띄우면 고른 코스가 시야에서 사라져
             견주던 맥락이 끊긴다. 같은 자리에서 자라면 무엇을 보고 있는지 안 잃어버린다. */}
      <Animated.View
        style={[
          styles.courseBar,
          {
            bottom: tabBarBottomMargin(insets.bottom),
            height: grow.interpolate({ inputRange: [0, 1], outputRange: [TAB_BAR_HEIGHT, TAB_BAR_SHEET_HEIGHT] }),
            // 🔴 폭도 같이 자란다. 접혔을 때까지 시트 폭을 쓰면 아래 막대만 혼자 넓어
            //    탭바가 있던 자리와 어긋난다 — 같은 자리에 서는 것으로 안 읽힌다.
            maxWidth: grow.interpolate({ inputRange: [0, 1], outputRange: [BAR_MAX_WIDTH, SHEET_MAX_WIDTH] }),
          },
        ]}
      >
        {/* 접힌 줄 — 자라는 동안 투명해지고, 눌리지도 않아야 한다. */}
        <Animated.View
          pointerEvents={sheetOpen ? 'none' : 'auto'}
          style={[styles.barRow, { opacity: grow.interpolate({ inputRange: [0, 1], outputRange: [1, 0] }) }]}
        >
          {/* 🔴 고르기 «전» 과 «후» 의 두 벌을 겹쳐 두고 **200ms 에 걸쳐 바꿔 흐린다**
              (시안 2절). 글자만 갈아 끼우면 「코스를 골라 주세요」가 값으로 툭 바뀌어
              앞의 말을 읽던 중에 사라진다. */}
          <Animated.View
            pointerEvents={current ? 'none' : 'auto'}
            accessibilityElementsHidden={Boolean(current)}
            importantForAccessibility={current ? 'no-hide-descendants' : 'auto'}
            style={[styles.barLayer, { opacity: pickFade.interpolate({ inputRange: [0, 1], outputRange: [1, 0] }) }]}
          >
            <View style={styles.bottomCopy}>
              <Text weight="bold" numberOfLines={1}>{tx('코스를 골라 주세요', 'Pick a course')}</Text>
              <Text variant="caption" color={color.text.muted} numberOfLines={1}>
                {tx(`${courses.length}가지 중 하나를 고르면 일정이 열려요`, `Pick one of ${courses.length} to open the itinerary`)}
              </Text>
            </View>
            {/* 고를 것이 없는데 누를 수 있는 단추를 두면, 눌러 보고 아무 일도 안 일어나는
                것으로 «고장» 을 배운다. 눌리지 않는 모양으로 둔다. */}
            <View style={styles.barCtaOff}>
              <Text weight="bold" color={color.text.muted} numberOfLines={1}>{tx('일정 보기', 'View')}</Text>
            </View>
          </Animated.View>

          <Animated.View
            pointerEvents={current ? 'auto' : 'none'}
            accessibilityElementsHidden={!current}
            importantForAccessibility={current ? 'auto' : 'no-hide-descendants'}
            style={[styles.barLayer, { opacity: pickFade }]}
          >
            <View style={styles.bottomCopy}>
              <Text weight="bold" numberOfLines={1}>
                {shown
                  ? `${courseCost(shown, tx) ?? tx('비용 미정', 'Cost unknown')}${courseCost(shown, tx) ? tx(' 예상', ' est.') : ''}`
                  : ''}
              </Text>
              <Text variant="caption" color={color.text.muted} numberOfLines={1}>{shown ? courseFacts(shown, tx) : ''}</Text>
            </View>
            <Pressable
              accessibilityRole="button"
              onPress={() => setSheetOpen(true)}
              style={({ pressed }) => [styles.barCta, pressed && styles.pressed]}
            >
              <Text weight="bold" color={color.text.onAction} numberOfLines={1}>{tx('해당 코스 일정 보기', 'View this course')}</Text>
            </Pressable>
          </Animated.View>
        </Animated.View>

        {/* 자란 시트 — 고른 코스의 하루를 지도와 정차지로 본다. */}
        <Animated.View
          pointerEvents={sheetOpen ? 'auto' : 'none'}
          style={[StyleSheet.absoluteFill, styles.sheet, { opacity: grow }]}
        >
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={tx('일정 닫기', 'Close the itinerary')}
            onPress={() => setSheetOpen(false)}
            style={styles.handleHit}
          >
            <View style={styles.handle} />
          </Pressable>
          {current ? (
            <>
              <Text variant="title" weight="bold" numberOfLines={1}>{current.title}</Text>
              <Text variant="caption" color={color.text.muted} numberOfLines={1}>{courseFacts(current, tx)}</Text>
              {current.days.length > 1 ? (
                <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.phoneChips}>
                  {current.days.map((day) => (
                    <Pressable
                      key={day.day}
                      accessibilityRole="button"
                      accessibilityState={{ selected: day.day === sheetDay }}
                      onPress={() => { setSheetDay(day.day); setSelectedStopId(''); }}
                      style={[styles.legendChip, day.day === sheetDay && styles.legendChipOn]}
                    >
                      <Text variant="caption" weight="bold" color={day.day === sheetDay ? color.text.onAction : color.text.heading}>
                        {tx(`${day.day}일차`, `Day ${day.day}`)}
                      </Text>
                    </Pressable>
                  ))}
                </ScrollView>
              ) : null}
              {/* 🔴 지도 높이를 숫자로 적지 않는다. 정차지가 둘인 날과 여섯인 날은 아래
                  목록 길이가 다른데, 240 으로 박아 두면 한쪽은 지도가 남고 한쪽은 목록이
                  잘린다. 남는 자리를 «재서» 그만큼 쓴다 — 넓은 화면과 같은 방식이다. */}
              <View style={styles.sheetMap} onLayout={(event) => setSheetMapHeight(event.nativeEvent.layout.height)}>
                {sheetMapHeight > 0 && dayLayers.stops.length ? (
                  <RouteMap
                    stops={dayLayers.stops}
                    selectedId={selectedStopId}
                    onSelect={setSelectedStopId}
                    routes={dayLayers.routes}
                    height={sheetMapHeight}
                  />
                ) : null}
              </View>
              <ScrollView style={styles.sheetStops} contentContainerStyle={styles.sheetStopsInner}>
                {(current.days.find((day) => day.day === sheetDay)?.stops ?? []).map((stop, index) => (
                  <View key={`${sheetDay}-${index}-${stop.name}`} style={styles.mapStop}>
                    <Text variant="caption" weight="bold" color={color.text.muted} style={styles.mapTime}>{stop.time ?? ''}</Text>
                    <Text weight="bold" numberOfLines={1} style={styles.mapStopCopy}>{stop.name}</Text>
                  </View>
                ))}
              </ScrollView>
            </>
          ) : null}
        </Animated.View>
      </Animated.View>
      {/* 🔴 탭바는 치운다. 코스 바가 그 자리에 서기 때문이다 — 둘 다 두면 아래에
          막대가 두 겹으로 쌓이고, 시트가 자랄 때 탭바가 그 위에 남는다. */}
      <TabBar active="map" hidden />
    </View>
  );
}

const styles = StyleSheet.create({
  // 한 자리 안에서 카드와 접힌 줄이 자리를 바꾼다. 넘치는 것은 잘라야 줄어드는 동안
  // 카드가 아래 것을 덮지 않는다.
  slot: { overflow: 'hidden' },
  slotRow: { position: 'absolute', top: 0, left: 0, right: 0 },
  shell: { flex: 1, backgroundColor: color.canvas },
  // 🔴 넓은 화면만 가로 2단이다. 폰에서 가로로 두면 목록이 600 을 차지해 화면 밖으로 나간다.
  shellWide: { flexDirection: 'row' },

  // 데스크톱: 왼쪽 목록 600 · 오른쪽 전면 지도 (시안 ③).
  // 🔴 폭을 셋 다 적는다. 가로 배치에서 width 하나만 주면 남는 자리를 채우려고 늘어난다.
  // 목록 칸도 화면 바탕이다 — 순백이면 그 위의 선 없는 흰 카드가 사라진다.
  listPane: { width: 600, maxWidth: 600, flexGrow: 0, flexShrink: 0, flexBasis: 600, backgroundColor: color.canvas },
  listPaneInner: { padding: spacing[6], gap: spacing[4] },
  mapPane: { flex: 1, minWidth: 0, backgroundColor: color.surface.soft },
  mapLegend: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], padding: spacing[4] },
  // 🔴 `minHeight: 0` 이 없으면 flex 자식이 내용만큼 부풀어 스트립을 화면 밖으로 민다.
  mapArea: { flex: 1, minHeight: 0 },
  mapEmpty: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[6] },
  mapNote: { paddingVertical: spacing[2], paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card },
  mapSummary: {
    position: 'absolute', top: spacing[3], right: spacing[3],
    paddingVertical: spacing[2], paddingHorizontal: spacing[3],
    borderRadius: radius.md, backgroundColor: color.surface.card,
    shadowColor: color.brand.navy, shadowOpacity: 0.08, shadowRadius: 8, shadowOffset: { width: 0, height: 2 },
  },
  // 🔴 가로 스크롤은 그냥 두면 «남은 세로» 를 전부 먹는다. 그러면 카드 한 장이 화면
  //    절반 높이로 늘어나고 지도는 그만큼 눌린다. 자기 내용만큼만 차지하게 묶는다.
  // 손잡이와 점이 스트립 «위에» 얹히는 자리. 스트립 자체는 자기 내용만큼만 차지한다.
  stripWrap: {
    // 지도 위에 뜬다. 왼쪽·오른쪽 끝까지 닿아야 페이드가 화면 가장자리까지 간다.
    //
    // 🔴 **바닥까지 내리지 않는다.** 시안은 `bottom: 0` 인데, 시안의 지도는 그림이고
    //    진짜 카카오 지도는 **왼쪽 아래에 축척과 로고**를 그린다. 바닥까지 덮으면 그것이
    //    가려지고, 그건 지도 이용약관을 어기는 것이다(실측: 로고 y=982 를 바탕이 덮었다).
    //    그 한 줄만큼 띄워 둔다 — 카드는 그대로 지도 위에 뜨고 로고는 아래로 보인다.
    position: 'absolute', left: 0, right: 0, bottom: spacing[6],
    paddingTop: spacing[6],
  },
  // 🔴 시안의 크림색(#F4F1EA) 대신 **이 칸의 바탕색**을 쓴다. 뜻이 「지도가 바탕으로
  //    스며든다」이므로, 색을 따로 박으면 바탕을 바꿀 때 이 한 줄만 낡는다.
  stripBackdrop: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 },
  // 카드가 굴러가는 줄. 페이드와 손잡이는 이 안에만 얹혀 아래 쪽 표시를 안 덮는다.
  stripRow: { position: 'relative' },
  stripFade: { position: 'absolute', top: 0, bottom: 0, width: 56 },
  stripFadeLeft: { left: 0 },
  stripFadeRight: { right: 0 },
  stripPane: { flexGrow: 0, flexShrink: 0 },
  stripArrow: {
    position: 'absolute', top: '40%',
    width: 36, height: 36, borderRadius: radius.full,
    alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy,
    shadowColor: color.brand.navy, shadowOpacity: 0.2, shadowRadius: 8, shadowOffset: { width: 0, height: 2 },
  },
  stripArrowLeft: { left: spacing[2] },
  stripArrowRight: { right: spacing[2] },
  stripDots: { flexDirection: 'row', alignSelf: 'center', gap: spacing[1], paddingBottom: spacing[2] },
  stripDot: { width: 6, height: 6, borderRadius: radius.full, backgroundColor: color.surface.field },
  stripDotOn: { width: 18, backgroundColor: color.brand.navy },
  strip: { gap: spacing[2], alignItems: 'flex-start', paddingHorizontal: spacing[4], paddingVertical: spacing[4] },
  stripCard: {
    width: 150, gap: spacing[1], padding: spacing[3],
    borderRadius: radius.md, borderWidth: 2, borderColor: color.surface.card,
    backgroundColor: color.surface.card,
  },
  // 테두리 색은 «그날의 색» 이라 스타일에 못 적는다 — 그리는 자리에서 넣는다.
  stripCardOn: {},
  stripTop: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  // 카드와 카드 사이 — 위에 이동 시간, 아래 점선.
  // 🔴 `alignSelf: 'center'` 가 있어야 카드 «높이의 가운데» 에 온다. 스트립이
  //    `alignItems: 'flex-start'` 라(카드가 세로로 늘어나는 것을 막으려고) 이게 없으면
  //    연결부가 카드 윗변에 붙어 두 카드를 잇는 것으로 안 보인다.
  stripLink: { width: 44, alignSelf: 'center', alignItems: 'center', justifyContent: 'center', gap: spacing[1] },
  stripDash: { alignSelf: 'stretch', borderTopWidth: 2, borderStyle: 'dashed', borderColor: color.surface.field },
  stripNumber: {
    width: 22, height: 22, borderRadius: radius.full,
    alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy,
  },
  mapStop: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3] },
  mapTime: { width: 44 },
  mapStopCopy: { flex: 1, minWidth: 0 },

  list: { gap: spacing[4] },
  head: { gap: spacing[1] },
  stateCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  retry: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border },

  legendChip: { minHeight: 36, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  legendChipOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },

  phoneTop: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  phoneBack: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  phoneChips: { gap: spacing[2], paddingVertical: spacing[2] },

  courseBar: {
    // 🔴 bottom 은 여기서 정하지 않는다 — 96 은 탭바 높이와 안전영역을 어림한 숫자였고,
    //    3단추 탐색줄처럼 안전영역이 큰 기기에서는 모자라 탭바가 이 바의 아랫단을 덮었다.
    //    이제는 탭바를 치우고 이 바가 그 자리에 서므로, 탭바가 쓰던 계산을 그대로 쓴다.
    position: 'absolute', left: spacing[4], right: spacing[4],
    alignSelf: 'center', overflow: 'hidden',
    borderRadius: radius.lg, backgroundColor: color.surface.card,
    // 🔴 그림자는 **위로** 향한다(offset -2). 이 막대는 화면 아래에 «떠» 있어서 빛을
    //    위에서 받는다 — 아래로 드리우면 잘린 화면 밖으로 지고 떠 보이지 않는다.
    //    값은 탭바의 것과 같다(시안: "TabBar 와 동일"). 코스 바가 탭바를 대신 서기 때문이다.
    shadowColor: color.brand.navy, shadowOpacity: 0.10, shadowRadius: 14, shadowOffset: { width: 0, height: -2 }, elevation: 8,
    zIndex: 30,
  },
  // 겹쳐 두는 두 벌 — 같은 자리를 차지해야 바꿔 흐릴 때 글자가 안 움직인다.
  barLayer: {
    position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
    flexDirection: 'row', alignItems: 'center', gap: spacing[3],
    paddingLeft: spacing[4], paddingRight: spacing[2],
  },
  barRow: {
    position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
    flexDirection: 'row', alignItems: 'center', gap: spacing[3],
    paddingLeft: spacing[4], paddingRight: spacing[2],
  },
  sheet: { padding: spacing[3], paddingBottom: spacing[4], gap: spacing[3] },
  handleHit: { height: 20, alignItems: 'center', justifyContent: 'center' },
  handle: { width: 36, height: 4, borderRadius: radius.full, backgroundColor: color.surface.field },
  // 🔴 `minHeight: 0` 이 없으면 flex 자식이 내용만큼 부풀어 목록을 시트 밖으로 민다.
  sheetMap: { flex: 1, minHeight: 0 },
  sheetStops: { flexGrow: 0, flexShrink: 0, maxHeight: 132 },
  sheetStopsInner: { gap: spacing[2] },
  bottomCopy: { flex: 1, minWidth: 0 },
  barCta: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.action.primary },
  barCtaOff: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.tint },
  pressed: { opacity: 0.82 },
});
