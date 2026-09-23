import { useEffect, useMemo, useRef, useState } from 'react';
import { Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import Animated, { FadeInRight, FadeOutLeft, ReduceMotion } from 'react-native-reanimated';

import { ApiClientError } from '@/api/client';
import { resendEmailVerification, signup, type Registration, type SignupLanguage } from '@/auth/authApi';
import { isSafeReturnPath, savePendingReturnTo } from '@/auth/pendingReturnTo';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { webInputNoOutline } from '@/design/webGlobalStyles';
import { useLayout } from '@/layout/useLayout';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const SPECIAL_CHARACTER_PATTERN = /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?`~]/;

const PANEL_LABELS = [['이메일', 'Email'], ['비밀번호', 'Password'], ['이름·언어', 'Name · Language'], ['약관 동의', 'Agreements']] as const;

// 넓은 화면 왼쪽 판 — 부산 야경 (S15P21E201-1518 시안 6번). 글자는 사진 윗부분 검은 하늘에
// 얹히므로 어둡게 덮는 막 없이도 읽힌다. 원본은 6192×4128·18MB 라 긴 변 2000 으로 줄여 넣었다.
const introPhoto = require('../../assets/home/busan-night.jpg');

type FieldKey = 'email' | 'password' | 'confirm' | 'name';

export default function SignUp() {
  const router = useRouter();
  const { returnTo } = useLocalSearchParams<{ returnTo?: string }>();
  const { kind } = useLayout();
  const { tx } = useI18n();
  const { language: onboardingLanguage, setLanguage: setOnboardingLanguage } = useOnboardingPreferences();
  const passwordRef = useRef<TextInput>(null);
  const passwordConfirmRef = useRef<TextInput>(null);
  const displayNameRef = useRef<TextInput>(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [language, setLanguage] = useState<SignupLanguage>(onboardingLanguage === 'ko' ? 'KO' : 'EN');
  const [ageAccepted, setAgeAccepted] = useState(false);
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [privacyAccepted, setPrivacyAccepted] = useState(false);
  // 🔴 「입력한 뒤에만」 빨갛게 — 처음 연 빈 칸은 오류가 아니다(시안 4번). 그래서 손댄 칸을
  //    따로 센다. 기준은 떠날 때(onBlur)가 아니라 **바꿀 때(onChangeText)** 다 — 시안과 같다.
  const [touched, setTouched] = useState<Record<FieldKey, boolean>>({ email: false, password: false, confirm: false, name: false });
  const [focused, setFocused] = useState<FieldKey | null>(null);
  const touch = (key: FieldKey) => setTouched((current) => (current[key] ? current : { ...current, [key]: true }));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [duplicateEmail, setDuplicateEmail] = useState(false);
  const [registration, setRegistration] = useState<Registration | null>(null);
  const [resending, setResending] = useState(false);
  const [resent, setResent] = useState(false);
  const [panelIndex, setPanelIndex] = useState(0);
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
  const panelValid = [emailValid, passwordValid && passwordMatches, nameValid, ageAccepted && termsAccepted && privacyAccepted];
  const goToPanel = (index: number) => setPanelIndex(Math.max(0, Math.min(PANEL_LABELS.length - 1, index)));

  // 회원가입 버튼이 왜 잠겼나 — 버튼 바로 위 회색 상자에 적는다(시안 2번). canSubmit 과 같은
  // 조건을 사람 말로 푼 것이라, 이 목록이 비면 버튼이 열린다(보내는 중만 빼고).
  //
  // 🔴 폰도 같은 목록을 쓴다. 시안은 폰에 「동의 셋만」 나온다고 적었는데, 그건 앞 칸의 「다음」이
  //    이미 막아서 나머지가 늘 비어 있기 때문이다. 위 진행 점을 눌러 칸을 건너뛰면 앞 칸이 빈 채로
  //    여기 올 수 있다 — 동의 셋만 세면 상자는 사라졌는데 버튼은 잠긴 채가 된다.
  // 🔴 빨강을 쓰지 않는다. 아직 안 채운 것은 오류가 아니다(tokens 규칙 4).
  const blockers = [
    !emailValid && tx('이메일 형식이 올바르지 않아요', 'The email format is not valid'),
    !passwordChecks.length && tx('비밀번호를 8~64자로 입력해 주세요', 'Use 8-64 characters for your password'),
    !passwordChecks.letter && tx('비밀번호에 영문을 넣어 주세요', 'Add a letter to your password'),
    !passwordChecks.number && tx('비밀번호에 숫자를 넣어 주세요', 'Add a number to your password'),
    !passwordChecks.special && tx('비밀번호에 특수문자(!@#$% 등)를 넣어 주세요', 'Add a symbol (!@#$% etc.) to your password'),
    password.length > 0 && !passwordMatches && tx('비밀번호 확인이 일치하지 않아요', 'The password confirmation does not match'),
    !nameValid && tx('이름을 1~30자로 입력해 주세요', 'Enter a name of 1-30 characters'),
    !ageAccepted && tx('만 14세 이상인지 확인해 주세요', 'Confirm that you are 14 or older'),
    !termsAccepted && tx('이용약관에 동의해 주세요', 'Agree to the Terms of Service'),
    !privacyAccepted && tx('개인정보 처리방침에 동의해 주세요', 'Agree to the Privacy Policy'),
  ].filter((item): item is string => Boolean(item));

  // 칸에 문제가 있나 — 🔴 손댄 뒤에, 값이 있을 때만. 빈 칸·처음 연 칸은 기본 모습이다.
  const problem: Record<FieldKey, boolean> = {
    email: (touched.email && email.length > 0 && !emailValid) || duplicateEmail,
    password: touched.password && password.length > 0 && !passwordValid,
    confirm: touched.confirm && passwordConfirm.length > 0 && !passwordMatches,
    name: touched.name && displayName.length > 0 && !nameValid,
  };
  // 테두리·채움은 행(inputRow)이 그린다. 포커스는 붉은 2px 선, 문제는 옅은 붉은 채움.
  const rowStyle = (key: FieldKey) => [styles.inputRow, problem[key] && styles.inputProblem, focused === key && styles.inputFocused];
  const focusProps = (key: FieldKey) => ({ onFocus: () => setFocused(key), onBlur: () => setFocused((current) => (current === key ? null : current)) });

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
            {txf(tx, '%s로 인증 링크를 보냈어요. 링크를 눌러 인증을 마치면 로그인할 수 있습니다.', 'We sent a verification link to %s. Click it to finish verifying and sign in.', registration.email)}
          </Text>
          {/* — 서버 열거값(PENDING_EMAIL_VERIFICATION)을 그대로 찍고 있었다. 읽는 사람에게는 오류 문구로 보인다. 바로 위에서 이미 "인증 링크를 보냈다" 고 말하므로 같은 사실을 상태값으로 한 번 더 적지 않는다. */}
          <Text variant="caption" color={color.text.muted}>{tx(`메일이 안 보이면 스팸함도 확인해 주세요. 링크는 30분 동안 쓸 수 있어요.`, `If you do not see it, check your spam folder. The link works for 30 minutes.`)}</Text>
          {resent && <Text accessibilityRole="alert" variant="caption" color={color.state.success}>{tx('인증 메일을 다시 보냈어요.', 'Verification email resent.')}</Text>}
          {error && <View accessibilityRole="alert" style={styles.errorBox}><ErrorText>{localizeMessage(tx, error)}</ErrorText></View>}
        </View>
        <View style={styles.resultActions}>
          <Button label={resending ? tx('재전송 중…', 'Resending…') : tx('인증 메일 다시 보내기', 'Resend verification email')} variant="tertiary" disabled={resending} onPress={() => void resend()} />
          <Button label={tx('이메일 확인 후 로그인', 'Sign in after verifying')} onPress={() => router.replace({ pathname: '/sign-in', params: returnTo ? { returnTo } : {} })} />
        </View>
      </Screen>
    );
  }

  return (
    <Screen scroll wide>
      <View style={styles.topBar}><Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/sign-in')} style={styles.backLink}><Text variant="body" weight="bold">{tx('← 뒤로', '← Back')}</Text></Pressable><BrandLogoLink href={kind === 'tablet' ? '/' : '/home'} imageStyle={styles.logo} /></View>
      <View style={[styles.columns, kind === 'tablet' && styles.columnsWide]}>
        {kind === 'tablet' && <View style={styles.photoPanel}>
          <Image source={introPhoto} resizeMode="cover" accessible={false} accessibilityIgnoresInvertColors style={styles.photoPanelImage} />
          <Text variant="caption" weight="bold" color={color.text.onDarkMuted}>{tx('가볼래 계정', 'GABOLLE Account')}</Text>
          <Text variant="hero" weight="bold" color={color.text.onAction} style={styles.photoPanelCopy}>{tx('내 여행을 안전하게 저장하세요', 'Keep your trips safely saved')}</Text>
          <Text variant="body" color={color.text.onDarkMuted} style={styles.photoPanelCopy}>{tx('선택한 언어와 여행 조건을 이어서 사용할 수 있어요.', 'Pick up your language and trip details right where you left off.')}</Text>
        </View>}
        <View style={styles.formColumn}>
          <Text variant="display" weight="bold">{tx('회원가입', 'Sign up')}</Text>
          <Text variant="body" style={styles.subtitle}>{tx('여행을 저장하고 어디서든 이어보세요.', 'Save your trip and continue it anywhere.')}</Text>

          {kind === 'phone' && <View style={styles.questionProgress}>
            <View style={styles.questionMeta}><Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx(PANEL_LABELS[panelIndex][0], PANEL_LABELS[panelIndex][1])} {panelIndex + 1} / {PANEL_LABELS.length}</Text></View>
            <View style={styles.questionDots}>{PANEL_LABELS.map(([labelKo, labelEn], index) => <Pressable key={labelKo} accessibilityRole="button" accessibilityLabel={txf(tx, '%s 단계로 이동', 'Go to %s', tx(labelKo, labelEn))} onPress={() => goToPanel(index)} style={[styles.questionDot, index === panelIndex && styles.questionDotCurrent, panelValid[index] && styles.questionDotAnswered]} />)}</View>
          </View>}

          <Animated.View key={kind === 'phone' ? panelIndex : 'desktop'} entering={kind === 'phone' ? FadeInRight.duration(180).reduceMotion(ReduceMotion.System) : undefined} exiting={kind === 'phone' ? FadeOutLeft.duration(120).reduceMotion(ReduceMotion.System) : undefined} style={styles.form}>

          {(kind === 'tablet' || panelIndex === 0) && <Field label={tx('이메일', 'Email')}>
          <View style={rowStyle('email')}>
            <TextInput testID="sign-up-email" accessibilityLabel={tx('이메일', 'Email')} autoFocus={kind === 'phone'} autoCapitalize="none" autoComplete="email" keyboardType="email-address" textContentType="username" returnKeyType="next" onSubmitEditing={() => passwordRef.current?.focus()} submitBehavior="submit" {...focusProps('email')} onChangeText={(value) => { setEmail(value); touch('email'); setDuplicateEmail(false); }} placeholder="name@example.com" placeholderTextColor={color.text.muted} style={styles.inputWithClear} value={email} />
            {email.length > 0 && <Pressable accessibilityRole="button" accessibilityLabel={tx('이메일 지우기', 'Clear email')} onPress={() => setEmail('')} tabIndex={-1} style={styles.clear}><Text variant="body" color={color.text.muted}>✕</Text></Pressable>}
          </View>
          {touched.email && email.length > 0 && !emailValid && <ErrorText>{tx('올바른 이메일 주소를 입력해 주세요.', 'Please enter a valid email address.')}</ErrorText>}
          {duplicateEmail && <View style={styles.inlineRow}><ErrorText>{tx('이미 가입된 이메일이에요.', 'This email is already registered.')}</ErrorText><Pressable accessibilityRole="link" onPress={() => router.push({ pathname: '/sign-in', params: returnTo ? { returnTo } : {} })}><Text variant="caption" weight="bold" color={color.action.secondary}>{tx('로그인하기', 'Sign in')}</Text></Pressable></View>}
        </Field>}

        {(kind === 'tablet' || panelIndex === 1) && <>
        <Field label={tx('비밀번호', 'Password')}>
          <View style={rowStyle('password')}>
            <TextInput testID="sign-up-password" accessibilityLabel={tx('비밀번호', 'Password')} autoFocus={kind === 'phone'} autoCapitalize="none" autoComplete="new-password" textContentType="newPassword" returnKeyType="next" onSubmitEditing={() => passwordConfirmRef.current?.focus()} submitBehavior="submit" ref={passwordRef} {...focusProps('password')} onChangeText={(value) => { setPassword(value); touch('password'); }} placeholder={tx('영문·숫자·특수문자 포함 8~64자', '8-64 characters with letters, numbers, and symbols')} placeholderTextColor={color.text.muted} secureTextEntry style={styles.inputWithClear} value={password} />
            {password.length > 0 && <Pressable accessibilityRole="button" accessibilityLabel={tx('비밀번호 지우기', 'Clear password')} onPress={() => setPassword('')} tabIndex={-1} style={styles.clear}><Text variant="body" color={color.text.muted}>✕</Text></Pressable>}
          </View>
          <View style={styles.ruleRow}><Rule ok={passwordChecks.length} label={tx('8~64자', '8-64 characters')} /><Rule ok={passwordChecks.letter} label={tx('영문', 'Letters')} /><Rule ok={passwordChecks.number} label={tx('숫자', 'Numbers')} /><Rule ok={passwordChecks.special} label={tx('특수문자 (!@#$% 등)', 'Symbols (!@#$% etc.)')} /></View>
        </Field>

        <Field label={tx('비밀번호 확인', 'Confirm password')}>
          <View style={rowStyle('confirm')}>
            <TextInput testID="sign-up-confirm" accessibilityLabel={tx('비밀번호 확인', 'Confirm password')} autoCapitalize="none" autoComplete="new-password" textContentType="newPassword" returnKeyType="next" onSubmitEditing={() => displayNameRef.current?.focus()} submitBehavior="submit" ref={passwordConfirmRef} {...focusProps('confirm')} onChangeText={(value) => { setPasswordConfirm(value); touch('confirm'); }} placeholder={tx('한 번 더 입력하세요', 'Enter it once more')} placeholderTextColor={color.text.muted} secureTextEntry style={styles.inputWithClear} value={passwordConfirm} />
            {passwordConfirm.length > 0 && <Pressable accessibilityRole="button" accessibilityLabel={tx('비밀번호 확인 지우기', 'Clear password confirmation')} onPress={() => setPasswordConfirm('')} tabIndex={-1} style={styles.clear}><Text variant="body" color={color.text.muted}>✕</Text></Pressable>}
          </View>
          {passwordConfirm.length > 0 && <Text variant="caption" color={passwordMatches ? color.state.success : color.state.danger}>{passwordMatches ? tx('비밀번호가 일치해요.', 'Passwords match.') : tx('비밀번호가 일치하지 않아요.', 'Passwords do not match.')}</Text>}
        </Field>
        </>}

        {(kind === 'tablet' || panelIndex === 2) && <>
        <Field label={tx('이름', 'Name')}>
          <View style={rowStyle('name')}>
            <TextInput testID="sign-up-name" accessibilityLabel={tx('이름', 'Name')} autoFocus={kind === 'phone'} autoComplete="name" textContentType="name" returnKeyType="done" ref={displayNameRef} maxLength={30} {...focusProps('name')} onChangeText={(value) => { setDisplayName(value); touch('name'); }} placeholder={tx('1~30자', '1-30 characters')} placeholderTextColor={color.text.muted} style={styles.inputWithClear} value={displayName} />
            {displayName.length > 0 && <Pressable accessibilityRole="button" accessibilityLabel={tx('이름 지우기', 'Clear name')} onPress={() => setDisplayName('')} tabIndex={-1} style={styles.clear}><Text variant="body" color={color.text.muted}>✕</Text></Pressable>}
          </View>
          <Text variant="caption" color={nameValid ? color.state.success : color.text.muted}>{tx(`${displayName.trim().length}/30자`, `${displayName.trim().length}/30`)}</Text>
        </Field>

        <Field label={tx('언어', 'Language')}>
          <View accessibilityRole="radiogroup" style={styles.languageRow}>{(['KO', 'EN'] as const).map((value) => <Pressable accessibilityRole="radio" accessibilityState={{ selected: language === value }} key={value} onPress={() => { setLanguage(value); setOnboardingLanguage(value === 'KO' ? 'ko' : 'en'); }} style={[styles.language, language === value && styles.languageSelected]}><Text variant="body" weight="bold" color={language === value ? color.text.onAction : color.text.heading}>{value === 'KO' ? '한국어' : 'English'}</Text></Pressable>)}</View>
        </Field>
        </>}

        {(kind === 'tablet' || panelIndex === 3) && <View style={styles.agreements}>
          <CheckRow checked={ageAccepted} label={tx('만 14세 이상입니다.', 'I am 14 years of age or older.')} onPress={() => setAgeAccepted((value) => !value)} />
          <CheckRow checked={termsAccepted} label={tx('이용약관에 동의합니다. (필수)', 'I agree to the Terms of Service. (required)')} onPress={() => setTermsAccepted((value) => !value)} />
          <Pressable accessibilityRole="link" onPress={() => router.push('/legal/terms')} style={styles.policyLink}>
            <Text variant="caption" weight="bold" color={color.action.secondary}>{tx('이용약관 보기 ›', 'View Terms of Service ›')}</Text>
          </Pressable>
          <CheckRow checked={privacyAccepted} label={tx('개인정보 처리방침에 동의합니다. (필수)', 'I agree to the Privacy Policy. (required)')} onPress={() => setPrivacyAccepted((value) => !value)} />
          <Pressable accessibilityRole="link" onPress={() => router.push('/legal/privacy')} style={styles.policyLink}>
            <Text variant="caption" weight="bold" color={color.action.secondary}>{tx('개인정보 처리 안내 보기 ›', 'View Privacy Policy ›')}</Text>
          </Pressable>
        </View>}

        {(kind === 'tablet' || panelIndex === 3) && blockers.length > 0 && <View accessibilityLiveRegion="polite" style={styles.blockers}>
          <Text variant="caption" weight="bold" color={color.text.heading}>{tx('회원가입하려면 아래를 마저 채워 주세요', 'To sign up, finish the items below')}</Text>
          {blockers.map((reason) => <Text key={reason} variant="caption" color={color.text.body}>○ {reason}</Text>)}
        </View>}

        {error && <View accessibilityRole="alert" style={styles.errorBox}><ErrorText>{localizeMessage(tx, error)}</ErrorText></View>}

        {kind === 'tablet' ? (
          <Button testID="sign-up-submit" label={submitting ? tx('가입 중…', 'Signing up…') : tx('회원가입', 'Sign up')} disabled={!canSubmit} onPress={() => void submit()} />
        ) : (
          <View style={styles.panelNav}>
            {panelIndex > 0 && <Pressable accessibilityRole="button" onPress={() => goToPanel(panelIndex - 1)} style={styles.panelNavButton}><Text variant="caption" weight="bold">{tx('이전', 'Back')}</Text></Pressable>}
            {panelIndex < PANEL_LABELS.length - 1
              ? <Button testID="sign-up-next" accessibilityRole="button" label={tx('다음', 'Next')} disabled={!panelValid[panelIndex]} containerStyle={styles.panelCta} onPress={() => goToPanel(panelIndex + 1)} />
              : <Button testID="sign-up-submit" accessibilityRole="button" label={submitting ? tx('가입 중…', 'Signing up…') : tx('회원가입', 'Sign up')} disabled={!canSubmit} containerStyle={styles.panelCta} onPress={() => void submit()} />}
          </View>
        )}
          </Animated.View>

        <Button label={tx('비회원으로 둘러보기', 'Browse as guest')} variant="tertiary" containerStyle={styles.guest} onPress={() => router.replace(isSafeReturnPath(returnTo) ? returnTo : '/home')} />
        <Pressable accessibilityRole="link" onPress={() => router.replace({ pathname: '/sign-in', params: returnTo ? { returnTo } : {} })} style={styles.loginLink}><Text variant="body">{tx('이미 계정이 있나요? ', 'Already have an account? ')}<Text variant="body" weight="bold" color={color.action.secondary}>{tx('로그인', 'Sign in')}</Text></Text></Pressable>
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
  logo: { width: 176, height: 32 },
  backLink: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center' },
  columns: { width: '100%' },
  // 사진 판이 폼 높이만큼 늘어나야 하므로 stretch (시안 6번 — 전에는 flex-start 라 판이 제 글자 높이였다).
  columnsWide: { flexDirection: 'row', alignItems: 'stretch', gap: spacing[8] },
  // 글자는 위쪽 — 사진 윗부분이 검은 하늘이라 거기 얹어야 읽힌다. 사진이 뜨기 전에는 먹색 바탕.
  // 🔴 zIndex: 0 — react-native-web 의 Image 는 그림을 z-index -1 로 그린다. 판이 쌓임 문맥이 아니면
  //    그림이 판의 먹색 바탕 «뒤»로 가서 안 보인다 (sign-in.tsx 의 같은 판에서 2026-09-21 실측).
  photoPanel: { flex: 1, minHeight: 280, borderRadius: radius.md, overflow: 'hidden', padding: spacing[8] * 2, justifyContent: 'flex-start', gap: spacing[4], backgroundColor: color.brand.navy, zIndex: 0 },
  photoPanelImage: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, width: '100%', height: '100%' },
  photoPanelCopy: { maxWidth: 440 },
  formColumn: { flex: 1, width: '100%', maxWidth: 480 },
  resultBody: { flex: 1, justifyContent: 'center', gap: spacing[3] },
  resultMark: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.state.success },
  resultCopy: { color: color.text.body },
  resultActions: { gap: spacing[2] },
  form: { marginTop: spacing[6], gap: spacing[4] },
  questionProgress: { gap: spacing[2], marginTop: spacing[4] }, questionMeta: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, questionDots: { flexDirection: 'row', gap: spacing[2] }, questionDot: { flex: 1, height: 4, borderRadius: radius.full, backgroundColor: color.surface.field }, questionDotCurrent: { backgroundColor: color.action.secondary }, questionDotAnswered: { opacity: 0.72, backgroundColor: color.action.secondary },
  // 위 간격은 form 의 gap(16)이 이미 준다 — 여기 16 을 더 두면 이유 상자와 버튼 사이가 32 로 벌어진다(시안: 16 → 4).
  panelNav: { marginTop: spacing[1], flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, panelNavButton: { minWidth: 72, minHeight: 48, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, panelCta: { flex: 1, marginTop: 0 },
  field: { gap: spacing[2] },
  input: { minHeight: 52, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4] },
  inputRow: { minHeight: 52, flexDirection: 'row', alignItems: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  // 🔴 웹의 검은 포커스 외곽선을 끈다. 포커스 표시는 행(inputRow)의 붉은 선(inputFocused)이 대신한다.
  inputWithClear: { flex: 1, minWidth: 0, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4], paddingVertical: spacing[3], ...webInputNoOutline },
  // ✕ 는 tabIndex={-1} — 키보드 탭이 ✕ 에 걸려 이메일 → 비밀번호로 바로 못 가던 것(S15P21E201-1518 피드백).
  //    누르는 것은 그대로 된다. 키보드로는 칸 안에서 지우면 된다.
  clear: { minWidth: 36, minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  // 손댄 뒤 값에 문제가 있을 때 — 선은 경고 글자색, 칸 안은 아주 옅은 붉은빛(dangerBg 는 너무 진하다).
  inputProblem: { borderColor: color.state.danger, backgroundColor: color.state.dangerFieldBg },
  // 포커스 — 붉은 2px 선. 문제 채움은 그대로 둔다(포커스 + 문제 = 붉은 선 + 옅은 채움). 뒤에 와야 선 색이 이긴다.
  inputFocused: { borderColor: color.action.outline, borderWidth: 2 },
  blockers: { gap: spacing[1], paddingVertical: spacing[3], paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.tint },
  guest: { marginTop: spacing[3] },
  inlineRow: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing[2] },
  ruleRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3] },
  languageRow: { flexDirection: 'row', gap: spacing[2] },
  language: { flex: 1, alignItems: 'center', padding: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  languageSelected: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  agreements: { gap: spacing[3], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.card },
  checkRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  checkLabel: { flex: 1 },
  policyLink: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center' },
  checkbox: { width: 24, height: 24, alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm, borderWidth: 1.5, borderColor: color.surface.field },
  checkboxChecked: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  errorBox: { padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.dangerBg },
  loginLink: { minHeight: 44, alignItems: 'center', justifyContent: 'center' },
});
