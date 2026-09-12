// 온보딩 세 질문 — 오는 교통·숙소·식사 (S15P21E201-807, docs/COLDSTART-THREE-QUESTIONS.md).
// 계정(USER) 첫 실행에서만 쓴다 — 여행(TRIP) 조건 입력 쪽 재사용은 별도 티켓 범위다.
//
// 🔴 이 화면의 답은 순위 필터가 아니라 가중치 배수로만 쓰인다(문서 1.3) — 그래서 여기서는
// 후보를 줄이는 어떤 로직도 없다. 답을 서버에 그대로 저장하는 것이 전부다. 배수 크기는
// 아직 안 정해져 1.0(변화 없음)이고, 그 크기를 정하는 것은 이 화면의 몫이 아니다(문서 5.2).
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { getSpendProfile, MEAL_VARIES_CODE, putSpendProfile, SPEND_HEADER, SPEND_QUESTIONS, type SpendAnswers } from '@/onboarding/spendProfile';

export default function SpendProfileScreen() {
  const router = useRouter();
  const { tx } = useI18n();
  const { accessToken, ready } = useAuth();
  const [step, setStep] = useState(0);
  const [answers, setAnswers] = useState<SpendAnswers>({});
  const [checking, setChecking] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  // 직접 주소로 들어오는 등 이미 답한 계정이면 다시 묻지 않는다 — 서버 상태가 유일한 기준이다
  // (로컬 플래그를 따로 두지 않는다, home.tsx 의 1회성 안내와 같은 원칙).
  useEffect(() => {
    if (!ready) return;
    if (!accessToken) { router.replace('/home'); return; }
    let active = true;
    void getSpendProfile(accessToken).then((result) => {
      if (!active) return;
      if (result.status !== 'UNKNOWN') { router.replace('/home'); return; }
      setChecking(false);
    }).catch(() => { if (active) setChecking(false); });
    return () => { active = false; };
  }, [ready, accessToken, router]);

  const finish = async (finalAnswers: SpendAnswers) => {
    if (submitting) return;
    setSubmitting(true);
    try {
      await putSpendProfile(finalAnswers, accessToken);
    } catch {
      // 저장에 실패해도 이 화면에 사람을 가둬 두지 않는다 — 다음에 홈에 들어올 때
      // 상태가 여전히 UNKNOWN이면 다시 물어볼 기회가 있다.
    } finally {
      router.replace('/home');
    }
  };

  const choose = (code: string | null) => {
    const question = SPEND_QUESTIONS[step];
    const next: SpendAnswers = code ? { ...answers, [question.key]: code } : answers;
    setAnswers(next);
    if (step + 1 < SPEND_QUESTIONS.length) setStep(step + 1);
    else void finish(next);
  };

  if (checking) {
    return <Screen style={styles.centerScreen}><ActivityIndicator color={color.brand.orange} /></Screen>;
  }

  const question = SPEND_QUESTIONS[step];
  const header = SPEND_HEADER.USER;

  return <Screen scroll style={styles.screen}>
    <View style={styles.topBar}>
      <BrandLogoLink href="/home" imageStyle={styles.logo} />
      <View style={styles.stepPill}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx(`${step + 1} / ${SPEND_QUESTIONS.length}`, `${step + 1} / ${SPEND_QUESTIONS.length}`)}</Text></View>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('전체 건너뛰기', 'Skip all')} disabled={submitting} onPress={() => void finish(answers)} style={styles.skipAll}>
        <Text variant="caption" weight="bold" color={color.text.muted}>{tx('전체 건너뛰기', 'Skip all')}</Text>
      </Pressable>
    </View>

    {step === 0 && <View style={styles.heading}><Text variant="display" weight="bold">{tx(header.titleKo, header.titleEn)}</Text><Text color={color.text.body}>{tx(header.bodyKo, header.bodyEn)}</Text></View>}

    <Text variant="title" weight="bold" style={styles.question}>{question.titleKo('USER')}</Text>

    <View style={styles.options}>
      {question.options.map((option) => (
        <Pressable key={option.code} accessibilityRole="button" accessibilityLabel={tx(option.labelKo, option.labelEn)} disabled={submitting} onPress={() => choose(option.code)} style={({ pressed }) => [styles.option, pressed && styles.optionPressed]}>
          <Text weight="bold">{tx(option.labelKo, option.labelEn)}</Text>
          {option.descKo && <Text variant="caption" color={color.text.body} style={styles.optionDesc}>{tx(option.descKo, option.descEn ?? '')}</Text>}
        </Pressable>
      ))}
      {question.key === 'meal' ? (
        <Pressable accessibilityRole="button" accessibilityLabel={tx(question.skipLabelKo, question.skipLabelEn)} disabled={submitting} onPress={() => choose(MEAL_VARIES_CODE)} style={({ pressed }) => [styles.option, pressed && styles.optionPressed]}>
          <Text weight="bold">{tx(question.skipLabelKo, question.skipLabelEn)}</Text>
        </Pressable>
      ) : (
        <Pressable accessibilityRole="button" accessibilityLabel={tx(question.skipLabelKo, question.skipLabelEn)} disabled={submitting} onPress={() => choose(null)} style={styles.skipQuestion}>
          <Text weight="bold" color={color.text.muted}>{tx(question.skipLabelKo, question.skipLabelEn)}</Text>
        </Pressable>
      )}
    </View>

    <View style={styles.footer}>
      {step > 0 && <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 질문으로', 'Previous question')} disabled={submitting} onPress={() => setStep(step - 1)} style={styles.backLink}><Text weight="bold" color={color.brand.navy}>{tx('‹ 이전', '‹ Back')}</Text></Pressable>}
      {submitting && <ActivityIndicator color={color.brand.orange} />}
    </View>
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  centerScreen: { alignItems: 'center', justifyContent: 'center' },
  // 🔴 marginTop — Screen 의 기본 paddingTop(24) 만으로는 전역 언어 배지(우측 상단
  //    절대좌표)를 못 피한다(home.tsx·app-intro.tsx 에서 실사용 리포트로 확인된 것과
  //    같은 자리). "전체 건너뛰기" 가 배지와 겹치던 결함을 여기도 같은 값으로 고친다.
  topBar: { minHeight: 44, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] },
  logo: { width: 88, height: 24 },
  stepPill: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.brand.navy },
  skipAll: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[2] },
  heading: { gap: spacing[2], marginTop: spacing[6], marginBottom: spacing[6] },
  question: { marginTop: spacing[4], marginBottom: spacing[4] },
  options: { gap: spacing[3] },
  option: { minHeight: 64, gap: spacing[1], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  optionPressed: { borderColor: color.brand.orange, backgroundColor: color.surface.tint },
  optionDesc: { lineHeight: 18 },
  skipQuestion: { minHeight: 44, alignItems: 'center', justifyContent: 'center', marginTop: spacing[1] },
  footer: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: spacing[6] },
  backLink: { minHeight: 44, justifyContent: 'center' },
});
