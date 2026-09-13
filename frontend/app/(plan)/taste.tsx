import { useEffect, useRef, useState } from 'react';
import { Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';
import Animated, { FadeInRight, FadeOutLeft, ReduceMotion } from 'react-native-reanimated';
import { Button } from '@/components/Button';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { PlanStepHeader } from '@/plan/PlanStepHeader';
import { PlanDesktopShell } from '@/plan/PlanDesktopShell';
import { type MustVisitPlace, type PreferenceDimension, usePlan } from '@/plan/PlanProvider';
import { CONFLICT_LABEL_PAIR, conflictingFoodCode, FOODS } from '@/plan/foodConflicts';
import { bilingualPlaceName, searchPlacesByName, type PlaceSearchItem } from '@/discovery/places';
import { getPlaceCategories } from '@/discovery/placeCategories';
import { haversineDistanceKm } from '@/utils/geo';
import { useI18n } from '@/i18n';

const MUST_VISIT_MAX = 5;
const MUST_VISIT_WARN_KM = 25;
const MUST_VISIT_SEARCH_DEBOUNCE_MS = 300;
const MUST_VISIT_MIN_QUERY_LENGTH = 2;

const CATEGORIES = [
  { key: 'SEA_BEACH', labelKo: '바다 & 해변', labelEn: 'Sea & Beach', image: require('../../assets/taste/sea-beach.png') },
  { key: 'CITY', labelKo: '도심 탐험', labelEn: 'City Exploration', image: require('../../assets/taste/city.png') },
  { key: 'CAFE_HEALING', labelKo: '카페 & 힐링', labelEn: 'Cafe & Healing', image: require('../../assets/taste/cafe.png') },
  { key: 'CULTURE_TEMPLE', labelKo: '문화 & 사찰', labelEn: 'Culture & Temples', image: require('../../assets/taste/culture.png') },
  { key: 'FOOD', labelKo: '맛집 & 먹거리', labelEn: 'Food & Eats', image: require('../../assets/taste/food.png') },
  { key: 'NATURE_WALK', labelKo: '자연 & 산책', labelEn: 'Nature & Walks', image: require('../../assets/taste/nature.png') },
] as const;
const ATMOSPHERES = [['LIVELY', '활기찬', 'Lively'], ['RELAXED', '여유로운', 'Relaxed'], ['SENTIMENTAL', '감성적인', 'Sentimental'], ['ROMANTIC', '낭만적인', 'Romantic']] as const;
const LEGACY_CATEGORY: Record<string, string> = { sea: 'SEA_BEACH', alley: 'CITY', food: 'FOOD', nature: 'NATURE_WALK', night: 'CITY', photo: 'CITY' };
const PACE_OPTIONS = [
  { value: 'RELAXED' as const, ko: '여유롭게', en: 'Relaxed', koDesc: '하루 2~3곳만 돌고 한 곳에 오래 머물러요', enDesc: '2-3 places a day, more time at each' },
  { value: 'BALANCED' as const, ko: '균형 있게', en: 'Balanced', koDesc: '하루 3~4곳, 적당히 머물러요', enDesc: '3-4 places a day, a moderate pace' },
  { value: 'PACKED' as const, ko: '알차게', en: 'Packed', koDesc: '하루 5곳 이상, 짧게짧게 돌아요', enDesc: '5+ places a day, quick visits' },
];
const QUESTION_LABELS = [['카테고리', 'Categories'], ['분위기', 'Mood'], ['로컬성', 'Local feel'], ['조용함', 'Quietness'], ['관광지', 'Tourist spots'], ['음식', 'Food']] as const;

function Section({ title, description, skipped, onSkip, children }: { title: string; description: string; skipped?: boolean; onSkip: () => void; children: React.ReactNode }) {
  const { tx } = useI18n(); return <View style={styles.section}><View style={styles.sectionHeader}><View style={styles.sectionCopy}><Text variant="title" weight="bold">{title}</Text><Text variant="caption" color={color.text.muted}>{description}</Text></View><Pressable accessibilityRole="button" accessibilityState={{ selected: skipped }} onPress={onSkip} style={styles.skip}><Text variant="caption" weight="bold" color={color.text.eyebrow}>{skipped ? tx('건너뜀 ✓', 'Skipped ✓') : tx('건너뛰기', 'Skip')}</Text></Pressable></View>{children}</View>;
}
function Chips({ options, values, desktop, onChange }: { options: readonly (readonly [string, string])[]; values: string[]; desktop?: boolean; onChange: (values: string[]) => void }) {
  return <View style={styles.chips}>{options.map(([key, label]) => { const selected = values.includes(key); return <Pressable key={key} accessibilityRole="checkbox" accessibilityState={{ checked: selected }} onPress={() => onChange(selected ? values.filter((value) => value !== key) : [...values, key])} style={[styles.chip, desktop && styles.chipDesktop, selected && styles.selected, selected && desktop && styles.selectedDesktop]}><Text weight="bold" color={selected ? (desktop ? color.brand.orange : color.text.onAction) : color.text.heading}>{label}</Text></Pressable>; })}</View>;
}
function FoodChips({ options, values, allergies, dietTypes, desktop, onChange }: { options: readonly (readonly [string, string, string])[]; values: string[]; allergies: string[]; dietTypes: string[]; desktop?: boolean; onChange: (values: string[]) => void }) {
  const { tx } = useI18n();
  return <View style={styles.chips}>{options.map(([key, labelKo, labelEn]) => {
    const conflict = conflictingFoodCode(key, allergies, dietTypes);
    const blocked = conflict !== null;
    const selected = values.includes(key);
    const conflictLabel = conflict ? tx(...CONFLICT_LABEL_PAIR[conflict.code]) : '';
    return <View key={key} style={styles.foodChipWrap}>
      <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: selected, disabled: blocked }} disabled={blocked} onPress={() => onChange(selected ? values.filter((value) => value !== key) : [...values, key])} style={[styles.chip, desktop && styles.chipDesktop, selected && styles.selected, selected && desktop && styles.selectedDesktop, blocked && styles.chipBlocked]}>
        <Text weight="bold" color={blocked ? color.text.muted : selected ? (desktop ? color.brand.orange : color.text.onAction) : color.text.heading}>{tx(labelKo, labelEn)}</Text>
      </Pressable>
      {conflict && <Text variant="caption" color={color.state.danger}>{tx(`${conflictLabel}${conflict.kind === 'allergy' ? ' 알레르기' : ' 식단'}와 겹쳐요`, `Conflicts with ${conflictLabel}`)}</Text>}
    </View>;
  })}</View>;
}
function Scale({ label, value, low, high, desktop, onChange }: { label: string; value: number | null; low: string; high: string; desktop?: boolean; onChange: (value: number | null) => void }) {
  const { tx } = useI18n();
  return <View accessibilityRole="radiogroup" accessibilityLabel={label} style={styles.scale}><View style={styles.scaleLabels}><Text variant="caption">{low}</Text><Text variant="caption">{high}</Text></View><View style={[styles.scalePoints, desktop && styles.scalePointsDesktop]}>{[1, 2, 3, 4, 5].map((point) => <Pressable key={point} accessibilityRole="radio" accessibilityLabel={tx(`${label} ${point}단계`, `${label} level ${point}`)} accessibilityState={{ selected: value === point }} onPress={() => onChange(point)} style={[styles.scalePoint, value === point && styles.scalePointSelected, value === point && desktop && styles.scalePointSelectedDesktop]}><Text variant="caption" weight="bold" color={value === point ? color.text.onAction : color.text.body}>{point}</Text></Pressable>)}</View></View>;
}

// 서로 25km 이상 떨어진 쌍을 전부 찾는다(S15P21E201-463) — 한 쌍만 걸러 숨기면 "왜 저 둘은
// 경고가 없냐" 는 재현하기 어려운 의문이 된다.
type MustVisitWarning = { key: string; nameA: string; nameB: string; km: string };
function mustVisitWarnings(places: MustVisitPlace[]): MustVisitWarning[] {
  const warnings: MustVisitWarning[] = [];
  for (let i = 0; i < places.length; i++) {
    for (let j = i + 1; j < places.length; j++) {
      const km = haversineDistanceKm(places[i], places[j]);
      if (km > MUST_VISIT_WARN_KM) warnings.push({ key: `${places[i].placeId}-${places[j].placeId}`, nameA: places[i].nameKo, nameB: places[j].nameKo, km: km.toFixed(1) });
    }
  }
  return warnings;
}

export default function Taste() {
  const router = useRouter(); const { kind } = useLayout(); const { tx } = useI18n(); const { draft, update, completeStep, foodConflictNotice, clearFoodConflictNotice } = usePlan();
  const [feedback, setFeedback] = useState<string | null>(null);
  const [panelIndex, setPanelIndex] = useState(0);
  const advanceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [mustVisitQuery, setMustVisitQuery] = useState('');
  const [mustVisitResults, setMustVisitResults] = useState<PlaceSearchItem[]>([]);
  const [mustVisitSearching, setMustVisitSearching] = useState(false);
  const [mustVisitSearched, setMustVisitSearched] = useState(false);
  const [mustVisitNotice, setMustVisitNotice] = useState<string | null>(null);
  const mustVisitDebounce = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mustVisitAbort = useRef<AbortController | null>(null);
  // S15P21E201-897: 추천 후보 비교에 쓰이는 place.category 값이 지금 갈래 몇 개는 0곳이라
  // 그걸 고르면 일정 생성이 ERROR_NO_CANDIDATES 로 실패하고 재시도해도 안 풀린다(엔진 규칙).
  // 그래서 CATEGORIES 여섯 개 중 서버가 "지금 장소가 있다" 고 낸 것만 카드로 보여준다.
  const [categoryAvailability, setCategoryAvailability] = useState<'loading' | 'ready' | 'unavailable'>('loading');
  const [availableCategoryCodes, setAvailableCategoryCodes] = useState<Set<string>>(new Set());
  useEffect(() => {
    const controller = new AbortController();
    void getPlaceCategories(controller.signal).then((result) => {
      if (controller.signal.aborted) return;
      if (result.state !== 'success') { setCategoryAvailability('unavailable'); return; }
      const codes = new Set(result.categories.filter((item) => item.placeCount > 0).map((item) => item.code));
      setAvailableCategoryCodes(codes);
      setCategoryAvailability(codes.size > 0 ? 'ready' : 'unavailable');
    });
    return () => controller.abort();
  }, []);
  const visibleCategories = categoryAvailability === 'ready' ? CATEGORIES.filter((item) => availableCategoryCodes.has(item.key)) : [];
  const goToPanel = (index: number) => setPanelIndex(Math.max(0, Math.min(5, index)));
  const advancePanel = () => {
    if (advanceTimer.current) clearTimeout(advanceTimer.current);
    advanceTimer.current = setTimeout(() => setPanelIndex((value) => Math.min(5, value + 1)), 220);
  };
  const setStatus = (dimension: PreferenceDimension, status: 'UNKNOWN' | 'SELECTED' | 'SKIPPED') => update({ preferenceAnswerStatus: { ...draft.preferenceAnswerStatus, [dimension]: status } });
  const skip = (dimension: PreferenceDimension, patch: Partial<typeof draft>) => { update({ ...patch, preferenceAnswerStatus: { ...draft.preferenceAnswerStatus, [dimension]: 'SKIPPED' } }); };
  const skipAndAdvance = (dimension: PreferenceDimension, patch: Partial<typeof draft>) => { skip(dimension, patch); if (kind === 'phone') advancePanel(); };
  useEffect(() => {
    const normalized = [...new Set(draft.preferences.map((value) => LEGACY_CATEGORY[value] ?? value))];
    if (normalized.join('|') !== draft.preferences.join('|')) update({ preferences: normalized });
  }, [draft.preferences, update]);
  useEffect(() => () => { if (advanceTimer.current) clearTimeout(advanceTimer.current); }, []);
  function toggleCategory(key: string) {
    const selected = draft.preferences.includes(key);
    if (!selected && draft.preferences.length >= 3) { setFeedback(tx('최대 3개까지 선택할 수 있어요. 먼저 하나를 해제해 주세요.', 'You can choose up to 3. Remove one first.')); return; }
    const preferences = selected ? draft.preferences.filter((value) => value !== key) : [...draft.preferences, key];
    update({ preferences, preferenceAnswerStatus: { ...draft.preferenceAnswerStatus, category: preferences.length ? 'SELECTED' : 'UNKNOWN' } }); setFeedback(null);
    if (kind === 'phone' && !selected && preferences.length === 3) advancePanel();
  }
  const performMustVisitSearch = async (query: string) => {
    mustVisitAbort.current?.abort();
    const controller = new AbortController();
    mustVisitAbort.current = controller;
    setMustVisitSearching(true);
    try {
      const items = await searchPlacesByName(query, controller.signal);
      if (controller.signal.aborted) return;
      setMustVisitResults(items);
    } catch {
      if (controller.signal.aborted) return;
      setMustVisitResults([]);
    } finally {
      if (!controller.signal.aborted) { setMustVisitSearching(false); setMustVisitSearched(true); }
    }
  };
  const handleMustVisitChange = (value: string) => {
    setMustVisitQuery(value);
    if (mustVisitDebounce.current) clearTimeout(mustVisitDebounce.current);
    const trimmed = value.trim();
    if (trimmed.length < MUST_VISIT_MIN_QUERY_LENGTH) {
      mustVisitAbort.current?.abort();
      setMustVisitSearching(false); setMustVisitSearched(false); setMustVisitResults([]);
      return;
    }
    mustVisitDebounce.current = setTimeout(() => void performMustVisitSearch(trimmed), MUST_VISIT_SEARCH_DEBOUNCE_MS);
  };
  const addMustVisit = (item: PlaceSearchItem) => {
    if (draft.mustVisitPlaces.some((place) => place.placeId === item.placeId)) {
      setMustVisitNotice(tx('이미 추가한 장소예요.', 'Already added.'));
      return;
    }
    if (draft.mustVisitPlaces.length >= MUST_VISIT_MAX) {
      setMustVisitNotice(tx(`최대 ${MUST_VISIT_MAX}곳까지 추가할 수 있어요.`, `You can add up to ${MUST_VISIT_MAX} places.`));
      return;
    }
    update({ mustVisitPlaces: [...draft.mustVisitPlaces, { placeId: item.placeId, nameKo: item.nameKo, nameEn: item.nameEn, lat: item.lat, lng: item.lng }] });
    setMustVisitNotice(null);
    setMustVisitQuery(''); setMustVisitResults([]); setMustVisitSearched(false);
    if (mustVisitDebounce.current) clearTimeout(mustVisitDebounce.current);
    mustVisitAbort.current?.abort();
  };
  const removeMustVisit = (placeId: string) => {
    update({ mustVisitPlaces: draft.mustVisitPlaces.filter((place) => place.placeId !== placeId) });
    setMustVisitNotice(null);
  };
  const mustVisitWarningList = mustVisitWarnings(draft.mustVisitPlaces);
  function next() { completeStep(2); router.push('/plan/conditions'); }
  // 이미 답한 항목(SELECTED)은 그대로 두고, 아직 안 건드린 항목(UNKNOWN)만 건너뜀 처리한다 —
  // 개별 질문의 "건너뛰기"가 다른 질문 답을 지우지 않는 것과 같은 규칙이다. 전부 지우면
  // 카테고리를 3개 고르고 "선택 완료"까지 누른 뒤 나머지를 건너뛰었는데 최종 확인 화면에
  // 카테고리가 "선택 안 함"으로 나오는 식으로, 이미 낸 답이 조용히 사라진다.
  function skipAll() {
    const patch: Partial<typeof draft> = {};
    const status = { ...draft.preferenceAnswerStatus };
    if (status.category === 'UNKNOWN') { patch.preferences = []; status.category = 'SKIPPED'; }
    if (status.atmosphere === 'UNKNOWN') { patch.atmospheres = []; status.atmosphere = 'SKIPPED'; }
    if (status.locality === 'UNKNOWN') { patch.localityLevel = null; status.locality = 'SKIPPED'; }
    if (status.quietness === 'UNKNOWN') { patch.quietLevel = null; status.quietness = 'SKIPPED'; }
    if (status.touristPreference === 'UNKNOWN') { patch.touristLevel = null; status.touristPreference = 'SKIPPED'; }
    if (status.foodPreference === 'UNKNOWN') { patch.foods = []; status.foodPreference = 'SKIPPED'; }
    update({ ...patch, preferenceAnswerStatus: status });
    next();
  }
  const answerStatuses = [
    draft.preferenceAnswerStatus.category,
    draft.preferenceAnswerStatus.atmosphere,
    draft.preferenceAnswerStatus.locality,
    draft.preferenceAnswerStatus.quietness,
    draft.preferenceAnswerStatus.touristPreference,
    draft.preferenceAnswerStatus.foodPreference,
  ];
  const multiSelectReady = panelIndex === 0
    ? categoryAvailability !== 'ready' || draft.preferences.length > 0
    : panelIndex === 1
      ? draft.atmospheres.length > 0
      : panelIndex === 5
        ? draft.foods.length > 0
        : true;
  return <PlanDesktopShell><Screen scroll wide style={styles.canvas}>
    {kind === 'phone' && <View style={styles.topBar}><Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/plan/basic')} style={styles.back}><Text variant="title">‹</Text></Pressable><BrandLogoLink imageStyle={styles.logo} /><View style={styles.stepPill}><Text variant="caption" weight="bold">2 / 4</Text></View></View>}
    <PlanStepHeader current={2} />
    <View style={styles.headingRow}><View><Text variant="display" weight="bold">{tx('취향을 알려주세요', 'Tell us your preferences')}</Text><Text color={color.text.muted} style={styles.subtitle}>{tx('좋아하는 여행 스타일을 선택해 주세요', 'Choose the travel styles you enjoy')}</Text></View><Pressable accessibilityRole="button" onPress={skipAll} style={styles.skipAll}><Text variant="caption" weight="bold">{tx('전체 건너뛰기', 'Skip all')}</Text></Pressable></View>

    {kind === 'phone' && <View style={styles.questionProgress}>
      <View style={styles.questionMeta}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx(`취향 ${panelIndex + 1} / 6`, `Preference ${panelIndex + 1} / 6`)}</Text><Text variant="caption" color={color.text.muted}>{tx(`약 ${Math.max(10, (6 - panelIndex) * 10)}초 남음`, `About ${Math.max(10, (6 - panelIndex) * 10)}s left`)}</Text></View>
      <View style={styles.questionDots}>{QUESTION_LABELS.map(([labelKo, labelEn], index) => <Pressable key={labelKo} accessibilityRole="button" accessibilityLabel={tx(`${labelKo} 질문으로 이동`, `Go to ${labelEn} question`)} onPress={() => goToPanel(index)} style={[styles.questionDot, index === panelIndex && styles.questionDotCurrent, answerStatuses[index] !== 'UNKNOWN' && styles.questionDotAnswered]} />)}</View>
      <View style={styles.answerSummary}>{QUESTION_LABELS.map(([labelKo, labelEn], index) => answerStatuses[index] !== 'UNKNOWN' && <Pressable key={labelKo} accessibilityRole="button" accessibilityLabel={tx(`${labelKo} 답변 수정`, `Edit ${labelEn} answer`)} onPress={() => goToPanel(index)} style={styles.answerChip}><Text variant="caption" weight="bold" color={color.text.body}>{tx(labelKo, labelEn)} {answerStatuses[index] === 'SKIPPED' ? tx('건너뜀', 'Skipped') : tx('완료', 'Done')} · {tx('수정', 'Edit')}</Text></Pressable>)}</View>
    </View>}

    <View style={[styles.content, kind === 'tablet' && styles.contentWide]}>
      <Animated.View key={kind === 'phone' ? panelIndex : 'desktop'} entering={kind === 'phone' ? FadeInRight.duration(180).reduceMotion(ReduceMotion.System) : undefined} exiting={kind === 'phone' ? FadeOutLeft.duration(120).reduceMotion(ReduceMotion.System) : undefined} style={[styles.animatedContent, kind === 'tablet' && styles.animatedContentWide]}>
      {(kind === 'tablet' || panelIndex === 0) && <View style={kind === 'tablet' ? styles.categoryColumn : undefined}>
      <Section title={tx('여행 카테고리', 'Travel categories')} description={tx('최대 3개까지 선택할 수 있어요.', 'Choose up to 3.')} skipped={draft.preferenceAnswerStatus.category === 'SKIPPED'} onSkip={() => skipAndAdvance('category', { preferences: [] })}>
        {categoryAvailability === 'loading' ? (
          <Text variant="caption" color={color.text.muted}>{tx('고를 수 있는 카테고리를 불러오고 있어요…', 'Loading available categories…')}</Text>
        ) : categoryAvailability === 'unavailable' ? (
          <Text variant="caption" color={color.text.muted}>{tx('지금은 고를 수 있는 여행 카테고리가 없어요. 모든 곳에서 추천해 드릴게요.', "No travel categories are available right now — we'll recommend from everywhere.")}</Text>
        ) : (
          <>
            <View style={styles.imageGrid}>{visibleCategories.map((item) => { const selected = draft.preferences.includes(item.key); return <Pressable key={item.key} accessibilityRole="checkbox" accessibilityState={{ checked: selected }} onPress={() => toggleCategory(item.key)} style={[styles.imageCard, kind === 'phone' && styles.imageCardPhone, kind === 'tablet' && styles.imageCardWide, selected && styles.imageCardSelected]}><Image source={item.image} resizeMode="cover" accessibilityIgnoresInvertColors style={[styles.cardImage, kind === 'phone' && styles.cardImagePhone]} />{selected && <View style={styles.check}><Text weight="bold" color={color.text.onAction}>✓</Text></View>}<Text variant="caption" weight="bold" color={selected ? color.brand.orange : color.text.heading} style={styles.cardLabel}>{tx(item.labelKo, item.labelEn)}</Text></Pressable>; })}</View>
            <Text accessibilityRole={feedback ? 'alert' : undefined} variant="caption" color={feedback ? color.state.danger : color.text.muted} style={styles.selectionHint}>{feedback ?? tx(`${draft.preferences.length}개 선택됨 · 최대 3개`, `${draft.preferences.length} selected · up to 3`)}</Text>
          </>
        )}
      </Section>
      <View style={styles.paceSection}>
        <Text variant="title" weight="bold">{tx('여행 기분', 'Trip pace')}</Text>
        <Text variant="caption" color={color.text.muted}>{tx('하루 일정의 밀도를 골라주세요. 고르지 않으면 균형 있게로 진행돼요.', "Choose how packed each day should be. We'll use Balanced if you skip this.")}</Text>
        <View style={styles.paceGrid}>{PACE_OPTIONS.map((opt) => { const selected = draft.paceLevel === opt.value; return <Pressable key={opt.value} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => update({ paceLevel: opt.value })} style={[styles.paceCard, selected && styles.paceCardSelected]}><Text weight="bold" color={selected ? color.brand.orange : color.text.heading}>{tx(opt.ko, opt.en)}</Text><Text variant="caption" color={color.text.muted}>{tx(opt.koDesc, opt.enDesc)}</Text></Pressable>; })}</View>
      </View>
      </View>}
      <View style={[styles.detailColumn, kind === 'tablet' && styles.detailColumnWide]}>
        {(kind === 'tablet' || panelIndex === 1) && <Section title={tx('좋아하는 분위기', 'Mood you like')} description={tx('여러 개를 선택해도 좋아요.', 'Feel free to pick more than one.')} skipped={draft.preferenceAnswerStatus.atmosphere === 'SKIPPED'} onSkip={() => skipAndAdvance('atmosphere', { atmospheres: [] })}><Chips options={ATMOSPHERES.map(([key, ko, en]) => [key, tx(ko, en)] as const)} values={draft.atmospheres} desktop={kind === 'tablet'} onChange={(atmospheres) => { update({ atmospheres }); setStatus('atmosphere', atmospheres.length ? 'SELECTED' : 'UNKNOWN'); }} /></Section>}
        {(kind === 'tablet' || panelIndex === 2) && <Section title={tx('로컬성', 'Local feel')} description={tx('현지인 공간을 얼마나 좋아하나요?', 'How much do you enjoy local, non-touristy spots?')} skipped={draft.preferenceAnswerStatus.locality === 'SKIPPED'} onSkip={() => skipAndAdvance('locality', { localityLevel: null })}><Scale label={tx('로컬성', 'Local feel')} value={draft.localityLevel} low={tx('대표 명소', 'Famous spots')} high={tx('현지인 공간', 'Local spots')} desktop={kind === 'tablet'} onChange={(localityLevel) => { update({ localityLevel }); setStatus('locality', 'SELECTED'); if (kind === 'phone') advancePanel(); }} /></Section>}
        {(kind === 'tablet' || panelIndex === 3) && <Section title={tx('조용한 곳 선호', 'Preference for quiet places')} description={tx('혼잡을 피하고 싶은 정도를 알려주세요.', 'Tell us how much you want to avoid crowds.')} skipped={draft.preferenceAnswerStatus.quietness === 'SKIPPED'} onSkip={() => skipAndAdvance('quietness', { quietLevel: null })}><Scale label={tx('조용함', 'Quietness')} value={draft.quietLevel} low={tx('상관없음', "Doesn't matter")} high={tx('매우 선호', 'Strongly prefer')} desktop={kind === 'tablet'} onChange={(quietLevel) => { update({ quietLevel }); setStatus('quietness', 'SELECTED'); if (kind === 'phone') advancePanel(); }} /></Section>}
        {(kind === 'tablet' || panelIndex === 4) && <Section title={tx('관광지 선호', 'Tourist spot preference')} description={tx('유명한 명소와 숨은 곳 중 어느 쪽인가요?', 'Do you prefer famous landmarks or hidden gems?')} skipped={draft.preferenceAnswerStatus.touristPreference === 'SKIPPED'} onSkip={() => skipAndAdvance('touristPreference', { touristLevel: null })}><Scale label={tx('관광지 선호', 'Tourist spot preference')} value={draft.touristLevel} low={tx('숨은 곳', 'Hidden gems')} high={tx('대표 관광지', 'Famous landmarks')} desktop={kind === 'tablet'} onChange={(touristLevel) => { update({ touristLevel }); setStatus('touristPreference', 'SELECTED'); if (kind === 'phone') advancePanel(); }} /></Section>}
        {(kind === 'tablet' || panelIndex === 5) && <Section title={tx('음식 취향', 'Food preferences')} description={tx('먹고 싶은 음식을 모두 골라주세요.', 'Pick all the foods you want to try.')} skipped={draft.preferenceAnswerStatus.foodPreference === 'SKIPPED'} onSkip={() => skipAndAdvance('foodPreference', { foods: [] })}>
          {foodConflictNotice && <View accessibilityRole="alert" style={styles.foodConflictNotice}>
            <Text variant="caption" weight="bold" color={color.state.danger}>{tx(`${foodConflictNotice} 선택이 이용 조건과 겹쳐 자동으로 해제됐어요.`, `${foodConflictNotice} was deselected because it conflicts with your conditions.`)}</Text>
            <Pressable accessibilityRole="button" onPress={clearFoodConflictNotice}><Text variant="caption" weight="bold">{tx('닫기', 'Dismiss')}</Text></Pressable>
          </View>}
          <FoodChips options={FOODS} values={draft.foods} allergies={draft.allergyStatus === 'VALUES' ? draft.allergies : []} dietTypes={draft.dietStatus === 'VALUES' ? draft.dietTypes : []} desktop={kind === 'tablet'} onChange={(foods) => { update({ foods }); setStatus('foodPreference', foods.length ? 'SELECTED' : 'UNKNOWN'); }} />
        </Section>}
        {kind === 'phone' && <View style={styles.detailNav}>
          <Pressable accessibilityRole="button" accessibilityState={{ disabled: panelIndex === 0 }} disabled={panelIndex === 0} onPress={() => setPanelIndex((value) => Math.max(0, value - 1))} style={[styles.detailNavButton, panelIndex === 0 && styles.detailNavButtonDisabled]}><Text variant="caption" weight="bold">{tx('이전', 'Back')}</Text></Pressable>
          <Text variant="caption" weight="bold" color={color.text.muted}>{panelIndex + 1} / 6</Text>
          {panelIndex < 5 && <Pressable accessibilityRole="button" onPress={() => setPanelIndex((value) => Math.min(5, value + 1))} style={styles.detailNavButton}><Text variant="caption" weight="bold">{tx('다음 항목', 'Next')}</Text></Pressable>}
        </View>}
      </View>
      </Animated.View>
    </View>
    <View style={styles.mustVisitSection}>
      <Text variant="title" weight="bold">{tx('꼭 가고 싶은 장소', 'Places you must visit')}</Text>
      <Text variant="caption" color={color.text.muted}>{tx(`최대 ${MUST_VISIT_MAX}곳까지 추가할 수 있어요. 일정에 반드시 포함돼요.`, `Add up to ${MUST_VISIT_MAX} places — they'll always be included in your itinerary.`)}</Text>
      <TextInput accessibilityLabel={tx('장소 검색', 'Search places')} value={mustVisitQuery} onChangeText={handleMustVisitChange} placeholder={tx('예: 감천문화마을', 'e.g. Gamcheon Culture Village')} placeholderTextColor={color.text.muted} style={styles.mustVisitInput} />
      {mustVisitSearching && <Text accessibilityLiveRegion="polite" variant="caption" color={color.text.muted}>{tx('검색 중…', 'Searching…')}</Text>}
      {mustVisitResults.length > 0 && <View accessibilityRole="list" style={styles.mustVisitList}>
        {mustVisitResults.map((item) => <Pressable key={item.placeId} accessibilityRole="button" accessibilityLabel={tx(`${item.nameKo} 추가`, `Add ${item.nameKo}`)} onPress={() => addMustVisit(item)} style={({ pressed }) => [styles.mustVisitItem, pressed && styles.mustVisitItemPressed]}>
          <Text weight="bold">{bilingualPlaceName(item.nameKo, item.nameEn)}</Text>
          <Text variant="caption" color={color.text.muted}>{item.address}</Text>
        </Pressable>)}
      </View>}
      {!mustVisitSearching && mustVisitSearched && mustVisitResults.length === 0 && <Text accessibilityLiveRegion="polite" variant="caption" color={color.text.muted}>{tx('검색 결과가 없습니다.', 'No results.')}</Text>}
      {mustVisitNotice && <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{mustVisitNotice}</Text>}
      {draft.mustVisitPlaces.length > 0 && <View style={styles.mustVisitChips}>
        {draft.mustVisitPlaces.map((place) => <View key={place.placeId} style={styles.mustVisitChip}>
          <Text weight="bold">{bilingualPlaceName(place.nameKo, place.nameEn)}</Text>
          <Pressable accessibilityRole="button" accessibilityLabel={tx(`${place.nameKo} 빼기`, `Remove ${place.nameKo}`)} onPress={() => removeMustVisit(place.placeId)} style={styles.mustVisitRemove}><Text weight="bold" color={color.text.muted}>×</Text></Pressable>
        </View>)}
      </View>}
      {mustVisitWarningList.map((warning) => <Text key={warning.key} accessibilityRole="alert" variant="caption" color={color.state.danger} style={styles.mustVisitWarning}>{tx(`${warning.nameA}·${warning.nameB}가 ${warning.km}km 떨어져 있어 하루에 함께 넣기 어려워요.`, `${warning.nameA} and ${warning.nameB} are ${warning.km}km apart — hard to fit in one day.`)}</Text>)}
    </View>
    {kind === 'phone' && <View style={styles.mobileActions}>
      <Pressable accessibilityRole="button" accessibilityState={{ disabled: panelIndex === 0 }} disabled={panelIndex === 0} onPress={() => goToPanel(panelIndex - 1)} style={[styles.previousLink, panelIndex === 0 && styles.detailNavButtonDisabled]}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('이전 질문', 'Previous question')}</Text></Pressable>
      {(panelIndex === 0 || panelIndex === 1) && <Button accessibilityRole="button" label={tx('선택 완료', 'Done')} disabled={!multiSelectReady} containerStyle={styles.inlineCta} onPress={() => advancePanel()} />}
      {panelIndex === 5 && <Button accessibilityRole="button" label={tx('취향 입력 완료', 'Finish preferences')} disabled={!multiSelectReady} containerStyle={styles.inlineCta} onPress={next} />}
    </View>}
    {kind === 'tablet' && <Button accessibilityRole="button" label={tx('다음 단계', 'Continue')} containerStyle={styles.cta} onPress={next} />}
  </Screen></PlanDesktopShell>;
}

const styles = StyleSheet.create({
  canvas: { backgroundColor: color.brand.ivory }, topBar: { minHeight: 44, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 36, height: 36, borderRadius: radius.full, backgroundColor: color.surface.subtle, alignItems: 'center', justifyContent: 'center' }, logo: { width: 86, height: 22 }, stepPill: { paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.subtle }, headingRow: { marginTop: spacing[4], gap: spacing[3] }, subtitle: { marginTop: spacing[1] }, skipAll: { alignSelf: 'flex-end', minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[3] }, questionProgress: { gap: spacing[2], marginBottom: spacing[3] }, questionMeta: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, questionDots: { flexDirection: 'row', gap: spacing[2] }, questionDot: { flex: 1, height: 4, borderRadius: radius.full, backgroundColor: color.surface.field }, questionDotCurrent: { backgroundColor: color.brand.orange }, questionDotAnswered: { opacity: 0.72, backgroundColor: color.brand.orange }, answerSummary: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[1] }, answerChip: { minHeight: 32, justifyContent: 'center', paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.subtle }, content: { gap: spacing[4] }, contentWide: { flexDirection: 'row', alignItems: 'flex-start' }, animatedContent: { width: '100%' }, animatedContentWide: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[4] }, section: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border }, sectionHeader: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: spacing[2] }, sectionCopy: { flex: 1, gap: spacing[1] }, skip: { minHeight: 44, justifyContent: 'center' },
  imageGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3] }, imageCard: { position: 'relative', width: '47%', borderRadius: radius.md, overflow: 'hidden', borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card }, imageCardPhone: { width: '47%' }, imageCardWide: { width: '30%' }, imageCardSelected: { borderWidth: 2, borderColor: color.brand.orange, backgroundColor: color.surface.warm }, cardImage: { width: '100%', height: 110 }, cardImagePhone: { height: 88 }, cardLabel: { textAlign: 'center', paddingVertical: spacing[2] }, check: { position: 'absolute', top: spacing[2], right: spacing[2], width: 24, height: 24, borderRadius: radius.full, backgroundColor: color.brand.orange, alignItems: 'center', justifyContent: 'center' }, selectionHint: { textAlign: 'center' }, detailColumn: { gap: spacing[4] }, detailColumnWide: { width: 360, flexGrow: 0, flexShrink: 0, flexBasis: 'auto' }, categoryColumn: { flex: 1, width: 0, minWidth: 0, gap: spacing[4] }, detailNav: { display: 'none' }, detailNavButton: { minHeight: 44, minWidth: 72, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, detailNavButtonDisabled: { opacity: 0.35 }, mobileActions: { marginTop: spacing[4], flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, previousLink: { minWidth: 86, minHeight: 48, alignItems: 'center', justifyContent: 'center' }, inlineCta: { flex: 1, marginTop: 0, backgroundColor: color.brand.navy }, chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, foodConflictNotice: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], marginBottom: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg }, chip: { minHeight: 44, borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, paddingHorizontal: spacing[3], alignItems: 'center', justifyContent: 'center' }, chipDesktop: { borderColor: color.surface.border, backgroundColor: color.surface.subtle }, selected: { backgroundColor: color.action.primary, borderColor: color.action.primary }, selectedDesktop: { backgroundColor: color.surface.warm, borderColor: color.brand.orange }, foodChipWrap: { gap: spacing[1] }, chipBlocked: { opacity: 0.5 }, scale: { gap: spacing[2] }, scaleLabels: { flexDirection: 'row', justifyContent: 'space-between' }, scalePoints: { flexDirection: 'row', justifyContent: 'space-between', borderRadius: radius.full, backgroundColor: color.surface.soft, padding: spacing[1] }, scalePointsDesktop: { backgroundColor: color.surface.subtle }, scalePoint: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' }, scalePointSelected: { backgroundColor: color.action.primary }, scalePointSelectedDesktop: { backgroundColor: color.brand.orange }, paceSection: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border }, paceGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, paceCard: { minWidth: '100%', gap: spacing[1], padding: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.brand.ivory }, paceCardSelected: { borderWidth: 2, borderColor: color.brand.orange, backgroundColor: color.surface.warm }, cta: { marginTop: spacing[4], backgroundColor: color.brand.navy },
  mustVisitSection: { gap: spacing[2], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  mustVisitInput: { minHeight: 48, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.brand.ivory, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[3] },
  mustVisitList: { gap: spacing[1], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, padding: spacing[2] },
  mustVisitItem: { minHeight: 48, justifyContent: 'center', gap: 2, borderRadius: radius.sm, paddingHorizontal: spacing[2], paddingVertical: spacing[1] },
  mustVisitItemPressed: { backgroundColor: color.surface.tint },
  mustVisitChips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  mustVisitChip: { flexDirection: 'row', alignItems: 'center', gap: spacing[1], minHeight: 40, paddingLeft: spacing[3], paddingRight: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.warm },
  mustVisitRemove: { width: 28, height: 28, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' },
  mustVisitWarning: { lineHeight: 18 },
});
