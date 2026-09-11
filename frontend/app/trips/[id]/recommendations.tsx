import { useCallback, useEffect, useState } from 'react';
import { Image, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { sendAppEvent } from '@/analytics/appEvents';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Skeleton } from '@/components/Skeleton';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { loadRecommendationResult, type RecommendationCourse, type RecommendationViewModel, unavailableRecommendations } from '@/plan/recommendations';

function CourseCard({ course, onAction }: { course: RecommendationCourse; onAction: (state: RecommendationCourse['actionState']) => void }) {
  const { tx } = useI18n();
  const STATUS = { VERIFIED: tx('확인됨', 'Verified'), ESTIMATED: tx('추정', 'Estimated'), UNKNOWN: tx('미확인', 'Unconfirmed') } as const;
  const CROWD = { LOW: tx('여유', 'Light'), MEDIUM: tx('보통', 'Moderate'), HIGH: tx('혼잡', 'Crowded') } as const;
  const FALLBACK = { MODEL: tx('개인화 추천', 'Personalized recommendation'), RULE: tx('조건 기반 추천', 'Condition-based recommendation'), BASELINE: tx('기본 추천', 'Baseline recommendation') } as const;
  return <View style={styles.card}>{course.imageUrl ? <Image source={{ uri: course.imageUrl }} accessibilityLabel={tx(`${course.title} 대표 이미지`, `${course.title} cover image`)} resizeMode="cover" style={styles.image} /> : <View accessibilityLabel={tx('대표 이미지 없음', 'No cover image')} style={styles.imageFallback}><Text variant="caption" color={color.text.muted}>{tx('이미지 정보 없음', 'No image available')}</Text></View>}<View style={styles.cardBody}><View style={styles.titleRow}><Text variant="title" weight="bold" style={styles.grow}>{course.title}</Text><View style={styles.status}><Text variant="caption" weight="bold">{STATUS[course.dataStatus]}</Text></View></View><View style={styles.tags}>{course.reasons.map((reason, index) => <View key={`${reason}-${index}`} style={styles.tag}><Text variant="caption" weight="bold" color={color.brand.orange}>#{reason}</Text></View>)}</View><Text variant="caption" color={color.text.body}>{tx('예상 비용', 'Estimated cost')} · {course.estimatedCostKrw == null ? tx('미확인', 'Unconfirmed') : `${course.estimatedCostKrw.toLocaleString()}${tx('원', ' KRW')}`}</Text><Text variant="caption" color={color.text.body}>{tx('혼잡도', 'Crowd level')} · {course.crowdLevel ? CROWD[course.crowdLevel] : tx('미확인', 'Unconfirmed')}</Text><Text variant="caption" color={course.mobilityWarnings?.length ? color.state.danger : color.text.body}>{tx('이동 제약', 'Mobility constraints')} · {course.mobilityWarnings?.join(' · ') || tx('확인된 경고 없음', 'No warnings found')}</Text><Text variant="caption" color={color.text.muted}>{FALLBACK[course.fallbackMode]}</Text><View style={styles.actions}><Pressable accessibilityRole="button" accessibilityState={{ selected: course.actionState === 'saved', busy: course.actionState === 'saving' }} onPress={() => onAction(course.actionState === 'saved' ? 'idle' : 'saved')} style={styles.action}><Text variant="caption" weight="bold">{course.actionState === 'saved' ? tx('저장됨', 'Saved') : tx('저장', 'Save')}</Text></Pressable><Pressable accessibilityRole="button" accessibilityState={{ selected: course.actionState === 'excluded', busy: course.actionState === 'excluding' }} onPress={() => onAction(course.actionState === 'excluded' ? 'idle' : 'excluded')} style={styles.action}><Text variant="caption" weight="bold">{course.actionState === 'excluded' ? tx('제외됨', 'Excluded') : tx('제외', 'Exclude')}</Text></Pressable></View></View></View>;
}

export default function Recommendations() {
  const router = useRouter(); const { accessToken } = useAuth(); const { tx } = useI18n(); const { id, jobId } = useLocalSearchParams<{ id: string; jobId?: string }>();
  const [view, setView] = useState<RecommendationViewModel>(() => jobId ? { ...unavailableRecommendations(), state: 'loading', message: tx('추천 결과를 확인하고 있어요.', 'Checking your recommendation result.') } : unavailableRecommendations());
  const reload = useCallback(async () => {
    if (!jobId) { setView(unavailableRecommendations()); return; }
    setView((current) => ({ ...current, state: 'loading', message: tx('추천 결과를 확인하고 있어요.', 'Checking your recommendation result.') }));
    setView(await loadRecommendationResult(jobId, accessToken));
  }, [accessToken, jobId, tx]);
  useEffect(() => { void reload(); }, [reload]);
  // 저장·제외는 지금까지 화면 상태만 바꿨다. 이제 서버로도 간다 — 노출된 것 중에서 고른 것이라
  // 가장 깨끗한 취향 신호다. 되돌리기(idle)는 아무 뜻이 아니라 보내지 않는다.
  //
  // 🔴 전송은 setView 의 갱신 함수 밖에서 한다. 그 안에서 하면 React 가 갱신 함수를 두 번 부를 때
  //    이벤트도 두 건 적힌다. 그리고 버튼은 서버 응답을 기다리지 않는다.
  const updateAction = (courseId: string, actionState: RecommendationCourse['actionState']) => {
    setView((current) => ({ ...current, courses: current.courses.map((course) => course.id === courseId ? { ...course, actionState } : course) }));
    if (actionState === 'saved' || actionState === 'excluded') {
      sendAppEvent({ type: actionState === 'saved' ? 'place_like' : 'place_dislike', accessToken, tripId: id, payload: { place_id: courseId, surface: 'recommendations' } });
    }
  };
  const unavailable = view.state === 'unavailable' || view.state === 'empty-conflict';
  return <View style={styles.shell}><Screen scroll wide style={styles.canvas}><View style={styles.nav}><Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/plan/confirm')} style={styles.back}><Text variant="title">‹</Text></Pressable><View style={styles.dots}><View style={styles.dot} /><View style={styles.dot} /><View style={styles.activeDot} /></View></View><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('추천 일정 요약', 'Recommendation summary')}</Text><Text variant="display" weight="bold" style={styles.heading}>{view.state === 'success' || view.state === 'partial' || view.state === 'fallback' ? tx(`${view.courses.length}가지 코스를 골라봤어요.`, `We picked ${view.courses.length} courses for you.`) : tx('추천 결과', 'Recommendation result')}</Text>
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
      {unavailable && <View style={styles.stateCard}><View style={styles.emptyMark}><Text variant="display">⌁</Text></View><Text variant="title" weight="bold">{view.state === 'empty-conflict' ? tx('조건을 만족하는 코스가 없어요', 'No course matched your conditions') : tx('아직 생성된 추천이 없어요', 'No recommendations yet')}</Text><Text color={color.text.body}>{view.message}</Text>{view.conflicts.map((item) => <Text key={item} accessibilityRole="alert" variant="caption" color={color.state.danger}>• {item}</Text>)}<Button accessibilityRole="button" label={tx('조건 수정하기', 'Edit conditions')} variant="ghost" onPress={() => router.push('/plan/confirm')} />{jobId && <Button accessibilityRole="button" label={tx('다시 확인', 'Check again')} variant="ghost" onPress={() => void reload()} />}</View>}
      <View style={styles.list}>{view.courses.map((course) => <CourseCard key={course.id} course={course} onAction={(state) => updateAction(course.id, state)} />)}</View>
      <Button accessibilityRole="button" accessibilityState={{ disabled: !view.itineraryId }} label={tx('이 일정으로 보기', 'View this itinerary')} disabled={!view.itineraryId} containerStyle={styles.cta} onPress={() => { if (view.itineraryId) router.push(`/trips/${view.itineraryId}/itinerary`); }} />
      {!view.itineraryId && <Text variant="caption" color={color.text.muted} style={styles.reason}>{tx('완성된 일정이 생기면 상세 일정으로 이동할 수 있어요.', 'You can move to the full itinerary once it’s ready.')}</Text>}
      <Text variant="caption" color={color.text.muted} style={styles.tripRef}>{tx(`여행 ${id || '미확인'}`, `Trip ${id || 'unconfirmed'}`)}</Text>
    </Screen><TabBar active="schedule" /></View>;
}
const styles = StyleSheet.create({ shell: { flex: 1, backgroundColor: color.brand.ivory }, canvas: { backgroundColor: color.brand.ivory }, nav: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, dots: { flexDirection: 'row', gap: spacing[1] }, dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: color.surface.field }, activeDot: { width: 18, height: 6, borderRadius: 3, backgroundColor: color.brand.orange }, heading: { marginTop: spacing[1] }, summary: { marginBottom: spacing[4] }, stateCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border, alignItems: 'center' }, emptyMark: { width: 64, height: 64, borderRadius: radius.full, backgroundColor: '#ede9e0', alignItems: 'center', justifyContent: 'center' }, notice: { marginBottom: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg }, list: { gap: spacing[3] }, card: { overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: .08, shadowRadius: 12, elevation: 2 }, image: { width: '100%', height: 140 }, imageFallback: { width: '100%', height: 120, backgroundColor: '#ede9e0', alignItems: 'center', justifyContent: 'center' }, cardBody: { gap: spacing[2], padding: spacing[4] }, titleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, grow: { flex: 1 }, status: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft }, tags: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[1] }, tag: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.sm, backgroundColor: color.surface.tint }, actions: { flexDirection: 'row', gap: spacing[2] }, action: { minWidth: 72, minHeight: 44, borderRadius: radius.sm, backgroundColor: color.surface.subtle, alignItems: 'center', justifyContent: 'center' }, cta: { minHeight: 50, marginTop: spacing[4], backgroundColor: color.brand.navy }, reason: { textAlign: 'center', marginTop: spacing[2] }, tripRef: { textAlign: 'center', marginTop: spacing[4] } });
