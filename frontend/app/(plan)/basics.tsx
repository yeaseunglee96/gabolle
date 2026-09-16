import { useRef, useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Button } from '@/components/Button';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { DateFieldInput } from '@/components/DateFieldInput';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { RouteMap } from '@/map/RouteMap';
import { maskTimeInput } from '@/plan/inputMasks';
import { PlanStepHeader } from '@/plan/PlanStepHeader';
import { PlanDesktopShell } from '@/plan/PlanDesktopShell';
import { MAJOR_BUSAN_ORIGINS, searchOrigins, type OriginCandidate } from '@/plan/origins';
import { useAuth } from '@/auth/AuthProvider';
import { useI18n } from '@/i18n';
import { type PlanDraft, type Transport, usePlan } from '@/plan/PlanProvider';
import { addDays, BUDGET_UNIT_KRW, formatBudgetEn, formatBudgetKo, localToday, validateTripBasics, type TripBasicsErrors } from '@/plan/tripBasics';

const ORIGIN_SEARCH_DEBOUNCE_MS = 300;
const ORIGIN_MIN_QUERY_LENGTH = 2;

const AREAS = [['HAEUNDAE', '해운대', 'Haeundae'], ['GWANGALLI', '광안리', 'Gwangalli'], ['NAMPO', '남포동', 'Nampo-dong'], ['SEOMYEON', '서면', 'Seomyeon'], ['YEONGDO', '영도', 'Yeongdo'], ['SONGJEONG', '송정', 'Songjeong']] as const;
// 🔴 예산은 **쌓는다.** 한 칸을 누르면 그만큼 더해지고, 같은 칸을 또 누르면 또 더해진다
// (5 → 5만, 5 를 한 번 더 → 10만. 55만이 아니다). 여행 예산은 만 원 단위로 말하는 값이라
// 원 단위로 0 을 네 개 치게 하는 것보다 이 편이 빠르고, 0 을 하나 빠뜨릴 일도 없다.
// 마지막 10 은 30만·50만 같은 큰 금액을 몇 번 만에 만들기 위한 큰 칸이다.
const BUDGET_STEPS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] as const;
/** 영어에는 "만" 자리가 없다. 키에 ₩50,000 을 그대로 넣으면 칸을 넘치므로 천 단위(K)로 줄여 쓴다. */
const enStep = (units: number) => `₩${units * 10}K`;

// 모바일에서 7개 카드를 한 화면에 몰아두면 설문이 길어 보인다 (taste·constraints 는
// 이미 화면당 1~2문항으로 쪼개져 있는데 여기만 그대로였다). 같은 패턴으로 4묶음
// 진행형 UI를 적용한다. 각 묶음이 어떤 필드의 에러를 gating 하는지 여기서 정한다.
const PANEL_LABELS = [['날짜', 'Dates'], ['인원·지역', 'People · Areas'], ['예산', 'Budget'], ['출발·이동', 'Start · Transport']] as const;
const PANEL_ERROR_KEYS: readonly (keyof TripBasicsErrors)[][] = [
  ['startDate', 'endDate'],
  ['travelers', 'adults', 'children'],
  ['budgetKrw'],
  ['origin', 'dayStartTime', 'dayEndTime', 'transport'],
];

function Card({ title, hint, children }: { title: string; hint?: string; children: React.ReactNode }) {
  return <View style={styles.card}><Text variant="title" weight="bold">{title}</Text>{hint && <Text variant="caption" color={color.text.muted}>{hint}</Text>}{children}</View>;
}
function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return <View style={styles.field}><Text variant="caption" weight="bold">{label}</Text>{children}{error && <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{error}</Text>}</View>;
}
function Stepper({ label, hint, value, desktop, onChange }: { label: string; hint: string; value: number; desktop?: boolean; onChange: (value: number) => void }) {
  const { tx } = useI18n();
  return <View style={styles.stepper}><View><Text weight="bold">{label}</Text><Text variant="caption" color={color.text.muted}>{hint}</Text></View><View style={styles.stepperControls}><Pressable accessibilityRole="button" accessibilityLabel={tx(`${label} 줄이기`, `Decrease ${label}`)} accessibilityState={{ disabled: value === 0 }} disabled={value === 0} onPress={() => onChange(Math.max(0, value - 1))} style={[styles.roundButton, desktop && styles.roundButtonDesktop, value === 0 && styles.disabled]}><Text variant="title">−</Text></Pressable><Text accessibilityLabel={tx(`${label} ${value}명`, `${label} ${value}`)} weight="bold">{value}</Text><Pressable accessibilityRole="button" accessibilityLabel={tx(`${label} 늘리기`, `Increase ${label}`)} onPress={() => onChange(value + 1)} style={[styles.roundButton, desktop && styles.roundButtonPlusDesktop]}><Text variant="title" color={desktop ? color.text.onAction : color.text.heading}>+</Text></Pressable></View></View>;
}

export default function Basics() {
  const { tx } = useI18n();
  const router = useRouter();
  const { kind, width } = useLayout();
  const isDesktop = kind === 'tablet' && isAtLeast(width, 'lg');
  const { draft, ready, update, completeStep } = usePlan();
  const { accessToken } = useAuth();
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [panelIndex, setPanelIndex] = useState(0);
  const [originResults, setOriginResults] = useState<OriginCandidate[]>([]);
  const [originSearching, setOriginSearching] = useState(false);
  const [originSearched, setOriginSearched] = useState(false);
  const originDebounce = useRef<ReturnType<typeof setTimeout> | null>(null);
  const originAbort = useRef<AbortController | null>(null);
  const touch = (field: string) => setTouched((current) => ({ ...current, [field]: true }));
  const errors = validateTripBasics(draft);
  const valid = ready && Object.keys(errors).length === 0;
  const nights = /^\d{4}-\d{2}-\d{2}$/.test(draft.startDate) && /^\d{4}-\d{2}-\d{2}$/.test(draft.endDate) ? Math.max(0, Math.round((Date.parse(`${draft.endDate}T00:00:00Z`) - Date.parse(`${draft.startDate}T00:00:00Z`)) / 86400000)) : null;
  const set = <K extends keyof PlanDraft>(key: K, value: PlanDraft[K]) => update({ [key]: value });
  const setPeople = (key: 'adults' | 'children', value: number) => update({ [key]: value, travelers: value + draft[key === 'adults' ? 'children' : 'adults'] });
  // 되돌리기는 **누르기 전 상태를 통째로 쌓아두는 방식**이다. 금액만 빼면 "전체 지우기" 를
  // 무를 수 없는데, 한 번 누르면 값이 통째로 사라지는 버튼에 되돌리기가 없는 것이 제일 나쁘다.
  const [budgetUndo, setBudgetUndo] = useState<{ krw: number | null; added: number | null; trail: number[] }[]>([]);
  const [budgetAdded, setBudgetAdded] = useState<number | null>(null);
  const [budgetTrail, setBudgetTrail] = useState<number[]>([]);
  const pushBudgetUndo = () => setBudgetUndo((stack) => [...stack, { krw: draft.budgetKrw, added: budgetAdded, trail: budgetTrail }]);
  const addBudget = (units: number) => { pushBudgetUndo(); update({ budgetKrw: (draft.budgetKrw ?? 0) + units * BUDGET_UNIT_KRW }); setBudgetAdded(units); setBudgetTrail([...budgetTrail, units]); };
  const clearBudget = () => { pushBudgetUndo(); update({ budgetKrw: null }); setBudgetAdded(null); setBudgetTrail([]); };
  const undoBudget = () => { const previous = budgetUndo[budgetUndo.length - 1]; if (!previous) return; setBudgetUndo((stack) => stack.slice(0, -1)); update({ budgetKrw: previous.krw }); setBudgetAdded(previous.added); setBudgetTrail(previous.trail); };
  // 이 화면에 들어오기 전부터 들고 있던 금액. 쌓아온 순서를 "10만 원 +5만 +5만" 으로 보여줄 때 맨 앞에 온다.
  const budgetTrailBase = (draft.budgetKrw ?? 0) - budgetTrail.reduce((sum, units) => sum + units, 0) * BUDGET_UNIT_KRW;
  const today = localToday();
  const endMinimum = /^\d{4}-\d{2}-\d{2}$/.test(draft.startDate) ? draft.startDate : today;
  const endMaximum = addDays(endMinimum, 7);
  const toggleArea = (code: string) => set('travelAreas', draft.travelAreas.includes(code) ? draft.travelAreas.filter((item) => item !== code) : [...draft.travelAreas, code]);

  // 완료 기준: 글자를 이어 쳐도 요청이 글자 수만큼 나가지 않는다 — 300ms 안에 또 치면 이전
  // 타이머를 지우고 새로 잰다. 응답이 늦게 온 이전 요청이 최신 결과를 덮어쓰지 않도록 abort 도 같이 한다.
  const performOriginSearch = async (query: string) => {
    originAbort.current?.abort();
    const controller = new AbortController();
    originAbort.current = controller;
    setOriginSearching(true);
    const result = await searchOrigins(query, accessToken, controller.signal);
    if (controller.signal.aborted) return;
    setOriginSearching(false);
    setOriginSearched(true);
    setOriginResults(result.state === 'success' ? result.items : []);
  };
  const handleOriginChange = (value: string) => {
    update({ origin: value, originLat: null, originLng: null });
    if (originDebounce.current) clearTimeout(originDebounce.current);
    const trimmed = value.trim();
    if (trimmed.length < ORIGIN_MIN_QUERY_LENGTH) {
      originAbort.current?.abort();
      setOriginSearching(false); setOriginSearched(false); setOriginResults([]);
      return;
    }
    originDebounce.current = setTimeout(() => void performOriginSearch(trimmed), ORIGIN_SEARCH_DEBOUNCE_MS);
  };
  const selectOrigin = (candidate: OriginCandidate) => {
    if (originDebounce.current) clearTimeout(originDebounce.current);
    originAbort.current?.abort();
    update({ origin: candidate.name, originLat: candidate.lat, originLng: candidate.lng });
    setOriginResults([]); setOriginSearched(false); setOriginSearching(false);
    touch('origin');
  };
  const reason = !ready ? tx('저장된 정보를 불러오고 있어요.', 'Loading your saved details.') : !valid ? tx('빨간 안내가 표시된 항목을 확인해 주세요.', 'Check the fields highlighted in red.') : null;

  const panelBlocked = (index: number) => PANEL_ERROR_KEYS[index].some((key) => errors[key]);
  const goToPanel = (index: number) => setPanelIndex(Math.max(0, Math.min(PANEL_LABELS.length - 1, index)));
  const goNext = () => {
    if (panelBlocked(panelIndex)) {
      if (panelIndex === 0) { touch('startDate'); touch('endDate'); }
      if (panelIndex === 3) touch('origin');
      return;
    }
    goToPanel(panelIndex + 1);
  };

  return <PlanDesktopShell>
    <Screen scroll wide style={isDesktop ? styles.desktopCanvas : styles.canvas}>
    {kind === 'phone' && <View style={styles.topBar}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('홈으로 돌아가기', 'Back to home')} onPress={() => router.replace('/home')} style={styles.back}><Text variant="title">‹</Text></Pressable>
      <BrandLogoLink href="/home" imageStyle={styles.logo} />
      <Text variant="caption" weight="bold" color={color.text.eyebrow}>1 / 4</Text>
    </View>}
    <PlanStepHeader current={1} />
    <Text variant="display" weight="bold" style={styles.title}>{tx('여행 기본 정보', 'Trip basics')}</Text>
    <Text color={color.text.body} style={styles.subtitle}>{tx('부산 여행의 기본을 알려주세요', 'Tell us the essentials for your Busan trip')}</Text>

    {kind === 'phone' && <View style={styles.questionProgress}>
      <View style={styles.questionMeta}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('기본정보', 'Basics')} {panelIndex + 1} / {PANEL_LABELS.length}</Text></View>
      <View style={styles.questionDots}>{PANEL_LABELS.map(([ko, en], index) => <Pressable key={ko} accessibilityRole="button" accessibilityLabel={tx(`${ko} 항목으로 이동`, `Go to ${en}`)} onPress={() => goToPanel(index)} style={[styles.questionDot, index === panelIndex && styles.questionDotCurrent, !panelBlocked(index) && styles.questionDotAnswered]} />)}</View>
    </View>}

    <View style={[styles.grid, kind === 'tablet' && styles.gridWide]}>
      {(kind === 'tablet' || panelIndex === 0) && <Card title={tx('여행 날짜', 'Travel dates')} hint={tx('오늘 이후, 최대 7박까지 선택할 수 있어요.', 'Choose up to 7 nights starting today or later.')}>
        <View style={[styles.dateRow, kind === 'phone' && styles.dateRowPhone]}>
          <Field label={tx('출발', 'Start')} error={touched.startDate || draft.startDate ? errors.startDate : undefined}><DateFieldInput label={tx('여행 시작일', 'Trip start date')} value={draft.startDate} minimumDate={today} invalid={Boolean((touched.startDate || draft.startDate) && errors.startDate)} onBlur={() => touch('startDate')} onChange={(value) => set('startDate', value)} /></Field>
          {kind === 'tablet' && <Text style={styles.dateArrow}>→</Text>}
          <Field label={tx('귀환', 'Return')} error={touched.endDate || draft.endDate ? errors.endDate : undefined}><DateFieldInput label={tx('여행 종료일', 'Trip end date')} value={draft.endDate} minimumDate={endMinimum} maximumDate={endMaximum} invalid={Boolean((touched.endDate || draft.endDate) && errors.endDate)} onBlur={() => touch('endDate')} onChange={(value) => set('endDate', value)} /></Field>
        </View>
        {nights !== null && !errors.startDate && !errors.endDate && <View style={styles.badge}><Text variant="caption" weight="bold" color={isDesktop ? color.text.muted : color.text.eyebrow}>{tx(`${nights}박 ${nights + 1}일`, `${nights + 1} days, ${nights} nights`)}</Text></View>}
      </Card>}

      {(kind === 'tablet' || panelIndex === 1) && <>
      <Card title={tx('여행 인원', 'Travelers')} hint={tx('성인과 어린이를 합해 최소 1명이 필요해요.', 'Add at least one adult or child.')}>
        <Stepper label={tx('성인', 'Adults')} hint={tx('만 13세 이상', 'Age 13+')} value={draft.adults} desktop={isDesktop} onChange={(value) => setPeople('adults', value)} />
        <Stepper label={tx('어린이', 'Children')} hint={tx('만 2~12세', 'Ages 2–12')} value={draft.children} desktop={isDesktop} onChange={(value) => setPeople('children', value)} />
        {(errors.travelers ?? errors.adults ?? errors.children) && <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{errors.travelers ?? errors.adults ?? errors.children}</Text>}
      </Card>

      <Card title={tx('여행 범위', 'Areas to visit')} hint={tx('가고 싶은 지역을 여러 개 선택해도 좋아요.', 'Choose as many areas as you like.')}>
        <View accessibilityLabel={tx('여행 범위 복수 선택', 'Select one or more areas')} style={styles.chips}>{AREAS.map(([code, ko, en]) => { const selected = draft.travelAreas.includes(code); return <Pressable key={code} accessibilityRole="checkbox" accessibilityState={{ checked: selected }} onPress={() => toggleArea(code)} style={[styles.chip, isDesktop && styles.chipDesktop, selected && styles.chipSelected, selected && isDesktop && styles.chipSelectedDesktop]}><Text weight="bold" color={selected ? (isDesktop ? color.brand.orange : color.text.onAction) : color.text.heading}>{tx(ko, en)}</Text></Pressable>; })}</View>
      </Card>
      </>}

      {(kind === 'tablet' || panelIndex === 2) && <Card title={tx('총예산', 'Total budget')} hint={tx('전체 여행 기간에 쓸 돈이에요. 숙박비는 빼고 생각해 주세요.', 'What you plan to spend across the whole trip, excluding lodging.')}>
        <View style={[styles.budgetTotal, isDesktop && styles.budgetTotalDesktop]}>
          <View style={styles.budgetTotalHead}>
            <Text variant="caption" weight="bold" color={color.text.muted}>{tx('지금 예산', 'Current budget')}</Text>
            {budgetAdded !== null && <View style={styles.budgetJustAdded}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx(`방금 +${budgetAdded}만`, `Just +${enStep(budgetAdded)}`)}</Text></View>}
          </View>
          <Text accessibilityLiveRegion="polite" variant="hero" weight="bold" color={color.text.heading}>{draft.budgetKrw === null ? tx('0원', '₩0') : tx(formatBudgetKo(draft.budgetKrw), formatBudgetEn(draft.budgetKrw))}</Text>
          <Text variant="caption" color={color.text.muted}>{draft.budgetKrw === null ? tx('아직 정하지 않았어요.', 'Not set yet.') : tx(`${draft.budgetKrw.toLocaleString('ko-KR')}원`, `${(draft.budgetKrw / BUDGET_UNIT_KRW).toLocaleString('en-US')} × ₩10,000`)}</Text>
          {budgetTrail.length > 0 && <Text variant="caption" color={color.text.body}>{[...(budgetTrailBase > 0 ? [tx(formatBudgetKo(budgetTrailBase), formatBudgetEn(budgetTrailBase))] : []), ...budgetTrail.map((units) => tx(`+${units}만`, `+${enStep(units)}`))].join(' ')}</Text>}
        </View>

        <Text variant="caption" color={color.text.body}>{tx('숫자를 누를 때마다 그만큼 더해져요 — 5 를 누르면 5만 원, 한 번 더 누르면 10만 원.', 'Each tap adds that amount — tap 5 for ₩50,000, tap it again for ₩100,000.')}</Text>
        <View accessibilityLabel={tx('예산 더하기 키패드', 'Budget add keypad')} style={styles.budgetKeys}>
          {BUDGET_STEPS.map((units) => <Pressable key={units} accessibilityRole="button" accessibilityLabel={tx(`${units}만원 더하기`, `Add ${formatBudgetEn(units * BUDGET_UNIT_KRW)}`)} onPress={() => addBudget(units)} style={[styles.budgetKey, isDesktop && styles.budgetKeyDesktop]}><Text variant="title" weight="bold" color={color.text.heading}>{tx(`+${units}만`, `+${enStep(units)}`)}</Text></Pressable>)}
        </View>

        <View style={styles.budgetUndoRow}>
          <Pressable accessibilityRole="button" accessibilityState={{ disabled: budgetUndo.length === 0 }} disabled={budgetUndo.length === 0} accessibilityLabel={tx('마지막으로 누른 것 되돌리기', 'Undo the last tap')} onPress={undoBudget} style={[styles.budgetUndo, budgetUndo.length === 0 && styles.disabled]}><Text variant="caption" weight="bold" color={color.text.heading}>{budgetUndo.length === 0 ? tx('↩ 되돌리기', '↩ Undo') : budgetAdded === null ? tx('↩ 지운 것 되살리기', '↩ Undo clear') : tx(`↩ +${budgetAdded}만 취소`, `↩ Undo +${enStep(budgetAdded)}`)}</Text></Pressable>
          <Pressable accessibilityRole="button" accessibilityState={{ disabled: draft.budgetKrw === null }} disabled={draft.budgetKrw === null} accessibilityLabel={tx('예산 전체 지우기', 'Clear the whole budget')} onPress={clearBudget} style={[styles.budgetClear, draft.budgetKrw === null && styles.disabled]}><Text variant="caption" weight="bold" color={color.text.heading}>{tx('전체 지우기', 'Clear all')}</Text></Pressable>
        </View>
        {errors.budgetKrw && <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{errors.budgetKrw}</Text>}
      </Card>}

      {(kind === 'tablet' || panelIndex === 3) && <>
      <Card title={tx('출발지', 'Starting point')} hint={tx('숙소나 역 이름처럼 알아보기 쉽게 입력해 주세요.', 'Enter a recognizable place such as a hotel or station.')}>
        <Field label={tx('장소', 'Place')} error={touched.origin || draft.origin ? errors.origin : undefined}>
          <View style={[styles.inputRow, isDesktop && styles.inputDesktop, Boolean((touched.origin || draft.origin) && errors.origin) && styles.invalid]}>
            <TextInput accessibilityLabel={tx('출발지', 'Starting point')} value={draft.origin} onBlur={() => touch('origin')} onChangeText={handleOriginChange} placeholder={tx('예: 부산역', 'e.g. Busan Station')} placeholderTextColor={color.text.muted} style={styles.inputWithClear} />
            {draft.origin.length > 0 && <Pressable accessibilityRole="button" accessibilityLabel={tx('출발지 지우기', 'Clear starting point')} onPress={() => handleOriginChange('')} style={styles.clear}><Text variant="body" color={color.text.muted}>✕</Text></Pressable>}
          </View>
        </Field>
        {originSearching && <Text accessibilityLiveRegion="polite" variant="caption" color={color.text.muted} style={styles.originStatus}>{tx('검색 중…', 'Searching…')}</Text>}
        {originResults.length > 0 && <View accessibilityRole="list" style={styles.originList}>
          {originResults.map((item) => <Pressable key={item.externalId} accessibilityRole="button" accessibilityLabel={tx(`출발지로 ${item.name} 선택`, `Choose ${item.name} as the starting point`)} onPress={() => selectOrigin(item)} style={({ pressed }) => [styles.originItem, pressed && styles.originItemPressed]}>
            <Text weight="bold">{item.name}</Text>
            <Text variant="caption" color={color.text.muted}>{item.address}</Text>
          </Pressable>)}
        </View>}
        {!originSearching && originSearched && originResults.length === 0 && <View accessibilityRole="list" style={styles.originList}>
          <Text accessibilityLiveRegion="polite" variant="caption" color={color.text.muted} style={styles.originStatus}>{tx('검색 결과가 없습니다. 주요 출발지 중에서 골라 보세요.', 'No results. Try one of these major starting points.')}</Text>
          {MAJOR_BUSAN_ORIGINS.map((item) => <Pressable key={item.externalId} accessibilityRole="button" accessibilityLabel={tx(`출발지로 ${item.name} 선택`, `Choose ${item.name} as the starting point`)} onPress={() => selectOrigin(item)} style={({ pressed }) => [styles.originItem, pressed && styles.originItemPressed]}>
            <Text weight="bold">{item.name}</Text>
            <Text variant="caption" color={color.text.muted}>{item.address}</Text>
          </Pressable>)}
        </View>}
        {draft.originLat !== null && draft.originLng !== null && <View style={styles.originMapWrap}>
          <RouteMap stops={[{ id: 'origin-preview', number: 1, name: draft.origin, latitude: draft.originLat, longitude: draft.originLng }]} selectedId="origin-preview" onSelect={() => {}} height={160} />
        </View>}
      </Card>

      <Card title={tx('하루 여행 시간', 'Daily hours')} hint={tx('24시간제로 숫자만 입력하세요. 쌍점은 저절로 들어갑니다.', 'Type digits only in 24-hour time — the colon is added for you.')}><View style={[styles.dateRow, kind === 'phone' && styles.dateRowPhone]}><Field label={tx('시작', 'Start')} error={errors.dayStartTime}><TextInput accessibilityLabel={tx('매일 여행 시작 시각', 'Daily start time')} value={draft.dayStartTime} onChangeText={(value) => set('dayStartTime', maskTimeInput(value))} keyboardType="number-pad" maxLength={5} placeholder="09:00" placeholderTextColor={color.text.muted} style={[styles.input, errors.dayStartTime && styles.invalid]} /></Field>{kind === 'tablet' && <Text style={styles.dateArrow}>→</Text>}<Field label={tx('종료', 'End')} error={errors.dayEndTime}><TextInput accessibilityLabel={tx('매일 여행 종료 시각', 'Daily end time')} value={draft.dayEndTime} onChangeText={(value) => set('dayEndTime', maskTimeInput(value))} keyboardType="number-pad" maxLength={5} placeholder="18:00" placeholderTextColor={color.text.muted} style={[styles.input, errors.dayEndTime && styles.invalid]} /></Field></View></Card>

      <Card title={tx('주요 이동수단', 'Main transport')}><View accessibilityRole="radiogroup" accessibilityLabel={tx('이동수단 선택', 'Select transport')} style={styles.transport}>{([{ code: 'CAR', ko: '자차', en: 'Car' }, { code: 'TRANSIT', ko: '대중교통', en: 'Public transit' }, { code: 'WALK', ko: '도보 위주', en: 'Mostly walking' }] as { code: Transport; ko: string; en: string }[]).map((item) => { const selected = draft.transport === item.code; return <Pressable key={item.code} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => set('transport', item.code)} style={[styles.transportButton, isDesktop && styles.chipDesktop, selected && styles.chipSelected, selected && isDesktop && styles.chipSelectedDesktop]}><Text weight="bold" color={selected ? (isDesktop ? color.brand.orange : color.text.onAction) : color.text.heading}>{tx(item.ko, item.en)}</Text></Pressable>; })}</View>{errors.transport && <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{errors.transport}</Text>}</Card>
      </>}
    </View>

    {kind === 'phone' && panelIndex < PANEL_LABELS.length - 1 && <View style={styles.panelNav}>
      <Pressable accessibilityRole="button" accessibilityState={{ disabled: panelIndex === 0 }} disabled={panelIndex === 0} onPress={() => goToPanel(panelIndex - 1)} style={[styles.panelNavButton, panelIndex === 0 && styles.disabled]}><Text variant="caption" weight="bold">{tx('이전', 'Back')}</Text></Pressable>
      <Button accessibilityRole="button" label={tx('다음', 'Next')} containerStyle={styles.panelCta} onPress={goNext} />
    </View>}

    {(kind === 'tablet' || panelIndex === PANEL_LABELS.length - 1) && <>
      {kind === 'phone' && <View style={styles.panelNav}><Pressable accessibilityRole="button" onPress={() => goToPanel(panelIndex - 1)} style={styles.panelNavButton}><Text variant="caption" weight="bold">{tx('이전', 'Back')}</Text></Pressable></View>}
      {reason && <Text accessibilityRole="alert" variant="caption" style={styles.reason}>{reason}</Text>}
      <Button accessibilityRole="button" accessibilityState={{ disabled: !valid }} accessibilityHint={reason ?? tx('취향 선택 단계로 이동합니다.', 'Continue to travel preferences.')} label={ready ? tx('다음', 'Continue') : tx('불러오는 중…', 'Loading…')} disabled={!valid} containerStyle={styles.cta} onPress={() => { completeStep(1); router.push('/plan/taste'); }} />
    </>}
    </Screen>
  </PlanDesktopShell>;
}

const styles = StyleSheet.create({
  canvas: { backgroundColor: color.brand.ivory }, desktopCanvas: { paddingTop: spacing[8], paddingHorizontal: spacing[8], maxWidth: 960, backgroundColor: color.brand.ivory }, topBar: { minHeight: 44, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, logo: { width: 88, height: 28 }, title: { marginTop: spacing[3] }, subtitle: { marginTop: spacing[1], marginBottom: spacing[3] },
  questionProgress: { gap: spacing[2], marginBottom: spacing[3] }, questionMeta: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, questionDots: { flexDirection: 'row', gap: spacing[2] }, questionDot: { flex: 1, height: 4, borderRadius: radius.full, backgroundColor: color.surface.field }, questionDotCurrent: { backgroundColor: color.brand.orange }, questionDotAnswered: { opacity: 0.72, backgroundColor: color.brand.orange },
  grid: { gap: spacing[4] }, gridWide: { flexDirection: 'row', flexWrap: 'wrap' }, card: { minWidth: '48%', flexGrow: 1, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border, shadowColor: color.brand.navy, shadowOpacity: .06, shadowRadius: 12, shadowOffset: { width: 0, height: 4 }, elevation: 2 }, field: { flex: 1, gap: spacing[2] }, input: { minHeight: 48, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[3] }, inputDesktop: { borderColor: color.surface.border, backgroundColor: color.surface.subtle }, invalid: { borderColor: color.state.danger },
  inputRow: { minHeight: 48, flexDirection: 'row', alignItems: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card }, inputWithClear: { flex: 1, minWidth: 0, minHeight: 48, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[3] }, clear: { minWidth: 36, minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  dateRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, dateRowPhone: { flexDirection: 'column', alignItems: 'stretch' }, dateArrow: { marginTop: spacing[6], color: color.text.muted }, badge: { alignSelf: 'center', paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.subtle }, stepper: { minHeight: 58, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, stepperControls: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, roundButton: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' }, roundButtonDesktop: { backgroundColor: color.surface.subtle }, roundButtonPlusDesktop: { backgroundColor: color.brand.navy }, disabled: { opacity: .4 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, chip: { minHeight: 44, paddingHorizontal: spacing[4], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, chipDesktop: { borderColor: color.surface.border, backgroundColor: color.surface.subtle }, chipSelected: { backgroundColor: color.action.primary, borderColor: color.action.primary }, chipSelectedDesktop: { backgroundColor: color.surface.warm, borderColor: color.brand.orange }, transport: { flexDirection: 'row', gap: spacing[2] }, transportButton: { flex: 1, minHeight: 48, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  budgetTotal: { gap: spacing[1], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.tint }, budgetTotalDesktop: { backgroundColor: color.surface.subtle }, budgetTotalHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] }, budgetJustAdded: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.brand.orange },
  budgetKeys: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, budgetKey: { flexGrow: 1, flexBasis: '30%', minHeight: 52, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, budgetKeyDesktop: { borderColor: color.surface.border, backgroundColor: color.surface.subtle },
  budgetUndoRow: { flexDirection: 'row', gap: spacing[2] }, budgetUndo: { flex: 2, minHeight: 48, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.orange, backgroundColor: color.surface.warm, alignItems: 'center', justifyContent: 'center' }, budgetClear: { flex: 1, minHeight: 48, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  panelNav: { marginTop: spacing[4], flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, panelNavButton: { minWidth: 72, minHeight: 48, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, panelCta: { flex: 1, marginTop: 0, backgroundColor: color.brand.navy },
  reason: { marginTop: spacing[6], textAlign: 'center', color: color.text.body }, cta: { marginTop: spacing[3], backgroundColor: color.brand.navy },
  originStatus: { paddingHorizontal: spacing[1] },
  originList: { gap: spacing[1], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, padding: spacing[2] },
  originItem: { minHeight: 48, justifyContent: 'center', gap: 2, borderRadius: radius.sm, paddingHorizontal: spacing[2], paddingVertical: spacing[1] },
  originItemPressed: { backgroundColor: color.surface.tint },
  originMapWrap: { borderRadius: radius.md, overflow: 'hidden' },
});
