import { useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter, type Href } from 'expo-router';
import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

function safeReturnTo(value: string | undefined): Href {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.includes('://') || value.startsWith('/sign-in')) return '/me';
  return value as Href;
}

export default function SignIn() {
  const router = useRouter();
  const { returnTo } = useLocalSearchParams<{ returnTo?: string }>();
  const { signIn } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const eligible = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) && password.length > 0;
  async function submit() {
    if (!eligible || submitting) return;
    setSubmitting(true); setError(null);
    try { await signIn(email, password); router.replace(safeReturnTo(returnTo)); }
    catch (cause) {
      if (cause instanceof ApiClientError && cause.status === 429) setError('요청이 너무 많아요. 잠시 후 다시 시도해 주세요.');
      else if (cause instanceof ApiClientError && cause.code === 'NETWORK_ERROR') setError(cause.message);
      else if (cause instanceof ApiClientError && cause.code === 'EMAIL_NOT_VERIFIED') setError('이메일 인증을 마친 뒤 로그인해 주세요.');
      else if (cause instanceof ApiClientError && cause.status === 401) setError('이메일 또는 비밀번호가 올바르지 않아요.');
      else setError(cause instanceof ApiClientError ? cause.message : '로그인하지 못했어요.');
    } finally { setSubmitting(false); }
  }
  return <Screen scroll>
    <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" onPress={() => router.canGoBack() ? router.back() : router.replace('/')} style={styles.back}><Text variant="title">‹</Text></Pressable>
    <Text variant="display" weight="bold" style={styles.title}>로그인</Text>
    <Text variant="body">내 여행을 안전하게 이어서 확인하세요.</Text>
    <View style={styles.form}>
      <View style={styles.field}><Text variant="caption" weight="bold">이메일</Text><TextInput accessibilityLabel="이메일" autoCapitalize="none" autoComplete="email" keyboardType="email-address" value={email} onChangeText={setEmail} placeholder="name@example.com" placeholderTextColor={color.text.muted} style={styles.input} /></View>
      <View style={styles.field}><Text variant="caption" weight="bold">비밀번호</Text><TextInput accessibilityLabel="비밀번호" autoCapitalize="none" autoComplete="current-password" secureTextEntry value={password} onChangeText={setPassword} onSubmitEditing={() => void submit()} placeholder="비밀번호" placeholderTextColor={color.text.muted} style={styles.input} /></View>
      {!eligible && (email.length > 0 || password.length > 0) && <Text variant="caption">올바른 이메일과 비밀번호를 입력해 주세요.</Text>}
      {error && <Card><Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{error}</Text></Card>}
      <Button
        accessibilityRole="button"
        accessibilityState={{ disabled: !eligible || submitting, busy: submitting }}
        label={submitting ? '로그인 중…' : '로그인'}
        disabled={!eligible || submitting}
        onPress={() => void submit()}
      />
      {submitting && <ActivityIndicator accessibilityLabel="로그인 처리 중" color={color.action.primary} />}
    </View>
    <View style={styles.links}><Pressable accessibilityRole="link" onPress={() => router.push('/sign-up')}><Text variant="body" weight="bold" color={color.action.primary}>회원가입</Text></Pressable><Text variant="caption">비밀번호 재설정은 별도 기능 준비 중입니다.</Text></View>
  </Screen>;
}
const styles = StyleSheet.create({ back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, title: { marginTop: spacing[6], marginBottom: spacing[2] }, form: { marginTop: spacing[8], gap: spacing[4] }, field: { gap: spacing[2] }, input: { minHeight: 52, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4] }, links: { marginTop: spacing[6], alignItems: 'center', gap: spacing[3] } });
