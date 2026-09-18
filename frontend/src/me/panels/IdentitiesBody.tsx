// 연결된 소셜 계정 본문 — 화면(`app/me/identities.tsx`)과 마이페이지 패널이 같은 것을 쓴다.
//
// 🔴 -1329 — 전에는 **무엇이 붙어 있는지 몰랐다.** 넷을 늘 「연결하기」로 그려 두고, 눌러 봐야
//    「이미 연결되어 있어요」로 알 수 있었다. 이제 서버가 목록을 준다(-1317).
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import { useFocusEffect } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import type { OAuthProvider } from '@/auth/authApi';
import { fetchLinkedIdentities, unlinkIdentity, type LinkedIdentity } from '@/auth/identitiesApi';
import { linkOAuthProvider } from '@/auth/oauth';
import { Button } from '@/components/Button';
import { SocialProviderIcon } from '@/components/SocialProviderIcon';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

// 구글·카카오 아이콘은 그 자체가 다색이라 흰 바탕에 보이지만, 애플·네이버 아이콘은
// sign-in.tsx 의 브랜드색 버튼 위에 놓일 흰색 그림이라 이 화면의 흰 배경에서는 흰색 위에
// 흰색이 되어 안 보인다. 그 둘만 브랜드색 배지를 뒤에 깔아 준다.
const BADGE_BG: Partial<Record<OAuthProvider, string>> = { apple: '#000000', naver: '#03c75a' };

const PROVIDERS: Array<{ id: OAuthProvider; name: string }> = [
  { id: 'kakao', name: 'Kakao' },
  { id: 'naver', name: 'Naver' },
  { id: 'google', name: 'Google' },
  { id: 'apple', name: 'Apple' },
];

type LoadState =
  | { status: 'loading' }
  | { status: 'loaded'; items: LinkedIdentity[]; canSignInWithPassword: boolean }
  /** 서버가 목록을 안 준다(옛 서버). 붙이는 것만 되던 예전 화면으로 돌아간다. */
  | { status: 'unknown' };

export function IdentitiesBody() {
  const { accessToken } = useAuth();
  const { tx } = useI18n();
  const [state, setState] = useState<LoadState>({ status: 'loading' });
  const [busy, setBusy] = useState<OAuthProvider | null>(null);
  /** 「연결 해제」를 한 번 누른 줄. 되돌리기 어려운 일이라 그 자리에서 한 번 더 묻는다. */
  const [confirming, setConfirming] = useState<OAuthProvider | null>(null);
  const [feedback, setFeedback] = useState<{ danger: boolean; text: string } | null>(null);

  const load = useCallback(async () => {
    const result = await fetchLinkedIdentities(accessToken);
    if (result.state === 'success') {
      setState({ status: 'loaded', items: result.items, canSignInWithPassword: result.canSignInWithPassword });
    } else {
      // 🔴 못 읽었다고 「하나도 안 붙어 있다」로 그리지 않는다. 그건 지어내는 것이다.
      setState({ status: 'unknown' });
    }
  }, [accessToken]);

  useFocusEffect(useCallback(() => { void load(); }, [load]));

  const linkedOf = (provider: OAuthProvider) =>
    state.status === 'loaded' ? state.items.find((item) => item.provider === provider) ?? null : null;

  // — 웹에서는 linkOAuthProvider 가 현재 페이지를 제공자 화면으로 그대로 넘긴다.
  // 이 아래는 실행되지 않고, 결과는 착지 화면(oauth/[provider]/callback.tsx)이 보여준 뒤
  // 「설정으로 돌아가기」로 이 화면에 돌아온다. 앱에서는 그 왕복 없이 여기서 바로 받는다.
  async function connect(provider: OAuthProvider) {
    if (!accessToken || busy) return;
    setBusy(provider);
    setFeedback(null);
    try {
      const result = await linkOAuthProvider(provider, accessToken, '/me/identities');
      if (result.status === 'TAKEN') {
        setFeedback({ danger: true, text: tx('이미 다른 계정에 연결된 소셜 계정이에요.', 'This social account is already connected to a different account.') });
      } else {
        setFeedback({ danger: false, text: result.alreadyLinked ? tx('이미 연결되어 있어요.', 'Already connected.') : tx('계정을 연결했어요.', 'Account connected.') });
        await load();
      }
    } catch (cause) {
      setFeedback({ danger: true, text: cause instanceof ApiClientError ? cause.message : tx('연결하지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not connect. Please try again shortly.') });
    } finally {
      setBusy(null);
    }
  }

  async function disconnect(provider: OAuthProvider) {
    if (busy) return;
    setBusy(provider);
    setConfirming(null);
    setFeedback(null);
    const result = await unlinkIdentity(provider, accessToken);
    if (result.state === 'success') {
      setFeedback({ danger: false, text: tx('연결을 해제했어요.', 'Account disconnected.') });
      await load();
    } else {
      setFeedback({ danger: true, text: result.message });
    }
    setBusy(null);
  }

  return (
    <>
      {state.status === 'loading' ? (
        <View style={styles.loading}><ActivityIndicator color={color.brand.orange} /></View>
      ) : null}

      {state.status !== 'loading' ? (
        <View style={styles.group}>
          {PROVIDERS.map((provider, index) => {
            const linked = linkedOf(provider.id);
            const working = busy === provider.id;
            const badgeBg = BADGE_BG[provider.id];
            const asking = confirming === provider.id;
            return (
              <View key={provider.id} style={[styles.row, index > 0 && styles.divided]}>
                <View style={styles.label}>
                  <View style={[styles.tile, badgeBg ? { backgroundColor: badgeBg } : null]}><SocialProviderIcon provider={provider.id} /></View>
                  <View style={styles.naming}>
                    <Text weight="bold">{provider.name}</Text>
                    {linked ? (
                      <Text variant="caption" color={color.text.muted} numberOfLines={1}>
                        {linked.providerEmail ?? tx('연결됨', 'Connected')}
                      </Text>
                    ) : null}
                  </View>
                </View>

                {asking ? (
                  <View style={styles.confirm}>
                    <Button compact variant="ghost" label={tx('그만', 'Cancel')} onPress={() => setConfirming(null)} />
                    <Button compact variant="danger" label={tx('뗀다', 'Disconnect')} onPress={() => void disconnect(provider.id)} />
                  </View>
                ) : linked ? (
                  <Button
                    compact
                    variant="ghost"
                    // 🔴 뗄 수 있는지는 서버가 정한다(canUnlink). 화면이 그 규칙을 다시 적지 않는다.
                    disabled={working || !linked.canUnlink}
                    label={working ? tx('처리 중…', 'Working…') : tx('연결 해제', 'Disconnect')}
                    onPress={() => setConfirming(provider.id)}
                  />
                ) : (
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={tx(`${provider.name} 계정 연결하기`, `Connect ${provider.name} account`)}
                    accessibilityState={{ disabled: working }}
                    disabled={working}
                    onPress={() => void connect(provider.id)}
                    style={({ pressed }) => [styles.connect, pressed && styles.pressed]}
                  >
                    <Text variant="caption" weight="bold" color={color.brand.orange}>
                      {working ? tx('연결하는 중…', 'Connecting…') : tx('연결하기', 'Connect')}
                    </Text>
                  </Pressable>
                )}
              </View>
            );
          })}
        </View>
      ) : null}

      {feedback ? (
        <View accessibilityRole="alert" style={[styles.feedback, feedback.danger && styles.feedbackDanger]}>
          <Text variant="caption" weight="bold" color={feedback.danger ? color.state.danger : color.state.success}>{feedback.text}</Text>
        </View>
      ) : null}

      {/* 🔴 마지막 하나는 왜 안 떼지는지 그 자리에서 말한다. 눌리지 않는 버튼만 두면
          「고장났나」로 읽힌다. 비밀번호가 없는 계정에서만 나오는 상태다. */}
      {state.status === 'loaded' && state.items.length === 1 && !state.canSignInWithPassword ? (
        <Text variant="caption" style={styles.note}>
          {tx('이 계정에 남은 마지막 로그인 수단이라 뗄 수 없어요. 다른 계정을 하나 더 연결하면 뗄 수 있어요.',
            'This is the only way left to sign in, so it cannot be removed. Connect another account first.')}
        </Text>
      ) : null}

      {state.status === 'unknown' ? (
        <Text variant="caption" style={styles.note}>
          {tx('어떤 계정이 연결돼 있는지는 지금 확인할 수 없어요. 눌러 보시면 그 자리에서 결과를 알려드려요.',
            'We cannot check which accounts are connected right now. Tap one and we will tell you the result.')}
        </Text>
      ) : null}
    </>
  );
}

const styles = StyleSheet.create({
  loading: { paddingVertical: spacing[6], alignItems: 'center' },
  group: { overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card },
  row: { minHeight: 62, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], paddingVertical: spacing[3] },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.surface.border },
  pressed: { opacity: 0.7 },
  label: { flex: 1, minWidth: 0, flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  naming: { flex: 1, minWidth: 0, gap: 2 },
  tile: { width: 44, height: 44, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  connect: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[2] },
  confirm: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  feedback: { marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.successBg },
  feedbackDanger: { backgroundColor: color.state.dangerBg },
  note: { marginTop: spacing[3] },
});
