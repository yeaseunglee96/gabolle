import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Button } from '@/components/Button';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { DateFieldInput } from '@/components/DateFieldInput';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { PlanStepHeader } from '@/plan/PlanStepHeader';
import { PlanDesktopShell } from '@/plan/PlanDesktopShell';
import { useI18n } from '@/i18n';
import { type PlanDraft, type Transport, usePlan } from '@/plan/PlanProvider';
import { addDays, localToday, validateTripBasics } from '@/plan/tripBasics';

const AREAS = [['HAEUNDAE', '해운대'], ['GWANGALLI', '광안리'], ['NAMPO', '남포동'], ['SEOMYEON', '서면'], ['YEONGDO', '영도'], ['SONGJEONG', '송정']] as const;
const BUDGETS = [50000, 100000, 150000, 200000] as const;

function Card({ title, hint, children }: { title: string; hint?: string; children: React.ReactNode }) {
  return <View style={styles.card}><Text variant="title" weight="bold">{title}</Text>{hint && <Text variant="caption" color={color.text.muted}>{hint}</Text>}{children}</View>;
}
function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return <View style={styles.field}><Text variant="caption" weight="bold">{label}</Text>{children}{error && <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{error}</Text>}</View>;
}
function Stepper({ label, hint, value, desktop, onChange }: { label: string; hint: string; value: number; desktop?: boolean; onChange: (value: number) => void }) {
  return <View style={styles.stepper}><View><Text weight="bold">{label}</Text><Text variant="caption" color={color.text.muted}>{hint}</Text></View><View style={styles.stepperControls}><Pressable accessibilityRole="button" accessibilityLabel={`${label} 줄이기`} accessibilityState={{ disabled: value === 0 }} disabled={value === 0} onPress={() => onChange(Math.max(0, value - 1))} style={[styles.roundButton, desktop && styles.roundButtonDesktop, value === 0 && styles.disabled]}><Text variant="title">−</Text></Pressable><Text accessibilityLabel={`${label} ${value}명`} weight="bold">{value}</Text><Pressable accessibilityRole="button" accessibilityLabel={`${label} 늘리기`} onPress={() => onChange(value + 1)} style={[styles.roundButton, desktop && styles.roundButtonPlusDesktop]}><Text variant="title" color={desktop ? color.text.onAction : color.text.heading}>+</Text></Pressable></View></View>;
}

export default function Basics() {
  const { tx } = useI18n();
  const router = useRouter();
  const { kind, width } = useLayout();
  const isDesktop = kind === 'tablet' && width >= 1100;
  const { draft, ready, update, completeStep } = usePlan();
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const touch = (field: string) => setTouched((current) => ({ ...current, [field]: true }));
  const errors = validateTripBasics(draft);
  const valid = ready && Object.keys(errors).length === 0;
  const nights = /^\d{4}-\d{2}-\d{2}$/.test(draft.startDate) && /^\d{4}-\d{2}-\d{2}$/.test(draft.endDate) ? Math.max(0, Math.round((Date.parse(`${draft.endDate}T00:00:00Z`) - Date.parse(`${draft.startDate}T00:00:00Z`)) / 86400000)) : null;
  const set = <K extends keyof PlanDraft>(key: K, value: PlanDraft[K]) => update({ [key]: value });
  const setPeople = (key: 'adults' | 'children', value: number) => update({ [key]: value, travelers: value + draft[key === 'adults' ? 'children' : 'adults'] });
  const selectBudget = (amount: number | null) => update({ budgetKrw: amount });
  const today = localToday();
  const endMinimum = /^\d{4}-\d{2}-\d{2}$/.test(draft.startDate) ? draft.startDate : today;
  const endMaximum = addDays(endMinimum, 7);
  const toggleArea = (code: string) => set('travelAreas', draft.travelAreas.includes(code) ? draft.travelAreas.filter((item) => item !== code) : [...draft.travelAreas, code]);
  const reason = !ready ? tx('저장된 정보를 불러오고 있어요.', 'Loading your saved details.') : !valid ? tx('빨간 안내가 표시된 항목을 확인해 주세요.', 'Check the fields highlighted in red.') : null;

  return <PlanDesktopShell>
    <Screen scroll wide style={isDesktop ? styles.desktopCanvas : styles.canvas}>
    {kind === 'phone' && <View style={styles.topBar}>
      <Pressable accessibilityRole="button" accessibilityLabel="홈으로 돌아가기" onPress={() => router.replace('/home')} style={styles.back}><Text variant="title">‹</Text></Pressable>
      <BrandLogoLink href="/home" imageStyle={styles.logo} />
      <Text variant="caption" weight="bold" color={color.text.eyebrow}>1 / 4</Text>
    </View>}
    <PlanStepHeader current={1} />
    <Text variant="display" weight="bold" style={styles.title}>{tx('여행 기본 정보', 'Trip basics')}</Text>
    <Text color={color.text.body} style={styles.subtitle}>{tx('부산 여행의 기본을 알려주세요', 'Tell us the essentials for your Busan trip')}</Text>

    <View style={[styles.grid, kind === 'tablet' && styles.gridWide]}>
      <Card title={tx('여행 날짜', 'Travel dates')} hint={tx('오늘 이후, 최대 7박까지 선택할 수 있어요.', 'Choose up to 7 nights starting today or later.')}>
        <View style={[styles.dateRow, kind === 'phone' && styles.dateRowPhone]}>
          <Field label="출발" error={touched.startDate || draft.startDate ? errors.startDate : undefined}><DateFieldInput label="여행 시작일" value={draft.startDate} minimumDate={today} invalid={Boolean((touched.startDate || draft.startDate) && errors.startDate)} onBlur={() => touch('startDate')} onChange={(value) => set('startDate', value)} /></Field>
          {kind === 'tablet' && <Text style={styles.dateArrow}>→</Text>}
          <Field label="귀환" error={touched.endDate || draft.endDate ? errors.endDate : undefined}><DateFieldInput label="여행 종료일" value={draft.endDate} minimumDate={endMinimum} maximumDate={endMaximum} invalid={Boolean((touched.endDate || draft.endDate) && errors.endDate)} onBlur={() => touch('endDate')} onChange={(value) => set('endDate', value)} /></Field>
        </View>
        {nights !== null && !errors.startDate && !errors.endDate && <View style={styles.badge}><Text variant="caption" weight="bold" color={isDesktop ? color.text.muted : color.text.eyebrow}>{nights}박 {nights + 1}일</Text></View>}
      </Card>

      <Card title={tx('여행 인원', 'Travelers')} hint={tx('성인과 어린이를 합해 최소 1명이 필요해요.', 'Add at least one adult or child.')}>
        <Stepper label={tx('성인', 'Adults')} hint={tx('만 13세 이상', 'Age 13+')} value={draft.adults} desktop={isDesktop} onChange={(value) => setPeople('adults', value)} />
        <Stepper label={tx('어린이', 'Children')} hint={tx('만 2~12세', 'Ages 2–12')} value={draft.children} desktop={isDesktop} onChange={(value) => setPeople('children', value)} />
        {(errors.travelers ?? errors.adults ?? errors.children) && <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{errors.travelers ?? errors.adults ?? errors.children}</Text>}
      </Card>

      <Card title={tx('여행 범위', 'Areas to visit')} hint={tx('가고 싶은 지역을 여러 개 선택해도 좋아요.', 'Choose as many areas as you like.')}>
        <View accessibilityLabel="여행 범위 복수 선택" style={styles.chips}>{AREAS.map(([code, label]) => { const selected = draft.travelAreas.includes(code); return <Pressable key={code} accessibilityRole="checkbox" accessibilityState={{ checked: selected }} onPress={() => toggleArea(code)} style={[styles.chip, isDesktop && styles.chipDesktop, selected && styles.chipSelected, selected && isDesktop && styles.chipSelectedDesktop]}><Text weight="bold" color={selected ? (isDesktop ? color.brand.orange : color.text.onAction) : color.text.heading}>{label}</Text></Pressable>; })}</View>
      </Card>

      <Card title={tx('총예산', 'Total budget')} hint={tx('전체 여행 기간의 숙박비 제외 예산이에요. 10,000원 단위로 선택해 주세요.', 'Budget for the whole trip excluding lodging, in KRW 10,000 increments.')}>
        <View accessibilityRole="radiogroup" accessibilityLabel="총예산 구간" style={styles.chips}>
          {BUDGETS.map((amount) => { const selected = draft.budgetKrw === amount; return <Pressable key={amount} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => selectBudget(amount)} style={[styles.chip, isDesktop && styles.chipDesktop, selected && styles.chipSelected, selected && isDesktop && styles.chipSelectedDesktop]}><Text weight="bold" color={selected ? (isDesktop ? color.brand.orange : color.text.onAction) : color.text.heading}>{amount / 10000}만원</Text></Pressable>; })}
        </View>
        <Field label="직접 입력·10,000원 단위" error={errors.budgetKrw}><View style={styles.moneyRow}><TextInput accessibilityLabel="총예산, 숙박비 제외" keyboardType="number-pad" value={draft.budgetKrw === null ? '' : draft.budgetKrw.toLocaleString('ko-KR')} onChangeText={(value) => { const digits = value.replace(/\D/g, ''); selectBudget(digits ? Number(digits) : null); }} placeholder="100,000" placeholderTextColor={color.text.muted} style={[styles.input, isDesktop && styles.inputDesktop, styles.moneyInput, errors.budgetKrw && styles.invalid]} /><Text>원</Text></View></Field>
      </Card>

      <Card title={tx('출발지', 'Starting point')} hint={tx('숙소나 역 이름처럼 알아보기 쉽게 입력해 주세요.', 'Enter a recognizable place such as a hotel or station.')}><Field label={tx('장소', 'Place')} error={touched.origin || draft.origin ? errors.origin : undefined}><TextInput accessibilityLabel={tx('출발지', 'Starting point')} value={draft.origin} onBlur={() => touch('origin')} onChangeText={(value) => set('origin', value)} placeholder={tx('예: 부산역', 'e.g. Busan Station')} placeholderTextColor={color.text.muted} style={[styles.input, isDesktop && styles.inputDesktop, Boolean((touched.origin || draft.origin) && errors.origin) && styles.invalid]} /></Field></Card>

      <Card title="하루 여행 시간" hint="24시간 HH:MM 형식으로 입력해 주세요."><View style={[styles.dateRow, kind === 'phone' && styles.dateRowPhone]}><Field label="시작" error={errors.dayStartTime}><TextInput accessibilityLabel="매일 여행 시작 시각" value={draft.dayStartTime} onChangeText={(value) => set('dayStartTime', value)} placeholder="09:00" placeholderTextColor={color.text.muted} style={[styles.input, errors.dayStartTime && styles.invalid]} /></Field>{kind === 'tablet' && <Text style={styles.dateArrow}>→</Text>}<Field label="종료" error={errors.dayEndTime}><TextInput accessibilityLabel="매일 여행 종료 시각" value={draft.dayEndTime} onChangeText={(value) => set('dayEndTime', value)} placeholder="18:00" placeholderTextColor={color.text.muted} style={[styles.input, errors.dayEndTime && styles.invalid]} /></Field></View></Card>

      <Card title="주요 이동수단"><View accessibilityRole="radiogroup" accessibilityLabel="이동수단 선택" style={styles.transport}>{([{ code: 'CAR', label: '자차' }, { code: 'TRANSIT', label: '대중교통' }, { code: 'WALK', label: '도보 위주' }] as { code: Transport; label: string }[]).map((item) => { const selected = draft.transport === item.code; return <Pressable key={item.code} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => set('transport', item.code)} style={[styles.transportButton, isDesktop && styles.chipDesktop, selected && styles.chipSelected, selected && isDesktop && styles.chipSelectedDesktop]}><Text weight="bold" color={selected ? (isDesktop ? color.brand.orange : color.text.onAction) : color.text.heading}>{item.label}</Text></Pressable>; })}</View>{errors.transport && <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{errors.transport}</Text>}</Card>
    </View>
    {reason && <Text accessibilityRole="alert" variant="caption" style={styles.reason}>{reason}</Text>}
    <Button accessibilityRole="button" accessibilityState={{ disabled: !valid }} accessibilityHint={reason ?? tx('취향 선택 단계로 이동합니다.', 'Continue to travel preferences.')} label={ready ? tx('다음', 'Continue') : tx('불러오는 중…', 'Loading…')} disabled={!valid} containerStyle={styles.cta} onPress={() => { completeStep(1); router.push('/plan/taste'); }} />
    </Screen>
  </PlanDesktopShell>;
}

const styles = StyleSheet.create({
  canvas: { backgroundColor: color.brand.ivory }, desktopCanvas: { paddingTop: spacing[8], paddingHorizontal: spacing[8], maxWidth: 960, backgroundColor: color.brand.ivory }, topBar: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, logo: { width: 88, height: 28 }, title: { marginTop: spacing[3] }, subtitle: { marginTop: spacing[1], marginBottom: spacing[3] },
  grid: { gap: spacing[4] }, gridWide: { flexDirection: 'row', flexWrap: 'wrap' }, card: { minWidth: '48%', flexGrow: 1, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border, shadowColor: color.brand.navy, shadowOpacity: .06, shadowRadius: 12, shadowOffset: { width: 0, height: 4 }, elevation: 2 }, field: { flex: 1, gap: spacing[2] }, input: { minHeight: 48, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[3] }, inputDesktop: { borderColor: color.surface.border, backgroundColor: color.surface.subtle }, invalid: { borderColor: color.state.danger },
  dateRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, dateRowPhone: { flexDirection: 'column', alignItems: 'stretch' }, dateArrow: { marginTop: spacing[6], color: color.text.muted }, badge: { alignSelf: 'center', paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.subtle }, stepper: { minHeight: 58, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, stepperControls: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, roundButton: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' }, roundButtonDesktop: { backgroundColor: color.surface.subtle }, roundButtonPlusDesktop: { backgroundColor: color.brand.navy }, disabled: { opacity: .4 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, chip: { minHeight: 44, paddingHorizontal: spacing[4], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, chipDesktop: { borderColor: color.surface.border, backgroundColor: color.surface.subtle }, chipSelected: { backgroundColor: color.action.primary, borderColor: color.action.primary }, chipSelectedDesktop: { backgroundColor: color.surface.warm, borderColor: color.brand.orange }, moneyRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, moneyInput: { flex: 1 }, transport: { flexDirection: 'row', gap: spacing[2] }, transportButton: { flex: 1, minHeight: 48, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, reason: { marginTop: spacing[6], textAlign: 'center', color: color.text.body }, cta: { marginTop: spacing[3], backgroundColor: color.brand.navy },
});
