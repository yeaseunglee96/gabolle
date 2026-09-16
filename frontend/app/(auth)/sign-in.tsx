import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter, type Href } from 'expo-router';
import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { loginWithOAuth } from '@/auth/oauth';
import { navigateAfterOAuthComplete } from '@/auth/oauthNavigation';
import type { OAuthProvider } from '@/auth/authApi';
import { resolveDestination, savePendingReturnTo } from '@/auth/pendingReturnTo';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { SocialProviderIcon } from '@/components/SocialProviderIcon';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';

// 🔴 401 을 password/social 로 나눠서 말한다. 소셜 버튼에는 애초에 비밀번호가 없으니
// "비밀번호가 틀렸다" 는 말은 거짓이고, 이 문구 하나가 실제 사고를 가렸다 — 2026-09-07,
// 백엔드의 challenge/refresh/logout 엔드포인트가 통째로 사라져 소셜 로그인이 전부 401을
// 받았는데, 화면은 계속 "이메일 또는 비밀번호가 올바르지 않아요" 라고만 보여줬다
// (jaehyeon 님, -689 사고 보고). Spring 이 없는 경로를 401 로 접는다는 것도 여기 남긴다 —
// 다음에 소셜 버튼에서 이 문구가 뜨면 비밀번호가 아니라 엔드포인트 존재부터 의심한다.
function errorMessage(cause: unknown, tx: (ko: string, en: string) => string, context: 'password' | 'social') {
  if (cause instanceof ApiClientError && cause.status === 429) return tx('요청이 너무 많아요. 잠시 후 다시 시도해 주세요.', 'Too many attempts. Please try again shortly.');
  if (cause instanceof ApiClientError && cause.code === 'EMAIL_NOT_VERIFIED') return tx('이메일 인증을 마친 뒤 로그인해 주세요.', 'Verify your email before signing in.');
  if (cause instanceof ApiClientError && cause.status === 401) {
    return context === 'password'
      ? tx('이메일 또는 비밀번호가 올바르지 않아요.', 'The email or password is incorrect.')
      : tx('소셜 로그인을 완료하지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not complete social sign-in. Please try again shortly.');
  }
  return cause instanceof ApiClientError ? cause.message : tx('로그인하지 못했어요.', 'Unable to sign in.');
}
export default function SignIn() {
  const router = useRouter(); const { returnTo, passwordReset } = useLocalSearchParams<{ returnTo?: string; passwordReset?: string }>(); const { signIn, acceptTokens } = useAuth();
  const { tx } = useI18n();
  const { kind } = useLayout();
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [show, setShow] = useState(false);
  // S15P21E201-1087 — 엔터키로 다음 칸으로 넘어간다. 예전에는 엔터가 아무것도 안 해서,
  // 칸을 옮길 때마다 자판을 내리고 다음 칸을 손으로 눌러야 했다.
  const emailRef = useRef<TextInput>(null);
  const passwordRef = useRef<TextInput>(null);
  const [busy, setBusy] = useState(false); const [provider, setProvider] = useState<OAuthProvider | null>(null); const [feedback, setFeedback] = useState<{ danger: boolean; text: string } | null>(passwordReset === 'success' ? { danger: false, text: tx('비밀번호가 변경됐어요. 새 비밀번호로 로그인해 주세요.', 'Your password was changed. Sign in with your new password.') } : null);
  const eligible = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) && password.length > 0;
  useEffect(() => { void savePendingReturnTo(returnTo); }, [returnTo]);
  async function submit() { if (!eligible || busy || provider) return; setBusy(true); setFeedback(null); try { await signIn(email, password); router.replace((await resolveDestination(returnTo)) as Href); } catch (e) { setFeedback({ danger: true, text: errorMessage(e, tx, 'password') }); } finally { setBusy(false); } }
  async function social(next: OAuthProvider) {
    if (busy || provider) return;
    setProvider(next);
    setFeedback(null);
    try {
      // 웹에서는 loginWithOAuth가 현재 페이지를 제공자 화면으로 그대로 넘긴다
      // (S15P21E201-830) — 이 아래는 실행되지 않고, 완료 뒤 분기는
      // oauth/[provider]/callback.tsx가 같은 navigateAfterOAuthComplete로 이어받는다.
      const result = await loginWithOAuth(next, returnTo);
      await navigateAfterOAuthComplete({ result, provider: next, returnTo, router, acceptTokens });
    } catch (e) {
      setFeedback({ danger: true, text: errorMessage(e, tx, 'social') });
    } finally {
      setProvider(null);
    }
  }
  return <Screen scroll wide style={styles.screen}><View style={[styles.loginLayout, kind === 'tablet' && styles.loginLayoutWide]}>
      {kind === 'tablet' && <View style={styles.webIntro}><Eyebrow>{tx('가볼래 계정', 'GABOLLE Account')}</Eyebrow><Text variant="display" weight="bold" color={color.text.onAction} style={styles.webIntroTitle}>{tx('여행의 설렘은 그대로,\n일정은 안전하게', 'Keep the excitement,\nsave every plan.')}</Text><Text variant="body" color={color.text.onDarkMuted}>{tx('저장한 부산 여행과 동행자 일정을 어디서든 이어보세요.', 'Continue your saved Busan trips and shared plans anywhere.')}</Text></View>}
    <View style={styles.panel}>
    <Pressable accessibilityRole="link" accessibilityLabel={tx('GABOLLE 홈으로 이동', 'Go to the GABOLLE home')} onPress={() => router.replace(kind === 'phone' ? '/home' : '/')} style={({ pressed }) => [styles.logoLink, pressed && styles.pressed]}>
      <Image source={require('../../assets/brand/gabolle-logo-hd.png')} resizeMode="contain" accessibilityIgnoresInvertColors style={styles.logo} />
    </Pressable>
    <Text variant="display" weight="bold" style={styles.title}>{tx('로그인', 'Sign in')}</Text>
    <Text color={color.text.body} style={styles.subtitle}>{tx('부산 여행을 시작해볼까요?', 'Ready to explore Busan?')}</Text>
    <View style={styles.form}>
      <View style={styles.field}><Text variant="caption" weight="bold">{tx('이메일', 'Email')}</Text><View style={styles.inputRow}><TextInput accessibilityLabel={tx('이메일', 'Email')} autoCapitalize="none" autoComplete="email" keyboardType="email-address" textContentType="username" returnKeyType="next" onSubmitEditing={() => passwordRef.current?.focus()} submitBehavior="submit" ref={emailRef} value={email} onChangeText={setEmail} placeholder="name@example.com" placeholderTextColor={color.text.muted} style={styles.inputWithClear} />{email.length > 0 && <Pressable accessibilityRole="button" accessibilityLabel={tx('이메일 지우기', 'Clear email')} onPress={() => setEmail('')} style={styles.clear}><Text variant="body" color={color.text.muted}>✕</Text></Pressable>}</View></View>
      <View style={styles.field}><Text variant="caption" weight="bold">{tx('비밀번호', 'Password')}</Text><View style={styles.passwordRow}><TextInput accessibilityLabel={tx('비밀번호', 'Password')} autoCapitalize="none" autoComplete="current-password" textContentType="password" returnKeyType="go" ref={passwordRef} secureTextEntry={!show} value={password} onChangeText={setPassword} onSubmitEditing={() => void submit()} placeholder={tx('비밀번호', 'Password')} placeholderTextColor={color.text.muted} style={styles.passwordInput} />{password.length > 0 && <Pressable accessibilityRole="button" accessibilityLabel={tx('비밀번호 지우기', 'Clear password')} onPress={() => setPassword('')} style={styles.clear}><Text variant="body" color={color.text.muted}>✕</Text></Pressable>}<Pressable accessibilityRole="button" accessibilityLabel={show ? tx('비밀번호 숨기기', 'Hide password') : tx('비밀번호 보이기', 'Show password')} accessibilityState={{ selected: show }} onPress={() => setShow(!show)} style={styles.eye}><Text variant="caption">{show ? tx('숨김', 'Hide') : tx('보기', 'Show')}</Text></Pressable></View></View>
      <Pressable accessibilityRole="link" style={styles.forgot} onPress={() => router.push('/forgot-password')}><Text variant="caption" color={color.action.primary}>{tx('비밀번호를 잊으셨나요?', 'Forgot your password?')}</Text></Pressable>
      {feedback && <Card><Text accessibilityRole="alert" variant="caption" color={feedback.danger ? color.state.danger : color.text.body}>{feedback.text}</Text></Card>}
      <Button accessibilityRole="button" accessibilityState={{ disabled: !eligible || busy || !!provider, busy }} label={busy ? tx('로그인 중…', 'Signing in…') : tx('로그인', 'Sign in')} disabled={!eligible || busy || !!provider} onPress={() => void submit()} />
      {/* 🔴 S15P21E201-1116 — returnTo 로 되돌려 보내지 않는다.

          전에는 isSafeReturnPath(returnTo) ? returnTo : '/home' 이었다. 그런데 returnTo 가
          로그인을 요구하는 화면(/me/* 는 ProtectedRoute 로 막혀 있다)이면 고리가 닫힌다 —
          보호 화면 → 로그인 → 둘러보기 → 보호 화면 → 로그인 … 앱을 강제 종료하는 것 말고
          빠져나올 방법이 없었다(2026-09-16 iOS 실기기에서 확인).

          isSafeReturnPath 는 「안전한 주소인가」만 본다. /sign-in·/sign-up 을 예외로 둔 것을
          보면 자기 자신으로 돌아오는 고리는 이미 의식하고 있었지만, 보호 화면을 거쳐 한 칸
          건너 돌아오는 고리는 놓쳤다.

          이 버튼의 뜻은 「로그인 없이 볼 수 있는 곳으로 가겠다」이지 「원래 가려던 곳으로
          가겠다」가 아니다. returnTo 는 로그인에 성공했을 때만 쓴다(resolveDestination). */}
      <Pressable accessibilityRole="button" accessibilityHint={tx('로그인 없이 홈과 주요 기능을 둘러봅니다.', 'Browse the home screen and core features without signing in.')} style={styles.guest} onPress={() => router.replace('/home')}><Text variant="body" weight="bold" color={color.action.primary}>{tx('비회원으로 둘러보기', 'Browse as guest')}</Text></Pressable>
    </View>
    <View style={styles.divider}><View style={styles.line} /><Text variant="caption">{tx('또는', 'or')}</Text><View style={styles.line} /></View>
    <View style={styles.socials}>{([
      // 외국인 관광객이 주 사용자라 계정 보유 가능성이 높은 순서로 둔다 — 구글·애플은
      // 외국에서도 흔한 글로벌 계정, 카카오·네이버는 한국 전용 계정이라 관광객은
      // 어차피 새로 만들어야 한다(둘 사이 순서는 무의미하니 그대로 카카오·네이버 순).
      { item: 'google', name: 'Google', backgroundColor: '#ffffff', textColor: '#202124', borderColor: '#dadce0' },
      { item: 'apple', name: 'Apple', backgroundColor: '#000000', textColor: '#ffffff', borderColor: '#000000' },
      { item: 'kakao', name: 'Kakao', backgroundColor: '#fee500', textColor: '#191919', borderColor: '#fee500' },
      { item: 'naver', name: 'Naver', backgroundColor: '#03c75a', textColor: '#ffffff', borderColor: '#03c75a' },
    ] as const).map(({ item, name, backgroundColor, textColor, borderColor }) => { const action = tx(`${name}로 계속하기`, `Continue with ${name}`); return <Pressable key={item} accessibilityRole="button" accessibilityLabel={action} accessibilityState={{ disabled: busy || !!provider, busy: provider === item }} disabled={busy || !!provider} onPress={() => void social(item)} style={({ pressed }) => [styles.social, { backgroundColor, borderColor }, pressed && styles.pressed]}><View accessible={false} style={styles.socialContent}><View style={styles.socialMark}><SocialProviderIcon provider={item} /></View><Text weight="bold" color={textColor}>{provider === item ? tx('연결 중…', 'Connecting…') : action}</Text></View></Pressable>; })}</View>
    {provider && <ActivityIndicator accessibilityLabel={tx('소셜 로그인 처리 중', 'Processing social sign-in')} color={color.action.primary} />}
    <Pressable accessibilityRole="link" style={[styles.signup, styles.signupTop]} onPress={() => router.push({ pathname: '/sign-up', params: returnTo ? { returnTo } : {} })}><Text variant="caption">{tx('처음이신가요? ', 'New here? ')}<Text variant="caption" weight="bold" color={color.brand.orange}>{tx('회원가입', 'Create an account')}</Text></Text></Pressable>
  </View></View></Screen>;
}
const styles = StyleSheet.create({ screen: { alignItems: 'center', justifyContent: 'center' }, loginLayout: { width: '100%', alignItems: 'center' }, loginLayoutWide: { flexDirection: 'row', alignItems: 'stretch', gap: spacing[8] }, webIntro: { flex: 1, minHeight: 680, justifyContent: 'center', gap: spacing[4], padding: spacing[8], borderRadius: radius.lg, backgroundColor: color.brand.navy }, webIntroTitle: { fontSize: 38, lineHeight: 48 }, panel: { flex: 1, width: '100%', maxWidth: 420, justifyContent: 'center', paddingVertical: spacing[3] }, logoLink: { minWidth: 120, minHeight: 44, alignSelf: 'center', alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm }, logo: { width: 120, height: 44 }, title: { textAlign: 'center', marginTop: spacing[3] }, subtitle: { textAlign: 'center', marginTop: spacing[2], marginBottom: spacing[6] }, form: { gap: spacing[3] }, field: { gap: spacing[2] }, input: { minHeight: 52, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4] }, inputRow: { minHeight: 52, flexDirection: 'row', alignItems: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card }, inputWithClear: { flex: 1, minWidth: 0, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4], paddingVertical: spacing[3] }, passwordRow: { minHeight: 52, flexDirection: 'row', alignItems: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card }, passwordInput: { flex: 1, minWidth: 0, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4], paddingVertical: spacing[3] }, clear: { minWidth: 36, minHeight: 44, alignItems: 'center', justifyContent: 'center' }, eye: { minWidth: 52, minHeight: 44, alignItems: 'center', justifyContent: 'center' }, forgot: { minHeight: 44, alignSelf: 'flex-end', justifyContent: 'center' }, guest: { minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.action.primary, backgroundColor: color.surface.card }, divider: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginVertical: spacing[6] }, line: { flex: 1, height: 1, backgroundColor: color.surface.field }, socials: { gap: spacing[3] }, social: { minHeight: 52, borderRadius: radius.md, borderWidth: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4] }, socialContent: { width: '100%', flexDirection: 'row', alignItems: 'center', justifyContent: 'center' }, socialMark: { position: 'absolute', left: 0, width: 24, height: 24 }, pressed: { opacity: .75 }, signup: { minHeight: 44, alignItems: 'center', justifyContent: 'center' }, signupTop: { marginTop: spacing[6] } });
