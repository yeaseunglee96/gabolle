// 마이페이지 › 연결된 소셜 계정 (S15P21E201-965). `(tabs)/me.tsx` 의 섹션을 그대로 옮겼다.
//
// 🔴 시안에는 줄마다 「연결됨 · {날짜}」 배지와 「연결 해제」 버튼이 있는데 **둘 다 안 그린다.**
// (S15P21E201-832) 서버가 연결된 제공자 목록을 돌려주는 조회가 없어서 무엇이 연결돼 있는지
// 알 방법이 없고, 해제 API 도 없다. 배지를 그리면 늘 「연결 안 됨」으로 보이거나 지어낸 값이
// 되고, 해제 버튼은 눌러도 아무 일이 없다. 지금 할 수 있는 것은 **연결뿐**이라 그것만 둔다.
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import type { OAuthProvider } from '@/auth/authApi';
import { linkOAuthProvider } from '@/auth/oauth';
import { SocialProviderIcon } from '@/components/SocialProviderIcon';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { MyPageShell } from '@/me/MyPageShell';

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

export default function MyPageIdentities() {
  const { accessToken } = useAuth();
  const { tx } = useI18n();
  const [linking, setLinking] = useState<OAuthProvider | null>(null);
  const [feedback, setFeedback] = useState<{ danger: boolean; text: string } | null>(null);

  // S15P21E201-832 — 웹에서는 linkOAuthProvider 가 현재 페이지를 제공자 화면으로 그대로 넘긴다.
  // 이 아래는 실행되지 않고, 결과는 착지 화면(oauth/[provider]/callback.tsx)이 보여준 뒤
  // 「설정으로 돌아가기」로 이 화면에 돌아온다. 앱에서는 그 왕복 없이 여기서 바로 받는다.
  async function connect(provider: OAuthProvider) {
    if (!accessToken || linking) return;
    setLinking(provider);
    setFeedback(null);
    try {
      const result = await linkOAuthProvider(provider, accessToken, '/me/identities');
      if (result.status === 'TAKEN') {
        setFeedback({ danger: true, text: tx('이미 다른 계정에 연결된 소셜 계정이에요.', 'This social account is already connected to a different account.') });
      } else {
        setFeedback({ danger: false, text: result.alreadyLinked ? tx('이미 연결되어 있어요.', 'Already connected.') : tx('계정을 연결했어요.', 'Account connected.') });
      }
    } catch (cause) {
      setFeedback({ danger: true, text: cause instanceof ApiClientError ? cause.message : tx('연결하지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not connect. Please try again shortly.') });
    } finally {
      setLinking(null);
    }
  }

  return (
    <MyPageShell
      tab="identities"
      title={tx('연결된 소셜 계정', 'Connected accounts')}
      description={tx('어느 계정으로 로그인해도 같은 가볼래로 들어와요. 다른 방식으로 로그인할 계획이면 미리 연결해 두세요.', 'Sign in any of these ways and you land on the same Gabolle account. Connect one ahead of time if you plan to switch.')}
    >
      <View style={styles.group}>
        {PROVIDERS.map((provider, index) => {
          const busy = linking === provider.id;
          const badgeBg = BADGE_BG[provider.id];
          return (
            <Pressable
              key={provider.id}
              accessibilityRole="button"
              accessibilityLabel={tx(`${provider.name} 계정 연결하기`, `Connect ${provider.name} account`)}
              accessibilityState={{ disabled: busy }}
              disabled={busy}
              onPress={() => void connect(provider.id)}
              style={({ pressed }) => [styles.row, index > 0 && styles.divided, pressed && styles.pressed]}
            >
              <View style={styles.label}>
                <View style={[styles.tile, badgeBg ? { backgroundColor: badgeBg } : null]}><SocialProviderIcon provider={provider.id} /></View>
                <Text weight="bold">{provider.name}</Text>
              </View>
              <Text variant="caption" weight="bold" color={color.brand.orange}>
                {busy ? tx('연결하는 중…', 'Connecting…') : tx('연결하기', 'Connect')}
              </Text>
            </Pressable>
          );
        })}
      </View>

      {feedback ? (
        <View accessibilityRole="alert" style={[styles.feedback, feedback.danger && styles.feedbackDanger]}>
          <Text variant="caption" weight="bold" color={feedback.danger ? color.state.danger : color.state.success}>{feedback.text}</Text>
        </View>
      ) : null}

      <Text variant="caption" style={styles.note}>
        {tx('어떤 계정이 연결돼 있는지는 아직 보여드릴 수 없어요. 눌러 보시면 그 자리에서 결과를 알려드려요.', 'We cannot list which accounts are already connected yet. Tap one and we will tell you the result right away.')}
      </Text>
    </MyPageShell>
  );
}

const styles = StyleSheet.create({
  group: { overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card },
  row: { minHeight: 62, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], paddingVertical: spacing[3] },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.surface.border },
  pressed: { opacity: 0.7, backgroundColor: color.surface.tint },
  label: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  tile: { width: 44, height: 44, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  feedback: { marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.successBg },
  feedbackDanger: { backgroundColor: color.state.dangerBg },
  note: { marginTop: spacing[3] },
});
