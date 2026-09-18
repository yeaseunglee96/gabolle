import { useCallback, useEffect, useState } from 'react';
import { Image, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { shouldAskTripName, wasTripNameAsked } from '@/trip/tripNaming';
import { loadTrips } from '@/trip/trips';
import { sendAppEvent } from '@/analytics/appEvents';
import { loadRecommendationActions, saveRecommendationAction, type RecommendationActionScope } from '@/plan/recommendationActions';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Skeleton } from '@/components/Skeleton';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { describeWarningCodes } from '@/plan/warningLabels';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { findLatestRecommendationJob, loadRecommendationResult, type RecommendationCourse, type RecommendationViewModel, unavailableRecommendations } from '@/plan/recommendations';

function CourseCard({ course, onAction }: { course: RecommendationCourse; onAction: (state: RecommendationCourse['actionState']) => void }) {
  const { tx } = useI18n();
  const STATUS = { VERIFIED: tx('확인됨', 'Verified'), ESTIMATED: tx('추정', 'Estimated'), UNKNOWN: tx('미확인', 'Unconfirmed') } as const;
  const CROWD = { LOW: tx('여유', 'Light'), MEDIUM: tx('보통', 'Moderate'), HIGH: tx('혼잡', 'Crowded') } as const;
  const FALLBACK = { MODEL: tx('개인화 추천', 'Personalized recommendation'), RULE: tx('조건 기반 추천', 'Condition-based recommendation'), BASELINE: tx('기본 추천', 'Baseline recommendation') } as const;
  // S15P21E201-1003 — 🔴 사진 없는 카드가 기본이고 사진이 예외다. 장소 사진이 채워진 비율은
  // 2.7% 이고 앞으로 채울 수 있는 상한이 2.9% 다. 음식점 2,355곳은 영원히 없다(적재기가
  // 구조적으로 null 을 넣는다). 그래서 사진 자리를 크게 잡아 두면 회색 띠만 남는다 —
  // 아예 두지 않고, 사진이 있을 때만 얹는다.
  //
  // 🔴 값이 없는 칸도 만들지 않는다. 예상 비용은 서버에 자료가 없어 항상 null 이고,
  // 혼잡도·이동 제약도 대부분 비어 있다. 「미확인」이라고 적힌 칸은 정보가 아니다.
  const facts = [
    course.estimatedCostKrw == null ? null : tx(`예상 비용 ${course.estimatedCostKrw.toLocaleString()}원`, `Est. cost ${course.estimatedCostKrw.toLocaleString()} KRW`),
    course.crowdLevel ? tx(`혼잡도 ${CROWD[course.crowdLevel]}`, `Crowd ${CROWD[course.crowdLevel]}`) : null,
  ].filter((fact): fact is string => fact !== null);
  return <View style={styles.card}>{course.imageUrl ? <Image source={{ uri: course.imageUrl }} accessibilityLabel={tx(`${course.title} 대표 이미지`, `${course.title} cover image`)} resizeMode="cover" style={styles.image} /> : null}<View style={styles.cardBody}><View style={styles.titleRow}><Text variant="title" weight="bold" style={styles.grow}>{course.title}</Text><View style={styles.status}><Text variant="caption" weight="bold">{STATUS[course.dataStatus]}</Text></View></View><View style={styles.tags}>{course.reasons.map((reason, index) => <View key={`${reason}-${index}`} style={styles.tag}><Text variant="caption" weight="bold" color={color.brand.orange}>#{reason}</Text></View>)}</View>{facts.length ? <Text variant="caption" color={color.text.body}>{facts.join(' · ')}</Text> : null}{(() => {
      // 🔴 코드를 그대로 찍지 않는다 (S15P21E201-1171). 전에는 join(' · ') 으로 이어 붙여
      // 「이동 제약 · ACCESSIBILITY_UNVERIFIED」 처럼 영문 대문자가 그대로 나갔다 —
      // 아침에 일정 화면에서 고친 것(S15P21E201-1150)과 같은 결함이 여기 남아 있었다.
      // 사전에 짝이 없는 코드는 describeWarningCodes 가 뺀다.
      const warnings = describeWarningCodes(course.mobilityWarnings, tx);
      return warnings.length ? <Text variant="caption" color={color.state.danger}>{tx('이동 제약', 'Mobility constraints')} · {warnings.join(' · ')}</Text> : null;
    })()}<Text variant="caption" color={color.text.muted}>{FALLBACK[course.fallbackMode]}</Text><View style={styles.actions}><Pressable accessibilityRole="button" accessibilityState={{ selected: course.actionState === 'saved', busy: course.actionState === 'saving' }} onPress={() => onAction(course.actionState === 'saved' ? 'idle' : 'saved')} style={styles.action}><Text variant="caption" weight="bold">{course.actionState === 'saved' ? tx('저장됨', 'Saved') : tx('저장', 'Save')}</Text></Pressable><Pressable accessibilityRole="button" accessibilityState={{ selected: course.actionState === 'excluded', busy: course.actionState === 'excluding' }} onPress={() => onAction(course.actionState === 'excluded' ? 'idle' : 'excluded')} style={styles.action}><Text variant="caption" weight="bold">{course.actionState === 'excluded' ? tx('제외됨', 'Excluded') : tx('제외', 'Exclude')}</Text></Pressable></View></View></View>;
}

export default function Recommendations() {
  const router = useRouter(); const { accessToken } = useAuth(); const { tx } = useI18n(); const { id, jobId } = useLocalSearchParams<{ id: string; jobId?: string }>();
  const [view, setView] = useState<RecommendationViewModel>(() => jobId || id ? { ...unavailableRecommendations(), state: 'loading', message: tx('추천 결과를 확인하고 있어요.', 'Checking your recommendation result.') } : unavailableRecommendations());

  // 완성된 일정으로 가기 전에 한 번만 이름을 물어본다 (S15P21E201-1036).
  //
  // 🔴 2026-09-17 (S15P21E201-1178) — 여기 있던 주석이 **틀려 있었다.** 지우지 않고 적어 둔다:
  //    *「주소의 id 는 여행 id 이고(76행이 tripId 로 쓴다)」*. 아니다. 아래 reload() 의 주석이
  //    맞다 — 이 화면의 주소는 `/trips/{작업번호}/recommendations` 이고, 그 칸은 **작업 번호**다.
  //    같은 파일 안에서 두 주석이 서로 반대를 말하고 있었다.
  //
  //    그래서 이름 짓기로 갈 때 **작업 번호를 여행 번호 자리에 넣어 보냈고**, 이름 화면은
  //    그 값으로 여행을 찾다 실패해 「이 여행을 찾을 수 없어요」를 띄웠다. 사용자가 여행을
  //    완성하고 이름을 짓는 마지막 자리에서 막혔다.
  //
  // 🔴 그래서 **주소를 믿지 않고 서버가 준 값을 쓴다** — `view.tripId`(S15P21E201-1084 로
  //    응답에 생긴 칸). 주소만 고치면 전제는 여전히 아무도 안 검사한다.
  //
  // 🔴 그 칸을 아직 안 주는 서버가 있다. 없으면 **묻지 않고 일정으로 바로 간다** —
  //    틀린 번호를 넘기는 것보다 낫고, 아래 catch 의 방침과 같다.
  //
  // 🔴 이미 이름이 있거나 한 번 물어봤으면 **묻지 않고 그냥 지나간다.** 같은 질문을 두 번
  // 하면 건너뛰기가 「나중에 또 물어볼게요」가 된다.
  const openItinerary = async (itineraryId: string) => {
    const target = `/trips/${itineraryId}/itinerary`;
    const tripId = view.tripId;
    if (!tripId) { router.push(target); return; }
    try {
      const [trips, alreadyAsked] = await Promise.all([loadTrips(accessToken), wasTripNameAsked(tripId)]);
      const title = trips.state === 'success' ? trips.trips.find((trip) => trip.tripId === tripId)?.title : null;
      if (shouldAskTripName({ title, alreadyAsked })) {
        router.push(`/${tripId}/name?next=${encodeURIComponent(target)}` as never);
        return;
      }
    } catch {
      // 🔴 물어볼지 정하다 실패하면 **묻지 않고 지나간다.** 일정을 보러 가는 길을
      // 이름 짓기 때문에 막지 않는다.
    }
    router.push(target);
  };
  // 담아두기·빼기가 어디에 속하는가 — S15P21E201-1082.
  //
  // 🔴 서버 주소에는 여행 번호가 필요하고, 기기 저장에는 아무 열쇠나 있으면 된다. 둘이 다른
  //    값일 수 있어 따로 넘긴다. 여행 번호를 알면 기기 열쇠도 그것으로 해서, 같은 여행을 다시
  //    추천받아도(작업 번호가 새로 생겨도) 앞서 내린 판단이 그대로 보이게 한다.
  const actionScope = useCallback((tripId: string | null): RecommendationActionScope => ({
    tripId,
    deviceKey: tripId ?? id ?? '',
    accessToken,
  }), [accessToken, id]);
  const reload = useCallback(async () => {
    // S15P21E201-1002 — 「다시 열면 빈 화면」의 진짜 원인 (2026-09-16 배포본에서 실측).
    //
    // 🔴 이 화면의 주소는 `/trips/{작업번호}/recommendations?jobId={같은 값}` 이다 —
    // 생성 화면이 그렇게 보낸다. 경로에 작업 번호가 이미 들어 있는데 코드는 물음표 뒤만
    // 읽었다. 그래서 같은 주소를 다시 열면, 값이 멀쩡히 있는데도 서버를 안 불렀다.
    //
    // 🔴 이 칸은 여행 번호가 아니다. 같은 `[id]` 자리가 일정 화면에서는 일정 번호이고
    // 여기서는 작업 번호다. 이것을 여행 번호로 알고 물었더니 서버가 TRIP_NOT_FOUND 를 냈고,
    // 화면은 멀쩡히 있는 여행을 두고 「그 여행을 찾을 수 없어요」라고 말했다. 그래서 여기서는
    // 여행이 없다고 단정하지 않는다 — 우리가 든 값이 여행 번호인지조차 모른다.
    if (!jobId && !id) { setView(unavailableRecommendations()); return; }
    setView((current) => ({ ...current, state: 'loading', message: tx('추천 결과를 확인하고 있어요.', 'Checking your recommendation result.') }));
    let next = await loadRecommendationResult(jobId ?? id, accessToken);
    // 작업 번호로 못 찾았을 때만 여행 번호로 한 번 더 찾아본다. 여행에서 바로 들어오는 길이
    // 생기면 그때는 이 칸이 여행 번호이므로, 그 경로를 위해 남겨 둔다.
    if (next.state === 'unavailable' && id) {
      const lookup = await findLatestRecommendationJob(id, accessToken);
      if (lookup.state === 'found' || lookup.state === 'in-progress') next = await loadRecommendationResult(lookup.jobId, accessToken);
    }
    // 담아두기·빼기를 되살린다 — S15P21E201-975 · 1082.
    //
    // 🔴 서버에 물을 주소에는 **여행 번호**가 필요한데, 이 화면의 `id` 는 작업 번호다(위 참고).
    //    여행 번호는 추천 결과 응답의 tripId(S15P21E201-1084)로만 온다. 그 칸을 아직 안 주는
    //    서버를 상대할 때는 null 이고, 그때는 종전처럼 기기에만 적는다.
    const stored = await loadRecommendationActions(actionScope(next.tripId));
    setView({ ...next, courses: next.courses.map((course) => stored[course.id] ? { ...course, actionState: stored[course.id] } : course) });
  }, [accessToken, actionScope, id, jobId, tx]);
  useEffect(() => { void reload(); }, [reload]);
  // 저장·제외는 지금까지 화면 상태만 바꿨다. 이제 서버로도 간다 — 노출된 것 중에서 고른 것이라
  // 가장 깨끗한 취향 신호다. 되돌리기(idle)는 아무 뜻이 아니라 보내지 않는다.
  //
  // 🔴 전송은 setView 의 갱신 함수 밖에서 한다. 그 안에서 하면 React 가 갱신 함수를 두 번 부를 때
  //    이벤트도 두 건 적힌다. 그리고 버튼은 서버 응답을 기다리지 않는다.
  const updateAction = (courseId: string, actionState: RecommendationCourse['actionState']) => {
    setView((current) => ({ ...current, courses: current.courses.map((course) => course.id === courseId ? { ...course, actionState } : course) }));
    // 🔴 적는다 — S15P21E201-975 · 1082. 아래 행동 이벤트는 분석용이라 되읽지 않는다.
    //    이것이 없던 동안 버튼은 눌려도 화면을 다시 열면 원래대로 돌아갔다.
    //    여행 번호를 알면 서버에도 간다 — 그래야 동행자가 서로의 판단을 본다.
    void saveRecommendationAction(actionScope(view.tripId), courseId, actionState === 'saved' || actionState === 'excluded' ? actionState : null);
    if (actionState === 'saved' || actionState === 'excluded') {
      sendAppEvent({ type: actionState === 'saved' ? 'place_like' : 'place_dislike', accessToken, tripId: id, payload: { place_id: courseId, surface: 'recommendations' } });
    }
  };
  const { width } = useLayout();
  const wide = isAtLeast(width, 'lg');
  const [selectedCourseId, setSelectedCourseId] = useState<string | null>(null);
  // 고른 것이 없거나 목록이 바뀌어 사라졌으면 첫 코스를 본다 — 오른쪽 칸이 비는 순간을 안 만든다.
  const selectedCourse = view.courses.find((course) => course.id === selectedCourseId) ?? view.courses[0] ?? null;
  const unavailable = view.state === 'unavailable' || view.state === 'empty-conflict';
  return <View style={styles.shell}><Screen scroll wide withTabBar style={styles.canvas}><View style={styles.nav}><Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/plan/confirm')} style={styles.back}><Text variant="title">‹</Text></Pressable><View style={styles.dots}><View style={styles.dot} /><View style={styles.dot} /><View style={styles.activeDot} /></View></View><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('추천 일정 요약', 'Recommendation summary')}</Text><Text variant="display" weight="bold" style={styles.heading}>{view.state === 'success' || view.state === 'partial' || view.state === 'fallback' ? tx(`${view.courses.length}가지 코스를 골라봤어요.`, `We picked ${view.courses.length} courses for you.`) : tx('추천 결과', 'Recommendation result')}</Text>
      {(view.state === 'success' || view.state === 'partial' || view.state === 'fallback') && (view.placeCount !== null || view.estimatedTravelMinutes !== null) && <Text variant="body" color={color.text.muted} style={styles.summary}>{[view.placeCount !== null ? tx(`장소 ${view.placeCount}곳`, `${view.placeCount} places`) : null, view.estimatedTravelMinutes !== null ? tx(`이동 약 ${view.estimatedTravelMinutes}분`, `~${view.estimatedTravelMinutes} min travel`) : null].filter(Boolean).join(' · ')}</Text>}
      {view.state === 'loading' && <View accessibilityLabel={tx('추천을 불러오고 있어요', 'Loading recommendations')} style={styles.list}>{[0, 1].map((key) => (
        <View key={key} style={styles.card}>
          <Skeleton width="100%" height={140} radius={0} />
          <View style={styles.cardBody}>
            <Skeleton width="70%" height={18} />
            <View style={styles.tags}><Skeleton width={64} height={22} radius={radius.sm} /><Skeleton width={64} height={22} radius={radius.sm} /></View>
            <Skeleton width="50%" height={14} />
            <Skeleton width="40%" height={14} />
          </View>
        </View>
      ))}</View>}
      {(view.state === 'partial' || view.state === 'fallback') && <View style={styles.notice}><Text accessibilityRole="alert" variant="caption" weight="bold">{view.message}</Text></View>}
      {(view.state === 'error' || view.state === 'offline') && <View style={styles.stateCard}><Text variant="title" weight="bold">{view.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : tx('추천을 불러오지 못했어요', 'Could not load recommendations')}</Text><Text color={color.text.body}>{view.message}</Text><Button accessibilityRole="button" label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void reload()} /></View>}
      {unavailable && <View style={styles.stateCard}><View style={styles.emptyMark}><Text variant="display">⌁</Text></View><Text variant="title" weight="bold">{view.state === 'empty-conflict' ? tx('조건을 만족하는 코스가 없어요', 'No course matched your conditions') : tx('아직 생성된 추천이 없어요', 'No recommendations yet')}</Text><Text color={color.text.body}>{view.message}</Text>{describeWarningCodes(view.conflicts, tx).map((item) => <Text key={item} accessibilityRole="alert" variant="caption" color={color.state.danger}>• {item}</Text>)}<Button accessibilityRole="button" label={tx('조건 수정하기', 'Edit conditions')} variant="ghost" onPress={() => router.push('/plan/confirm')} />{jobId && <Button accessibilityRole="button" label={tx('다시 확인', 'Check again')} variant="ghost" onPress={() => void reload()} />}</View>}
      {/* S15P21E201-1003 — 넓은 화면은 왼쪽 목록 + 오른쪽 고른 코스로 나눈다. 카드 하나가
          1180px 를 차지하던 자리다. 폰은 지금처럼 한 줄로 둔다(티켓 지시) — 목록과 상세를
          함께 쌓으면 정작 보러 온 목록이 한참 밀려 내려간다.

          🔴 Split.tsx 를 안 쓴 이유: 그 부품은 최단변 600 이상이면 나누는데, 이 저장소는
          「600 에서 320 보조 칸을 붙이면 본문이 짓눌린다」고 이미 판단해 피드 화면을 1024
          기준으로 만들었다(feed.tsx 주석). 같은 기준을 따른다. */}
      {wide && view.courses.length
        ? <View style={styles.wideGrid}>
            <View style={styles.listColumn} accessibilityRole="tablist">
              {view.courses.map((course) => {
                const selected = course.id === selectedCourse?.id;
                return <Pressable key={course.id} accessibilityRole="tab" accessibilityState={{ selected }} onPress={() => setSelectedCourseId(course.id)} style={[styles.listRow, selected && styles.listRowSelected]}>
                  <Text variant="body" weight="bold" numberOfLines={1}>{course.title}</Text>
                  {course.reasons.length ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>#{course.reasons.slice(0, 2).join(' #')}</Text> : null}
                </Pressable>;
              })}
            </View>
            <View style={styles.detailColumn}>
              {selectedCourse ? <CourseCard course={selectedCourse} onAction={(state) => updateAction(selectedCourse.id, state)} /> : null}
            </View>
          </View>
        : <View style={styles.list}>{view.courses.map((course) => <CourseCard key={course.id} course={course} onAction={(state) => updateAction(course.id, state)} />)}</View>}
      <Button accessibilityRole="button" accessibilityState={{ disabled: !view.itineraryId }} label={tx('이 일정으로 보기', 'View this itinerary')} disabled={!view.itineraryId} containerStyle={styles.cta} onPress={() => { if (view.itineraryId) void openItinerary(view.itineraryId); }} />
      {view.itineraryId ? <Text variant="caption" color={color.text.muted} style={styles.reason}>{tx('다음에 여행 이름을 붙일 수 있어요. 건너뛰어도 괜찮아요.', 'You can name your trip next. Skipping is fine.')}</Text> : null}
      {!view.itineraryId && <Text variant="caption" color={color.text.muted} style={styles.reason}>{tx('완성된 일정이 생기면 상세 일정으로 이동할 수 있어요.', 'You can move to the full itinerary once it’s ready.')}</Text>}
    </Screen><TabBar active="schedule" /></View>;
}
const styles = StyleSheet.create({ shell: { flex: 1, backgroundColor: color.brand.ivory }, canvas: { backgroundColor: color.brand.ivory }, nav: { minHeight: 44, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, dots: { flexDirection: 'row', gap: spacing[1] }, dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: color.surface.field }, activeDot: { width: 18, height: 6, borderRadius: 3, backgroundColor: color.brand.orange }, heading: { marginTop: spacing[1] }, summary: { marginBottom: spacing[4] }, stateCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border, alignItems: 'center' }, emptyMark: { width: 64, height: 64, borderRadius: radius.full, backgroundColor: '#ede9e0', alignItems: 'center', justifyContent: 'center' }, notice: { marginBottom: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg }, list: { gap: spacing[3] }, wideGrid: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[6] }, listColumn: { width: 320, gap: spacing[2] }, detailColumn: { flex: 1, minWidth: 0 }, listRow: { gap: spacing[1], padding: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card }, listRowSelected: { borderColor: color.brand.orange, backgroundColor: color.surface.warm }, card: { overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: .08, shadowRadius: 12, elevation: 2 }, image: { width: '100%', height: 140 }, cardBody: { gap: spacing[2], padding: spacing[4] }, titleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, grow: { flex: 1 }, status: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft }, tags: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[1] }, tag: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.sm, backgroundColor: color.surface.tint }, actions: { flexDirection: 'row', gap: spacing[2] }, action: { minWidth: 72, minHeight: 44, borderRadius: radius.sm, backgroundColor: color.surface.subtle, alignItems: 'center', justifyContent: 'center' }, cta: { minHeight: 50, marginTop: spacing[4], backgroundColor: color.brand.navy }, reason: { textAlign: 'center', marginTop: spacing[2] }, tripRef: { textAlign: 'center', marginTop: spacing[4] } });
