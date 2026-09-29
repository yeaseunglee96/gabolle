import { useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, Animated, Easing, Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useAuth } from '@/auth/AuthProvider';
import { AccessibilityUnverifiedModal } from '@/components/AccessibilityUnverifiedModal';
import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { usePlan } from '@/plan/PlanProvider';
import { adaptStreamedJob, createRecommendationJobAdapter, type RecommendationJobSnapshot, unavailableJob } from '@/plan/recommendationJob';
import { openJobProgressStream, supportsJobProgressStream } from '@/plan/recommendationJobStream';
import { loadRecommendationResult } from '@/plan/recommendations';
import { ACCESSIBILITY_UNVERIFIED, itineraryAccessibilityCounts, selectedMobilityAids } from '@/plan/accessibilityNotice';
import { loadItinerary, type ItineraryDto } from '@/plan/itinerary';
import { TripPass } from '@/plan/TripPass';
import { markChecklistStep } from '@/onboarding/firstRun';
import { loadPlacePhotos } from '@/plan/placePhotos';
import { buildTripPass, buildTripPassDetails } from '@/plan/tripPassData';
import { useI18n } from '@/i18n';
import { otherNameFor } from '@/discovery/localNames';
import { DIETS } from '@/plan/travelConditions';
import { txf } from '@/i18n/format';

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
  // 🔴 -1338 — 미리보기도 서버가 보내는 것과 같은 모양이어야 한다. 이 칸이 없으면
  //    미리보기에서만 「인원」 줄이 사라지고, 시안을 볼 때 있어야 할 줄이 없어 보인다.
  partySize: 2,
};

export default function Generating() {
  const router = useRouter(); const { kind } = useLayout(); const { tx, language, locale } = useI18n(); const { accessToken, user } = useAuth(); const { draft, clear } = usePlan();
  const { jobId, preview } = useLocalSearchParams<{ jobId?: string; preview?: string }>();
  const adapter = useMemo(() => createRecommendationJobAdapter(accessToken), [accessToken]);
  const previewJob = __DEV__ && preview === 'completed' ? { state: 'completed', jobId: 'preview', progress: 100, stage: '시간표 배치', canCancel: false, errorMessage: null, resultRef: 'preview-trip' } satisfies RecommendationJobSnapshot : __DEV__ && preview === 'running' ? { state: 'polling', jobId: 'preview', progress: 48, stage: '최적 동선 계산', canCancel: false, errorMessage: null, resultRef: null } satisfies RecommendationJobSnapshot : __DEV__ && preview === 'failed' ? { state: 'failed', jobId: 'preview', progress: null, stage: null, canCancel: false, errorMessage: '조건에 맞는 장소를 찾지 못했어요. 날짜·예산·취향 조건을 조금 넓혀서 다시 시도해 주세요.', resultRef: null } satisfies RecommendationJobSnapshot : null;
  const [job, setJob] = useState<RecommendationJobSnapshot>(() => previewJob ?? (jobId ? { state: 'accepted', jobId, progress: 0, stage: tx('요청 접수', 'Request received'), canCancel: false, errorMessage: null, resultRef: null } : unavailableJob(tx('생성 요청을 찾을 수 없어요. 조건을 확인한 뒤 다시 시작해 주세요.', 'Could not find the generation request. Please review your conditions and try again.'))));
  // 폰에서 다 끝난 뒤의 네 단계 목록 — 접어 두고 「자세히」로 편다(UI 캔버스 ⑤-6). 끝난 뒤에도 네 줄이 크게 남아 승차권이 아래로 밀렸다.
  const [stagesOpen, setStagesOpen] = useState(false);
  const [itinerary, setItinerary] = useState<ItineraryDto | null>(() => previewJob?.state === 'completed' ? PREVIEW_ITINERARY : null);
  const [itineraryMessage, setItineraryMessage] = useState<string | null>(null);
  // 🔴 S15P21E201-1559 — 「일정 보기」가 이 트립 ID로 /trips/{tripId}/recommendations 를 연다.
  //    이 화면은 jobId 만 라우트 파라미터로 받고 tripId 는 안 받아서, 아래 결과 로딩이 끝나야만
  //    (recommendation.tripId) 알 수 있다 — 그래서 로컬 변수로 두면 안 되고 상태로 들고 있어야
  //    버튼 핸들러가 나중에 읽는다. 전에는 이 자리가 없어서 job.jobId(작업 ID)를 대신 넣었고,
  //    그 값으로는 /api/v1/trips/{jobId}/recommendations 가 항상 404 였다(실기기 재확인, 2026-09-24).
  const [tripId, setTripId] = useState<string | null>(previewJob?.state === 'completed' ? 'preview-trip' : null);
  // 🔴 S15P21E201-1577 — 티켓은 일정을 **받아 온 뒤에** 한 번 출력한다. 완성 순간에는 아직 일정이
  //    없어서 코드가 빈 티켓이 먼저 나오고, 일정이 오면 또 나와 영수증이 두 번 출력됐다.
  //    받아 오기에 실패해도 true 가 된다 — 그때는 가진 값으로 한 번 나온다. 프린터가 멈춰 있으면 안 된다.
  const [ticketLoaded, setTicketLoaded] = useState(previewJob?.state === 'completed');
  // 접근성 안내 창 — 확인 안 된 곳이 하나라도 있으면 한 번만 뜬다.
  // warnedJobRef 가 "한 번만" 을 지킨다. 이 화면은 스트림·폴링·재렌더로 같은 완료 상태를
  // 여러 번 지나가므로, 상태 하나로는 닫은 창이 다시 열린다.
  // preview=access 는 개발 중에만 도는 갈래다. 이 창은 서버가 접근성 경고를 실어 보낸
  // 추천에서만 뜨는데, 그 조건을 손으로 만들려면 로그인해서 실제 추천을 돌려야 한다.
  // 시안을 보거나 문구를 고칠 때마다 그걸 하는 것은 너무 비싸다.
  const [accessibilityNotice, setAccessibilityNotice] = useState<{ unverified: number; total: number } | null>(
    () => (__DEV__ && preview === 'access' ? { unverified: 9, total: 12 } : null),
  );
  const warnedJobRef = useRef<string | null>(null);
  // 안내 창 문장을 고른 이동 보조에 맞추려고 처음 값을 잡아 둔다(S15P21E201-1814). 일정을 받으면 clear() 가 조건을 지우므로
  // 그 뒤의 draft 로 고르면 늘 「모름」이 된다.
  const [mobilityAids] = useState(() => selectedMobilityAids(draft));
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

    // — 열리면 실시간으로 받고, 실패하거나 지원하지 않으면 조용히
    // 폴링으로 갈아탄다. 화면 쪽에서는 어느 경로로 왔든 같은 setJob 이 받는다.
    if (supportsJobProgressStream()) {
      closeStream = openJobProgressStream(activeJobId, accessToken, {
        onSnapshot: (snapshot) => {
          if (cancelled) return;
          const next = adaptStreamedJob(activeJobId, snapshot, jobRef.current);
          setJob(next);
          // 🔴 스트림은 실패의 «이유»를 안 싣는다 — {code} 뿐이라 멈춘 단계(detail)도,
          //    어느 조건이 막았는지(blockedBy)도 없다. 실패로 끝났으면 정식 조회를 한 번 해서
          //    그 둘을 받아 온다(S15P21E201-1514). 폰은 원래 폴링이라 처음부터 받는다.
          //
          //    `cancelled` 로 막지 않는다 — 실패로 바뀌는 순간 isWorking 이 꺼져 이 효과가
          //    정리되므로, 그걸로 막으면 답이 늘 버려진다. 대신 «아직 같은 작업을 보고 있나»로 본다.
          //    조회 자체가 실패하면(stage 가 null — toFailure 가 만든 것) 스트림 문구를 그대로 둔다.
          if (snapshot.status === 'FAILED') {
            void adapter.poll(activeJobId, next).then((full) => {
              if (jobRef.current.jobId === activeJobId && full.state === 'failed' && full.stage !== null) setJob(full);
            });
          }
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
      if (recommendation.tripId) setTripId(recommendation.tripId);
      // 일정을 못 읽어도 이 안내는 띄운다. 접근성은 일정이 열리는지와 별개로
      // 사용자가 알아야 하는 것이고, 아래 early return 뒤에 두면 그때 조용히 사라진다.
      const warn = recommendation.conflicts.includes(ACCESSIBILITY_UNVERIFIED);
      const notify = (counts: { unverified: number; total: number }) => {
        if (warnedJobRef.current === job.jobId) return;
        warnedJobRef.current = job.jobId!;
        setAccessibilityNotice(counts);
      };
      // 추천 목록으로 센 수 — 코스 A·B·C 를 합친 목록이라 보이는 일정을 못 셀 때만 쓴다(S15P21E201-1732).
      const fromRecommendation = () => {
        const unverified = recommendation.courses.filter((course) => course.mobilityWarnings?.includes(ACCESSIBILITY_UNVERIFIED)).length;
        // conflicts 에 코드가 있는데 항목에서 못 셌다면(서버 판이 달라 항목 경고가 안 올 수
        // 있다) 0곳이라고 말하지 않는다 — 그건 "확인됐다" 로 읽힌다. 분모 없이 알린다.
        return { unverified: unverified || recommendation.courses.length, total: unverified ? recommendation.courses.length : 0 };
      };
      if (!recommendation.itineraryId) {
        if (warn) notify(fromRecommendation());
        setItineraryMessage(recommendation.message);
        setTicketLoaded(true);
        return;
      }
      const result = await loadItinerary(recommendation.itineraryId, accessToken);
      if (cancelled) return;
      if (warn) {
        // 🔴 승차권에 보이는 일정만 센다(S15P21E201-1732). 추천 목록으로 세면 방문지 8곳 승차권 위에 「24곳 중 23곳」이
        //    떴다. 보이는 일정에 미확인이 없으면 창을 안 띄운다. 항목 경고를 모르는 옛 서버·못 읽은 일정은 전처럼 센다.
        //    「띄웠다」 표시는 실제로 띄울 때 한다 — 일정을 읽는 동안 이 효과가 다시 돌면(열쇠 갱신 등) 창이 사라지지 않게.
        const visible = result.state === 'success' ? itineraryAccessibilityCounts(result.itinerary) : null;
        if (!visible) notify(fromRecommendation());
        else if (visible.unverified > 0) notify(visible);
      }
      if (result.state === 'success') { setItinerary(result.itinerary); void clear(); }
      else setItineraryMessage(result.message);
      setTicketLoaded(true);
    };
    void load();
    return () => { cancelled = true; };
  }, [accessToken, job.jobId, job.state, previewJob]);
  // — 종이가 다 나온 순간을 상태로 든다.
  const [printed, setPrinted] = useState(false);
  /** 「다시 출력」을 누른 횟수. key 로 써서 출력 애니메이션을 처음부터 돌린다. */
  const [reprint, setReprint] = useState(0);
  useEffect(() => {
    ticketReveal.stopAnimation();
    if (job.state !== 'completed') { setPrinted(false); ticketReveal.setValue(0); return; }
    void markChecklistStep('trip');
    if (reduceMotion) { ticketReveal.setValue(1); setPrinted(true); return; }
    setPrinted(false);
    ticketReveal.setValue(0);
    Animated.sequence([
      Animated.delay(520),
      Animated.timing(ticketReveal, { toValue: 0.2, duration: 420, easing: Easing.linear, useNativeDriver: false }),
      Animated.timing(ticketReveal, { toValue: 0.46, duration: 480, easing: Easing.linear, useNativeDriver: false }),
      Animated.timing(ticketReveal, { toValue: 0.74, duration: 520, easing: Easing.linear, useNativeDriver: false }),
      Animated.timing(ticketReveal, { toValue: 1, duration: 560, easing: Easing.out(Easing.cubic), useNativeDriver: false }),
      // 끝났다는 표시는 애니메이션이 실제로 끝났을 때만 켠다. 시간을 재서 맞추면
      // 기기가 느릴 때 종이가 아직 나오는 중인데 "출력 완료" 라고 말한다.
    ]).start(({ finished }) => { if (finished) setPrinted(true); });
  }, [job.state, reduceMotion, ticketReveal]);
  // 🔴 단계가 다 체크되고 티켓이 출력되기 시작하면 티켓으로 굴러간다 (S15P21E201-1628). 폰에서는 네이비 띠(단계 넷)
  //    아래에서 티켓이 나오는데 스크롤이 맨 위라, 티켓이 화면 아래에 반쯤 걸려 출력되는 모습을 놓쳤다.
  //    작업마다 한 번만 — 「다시 출력」은 사람이 이미 티켓 앞에 있으니 굴리지 않는다.
  const scrollRef = useRef<ScrollView>(null);
  const layoutY = useRef(0);
  const ticketY = useRef<number | null>(null);
  const scrolledForJob = useRef<string | null>(null);
  /** 굴러갈 작업. 티켓 자리를 아직 못 쟀으면(웹은 onLayout 이 한 박자 늦다) 재는 순간에 간다. */
  const scrollPending = useRef<string | null>(null);
  const scrollToTicket = () => {
    const pending = scrollPending.current;
    if (!pending || ticketY.current === null) return;
    scrollPending.current = null;
    scrolledForJob.current = pending;
    scrollRef.current?.scrollTo({ y: Math.max(0, layoutY.current + ticketY.current - spacing[3]), animated: !reduceMotion });
  };
  useEffect(() => {
    if (job.state !== 'completed' || !ticketLoaded || !job.jobId || scrolledForJob.current === job.jobId) return undefined;
    scrollPending.current = job.jobId;
    const timer = setTimeout(scrollToTicket, 80);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [job.state, job.jobId, ticketLoaded]);
  const duration = itinerary?.days.length || daysBetween(draft.startDate, draft.endDate); const areas = itinerary?.title || (draft.travelAreas.length ? draft.travelAreas.join(' · ') : tx('부산 맞춤 여행', 'Personalized Busan trip')); const failed = ['failed', 'conflict', 'cancelled', 'unavailable'].includes(job.state);
  const itineraryStops = itinerary?.days.flatMap((day) => day.items).slice(0, 3) ?? [];
  // 승차권 뒷면 사진 — 첫 정차지 가운데 사진이 있는 곳(S15P21E201-1378). 식당·카페는 대개 없어서 앞의 여섯을 본다.
  const [coverUrl, setCoverUrl] = useState<string | null>(null);
  const coverIds = (itinerary?.days.flatMap((day) => day.items).slice(0, 6) ?? []).map((item) => item.placeId).join(',');
  useEffect(() => {
    if (!coverIds) return undefined;
    let active = true;
    void loadPlacePhotos(coverIds.split(',')).then((photos) => { if (!active) return; const hit = coverIds.split(',').map((id) => photos[id]?.photoUrl).find(Boolean); setCoverUrl(hit ?? null); });
    return () => { active = false; };
  }, [coverIds]);
  // 승차권 첫·마지막 일정의 영어 이름 — 영어(일·중) 화면에서 「영어 (한글)」, 없으면 「한글 (로마자)」(S15P21E201-1735).
  //    일정 항목에는 영어 이름 칸이 없어 사진 조회에 함께 실려 온 것을 쓴다(같은 캐시라 겉사진과 요청이 겹치지 않는다).
  const [nameEnByPlaceId, setNameEnByPlaceId] = useState<Record<string, string | null>>({});
  const stopIds = (itinerary?.days.flatMap((day) => day.items) ?? []).map((item) => item.placeId).join(',');
  useEffect(() => {
    if (!stopIds) return undefined;
    let active = true;
    void loadPlacePhotos(stopIds.split(',')).then((photos) => { if (active) setNameEnByPlaceId(Object.fromEntries(Object.entries(photos).map(([id, photo]) => [id, otherNameFor(photo.nameEn, photo.localNames, language)]))); });
    return () => { active = false; };
  }, [stopIds]);
  const visitCount = itinerary?.days.reduce((sum, day) => sum + day.items.length, 0) ?? 0;
  const actualStartDate = itinerary?.days[0]?.date || draft.startDate;
  const actualEndDate = itinerary?.days.at(-1)?.date || draft.endDate;
  // 여행 티켓에 찍히는 값 — 시안(TripPassCard)대로 실제 일정에서 만든다
  // 계산은 tripPassData 가 하고 시험이 붙든다.
  // 「이 조건을 지켜서 만들었어요」 — 서버에 실어 보낸 조건만(모르는 칸은 안 적는다).
  const keptConditions = [
    ...(draft.dietStatus === 'VALUES' ? DIETS.filter(([code]) => draft.dietTypes.includes(code)).map(([, ko, en]) => tx(ko, en)) : []),
    ...(typeof draft.maxWalkingDistanceM === 'number' ? [txf(tx, '한 번에 %s까지 걷기', 'Walk up to %s at a time', draft.maxWalkingDistanceM >= 1000 ? `${draft.maxWalkingDistanceM / 1000}km` : `${draft.maxWalkingDistanceM}m`)] : []),
    ...(draft.slopeConstraint === 'AVOID' ? [tx('가파른 경사 피하기', 'Avoid steep slopes')] : []),
    ...(draft.stairsConstraint === 'AVOID' ? [tx('계단 피하기', 'Avoid stairs')] : []),
    ...(draft.wheelchair ? [tx('휠체어', 'Wheelchair')] : []),
    ...(draft.stroller ? [tx('유아차', 'Stroller')] : []),
  ];
  const tripPass = buildTripPass({
    conditions: keptConditions,
    itinerary,
    baseUrl: process.env.EXPO_PUBLIC_API_BASE_URL ?? null,
    origin: draft.origin || null,
    startDate: draft.startDate || null,
    endDate: draft.endDate || null,
    transport: draft.transport || null,
    ownerName: user?.displayName ?? null,
    language,
    nameEnByPlaceId,
  });
  const ticketReady = job.state === 'completed';
  const tripPassDetails = buildTripPassDetails({
    itinerary, origin: draft.origin || null, startDate: draft.startDate || null, endDate: draft.endDate || null,
    transport: draft.transport || null, ownerName: user?.displayName ?? null,
    language,
    nameEnByPlaceId,
  });
  const printedHeight = ticketReveal.interpolate({ inputRange: [0, 1], outputRange: [0, 620] });
  return <Screen scroll wide style={styles.canvas} scrollRef={scrollRef}>
    {kind === 'phone' && <View style={styles.mobileTop}><Pressable accessibilityRole="button" accessibilityLabel={tx('조건 확인으로 돌아가기', 'Back to trip review')} onPress={() => router.replace('/plan')} style={styles.back}><Text variant="title">‹</Text></Pressable><BrandLogoLink imageStyle={styles.logo} /><View style={styles.stepPill}><Text variant="caption" weight="bold" color={color.brand.ivory}>{tx('생성', 'Generate')}</Text></View></View>}
    <View style={[styles.layout, kind !== 'phone' && styles.layoutWide]} onLayout={(event) => { layoutY.current = event.nativeEvent.layout.y; }}>
      {/* 🔴 폰 · 만드는 중/실패는 시안 5 의 03b 「동백이 대기 화면」이다(S15P21E201-1415). 전에는 검은 띠에
          「AI가…」만 있고 동백이가 없었고, 만드는 중인데도 아래에 빈 승차권 프린터(출발 —)가 같이 보였다.
          승차권은 완성됐을 때(03c)만 나온다. 넓은 화면은 아래의 띠 + 승차권 나란히 그대로. */}
      {kind === 'phone' && job.state !== 'completed' ? (
        <View style={styles.waitScreen}>
          <Eyebrow>{failed ? tx('일정 생성 실패', 'Itinerary generation failed') : tx('AI 일정 생성', 'AI itinerary')}</Eyebrow>
          <Text variant="display" weight="bold" style={styles.waitHeadline}>{failed ? tx('이번엔 일정을\n만들지 못했어요', "Couldn't build\nthis one") : tx('동백이가 당신만을 위한\n부산 여행을 만들고 있어요', 'Dongbaek is building\nyour own Busan trip')}</Text>
          <Text variant="caption" color={color.text.body}>{failed ? job.errorMessage : delayed && isWorking ? tx('부산 동선을 조금 더 다듬고 있어요. 화면을 닫아도 작업은 계속됩니다.', 'We are refining your route through Busan. The job continues if you leave this screen.') : tx('현지 정보와 안전 조건, 이동 부담을 함께 확인하고 있어요.', 'We are checking local information, safety, and travel effort together.')}</Text>
          <View style={styles.waitMascotBlock}>
            <View style={styles.waitMascotCircle}>
              <View style={styles.waitMascotRing} />
              <GabolleMascot state={failed ? 'sad' : 'thinking'} still={failed} style={styles.waitMascot} />
            </View>
            {/* 서버가 없거나 닿지 않을 때(unavailable)는 조건 탓이 아니다 — 조건을 넓히라고 하지 않는다(S15P21E201-1669). */}
            {/* 오늘 남은 시간이 없을 때(ITINERARY_NO_TIME_LEFT_TODAY)도 조건 탓이 아니다 — 날짜를 내일로 이끈다(S15P21E201-1739). */}
            <Text weight="bold">{failed ? (job.state === 'unavailable' ? tx('잠시 뒤 다시 해 볼까요?', 'Shall we try again in a moment?') : job.failureCode === 'ITINERARY_NO_TIME_LEFT_TODAY' ? tx('날짜를 내일로 바꿔 볼까요?', 'Shall we start tomorrow instead?') : tx('조건을 조금 넓혀서 다시 해 볼까요?', 'Shall we widen the conditions and try again?')) : tx('잠시만요, 딱 맞는 동선을 찾고 있어요!', 'One moment — finding the route that fits you!')}</Text>
          </View>
          {!failed ? (
            <View accessibilityLiveRegion="polite" style={styles.waitStages}>{STAGES.map((item, index) => { const done = index < currentStage; const active = index === currentStage && isWorking; return (
              <View key={item.label} style={[styles.waitStage, active && styles.waitStageActive]}>
                <View style={[styles.waitStageIcon, done && styles.stageDone, active && styles.waitStageIconActive]}>{done ? <Text variant="caption" weight="bold" color={color.text.onAction}>✓</Text> : <View style={[styles.waitStageDot, active && styles.waitStageDotActive]} />}</View>
                <Text weight={done || active ? 'bold' : 'medium'} color={done || active ? color.text.heading : color.text.muted} style={styles.stageText}>{tx(item.label, item.en)}</Text>
                <Text variant="caption" weight="bold" color={done ? color.state.success : active ? color.text.heading : color.text.muted}>{done ? tx('완료', 'Done') : active ? tx('진행 중', 'In progress') : tx('대기', 'Waiting')}</Text>
              </View>); })}</View>
          ) : null}
          {job.progress !== null && !failed ? (
            <View style={styles.waitProgress}>
              <View style={styles.waitTrack}><View style={[styles.waitFill, { width: `${job.progress}%` }]} /></View>
              <View style={styles.waitProgressRow}><Text variant="caption" weight="bold">{stageLabel(job.stage, tx) ?? tx('요청 접수', 'Request received')} · {job.progress}%</Text><Text variant="caption" color={color.text.muted}>{tx('완성되면 자동으로 넘어가요', 'Moves on by itself when done')}</Text></View>
            </View>
          ) : null}
          <View style={styles.waitActions}>
            {failed
              ? <Button label={tx('조건 다시 확인하기', 'Review trip details')} variant="outline" onPress={() => router.replace('/plan')} />
              : <>
                  <Button label={tx('백그라운드에서 계속', 'Continue in background')} variant="secondary" onPress={() => router.replace('/home')} />
                  {/* 홈에는 아직 「만드는 중」 알림 줄이 없다 — 있다고 말하지 않는다. 완성된 일정이 실제로 보이는 곳(내 여행)을 말한다. */}
                  <Text variant="caption" color={color.text.muted} style={styles.waitNote}>{tx('화면을 닫아도 작업은 계속돼요 · 완성된 일정은 「내 여행」에서 볼 수 있어요', 'Leaving this screen keeps the job running · the finished trip shows up under My trips')}</Text>
                </>}
          </View>
        </View>
      ) : null}
      {kind === 'phone' && job.state === 'completed' && !failed && !stagesOpen ? (
        <View style={styles.doneHead}>
          <Pressable accessibilityRole="button" accessibilityState={{ expanded: false }} onPress={() => setStagesOpen(true)} style={({ pressed }) => [styles.doneStrip, pressed && styles.donePressed]}>
            <View style={styles.doneCheck}><Text variant="micro" weight="bold" color={color.text.onAction}>✓</Text></View>
            <Text variant="caption" weight="bold" color={color.text.onAction} style={styles.doneStripText}>{txf(tx, 'AI 일정 완성 · %s단계 모두 확인', 'AI plan ready · all %s steps checked', STAGES.length)}</Text>
            <Text variant="caption" color={color.text.onDarkMuted}>{tx('자세히', 'Details')}</Text>
          </Pressable>
          <Text variant="title" weight="bold">{tx('당신만의 부산 여행이 완성됐어요', 'Your own Busan trip is ready')}</Text>
        </View>
      ) : null}
      {!(kind === 'phone' && job.state !== 'completed') && !(kind === 'phone' && job.state === 'completed' && !failed && !stagesOpen) && <View style={[styles.statusPanel, kind !== 'phone' && styles.statusWide]}>
        {/* 시안 p4 — 네이비는 위에 가로로 눕는 띠다. 왼쪽에 글, 오른쪽에 단계 넷을
            2열로 둔다. 전에는 왼쪽 44% 세로 칸이라 여행표가 옆으로 밀려 있었다.
        */}
        <View style={kind !== 'phone' ? styles.statusCopy : undefined}>
        <View style={styles.aiBadge}><View style={[styles.pulse, isWorking && styles.pulseActive]} /><Text variant="caption" weight="bold" color={color.text.onAction}>{job.state === 'completed' ? tx('AI 일정 완성', 'AI itinerary ready') : failed ? tx('일정 생성 실패', 'Itinerary generation failed') : tx('AI 일정 생성 중', 'Creating your itinerary')}</Text></View>
        <Text variant="display" weight="bold" color={color.brand.ivory} style={styles.headline}>{job.state === 'completed' ? tx(kind === 'phone' ? '당신만의 부산 여행이\n완성됐어요' : '당신만의 부산 여행이 완성됐어요', kind === 'phone' ? 'Your Busan trip\nis ready' : 'Your Busan trip is ready') : failed ? tx('일정을 만들지 못했어요', "We couldn't build your itinerary") : tx('동백이가 당신만을 위한\n부산 여행을 만들고 있어요', 'Dongbaek is building\nyour Busan trip')}</Text>
        {/* 🔴 끝난 화면에서 진행 중 문구를 남기지 않는다 — S15P21E201-1489(B-10).
            바로 아래 진행률이 이미 같은 이유로 완료 때 치워진다. 이 부제만 그 처리를
            빠뜨려서, 제목은 「완성됐어요」인데 부제는 「확인하고 있어요」였다(iOS build 39).
            한 화면에서 끝났다고도 하고 하는 중이라고도 하면 사람은 덜 끝난 쪽을 믿는다. */}
        <Text color={color.text.onDarkMuted}>{failed ? job.errorMessage : job.state === 'completed' ? tx('현지 정보와 안전 조건, 이동 부담을 모두 확인했어요.', 'We checked local information, safety, and travel effort.') : delayed && isWorking ? tx('부산 동선을 조금 더 다듬고 있어요. 화면을 닫아도 작업은 계속됩니다.', 'We are refining your route through Busan. The job continues if you leave this screen.') : tx('현지 정보와 안전 조건, 이동 부담을 함께 확인하고 있어요.', 'We are checking local information, safety, and travel effort together.')}</Text>
        {/* 끝났으면 진행률을 치운다. 100% 로 멈춘 막대와 "처리 중" 이라는
            단계 이름은 완료된 뒤에는 정보가 아니라 거짓이다 — 위의 단계 목록이 이미 전부
            "완료" 라고 말하고 있고, 그 옆에서 다른 말을 하면 사용자는 덜 끝난 쪽을 믿는다.
        */}
        {job.progress !== null && !failed && job.state !== 'completed' && <View style={styles.progressBlock}><View style={styles.progressTrack}><View style={[styles.progressFill, { width: `${job.progress}%` }]} /></View><Text variant="caption" color={color.text.onDarkMuted}>{stageLabel(job.stage, tx) ?? tx('요청 접수', 'Request received')} · {job.progress}%</Text></View>}
        {failed && <Button label={tx('조건 다시 확인하기', 'Review trip details')} onPress={() => router.replace('/plan')} variant="primary" />}
        </View>
        {!failed && <View accessibilityLiveRegion="polite" style={[styles.stageList, kind !== 'phone' && styles.stageListWide]}>{STAGES.map((item, index) => { const done = index < currentStage || job.state === 'completed'; const active = index === currentStage && isWorking; return <View key={item.label} style={[styles.stage, kind !== 'phone' && styles.stageItemWide, active && styles.stageActive]}><View style={[styles.stageIcon, done && styles.stageDone]}><Text variant="caption" weight="bold" color={done ? color.text.onAction : active ? color.action.primary : color.text.muted}>{done ? '✓' : '○'}</Text></View><Text weight={done || active ? 'bold' : 'regular'} color={done || active ? color.text.onAction : color.text.muted} style={styles.stageText}>{tx(item.label, item.en)}</Text><Text variant="caption" color={done ? color.state.success : active ? color.action.primary : color.text.muted}>{done ? tx('완료', 'Done') : active ? tx('진행 중', 'In progress') : tx('대기', 'Waiting')}</Text></View>; })}</View>}
      </View>}
      {/* 🔴 실패하면 넓은 화면도 여행표 칸을 안 그린다(S15P21E201-1669) — 프린터만 있고 표가 안 나오는 빈 칸이 남았다. */}
      {(kind === 'phone' && job.state !== 'completed') || failed ? null : <View style={styles.ticketArea} onLayout={(event) => { ticketY.current = event.nativeEvent.layout.y; scrollToTicket(); }}>
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
 {/* 시안 TripPassCard 로 바꿨다. 전에는 이 자리에 영수증을
            직접 그렸는데, 찍히는 값이 「BUSAN」·「READY TO BOARD」 같은 **고정 글자**라
            실제 일정과 무관했다. 이제 날짜·방문지·걷는 거리·예상 비용이 전부 실값이다. */}
        {/* 🔴 상세는 티켓 **뒷면**으로 갔다(시안 ②). 전에는 옆에 따로 서 있었는데, 그러면
            티켓이 장식이 되고 사람이 읽는 것은 옆 칸이었다. 뒤집어 봐야 나오는 것이
            「이 티켓이 내 여행이다」를 가장 짧게 말한다.

            만드는 중에는 details 를 안 준다 — 뒤집을 내용이 없는데 뒤집히면 빈 뒷면이 나온다. */}
        {kind !== 'phone' ? (
          <View style={styles.ticketColumn}>
            <TripPass
              data={tripPass}
              wide
              tx={tx}
              details={ticketReady ? tripPassDetails : undefined}
              onOpenItinerary={ticketReady && tripId && job.jobId ? () => router.replace(`/trips/${tripId}/recommendations?jobId=${encodeURIComponent(job.jobId as string)}`) : undefined}
              coverUrl={coverUrl}
              ready={ticketReady && ticketLoaded}
              onReprint={() => setReprint((n) => n + 1)}
              key={reprint}
            />
          </View>
        ) : (
          <TripPass
            data={tripPass}
            wide={false}
            tx={tx}
            details={ticketReady ? tripPassDetails : undefined}
            onOpenItinerary={ticketReady && tripId && job.jobId ? () => router.replace(`/trips/${tripId}/recommendations?jobId=${encodeURIComponent(job.jobId as string)}`) : undefined}
            coverUrl={coverUrl}
            ready={ticketReady && ticketLoaded}
            onReprint={() => setReprint((n) => n + 1)}
            key={reprint}
          />
        )}
        {/* 승차권 앞면 QR 자리의 「내 일정 보기」가 문이다 — 같은 곳으로 가는 큰 단추를 아래 또 두지 않는다(2026-09-21 실기, S15P21E201-1381).
            「뒤집으면 일정 보기가 있어요」 안내는 뺐다 — 앞면에 단추가 생겨 할 말이 없어졌다(S15P21E201-1562). */}
        </View>
      </View>}
    </View>
    <AccessibilityUnverifiedModal
      visible={accessibilityNotice !== null}
      unverifiedCount={accessibilityNotice?.unverified ?? 0}
      totalCount={accessibilityNotice?.total ?? 0}
      aids={mobilityAids}
      onClose={() => setAccessibilityNotice(null)}
    />
  </Screen>;
}
const styles = StyleSheet.create({ doneHead: { gap: spacing[3], marginBottom: spacing[2] }, doneStrip: { minHeight: 44, flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.brand.navy }, donePressed: { opacity: 0.85 }, doneCheck: { width: 18, height: 18, borderRadius: radius.full, backgroundColor: color.state.success, alignItems: 'center', justifyContent: 'center' }, doneStripText: { flex: 1 }, canvas: { backgroundColor: color.canvas, maxWidth: 1200 }, mobileTop: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[4] }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, logo: { width: 154, height: 28 }, stepPill: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.brand.navy }, layout: { gap: spacing[4] }, layoutWide: { minHeight: 720 }, statusPanel: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.navy }, statusWide: { flexDirection: 'row', alignItems: 'center', gap: spacing[8], paddingHorizontal: spacing[8], paddingVertical: spacing[6] }, aiBadge: { alignSelf: 'flex-start', flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.08)' }, pulse: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: '#4a5568' }, pulseActive: { backgroundColor: color.state.dot }, headline: { lineHeight: 32 }, stageList: { gap: spacing[2] },
  stageItemWide: { width: '48%' },
  stageListWide: { width: 440, flexShrink: 0, flexDirection: 'row', flexWrap: 'wrap' },
  // ── 03b 동백이 대기 화면(폰) ──
  waitScreen: { gap: spacing[2], minHeight: 640 },
  waitHeadline: { lineHeight: 34 },
  waitMascotBlock: { alignItems: 'center', gap: spacing[3], paddingVertical: spacing[4] },
  waitMascotCircle: { width: 176, height: 176, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.blush },
  waitMascotRing: { position: 'absolute', top: 12, left: 12, right: 12, bottom: 12, borderRadius: radius.full, borderWidth: 1.5, borderStyle: 'dashed', borderColor: color.action.outline, opacity: 0.45 },
  waitMascot: { width: 128, height: 128 },
  waitStages: { gap: 2, padding: 6, borderRadius: radius.lg, backgroundColor: color.surface.card },
  waitStage: { minHeight: 50, flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.md },
  waitStageActive: { backgroundColor: color.surface.soft },
  waitStageIcon: { width: 26, height: 26, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' },
  waitStageIconActive: { backgroundColor: color.surface.card },
  waitStageDot: { width: 10, height: 10, borderRadius: radius.full, borderWidth: 2, borderColor: color.surface.field },
  waitStageDotActive: { borderColor: color.text.heading },
  waitProgress: { gap: spacing[2], marginTop: spacing[3] },
  waitTrack: { height: 6, borderRadius: radius.full, overflow: 'hidden', backgroundColor: color.surface.field },
  waitFill: { height: 6, borderRadius: radius.full, backgroundColor: color.text.heading },
  waitProgressRow: { flexDirection: 'row', justifyContent: 'space-between', flexWrap: 'wrap', gap: spacing[2] },
  waitActions: { marginTop: 'auto', paddingTop: spacing[6], gap: spacing[2] },
  waitNote: { textAlign: 'center' },
  statusCopy: { flex: 1, minWidth: 0, gap: spacing[2] }, stage: { minHeight: 54, flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: 'rgba(255,255,255,0.06)' }, stageActive: { borderWidth: 1, borderColor: 'rgba(216,58,72,0.45)', backgroundColor: 'rgba(216,58,72,0.08)' }, stageIcon: { width: 28, height: 28, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.06)' }, stageDone: { backgroundColor: color.state.success }, stageText: { flex: 1 }, progressBlock: { gap: spacing[2] }, progressTrack: { height: 6, overflow: 'hidden', borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.08)' }, progressFill: { height: 6, borderRadius: radius.full, backgroundColor: color.state.dot }, // 🔴 시안 TripPassCard 는 **흰 카드 하나**다. 머리줄(‹ · TRIP PASS · 코드) 아래에
  // 티켓과 상세가 나란히 서고, 「다시 출력」이 카드 바닥 가운데에 온다.
  // 전에는 베이지 판 위에 티켓만 있고 상세가 따로 뜬 흰 카드였다.
  // 배경은 아이보리다. 시안에서 직접 읽었다 — rgb(255,253,248), 테두리 1px #e8e4dd.
  // 흰색(surface.card)으로 두면 아이보리 바탕 위에서 카드만 허옇게 떠 보인다.
  ticketArea: { alignItems: 'center', justifyContent: 'flex-start', padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.ivory, borderWidth: 1, borderColor: color.surface.border },
  passHead: { width: '100%', minHeight: 40, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[4] },
  passTitle: { letterSpacing: 1.5 },
  // 상세가 뒷면으로 간 뒤로 이 줄에는 티켓 하나만 선다 — 가운데로 모은다.
  passBody: { width: '100%', flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'center', gap: spacing[8] },
  ticketColumn: { width: 420, flexShrink: 0, alignItems: 'center' },
  ticketAreaWide: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[8], justifyContent: 'center' },
  ticketDetails: { flex: 1, minWidth: 320, gap: spacing[1], paddingTop: spacing[2] },
  // 시안은 이름 왼쪽 · 값 오른쪽 한 줄이다. 쌓으면 줄 수가 두 배가 되고 값이 눈에 안 띈다.
  detailRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[4], minHeight: 40, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border },
  detailValue: { flexShrink: 1 },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  statusDot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.state.success }, printer: { zIndex: 4, width: '100%', maxWidth: 400, height: 116, alignItems: 'center', justifyContent: 'center', gap: spacing[2], borderRadius: radius.lg, backgroundColor: color.brand.navy, shadowColor: color.brand.navy, shadowOpacity: 0.2, shadowRadius: 12, shadowOffset: { width: 0, height: 6 }, elevation: 8 }, printerLabel: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.08)' }, lights: { flexDirection: 'row', gap: spacing[1] }, light: { width: 6, height: 6, borderRadius: radius.full, backgroundColor: '#4a5568' }, green: { backgroundColor: color.state.success }, orange: { backgroundColor: color.state.dot }, slot: { width: '70%', height: 10, borderRadius: radius.full, backgroundColor: '#061328' }, ticketViewport: { width: '100%', height: 620, marginTop: -8, overflow: 'hidden', alignItems: 'center' }, printedPaper: { width: '82%', maxWidth: 300, overflow: 'hidden', backgroundColor: color.surface.card, shadowColor: color.brand.navy, shadowOpacity: 0.16, shadowRadius: 10, shadowOffset: { width: 0, height: 6 }, elevation: 4 }, ticket: { position: 'absolute', bottom: 0, width: '100%', height: 620, borderLeftWidth: 1, borderRightWidth: 1, borderColor: '#ddd5ca', backgroundColor: color.surface.card }, ticketHeader: { minHeight: 92, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: spacing[4], backgroundColor: color.brand.navy }, ticketBrand: { gap: spacing[1] }, ticketLogo: { width: 187, height: 34, marginLeft: -10 }, passLabel: { letterSpacing: 2.4 }, flightMark: { width: 56, height: 56, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.ivory }, flightIcon: { fontSize: 30, lineHeight: 34 }, ticketBody: { gap: spacing[3], padding: spacing[4], paddingBottom: 64 }, destination: { fontSize: 32, lineHeight: 38, letterSpacing: 2 }, routeLine: { height: 38, flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, routeTrack: { flex: 1, borderTopWidth: 1, borderStyle: 'dashed', borderColor: '#c8cace' }, routePlane: { fontSize: 20, lineHeight: 24, transform: [{ rotate: '8deg' }] }, ticketMeta: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing[2] }, metaCell: { flex: 1, gap: 2 }, previewRoute: { gap: spacing[2] }, receiptRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, receiptStop: { flex: 1 }, memoRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, memo: { flex: 1, gap: 2 }, stampImage: { width: 78, height: 78, transform: [{ rotate: '-7deg' }] }, dash: { borderTopWidth: 1, borderStyle: 'dashed', borderColor: '#c8cace' }, perforation: { position: 'absolute', left: 0, right: 0, bottom: 46, height: 10, justifyContent: 'center', backgroundColor: color.surface.card }, perforationCut: { borderTopWidth: 1, borderStyle: 'dashed', borderColor: '#c8cace' }, ticketTail: { position: 'absolute', left: 0, right: 0, bottom: 0, minHeight: 46, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: spacing[3], backgroundColor: color.surface.subtle }, actions: { width: '88%', maxWidth: 320, gap: spacing[2], marginTop: spacing[4] } });
