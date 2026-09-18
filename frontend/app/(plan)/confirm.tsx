import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Button } from '@/components/Button';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { updateMyConsents } from '@/auth/authApi';
import { subscribeApiAvailability } from '@/api/client';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { PlanStepHeader } from '@/plan/PlanStepHeader';
import { useFocusedRedirect } from '@/nav/useFocusedRedirect';
import { usePlan } from '@/plan/PlanProvider';
import { createRecommendationJobAdapter, type RecommendationJobSnapshot } from '@/plan/recommendationJob';
import { useI18n } from '@/i18n';
import { ConditionsPromptModal } from '@/plan/ConditionsPromptModal';

const LABEL: Record<string, [string, string]> = {
  SEA_BEACH: ['바다 & 해변', 'Sea & Beach'], CITY: ['도심 탐험', 'City Exploration'], CAFE_HEALING: ['카페 & 힐링', 'Cafe & Healing'], CULTURE_TEMPLE: ['문화 & 사찰', 'Culture & Temples'], FOOD: ['맛집 & 먹거리', 'Food & Eats'], NATURE_WALK: ['자연 & 산책', 'Nature & Walks'],
  LIVELY: ['활기찬', 'Lively'], RELAXED: ['여유로운', 'Relaxed'], SENTIMENTAL: ['감성적인', 'Sentimental'], ROMANTIC: ['낭만적인', 'Romantic'],
  SEAFOOD: ['해산물', 'Seafood'], PORK_SOUP: ['돼지국밥', 'Pork bone soup'], MILMYEON: ['밀면', 'Milmyeon (cold noodles)'], CAFE_DESSERT: ['카페·디저트', 'Cafe & Dessert'], MARKET: ['시장 먹거리', 'Market food'], VEGETARIAN: ['채식', 'Vegetarian'],
  PEANUT: ['땅콩', 'Peanuts'], TREE_NUT: ['견과류', 'Tree nuts'], SHELLFISH_CRUSTACEAN: ['갑각류', 'Shellfish (crustacean)'], SHELLFISH: ['갑각류', 'Shellfish'], FISH: ['생선', 'Fish'], EGG: ['달걀', 'Egg'], MILK_DAIRY: ['우유·유제품', 'Milk & dairy'], MILK: ['우유·유제품', 'Milk & dairy'], WHEAT: ['밀', 'Wheat'], SOY: ['대두', 'Soy'],
  VEGAN: ['비건', 'Vegan'], HALAL: ['할랄', 'Halal'], GLUTEN_FREE: ['글루텐 프리', 'Gluten-free'], PESCATARIAN: ['페스코', 'Pescatarian'], seedHotteok: ['씨앗호떡', 'Seed hotteok'],
};
const names = (tx: (ko: string, en: string) => string, values: string[], emptyKo = '해당 없음', emptyEn = 'None') =>
  values.map((value) => (LABEL[value] ? tx(LABEL[value][0], LABEL[value][1]) : value)).join(' · ') || tx(emptyKo, emptyEn);

function Row({ label, value }: { label: string; value: string }) { return <View style={styles.row}><Text variant="caption" color={color.text.muted}>{label}</Text><Text weight="bold" style={styles.value}>{value}</Text></View>; }
function formatDate(iso: string, locale: string, fallback: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(iso)) return fallback;
  const date = new Date(`${iso}T00:00:00`);
  return Number.isNaN(date.getTime()) ? fallback : date.toLocaleDateString(locale, { year: 'numeric', month: 'short', day: 'numeric' });
}
// 🔴 「수정」이 어디로 가는지는 칸마다 다르다 (S15P21E201-1245).
//    「반드시 지킬 조건」의 알레르기·식단·길 환경은 **질문 페이지가 안 묻는다** — 모달이 묻는다.
//    그런데 이 단추가 다 같이 질문 페이지로 보내고 있어서, 고치러 눌러도 고칠 자리가 없었다.
//    그래서 `onEdit` 이 있으면 그것을 부르고, 없을 때만 페이지로 보낸다.
function Section({ title, path, hard, alert, onEdit, children }: { title: string; path: '/plan'; hard?: boolean; alert?: boolean; onEdit?: () => void; children: React.ReactNode }) { const router = useRouter(); const { tx } = useI18n(); return <View style={[styles.card, alert && styles.alertCard]}><View style={styles.cardHeader}><View style={styles.cardTitle}>{hard && <View style={styles.dot} />}<Text variant="title" weight="bold">{title}</Text></View><Pressable accessibilityRole="button" accessibilityLabel={tx(`${title} 수정`, `Edit ${title}`)} onPress={() => (onEdit ? onEdit() : router.push(path))} style={styles.edit}><Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('수정', 'Edit')}</Text></Pressable></View>{children}</View>; }

/**
 * 지역 코드의 이름 — S15P21E201-980. 기본 정보 화면의 {@code AREAS} 와 같은 목록이다.
 *
 * 두 곳에 같은 표가 생기지만, 확인 화면이 기본 정보 화면의 내부 상수를 끌어다 쓰면 그쪽
 * 화면을 고칠 때 이 화면이 조용히 따라 깨진다. 모르는 코드는 코드 그대로 보여 준다.
 */
const AREA_NAMES: Record<string, [string, string]> = {
  HAEUNDAE: ['해운대', 'Haeundae'],
  GWANGALLI: ['광안리', 'Gwangalli'],
  NAMPO: ['남포동', 'Nampo-dong'],
  SEOMYEON: ['서면', 'Seomyeon'],
  YEONGDO: ['영도', 'Yeongdo'],
  SONGJEONG: ['송정', 'Songjeong'],
};


export default function Confirm() {
  const router = useRouter(); const { preview } = useLocalSearchParams<{ preview?: string }>(); const { kind } = useLayout(); const { tx, locale } = useI18n(); const { user, accessToken, ready: authReady } = useAuth(); const { draft, basicComplete, ready: planReady } = usePlan(); const [job, setJob] = useState<RecommendationJobSnapshot | null>(preview === 'api-error' ? { state: 'unavailable', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: tx('여행 생성 서버 연결을 확인하고 있어요. 잠시 후 다시 시도해 주세요.', 'Checking the itinerary server connection. Please try again shortly.'), resultRef: null } : null);
  const [apiUnavailable, setApiUnavailable] = useState(false);
  // 🔴 「미확인 필수 조건이 있어요」를 누르면 **여기서 바로 묻는다.** 전에는 옛 제약조건
  //    화면으로 보냈는데 그 화면이 없어져서, 눌러도 물어볼 자리가 없었다 (2026-09-18).
  const [conditionsOpen, setConditionsOpen] = useState(false);
  const [grantingConsent, setGrantingConsent] = useState(false);
  useEffect(() => subscribeApiAvailability(setApiUnavailable), []);

  // S15P21E201-549(백엔드, 2026-09-11) — 알레르기·필수 식단 제약이 있는 요청은 HEALTH_CONSTRAINTS
  // 동의 없이 403을 받는다. 동의를 켜고 같은 조건으로 곧바로 다시 요청한다 — 사용자가 방금 4단계를
  // 채운 그 화면에서 한 번 더 버튼을 누르게 하지 않는다.
  async function grantHealthConsentAndRetry() {
    if (!accessToken || grantingConsent) return;
    setGrantingConsent(true);
    try {
      await updateMyConsents(accessToken, { HEALTH_CONSTRAINTS: true });
      setJob({ state: 'submitting', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: null, resultRef: null });
      const next = await createRecommendationJobAdapter(accessToken).submit(draft);
      setJob(next);
      if (next.jobId) router.push({ pathname: '/plan/generating', params: { jobId: next.jobId } });
    } catch (cause) {
      setJob({ state: 'failed', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: cause instanceof ApiClientError ? cause.message : tx('동의 처리에 실패했어요. 잠시 후 다시 시도해 주세요.', 'Could not save your consent. Please try again shortly.'), resultRef: null });
    } finally {
      setGrantingConsent(false);
    }
  }
  // 🔴 S15P21E201-1128 — 이 화면을 보고 있을 때만 보낸다.
  //
  //    전에는 그냥 useEffect 였다. 그런데 이 화면은 생성 화면으로 push 한 뒤에도
  //    밑에 남아 계속 돈다. 일정이 완성되면 생성 화면이 clear() 로 입력을 비우는데,
  //    그 순간 basicComplete 가 거짓이 되어 이 줄이 사용자를 1단계 빈 화면으로
  //    끌어내렸다 — 서버는 여행도 일정도 다 만들어 놓은 뒤였다.
  // 🔴 초안을 **다 읽기 전에는 판단하지 않는다** (S15P21E201-1245). 저장소에서 읽어오는
  //    동안 basicComplete 는 거짓이라, 이 줄이 그 찰나에 사용자를 1단계로 끌어내렸다 —
  //    이 주소를 새로고침하거나 링크로 바로 열면 **항상** 그랬다. 2026-09-18 실측.
  useFocusedRedirect(planReady && !basicComplete && preview !== 'api-error', '/plan');
  const allergy = draft.allergyStatus === 'UNKNOWN' ? tx('미확인', 'Unconfirmed') : draft.allergyStatus === 'NONE' ? tx('해당 없음', 'None') : names(tx, draft.allergies);
  const diet = draft.dietStatus === 'UNKNOWN' ? tx('미확인', 'Unconfirmed') : draft.dietStatus === 'NONE' ? tx('해당 없음', 'None') : names(tx, draft.dietTypes);
  const hardUnknown = draft.allergyStatus === 'UNKNOWN' || draft.dietStatus === 'UNKNOWN' || (draft.allergyStatus === 'VALUES' && !draft.allergies.length) || (draft.dietStatus === 'VALUES' && !draft.dietTypes.length);
  const environment = [draft.maxWalkingDistanceM === null ? tx('보행거리 미확인', 'Walking distance unconfirmed') : draft.maxWalkingDistanceM === 0 ? tx('보행거리 제한 없음', 'No walking distance limit') : tx(`최대 ${draft.maxWalkingDistanceM.toLocaleString()}m`, `Up to ${draft.maxWalkingDistanceM.toLocaleString()}m`), draft.slopeConstraint === 'AVOID' ? tx('경사 피하기', 'Avoid slopes') : draft.slopeConstraint === 'ALLOW' ? tx('경사 허용', 'Slopes okay') : tx('경사 미확인', 'Slope preference unconfirmed'), draft.stairsConstraint === 'AVOID' ? tx('계단 피하기', 'Avoid stairs') : draft.stairsConstraint === 'ALLOW' ? tx('계단 허용', 'Stairs okay') : tx('계단 미확인', 'Stairs preference unconfirmed'), draft.shadePreference === 'PREFER' ? tx('그늘길 우선', 'Prefer shaded routes') : draft.shadePreference === 'NO_PREFERENCE' ? tx('그늘 무관', 'No shade preference') : tx('그늘 미확인', 'Shade preference unconfirmed')].join(' · ');
  const assists = [draft.wheelchair === null ? tx('휠체어 미확인', 'Wheelchair unconfirmed') : draft.wheelchair ? tx('휠체어 있음', 'Uses wheelchair') : null, draft.stroller === null ? tx('유아차 미확인', 'Stroller unconfirmed') : draft.stroller ? tx('유아차 있음', 'Has stroller') : null, draft.luggage === null ? tx('큰 짐 미확인', 'Luggage unconfirmed') : draft.luggage ? tx('큰 짐 있음', 'Has large luggage') : null].filter(Boolean).join(' · ') || tx('해당 없음', 'None');
  const usage = [draft.englishMenuRequired ? tx('영어 메뉴 필요', 'Needs English menu') : null, draft.foreignCardRequired ? tx('해외카드 결제 필요', 'Needs foreign card payment') : null, draft.soloDiningPreferred ? tx('혼밥 우선', 'Prefers solo dining') : null, draft.accommodation.trim() ? tx(`숙소: ${draft.accommodation.trim()}`, `Lodging: ${draft.accommodation.trim()}`) : null, draft.transport !== 'CAR' && draft.maxTransfers !== null ? tx(`최대 환승 ${draft.maxTransfers}회`, `Up to ${draft.maxTransfers} transfers`) : null].filter(Boolean).join(' · ') || tx('없음', 'None');
  // S15P21E201-975 — 고른 곳을 여기서도 보여 준다. 이 줄이 없던 동안에는 취향 단계에서 고른
  // 장소가 확인 화면 어디에도 안 나와서, 빠졌는지 들어갔는지 사용자가 알 길이 없었다.
  // S15P21E201-980 — 고른 범위를 여기서도 보여 준다. 이 줄이 없던 동안 지역 칩이 확인
  // 화면 어디에도 안 나와서, 반영됐는지 사용자가 알 길이 없었다.
  const areas = draft.travelAreas.length
    ? draft.travelAreas.map((code) => tx(AREA_NAMES[code]?.[0] ?? code, AREA_NAMES[code]?.[1] ?? code)).join(' · ')
    : tx('선택 안 함', 'None selected');
  const mustVisit = draft.mustVisitPlaces.length
    ? draft.mustVisitPlaces.map((place) => tx(place.nameKo, place.nameEn ?? place.nameKo)).join(' · ')
    : tx('선택 안 함', 'None selected');
  const conflict = draft.transport === 'WALK' && draft.maxWalkingDistanceM !== null && draft.maxWalkingDistanceM > 0 && draft.maxWalkingDistanceM <= 500;
  return <Screen scroll wide style={styles.canvas}>
    {kind === 'phone' && <View style={styles.top}><Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/plan/conditions')} style={styles.back}><Text variant="title">‹</Text></Pressable><BrandLogoLink imageStyle={styles.logo} /><View style={styles.pill}><Text variant="caption" weight="bold" color={color.brand.ivory}>4 / 4</Text></View></View>}
    <PlanStepHeader current={4} /><Text variant="display" weight="bold" style={styles.title}>{tx('여행 조건을 확인해 주세요', 'Review your trip details')}</Text><Text color={color.text.body} style={styles.subtitle}>{tx('일정을 만들기 전에 입력한 내용을 한 번 더 확인해요.', 'Check your choices once more before creating the itinerary.')}</Text>
    <View style={[styles.grid, kind === 'tablet' && styles.gridWide]}>
      <Section title={tx('기본 정보', 'Trip basics')} path="/plan"><Row label={tx('여행 날짜', 'Travel dates')} value={`${formatDate(draft.startDate, locale, tx('미입력', 'Not entered'))} ~ ${formatDate(draft.endDate, locale, tx('미입력', 'Not entered'))}`} /><Row label={tx('인원 · 출발지', 'Travelers · Starting point')} value={tx(`${draft.adults}명 성인 · ${draft.children}명 어린이 · ${draft.origin || '미입력'}`, `${draft.adults} adults · ${draft.children} children · ${draft.origin || 'Not entered'}`)} /><Row label={tx('예산 · 교통', 'Budget · Transport')} value={`${draft.budgetKrw === null ? tx('예산 미입력', 'Budget not entered') : tx(`${draft.budgetKrw.toLocaleString(locale)}원 (숙박비 제외)`, `KRW ${draft.budgetKrw.toLocaleString(locale)} excluding lodging`)} · ${tx(({ TRANSIT: '대중교통', WALK: '도보 위주', CAR: '자차' } as const)[draft.transport], ({ TRANSIT: 'Public transit', WALK: 'Mostly walking', CAR: 'Car' } as const)[draft.transport])}`} /><Row label={tx('여행 범위', 'Areas to visit')} value={areas} /><Row label={tx('이동 시간대', 'Daily hours')} value={draft.dayStartTime && draft.dayEndTime ? `${draft.dayStartTime} ~ ${draft.dayEndTime}` : tx('없음', 'None')} /></Section>
      <Section title={tx('여행 취향', 'Travel preferences')} path="/plan"><Row label={tx('여행 기분', 'Trip pace')} value={draft.paceLevel === 'RELAXED' ? tx('여유롭게', 'Relaxed') : draft.paceLevel === 'BALANCED' ? tx('균형 있게', 'Balanced') : draft.paceLevel === 'PACKED' ? tx('알차게', 'Packed') : tx('선택 안 함 (균형 있게로 진행)', 'Not selected (defaults to Balanced)')} /><Row label={tx('카테고리', 'Categories')} value={names(tx, draft.preferences, '선택 안 함', 'None selected')} /><Row label={tx('분위기', 'Mood')} value={names(tx, draft.atmospheres, '선택 안 함', 'None selected')} /><Row label={tx('음식', 'Food')} value={names(tx, draft.foods, '선택 안 함', 'None selected')} /><Row label={tx('꼭 가고 싶은 장소', 'Must-visit places')} value={mustVisit} /></Section>
      <Section title={tx('반드시 지킬 조건', 'Required constraints')} path="/plan" hard alert={hardUnknown} onEdit={() => setConditionsOpen(true)}><Row label={tx('알레르기', 'Allergies')} value={allergy} /><Row label={tx('식단', 'Diet')} value={diet} /><Row label={tx('보행 · 길 환경', 'Walking · Route')} value={environment} /><Row label={tx('이동 보조 · 짐', 'Mobility aids · Luggage')} value={assists} /><Row label={tx('이용 조건', 'Usage conditions')} value={usage} /><Text variant="caption" color={color.text.muted}>{tx('알레르기·식단 정보가 없는 장소는 안전하다고 추정하지 않아요.', 'We never assume a place is safe when allergy or diet information is missing.')}</Text>{hardUnknown &&<Pressable accessibilityRole="button" onPress={() => setConditionsOpen(true)} style={styles.warning}><Text accessibilityRole="alert" variant="caption" weight="bold" color={color.state.danger}>{tx('미확인 필수 조건이 있어요. 제약 조건을 확인해 주세요 →', 'Some required constraints are unanswered. Review them →')}</Text></Pressable>}</Section>
    </View>
    {conflict && <View style={styles.conflict}><Text accessibilityRole="alert" variant="caption" weight="bold" color={color.state.warning}>{tx('도보 위주 이동과 500m 이하 보행 제한이 함께 선택됐어요. 생성 전에 이동수단을 확인해 주세요.', 'You picked walking as transport but limited walking to under 500m. Please review your transport choice before generating.')}</Text></View>}
    <View style={styles.notice}><Text variant="caption" color={color.text.body}>{tx('장소 운영시간·접근성·혼잡도는 최신 정보가 아닐 수 있어요. 최종 방문 전 공식 정보를 확인해 주세요.', 'Hours, accessibility, and crowd data may change. Check official information before visiting.')}</Text></View>
    <View style={styles.nextSteps}><Text weight="bold">{tx('이후 진행 단계', 'What happens next')}</Text><Text variant="caption" color={color.text.body}>{tx('조건 검토 → 추천 장소 구성 → 이동 동선 확인 → 일정 완성', 'Review constraints → Build recommendations → Check routes → Complete itinerary')}</Text></View>
    {job?.state === 'consent-required' && job.requiredConsent === 'HEALTH_CONSTRAINTS' && <View style={styles.unavailable}><Text accessibilityRole="alert" variant="caption" weight="bold" style={styles.generateNotice}>{tx('알레르기·식단 정보 사용에 동의가 필요해요', 'We need your consent to use allergy/diet info')}</Text><Text variant="caption" color={color.text.body}>{tx('입력하신 알레르기·필수 식단 조건으로 안전한 장소만 추천하려면 건강 정보 사용에 동의해 주세요. 언제든 마이페이지에서 철회할 수 있어요.', 'To recommend only safe places for your allergy/required diet, please consent to using health info. You can withdraw anytime in My Page.')}</Text><Button accessibilityRole="button" label={grantingConsent ? tx('처리 중…', 'Working…') : tx('동의하고 계속하기', 'Consent and continue')} disabled={grantingConsent} onPress={() => void grantHealthConsentAndRetry()} /></View>}
    {job?.errorMessage && job.state !== 'consent-required' && <View style={styles.unavailable}><Text accessibilityRole="alert" variant="caption" style={styles.generateNotice}>{job.errorMessage}</Text><Text variant="caption" color={color.text.body}>{tx('입력한 조건은 그대로 보관돼요. 서버가 준비되면 아래 버튼으로 다시 요청할 수 있어요.', 'Your choices are preserved. Retry below when the server is ready.')}</Text><Button accessibilityRole="button" label={tx('조건 다시 확인', 'Review constraints')} variant="ghost" onPress={() => setConditionsOpen(true)} /></View>}
    <Button accessibilityRole="button" accessibilityState={{ disabled: !basicComplete || hardUnknown || !authReady || apiUnavailable, busy: job?.state === 'submitting' }} accessibilityHint={apiUnavailable ? tx('서버 연결을 확인하고 있어요. 잠시 후 다시 시도해 주세요.', 'Checking the server connection. Please try again shortly.') : hardUnknown ? tx('미확인 제약 조건을 먼저 확인해 주세요.', 'Please review the unanswered constraints first.') : user ? tx('일정 생성을 요청합니다.', 'Requests itinerary generation.') : tx('로그인 후 입력한 조건으로 일정 생성을 계속합니다.', 'Sign in to continue generating your itinerary with these choices.')} label={!authReady ? tx('로그인 상태 확인 중…', 'Checking sign-in status…') : job?.state === 'submitting' ? tx('요청 중…', 'Requesting…') : user ? tx('이 조건으로 일정 만들기', 'Create itinerary with these choices') : tx('로그인하고 일정 만들기', 'Sign in and create itinerary')} disabled={!basicComplete || hardUnknown || !authReady || apiUnavailable || job?.state === 'submitting'} containerStyle={styles.cta} onPress={async () => {
      if (!user) { router.push({ pathname: '/sign-in', params: { returnTo: '/plan/confirm' } }); return; }
      setJob({ state: 'submitting', jobId: null, progress: null, stage: null, canCancel: false, errorMessage: null, resultRef: null });
      const next = await createRecommendationJobAdapter(accessToken).submit(draft);
      setJob(next);
      if (next.jobId) router.push({ pathname: '/plan/generating', params: { jobId: next.jobId } });
    }} />
    <ConditionsPromptModal visible={conditionsOpen} onClose={() => setConditionsOpen(false)} />
  </Screen>;
}
const styles = StyleSheet.create({ canvas: { backgroundColor: color.brand.ivory, maxWidth: 1200 }, top: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, logo: { width: 88, height: 28 }, pill: { borderRadius: radius.full, backgroundColor: color.brand.navy, paddingHorizontal: spacing[3], paddingVertical: spacing[1] }, title: { marginTop: spacing[4] }, subtitle: { marginTop: spacing[1], marginBottom: spacing[4] }, grid: { gap: spacing[4] }, gridWide: { flexDirection: 'row', flexWrap: 'wrap' }, card: { minWidth: '48%', flexGrow: 1, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border }, alertCard: { borderColor: color.state.danger, backgroundColor: color.state.dangerBg }, cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }, cardTitle: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, dot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.brand.orange }, edit: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' }, row: { gap: spacing[1], paddingBottom: spacing[2], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border }, value: { flexShrink: 1 }, warning: { minHeight: 44, justifyContent: 'center', padding: spacing[2], borderRadius: radius.sm, backgroundColor: color.state.dangerBg }, conflict: { marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg }, notice: { marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.subtle }, nextSteps: { marginTop: spacing[3], gap: spacing[1], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card }, unavailable: { marginTop: spacing[3], gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg }, generateNotice: { color: color.text.eyebrow, textAlign: 'center' }, cta: { minHeight: 54, marginTop: spacing[3], backgroundColor: color.brand.navy } });
