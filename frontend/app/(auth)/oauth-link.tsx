// 소셜 인증에 쓴 이메일로 이미 이메일 가입 계정이 있을 때(LINK_REQUIRED) 비밀번호로 그
// 계정에 소셜을 붙이는 화면. sign-in.tsx 의 social 이 409 OAUTH_ACCOUNT_LINK_REQUIRED 를
// 받으면 여기로 보낸다. 비밀번호가 틀려도 같은 티켓으로 재시도할 수 있다(서버 의도)
// , jaehyeon 님 !288 계약.
import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { completeOAuthLink, type OAuthProvider } from '@/auth/authApi';
import { consumePendingReturnTo, isSafeReturnPath } from '@/auth/pendingReturnTo';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';

// 기본값 /home — sign-in.tsx 의 resolveDestination 과 같은 이유(jaehyeon 님 제안).
async function resolveDestination(returnTo?: string) {
  if (isSafeReturnPath(returnTo)) return returnTo;
  return (await consumePendingReturnTo()) ?? '/home';
}
const PROVIDER_LABEL: Record<OAuthProvider, string> = { google: 'Google', naver: 'Naver', kakao: 'Kakao', apple: 'Apple' };

export default function OAuthLink() {
  const router = useRouter();
  const { tx } = useI18n();
  const { acceptTokens } = useAuth();
  const params = useLocalSearchParams<{ provider?: OAuthProvider; linkTicket?: string; maskedEmail?: string; returnTo?: string }>();
  const [password, setPassword] = useState('');
  const [show, setShow] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);

  const providerLabel = params.provider ? PROVIDER_LABEL[params.provider] : tx('소셜', 'social');
  const canSubmit = !!params.linkTicket && password.length > 0 && !submitting;

  async function submit() {
    if (!canSubmit || !params.linkTicket) return;
    setSubmitting(true);
    setError(null);
    try {
      const result = await completeOAuthLink({ linkTicket: params.linkTicket, password });
      await acceptTokens(result);
      router.replace((await resolveDestination(params.returnTo)) as never);
    } catch (cause) {
      if (cause instanceof ApiClientError && cause.status === 429) {
        setError(tx('로그인 시도가 너무 많아요. 잠시 후 다시 시도해 주세요.', 'Too many attempts. Please try again shortly.'));
      } else if (cause instanceof ApiClientError && cause.code === 'INVALID_CREDENTIALS') {
        setError(tx('비밀번호가 올바르지 않아요. 같은 티켓으로 다시 시도할 수 있어요.', 'That password is incorrect. You can try again with the same link.'));
      } else if (cause instanceof ApiClientError && cause.code === 'OAUTH_TICKET_INVALID') {
        setExpired(true);
      } else {
        setError(cause instanceof ApiClientError ? cause.message : tx('계정을 연결하지 못했어요.', 'Could not link the account.'));
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (!params.linkTicket || expired) {
    return (
      <Screen>
        <View style={styles.expiredBody}>
          <Text variant="display" weight="bold">{tx('연결 정보가 만료됐어요', 'Your link request expired')}</Text>
          <Text color={color.text.body}>{tx('소셜 로그인을 처음부터 다시 시작해 주세요.', 'Please start social sign-in again from the beginning.')}</Text>
          <Button compact label={tx('로그인으로 돌아가기', 'Back to sign-in')} onPress={() => router.replace('/sign-in')} />
        </View>
      </Screen>
    );
  }

  return (
    <Screen>
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.replace('/sign-in')} style={styles.backLink}>
          <Text variant="body" weight="bold">{tx('← 뒤로', '← Back')}</Text>
        </Pressable>
        <BrandLogoLink href="/home" imageStyle={styles.logo} />
      </View>
      <Text variant="display" weight="bold" style={styles.title}>{tx('이미 가입된 이메일이에요', 'This email is already registered')}</Text>
      <Text variant="body" color={color.text.body} style={styles.subtitle}>
        {txf(tx, '%s 계정에 %s 로그인을 연결할게요. 비밀번호를 입력해 주세요.', 'For the %s account, we\'ll link %s sign-in. Please enter your password.', params.maskedEmail ?? '', providerLabel)}
      </Text>

      <View style={styles.form}>
        <View style={styles.field}>
          <Text variant="caption" weight="bold">{tx('비밀번호', 'Password')}</Text>
          <View style={styles.passwordRow}>
            <TextInput accessibilityLabel={tx('비밀번호', 'Password')} autoCapitalize="none" autoComplete="current-password" secureTextEntry={!show} value={password} onChangeText={setPassword} onSubmitEditing={() => void submit()} placeholder={tx('비밀번호', 'Password')} placeholderTextColor={color.text.muted} style={styles.passwordInput} />
            <Pressable accessibilityRole="button" accessibilityLabel={show ? tx('비밀번호 숨기기', 'Hide password') : tx('비밀번호 보이기', 'Show password')} accessibilityState={{ selected: show }} onPress={() => setShow(!show)} style={styles.eye}>
              <Text variant="caption">{show ? tx('숨김', 'Hide') : tx('보기', 'Show')}</Text>
            </Pressable>
          </View>
        </View>

        {error && <Card><Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{localizeMessage(tx, error)}</Text></Card>}
        <Button label={submitting ? tx('연결 중…', 'Linking…') : tx('연결하고 로그인', 'Link and sign in')} disabled={!canSubmit} onPress={() => void submit()} />
        <Pressable accessibilityRole="link" style={styles.forgot} onPress={() => router.push('/forgot-password')}>
          <Text variant="caption" color={color.action.primary}>{tx('비밀번호를 잊으셨나요?', 'Forgot your password?')}</Text>
        </Pressable>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[2] },
  logo: { width: 176, height: 32 },
  backLink: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center' },
  title: { marginTop: spacing[3] },
  subtitle: { marginTop: spacing[1], marginBottom: spacing[6] },
  form: { gap: spacing[3] },
  field: { gap: spacing[2] },
  passwordRow: { minHeight: 52, flexDirection: 'row', alignItems: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  passwordInput: { flex: 1, minWidth: 0, color: color.text.heading, fontSize: 15, paddingHorizontal: spacing[4], paddingVertical: spacing[3] },
  eye: { minWidth: 52, minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  forgot: { minHeight: 44, alignSelf: 'flex-end', justifyContent: 'center' },
  expiredBody: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing[3], padding: spacing[6] },
});
