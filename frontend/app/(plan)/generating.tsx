import { useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, Animated, Easing, Image, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { PlanDesktopShell } from '@/plan/PlanDesktopShell';
import { usePlan } from '@/plan/PlanProvider';
import { createRecommendationJobAdapter, type RecommendationJobSnapshot, unavailableJob } from '@/plan/recommendationJob';
import { loadRecommendationResult } from '@/plan/recommendations';
import { loadItinerary, type ItineraryDto } from '@/plan/itinerary';
import { useI18n } from '@/i18n';

// S15P21E201-919: 백엔드 JobStage.java(CREATED·VERSION_RESOLUTION·CANDIDATE_GENERATION·
// CONSTRAINT_EVALUATION·FEATURE_LOOKUP·RANKING·ROUTE_OPTIMIZATION·PERSISTENCE·COMPLETED)의
// 실제 값을 키에 넣는다 — 예전 키(COLLECT·FILTER·ROUTE·OPTIM·SCHEDULE)는 이 값들과 하나도
// 안 겹쳐서 진행 단계 매칭이 사실상 항상 실패하고 있었다.
const STAGES = [
  { keys: ['접수', '후보', 'COLLECT', 'CANDIDATE', 'CREATED', 'VERSION_RESOLUTION'], label: '후보 장소 수집', en: 'Collect candidate places' },
  { keys: ['제약', '안전', 'FILTER', 'CONSTRAINT', 'FEATURE_LOOKUP'], label: '추천·안전 조건 판정', en: 'Check preferences and safety' },
  { keys: ['동선', '경로', 'ROUTE', 'OPTIM', 'RANKING'], label: '최적 동선 계산', en: 'Optimize routes' },
  { keys: ['배치', '시간표', '일정', 'SCHEDULE', 'PERSISTENCE'], label: '시간표 배치', en: 'Build the schedule' },
] as const;

function findStage(stage: string | null) {
  if (!stage) return null;
  const normalized = stage.toUpperCase();
  return STAGES.find((item) => item.keys.some((key) => normalized.includes(key.toUpperCase()))) ?? null;
}
function stageIndex(stage: string | null, state: RecommendationJobSnapshot['state']) {
  if (state === 'completed') return STAGES.length;
  if (!stage) return state === 'accepted' ? 0 : -1;
  const found = STAGES.findIndex((item) => item === findStage(stage));
  return found < 0 ? 0 : found;
}
// 매칭 안 되는 원문 코드를 화면에 그대로 보여주지 않는다 — 알려진 단계면 사람이 읽는
// 라벨로, 모르는 값이면 일반적인 안내 문구로 떨어뜨린다.
function stageLabel(stage: string | null, tx: (ko: string, en: string) => string): string | null {
  const found = findStage(stage);
  return found ? tx(found.label, found.en) : stage ? tx('처리 중', 'Processing') : null;
}
function daysBetween(start: string, end: string) { const value = Math.round((new Date(`${end}T00:00:00`).getTime() - new Date(`${start}T00:00:00`).getTime()) / 86400000) + 1; return Number.isFinite(value) && value > 0 ? value : 1; }
function timeLabel(value: string) { return value.match(/T(\d{2}:\d{2})/)?.[1] ?? value.match(/^(\d{2}:\d{2})/)?.[1] ?? ''; }

const PREVIEW_ITINERARY: ItineraryDto = {
  id: 'preview-trip', title: '부산 바다와 로컬 맛집 여행', version: 1,
  days: [{ date: '2026-09-12', items: [
    { id: 'preview-1', startsAt: '2026-09-12T09:30:00', title: '해운대 바다 산책', locked: false, placeId: 'preview-place-1' },
    { id: 'preview-2', startsAt: '2026-09-12T12:00:00', title: '로컬 맛집', locked: false, placeId: 'preview-place-2' },
    { id: 'preview-3', startsAt: '2026-09-12T17:30:00', title: '광안리 노을', locked: false, placeId: 'preview-place-3' },
  ] }], totalEstimatedCostKrw: 78000, totalWalkingMeters: 3200, fallbackMode: 'MODEL',
};

export default function Generating() {
  const router = useRouter(); const { kind } = useLayout(); const { tx, language, locale } = useI18n(); const { accessToken } = useAuth(); const { draft, clear } = usePlan();
  const { jobId, preview } = useLocalSearchParams<{ jobId?: string; preview?: string }>();
  const adapter = useMemo(() => createRecommendationJobAdapter(accessToken), [accessToken]);
  const previewJob = __DEV__ && preview === 'completed' ? { state: 'completed', jobId: 'preview', progress: 100, stage: '시간표 배치', canCancel: false, errorMessage: null, resultRef: 'preview-trip' } satisfies RecommendationJobSnapshot : __DEV__ && preview === 'running' ? { state: 'polling', jobId: 'preview', progress: 48, stage: '최적 동선 계산', canCancel: false, errorMessage: null, resultRef: null } satisfies RecommendationJobSnapshot : __DEV__ && preview === 'failed' ? { state: 'failed', jobId: 'preview', progress: null, stage: null, canCancel: false, errorMessage: '조건에 맞는 장소를 찾지 못했어요. 날짜·예산·취향 조건을 조금 넓혀서 다시 시도해 주세요.', resultRef: null } satisfies RecommendationJobSnapshot : null;
  const [job, setJob] = useState<RecommendationJobSnapshot>(() => previewJob ?? (jobId ? { state: 'accepted', jobId, progress: 0, stage: tx('요청 접수', 'Request received'), canCancel: false, errorMessage: null, resultRef: null } : unavailableJob(tx('생성 요청을 찾을 수 없어요. 조건을 확인한 뒤 다시 시작해 주세요.', 'Could not find the generation request. Please review your conditions and try again.'))));
  const [itinerary, setItinerary] = useState<ItineraryDto | null>(() => previewJob?.state === 'completed' ? PREVIEW_ITINERARY : null);
  const [itineraryMessage, setItineraryMessage] = useState<string | null>(null);
  const [delayed, setDelayed] = useState(false); const [reduceMotion, setReduceMotion] = useState(false); const ticketReveal = useRef(new Animated.Value(0)).current; const jobRef = useRef(job);
  const currentStage = stageIndex(job.stage, job.state); const isWorking = job.state === 'accepted' || job.state === 'polling';
  useEffect(() => { jobRef.current = job; }, [job]);
  useEffect(() => { void AccessibilityInfo.isReduceMotionEnabled().then(setReduceMotion); const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion); return () => subscription.remove(); }, []);
  useEffect(() => {
    if (!isWorking || previewJob || !job.jobId) return;
    const activeJobId = job.jobId;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const delayTimer = setTimeout(() => setDelayed(true), 10000);
    const poll = async () => {
      const next = await adapter.poll(activeJobId, jobRef.current);
      if (cancelled) return;
      setJob(next);
      if (next.state === 'accepted' || next.state === 'polling') timer = setTimeout(poll, 2000);
    };
    timer = setTimeout(poll, 2000);
    return () => { cancelled = true; clearTimeout(delayTimer); if (timer) clearTimeout(timer); };
  }, [adapter, isWorking, job.jobId, previewJob]);
  useEffect(() => {
    if (job.state !== 'completed' || previewJob || !job.jobId) return;
    let cancelled = false;
    const load = async () => {
      setItineraryMessage(null);
      const recommendation = await loadRecommendationResult(job.jobId!, accessToken);
      if (cancelled) return;
      if (!recommendation.itineraryId) {
        setItineraryMessage(recommendation.message);
        return;
      }
      const result = await loadItinerary(recommendation.itineraryId, accessToken);
      if (cancelled) return;
      if (result.state === 'success') { setItinerary(result.itinerary); void clear(); }
      else setItineraryMessage(result.message);
    };
    void load();
    return () => { cancelled = true; };
  }, [accessToken, job.jobId, job.state, previewJob]);
  useEffect(() => {
    ticketReveal.stopAnimation();
    if (reduceMotion) { ticketReveal.setValue(job.state === 'completed' ? 1 : 0); return; }
    if (job.state !== 'completed') { ticketReveal.setValue(0); return; }
    ticketReveal.setValue(0);
    Animated.sequence([
      Animated.delay(520),
      Animated.timing(ticketReveal, { toValue: 0.2, duration: 420, easing: Easing.linear, useNativeDriver: false }),
      Animated.timing(ticketReveal, { toValue: 0.46, duration: 480, easing: Easing.linear, useNativeDriver: false }),
      Animated.timing(ticketReveal, { toValue: 0.74, duration: 520, easing: Easing.linear, useNativeDriver: false }),
      Animated.timing(ticketReveal, { toValue: 1, duration: 560, easing: Easing.out(Easing.cubic), useNativeDriver: false }),
    ]).start();
  }, [job.state, reduceMotion, ticketReveal]);
  const duration = itinerary?.days.length || daysBetween(draft.startDate, draft.endDate); const areas = itinerary?.title || (draft.travelAreas.length ? draft.travelAreas.join(' · ') : tx('부산 맞춤 여행', 'Personalized Busan trip')); const failed = ['failed', 'conflict', 'cancelled', 'unavailable'].includes(job.state);
  const itineraryStops = itinerary?.days.flatMap((day) => day.items).slice(0, 3) ?? [];
  const visitCount = itinerary?.days.reduce((sum, day) => sum + day.items.length, 0) ?? 0;
  const actualStartDate = itinerary?.days[0]?.date || draft.startDate;
  const actualEndDate = itinerary?.days.at(-1)?.date || draft.endDate;
  const printedHeight = ticketReveal.interpolate({ inputRange: [0, 1], outputRange: [0, 560] });
  return <PlanDesktopShell><Screen scroll wide style={styles.canvas}>
    {kind === 'phone' && <View style={styles.mobileTop}><Pressable accessibilityRole="button" accessibilityLabel={tx('조건 확인으로 돌아가기', 'Back to trip review')} onPress={() => router.replace('/plan/confirm')} style={styles.back}><Text variant="title">‹</Text></Pressable><BrandLogoLink imageStyle={styles.logo} /><View style={styles.stepPill}><Text variant="caption" weight="bold" color={color.brand.ivory}>{tx('생성', 'Generate')}</Text></View></View>}
    <View style={[styles.layout, kind !== 'phone' && styles.layoutWide]}>
      {!(kind === 'phone' && job.state === 'completed') && <View style={[styles.statusPanel, kind !== 'phone' && styles.statusWide]}>
        <View style={styles.aiBadge}><View style={[styles.pulse, isWorking && styles.pulseActive]} /><Text variant="caption" weight="bold" color={color.brand.orange}>{job.state === 'completed' ? tx('AI 일정 완성', 'AI itinerary ready') : failed ? tx('일정 생성 실패', 'Itinerary generation failed') : tx('AI 일정 생성 중', 'Creating your itinerary')}</Text></View>
        <Text variant="display" weight="bold" color={color.brand.ivory} style={styles.headline}>{job.state === 'completed' ? tx('당신만의 부산 여행이\n완성됐어요', 'Your Busan trip\nis ready') : failed ? tx('일정을 만들지\n못했어요', "We couldn't build\nyour itinerary") : tx('AI가 당신만을 위한\n부산 여행을 만들고 있어요', 'AI is building\nyour Busan trip')}</Text>
        <Text color="#a2a7b8">{failed ? job.errorMessage : delayed && isWorking ? tx('부산 동선을 조금 더 다듬고 있어요. 화면을 닫아도 작업은 계속됩니다.', 'We are refining your route through Busan. The job continues if you leave this screen.') : tx('현지 정보와 안전 조건, 이동 부담을 함께 확인하고 있어요.', 'We are checking local information, safety, and travel effort together.')}</Text>
        {!failed && <View accessibilityLiveRegion="polite" style={styles.stageList}>{STAGES.map((item, index) => { const done = index < currentStage || job.state === 'completed'; const active = index === currentStage && isWorking; return <View key={item.label} style={[styles.stage, active && styles.stageActive]}><View style={[styles.stageIcon, done && styles.stageDone]}><Text variant="caption" weight="bold" color={done ? color.text.onAction : active ? color.brand.orange : '#6e7280'}>{done ? '✓' : '○'}</Text></View><Text weight={done || active ? 'bold' : 'regular'} color={done || active ? color.brand.ivory : '#6e7280'} style={styles.stageText}>{language === 'en' ? item.en : item.label}</Text><Text variant="caption" color={done ? color.state.success : active ? color.brand.orange : '#6e7280'}>{done ? tx('완료', 'Done') : active ? tx('진행 중', 'In progress') : tx('대기', 'Waiting')}</Text></View>; })}</View>}
        {job.progress !== null && !failed && <View style={styles.progressBlock}><View style={styles.progressTrack}><View style={[styles.progressFill, { width: `${job.progress}%` }]} /></View><Text variant="caption" color={color.brand.orange}>{stageLabel(job.stage, tx) ?? tx('요청 접수', 'Request received')} · {job.progress}%</Text></View>}
        {failed && <Button label={tx('조건 다시 확인하기', 'Review trip details')} onPress={() => router.replace('/plan/confirm')} containerStyle={styles.retry} />}
      </View>}
      <View style={styles.ticketArea}><View style={styles.printer}><View style={styles.printerLabel}><Text variant="caption" weight="bold" color={color.brand.ivory}>{job.state === 'completed' ? tx('여행표 출력 중', 'PRINTING TRIP PASS') : failed ? tx('일정 조립 중단', 'BUILD STOPPED') : tx('일정 조립 중', 'BUILDING ITINERARY')}</Text></View><View style={styles.lights}><View style={[styles.light, styles.green]} /><View style={[styles.light, styles.orange]} /><View style={styles.light} /></View><View style={styles.slot} /></View>
        <View style={styles.ticketViewport}><Animated.View style={[styles.printedPaper, { height: printedHeight, opacity: failed ? 0.45 : 1 }]}><View style={styles.ticket}><View style={styles.ticketHeader}><View style={styles.ticketBrand}><Image source={require('../../assets/brand/gabolle-logo-night.png')} resizeMode="contain" style={styles.ticketLogo} /><Text variant="caption" weight="bold" color={color.brand.ivory} style={styles.passLabel}>TRIP PASS</Text></View><View style={styles.flightMark}><Text weight="bold" color={color.brand.orange} style={styles.flightIcon}>✈</Text></View></View><View style={styles.ticketBody}><Text variant="caption" color={color.text.muted}>{tx('나만의 부산 여행', 'MY BUSAN JOURNEY')}</Text><Text variant="display" weight="bold" style={styles.destination}>BUSAN</Text><Text variant="caption" weight="bold" color={color.brand.orange}>{areas}</Text><View style={styles.routeLine}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('여행 시작', 'START')}</Text><View style={styles.routeTrack} /><Text weight="bold" color={color.brand.orange} style={styles.routePlane}>✈</Text><View style={styles.routeTrack} /><Text variant="caption" weight="bold" color={color.brand.orange}>BUSAN</Text></View><View style={styles.dash} /><View style={styles.ticketMeta}><View style={styles.metaCell}><Text variant="caption" color={color.text.muted}>DATE</Text><Text variant="caption" weight="bold">{actualStartDate || tx('확인 중', 'PENDING')}{actualEndDate && actualEndDate !== actualStartDate ? ` – ${actualEndDate}` : ''}</Text></View><View style={styles.metaCell}><Text variant="caption" color={color.text.muted}>DAYS</Text><Text variant="caption" weight="bold">{tx(`${duration}일`, `${duration} DAYS`)}</Text></View><View style={styles.metaCell}><Text variant="caption" color={color.text.muted}>PLACES</Text><Text variant="caption" weight="bold">{itinerary ? tx(`${visitCount}곳`, `${visitCount} STOPS`) : tx('확인 중', 'PENDING')}</Text></View></View>{itineraryStops.length > 0 && <><View style={styles.dash} /><View style={styles.previewRoute}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('미리보기 일정', 'PREVIEW ITINERARY')}</Text>{itineraryStops.map((stop, index) => <View key={stop.id} style={styles.receiptRow}><Text variant="caption" color={color.text.muted}>{String(index + 1).padStart(2, '0')}</Text><Text variant="caption" weight="bold" style={styles.receiptStop}>{timeLabel(stop.startsAt)}{timeLabel(stop.startsAt) ? ' · ' : ''}{stop.title}</Text></View>)}</View></>}<View style={styles.memoRow}><View style={styles.memo}><Text variant="caption" color={color.text.muted}>MEMO</Text><Text variant="caption" weight="bold">{itinerary?.totalEstimatedCostKrw != null ? tx(`예상 비용 ${itinerary.totalEstimatedCostKrw.toLocaleString(locale)}원`, `EST. KRW ${itinerary.totalEstimatedCostKrw.toLocaleString(locale)}`) : itinerary?.totalWalkingMeters != null ? tx(`예상 도보 ${itinerary.totalWalkingMeters.toLocaleString(locale)}m`, `EST. WALK ${itinerary.totalWalkingMeters.toLocaleString(locale)}m`) : tx('좋은 추억 가득 만들어요!', 'MAKE WONDERFUL MEMORIES!')}</Text>{itineraryMessage && <Text variant="caption" color={color.text.muted}>{itineraryMessage}</Text>}</View><Image source={require('../../assets/brand/busan-travel-stamp-v3-transparent.png')} resizeMode="contain" style={styles.stampImage} /></View></View><View style={styles.perforation}><View style={styles.perforationCut} /></View><View style={styles.ticketTail}><Text variant="caption" color={color.text.muted}>{job.state === 'completed' ? 'READY TO BOARD' : failed ? tx('생성 실패', 'GENERATION FAILED') : stageLabel(job.stage, tx) ?? tx('AI 생성 대기 중', 'Waiting for AI')}</Text><Text variant="caption" weight="bold">GABOLLE · BUSAN</Text></View></View></Animated.View></View>
        {job.state === 'completed' && <View style={styles.actions}><Button label={tx('일정 자세히 보기', 'View itinerary details')} onPress={() => job.jobId && router.replace(`/trips/${job.jobId}/recommendations?jobId=${encodeURIComponent(job.jobId)}`)} disabled={!job.jobId} /><View style={styles.secondaryActions}><Button label={tx('여행표 저장', 'Save trip pass')} variant="ghost" disabled /><Button label={tx('공유', 'Share')} variant="ghost" disabled /></View><Text variant="caption" color={color.text.muted}>{tx('추천 후보를 확인한 뒤 완성된 일정으로 이동할 수 있어요.', 'Review the recommendations, then open your completed itinerary.')}</Text></View>}
      </View>
    </View>
  </Screen></PlanDesktopShell>;
}
const styles = StyleSheet.create({ canvas: { backgroundColor: color.brand.ivory }, mobileTop: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[4] }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, logo: { width: 88, height: 28 }, stepPill: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.brand.navy }, layout: { gap: spacing[4] }, layoutWide: { minHeight: 720, flexDirection: 'row', alignItems: 'stretch' }, statusPanel: { gap: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.navy }, statusWide: { width: '44%', padding: spacing[8], justifyContent: 'center' }, aiBadge: { alignSelf: 'flex-start', flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.08)' }, pulse: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: '#4a5568' }, pulseActive: { backgroundColor: color.brand.orange }, headline: { lineHeight: 32 }, stageList: { gap: spacing[3] }, stage: { minHeight: 54, flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: 'rgba(255,255,255,0.03)' }, stageActive: { borderWidth: 1, borderColor: 'rgba(242,101,50,0.45)', backgroundColor: 'rgba(242,101,50,0.08)' }, stageIcon: { width: 28, height: 28, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.06)' }, stageDone: { backgroundColor: color.state.success }, stageText: { flex: 1 }, progressBlock: { gap: spacing[2] }, progressTrack: { height: 6, overflow: 'hidden', borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.08)' }, progressFill: { height: 6, borderRadius: radius.full, backgroundColor: color.brand.orange }, retry: { backgroundColor: color.brand.orange }, ticketArea: { flex: 1, alignItems: 'center', justifyContent: 'flex-start', padding: spacing[6], borderRadius: radius.lg, backgroundColor: '#f0eee8' }, printer: { zIndex: 4, width: '100%', maxWidth: 400, height: 116, alignItems: 'center', justifyContent: 'center', gap: spacing[2], borderRadius: radius.lg, backgroundColor: color.brand.navy, shadowColor: color.brand.navy, shadowOpacity: 0.2, shadowRadius: 12, shadowOffset: { width: 0, height: 6 }, elevation: 8 }, printerLabel: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.08)' }, lights: { flexDirection: 'row', gap: spacing[1] }, light: { width: 6, height: 6, borderRadius: radius.full, backgroundColor: '#4a5568' }, green: { backgroundColor: color.state.success }, orange: { backgroundColor: color.brand.orange }, slot: { width: '70%', height: 10, borderRadius: radius.full, backgroundColor: '#061328' }, ticketViewport: { width: '100%', height: 560, marginTop: -8, overflow: 'hidden', alignItems: 'center' }, printedPaper: { width: '82%', maxWidth: 300, overflow: 'hidden', backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: 0.16, shadowRadius: 10, shadowOffset: { width: 0, height: 6 }, elevation: 4 }, ticket: { position: 'absolute', bottom: 0, width: '100%', height: 560, borderLeftWidth: 1, borderRightWidth: 1, borderColor: '#ddd5ca', backgroundColor: color.surface.card }, ticketHeader: { minHeight: 92, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: spacing[4], backgroundColor: color.brand.navy }, ticketBrand: { gap: spacing[1] }, ticketLogo: { width: 140, height: 34, marginLeft: -10 }, passLabel: { letterSpacing: 2.4 }, flightMark: { width: 56, height: 56, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.ivory }, flightIcon: { fontSize: 30, lineHeight: 34 }, ticketBody: { gap: spacing[3], padding: spacing[4] }, destination: { fontSize: 32, lineHeight: 38, letterSpacing: 2 }, routeLine: { height: 38, flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, routeTrack: { flex: 1, borderTopWidth: 1, borderStyle: 'dashed', borderColor: '#c8cace' }, routePlane: { fontSize: 20, lineHeight: 24, transform: [{ rotate: '8deg' }] }, ticketMeta: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing[2] }, metaCell: { flex: 1, gap: 2 }, previewRoute: { gap: spacing[2] }, receiptRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, receiptStop: { flex: 1 }, memoRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, memo: { flex: 1, gap: 2 }, stampImage: { width: 78, height: 78, transform: [{ rotate: '-7deg' }] }, dash: { borderTopWidth: 1, borderStyle: 'dashed', borderColor: '#c8cace' }, perforation: { position: 'absolute', left: 0, right: 0, bottom: 46, height: 10, justifyContent: 'center', backgroundColor: color.surface.card }, perforationCut: { borderTopWidth: 1, borderStyle: 'dashed', borderColor: '#c8cace' }, ticketTail: { position: 'absolute', left: 0, right: 0, bottom: 0, minHeight: 46, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: spacing[3], backgroundColor: color.surface.subtle }, actions: { width: '88%', maxWidth: 320, gap: spacing[2], marginTop: spacing[4] }, secondaryActions: { flexDirection: 'row', gap: spacing[2] } });
