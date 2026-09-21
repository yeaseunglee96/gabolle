// 코스 고르기 — **전체 일정 3안을 견준다** (시안 ③, 인계 §4).
//
// 🔴 전에는 장소 후보 목록이었다. 장소를 고르는 것과 일정을 고르는 것은 사람이 하는 판단
//    자체가 다르다 — 앞은 「여기 갈까」이고 뒤는 「이렇게 다닐까」다. 그래서 옛 「추천 일정
//    요약」 화면은 없어지고, 이 자리는 코스 비교가 된다.
//
// 🔴 필터(예산·이동 적게·휠체어)는 **없다**(인계 §7③). 조건은 ① 에서 이미 받았다. 여기서
//    또 물으면 앞에서 답한 것이 반영되지 않았다는 뜻이 된다.
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Animated, Easing, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useAuth } from '@/auth/AuthProvider';
import { Screen } from '@/components/Screen';
import { Skeleton } from '@/components/Skeleton';
import { TabBar, TAB_BAR_HEIGHT, TAB_BAR_SHEET_HEIGHT, tabBarBottomMargin } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { resolveTextLanguage } from '@/i18n/languages';
import { useLayout } from '@/layout/useLayout';
import { RouteMap } from '@/map/RouteMap';
import { CourseCard, CourseRow, courseCost, courseFacts, courseLetter } from '@/plan/CourseCard';
import { courseMapLayers } from '@/plan/courseMap';
import { useCourseRoutePaths } from '@/map/courseRoutePaths';
import { findLatestRecommendationJob, loadRecommendationResult } from '@/plan/recommendations';
import { loadTripCourses, type TripCourse, type TripCoursesResult } from '@/plan/tripCourses';
import { shouldAskTripName, wasTripNameAsked } from '@/trip/tripNaming';
import { loadTrips } from '@/trip/trips';
import { localizeMessage } from '@/i18n/messages';

type Loaded = { state: 'loading' } | { state: 'ready'; result: TripCoursesResult };

export default function Recommendations() {
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
          picked && courses.length > 1 && course.id !== picked ? (
            <CourseRow
              key={course.id}
              course={course}
              selected={false}
              saved={Boolean(saved[course.id])}
              onSelect={() => setPicked(course.id)}
              onToggleSave={() => setSaved((prev) => ({ ...prev, [course.id]: !prev[course.id] }))}
              tx={tx}
            />
          ) : (
          <CourseCard
            key={course.id}
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
          )
        ))
      )}
    </View>
  );

  if (wide) {
    const dayStops = current?.days.find((day) => day.day === sheetDay)?.stops ?? [];
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
          </View>

          {/* 아래 스트립 — 고른 일차의 정차지를 **순서대로** 옆으로 세운다.
              지도의 번호와 같은 번호를 달아, 점을 누르든 카드를 누르든 같은 곳이 켜진다. */}
          {dayStops.length ? (
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.stripPane} contentContainerStyle={styles.strip}>
              {dayStops.map((stop, index) => {
                const id = `${sheetDay}-${index + 1}`;
                const on = id === selectedStopId;
                return (
                  <Pressable
                    key={`${id}-${stop.name}`}
                    accessibilityRole="button"
                    accessibilityState={{ selected: on }}
                    onPress={() => setSelectedStopId(on ? '' : id)}
                    style={[styles.stripCard, on && styles.stripCardOn]}
                  >
                    <View style={styles.stripTop}>
                      <View style={styles.stripNumber}>
                        <Text variant="caption" weight="bold" color={color.text.onAction}>{index + 1}</Text>
                      </View>
                      <Text variant="caption" weight="bold" color={color.text.muted} numberOfLines={1}>{stop.time ?? ''}</Text>
                    </View>
                    <Text weight="bold" numberOfLines={1}>{stop.name}</Text>
                  </Pressable>
                );
              })}
            </ScrollView>
          ) : null}
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
          },
        ]}
      >
        {/* 접힌 줄 — 자라는 동안 투명해지고, 눌리지도 않아야 한다. */}
        <Animated.View
          pointerEvents={sheetOpen ? 'none' : 'auto'}
          style={[styles.barRow, { opacity: grow.interpolate({ inputRange: [0, 1], outputRange: [1, 0] }) }]}
        >
          <View style={styles.bottomCopy}>
            <Text weight="bold" numberOfLines={1}>
              {current
                ? `${courseCost(current, tx) ?? tx('비용 미정', 'Cost unknown')}${courseCost(current, tx) ? tx(' 예상', ' est.') : ''}`
                : tx('코스를 골라 주세요', 'Pick a course')}
            </Text>
            <Text variant="caption" color={color.text.muted} numberOfLines={1}>
              {current
                ? courseFacts(current, tx)
                : tx(`${courses.length}가지 중 하나를 고르면 일정이 열려요`, `Pick one of ${courses.length} to open the itinerary`)}
            </Text>
          </View>
          {current ? (
            <Pressable
              accessibilityRole="button"
              onPress={() => setSheetOpen(true)}
              style={({ pressed }) => [styles.barCta, pressed && styles.pressed]}
            >
              <Text weight="bold" color={color.text.onAction} numberOfLines={1}>{tx('해당 코스 일정 보기', 'View this course')}</Text>
            </Pressable>
          ) : (
            // 고를 것이 없는데 누를 수 있는 단추를 두면, 눌러 보고 아무 일도 안 일어나는
            // 것으로 «고장» 을 배운다. 눌리지 않는 모양으로 둔다.
            <View style={styles.barCtaOff}>
              <Text weight="bold" color={color.text.muted} numberOfLines={1}>{tx('일정 보기', 'View')}</Text>
            </View>
          )}
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
              {dayLayers.stops.length ? (
                <RouteMap
                  stops={dayLayers.stops}
                  selectedId={selectedStopId}
                  onSelect={setSelectedStopId}
                  routes={dayLayers.routes}
                  height={240}
                />
              ) : null}
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
  stripPane: { flexGrow: 0, flexShrink: 0 },
  strip: { gap: spacing[2], alignItems: 'flex-start', paddingHorizontal: spacing[4], paddingVertical: spacing[4] },
  stripCard: {
    width: 150, gap: spacing[1], padding: spacing[3],
    borderRadius: radius.md, borderWidth: 2, borderColor: color.surface.card,
    backgroundColor: color.surface.card,
  },
  stripCardOn: { borderColor: color.action.outline },
  stripTop: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
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
    alignSelf: 'center', maxWidth: 361, overflow: 'hidden',
    borderRadius: radius.lg, backgroundColor: color.surface.card,
    shadowColor: color.brand.navy, shadowOpacity: 0.18, shadowRadius: 16, shadowOffset: { width: 0, height: 6 }, elevation: 8,
    zIndex: 30,
  },
  barRow: {
    position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
    flexDirection: 'row', alignItems: 'center', gap: spacing[3],
    paddingLeft: spacing[4], paddingRight: spacing[2],
  },
  sheet: { padding: spacing[3], paddingBottom: spacing[4], gap: spacing[2] },
  handleHit: { height: 20, alignItems: 'center', justifyContent: 'center' },
  handle: { width: 36, height: 4, borderRadius: radius.full, backgroundColor: color.surface.field },
  sheetStops: { flex: 1 },
  sheetStopsInner: { gap: spacing[2] },
  bottomCopy: { flex: 1, minWidth: 0 },
  barCta: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.action.primary },
  barCtaOff: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.tint },
  pressed: { opacity: 0.82 },
});
