// 소셜 인증은 끝났지만 처음 보는 계정이라(SIGNUP_REQUIRED) 가입을 마무리하는 화면.
// sign-in.tsx 의 social 이 signupTicket 과 provider 가 준 정보(prefill)를 params 로 넘겨준다.
// 티켓은 10분짜리라 이 화면에서 값을 채우는 동안 만료될 수 있고, 만료·재사용된 티켓은
// 종류를 가리지 않고 전부 OAUTH_TICKET_INVALID 로 온다(서버 쪽 의도) — 그때는 소셜 로그인을
// 처음부터 다시 밟게 한다., jaehyeon 님 !288 계약.
import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter, type Href } from 'expo-router';
import { enterApp } from '@/auth/enterApp';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { completeOAuthSignup, type OAuthLinkRequiredResult, type OAuthProvider, type SignupLanguage } from '@/auth/authApi';
import { consumePendingReturnTo, isSafeReturnPath } from '@/auth/pendingReturnTo';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { webInputNoOutline } from '@/design/webGlobalStyles';
import { useI18n } from '@/i18n';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { localizeMessage } from '@/i18n/messages';

// 기본값 /home — sign-in.tsx 의 resolveDestination 과 같은 이유(jaehyeon 님 제안).
async function resolveDestination(returnTo?: string) {
  if (isSafeReturnPath(returnTo)) return returnTo;
  return (await consumePendingReturnTo()) ?? '/home';
}

export default function OAuthSignup() {
  const router = useRouter();
  const { tx } = useI18n();
  const { acceptTokens } = useAuth();
  const { setLanguage: setOnboardingLanguage } = useOnboardingPreferences();
  const params = useLocalSearchParams<{
    provider?: OAuthProvider; signupTicket?: string; email?: string; displayName?: string; language?: string; emailProvided?: string; returnTo?: string;
  }>();
  const [displayName, setDisplayName] = useState(params.displayName ?? '');
  // 포커스 = 붉은 2px 선 — 웹의 검은 기본 외곽선을 끈 대신이다(회원가입 시안과 같게, S15P21E201-1518).
  const [nameFocused, setNameFocused] = useState(false);
  const [language, setLanguage] = useState<SignupLanguage>(params.language === 'EN' ? 'EN' : 'KO');
  const [ageAccepted, setAgeAccepted] = useState(false);
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [privacyAccepted, setPrivacyAccepted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);

  const emailProvided = params.emailProvided === 'true';
  const nameValid = displayName.trim().length >= 1 && displayName.trim().length <= 30;
  const canSubmit = !!params.signupTicket && nameValid && ageAccepted && termsAccepted && privacyAccepted && !submitting;

  async function submit() {
    if (!canSubmit || !params.signupTicket) return;
    setSubmitting(true);
    setError(null);
    try {
      const result = await completeOAuthSignup({
        signupTicket: params.signupTicket,
        displayName: displayName.trim(),
        language,
        ageGateAccepted: ageAccepted,
        termsAccepted,
        privacyAccepted,
      });
      await acceptTokens(result);
      enterApp(router, (await resolveDestination(params.returnTo)) as Href);
    } catch (cause) {
      if (cause instanceof ApiClientError && cause.code === 'OAUTH_TICKET_INVALID') {
        setExpired(true);
      } else if (cause instanceof ApiClientError && cause.code === 'OAUTH_ACCOUNT_LINK_REQUIRED' && cause.data) {
        const link = cause.data as OAuthLinkRequiredResult;
        router.replace({ pathname: '/oauth-link', params: { provider: link.provider, linkTicket: link.linkTicket, maskedEmail: link.maskedEmail, ...(params.returnTo ? { returnTo: params.returnTo } : {}) } });
      } else {
        setError(cause instanceof ApiClientError ? cause.message : tx('가입을 완료하지 못했어요.', 'Could not finish signing up.'));
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (!params.signupTicket || expired) {
    return (
      <Screen>
        <View style={styles.expiredBody}>
          <Text variant="display" weight="bold">{tx('로그인 정보가 만료됐어요', 'Your sign-in info expired')}</Text>
          <Text color={color.text.body}>{tx('소셜 로그인을 처음부터 다시 시작해 주세요.', 'Please start social sign-in again from the beginning.')}</Text>
          <Button compact label={tx('로그인으로 돌아가기', 'Back to sign-in')} onPress={() => router.replace('/sign-in')} />
        </View>
      </Screen>
    );
  }

  return (
    <Screen scroll>
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.replace('/sign-in')} style={styles.backLink}>
          <Text variant="body" weight="bold">{tx('← 뒤로', '← Back')}</Text>
        </Pressable>
        <BrandLogoLink enter href="/home" imageStyle={styles.logo} />
      </View>
      <Text variant="display" weight="bold" style={styles.title}>{tx('회원가입 완료하기', 'Finish signing up')}</Text>
      <Text variant="body" color={color.text.body} style={styles.subtitle}>{tx('소셜 인증은 끝났어요. 몇 가지만 더 확인할게요.', "You're verified — just a couple more details.")}</Text>

      <View style={styles.form}>
        <View style={styles.field}>
          <Text variant="caption" weight="bold">{tx('이메일', 'Email')}</Text>
          <Text variant="body" color={color.text.body}>{emailProvided && params.email ? params.email : tx('이 계정은 이메일을 제공하지 않았어요 — 비밀번호 재설정은 쓸 수 없어요.', "This account didn't share an email — password reset won't be available.")}</Text>
        </View>

        <View style={styles.field}>
          <Text variant="caption" weight="bold">{tx('이름', 'Name')}</Text>
          <TextInput accessibilityLabel={tx('이름', 'Name')} autoComplete="name" maxLength={30} onFocus={() => setNameFocused(true)} onBlur={() => setNameFocused(false)} onChangeText={setDisplayName} placeholder={tx('1~30자', '1-30 characters')} placeholderTextColor={color.text.muted} style={[styles.input, nameFocused && styles.inputFocused]} value={displayName} />
        </View>

        <View style={styles.field}>
          <Text variant="caption" weight="bold">{tx('언어', 'Language')}</Text>
          <View accessibilityRole="radiogroup" style={styles.languageRow}>
            {(['KO', 'EN'] as const).map((value) => (
              <Pressable key={value} accessibilityRole="radio" accessibilityState={{ selected: language === value }} onPress={() => { setLanguage(value); setOnboardingLanguage(value === 'KO' ? 'ko' : 'en'); }} style={[styles.language, language === value && styles.languageSelected]}>
                <Text variant="body" weight="bold" color={language === value ? color.text.onAction : color.text.heading}>{value === 'KO' ? '한국어' : 'English'}</Text>
              </Pressable>
            ))}
          </View>
        </View>

        <View style={styles.agreements}>
          <CheckRow checked={ageAccepted} label={tx('만 14세 이상입니다.', 'I am 14 years of age or older.')} onPress={() => setAgeAccepted((value) => !value)} />
          <CheckRow checked={termsAccepted} label={tx('이용약관에 동의합니다. (필수)', 'I agree to the Terms of Service. (required)')} onPress={() => setTermsAccepted((value) => !value)} />
          <CheckRow checked={privacyAccepted} label={tx('개인정보 처리방침에 동의합니다. (필수)', 'I agree to the Privacy Policy. (required)')} onPress={() => setPrivacyAccepted((value) => !value)} />
        </View>

        {error && <Card><Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{localizeMessage(tx, error)}</Text></Card>}
        <Button label={submitting ? tx('가입 중…', 'Signing up…') : tx('가입 완료', 'Finish signing up')} disabled={!canSubmit} onPress={() => void submit()} />
      </View>
    </Screen>
  );
}

function CheckRow({ checked, label, onPress }: { checked: boolean; label: string; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="checkbox" accessibilityState={{ checked }} onPress={onPress} style={styles.checkRow}>
      <View style={[styles.checkbox, checked && styles.checkboxChecked]}>{checked && <Text variant="caption" weight="bold" color={color.text.onAction}>✓</Text>}</View>
      <Text variant="body" style={styles.checkLabel}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[2] },
  logo: { width: 176, height: 32 },
  backLink: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center' },
  title: { marginTop: spacing[3] },
  subtitle: { marginTop: spacing[1], marginBottom: spacing[6] },
  form: { gap: spacing[4] },
  field: { gap: spacing[2] },
  // 🔴 웹의 검은 포커스 외곽선을 끈다 — 대신 inputFocused 의 붉은 선이 포커스를 보여 준다.
  input: { minHeight: 52, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4], ...webInputNoOutline },
  inputFocused: { borderColor: color.action.outline, borderWidth: 2 },
  languageRow: { flexDirection: 'row', gap: spacing[2] },
  language: { flex: 1, alignItems: 'center', padding: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  languageSelected: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  agreements: { gap: spacing[3], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.card },
  checkRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  checkLabel: { flex: 1 },
  checkbox: { width: 24, height: 24, alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm, borderWidth: 1.5, borderColor: color.surface.field },
  checkboxChecked: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  expiredBody: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing[3], padding: spacing[6] },
});
