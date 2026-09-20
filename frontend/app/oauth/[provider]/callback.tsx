// 소셜 로그인이 끝나고 provider 가 사용자를 되돌려 보내는 착지 화면이다.
// 경로는 /oauth/google/callback · /oauth/naver/callback · /oauth/kakao/callback ·
// /oauth/apple/callback 넷이고 [provider] 동적 구간으로 한 파일이 다 받는다
// (DEC-AUTH-006). Apple도 scope를 email만 요청해(oauth.ts 주석)
// 다른 셋과 똑같이 GET 리다이렉트로 바로 여기 온다 — 별도 경유지가 없다.
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { completeOAuth, linkOAuthAccount } from '@/auth/authApi';
import { navigateAfterOAuthComplete } from '@/auth/oauthNavigation';
import { consumePendingOAuth } from '@/auth/pendingOAuth';
import { color, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';

const LABEL: Record<string, { ko: string; en: string }> = {
  google: { ko: '구글', en: 'Google' },
  naver: { ko: '네이버', en: 'Naver' },
  kakao: { ko: '카카오', en: 'Kakao' },
  apple: { ko: '애플', en: 'Apple' },
};

type ScreenResult =
  | { kind: 'error'; message: string; backTo: string }
  | { kind: 'link-done'; message: string; backTo: string };

export default function OAuthCallback() {
  const { provider, code, state, error } = useLocalSearchParams<{ provider?: string; code?: string; state?: string; error?: string }>();
  const { tx } = useI18n();
  const { acceptTokens, accessToken, ready } = useAuth();
  const router = useRouter();
  const entry = provider ? LABEL[provider] : undefined;
  const label = entry ? tx(entry.ko, entry.en) : tx('소셜', 'Social');
  const [result, setResult] = useState<ScreenResult | null>(null);

  useEffect(() => {
    // 팝업을 연 원래 창으로 결과를 넘기고 이 창을 닫는다. 팝업이 아닌 상황
    // (전체 페이지 이동으로 왔거나, 사용자가 이 주소를 직접 열었을 때)에서는 아무 일도 하지 않는다.
    WebBrowser.maybeCompleteAuthSession();
  }, []);

  useEffect(() => {
    // link 완료에는 accessToken 이 있어야 한다 — AuthProvider의 부팅 복원(ready)이 끝나기
    // 전에 꺼내 쓰면 아직 없는 값을 쓰게 된다. login 은 accessToken 이 필요 없지만, 두
    // intent 를 한 곳에서 다루려고 이 게이트를 공유한다(로그인 쪽은 지연이 아주 짧다).
    if (!ready) return;
    let cancelled = false;
    void consumePendingOAuth().then(async (pending) => {
      // 전체 페이지 이동으로 온 것이 아니면(예: 팝업이 방금 처리했거나, 이 주소를 직접 열었을 때)
      // 여기서 더 할 일이 없다 — 지역 변수를 다시 만들 수 없어 이 착지에서 완료할 방법이
      // 없다. 팝업 쪽은 위 maybeCompleteAuthSession이 이미 처리했다.
      if (!pending || cancelled) return;
      const backTo = pending.intent === 'link' ? (pending.returnTo ?? '/me') : '/sign-in';
      if (error) { setResult({ kind: 'error', message: tx('소셜 로그인 요청이 거절되었어요.', 'The social sign-in request was declined.'), backTo }); return; }
      if (!code || state !== pending.state) { setResult({ kind: 'error', message: tx('로그인 응답을 확인할 수 없어요.', 'Could not verify the sign-in response.'), backTo }); return; }
      try {
        if (pending.intent === 'link') {
          if (!accessToken) { setResult({ kind: 'error', message: tx('로그인이 만료됐어요. 다시 로그인한 뒤 연결해 주세요.', 'Your session expired. Please sign in again before connecting.'), backTo: '/sign-in' }); return; }
          const linkResult = await linkOAuthAccount(pending.provider, {
            authorizationCode: code,
            redirectUri: pending.redirectUri,
            codeVerifier: pending.codeVerifier,
            state: pending.state,
            nonce: pending.nonce,
          }, accessToken);
          if (cancelled) return;
          if (linkResult.status === 'TAKEN') {
            setResult({ kind: 'link-done', message: txf(tx, '이미 다른 계정에 연결된 %s 계정이에요.', 'This %s account is already connected to a different account.', label), backTo });
          } else {
            setResult({ kind: 'link-done', message: linkResult.alreadyLinked ? tx(`이미 연결되어 있어요.`, 'Already connected.') : txf(tx, '%s 계정을 연결했어요.', 'Connected your %s account.', label), backTo });
          }
          return;
        }
        const loginResult = await completeOAuth(pending.provider, {
          authorizationCode: code,
          redirectUri: pending.redirectUri,
          codeVerifier: pending.codeVerifier,
          state: pending.state,
          nonce: pending.nonce,
        });
        if (cancelled) return;
        await navigateAfterOAuthComplete({ result: loginResult, provider: pending.provider, returnTo: pending.returnTo, router, acceptTokens });
      } catch (cause) {
        if (cancelled) return;
        setResult({ kind: 'error', message: cause instanceof ApiClientError ? cause.message : tx('소셜 로그인을 완료하지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not complete social sign-in. Please try again shortly.'), backTo });
      }
    });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready]);

  return (
    <Screen>
      <View style={styles.body}>
        {result ? (
          <>
            <Text accessibilityRole="alert" weight="bold" color={result.kind === 'error' ? color.state.danger : color.state.success}>{result.message}</Text>
            <Pressable accessibilityRole="link" onPress={() => router.replace(result.backTo as never)}>
              <Text weight="bold" color={color.brand.navy}>{result.backTo === '/sign-in' ? tx('로그인 화면으로 돌아가기', 'Back to sign-in') : tx('설정으로 돌아가기', 'Back to settings')}</Text>
            </Pressable>
          </>
        ) : (
          <>
            <Text>{txf(tx, '%s 로그인을 처리하고 있어요.', 'Completing %s sign-in.', label)}</Text>
            <Text>{tx('창이 자동으로 닫히지 않으면 닫고 다시 시도해 주세요.', 'If this window does not close automatically, close it and try again.')}</Text>
          </>
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing[2],
    backgroundColor: color.canvas,
  },
});
