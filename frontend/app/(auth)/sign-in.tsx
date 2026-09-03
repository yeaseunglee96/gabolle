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

function safeReturnTo(value?: string): Href { return !value || !value.startsWith('/') || value.startsWith('//') || value.includes('://') || value.startsWith('/sign-in') ? '/me' : value as Href; }
function errorMessage(cause: unknown) {
  if (cause instanceof ApiClientError && cause.status === 429) return '요청이 너무 많아요. 잠시 후 다시 시도해 주세요.';
  if (cause instanceof ApiClientError && cause.code === 'EMAIL_NOT_VERIFIED') return '이메일 인증을 마친 뒤 로그인해 주세요.';
  if (cause instanceof ApiClientError && cause.status === 401) return '이메일 또는 비밀번호가 올바르지 않아요.';
  return cause instanceof ApiClientError ? cause.message : '로그인하지 못했어요.';
}
export default function SignIn() {
  const router = useRouter(); const { returnTo } = useLocalSearchParams<{ returnTo?: string }>(); const { signIn, acceptTokens } = useAuth();
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false); const [provider, setProvider] = useState<OAuthProvider | null>(null); const [feedback, setFeedback] = useState<{ danger: boolean; text: string } | null>(null);
  const eligible = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) && password.length > 0;
  async function submit() { if (!eligible || busy || provider) return; setBusy(true); setFeedback(null); try { await signIn(email, password); router.replace(safeReturnTo(returnTo)); } catch (e) { setFeedback({ danger: true, text: errorMessage(e) }); } finally { setBusy(false); } }
  async function social(next: OAuthProvider) { if (busy || provider) return; setProvider(next); setFeedback(null); try { const tokens = await loginWithOAuth(next); await acceptTokens(tokens); router.replace(safeReturnTo(returnTo)); } catch (e) { setFeedback({ danger: true, text: errorMessage(e) }); } finally { setProvider(null); } }
  return <Screen scroll style={styles.screen}><View style={styles.panel}>
    <Image source={require('../../assets/brand/gabolle-logo-figma.png')} resizeMode="contain" accessibilityLabel="GABOLLE" style={styles.logo} />
    <Text variant="display" weight="bold" style={styles.title}>로그인</Text>
    <Text color={color.text.body} style={styles.subtitle}>부산 여행을 시작해볼까요?</Text>
    <View style={styles.form}>
      <View style={styles.field}><Text variant="caption" weight="bold">이메일</Text><TextInput accessibilityLabel="이메일" autoCapitalize="none" autoComplete="email" keyboardType="email-address" value={email} onChangeText={setEmail} placeholder="name@example.com" placeholderTextColor={color.text.muted} style={styles.input} /></View>
      <View style={styles.field}><Text variant="caption" weight="bold">비밀번호</Text><View style={styles.passwordRow}><TextInput accessibilityLabel="비밀번호" autoCapitalize="none" autoComplete="current-password" secureTextEntry={!show} value={password} onChangeText={setPassword} onSubmitEditing={() => void submit()} placeholder="비밀번호" placeholderTextColor={color.text.muted} style={styles.passwordInput} /><Pressable accessibilityRole="button" accessibilityLabel={show ? '비밀번호 숨기기' : '비밀번호 보이기'} accessibilityState={{ selected: show }} onPress={() => setShow(!show)} style={styles.eye}><Text variant="caption">{show ? '숨김' : '보기'}</Text></Pressable></View></View>
      <Pressable accessibilityRole="button" style={styles.forgot} onPress={() => setFeedback({ danger: false, text: '비밀번호 재설정 기능은 준비 중이에요.' })}><Text variant="caption" color={color.action.primary}>비밀번호를 잊으셨나요?</Text></Pressable>
      {feedback && <Card><Text accessibilityRole="alert" variant="caption" color={feedback.danger ? color.state.danger : color.text.body}>{feedback.text}</Text></Card>}
      <Button accessibilityRole="button" accessibilityState={{ disabled: !eligible || busy || !!provider, busy }} label={busy ? '로그인 중…' : '로그인'} disabled={!eligible || busy || !!provider} onPress={() => void submit()} />
    </View>
    <View style={styles.divider}><View style={styles.line} /><Text variant="caption">또는</Text><View style={styles.line} /></View>
    <View style={styles.socials}>{([
      { item: 'google', name: 'Google', mark: 'G', backgroundColor: '#ffffff', textColor: '#202124', borderColor: '#dadce0', markColor: '#4285f4' },
      { item: 'naver', name: 'Naver', mark: 'N', backgroundColor: '#03c75a', textColor: '#ffffff', borderColor: '#03c75a', markColor: '#ffffff' },
      { item: 'kakao', name: 'Kakao', mark: 'K', backgroundColor: '#fee500', textColor: '#191919', borderColor: '#fee500', markColor: '#191919' },
    ] as const).map(({ item, name, mark, backgroundColor, textColor, borderColor, markColor }) => <Pressable key={item} accessibilityRole="button" accessibilityLabel={`${name}로 계속하기`} accessibilityState={{ disabled: busy || !!provider, busy: provider === item }} disabled={busy || !!provider} onPress={() => void social(item)} style={({ pressed }) => [styles.social, { backgroundColor, borderColor }, pressed && styles.pressed]}><View accessible={false} style={styles.socialContent}><Text weight="bold" color={markColor} style={styles.socialMark}>{mark}</Text><Text weight="bold" color={textColor}>{provider === item ? '연결 중…' : `${name}로 계속하기`}</Text></View></Pressable>)}</View>
    {provider && <ActivityIndicator accessibilityLabel="소셜 로그인 처리 중" color={color.action.primary} />}
    <Pressable accessibilityRole="button" style={styles.guest} onPress={() => router.replace('/home')}><Text weight="bold" color={color.action.primary}>계정 없이 둘러보기</Text></Pressable>
    <Pressable accessibilityRole="link" style={styles.signup} onPress={() => router.push('/sign-up')}><Text variant="caption">처음이신가요? <Text variant="caption" weight="bold" color={color.action.primary}>회원가입</Text></Text></Pressable>
  </View></Screen>;
}
const styles = StyleSheet.create({ screen: { alignItems: 'center' }, panel: { width: '100%', maxWidth: 420, paddingVertical: spacing[3] }, logo: { width: 120, height: 44, alignSelf: 'center' }, title: { textAlign: 'center', marginTop: spacing[3] }, subtitle: { textAlign: 'center', marginTop: spacing[2], marginBottom: spacing[6] }, form: { gap: spacing[3] }, field: { gap: spacing[2] }, input: { minHeight: 52, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4] }, passwordRow: { minHeight: 52, flexDirection: 'row', alignItems: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card }, passwordInput: { flex: 1, minWidth: 0, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4], paddingVertical: spacing[3] }, eye: { minWidth: 52, minHeight: 44, alignItems: 'center', justifyContent: 'center' }, forgot: { minHeight: 44, alignSelf: 'flex-end', justifyContent: 'center' }, divider: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginVertical: spacing[6] }, line: { flex: 1, height: 1, backgroundColor: color.surface.field }, socials: { gap: spacing[3] }, social: { minHeight: 52, borderRadius: radius.md, borderWidth: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4] }, socialContent: { width: '100%', flexDirection: 'row', alignItems: 'center', justifyContent: 'center' }, socialMark: { position: 'absolute', left: 0, width: 24, textAlign: 'center', fontSize: 17 }, pressed: { opacity: .75 }, guest: { minHeight: 44, alignItems: 'center', justifyContent: 'center', marginTop: spacing[6] }, signup: { minHeight: 44, alignItems: 'center', justifyContent: 'center' } });
