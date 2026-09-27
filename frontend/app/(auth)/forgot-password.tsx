import { useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { requestPasswordReset } from '@/auth/authApi';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { localizeMessage } from '@/i18n/messages';

const logo = require('../../assets/brand/gabolle-logo-hd.png');
const envelopeIcon = require('../../assets/icons/common/envelope.png');
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function ForgotPassword() {
  const router = useRouter();
  const { tx } = useI18n();
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (!email.trim() || submitting) return;
    // 형식부터 본다 — 안 보면 서버 400 의 일반 문구(「요청 형식이 올바르지 않습니다」)만 떠서
    // 무엇이 틀렸는지 모른다. 회원가입·로그인과 같은 문구로 알린다(S15P21E201-1784).
    if (!EMAIL_PATTERN.test(email.trim())) {
      setError(tx('올바른 이메일 주소를 입력해 주세요.', 'Please enter a valid email address.'));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await requestPasswordReset(email);
      setSent(true);
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : tx('재설정 메일 요청 중 오류가 발생했어요.', 'Something went wrong while requesting the reset email.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Screen>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/sign-in')} style={styles.backButton}><Text variant="title">‹</Text></Pressable>
      <Pressable accessibilityRole="link" accessibilityLabel={tx('GABOLLE 시작 화면으로 이동', 'Go to the GABOLLE start screen')} onPress={() => router.replace('/')} style={({ pressed }) => [styles.logoLink, pressed && styles.pressed]}>
        <Image source={logo} resizeMode="contain" style={styles.logo} accessibilityIgnoresInvertColors />
      </Pressable>
      <View style={styles.body}>
        <Text variant="display" weight="bold">{tx('비밀번호 찾기', 'Forgot password')}</Text>
        <Text variant="body" style={styles.subtitle}>{tx('가입할 때 사용한 이메일을 입력해 주세요.', 'Enter the email address you signed up with.')}</Text>
        {sent ? (
          <View style={styles.sentBox}>
            <Image source={envelopeIcon} resizeMode="contain" style={styles.sentIcon} />
            <Text variant="body" weight="bold" style={styles.center}>{tx('입력한 이메일로 가입된 계정이 있다면 비밀번호 재설정 링크를 보내드렸어요.', "If that email has an account, we've sent a password reset link.")}</Text>
            <Text variant="caption" style={styles.center}>{tx('메일이 보이지 않으면 스팸함도 확인해 주세요.', "If you don't see it, please check your spam folder too.")}</Text>
          </View>
        ) : (
          <View style={styles.fieldGroup}>
            <Text variant="caption" weight="bold" color={color.text.heading}>{tx('이메일', 'Email')}</Text>
            <TextInput accessibilityLabel={tx('이메일', 'Email')} autoCapitalize="none" autoComplete="email" keyboardType="email-address" onChangeText={setEmail} onSubmitEditing={() => void submit()} placeholder="name@example.com" placeholderTextColor={color.text.muted} style={styles.input} value={email} />
          </View>
        )}
        {error && <View accessibilityRole="alert" style={styles.errorBox}><Text variant="caption" weight="bold" color={color.state.danger}>{localizeMessage(tx, error)}</Text></View>}
        {sent ? <Button label={submitting ? tx('전송 중…', 'Sending…') : tx('메일 다시 보내기', 'Resend email')} variant="tertiary" disabled={submitting} onPress={() => void submit()} /> : <Button label={submitting ? tx('전송 중…', 'Sending…') : tx('재설정 링크 받기', 'Get reset link')} disabled={!email.trim() || submitting} onPress={() => void submit()} />}
        {submitting && <ActivityIndicator color={color.action.secondary} />}
      </View>
      <Pressable accessibilityRole="link" onPress={() => router.replace('/sign-in')} style={styles.loginLink}><Text variant="body" weight="bold" color={color.action.secondary}>{tx('로그인으로 돌아가기', 'Back to sign in')}</Text></Pressable>
    </Screen>
  );
}

const styles = StyleSheet.create({
  backButton: { width: 40, height: 40, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  logoLink: { minWidth: 154, minHeight: 44, marginTop: spacing[8], alignSelf: 'center', alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm },
  logo: { width: 220, height: 40 },
  pressed: { opacity: 0.75 },
  body: { flex: 1, justifyContent: 'center', gap: spacing[4] },
  subtitle: { color: color.text.body },
  fieldGroup: { gap: spacing[2] },
  input: { minHeight: 52, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4] },
  sentBox: { alignItems: 'center', gap: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card, padding: spacing[6] },
  sentIcon: { width: 32, height: 32 },
  center: { textAlign: 'center' },
  errorBox: { borderRadius: radius.md, backgroundColor: color.state.dangerBg, padding: spacing[3] },
  loginLink: { alignItems: 'center', paddingVertical: spacing[4] },
});
