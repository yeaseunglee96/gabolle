import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { resendEmailVerification, signup, type Registration, type SignupLanguage } from '@/auth/authApi';
import { savePendingReturnTo } from '@/auth/pendingReturnTo';
import { Card } from '@/components/Card';
import { Eyebrow } from '@/components/Eyebrow';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { useI18n } from '@/i18n';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const SPECIAL_CHARACTER_PATTERN = /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?`~]/;

export default function SignUp() {
  const router = useRouter();
  const { returnTo } = useLocalSearchParams<{ returnTo?: string }>();
  const { kind } = useLayout();
  const { tx } = useI18n();
  const { language: onboardingLanguage, setLanguage: setOnboardingLanguage } = useOnboardingPreferences();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [language, setLanguage] = useState<SignupLanguage>(onboardingLanguage === 'ko' ? 'KO' : 'EN');
  const [ageAccepted, setAgeAccepted] = useState(false);
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [privacyAccepted, setPrivacyAccepted] = useState(false);
  const [emailTouched, setEmailTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [duplicateEmail, setDuplicateEmail] = useState(false);
  const [registration, setRegistration] = useState<Registration | null>(null);
  const [resending, setResending] = useState(false);
  const [resent, setResent] = useState(false);
  useEffect(() => { void savePendingReturnTo(returnTo); }, [returnTo]);

  const passwordChecks = useMemo(() => ({
    length: password.length >= 8 && password.length <= 64,
    letter: /[A-Za-z]/.test(password),
    number: /\d/.test(password),
    special: SPECIAL_CHARACTER_PATTERN.test(password),
  }), [password]);
  const emailValid = EMAIL_PATTERN.test(email.trim());
  const nameValid = displayName.trim().length >= 1 && displayName.trim().length <= 30;
  const passwordValid = Object.values(passwordChecks).every(Boolean);
  const passwordMatches = password.length > 0 && password === passwordConfirm;
  const canSubmit = emailValid && nameValid && passwordValid && passwordMatches
    && ageAccepted && termsAccepted && privacyAccepted && !submitting;

  async function submit() {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    setDuplicateEmail(false);
    try {
      const result = await signup({ email, password, displayName, language, ageGateAccepted: ageAccepted, termsAccepted, privacyAccepted });
      setRegistration(result);
    } catch (cause) {
      if (cause instanceof ApiClientError && cause.code === 'EMAIL_ALREADY_EXISTS') {
        setDuplicateEmail(true);
      } else {
        setError(cause instanceof ApiClientError ? cause.message : tx('회원가입을 완료하지 못했어요.', 'Could not complete sign-up.'));
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function resend() {
    if (!registration || resending) return;
    setResending(true);
    setError(null);
    try {
      await resendEmailVerification(registration.email);
      setResent(true);
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : tx('인증 메일을 다시 보내지 못했어요.', 'Could not resend the verification email.'));
    } finally {
      setResending(false);
    }
  }

  if (registration) {
    return (
      <Screen>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('회원가입 화면으로 돌아가기', 'Back to sign-up')} onPress={() => setRegistration(null)} style={styles.backLink}><Text variant="body" weight="bold">{tx('← 이메일 수정', '← Edit email')}</Text></Pressable>
        <View style={styles.resultBody}>
          <View style={styles.resultMark}><Text variant="title" weight="bold" color={color.text.onAction}>✓</Text></View>
          <Text variant="display" weight="bold">{tx('이메일을 확인해 주세요', 'Please check your email')}</Text>
          <Text variant="body" style={styles.resultCopy}>
            {tx(`${registration.email}로 인증 링크를 보냈어요. 링크를 눌러 인증을 마치면 로그인할 수 있습니다.`, `We sent a verification link to ${registration.email}. Click it to finish verifying and sign in.`)}
          </Text>
          {/* S15P21E201-941 — 서버 열거값(PENDING_EMAIL_VERIFICATION)을 그대로 찍고 있었다. 읽는 사람에게는 오류 문구로 보인다. 바로 위에서 이미 "인증 링크를 보냈다" 고 말하므로 같은 사실을 상태값으로 한 번 더 적지 않는다. */}
          <Text variant="caption" color={color.text.muted}>{tx(`메일이 안 보이면 스팸함도 확인해 주세요. 링크는 30분 동안 쓸 수 있어요.`, `If you do not see it, check your spam folder. The link works for 30 minutes.`)}</Text>
          {resent && <Text accessibilityRole="alert" variant="caption" color={color.state.success}>{tx('인증 메일을 다시 보냈어요.', 'Verification email resent.')}</Text>}
          {error && <View accessibilityRole="alert" style={styles.errorBox}><ErrorText>{error}</ErrorText></View>}
        </View>
        <View style={styles.resultActions}>
          <Button label={resending ? tx('재전송 중…', 'Resending…') : tx('인증 메일 다시 보내기', 'Resend verification email')} variant="ghost" disabled={resending} onPress={() => void resend()} />
          <Button label={tx('이메일 확인 후 로그인', 'Sign in after verifying')} onPress={() => router.replace({ pathname: '/sign-in', params: returnTo ? { returnTo } : {} })} />
        </View>
      </Screen>
    );
  }

  return (
    <Screen scroll wide>
      <View style={styles.topBar}><Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.back()} style={styles.backLink}><Text variant="body" weight="bold">{tx('← 뒤로', '← Back')}</Text></Pressable><BrandLogoLink href={kind === 'tablet' ? '/' : '/home'} imageStyle={styles.logo} /></View>
      <View style={[styles.columns, kind === 'tablet' && styles.columnsWide]}>
        {kind === 'tablet' && <Card tinted style={styles.introCard}><Eyebrow>{tx('가볼래 계정', 'GABOLLE Account')}</Eyebrow><Text variant="display" weight="bold">{tx('내 여행을 안전하게 저장하세요', 'Keep your trips safely saved')}</Text><Text variant="body">{tx('선택한 언어와 여행 조건을 이어서 사용할 수 있어요.', 'Pick up your language and trip details right where you left off.')}</Text></Card>}
        <View style={styles.formColumn}>
          <Text variant="display" weight="bold">{tx('회원가입', 'Sign up')}</Text>
          <Text variant="body" style={styles.subtitle}>{tx('여행을 저장하고 어디서든 이어보세요.', 'Save your trip and continue it anywhere.')}</Text>
          <View style={styles.form}>
        <Field label={tx('이메일', 'Email')}>
          <TextInput accessibilityLabel={tx('이메일', 'Email')} autoCapitalize="none" autoComplete="email" keyboardType="email-address" onBlur={() => setEmailTouched(true)} onChangeText={(value) => { setEmail(value); setDuplicateEmail(false); }} placeholder="name@example.com" placeholderTextColor={color.text.muted} style={[styles.input, ((emailTouched && !emailValid) || duplicateEmail) && styles.inputError]} value={email} />
          {emailTouched && !emailValid && <ErrorText>{tx('올바른 이메일 주소를 입력해 주세요.', 'Please enter a valid email address.')}</ErrorText>}
          {duplicateEmail && <View style={styles.inlineRow}><ErrorText>{tx('이미 가입된 이메일이에요.', 'This email is already registered.')}</ErrorText><Pressable accessibilityRole="link" onPress={() => router.push({ pathname: '/sign-in', params: returnTo ? { returnTo } : {} })}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('로그인하기', 'Sign in')}</Text></Pressable></View>}
        </Field>

        <Field label={tx('비밀번호', 'Password')}>
          <TextInput accessibilityLabel={tx('비밀번호', 'Password')} autoCapitalize="none" autoComplete="new-password" onChangeText={setPassword} placeholder={tx('영문·숫자·특수문자 포함 8~64자', '8-64 characters with letters, numbers, and symbols')} placeholderTextColor={color.text.muted} secureTextEntry style={styles.input} value={password} />
          <View style={styles.ruleRow}><Rule ok={passwordChecks.length} label={tx('8~64자', '8-64 characters')} /><Rule ok={passwordChecks.letter} label={tx('영문', 'Letters')} /><Rule ok={passwordChecks.number} label={tx('숫자', 'Numbers')} /><Rule ok={passwordChecks.special} label={tx('특수문자 (!@#$% 등)', 'Symbols (!@#$% etc.)')} /></View>
        </Field>

        <Field label={tx('비밀번호 확인', 'Confirm password')}>
          <TextInput accessibilityLabel={tx('비밀번호 확인', 'Confirm password')} autoCapitalize="none" autoComplete="new-password" onChangeText={setPasswordConfirm} placeholder={tx('한 번 더 입력하세요', 'Enter it once more')} placeholderTextColor={color.text.muted} secureTextEntry style={[styles.input, passwordConfirm.length > 0 && !passwordMatches && styles.inputError]} value={passwordConfirm} />
          {passwordConfirm.length > 0 && <Text variant="caption" color={passwordMatches ? color.state.success : color.state.danger}>{passwordMatches ? tx('비밀번호가 일치해요.', 'Passwords match.') : tx('비밀번호가 일치하지 않아요.', 'Passwords do not match.')}</Text>}
        </Field>

        <Field label={tx('이름', 'Name')}>
          <TextInput accessibilityLabel={tx('이름', 'Name')} autoComplete="name" maxLength={30} onChangeText={setDisplayName} placeholder={tx('1~30자', '1-30 characters')} placeholderTextColor={color.text.muted} style={[styles.input, displayName.length > 0 && !nameValid && styles.inputError]} value={displayName} />
          <Text variant="caption" color={nameValid ? color.state.success : color.text.muted}>{tx(`${displayName.trim().length}/30자`, `${displayName.trim().length}/30`)}</Text>
        </Field>

        <Field label={tx('언어', 'Language')}>
          <View accessibilityRole="radiogroup" style={styles.languageRow}>{(['KO', 'EN'] as const).map((value) => <Pressable accessibilityRole="radio" accessibilityState={{ selected: language === value }} key={value} onPress={() => { setLanguage(value); setOnboardingLanguage(value === 'KO' ? 'ko' : 'en'); }} style={[styles.language, language === value && styles.languageSelected]}><Text variant="body" weight="bold" color={language === value ? color.text.onAction : color.text.heading}>{value === 'KO' ? '한국어' : 'English'}</Text></Pressable>)}</View>
        </Field>

        <View style={styles.agreements}>
          <CheckRow checked={ageAccepted} label={tx('만 14세 이상입니다.', 'I am 14 years of age or older.')} onPress={() => setAgeAccepted((value) => !value)} />
          <CheckRow checked={termsAccepted} label={tx('이용약관에 동의합니다. (필수)', 'I agree to the Terms of Service. (required)')} onPress={() => setTermsAccepted((value) => !value)} />
          <Pressable accessibilityRole="link" onPress={() => router.push('/legal/terms')} style={styles.policyLink}>
            <Text variant="caption" weight="bold" color={color.brand.orange}>{tx('이용약관 보기 ›', 'View Terms of Service ›')}</Text>
          </Pressable>
          <CheckRow checked={privacyAccepted} label={tx('개인정보 처리방침에 동의합니다. (필수)', 'I agree to the Privacy Policy. (required)')} onPress={() => setPrivacyAccepted((value) => !value)} />
          <Pressable accessibilityRole="link" onPress={() => router.push('/legal/privacy')} style={styles.policyLink}>
            <Text variant="caption" weight="bold" color={color.brand.orange}>{tx('개인정보 처리 안내 보기 ›', 'View Privacy Policy ›')}</Text>
          </Pressable>
        </View>

        {error && <View accessibilityRole="alert" style={styles.errorBox}><ErrorText>{error}</ErrorText></View>}
        <Button label={submitting ? tx('가입 중…', 'Signing up…') : tx('회원가입', 'Sign up')} disabled={!canSubmit} onPress={() => void submit()} />
        <Pressable accessibilityRole="link" onPress={() => router.replace({ pathname: '/sign-in', params: returnTo ? { returnTo } : {} })} style={styles.loginLink}><Text variant="body">{tx('이미 계정이 있나요? ', 'Already have an account? ')}<Text variant="body" weight="bold" color={color.brand.orange}>{tx('로그인', 'Sign in')}</Text></Text></Pressable>
          </View>
        </View>
      </View>
    </Screen>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) { return <View style={styles.field}><Text variant="caption" weight="bold" color={color.text.heading}>{label}</Text>{children}</View>; }
function ErrorText({ children }: { children: React.ReactNode }) { return <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{children}</Text>; }
function Rule({ ok, label }: { ok: boolean; label: string }) { return <Text variant="caption" color={ok ? color.state.success : color.text.muted}>{ok ? '✓' : '○'} {label}</Text>; }
function CheckRow({ checked, label, onPress }: { checked: boolean; label: string; onPress: () => void }) { return <Pressable accessibilityRole="checkbox" accessibilityState={{ checked }} onPress={onPress} style={styles.checkRow}><View style={[styles.checkbox, checked && styles.checkboxChecked]}>{checked && <Text variant="caption" weight="bold" color={color.text.onAction}>✓</Text>}</View><Text variant="body" style={styles.checkLabel}>{label}</Text></Pressable>; }

const styles = StyleSheet.create({
  subtitle: { marginTop: spacing[1] },
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[2] },
  logo: { width: 112, height: 32 },
  backLink: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center' },
  columns: { width: '100%' },
  columnsWide: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[8] },
  introCard: { flex: 1, minHeight: 280, justifyContent: 'center', gap: spacing[4] },
  formColumn: { flex: 1, width: '100%', maxWidth: 480 },
  resultBody: { flex: 1, justifyContent: 'center', gap: spacing[3] },
  resultMark: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.state.success },
  resultCopy: { color: color.text.body },
  resultActions: { gap: spacing[2] },
  form: { marginTop: spacing[6], gap: spacing[4] },
  field: { gap: spacing[2] },
  input: { minHeight: 52, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4] },
  inputError: { borderColor: color.state.danger },
  inlineRow: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing[2] },
  ruleRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3] },
  languageRow: { flexDirection: 'row', gap: spacing[2] },
  language: { flex: 1, alignItems: 'center', padding: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  languageSelected: { backgroundColor: color.action.primary, borderColor: color.action.primary },
  agreements: { gap: spacing[3], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.card },
  checkRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  checkLabel: { flex: 1 },
  policyLink: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center' },
  checkbox: { width: 24, height: 24, alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm, borderWidth: 1.5, borderColor: color.surface.field },
  checkboxChecked: { backgroundColor: color.action.primary, borderColor: color.action.primary },
  errorBox: { padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.dangerBg },
  loginLink: { minHeight: 44, alignItems: 'center', justifyContent: 'center' },
});
