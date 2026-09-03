import { useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { requestPasswordReset } from '@/auth/authApi';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

const logo = require('../../assets/brand/gabolle-logo-figma.png');
const SAFE_MESSAGE = '입력한 이메일로 가입된 계정이 있다면 비밀번호 재설정 링크를 보내드렸어요.';

export default function ForgotPassword() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (!email.trim() || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await requestPasswordReset(email);
      setSent(true);
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : '재설정 메일 요청 중 오류가 발생했어요.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Screen>
      <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" onPress={() => router.canGoBack() ? router.back() : router.replace('/sign-in')} style={styles.backButton}><Text variant="title">‹</Text></Pressable>
      <Image source={logo} resizeMode="contain" style={styles.logo} accessibilityLabel="GABOLLE 가볼래" />
      <View style={styles.body}>
        <Text variant="display" weight="bold">비밀번호 찾기</Text>
        <Text variant="body" style={styles.subtitle}>가입할 때 사용한 이메일을 입력해 주세요.</Text>
        {sent ? (
          <View style={styles.sentBox}>
            <Text variant="title">✉</Text>
            <Text variant="body" weight="bold" style={styles.center}>{SAFE_MESSAGE}</Text>
            <Text variant="caption" style={styles.center}>메일이 보이지 않으면 스팸함도 확인해 주세요.</Text>
          </View>
        ) : (
          <View style={styles.fieldGroup}>
            <Text variant="caption" weight="bold" color={color.text.heading}>이메일</Text>
            <TextInput accessibilityLabel="이메일" autoCapitalize="none" autoComplete="email" keyboardType="email-address" onChangeText={setEmail} onSubmitEditing={() => void submit()} placeholder="name@example.com" placeholderTextColor={color.text.muted} style={styles.input} value={email} />
          </View>
        )}
        {error && <View accessibilityRole="alert" style={styles.errorBox}><Text variant="caption" weight="bold" color={color.state.danger}>{error}</Text></View>}
        {sent ? <Button label={submitting ? '전송 중…' : '메일 다시 보내기'} variant="ghost" disabled={submitting} onPress={() => void submit()} /> : <Button label={submitting ? '전송 중…' : '재설정 링크 받기'} disabled={!email.trim() || submitting} onPress={() => void submit()} />}
        {submitting && <ActivityIndicator color={color.action.secondary} />}
      </View>
      <Pressable accessibilityRole="link" onPress={() => router.replace('/sign-in')} style={styles.loginLink}><Text variant="body" weight="bold" color={color.action.secondary}>로그인으로 돌아가기</Text></Pressable>
    </Screen>
  );
}

const styles = StyleSheet.create({
  backButton: { width: 40, height: 40, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  logo: { width: 154, height: 40, marginTop: spacing[8], alignSelf: 'center' },
  body: { flex: 1, justifyContent: 'center', gap: spacing[4] },
  subtitle: { color: color.text.body },
  fieldGroup: { gap: spacing[2] },
  input: { minHeight: 52, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4] },
  sentBox: { alignItems: 'center', gap: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card, padding: spacing[6] },
  center: { textAlign: 'center' },
  errorBox: { borderRadius: radius.md, backgroundColor: color.state.dangerBg, padding: spacing[3] },
  loginLink: { alignItems: 'center', paddingVertical: spacing[4] },
});
