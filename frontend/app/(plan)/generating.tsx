import { useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, Animated, Easing, Image, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useAuth } from '@/auth/AuthProvider';
import { AccessibilityUnverifiedModal } from '@/components/AccessibilityUnverifiedModal';
import { Button } from '@/components/Button';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { usePlan } from '@/plan/PlanProvider';
import { adaptStreamedJob, createRecommendationJobAdapter, type RecommendationJobSnapshot, unavailableJob } from '@/plan/recommendationJob';
import { openJobProgressStream, supportsJobProgressStream } from '@/plan/recommendationJobStream';
import { loadRecommendationResult } from '@/plan/recommendations';
import { loadItinerary, type ItineraryDto } from '@/plan/itinerary';
import { TripPass } from '@/plan/TripPass';
import { buildTripPass, buildTripPassDetails } from '@/plan/tripPassData';
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
// 🔴 S15P21E201-1160 — 서버가 이미 보내고 있는 값이다. 새로 만들 필요가 없었다.
//
//    RecommendationResultQueryService 가 후보의 경고를 두 군데로 내보낸다 — 최상위
//    conflicts(중복 없는 집합)와 항목별 mobilityWarnings. 이 화면은 그동안 둘 다 받아
//    놓고 안 읽고 있었다. "확인 안 된 곳이 있다" 를 알려면 서버를 고칠 필요가 없다.
//
//    코드 값은 백엔드 BaselineCandidateScorer.ACCESSIBILITY_UNVERIFIED_WARNING 과 같은
//    글자여야 한다. 저장되는 값과 응답에 나가는 값이 중간에 안 바뀌는 것을 확인했다.
const ACCESSIBILITY_UNVERIFIED = 'ACCESSIBILITY_UNVERIFIED';

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
  const router = useRouter(); const { kind } = useLayout(); const { tx, language, locale } = useI18n(); const { accessToken, user } = useAuth(); const { draft, clear } = usePlan();
  const { jobId, preview } = useLocalSearchParams<{ jobId?: string; preview?: string }>();
  const adapter = useMemo(() => createRecommendationJobAdapter(accessToken), [accessToken]);
  const previewJob = __DEV__ && preview === 'completed' ? { state: 'completed', jobId: 'preview', progress: 100, stage: '시간표 배치', canCancel: false, errorMessage: null, resultRef: 'preview-trip' } satisfies RecommendationJobSnapshot : __DEV__ && preview === 'running' ? { state: 'polling', jobId: 'preview', progress: 48, stage: '최적 동선 계산', canCancel: false, errorMessage: null, resultRef: null } satisfies RecommendationJobSnapshot : __DEV__ && preview === 'failed' ? { state: 'failed', jobId: 'preview', progress: null, stage: null, canCancel: false, errorMessage: '조건에 맞는 장소를 찾지 못했어요. 날짜·예산·취향 조건을 조금 넓혀서 다시 시도해 주세요.', resultRef: null } satisfies RecommendationJobSnapshot : null;
  const [job, setJob] = useState<RecommendationJobSnapshot>(() => previewJob ?? (jobId ? { state: 'accepted', jobId, progress: 0, stage: tx('요청 접수', 'Request received'), canCancel: false, errorMessage: null, resultRef: null } : unavailableJob(tx('생성 요청을 찾을 수 없어요. 조건을 확인한 뒤 다시 시작해 주세요.', 'Could not find the generation request. Please review your conditions and try again.'))));
  const [itinerary, setItinerary] = useState<ItineraryDto | null>(() => previewJob?.state === 'completed' ? PREVIEW_ITINERARY : null);
  const [itineraryMessage, setItineraryMessage] = useState<string | null>(null);
  // 접근성 안내 창 — 확인 안 된 곳이 하나라도 있으면 한 번만 뜬다 (S15P21E201-1160).
  // warnedJobRef 가 "한 번만" 을 지킨다. 이 화면은 스트림·폴링·재렌더로 같은 완료 상태를
  // 여러 번 지나가므로, 상태 하나로는 닫은 창이 다시 열린다.
  // preview=access 는 개발 중에만 도는 갈래다. 이 창은 서버가 접근성 경고를 실어 보낸
  // 추천에서만 뜨는데, 그 조건을 손으로 만들려면 로그인해서 실제 추천을 돌려야 한다.
  // 시안을 보거나 문구를 고칠 때마다 그걸 하는 것은 너무 비싸다.
  const [accessibilityNotice, setAccessibilityNotice] = useState<{ unverified: number; total: number } | null>(
    () => (__DEV__ && preview === 'access' ? { unverified: 9, total: 12 } : null),
  );
  const warnedJobRef = useRef<string | null>(null);
  const [delayed, setDelayed] = useState(false); const [reduceMotion, setReduceMotion] = useState(false); const ticketReveal = useRef(new Animated.Value(0)).current; const jobRef = useRef(job);
  const currentStage = stageIndex(job.stage, job.state); const isWorking = job.state === 'accepted' || job.state === 'polling';
  useEffect(() => { jobRef.current = job; }, [job]);
  useEffect(() => { void AccessibilityInfo.isReduceMotionEnabled().then(setReduceMotion); const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion); return () => subscription.remove(); }, []);
  useEffect(() => {
    if (!isWorking || previewJob || !job.jobId) return;
    const activeJobId = job.jobId;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let closeStream: (() => void) | null = null;
    const delayTimer = setTimeout(() => setDelayed(true), 10000);

    const startPolling = () => {
      if (timer) return; // 스트림이 오류를 두 번 알려도 폴링 루프가 중복으로 돌지 않는다.
      const poll = async () => {
        const next = await adapter.poll(activeJobId, jobRef.current);
        if (cancelled) return;
        setJob(next);
        if (next.state === 'accepted' || next.state === 'polling') timer = setTimeout(poll, 2000);
      };
      timer = setTimeout(poll, 2000);
    };

    // S15P21E201-69 — 열리면 실시간으로 받고, 실패하거나 지원하지 않으면 조용히
    // 폴링으로 갈아탄다. 화면 쪽에서는 어느 경로로 왔든 같은 setJob 이 받는다.
    if (supportsJobProgressStream()) {
      closeStream = openJobProgressStream(activeJobId, accessToken, {
        onSnapshot: (snapshot) => {
          if (cancelled) return;
          setJob(adaptStreamedJob(activeJobId, snapshot, jobRef.current));
        },
        onDone: () => { /* 서버가 끝 상태를 보내고 스스로 닫았다 — 더 할 일 없음 */ },
        onError: () => { if (!cancelled) startPolling(); },
      });
    }
    else {
      startPolling();
    }

    return () => {
      cancelled = true;
      clearTimeout(delayTimer);
      if (timer) clearTimeout(timer);
      closeStream?.();
    };
  }, [accessToken, adapter, isWorking, job.jobId, previewJob]);
  useEffect(() => {
    if (job.state !== 'completed' || previewJob || !job.jobId) return;
    let cancelled = false;
    const load = async () => {
      setItineraryMessage(null);
      const recommendation = await loadRecommendationResult(job.jobId!, accessToken);
      if (cancelled) return;
      // 🔴 일정을 못 읽어도 이 안내는 띄운다. 접근성은 일정이 열리는지와 별개로
      //    사용자가 알아야 하는 것이고, 아래 early return 뒤에 두면 그때 조용히 사라진다.
      if (warnedJobRef.current !== job.jobId && recommendation.conflicts.includes(ACCESSIBILITY_UNVERIFIED)) {
        warnedJobRef.current = job.jobId!;
        const unverified = recommendation.courses.filter((course) => course.mobilityWarnings?.includes(ACCESSIBILITY_UNVERIFIED)).length;
        // conflicts 에 코드가 있는데 항목에서 못 셌다면(서버 판이 달라 항목 경고가 안 올 수
        // 있다) 0곳이라고 말하지 않는다 — 그건 "확인됐다" 로 읽힌다. 분모 없이 알린다.
        setAccessibilityNotice({ unverified: unverified || recommendation.courses.length, total: unverified ? recommendation.courses.length : 0 });
      }
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
  // 🔴 S15P21E201-1016 — 종이가 다 나온 순간을 상태로 든다.
  //
  // 이 화면은 완료된 뒤에도 "여행표 출력 중" 과 "처리 중 · 100%" 를 그대로 띄우고 있었다.
  // 네 단계가 전부 "완료" 이고 결과 카드까지 그려졌는데도 그랬다(jaehyeon 님 운영 실측,
  // 2026-09-16, jobId ae437972). 사용자는 그것을 **아직 안 끝난 것**으로 읽는다 —
  // 실제로는 끝나 있는데도 기다리거나, 먹통이라고 생각하고 나간다.
  const [printed, setPrinted] = useState(false);
  /** 「다시 출력」을 누른 횟수. key 로 써서 출력 애니메이션을 처음부터 돌린다. */
  const [reprint, setReprint] = useState(0);
  useEffect(() => {
    ticketReveal.stopAnimation();
    if (job.state !== 'completed') { setPrinted(false); ticketReveal.setValue(0); return; }
    if (reduceMotion) { ticketReveal.setValue(1); setPrinted(true); return; }
    setPrinted(false);
    ticketReveal.setValue(0);
    Animated.sequence([
      Animated.delay(520),
      Animated.timing(ticketReveal, { toValue: 0.2, duration: 420, easing: Easing.linear, useNativeDriver: false }),
      Animated.timing(ticketReveal, { toValue: 0.46, duration: 480, easing: Easing.linear, useNativeDriver: false }),
      Animated.timing(ticketReveal, { toValue: 0.74, duration: 520, easing: Easing.linear, useNativeDriver: false }),
      Animated.timing(ticketReveal, { toValue: 1, duration: 560, easing: Easing.out(Easing.cubic), useNativeDriver: false }),
      // 🔴 끝났다는 표시는 애니메이션이 실제로 끝났을 때만 켠다. 시간을 재서 맞추면
      //    기기가 느릴 때 종이가 아직 나오는 중인데 "출력 완료" 라고 말한다.
    ]).start(({ finished }) => { if (finished) setPrinted(true); });
  }, [job.state, reduceMotion, ticketReveal]);
  const duration = itinerary?.days.length || daysBetween(draft.startDate, draft.endDate); const areas = itinerary?.title || (draft.travelAreas.length ? draft.travelAreas.join(' · ') : tx('부산 맞춤 여행', 'Personalized Busan trip')); const failed = ['failed', 'conflict', 'cancelled', 'unavailable'].includes(job.state);
  const itineraryStops = itinerary?.days.flatMap((day) => day.items).slice(0, 3) ?? [];
  const visitCount = itinerary?.days.reduce((sum, day) => sum + day.items.length, 0) ?? 0;
  const actualStartDate = itinerary?.days[0]?.date || draft.startDate;
  const actualEndDate = itinerary?.days.at(-1)?.date || draft.endDate;
  // 🔴 여행 티켓에 찍히는 값 — 시안(TripPassCard)대로 **실제 일정**에서 만든다
  //    (S15P21E201-1233). 계산은 tripPassData 가 하고 시험이 붙든다.
  const tripPass = buildTripPass({
    itinerary,
    baseUrl: process.env.EXPO_PUBLIC_API_BASE_URL ?? null,
    origin: draft.origin || null,
    startDate: draft.startDate || null,
    endDate: draft.endDate || null,
    transport: draft.transport || null,
    travelers: draft.travelers || null,
    ownerName: user?.displayName ?? null,
    language: language === 'en' ? 'en' : 'ko',
  });
  const tripPassDetails = buildTripPassDetails({
    itinerary, origin: draft.origin || null, startDate: draft.startDate || null, endDate: draft.endDate || null,
    transport: draft.transport || null, travelers: draft.travelers || null, ownerName: user?.displayName ?? null,
    language: language === 'en' ? 'en' : 'ko',
  });
  const printedHeight = ticketReveal.interpolate({ inputRange: [0, 1], outputRange: [0, 620] });
  return <Screen scroll wide style={styles.canvas}>
    {kind === 'phone' && <View style={styles.mobileTop}><Pressable accessibilityRole="button" accessibilityLabel={tx('조건 확인으로 돌아가기', 'Back to trip review')} onPress={() => router.replace('/plan')} style={styles.back}><Text variant="title">‹</Text></Pressable><BrandLogoLink imageStyle={styles.logo} /><View style={styles.stepPill}><Text variant="caption" weight="bold" color={color.brand.ivory}>{tx('생성', 'Generate')}</Text></View></View>}
    <View style={[styles.layout, kind !== 'phone' && styles.layoutWide]}>
      {!(kind === 'phone' && job.state === 'completed') && <View style={[styles.statusPanel, kind !== 'phone' && styles.statusWide]}>
        {/* 🔴 시안 p4 — 네이비는 **위에 가로로 눕는 띠**다. 왼쪽에 글, 오른쪽에 단계 넷을
            2열로 둔다. 전에는 왼쪽 44% 세로 칸이라 여행표가 옆으로 밀려 있었다. */}
        <View style={kind !== 'phone' ? styles.statusCopy : undefined}>
        <View style={styles.aiBadge}><View style={[styles.pulse, isWorking && styles.pulseActive]} /><Text variant="caption" weight="bold" color={color.brand.orange}>{job.state === 'completed' ? tx('AI 일정 완성', 'AI itinerary ready') : failed ? tx('일정 생성 실패', 'Itinerary generation failed') : tx('AI 일정 생성 중', 'Creating your itinerary')}</Text></View>
        <Text variant="display" weight="bold" color={color.brand.ivory} style={styles.headline}>{job.state === 'completed' ? tx(kind === 'phone' ? '당신만의 부산 여행이\n완성됐어요' : '당신만의 부산 여행이 완성됐어요', kind === 'phone' ? 'Your Busan trip\nis ready' : 'Your Busan trip is ready') : failed ? tx('일정을 만들지\n못했어요', "We couldn't build\nyour itinerary") : tx('AI가 당신만을 위한\n부산 여행을 만들고 있어요', 'AI is building\nyour Busan trip')}</Text>
        <Text color="#a2a7b8">{failed ? job.errorMessage : delayed && isWorking ? tx('부산 동선을 조금 더 다듬고 있어요. 화면을 닫아도 작업은 계속됩니다.', 'We are refining your route through Busan. The job continues if you leave this screen.') : tx('현지 정보와 안전 조건, 이동 부담을 함께 확인하고 있어요.', 'We are checking local information, safety, and travel effort together.')}</Text>
        {/* 🔴 끝났으면 진행률을 치운다 (S15P21E201-1016). 100% 로 멈춘 막대와 "처리 중" 이라는
            단계 이름은 완료된 뒤에는 정보가 아니라 거짓이다 — 위의 단계 목록이 이미 전부
            "완료" 라고 말하고 있고, 그 옆에서 다른 말을 하면 사용자는 덜 끝난 쪽을 믿는다. */}
        {job.progress !== null && !failed && job.state !== 'completed' && <View style={styles.progressBlock}><View style={styles.progressTrack}><View style={[styles.progressFill, { width: `${job.progress}%` }]} /></View><Text variant="caption" color={color.brand.orange}>{stageLabel(job.stage, tx) ?? tx('요청 접수', 'Request received')} · {job.progress}%</Text></View>}
        {failed && <Button label={tx('조건 다시 확인하기', 'Review trip details')} onPress={() => router.replace('/plan')} variant="accent" />}
        </View>
        {!failed && <View accessibilityLiveRegion="polite" style={[styles.stageList, kind !== 'phone' && styles.stageListWide]}>{STAGES.map((item, index) => { const done = index < currentStage || job.state === 'completed'; const active = index === currentStage && isWorking; return <View key={item.label} style={[styles.stage, kind !== 'phone' && styles.stageItemWide, active && styles.stageActive]}><View style={[styles.stageIcon, done && styles.stageDone]}><Text variant="caption" weight="bold" color={done ? color.text.onAction : active ? color.brand.orange : '#6e7280'}>{done ? '✓' : '○'}</Text></View><Text weight={done || active ? 'bold' : 'regular'} color={done || active ? color.brand.ivory : '#6e7280'} style={styles.stageText}>{language === 'en' ? item.en : item.label}</Text><Text variant="caption" color={done ? color.state.success : active ? color.brand.orange : '#6e7280'}>{done ? tx('완료', 'Done') : active ? tx('진행 중', 'In progress') : tx('대기', 'Waiting')}</Text></View>; })}</View>}
      </View>}
      <View style={styles.ticketArea}>
        {/* 시안 TripPassCard 의 머리줄 — 왼쪽 뒤로가기 · 가운데 TRIP PASS · 오른쪽 승차권 번호. */}
        {kind !== 'phone' ? (
          <View style={styles.passHead}>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => (router.canGoBack() ? router.back() : router.replace('/plan'))}>
              <Text variant="title">‹</Text>
            </Pressable>
            <Text variant="caption" weight="bold" color={color.text.muted} style={styles.passTitle}>TRIP PASS</Text>
            <Text variant="caption" weight="bold" color={color.text.muted}>{tripPass.code}</Text>
          </View>
        ) : null}
        <View style={kind !== 'phone' ? styles.passBody : undefined}>
        {/* 🔴 시안 TripPassCard 로 바꿨다 (S15P21E201-1233). 전에는 이 자리에 영수증을
            직접 그렸는데, 찍히는 값이 「BUSAN」·「READY TO BOARD」 같은 **고정 글자**라
            실제 일정과 무관했다. 이제 날짜·방문지·걷는 거리·예상 비용이 전부 실값이다. */}
        {kind !== 'phone' ? (
          <View style={styles.ticketColumn}>
            <TripPass data={tripPass} wide tx={tx} onReprint={() => setReprint((n) => n + 1)} key={reprint} />
          </View>
        ) : (
          <TripPass data={tripPass} wide={false} tx={tx} onReprint={() => setReprint((n) => n + 1)} key={reprint} />
        )}
        {/* 🔴 시안 p4 — 티켓 **옆**에 여행표 상세가 선다. 출발지 · 첫 일정 · 마지막 일정 ·
            이동 합계 · 예상 비용, 그리고 「일정 보기」·「지도에서 보기」.
            🔴 **모르는 줄은 아예 안 만든다**(buildTripPassDetails). 시안에는 다섯 줄이 다
            있지만 값이 없는 자리에 「미확인」을 적으면 정보가 아니라 잡음이다. */}
        {job.state === 'completed' && kind !== 'phone' ? (
          <View style={styles.ticketDetails}>
            <Text variant="title" weight="bold">{[tripPass.fromLabel, tripPass.toLabel].filter(Boolean).join(' → ')}</Text>
            {tripPass.dateRange ? <Text variant="caption" color={color.text.muted}>{tripPass.dateRange}</Text> : null}
            {tripPassDetails.map((row) => (
              <View key={row.key} style={styles.detailRow}>
                <Text variant="caption" color={color.text.muted}>{row.key}</Text>
                <Text weight="bold" style={styles.detailValue} numberOfLines={1}>{row.value}</Text>
              </View>
            ))}
            <View style={styles.detailRow}>
              <Text variant="caption" color={color.text.muted}>{tx('일정 상태', 'Status')}</Text>
              <View style={styles.statusRow}>
                <View style={styles.statusDot} />
                <Text weight="bold">{tx('생성 완료', 'Ready')}</Text>
              </View>
            </View>
            <Button label={tx('일정 보기', 'View itinerary')} disabled={!job.jobId} onPress={() => job.jobId && router.replace(`/trips/${job.jobId}/recommendations?jobId=${encodeURIComponent(job.jobId)}`)} />
            <Button label={tx('지도에서 보기', 'See on the map')} variant="ghost" disabled={!job.jobId} onPress={() => job.jobId && router.push(`/trips/${job.jobId}/recommendations?jobId=${encodeURIComponent(job.jobId)}`)} />
          </View>
        ) : null}
        {job.state === 'completed' && kind === 'phone' && <View style={styles.actions}><Button label={tx('일정 자세히 보기', 'View itinerary details')} onPress={() => job.jobId && router.replace(`/trips/${job.jobId}/recommendations?jobId=${encodeURIComponent(job.jobId)}`)} disabled={!job.jobId} /><Text variant="caption" color={color.text.muted}>{tx('추천 후보를 확인한 뒤 완성된 일정으로 이동할 수 있어요.', 'Review the recommendations, then open your completed itinerary.')}</Text></View>}
        </View>
      </View>
    </View>
    <AccessibilityUnverifiedModal
      visible={accessibilityNotice !== null}
      unverifiedCount={accessibilityNotice?.unverified ?? 0}
      totalCount={accessibilityNotice?.total ?? 0}
      onClose={() => setAccessibilityNotice(null)}
    />
  </Screen>;
}
const styles = StyleSheet.create({ canvas: { backgroundColor: color.brand.ivory, maxWidth: 1200 }, mobileTop: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[4] }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, logo: { width: 88, height: 28 }, stepPill: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.brand.navy }, layout: { gap: spacing[4] }, layoutWide: { minHeight: 720 }, statusPanel: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.navy }, statusWide: { flexDirection: 'row', alignItems: 'center', gap: spacing[8], paddingHorizontal: spacing[8], paddingVertical: spacing[6] }, aiBadge: { alignSelf: 'flex-start', flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.08)' }, pulse: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: '#4a5568' }, pulseActive: { backgroundColor: color.brand.orange }, headline: { lineHeight: 32 }, stageList: { gap: spacing[2] },
  stageItemWide: { width: '48%' },
  stageListWide: { width: 440, flexShrink: 0, flexDirection: 'row', flexWrap: 'wrap' },
  statusCopy: { flex: 1, minWidth: 0, gap: spacing[2] }, stage: { minHeight: 54, flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: 'rgba(255,255,255,0.03)' }, stageActive: { borderWidth: 1, borderColor: 'rgba(242,101,50,0.45)', backgroundColor: 'rgba(242,101,50,0.08)' }, stageIcon: { width: 28, height: 28, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.06)' }, stageDone: { backgroundColor: color.state.success }, stageText: { flex: 1 }, progressBlock: { gap: spacing[2] }, progressTrack: { height: 6, overflow: 'hidden', borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.08)' }, progressFill: { height: 6, borderRadius: radius.full, backgroundColor: color.brand.orange }, // 🔴 시안 TripPassCard 는 **흰 카드 하나**다. 머리줄(‹ · TRIP PASS · 코드) 아래에
  //    티켓과 상세가 나란히 서고, 「다시 출력」이 카드 바닥 가운데에 온다.
  //    전에는 베이지 판 위에 티켓만 있고 상세가 **따로 뜬 흰 카드**였다.
  ticketArea: { alignItems: 'center', justifyContent: 'flex-start', padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  passHead: { width: '100%', minHeight: 40, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[4] },
  passTitle: { letterSpacing: 1.5 },
  passBody: { width: '100%', flexDirection: 'row', alignItems: 'flex-start', gap: spacing[8] },
  // 🔴 티켓 칸에 폭을 못 박는다. TripPass 의 뿌리가 width:100% 라, 안 잡으면 티켓이
  //    카드 폭을 통째로 먹고 상세 칸이 **카드 밖으로 밀려난다** (실측 1103px, 2026-09-18).
  ticketColumn: { width: 420, flexShrink: 0 },
  ticketAreaWide: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[8], justifyContent: 'center' },
  // 🔴 폭을 고정한다. flex:1 로 두면 티켓이 남는 폭을 다 가져가 이 칸이 40px 로 눌리고
  //    글자가 **세로로 선다** — 2026-09-18 화면을 띄워 보고 찾았다.
  ticketDetails: { flex: 1, minWidth: 320, gap: spacing[1], paddingTop: spacing[2] },
  // 🔴 시안은 **이름 왼쪽 · 값 오른쪽 한 줄**이다. 쌓으면 줄 수가 두 배가 되고 값이 눈에 안 띈다.
  detailRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[4], minHeight: 40, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border },
  detailValue: { flexShrink: 1 },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  statusDot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.state.success }, printer: { zIndex: 4, width: '100%', maxWidth: 400, height: 116, alignItems: 'center', justifyContent: 'center', gap: spacing[2], borderRadius: radius.lg, backgroundColor: color.brand.navy, shadowColor: color.brand.navy, shadowOpacity: 0.2, shadowRadius: 12, shadowOffset: { width: 0, height: 6 }, elevation: 8 }, printerLabel: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.08)' }, lights: { flexDirection: 'row', gap: spacing[1] }, light: { width: 6, height: 6, borderRadius: radius.full, backgroundColor: '#4a5568' }, green: { backgroundColor: color.state.success }, orange: { backgroundColor: color.brand.orange }, slot: { width: '70%', height: 10, borderRadius: radius.full, backgroundColor: '#061328' }, ticketViewport: { width: '100%', height: 620, marginTop: -8, overflow: 'hidden', alignItems: 'center' }, printedPaper: { width: '82%', maxWidth: 300, overflow: 'hidden', backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: 0.16, shadowRadius: 10, shadowOffset: { width: 0, height: 6 }, elevation: 4 }, ticket: { position: 'absolute', bottom: 0, width: '100%', height: 620, borderLeftWidth: 1, borderRightWidth: 1, borderColor: '#ddd5ca', backgroundColor: color.surface.card }, ticketHeader: { minHeight: 92, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: spacing[4], backgroundColor: color.brand.navy }, ticketBrand: { gap: spacing[1] }, ticketLogo: { width: 140, height: 34, marginLeft: -10 }, passLabel: { letterSpacing: 2.4 }, flightMark: { width: 56, height: 56, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.ivory }, flightIcon: { fontSize: 30, lineHeight: 34 }, ticketBody: { gap: spacing[3], padding: spacing[4], paddingBottom: 64 }, destination: { fontSize: 32, lineHeight: 38, letterSpacing: 2 }, routeLine: { height: 38, flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, routeTrack: { flex: 1, borderTopWidth: 1, borderStyle: 'dashed', borderColor: '#c8cace' }, routePlane: { fontSize: 20, lineHeight: 24, transform: [{ rotate: '8deg' }] }, ticketMeta: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing[2] }, metaCell: { flex: 1, gap: 2 }, previewRoute: { gap: spacing[2] }, receiptRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, receiptStop: { flex: 1 }, memoRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, memo: { flex: 1, gap: 2 }, stampImage: { width: 78, height: 78, transform: [{ rotate: '-7deg' }] }, dash: { borderTopWidth: 1, borderStyle: 'dashed', borderColor: '#c8cace' }, perforation: { position: 'absolute', left: 0, right: 0, bottom: 46, height: 10, justifyContent: 'center', backgroundColor: color.surface.card }, perforationCut: { borderTopWidth: 1, borderStyle: 'dashed', borderColor: '#c8cace' }, ticketTail: { position: 'absolute', left: 0, right: 0, bottom: 0, minHeight: 46, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: spacing[3], backgroundColor: color.surface.subtle }, actions: { width: '88%', maxWidth: 320, gap: spacing[2], marginTop: spacing[4] } });
