// 온보딩 세 질문 — 오는 교통·숙소·식사 (docs/COLDSTART-THREE-QUESTIONS.md).
// 계정(USER) 첫 실행에서만 쓴다 — 여행(TRIP) 조건 입력 쪽 재사용은 별도 티켓 범위다.
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { enterApp } from '@/auth/enterApp';
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
  const [saveFailed, setSaveFailed] = useState(false);
  const [checkFailed, setCheckFailed] = useState(false);
  const [checkAttempt, setCheckAttempt] = useState(0);

  // 직접 주소로 들어오는 등 이미 답한 계정이면 다시 묻지 않는다 — 서버 상태가 유일한 기준이다
  // (로컬 플래그를 따로 두지 않는다, home.tsx 의 1회성 안내와 같은 원칙).
  useEffect(() => {
    if (!ready) return;
    if (!accessToken) { enterApp(router, '/home'); return; }
    let active = true;
    setChecking(true);
    setCheckFailed(false);
    void getSpendProfile(accessToken).then((result) => {
      if (!active) return;
      if (result.status !== 'UNKNOWN') { enterApp(router, '/home'); return; }
      setChecking(false);
    }).catch(() => { if (active) { setChecking(false); setCheckFailed(true); } });
    return () => { active = false; };
  }, [ready, accessToken, router, checkAttempt]);

  const finish = async (finalAnswers: SpendAnswers) => {
    if (submitting) return;
    setSubmitting(true);
    setSaveFailed(false);
    try {
      await putSpendProfile(finalAnswers, accessToken);
      router.replace('/taste-profile');
    } catch {
      // 다음 화면으로 조용히 넘기면 저장된 줄 알고 같은 설문을 반복하게 된다.
      setSaveFailed(true);
    } finally {
      setSubmitting(false);
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
  if (checkFailed) {
    return <Screen scroll style={styles.screen}><View style={styles.heading}>
      <Text accessibilityRole="alert">{tx('저장한 답변을 확인하지 못했어요. 이미 답한 설문을 다시 묻지 않도록 연결을 확인한 뒤 재시도해 주세요.', 'We could not check your saved answers. Please retry so we do not ask you to repeat a completed survey.')}</Text>
      <Button label={tx('다시 확인', 'Check again')} onPress={() => setCheckAttempt((value) => value + 1)} />
      <Button variant="tertiary" label={tx('홈으로', 'Go to home')} onPress={() => enterApp(router, '/home')} />
    </View></Screen>;
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

    {saveFailed && <View style={styles.options}>
      <Text accessibilityRole="alert">{tx('답변을 저장하지 못했어요. 이 화면에서는 선택한 답이 유지돼요. 다시 저장하거나, 저장하지 않고 나중에 답할 수 있어요.', 'Your answers could not be saved. Your selections are kept on this screen. Retry saving, or leave without saving and answer later.')}</Text>
      <Button label={tx('다시 저장', 'Retry saving')} disabled={submitting} onPress={() => void finish(answers)} />
      <Button variant="tertiary" label={tx('저장하지 않고 나중에', 'Leave without saving')} disabled={submitting} onPress={() => enterApp(router, '/home')} />
    </View>}

    <Text variant="title" weight="bold" style={styles.question}>{tx(question.titleKo('USER'), question.titleEn('USER'))}</Text>

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
  // marginTop — Screen 의 기본 paddingTop(24) 만으로는 전역 언어 배지(우측 상단
  // 절대좌표)를 못 피한다(home.tsx·app-intro.tsx 에서 실사용 리포트로 확인된 것과
  // 같은 자리). "전체 건너뛰기" 가 배지와 겹치던 결함을 여기도 같은 값으로 고친다.
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
