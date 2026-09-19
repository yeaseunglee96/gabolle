// 온보딩 ③ 취향 다섯 — 로컬성·조용함·관광지·음식·경사.
// 세 질문(spend-profile.tsx) 바로 다음 단계이고, 틀은 그 화면을 그대로 따른다.
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { enterApp } from '@/auth/enterApp';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { FOODS } from '@/plan/foodConflicts';
import {
  countTasteAnswers,
  getTasteProfile,
  putTasteProfile,
  TASTE_QUESTIONS,
  type TasteAnswers,
  type TasteChanges,
  type TasteKey,
  type TasteValue,
} from '@/preferences/tasteProfile';

// 여행 만들기 취향 화면(app/(plan)/taste.tsx)의 advancePanel 과 같은 값이다. 고른 것이
// 눈에 남을 만큼은 머물고, 기다린다는 느낌은 안 드는 길이다.
const ADVANCE_MS = 220;

function Dots({ step, settled }: { step: number; settled: Set<number> }) {
  return <View style={styles.dots}>
    {TASTE_QUESTIONS.map((question, index) => (
      <View
        key={question.key}
        style={[
          styles.dot,
          index === step && styles.dotCurrent,
          index !== step && settled.has(index) && styles.dotSettled,
        ]}
      />
    ))}
  </View>;
}

// 넓은 화면에서 고른 것을 주황으로 바꾼다. 폰에서는 남색이다 — 인계 문서가 정한 것이고
// 까닭은 바탕이 다르기 때문이다. 폰은 아이보리 바탕 위에 바로 놓이고, 넓은 화면은 흰 카드
// 안에 들어가서 남색이 너무 무겁다.
function Scale({ label, value, low, high, desktop, onChange }: { label: string; value: number | undefined; low: string; high: string; desktop: boolean; onChange: (value: number) => void }) {
  const { tx } = useI18n();
  return <View accessibilityRole="radiogroup" accessibilityLabel={label}>
    <View style={styles.scaleEnds}>
      <Text variant="caption" color={color.text.muted}>{low}</Text>
      <Text variant="caption" color={color.text.muted}>{high}</Text>
    </View>
    <View style={[styles.scaleTrack, desktop && styles.scaleTrackDesktop]}>
      {[1, 2, 3, 4, 5].map((point) => (
        <Pressable
          key={point}
          accessibilityRole="radio"
          accessibilityLabel={tx(`${label} ${point}단계`, `${label} level ${point}`)}
          accessibilityState={{ selected: value === point }}
          onPress={() => onChange(point)}
          style={[styles.scalePoint, value === point && (desktop ? styles.scalePointSelectedDesktop : styles.scalePointSelected)]}
        >
          <Text weight="bold" color={value === point ? color.text.onAction : color.text.body}>{point}</Text>
        </Pressable>
      ))}
    </View>
  </View>;
}

function FoodChips({ values, desktop, onChange }: { values: string[]; desktop: boolean; onChange: (values: string[]) => void }) {
  const { tx } = useI18n();
  return <View style={styles.chips}>
    {FOODS.map(([code, labelKo, labelEn]) => {
      const selected = values.includes(code);
      return <Pressable
        key={code}
        accessibilityRole="checkbox"
        accessibilityState={{ checked: selected }}
        onPress={() => onChange(selected ? values.filter((value) => value !== code) : [...values, code])}
        style={[styles.chip, selected && (desktop ? styles.chipSelectedDesktop : styles.chipSelected)]}
      >
        <Text weight="bold" color={selected ? (desktop ? color.action.secondary : color.text.onAction) : color.text.heading}>{tx(labelKo, labelEn)}</Text>
      </Pressable>;
    })}
  </View>;
}

export default function TasteProfileScreen() {
  const router = useRouter();
  const { tx } = useI18n();
  const { accessToken, ready } = useAuth();
  const { width } = useWindowDimensions();
  // 1024 이상 — 사이드바가 들어가는 폭(breakpoints.ts 의 표). 이 화면에는 사이드바가
  // 없지만, 그 폭부터 한 열로 늘어진 문항이 읽기 어려워지는 것은 같다.
  const wide = isAtLeast(width, 'lg');
  const [step, setStep] = useState(0);
  const [answers, setAnswers] = useState<TasteAnswers>({});
  const [settled, setSettled] = useState<Set<number>>(new Set());
  const [foodDraft, setFoodDraft] = useState<string[]>([]);
  const [checking, setChecking] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState<number | null>(null);
  const advanceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => { if (advanceTimer.current) clearTimeout(advanceTimer.current); }, []);

  // 이미 답한 계정이면 다시 묻지 않는다 — 서버 상태가 유일한 기준이다(spend-profile 과
  // 같은 원칙). 경로가 아직 없는 서버에서도 화면은 뜬다. 물어보고 저장이 안 되는 것이
  // 첫 실행에서 빨간 화면을 보는 것보다 낫다.
  useEffect(() => {
    if (!ready) return;
    if (!accessToken) { enterApp(router, '/home'); return; }
    let active = true;
    void getTasteProfile(accessToken).then((saved) => {
      if (!active) return;
      if (countTasteAnswers(saved) > 0) { enterApp(router, '/home'); return; }
      setChecking(false);
    }).catch(() => { if (active) setChecking(false); });
    return () => { active = false; };
  }, [ready, accessToken, router]);

  const finish = async (finalAnswers: TasteAnswers) => {
    if (submitting) return;
    const answered = Object.entries(finalAnswers).filter(([, value]) => value !== undefined);
    setSubmitting(true);
    try {
      if (answered.length > 0) {
        await putTasteProfile(Object.fromEntries(answered) as TasteChanges, accessToken);
      }
    } catch {
      // 저장에 실패해도 이 화면에 사람을 가둬 두지 않는다 — 마이페이지에서 다시 답할 수
      // 있다. spend-profile 이 같은 자리에서 같은 선택을 한다.
    } finally {
      setSubmitting(false);
      setDone(answered.length);
    }
  };

  const goNext = (next: TasteAnswers, index: number) => {
    setSettled((prev) => new Set(prev).add(index));
    if (index + 1 < TASTE_QUESTIONS.length) setStep(index + 1);
    else void finish(next);
  };

  const answer = (key: TasteKey, value: TasteValue, index: number) => {
    const next = { ...answers, [key]: value };
    setAnswers(next);
    if (advanceTimer.current) clearTimeout(advanceTimer.current);
    advanceTimer.current = setTimeout(() => goNext(next, index), ADVANCE_MS);
  };

  // 건너뛰기는 그 답을 지운다. 앞 단계로 돌아가 건너뛰면 아까 고른 값이 남아 있으면
  // 안 된다 — 화면은 건너뛴 것으로 보이는데 저장은 되는 일이 생긴다.
  const skipQuestion = (key: TasteKey, index: number) => {
    const next = { ...answers };
    delete next[key];
    setAnswers(next);
    // 음식은 고르는 중간 상태를 따로 들고 있다. 그것도 같이 비워야 한다 — 안 그러면
    // 「이전」으로 돌아왔을 때 칩은 골라진 채인데 답은 지워진, 서로 안 맞는 화면이 된다.
    if (key === 'foods') setFoodDraft([]);
    goNext(next, index);
  };

  if (checking) {
    return <Screen style={styles.centerScreen}><ActivityIndicator color={color.action.primary} /></Screen>;
  }

  if (done !== null) {
    return <Screen scroll style={styles.screen}>
      <View style={styles.doneBody}>
        <Image source={require('../../assets/mascot/dongbaek-idle.png')} style={styles.mascot} resizeMode="contain" />
        <Text variant="display" weight="bold" style={styles.doneTitle}>
          {done > 0
            ? tx(`취향 ${done}개를 기억했어요`, `Saved ${done} preference${done > 1 ? 's' : ''}`)
            : tx('괜찮아요, 나중에 답해도 돼요', 'No problem — you can answer later')}
        </Text>
        <Text color={color.text.body} style={styles.doneTitle}>
          {tx('여행을 만들 때 미리 채워 드려요. 마이페이지 › 여행 취향에서 언제든 바꿀 수 있어요.',
            'We will fill these in when you plan a trip. You can change them any time in My page › Travel preferences.')}
        </Text>
        <Button label={tx('홈으로', 'Go home')} containerStyle={styles.doneCta} onPress={() => enterApp(router, '/home')} />
      </View>
    </Screen>;
  }

  const question = TASTE_QUESTIONS[step];

  const skipLink = <Pressable
    accessibilityRole="button"
    accessibilityLabel={tx(question.skip.ko, question.skip.en)}
    disabled={submitting}
    onPress={() => skipQuestion(question.key, step)}
    style={styles.skipQuestion}
  >
    <Text weight="bold" color={color.text.muted}>{tx(question.skip.ko, question.skip.en)}</Text>
  </Pressable>;

  const backLink = step > 0
    ? <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 질문으로', 'Previous question')} disabled={submitting} onPress={() => setStep(step - 1)} style={styles.backLink}>
        <Text weight="bold" color={color.brand.navy}>{tx('‹ 이전', '‹ Back')}</Text>
      </Pressable>
    : <View />;

  return <Screen scroll style={styles.screen}>
    <View style={wide ? styles.column : undefined}>
    <View style={styles.topBar}>
      <BrandLogoLink href="/home" imageStyle={styles.logo} />
      <View style={styles.stepPill}>
        <Text variant="caption" weight="bold" color={color.text.onAction}>{`${step + 1} / ${TASTE_QUESTIONS.length}`}</Text>
      </View>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('전체 건너뛰기', 'Skip all')} disabled={submitting} onPress={() => void finish(answers)} style={styles.skipAll}>
        <Text variant="caption" weight="bold" color={color.text.muted}>{tx('전체 건너뛰기', 'Skip all')}</Text>
      </Pressable>
    </View>

    <Dots step={step} settled={settled} />

    {/* 넓은 화면에서는 문항 한 덩어리를 카드에 담는다. 담지 않으면 1440 폭에서 글자 몇
        줄이 허공에 떠 있는 것처럼 보인다 — 폭을 좁히는 것만으로는 안 된다.
    */}
    <View style={wide ? styles.card : undefined}>
      {step === 0 && <View style={styles.heading}>
        <Text variant="display" weight="bold">{tx('여행 취향을 5개만 여쭤볼게요', 'Just 5 questions about your travel taste')}</Text>
        <Text color={color.text.body}>{tx('보통 어떤 여행을 좋아하시는지 알면 추천 순서가 달라져요. 건너뛰셔도 돼요.',
          'Knowing what you usually enjoy changes the order of our recommendations. Feel free to skip.')}</Text>
      </View>}

      <Text variant="title" weight="bold" style={styles.question}>{tx(question.title.ko, question.title.en)}</Text>

      {question.kind === 'scale' && <Scale
        label={tx(question.title.ko, question.title.en)}
        value={answers[question.key]}
        low={tx(question.low.ko, question.low.en)}
        high={tx(question.high.ko, question.high.en)}
        desktop={wide}
        onChange={(value) => answer(question.key, value, step)}
      />}

      {question.kind === 'multi' && <View style={styles.multi}>
        <FoodChips values={foodDraft} desktop={wide} onChange={setFoodDraft} />
        <Button
          label={tx('선택 완료', 'Done')}
          disabled={foodDraft.length === 0}
          containerStyle={styles.multiCta}
          onPress={() => answer('foods', foodDraft, step)}
        />
      </View>}

      {question.kind === 'choice' && <View style={[styles.options, wide && styles.optionsDesktop]}>
        {question.options.map((option) => (
          <Pressable
            key={option.value}
            accessibilityRole="button"
            accessibilityLabel={tx(option.label.ko, option.label.en)}
            disabled={submitting}
            onPress={() => answer('slope', option.value, step)}
            style={({ pressed }) => [styles.option, wide && styles.optionDesktop, pressed && styles.optionPressed, answers.slope === option.value && styles.optionPressed]}
          >
            <Text weight="bold">{tx(option.label.ko, option.label.en)}</Text>
            {option.desc && <Text variant="caption" color={color.text.body} style={styles.optionDesc}>{tx(option.desc.ko, option.desc.en)}</Text>}
          </Pressable>
        ))}
      </View>}

      {/* 넓은 화면에서는 「이전」과 「건너뛰기」가 카드 안 같은 줄에 있다. 폰에서는
          건너뛰기가 문항 바로 아래(엄지가 닿는 자리), 이전은 맨 아래다.
      */}
      {wide && <View style={styles.cardFooter}>{backLink}{skipLink}</View>}
    </View>

    {!wide && skipLink}

    <View style={styles.footer}>
      {!wide && backLink}
      {submitting && <ActivityIndicator color={color.action.primary} />}
    </View>
    </View>
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.canvas },
  centerScreen: { alignItems: 'center', justifyContent: 'center' },
  // 넓은 화면에서 읽는 열. Screen 이 이미 720 으로 묶고 있지만 문항 하나를 읽기에는
  // 그것도 넓다 — 눈이 줄 끝에서 다음 줄 앞으로 돌아오는 거리가 멀어진다.
  column: { width: '100%', maxWidth: 560, alignSelf: 'center' },
  card: { marginTop: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  cardFooter: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: spacing[4], paddingTop: spacing[3], borderTopWidth: 1, borderTopColor: color.surface.border },
  // marginTop — Screen 의 기본 paddingTop 만으로는 전역 언어 배지(우측 상단 절대좌표)를
  // 못 피한다. spend-profile 과 같은 값으로 맞춘다.
  topBar: { minHeight: 44, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] },
  logo: { width: 88, height: 24 },
  stepPill: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.brand.navy },
  skipAll: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[2] },
  dots: { flexDirection: 'row', gap: 6, marginTop: spacing[3] },
  dot: { flex: 1, height: 4, borderRadius: radius.full, backgroundColor: color.surface.field },
  dotCurrent: { backgroundColor: color.action.secondary },
  // 답했거나 건너뛴 단계. 현재 단계와 구별되게 흐리다 — 같은 색이면 어디까지 왔는지 모른다.
  dotSettled: { backgroundColor: color.action.secondary, opacity: 0.5 },
  heading: { gap: spacing[2], marginTop: spacing[6], marginBottom: spacing[6] },
  question: { marginTop: spacing[6], marginBottom: spacing[4] },
  scaleEnds: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: spacing[2] },
  scaleTrack: { flexDirection: 'row', justifyContent: 'space-between', padding: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
  scaleTrackDesktop: { backgroundColor: color.surface.subtle },
  scalePoint: { width: 56, height: 56, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' },
  scalePointSelected: { backgroundColor: color.action.secondary },
  scalePointSelectedDesktop: { backgroundColor: color.action.secondary },
  multi: { gap: spacing[4] },
  multiCta: { minHeight: 46 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 44, justifyContent: 'center', paddingHorizontal: 14, borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field },
  chipSelected: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  chipSelectedDesktop: { backgroundColor: color.surface.tint, borderColor: color.action.secondary },
  options: { gap: spacing[3] },
  // 넓은 화면에서는 두 카드를 한 줄에 나란히 — 세로로 쌓으면 카드 하나가 화면 폭을 다 먹는다.
  optionsDesktop: { flexDirection: 'row' },
  option: { minHeight: 64, gap: spacing[1], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  optionDesktop: { flex: 1, minHeight: 80, borderRadius: radius.md },
  optionPressed: { borderColor: color.action.secondary, backgroundColor: color.surface.tint },
  optionDesc: { lineHeight: 18 },
  skipQuestion: { minHeight: 44, alignItems: 'center', justifyContent: 'center', marginTop: spacing[4] },
  footer: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: spacing[4] },
  backLink: { minHeight: 44, justifyContent: 'center' },
  doneBody: { alignItems: 'center', gap: spacing[3], marginTop: spacing[8] },
  mascot: { width: 96, height: 96 },
  doneTitle: { textAlign: 'center' },
  doneCta: { minHeight: 46, alignSelf: 'stretch', marginTop: spacing[4] },
});
