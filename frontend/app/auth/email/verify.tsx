// 메일의 인증 링크가 도착하는 화면 —.
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { confirmEmailVerification } from '@/auth/authApi';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

type Phase = 'checking' | 'done' | 'failed';

/** 성공 뒤 로그인으로 넘어가기까지 기다리는 시간. 완료를 읽을 틈은 주되 기다리게 하지는 않는다. */
const REDIRECT_DELAY_MS = 2500;

export default function VerifyEmail() {
  const router = useRouter();
  const { tx } = useI18n();
  const { token, done, error } = useLocalSearchParams<{ token?: string; done?: string; error?: string }>();

  const [phase, setPhase] = useState<Phase>(() => {
    if (typeof done === 'string') return done === '1' ? 'done' : 'failed';
    return typeof token === 'string' && token.length > 0 ? 'checking' : 'failed';
  });
  const [message, setMessage] = useState<string | null>(null);

  // 개발 중 화면이 두 번 그려져도 확인 요청이 두 번 나가지 않게 막는다. 토큰은 한 번만 쓸 수
  // 있어서 두 번째 요청은 반드시 실패하고, 그 실패가 성공한 사람에게 보인다.
  const requested = useRef(false);

  useEffect(() => {
    if (phase !== 'checking' || requested.current) return;
    if (typeof token !== 'string' || token.length === 0) return;
    requested.current = true;

    void (async () => {
      try {
        await confirmEmailVerification(token);
        setPhase('done');
      } catch (cause) {
        setMessage(
          cause instanceof ApiClientError
            ? cause.message
            : tx('인증을 마치지 못했어요.', 'Could not finish verification.'),
        );
        setPhase('failed');
      }
    })();
  }, [phase, token, tx]);

  // 완료를 보여 준 뒤 스스로 넘어간다. 사용자가 버튼을 누를 수도 있으므로 아래 버튼은 그대로 둔다.
  useEffect(() => {
    if (phase !== 'done') return;
    const timer = setTimeout(() => router.replace('/sign-in'), REDIRECT_DELAY_MS);
    return () => clearTimeout(timer);
  }, [phase, router]);

  const failureText =
    message ??
    (error === 'MISSING_VERIFICATION_TOKEN'
      ? tx('링크가 잘린 것 같아요. 메일의 주소 전체를 눌러 주세요.', 'The link looks truncated. Please open the full address from the email.')
      : tx(
          '링크가 만료됐거나 이미 사용됐어요. 가입 화면에서 인증 메일을 다시 받아 주세요.',
          'This link has expired or was already used. Request a new verification email from the sign-up screen.',
        ));

  return (
    <Screen style={styles.screen}>
      <View style={styles.body}>
        {phase === 'checking' && (
          <>
            <ActivityIndicator accessibilityLabel={tx('인증 확인 중', 'Verifying')} color={color.brand.orange} />
            <Text variant="title" weight="bold">{tx('인증을 확인하고 있어요', 'Verifying your email')}</Text>
            <Text variant="body" color={color.text.muted}>{tx('잠시만 기다려 주세요.', 'This will take a moment.')}</Text>
          </>
        )}

        {phase === 'done' && (
          <>
            <Text accessibilityRole="alert" variant="display" weight="bold" color={color.state.success}>
              {tx('인증이 완료되었습니다', 'Email verified')}
            </Text>
            <Text variant="body" color={color.text.muted}>
              {tx('이제 로그인할 수 있어요. 잠시 뒤 로그인 화면으로 넘어갑니다.', 'You can sign in now. Taking you to the sign-in screen.')}
            </Text>
            <Button label={tx('지금 로그인하기', 'Sign in now')} onPress={() => router.replace('/sign-in')} />
          </>
        )}

        {phase === 'failed' && (
          <>
            <Text accessibilityRole="alert" variant="title" weight="bold" color={color.state.danger}>
              {tx('인증을 마치지 못했어요', 'Verification failed')}
            </Text>
            <Text variant="body" color={color.text.muted}>{failureText}</Text>
            <Button label={tx('가입 화면으로', 'Back to sign-up')} onPress={() => router.replace('/sign-up')} />
            <Button label={tx('로그인 화면으로', 'Back to sign-in')} variant="tertiary" onPress={() => router.replace('/sign-in')} />
          </>
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  screen: { justifyContent: 'center' },
  body: { gap: spacing[4], alignItems: 'center' },
});
