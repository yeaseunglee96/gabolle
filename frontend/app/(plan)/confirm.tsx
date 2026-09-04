import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Button } from '@/components/Button';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { useAuth } from '@/auth/AuthProvider';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { PlanStepHeader } from '@/plan/PlanStepHeader';
import { PlanDesktopShell } from '@/plan/PlanDesktopShell';
import { usePlan } from '@/plan/PlanProvider';
import { createRecommendationJobAdapter, type RecommendationJobSnapshot } from '@/plan/recommendationJob';
import { useI18n } from '@/i18n';

const LABEL: Record<string, string> = {
  SEA_BEACH: '바다 & 해변', CITY: '도심 탐험', CAFE_HEALING: '카페 & 힐링', CULTURE_TEMPLE: '문화 & 사찰', FOOD: '맛집 & 먹거리', NATURE_WALK: '자연 & 산책',
  LIVELY: '활기찬', RELAXED: '여유로운', SENTIMENTAL: '감성적인', ROMANTIC: '낭만적인',
  SEAFOOD: '해산물', PORK_SOUP: '돼지국밥', MILMYEON: '밀면', CAFE_DESSERT: '카페·디저트', MARKET: '시장 먹거리', VEGETARIAN: '채식',
  PEANUT: '땅콩', TREE_NUT: '견과류', SHELLFISH_CRUSTACEAN: '갑각류', SHELLFISH: '갑각류', FISH: '생선', EGG: '달걀', MILK_DAIRY: '우유·유제품', MILK: '우유·유제품', WHEAT: '밀', SOY: '대두',
  VEGAN: '비건', HALAL: '할랄', GLUTEN_FREE: '글루텐 프리', PESCATARIAN: '페스코', seedHotteok: '씨앗호떡',
};
const names = (values: string[], empty = '해당 없음') => values.map((value) => LABEL[value] ?? value).join(' · ') || empty;

function Row({ label, value }: { label: string; value: string }) { return <View style={styles.row}><Text variant="caption" color={color.text.muted}>{label}</Text><Text weight="bold" style={styles.value}>{value}</Text></View>; }
function Section({ title, path, hard, children }: { title: string; path: '/plan/basic' | '/plan/taste' | '/plan/conditions'; hard?: boolean; children: React.ReactNode }) { const router = useRouter(); const { tx } = useI18n(); return <View style={[styles.card, hard && styles.hardCard]}><View style={styles.cardHeader}><View style={styles.cardTitle}>{hard && <View style={styles.dot} />}<Text variant="title" weight="bold">{title}</Text></View><Pressable accessibilityRole="button" accessibilityLabel={tx(`${title} 수정`, `Edit ${title}`)} onPress={() => router.push(path)} style={styles.edit}><Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('수정', 'Edit')}</Text></Pressable></View>{children}</View>; }

export default function Confirm() {
  const router = useRouter(); const { kind } = useLayout(); const { tx, locale, language } = useI18n(); const { user, accessToken, ready: authReady } = useAuth(); const { draft, basicComplete } = usePlan(); const [job, setJob] = useState<RecommendationJobSnapshot | null>(null);
  useEffect(() => { if (!basicComplete) router.replace('/plan/basic'); }, [basicComplete, router]);
  const allergy = draft.allergyStatus === 'UNKNOWN' ? '미확인' : draft.allergyStatus === 'NONE' ? '해당 없음' : names(draft.allergies);
  const diet = draft.dietStatus === 'UNKNOWN' ? '미확인' : draft.dietStatus === 'NONE' ? '해당 없음' : names(draft.dietTypes);
  const hardUnknown = draft.allergyStatus === 'UNKNOWN' || draft.dietStatus === 'UNKNOWN' || (draft.allergyStatus === 'VALUES' && !draft.allergies.length) || (draft.dietStatus === 'VALUES' && !draft.dietTypes.length);
  const environment = [draft.maxWalkingDistanceM === null ? '보행거리 미확인' : draft.maxWalkingDistanceM === 0 ? '보행거리 제한 없음' : `최대 ${draft.maxWalkingDistanceM.toLocaleString()}m`, draft.slopeConstraint === 'AVOID' ? '경사 피하기' : draft.slopeConstraint === 'ALLOW' ? '경사 허용' : '경사 미확인', draft.stairsConstraint === 'AVOID' ? '계단 피하기' : draft.stairsConstraint === 'ALLOW' ? '계단 허용' : '계단 미확인', draft.shadePreference === 'PREFER' ? '그늘길 우선' : draft.shadePreference === 'NO_PREFERENCE' ? '그늘 무관' : '그늘 미확인'].join(' · ');
  const assists = [draft.wheelchair === null ? '휠체어 미확인' : draft.wheelchair ? '휠체어 있음' : null, draft.stroller === null ? '유아차 미확인' : draft.stroller ? '유아차 있음' : null, draft.luggage === null ? '큰 짐 미확인' : draft.luggage ? '큰 짐 있음' : null].filter(Boolean).join(' · ') || '해당 없음';
  const conflict = draft.transport === 'WALK' && draft.maxWalkingDistanceM !== null && draft.maxWalkingDistanceM > 0 && draft.maxWalkingDistanceM <= 500;
  return <PlanDesktopShell><Screen scroll wide style={styles.canvas}>
    {kind === 'phone' && <View style={styles.top}><Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" onPress={() => router.canGoBack() ? router.back() : router.replace('/plan/conditions')} style={styles.back}><Text variant="title">‹</Text></Pressable><BrandLogoLink imageStyle={styles.logo} /><View style={styles.pill}><Text variant="caption" weight="bold" color={color.brand.ivory}>4 / 4</Text></View></View>}
    <PlanStepHeader current={4} /><Text variant="display" weight="bold" style={styles.title}>{tx('여행 조건을 확인해 주세요', 'Review your trip details')}</Text><Text color={color.text.body} style={styles.subtitle}>{tx('일정을 만들기 전에 입력한 내용을 한 번 더 확인해요.', 'Check your choices once more before creating the itinerary.')}</Text>
    <View style={[styles.grid, kind === 'tablet' && styles.gridWide]}>
      <Section title={tx('기본 정보', 'Trip basics')} path="/plan/basic"><Row label={tx('여행 날짜', 'Travel dates')} value={`${draft.startDate || tx('미입력', 'Not entered')} ~ ${draft.endDate || tx('미입력', 'Not entered')}`} /><Row label={tx('인원 · 출발지', 'Travelers · Starting point')} value={tx(`${draft.adults}명 성인 · ${draft.children}명 어린이 · ${draft.origin || '미입력'}`, `${draft.adults} adults · ${draft.children} children · ${draft.origin || 'Not entered'}`)} /><Row label={tx('예산 · 교통', 'Budget · Transport')} value={`${draft.budgetKrw === null ? tx('예산 미입력', 'Budget not entered') : tx(`${draft.budgetKrw.toLocaleString(locale)}원 (숙박비 제외)`, `KRW ${draft.budgetKrw.toLocaleString(locale)} excluding lodging`)} · ${language === 'en' ? ({ TRANSIT: 'Public transit', WALK: 'Mostly walking', CAR: 'Car' } as const)[draft.transport] : ({ TRANSIT: '대중교통', WALK: '도보 위주', CAR: '자차' } as const)[draft.transport]}`} /></Section>
      <Section title={tx('여행 취향', 'Travel preferences')} path="/plan/taste"><Row label={tx('카테고리', 'Categories')} value={language === 'en' ? (draft.preferences.join(' · ') || 'None selected') : names(draft.preferences, '선택 안 함')} /><Row label={tx('분위기', 'Mood')} value={language === 'en' ? (draft.atmospheres.join(' · ') || 'None selected') : names(draft.atmospheres, '선택 안 함')} /><Row label={tx('음식', 'Food')} value={language === 'en' ? (draft.foods.join(' · ') || 'None selected') : names(draft.foods, '선택 안 함')} /></Section>
      <Section title={tx('반드시 지킬 조건', 'Required constraints')} path="/plan/conditions" hard><Row label={tx('알레르기', 'Allergies')} value={allergy} /><Row label={tx('식단', 'Diet')} value={diet} /><Row label={tx('보행 · 길 환경', 'Walking · Route')} value={environment} /><Row label={tx('이동 보조 · 짐', 'Mobility aids · Luggage')} value={assists} />{hardUnknown && <Pressable accessibilityRole="button" onPress={() => router.push('/plan/conditions')} style={styles.warning}><Text accessibilityRole="alert" variant="caption" weight="bold" color={color.state.danger}>{tx('미확인 필수 조건이 있어요. 제약 조건을 확인해 주세요 →', 'Some required constraints are unanswered. Review them →')}</Text></Pressable>}</Section>
    </View>
    {conflict && <View style={styles.conflict}><Text accessibilityRole="alert" variant="caption" weight="bold" color={color.state.warning}>도보 위주 이동과 500m 이하 보행 제한이 함께 선택됐어요. 생성 전에 이동수단을 확인해 주세요.</Text></View>}
    <View style={styles.notice}><Text variant="caption" color={color.text.body}>{tx('장소 운영시간·접근성·혼잡도는 최신 정보가 아닐 수 있어요. 최종 방문 전 공식 정보를 확인해 주세요.', 'Hours, accessibility, and crowd data may change. Check official information before visiting.')}</Text></View>
    <View style={styles.nextSteps}><Text weight="bold">{tx('이후 진행 단계', 'What happens next')}</Text><Text variant="caption" color={color.text.body}>{tx('조건 검토 → 추천 장소 구성 → 이동 동선 확인 → 일정 완성', 'Review constraints → Build recommendations → Check routes → Complete itinerary')}</Text></View>
    {job?.state === 'unavailable' && <View style={styles.unavailable}><Text accessibilityRole="alert" variant="caption" style={styles.generateNotice}>{job.errorMessage}</Text><Button accessibilityRole="button" label="조건 다시 확인" variant="ghost" onPress={() => router.push('/plan/conditions')} /></View>}
    <Button accessibilityRole="button" accessibilityState={{ disabled: !basicComplete || hardUnknown || !authReady, busy: job?.state === 'submitting' }} accessibilityHint={hardUnknown ? '미확인 제약 조건을 먼저 확인해 주세요.' : user ? '일정 생성을 요청합니다.' : '로그인 후 입력한 조건으로 일정 생성을 계속합니다.'} label={!authReady ? '로그인 상태 확인 중…' : job?.state === 'submitting' ? '요청 중…' : user ? '이 조건으로 일정 만들기' : '로그인하고 일정 만들기'} disabled={!basicComplete || hardUnknown || !authReady || job?.state === 'submitting'} containerStyle={styles.cta} onPress={async () => {
      if (!user) { router.push({ pathname: '/sign-in', params: { returnTo: '/plan/confirm' } }); return; }
      setJob({ state: 'submitting', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: null, resultRef: null });
      const next = await createRecommendationJobAdapter(accessToken).submit({
        trip: { startDate: draft.startDate, endDate: draft.endDate, partySize: draft.travelers, budgetKrw: draft.budgetKrw, origin: draft.origin, travelMode: draft.transport, timeWindowStart: draft.dayStartTime, timeWindowEnd: draft.dayEndTime },
        preferences: { categories: draft.preferences, atmospheres: draft.atmospheres, foods: draft.foods, localityLevel: draft.localityLevel, quietLevel: draft.quietLevel, touristLevel: draft.touristLevel },
        constraints: { allergyStatus: draft.allergyStatus, allergies: draft.allergies, dietStatus: draft.dietStatus, dietTypes: draft.dietTypes, maximumWalkingMeters: draft.maxWalkingDistanceM, slopePreference: draft.slopeConstraint, stairsAvoidance: draft.stairsConstraint === 'AVOID', shadePreference: draft.shadePreference, wheelchair: draft.wheelchair, stroller: draft.stroller, heavyLuggage: draft.luggage },
      });
      setJob(next);
      if (next.jobId) router.push({ pathname: '/plan/generating', params: { jobId: next.jobId } });
    }} />
  </Screen></PlanDesktopShell>;
}
const styles = StyleSheet.create({ canvas: { backgroundColor: color.brand.ivory }, top: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, logo: { width: 88, height: 28 }, pill: { borderRadius: radius.full, backgroundColor: color.brand.navy, paddingHorizontal: spacing[3], paddingVertical: spacing[1] }, title: { marginTop: spacing[4] }, subtitle: { marginTop: spacing[1], marginBottom: spacing[4] }, grid: { gap: spacing[4] }, gridWide: { flexDirection: 'row', flexWrap: 'wrap' }, card: { minWidth: '48%', flexGrow: 1, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border }, hardCard: { borderColor: color.state.danger, backgroundColor: color.state.dangerBg }, cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }, cardTitle: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, dot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.brand.orange }, edit: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' }, row: { gap: spacing[1], paddingBottom: spacing[2], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border }, value: { flexShrink: 1 }, warning: { minHeight: 44, justifyContent: 'center', padding: spacing[2], borderRadius: radius.sm, backgroundColor: color.state.dangerBg }, conflict: { marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg }, notice: { marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.subtle }, nextSteps: { marginTop: spacing[3], gap: spacing[1], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card }, unavailable: { marginTop: spacing[3], gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg }, generateNotice: { color: color.text.eyebrow, textAlign: 'center' }, cta: { minHeight: 54, marginTop: spacing[3], backgroundColor: color.brand.navy } });
