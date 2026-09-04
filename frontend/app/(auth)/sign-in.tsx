import { useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter, type Href } from 'expo-router';
import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { loginWithOAuth } from '@/auth/oauth';
import type { OAuthProvider } from '@/auth/authApi';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';

function safeReturnTo(value?: string): Href { return !value || !value.startsWith('/') || value.startsWith('//') || value.includes('://') || value.startsWith('/sign-in') ? '/me' : value as Href; }
function errorMessage(cause: unknown, tx: (ko: string, en: string) => string) {
  if (cause instanceof ApiClientError && cause.status === 429) return tx('요청이 너무 많아요. 잠시 후 다시 시도해 주세요.', 'Too many attempts. Please try again shortly.');
  if (cause instanceof ApiClientError && cause.code === 'EMAIL_NOT_VERIFIED') return tx('이메일 인증을 마친 뒤 로그인해 주세요.', 'Verify your email before signing in.');
  if (cause instanceof ApiClientError && cause.status === 401) return tx('이메일 또는 비밀번호가 올바르지 않아요.', 'The email or password is incorrect.');
  return cause instanceof ApiClientError ? cause.message : tx('로그인하지 못했어요.', 'Unable to sign in.');
}
export default function SignIn() {
  const router = useRouter(); const { returnTo, passwordReset } = useLocalSearchParams<{ returnTo?: string; passwordReset?: string }>(); const { signIn, acceptTokens } = useAuth();
  const { tx } = useI18n();
  const { kind } = useLayout();
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false); const [provider, setProvider] = useState<OAuthProvider | null>(null); const [feedback, setFeedback] = useState<{ danger: boolean; text: string } | null>(passwordReset === 'success' ? { danger: false, text: tx('비밀번호가 변경됐어요. 새 비밀번호로 로그인해 주세요.', 'Your password was changed. Sign in with your new password.') } : null);
  const eligible = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) && password.length > 0;
  async function submit() { if (!eligible || busy || provider) return; setBusy(true); setFeedback(null); try { await signIn(email, password); router.replace(safeReturnTo(returnTo)); } catch (e) { setFeedback({ danger: true, text: errorMessage(e, tx) }); } finally { setBusy(false); } }
  async function social(next: OAuthProvider) { if (busy || provider) return; setProvider(next); setFeedback(null); try { const tokens = await loginWithOAuth(next); await acceptTokens(tokens); router.replace(safeReturnTo(returnTo)); } catch (e) { setFeedback({ danger: true, text: errorMessage(e, tx) }); } finally { setProvider(null); } }
  return <Screen scroll wide style={styles.screen}><View style={[styles.loginLayout, kind === 'tablet' && styles.loginLayoutWide]}>
    {kind === 'tablet' && <View style={styles.webIntro}><Text variant="eyebrow" weight="bold" color={color.brand.orange}>GABOLLE ACCOUNT</Text><Text variant="display" weight="bold" color={color.text.onAction} style={styles.webIntroTitle}>{tx('여행의 설렘은 그대로,\n일정은 안전하게', 'Keep the excitement,\nsave every plan.')}</Text><Text variant="body" color="#dce5f2">{tx('저장한 부산 여행과 동행자 일정을 어디서든 이어보세요.', 'Continue your saved Busan trips and shared plans anywhere.')}</Text></View>}
    <View style={styles.panel}>
    <Pressable accessibilityRole="link" accessibilityLabel={tx('GABOLLE 홈으로 이동', 'Go to the GABOLLE home')} onPress={() => router.replace(kind === 'phone' ? '/home' : '/')} style={({ pressed }) => [styles.logoLink, pressed && styles.pressed]}>
      <Image source={require('../../assets/brand/gabolle-logo-figma.png')} resizeMode="contain" accessibilityIgnoresInvertColors style={styles.logo} />
    </Pressable>
    <Text variant="display" weight="bold" style={styles.title}>{tx('로그인', 'Sign in')}</Text>
    <Text color={color.text.body} style={styles.subtitle}>{tx('부산 여행을 시작해볼까요?', 'Ready to explore Busan?')}</Text>
    <View style={styles.form}>
      <View style={styles.field}><Text variant="caption" weight="bold">{tx('이메일', 'Email')}</Text><TextInput accessibilityLabel={tx('이메일', 'Email')} autoCapitalize="none" autoComplete="email" keyboardType="email-address" value={email} onChangeText={setEmail} placeholder="name@example.com" placeholderTextColor={color.text.muted} style={styles.input} /></View>
      <View style={styles.field}><Text variant="caption" weight="bold">{tx('비밀번호', 'Password')}</Text><View style={styles.passwordRow}><TextInput accessibilityLabel={tx('비밀번호', 'Password')} autoCapitalize="none" autoComplete="current-password" secureTextEntry={!show} value={password} onChangeText={setPassword} onSubmitEditing={() => void submit()} placeholder={tx('비밀번호', 'Password')} placeholderTextColor={color.text.muted} style={styles.passwordInput} /><Pressable accessibilityRole="button" accessibilityLabel={show ? tx('비밀번호 숨기기', 'Hide password') : tx('비밀번호 보이기', 'Show password')} accessibilityState={{ selected: show }} onPress={() => setShow(!show)} style={styles.eye}><Text variant="caption">{show ? tx('숨김', 'Hide') : tx('보기', 'Show')}</Text></Pressable></View></View>
      <Pressable accessibilityRole="link" style={styles.forgot} onPress={() => router.push('/forgot-password')}><Text variant="caption" color={color.action.primary}>{tx('비밀번호를 잊으셨나요?', 'Forgot your password?')}</Text></Pressable>
      {feedback && <Card><Text accessibilityRole="alert" variant="caption" color={feedback.danger ? color.state.danger : color.text.body}>{feedback.text}</Text></Card>}
      <Button accessibilityRole="button" accessibilityState={{ disabled: !eligible || busy || !!provider, busy }} label={busy ? tx('로그인 중…', 'Signing in…') : tx('로그인', 'Sign in')} disabled={!eligible || busy || !!provider} onPress={() => void submit()} />
    </View>
    <View style={styles.divider}><View style={styles.line} /><Text variant="caption">{tx('또는', 'or')}</Text><View style={styles.line} /></View>
    <View style={styles.socials}>{([
      { item: 'google', name: 'Google', mark: 'G', backgroundColor: '#ffffff', textColor: '#202124', borderColor: '#dadce0', markColor: '#4285f4' },
      { item: 'naver', name: 'Naver', mark: 'N', backgroundColor: '#03c75a', textColor: '#ffffff', borderColor: '#03c75a', markColor: '#ffffff' },
      { item: 'kakao', name: 'Kakao', mark: 'K', backgroundColor: '#fee500', textColor: '#191919', borderColor: '#fee500', markColor: '#191919' },
    ] as const).map(({ item, name, mark, backgroundColor, textColor, borderColor, markColor }) => { const action = tx(`${name}로 계속하기`, `Continue with ${name}`); return <Pressable key={item} accessibilityRole="button" accessibilityLabel={action} accessibilityState={{ disabled: busy || !!provider, busy: provider === item }} disabled={busy || !!provider} onPress={() => void social(item)} style={({ pressed }) => [styles.social, { backgroundColor, borderColor }, pressed && styles.pressed]}><View accessible={false} style={styles.socialContent}><Text weight="bold" color={markColor} style={styles.socialMark}>{mark}</Text><Text weight="bold" color={textColor}>{provider === item ? tx('연결 중…', 'Connecting…') : action}</Text></View></Pressable>; })}</View>
    {provider && <ActivityIndicator accessibilityLabel={tx('소셜 로그인 처리 중', 'Processing social sign-in')} color={color.action.primary} />}
    <Pressable accessibilityRole="button" style={styles.guest} onPress={() => router.replace('/home')}><Text weight="bold" color={color.action.primary}>{tx('계정 없이 둘러보기', 'Explore without an account')}</Text></Pressable>
    <Pressable accessibilityRole="link" style={styles.signup} onPress={() => router.push({ pathname: '/sign-up', params: returnTo ? { returnTo } : {} })}><Text variant="caption">{tx('처음이신가요? ', 'New here? ')}<Text variant="caption" weight="bold" color={color.brand.orange}>{tx('회원가입', 'Create an account')}</Text></Text></Pressable>
    {returnTo && <Pressable accessibilityRole="button" style={styles.signup} onPress={() => router.replace('/home')}><Text variant="caption" weight="bold" color={color.text.body}>{tx('홈으로 돌아가기', 'Back to home')}</Text></Pressable>}
  </View></View></Screen>;
}
const styles = StyleSheet.create({ screen: { alignItems: 'center', justifyContent: 'center' }, loginLayout: { width: '100%', alignItems: 'center' }, loginLayoutWide: { flexDirection: 'row', alignItems: 'stretch', gap: spacing[8] }, webIntro: { flex: 1, minHeight: 680, justifyContent: 'center', gap: spacing[4], padding: spacing[8], borderRadius: radius.lg, backgroundColor: color.brand.navy }, webIntroTitle: { fontSize: 38, lineHeight: 48 }, panel: { flex: 1, width: '100%', maxWidth: 420, justifyContent: 'center', paddingVertical: spacing[3] }, logoLink: { minWidth: 120, minHeight: 44, alignSelf: 'center', alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm }, logo: { width: 120, height: 44 }, title: { textAlign: 'center', marginTop: spacing[3] }, subtitle: { textAlign: 'center', marginTop: spacing[2], marginBottom: spacing[6] }, form: { gap: spacing[3] }, field: { gap: spacing[2] }, input: { minHeight: 52, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4] }, passwordRow: { minHeight: 52, flexDirection: 'row', alignItems: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card }, passwordInput: { flex: 1, minWidth: 0, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4], paddingVertical: spacing[3] }, eye: { minWidth: 52, minHeight: 44, alignItems: 'center', justifyContent: 'center' }, forgot: { minHeight: 44, alignSelf: 'flex-end', justifyContent: 'center' }, divider: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginVertical: spacing[6] }, line: { flex: 1, height: 1, backgroundColor: color.surface.field }, socials: { gap: spacing[3] }, social: { minHeight: 52, borderRadius: radius.md, borderWidth: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4] }, socialContent: { width: '100%', flexDirection: 'row', alignItems: 'center', justifyContent: 'center' }, socialMark: { position: 'absolute', left: 0, width: 24, textAlign: 'center', fontSize: 17 }, pressed: { opacity: .75 }, guest: { minHeight: 44, alignItems: 'center', justifyContent: 'center', marginTop: spacing[6] }, signup: { minHeight: 44, alignItems: 'center', justifyContent: 'center' } });
