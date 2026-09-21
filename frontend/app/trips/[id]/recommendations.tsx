// 코스 고르기 — **전체 일정 3안을 견준다** (시안 ③, 인계 §4).
//
// 🔴 전에는 장소 후보 목록이었다. 장소를 고르는 것과 일정을 고르는 것은 사람이 하는 판단
//    자체가 다르다 — 앞은 「여기 갈까」이고 뒤는 「이렇게 다닐까」다. 그래서 옛 「추천 일정
//    요약」 화면은 없어지고, 이 자리는 코스 비교가 된다.
//
// 🔴 필터(예산·이동 적게·휠체어)는 **없다**(인계 §7③). 조건은 ① 에서 이미 받았다. 여기서
//    또 물으면 앞에서 답한 것이 반영되지 않았다는 뜻이 된다.
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useAuth } from '@/auth/AuthProvider';
import { Screen } from '@/components/Screen';
import { Skeleton } from '@/components/Skeleton';
import { TabBar, bottomBarClearance } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { resolveTextLanguage } from '@/i18n/languages';
import { useLayout } from '@/layout/useLayout';
import { RouteMap } from '@/map/RouteMap';
import { CourseCard, courseCost, courseFacts, courseLetter } from '@/plan/CourseCard';
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
        ))
      )}
    </View>
  );

  if (wide) {
    return (
      <View style={[styles.shell, styles.shellWide]}>
        <ScrollView style={styles.listPane} contentContainerStyle={styles.listPaneInner}>{list}</ScrollView>
        {/* 오른쪽 전면 지도. 코스가 하나도 없을 때는 빈 판을 두지 않고 안내를 적는다. */}
        <View style={styles.mapPane}>
          <View style={styles.mapLegend}>
            {courses.map((course, index) => (
              <Pressable
                key={course.id}
                accessibilityRole="button"
                accessibilityState={{ selected: course.id === picked }}
                onPress={() => setPicked(course.id)}
                style={[styles.legendChip, course.id === picked && styles.legendChipOn]}
              >
                <Text variant="caption" weight="bold" color={course.id === picked ? color.text.onAction : color.text.heading} numberOfLines={1}>
                  {course.title || txf(tx, '코스 %s', 'Course %s', courseLetter(index))}
                </Text>
              </Pressable>
            ))}
          </View>
          {/* 🔴 좌표가 하나라도 오면 **선으로** 그린다 (-1333). 하나도 없으면 아래처럼
              **동선을 글로** 세운다 — 좌표 없이 선을 그으면 실제로 안 가는 길을 그리게 되고,
              그건 빈 지도보다 나쁘다. 옛 서버에 붙은 앱이 그 상태다. */}
          {mapLayers.stops.length ? (
            <RouteMap
              stops={mapLayers.stops}
              selectedId={selectedStopId}
              onSelect={setSelectedStopId}
              routes={mapLayers.routes}
              height={640}
            />
          ) : null}
          <ScrollView style={styles.mapBody} contentContainerStyle={styles.mapBodyInner}>
            {current ? (
              <>
                <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('코스 내용', 'What is in this course')}</Text>
                <Text variant="title" weight="bold">{current.title}</Text>
                <Text variant="caption" color={color.text.muted}>{courseFacts(current, tx)}</Text>
                {current.days.map((day) => (
                  <View key={day.day} style={styles.mapDay}>
                    <View style={styles.legendChip}>
                      <Text variant="caption" weight="bold">{tx(`${day.day}일차`, `Day ${day.day}`)}</Text>
                    </View>
                    {day.stops.map((stop, index) => (
                      <View key={`${day.day}-${index}-${stop.name}`} style={styles.mapStop}>
                        <Text variant="caption" weight="bold" color={color.text.muted} style={styles.mapTime}>{stop.time ?? ''}</Text>
                        <View style={styles.mapStopCopy}>
                          <Text weight="bold" numberOfLines={1}>{stop.name}</Text>
                          {stop.note ? <Text variant="caption" color={color.text.muted} numberOfLines={2}>{stop.note}</Text> : null}
                        </View>
                      </View>
                    ))}
                  </View>
                ))}
                {current.rationale ? (
                  <View style={styles.rationale}>
                    <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('이렇게 골랐어요', 'Why this course')}</Text>
                    <Text variant="caption" color={color.text.body}>{current.rationale}</Text>
                  </View>
                ) : null}
                {mapLayers.routes.length ? null : (
                  <Text variant="caption" color={color.text.muted}>
                    {tx('동선 지도는 준비 중이에요. 장소의 좌표가 들어오면 여기에 선으로 그려 드려요.',
                      'The route map is on the way — we will draw it once the stops carry coordinates.')}
                  </Text>
                )}
              </>
            ) : (
              <Text variant="caption" color={color.text.muted}>{tx('코스를 고르면 내용이 여기에 보여요.', 'Pick a course to see what is in it.')}</Text>
            )}
          </ScrollView>
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
        {courses.length > 1 ? (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.phoneChips}>
            {courses.map((course, index) => (
              <Pressable
                key={course.id}
                accessibilityRole="button"
                accessibilityState={{ selected: course.id === picked }}
                onPress={() => setPicked(course.id)}
                style={[styles.legendChip, course.id === picked && styles.legendChipOn]}
              >
                <Text variant="caption" weight="bold" color={course.id === picked ? color.text.onAction : color.text.heading} numberOfLines={1}>
                  {course.title || txf(tx, '코스 %s', 'Course %s', courseLetter(index))}
                </Text>
              </Pressable>
            ))}
          </ScrollView>
        ) : null}
        {list}
      </Screen>

      {/* 하단 고정 바 — 비용과 「이 코스로 일정 만들기」. 고른 안이 없으면 안 그린다. */}
      {current ? (
        <View style={[styles.bottomBar, { bottom: bottomBarClearance(insets.bottom) }]}>
          <View style={styles.bottomCopy}>
            <Text weight="bold" numberOfLines={1}>
              {courseCost(current, tx) ?? tx('비용 미정', 'Cost unknown')}
              {courseCost(current, tx) ? tx(' 예상', ' est.') : ''}
            </Text>
            <Text variant="caption" color={color.text.muted} numberOfLines={1}>{courseFacts(current, tx)}</Text>
          </View>
          <Pressable accessibilityRole="button" onPress={() => void build(current)} style={({ pressed }) => [styles.bottomCta, pressed && styles.pressed]}>
            <Text weight="bold" color={color.text.onAction} numberOfLines={1}>{tx('이 코스로 일정 만들기', 'Build this itinerary')}</Text>
          </Pressable>
        </View>
      ) : null}
      <TabBar active="map" />
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
  mapBody: { flex: 1 },
  mapBodyInner: { gap: spacing[2], padding: spacing[6] },
  mapDay: { gap: spacing[2], marginTop: spacing[3] },
  mapStop: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3] },
  mapTime: { width: 44 },
  mapStopCopy: { flex: 1, minWidth: 0 },
  rationale: { gap: spacing[1], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.card },

  list: { gap: spacing[4] },
  head: { gap: spacing[1] },
  stateCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  retry: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border },

  legendChip: { minHeight: 36, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  legendChipOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },

  phoneTop: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  phoneBack: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  phoneChips: { gap: spacing[2], paddingVertical: spacing[2] },

  bottomBar: {
    // 🔴 bottom 은 여기서 정하지 않는다 — 96 은 탭바 높이와 안전영역을 어림한 숫자였고,
    //    3단추 탐색줄처럼 안전영역이 큰 기기에서는 모자라 탭바가 이 바의 아랫단을 덮었다.
    position: 'absolute', left: spacing[4], right: spacing[4],
    flexDirection: 'row', alignItems: 'center', gap: spacing[3],
    padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card,
    shadowColor: color.brand.navy, shadowOpacity: 0.18, shadowRadius: 16, shadowOffset: { width: 0, height: 6 }, elevation: 8,
  },
  bottomCopy: { flex: 1, minWidth: 0 },
  bottomCta: { minHeight: 48, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.brand.navy },
  pressed: { opacity: 0.82 },
});
