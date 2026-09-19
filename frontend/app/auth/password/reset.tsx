import { useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { confirmPasswordReset } from '@/auth/authApi';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

const logo = require('../../../assets/brand/gabolle-logo-hd.png');

export default function PasswordReset() {
  const router = useRouter();
  const { tx } = useI18n();
  const { token } = useLocalSearchParams<{ token?: string }>();
  const { clearSession } = useAuth();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const validLength = password.length >= 8 && password.length <= 100;
  const matches = password.length > 0 && password === confirm;
  const validToken = typeof token === 'string' && token.length > 0;
  const canSubmit = validToken && validLength && matches && !submitting;

  async function submit() {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      await confirmPasswordReset(token, password);
      clearSession();
      router.replace({ pathname: '/sign-in', params: { passwordReset: 'success' } });
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : tx('비밀번호를 바꾸지 못했어요.', 'Could not change your password.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Screen scroll>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.replace('/sign-in')} style={styles.backButton}><Text variant="title">‹</Text></Pressable>
      <Pressable accessibilityRole="link" accessibilityLabel={tx('GABOLLE 시작 화면으로 이동', 'Go to the GABOLLE start screen')} onPress={() => router.replace('/')} style={({ pressed }) => [styles.logoLink, pressed && styles.pressed]}>
        <Image source={logo} resizeMode="contain" style={styles.logo} accessibilityIgnoresInvertColors />
      </Pressable>
      <Text variant="display" weight="bold" style={styles.title}>{tx('새 비밀번호 설정', 'Set a new password')}</Text>
      <Text variant="body" style={styles.subtitle}>{tx('다른 서비스에서 사용하지 않는 비밀번호를 권장해요.', "We recommend a password you don't use on other services.")}</Text>

      <View style={styles.form}>
        {!validToken && <View accessibilityRole="alert" style={styles.errorBox}><Text variant="body" weight="bold" color={color.state.danger}>{tx('유효하지 않거나 만료된 재설정 링크예요.', 'This reset link is invalid or expired.')}</Text></View>}
        <View style={styles.fieldGroup}>
          <Text variant="caption" weight="bold" color={color.text.heading}>{tx('새 비밀번호', 'New password')}</Text>
          <TextInput accessibilityLabel={tx('새 비밀번호', 'New password')} autoCapitalize="none" autoComplete="new-password" onChangeText={setPassword} placeholder={tx('8자 이상 입력하세요', 'Enter at least 8 characters')} placeholderTextColor={color.text.muted} secureTextEntry style={styles.input} value={password} />
          <Text variant="caption" color={password.length > 0 && !validLength ? color.state.danger : validLength ? color.state.success : color.text.muted}>{validLength ? tx('✓ 8~100자 조건을 충족했어요.', '✓ Meets the 8-100 character requirement.') : tx(`8~100자 · 현재 ${password.length}자`, `8-100 characters · currently ${password.length}`)}</Text>
        </View>
        <View style={styles.fieldGroup}>
          <Text variant="caption" weight="bold" color={color.text.heading}>{tx('새 비밀번호 확인', 'Confirm new password')}</Text>
          <TextInput accessibilityLabel={tx('새 비밀번호 확인', 'Confirm new password')} autoCapitalize="none" autoComplete="new-password" onChangeText={setConfirm} onSubmitEditing={() => void submit()} placeholder={tx('한 번 더 입력하세요', 'Enter it once more')} placeholderTextColor={color.text.muted} secureTextEntry style={styles.input} value={confirm} />
          {confirm.length > 0 && <Text variant="caption" color={matches ? color.state.success : color.state.danger}>{matches ? tx('✓ 비밀번호가 일치해요.', '✓ Passwords match.') : tx('비밀번호가 일치하지 않아요.', 'Passwords do not match.')}</Text>}
        </View>
        {error && <View accessibilityRole="alert" style={styles.errorBox}><Text variant="caption" weight="bold" color={color.state.danger}>{error}</Text></View>}
        {validToken ? <Button label={submitting ? tx('변경 중…', 'Changing…') : tx('비밀번호 변경', 'Change password')} disabled={!canSubmit} onPress={() => void submit()} /> : <Button label={tx('재설정 링크 다시 받기', 'Get a new reset link')} onPress={() => router.replace('/forgot-password')} />}
        {error && validToken && <Button label={tx('재설정 링크 다시 받기', 'Get a new reset link')} variant="tertiary" onPress={() => router.replace('/forgot-password')} />}
        {submitting && <ActivityIndicator color={color.action.secondary} />}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  backButton: { width: 40, height: 40, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  logoLink: { minWidth: 154, minHeight: 44, marginTop: spacing[6], alignSelf: 'center', alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm },
  logo: { width: 154, height: 40 },
  pressed: { opacity: 0.75 },
  title: { marginTop: spacing[8] },
  subtitle: { marginTop: spacing[2], color: color.text.body },
  form: { marginTop: spacing[8], gap: spacing[4] },
  fieldGroup: { gap: spacing[2] },
  input: { minHeight: 52, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4] },
  errorBox: { borderRadius: radius.md, backgroundColor: color.state.dangerBg, padding: spacing[4] },
});
