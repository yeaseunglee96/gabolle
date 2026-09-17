import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Button } from '@/components/Button';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { PlanStepHeader } from '@/plan/PlanStepHeader';
import { PlanDesktopShell } from '@/plan/PlanDesktopShell';
import { type ConstraintSelectionStatus, type PlanDraft, usePlan } from '@/plan/PlanProvider';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { useI18n } from '@/i18n';
import { searchPlacesByName, type PlaceSearchItem } from '@/discovery/places';
import { placeNameForLanguage } from '@/discovery/romanize';

const ALLERGIES = [['PEANUT', '땅콩', 'Peanuts'], ['TREE_NUT', '견과류', 'Tree nuts'], ['SHELLFISH_CRUSTACEAN', '갑각류', 'Shellfish'], ['FISH', '생선', 'Fish'], ['EGG', '달걀', 'Egg'], ['MILK_DAIRY', '우유·유제품', 'Milk · dairy'], ['WHEAT', '밀', 'Wheat'], ['SOY', '대두', 'Soy']] as const;
const DIETS = [['VEGETARIAN', '채식', 'Vegetarian'], ['VEGAN', '비건', 'Vegan'], ['HALAL', '할랄', 'Halal'], ['GLUTEN_FREE', '글루텐 프리', 'Gluten-free'], ['PESCATARIAN', '페스코', 'Pescatarian']] as const;
const WALK = [[500, '500m 이내', 'Within 500m'], [1000, '1km 이내', 'Within 1km'], [2000, '2km 이내', 'Within 2km'], [0, '제한 없음', 'No limit']] as const;

function Card({ title, required, children }: { title: string; required?: boolean; children: React.ReactNode }) { const { tx } = useI18n(); return <View style={styles.card}><View style={styles.cardHeader}><View style={styles.cardTitle}><View style={styles.dot} /><Text weight="bold">{title}</Text></View>{required && <View style={styles.required}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('필수', 'Required')}</Text></View>}</View>{children}</View>; }
function Chip({ label, selected, onPress, radio = false }: { label: string; selected: boolean; onPress: () => void; radio?: boolean }) { return <Pressable accessibilityRole={radio ? 'radio' : 'checkbox'} accessibilityState={radio ? { selected } : { checked: selected }} onPress={onPress} style={[styles.chip, selected && styles.selected]}><Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.heading}>{label}</Text></Pressable>; }
function Status({ label, value, answered, onChange }: { label: string; value: ConstraintSelectionStatus; answered: boolean; onChange: (value: ConstraintSelectionStatus) => void }) { const { tx } = useI18n(); return <View accessibilityRole="radiogroup" accessibilityLabel={tx(`${label} 여부`, `Has ${label}`)} style={styles.row}><Chip radio label={tx('해당 없음', 'None')} selected={answered && value === 'NONE'} onPress={() => onChange('NONE')} /><Chip radio label={tx('조건 선택', 'Select conditions')} selected={answered && value === 'VALUES'} onPress={() => onChange('VALUES')} /></View>; }
function Binary({ label, value, onChange }: { label: string; value: boolean | null; onChange: (value: boolean) => void }) { const { tx } = useI18n(); return <View style={styles.binary}><Text style={styles.grow} weight="bold">{label}</Text><View accessibilityRole="radiogroup" accessibilityLabel={label} style={styles.row}><Chip radio label={tx('예', 'Yes')} selected={value === true} onPress={() => onChange(true)} /><Chip radio label={tx('아니요', 'No')} selected={value === false} onPress={() => onChange(false)} /></View></View>; }

const accommodationStyles = StyleSheet.create({
  suggestionList: { overflow: 'hidden', borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.md, backgroundColor: color.surface.card },
  suggestionItem: { minHeight: 56, justifyContent: 'center', gap: spacing[1], paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border },
  suggestionPressed: { backgroundColor: color.surface.subtle },
  selectedAccommodation: { gap: spacing[1], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.successBg },
});

export default function Constraints() {
  const router = useRouter(); const { kind } = useLayout(); const { tx, language } = useI18n(); const { mobility } = useOnboardingPreferences();
  const { draft, ready, update, completeStep } = usePlan(); const [panelIndex, setPanelIndex] = useState(0); const [validationRequested, setValidationRequested] = useState(false);
  const [accommodationResults, setAccommodationResults] = useState<PlaceSearchItem[]>([]);
  const [accommodationSearching, setAccommodationSearching] = useState(false);
  const [accommodationSearched, setAccommodationSearched] = useState(false);
  const accommodationDebounce = useRef<ReturnType<typeof setTimeout> | null>(null);
  const accommodationAbort = useRef<AbortController | null>(null);
  const valid = ready && draft.allergyAnswered && draft.dietAnswered && (draft.allergyStatus !== 'VALUES' || draft.allergies.length > 0) && (draft.dietStatus !== 'VALUES' || draft.dietTypes.length > 0);
  const safetyReady = valid && draft.allergyStatus !== 'UNKNOWN' && draft.dietStatus !== 'UNKNOWN';
  useEffect(() => { const patch: Partial<PlanDraft> = {}; if (mobility === 'wheelchair' && draft.wheelchair === null) patch.wheelchair = true; if (mobility === 'stroller' && draft.stroller === null) patch.stroller = true; if (mobility === 'slow' && draft.maxWalkingDistanceM === null) patch.maxWalkingDistanceM = 500; if (Object.keys(patch).length) update(patch); }, [draft.maxWalkingDistanceM, draft.stroller, draft.wheelchair, mobility, update]);
  const setStatus = (stateKey: 'allergyStatus' | 'dietStatus', listKey: 'allergies' | 'dietTypes', value: ConstraintSelectionStatus) => update({ [stateKey]: value, [stateKey === 'allergyStatus' ? 'allergyAnswered' : 'dietAnswered']: true, ...(value !== 'VALUES' ? { [listKey]: [] } : {}) });
  const toggle = (key: 'allergies' | 'dietTypes', value: string) => {
    const next = draft[key].includes(value) ? draft[key].filter((item) => item !== value) : [...draft[key], value];
    update({
      [key]: next,
      [key === 'allergies' ? 'allergyStatus' : 'dietStatus']: 'VALUES',
      [key === 'allergies' ? 'allergyAnswered' : 'dietAnswered']: true,
    });
  };
  useEffect(() => () => {
    if (accommodationDebounce.current) clearTimeout(accommodationDebounce.current);
    accommodationAbort.current?.abort();
  }, []);
  const handleAccommodationChange = (value: string) => {
    update({ accommodation: value, accommodationPlace: null });
    if (accommodationDebounce.current) clearTimeout(accommodationDebounce.current);
    accommodationAbort.current?.abort();
    const query = value.trim();
    if (query.length < 2) {
      setAccommodationResults([]); setAccommodationSearching(false); setAccommodationSearched(false);
      return;
    }
    setAccommodationSearching(true); setAccommodationSearched(false);
    accommodationDebounce.current = setTimeout(() => {
      const controller = new AbortController();
      accommodationAbort.current = controller;
      void searchPlacesByName(query, controller.signal).then((items) => {
        if (!controller.signal.aborted) setAccommodationResults(items);
      }).catch(() => {
        if (!controller.signal.aborted) setAccommodationResults([]);
      }).finally(() => {
        if (!controller.signal.aborted) { setAccommodationSearching(false); setAccommodationSearched(true); }
      });
    }, 300);
  };
  const selectAccommodation = (item: PlaceSearchItem) => {
    update({ accommodation: placeNameForLanguage(item.nameKo, item.nameEn, language), accommodationPlace: { ...item } });
    setAccommodationResults([]); setAccommodationSearched(false);
  };
  return <PlanDesktopShell><Screen scroll wide style={styles.canvas}>
    {kind === 'phone' && <View style={styles.top}><Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/plan/taste')} style={styles.back}><Text variant="title">‹</Text></Pressable><BrandLogoLink imageStyle={styles.logo} /><View style={styles.pill}><Text variant="caption" weight="bold" color={color.brand.ivory}>3 / 4</Text></View></View>}
    <PlanStepHeader current={3} /><Text variant="display" weight="bold" style={styles.title}>{tx('제약 조건', 'Travel constraints')}</Text><Text color={color.text.body} style={styles.subtitle}>{tx('AI가 아래 조건을 임의로 완화하지 않습니다.', 'AI will not loosen these conditions without your consent.')}</Text>
    <View style={[styles.grid, kind === 'tablet' && styles.gridWide]}>
      {(kind === 'tablet' || panelIndex === 0) && <View style={styles.group}><View style={styles.groupHeading}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('1 / 3 · 안전 조건', '1 / 3 · Safety')}</Text><Text variant="caption" color={color.text.muted}>{tx('두 항목만 확인하면 돼요 · 약 20초', 'Only two required answers · about 20 seconds')}</Text></View><Card title={tx('알레르기', 'Allergies')} required><Text variant="caption" color={color.text.body}>{tx('해당 여부를 반드시 알려주세요. 선택한 재료는 추천에서 제외해요.', 'Tell us whether you have allergies. Selected ingredients are excluded.')}</Text><Status label={tx('알레르기', 'Allergies')} value={draft.allergyStatus} answered={draft.allergyAnswered} onChange={(v) => { setValidationRequested(false); setStatus('allergyStatus', 'allergies', v); }} />{draft.allergyStatus === 'VALUES' && <><View style={styles.wrap}>{ALLERGIES.map(([code, ko, en]) => <Chip key={code} label={tx(ko, en)} selected={draft.allergies.includes(code)} onPress={() => toggle('allergies', code)} />)}</View><Text variant="caption" color={color.text.muted}>{tx('안전한 저장을 위해 현재 지원하는 항목만 선택할 수 있어요.', 'For safe storage, only the supported items can be selected.')}</Text></>}{validationRequested && (!draft.allergyAnswered || (draft.allergyStatus === 'VALUES' && !draft.allergies.length)) && <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{!draft.allergyAnswered ? tx('알레르기 유무를 선택해 주세요.', 'Select whether you have allergies.') : tx('알레르기 항목을 하나 이상 선택해 주세요.', 'Choose at least one allergy.')}</Text>}</Card><Card title={tx('식단', 'Diet')} required><Text variant="caption" color={color.text.body}>{tx('적용할 식단이 없다면 ‘해당 없음’을 선택해 주세요.', 'Choose None if you have no dietary restrictions.')}</Text><Status label={tx('식단', 'Diet')} value={draft.dietStatus} answered={draft.dietAnswered} onChange={(v) => { setValidationRequested(false); setStatus('dietStatus', 'dietTypes', v); }} />{draft.dietStatus === 'VALUES' && <View style={styles.wrap}>{DIETS.map(([code, ko, en]) => <Chip key={code} label={tx(ko, en)} selected={draft.dietTypes.includes(code)} onPress={() => toggle('dietTypes', code)} />)}</View>}{validationRequested && (!draft.dietAnswered || (draft.dietStatus === 'VALUES' && !draft.dietTypes.length)) && <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{!draft.dietAnswered ? tx('식단 제한 유무를 선택해 주세요.', 'Select whether you have dietary restrictions.') : tx('식단 항목을 하나 이상 선택해 주세요.', 'Choose at least one dietary restriction.')}</Text>}</Card>
      <Card title={tx('이용 조건', 'Usage conditions')}>
        <Binary label={tx('영어 메뉴 필요', 'English menu needed')} value={draft.englishMenuRequired} onChange={(englishMenuRequired) => update({ englishMenuRequired })} />
        <Binary label={tx('해외카드 결제 필요', 'Foreign card payment needed')} value={draft.foreignCardRequired} onChange={(foreignCardRequired) => update({ foreignCardRequired })} />
        <Binary label={tx('혼밥 가능한 곳 우선', 'Prefer solo-dining friendly')} value={draft.soloDiningPreferred} onChange={(soloDiningPreferred) => update({ soloDiningPreferred })} />
        <View style={styles.accommodationField}>
          <Text weight="bold">{tx('숙소 지정 (선택)', 'Accommodation (optional)')}</Text>
          <TextInput accessibilityLabel={tx('숙소 검색', 'Search accommodation')} value={draft.accommodation} onChangeText={handleAccommodationChange} placeholder={tx('숙소 이름을 2자 이상 입력하세요', 'Enter at least 2 characters')} placeholderTextColor={color.text.muted} style={styles.accommodationInput} />
          {accommodationSearching && <Text variant="caption" color={color.text.muted}>{tx('장소를 찾고 있어요…', 'Searching places…')}</Text>}
          {accommodationResults.length > 0 && <View accessibilityRole="list" style={accommodationStyles.suggestionList}>{accommodationResults.map((item) => <Pressable key={item.placeId} accessibilityRole="button" accessibilityLabel={tx(`${item.nameKo} 숙소로 선택`, `Choose ${item.nameEn ?? item.nameKo} as accommodation`)} onPress={() => selectAccommodation(item)} style={({ pressed }) => [accommodationStyles.suggestionItem, pressed && accommodationStyles.suggestionPressed]}><Text weight="bold">{placeNameForLanguage(item.nameKo, item.nameEn, language)}</Text><Text variant="caption" color={color.text.muted}>{tx(item.address, item.addressEn ?? item.address) || tx('주소 정보 없음', 'Address unavailable')}</Text></Pressable>)}</View>}
          {accommodationSearched && !accommodationSearching && accommodationResults.length === 0 && !draft.accommodationPlace && <Text accessibilityRole="alert" variant="caption" color={color.text.muted}>{tx('검색 결과가 없어요. 다른 숙소명이나 주소로 다시 찾아주세요.', 'No results. Try another name or address.')}</Text>}
          {draft.accommodationPlace && <View style={accommodationStyles.selectedAccommodation}><Text variant="caption" weight="bold" color={color.state.success}>{tx('숙소 선택 완료', 'Accommodation selected')}</Text><Text variant="caption" color={color.text.body}>{tx(draft.accommodationPlace.address, draft.accommodationPlace.addressEn ?? draft.accommodationPlace.address) || placeNameForLanguage(draft.accommodationPlace.nameKo, draft.accommodationPlace.nameEn, language)}</Text></View>}
          {draft.accommodationPlace && <Text variant="caption" color={color.text.muted}>{tx('매일 이곳에서 시작하고 이곳으로 돌아와요.', "You'll start and return here every day.")}</Text>}
        </View>
        {draft.transport !== 'CAR' && <View style={styles.transfersField}>
          <Text weight="bold">{tx('최대 환승 횟수', 'Max transfers')}</Text>
          <View accessibilityRole="radiogroup" accessibilityLabel={tx('최대 환승 횟수', 'Max transfers')} style={styles.wrap}>{([[0, '0회', '0'], [1, '1회', '1'], [2, '2회', '2'], [3, '3회 이상', '3+']] as const).map(([value, ko, en]) => <Chip key={value} radio label={tx(ko, en)} selected={draft.maxTransfers === value} onPress={() => update({ maxTransfers: value })} />)}</View>
        </View>}
      </Card></View>}
      {(kind === 'tablet' || panelIndex === 1) && <View style={styles.group}><View style={styles.groupHeading}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('2 / 3 · 이동 환경', '2 / 3 · Mobility')}</Text><Text variant="caption" color={color.text.muted}>{tx('모르면 선택하지 않고 넘어가도 괜찮아요.', "If you're not sure, feel free to skip.")}</Text></View><Card title={tx('최대 보행 거리', 'Max walking distance')}><View accessibilityRole="radiogroup" accessibilityLabel={tx('한 번에 걷는 최대 거리', 'Maximum distance to walk at once')} style={styles.wrap}>{WALK.map(([meters, ko, en]) => <Chip radio key={meters} label={tx(ko, en)} selected={draft.maxWalkingDistanceM === meters} onPress={() => update({ maxWalkingDistanceM: meters })} />)}</View></Card><Card title={tx('길 환경', 'Route conditions')}><Binary label={tx('가파른 경사 피하기', 'Avoid steep slopes')} value={draft.slopeConstraint === null ? null : draft.slopeConstraint === 'AVOID'} onChange={(v) => update({ slopeConstraint: v ? 'AVOID' : 'ALLOW' })} /><Binary label={tx('계단 피하기', 'Avoid stairs')} value={draft.stairsConstraint === null ? null : draft.stairsConstraint === 'AVOID'} onChange={(v) => update({ stairsConstraint: v ? 'AVOID' : 'ALLOW' })} /><Binary label={tx('그늘길 우선', 'Prefer shaded routes')} value={draft.shadePreference === null ? null : draft.shadePreference === 'PREFER'} onChange={(v) => update({ shadePreference: v ? 'PREFER' : 'NO_PREFERENCE' })} /></Card></View>}
      {(kind === 'tablet' || panelIndex === 2) && <View style={styles.group}><View style={styles.groupHeading}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('3 / 3 · 이동 보조', '3 / 3 · Mobility aids')}</Text><Text variant="caption" color={color.text.muted}>{tx('일정과 이동 경로를 고를 때 반영해요.', 'Used when planning your schedule and routes.')}</Text></View><Card title={tx('이동 보조·짐', 'Mobility aids · Luggage')}><Binary label={tx('휠체어', 'Wheelchair')} value={draft.wheelchair} onChange={(wheelchair) => update({ wheelchair })} /><Binary label={tx('유아차', 'Stroller')} value={draft.stroller} onChange={(stroller) => update({ stroller })} /><Binary label={tx('큰 짐', 'Large luggage')} value={draft.luggage} onChange={(luggage) => update({ luggage })} /></Card></View>}
    </View>
    {kind === 'phone' && <View style={styles.panelNav}><Pressable accessibilityRole="button" accessibilityState={{ disabled: panelIndex === 0 }} disabled={panelIndex === 0} onPress={() => setPanelIndex((value) => Math.max(0, value - 1))} style={[styles.panelNavButton, panelIndex === 0 && styles.disabled]}><Text variant="caption" weight="bold">{tx('이전', 'Back')}</Text></Pressable>{panelIndex === 0 && <Button accessibilityRole="button" label={tx('안전 조건 확인 완료', 'Confirm safety conditions')} containerStyle={styles.panelCta} onPress={() => { if (safetyReady) setPanelIndex(1); else setValidationRequested(true); }} />}{panelIndex === 1 && <Button accessibilityRole="button" label={tx('이동 환경 확인 완료', 'Confirm mobility conditions')} containerStyle={styles.panelCta} onPress={() => setPanelIndex(2)} />}</View>}
    {(kind === 'tablet' || panelIndex === 2) && <><View style={styles.notice}><Text variant="caption" color={color.text.body}>{tx('알레르기와 이동 지원 정보는 이 여행을 준비하는 동안에만 사용하며, 기기에 저장하지 않습니다.', 'Allergy and mobility details are used only for this trip and are not stored on the device.')}</Text></View>{validationRequested && !valid && <Text accessibilityRole="alert" variant="caption" style={styles.reason}>{tx('알레르기와 식단의 ‘해당 없음’ 또는 적용 조건을 선택해 주세요.', 'Answer the allergy and diet questions before continuing.')}</Text>}<Button accessibilityRole="button" accessibilityHint={safetyReady ? tx('최종 확인 단계로 이동합니다.', 'Continue to final review.') : tx('누르면 아직 답하지 않은 필수 조건을 안내합니다.', 'Press to review unanswered required conditions.')} label={tx('최종 확인으로', 'Review trip')} containerStyle={styles.cta} onPress={() => { if (!safetyReady) { setValidationRequested(true); setPanelIndex(0); return; } completeStep(3); router.push('/plan/confirm'); }} /></>}
  </Screen></PlanDesktopShell>;
}
const styles = StyleSheet.create({ canvas: { backgroundColor: color.brand.ivory }, top: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, logo: { width: 88, height: 28 }, pill: { borderRadius: radius.full, backgroundColor: color.brand.navy, paddingHorizontal: spacing[3], paddingVertical: spacing[1] }, title: { marginTop: spacing[4] }, subtitle: { marginTop: spacing[1], marginBottom: spacing[4] }, grid: { gap: spacing[4] }, gridWide: { flexDirection: 'row', flexWrap: 'wrap' }, group: { width: '100%', gap: spacing[3] }, groupHeading: { gap: spacing[1] }, card: { minWidth: '48%', flexGrow: 1, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border, shadowColor: color.brand.navy, shadowOpacity: .06, shadowRadius: 12, shadowOffset: { width: 0, height: 4 }, elevation: 2 }, cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }, cardTitle: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] }, dot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.brand.orange }, required: { borderRadius: radius.full, backgroundColor: color.state.dangerBg, paddingHorizontal: spacing[2], paddingVertical: spacing[1] }, row: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, wrap: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, chip: { minHeight: 44, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.brand.ivory, alignItems: 'center', justifyContent: 'center' }, selected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy }, disabled: { opacity: .4 }, binary: { minHeight: 58, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.field }, grow: { flex: 1 }, accommodationField: { gap: spacing[2] }, accommodationInput: { minHeight: 48, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.brand.ivory, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[3] }, transfersField: { gap: spacing[2] }, panelNav: { marginTop: spacing[4], flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, panelNavButton: { minWidth: 72, minHeight: 48, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, panelCta: { flex: 1, marginTop: 0, backgroundColor: color.brand.navy }, notice: { marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.subtle }, reason: { marginTop: spacing[4], color: color.state.danger, textAlign: 'center' }, cta: { minHeight: 54, marginTop: spacing[3], backgroundColor: color.brand.navy } });
