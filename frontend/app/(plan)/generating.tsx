import { useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, Animated, Pressable, StyleSheet, View } from 'react-native';
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

const STAGES = [
  { keys: ['접수', '후보', 'COLLECT'], label: '후보 장소 수집' },
  { keys: ['제약', '안전', 'FILTER'], label: '추천·안전 조건 판정' },
  { keys: ['동선', '경로', 'ROUTE', 'OPTIM'], label: '최적 동선 계산' },
  { keys: ['배치', '시간표', '일정', 'SCHEDULE'], label: '시간표 배치' },
] as const;

function stageIndex(stage: string | null, state: RecommendationJobSnapshot['state']) {
  if (state === 'completed') return STAGES.length;
  if (!stage) return state === 'accepted' ? 0 : -1;
  const normalized = stage.toUpperCase();
  const found = STAGES.findIndex((item) => item.keys.some((key) => normalized.includes(key.toUpperCase())));
  return found < 0 ? 0 : found;
}
function daysBetween(start: string, end: string) { const value = Math.round((new Date(`${end}T00:00:00`).getTime() - new Date(`${start}T00:00:00`).getTime()) / 86400000) + 1; return Number.isFinite(value) && value > 0 ? value : 1; }

export default function Generating() {
  const router = useRouter(); const { kind } = useLayout(); const { accessToken } = useAuth(); const { draft } = usePlan();
  const { jobId, preview } = useLocalSearchParams<{ jobId?: string; preview?: string }>();
  const adapter = useMemo(() => createRecommendationJobAdapter(accessToken), [accessToken]);
  const previewJob = __DEV__ && preview === 'completed' ? { state: 'completed', jobId: 'preview', progress: 100, stage: '시간표 배치', canCancel: false, errorMessage: null, resultRef: 'preview-trip' } satisfies RecommendationJobSnapshot : __DEV__ && preview === 'running' ? { state: 'polling', jobId: 'preview', progress: 48, stage: '최적 동선 계산', canCancel: false, errorMessage: null, resultRef: null } satisfies RecommendationJobSnapshot : null;
  const [job, setJob] = useState<RecommendationJobSnapshot>(() => previewJob ?? (jobId ? { state: 'accepted', jobId, progress: 0, stage: '요청 접수', canCancel: false, errorMessage: null, resultRef: null } : unavailableJob('생성 요청을 찾을 수 없어요. 조건을 확인한 뒤 다시 시작해 주세요.')));
  const [delayed, setDelayed] = useState(false); const [reduceMotion, setReduceMotion] = useState(false); const ticketY = useRef(new Animated.Value(24)).current;
  const currentStage = stageIndex(job.stage, job.state); const isWorking = job.state === 'accepted' || job.state === 'polling';
  useEffect(() => { void AccessibilityInfo.isReduceMotionEnabled().then(setReduceMotion); }, []);
  useEffect(() => { if (!isWorking || previewJob) return; const delayTimer = setTimeout(() => setDelayed(true), 10000); const pollTimer = setInterval(() => { setJob((current) => { if (!current.jobId) return current; void adapter.poll(current.jobId, current).then(setJob); return current; }); }, 2000); return () => { clearTimeout(delayTimer); clearInterval(pollTimer); }; }, [adapter, isWorking, previewJob]);
  useEffect(() => { if (reduceMotion) { ticketY.setValue(0); return; } Animated.spring(ticketY, { toValue: job.state === 'completed' ? 0 : 24, useNativeDriver: true, damping: 18, stiffness: 120 }).start(); }, [job.state, reduceMotion, ticketY]);
  const duration = daysBetween(draft.startDate, draft.endDate); const areas = draft.travelAreas.length ? draft.travelAreas.join(' · ') : '부산 맞춤 여행'; const failed = ['failed', 'conflict', 'cancelled', 'unavailable'].includes(job.state);
  return <PlanDesktopShell><Screen scroll wide style={styles.canvas}>
    {kind === 'phone' && <View style={styles.mobileTop}><Pressable accessibilityRole="button" accessibilityLabel="조건 확인으로 돌아가기" onPress={() => router.replace('/plan/confirm')} style={styles.back}><Text variant="title">‹</Text></Pressable><BrandLogoLink imageStyle={styles.logo} /><View style={styles.stepPill}><Text variant="caption" weight="bold" color={color.brand.ivory}>생성</Text></View></View>}
    <View style={[styles.layout, kind !== 'phone' && styles.layoutWide]}>
      <View style={[styles.statusPanel, kind !== 'phone' && styles.statusWide]}>
        <View style={styles.aiBadge}><View style={[styles.pulse, isWorking && styles.pulseActive]} /><Text variant="caption" weight="bold" color={color.brand.orange}>{job.state === 'completed' ? 'AI 일정 완성' : 'AI 일정 생성 중'}</Text></View>
        <Text variant="display" weight="bold" color={color.brand.ivory} style={styles.headline}>{job.state === 'completed' ? '당신만의 부산 여행이\n완성됐어요' : 'AI가 당신만을 위한\n부산 여행을 만들고 있어요'}</Text>
        <Text color="#a2a7b8">{failed ? job.errorMessage : delayed && isWorking ? '조금 더 시간이 필요해요. 화면을 닫지 않아도 진행 상태를 계속 확인합니다.' : '현지 정보와 안전 조건, 이동 부담을 함께 확인하고 있어요.'}</Text>
        {!failed && <View accessibilityLiveRegion="polite" style={styles.stageList}>{STAGES.map((item, index) => { const done = index < currentStage || job.state === 'completed'; const active = index === currentStage && isWorking; return <View key={item.label} style={[styles.stage, active && styles.stageActive]}><View style={[styles.stageIcon, done && styles.stageDone]}><Text variant="caption" weight="bold" color={done ? color.text.onAction : active ? color.brand.orange : '#6e7280'}>{done ? '✓' : '○'}</Text></View><Text weight={done || active ? 'bold' : 'regular'} color={done || active ? color.brand.ivory : '#6e7280'} style={styles.stageText}>{item.label}</Text><Text variant="caption" color={done ? color.state.success : active ? color.brand.orange : '#6e7280'}>{done ? '완료' : active ? '진행 중' : '대기'}</Text></View>; })}</View>}
        {job.progress !== null && !failed && <View style={styles.progressBlock}><View style={styles.progressTrack}><View style={[styles.progressFill, { width: `${job.progress}%` }]} /></View><Text variant="caption" color={color.brand.orange}>{job.stage ?? '요청 접수'} · {job.progress}%</Text></View>}
        {failed && <Button label="조건 다시 확인하기" onPress={() => router.replace('/plan/confirm')} containerStyle={styles.retry} />}
      </View>
      <View style={styles.ticketArea}><View style={styles.printer}><View style={styles.lights}><View style={[styles.light, styles.green]} /><View style={[styles.light, styles.orange]} /><View style={styles.light} /></View><View style={styles.slot} /></View>
        <Animated.View style={[styles.ticket, { transform: [{ translateY: ticketY }], opacity: failed ? 0.45 : 1 }]}><View style={styles.ticketHeader}><Text variant="caption" weight="bold" color={color.brand.ivory}>GABOLLE TRIP PASS</Text><Text variant="title" weight="bold" color={color.brand.ivory}>{areas}</Text></View><View style={styles.ticketBody}><View><Text variant="caption" color={color.text.muted}>TRAVEL DATE</Text><Text weight="bold">{draft.startDate || '일정 확인 중'}{draft.endDate ? ` ~ ${draft.endDate}` : ''}</Text></View><View style={styles.ticketMeta}><View><Text variant="caption" color={color.text.muted}>DURATION</Text><Text weight="bold">{duration}일</Text></View><View><Text variant="caption" color={color.text.muted}>BUDGET</Text><Text weight="bold">{draft.budgetKrw === null ? '미정' : `${draft.budgetKrw.toLocaleString()}원`}</Text></View></View><View style={styles.dash} /><Text variant="caption" color={color.text.muted}>{job.state === 'completed' ? '안전 조건을 반영한 일정이 준비됐어요.' : '서버에서 확인된 정보만 여행표에 인쇄합니다.'}</Text></View><View style={styles.ticketTail}><Text variant="caption" color={color.text.muted}>{job.state === 'completed' ? 'READY TO GO' : job.stage ?? 'AI 생성 대기 중'}</Text></View></Animated.View>
        {job.state === 'completed' && <View style={styles.actions}><Button label="추천 결과 확인하기" onPress={() => job.resultRef && router.replace(`/trips/${job.resultRef}/recommendations?jobId=${encodeURIComponent(job.jobId ?? '')}`)} disabled={!job.resultRef || !job.jobId} /><View style={styles.secondaryActions}><Button label="여행표 저장" variant="ghost" disabled /><Button label="공유" variant="ghost" disabled /></View><Text variant="caption" color={color.text.muted}>추천 후보를 확인한 뒤 완성된 일정으로 이동할 수 있어요.</Text></View>}
      </View>
    </View>
  </Screen></PlanDesktopShell>;
}
const styles = StyleSheet.create({ canvas: { backgroundColor: color.brand.ivory }, mobileTop: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[4] }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, logo: { width: 88, height: 28 }, stepPill: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.brand.navy }, layout: { gap: spacing[4] }, layoutWide: { minHeight: 620, flexDirection: 'row', alignItems: 'stretch' }, statusPanel: { gap: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.navy }, statusWide: { width: '44%', padding: spacing[8], justifyContent: 'center' }, aiBadge: { alignSelf: 'flex-start', flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.08)' }, pulse: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: '#4a5568' }, pulseActive: { backgroundColor: color.brand.orange }, headline: { lineHeight: 32 }, stageList: { gap: spacing[3] }, stage: { minHeight: 54, flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: 'rgba(255,255,255,0.03)' }, stageActive: { borderWidth: 1, borderColor: 'rgba(242,101,50,0.45)', backgroundColor: 'rgba(242,101,50,0.08)' }, stageIcon: { width: 28, height: 28, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.06)' }, stageDone: { backgroundColor: color.state.success }, stageText: { flex: 1 }, progressBlock: { gap: spacing[2] }, progressTrack: { height: 6, overflow: 'hidden', borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.08)' }, progressFill: { height: 6, borderRadius: radius.full, backgroundColor: color.brand.orange }, retry: { backgroundColor: color.brand.orange }, ticketArea: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[6], borderRadius: radius.lg, backgroundColor: '#f0eee8' }, printer: { zIndex: 2, width: '100%', maxWidth: 400, height: 100, alignItems: 'center', justifyContent: 'center', gap: spacing[3], borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg, backgroundColor: color.brand.navy }, lights: { flexDirection: 'row', gap: spacing[1] }, light: { width: 6, height: 6, borderRadius: radius.full, backgroundColor: '#4a5568' }, green: { backgroundColor: color.state.success }, orange: { backgroundColor: color.brand.orange }, slot: { width: '70%', height: 8, borderRadius: radius.full, backgroundColor: '#061328' }, ticket: { width: '88%', maxWidth: 320, backgroundColor: color.surface.card }, ticketHeader: { gap: spacing[2], padding: spacing[4], backgroundColor: color.brand.navy }, ticketBody: { gap: spacing[4], padding: spacing[4] }, ticketMeta: { flexDirection: 'row', justifyContent: 'space-between' }, dash: { borderTopWidth: 1, borderStyle: 'dashed', borderColor: '#c8cace' }, ticketTail: { padding: spacing[3], borderTopWidth: 1, borderColor: '#eae6df', backgroundColor: '#f6f5f2' }, actions: { width: '88%', maxWidth: 320, gap: spacing[2], marginTop: spacing[6] }, secondaryActions: { flexDirection: 'row', gap: spacing[2] } });
