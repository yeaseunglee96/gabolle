// 기록 공동 작성 초대 링크를 열었을 때 보이는 화면 —. 여행 초대
// (app/invite/[token].tsx,와 같은 모양이다: 아무것도 물어보지 않고 들어오자마자
// 수락 처리를 부른 뒤 그 기록으로 보낸다.
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { acceptStoryInvite } from '@/social/storyCoauthors';

type Status = { state: 'checking' } | { state: 'expired' } | { state: 'not-found' } | { state: 'error'; message: string };

export default function AcceptStoryInvite() {
  const router = useRouter();
  const { tx } = useI18n();
  const { token } = useLocalSearchParams<{ token?: string }>();
  const { accessToken, user, ready } = useAuth();
  const [status, setStatus] = useState<Status>({ state: 'checking' });

  useEffect(() => {
    if (!token) {
      setStatus({ state: 'error', message: tx('초대 링크가 올바르지 않아요.', 'This invite link is not valid.') });
      return;
    }
    if (!ready) return;
    if (!user || !accessToken) {
      router.replace({ pathname: '/sign-in', params: { returnTo: `/story-invite/${token}` } });
      return;
    }

    let active = true;
    (async () => {
      const outcome = await acceptStoryInvite(token, accessToken);
      if (!active) return;
      if (outcome.state === 'success') { router.replace(`/feed/${outcome.storyId}`); return; }
      if (outcome.state === 'expired') { setStatus({ state: 'expired' }); return; }
      if (outcome.state === 'not-found') { setStatus({ state: 'not-found' }); return; }
      setStatus({ state: 'error', message: outcome.message });
    })();
    return () => { active = false; };
  }, [token, ready, user, accessToken, router, tx]);

  return <Screen style={styles.screen}>
    {status.state === 'checking' && (
      <View accessibilityLiveRegion="polite" style={styles.card}>
        <Text variant="title" weight="bold">{tx('초대를 확인하는 중입니다…', 'Checking your invite…')}</Text>
      </View>
    )}
    {status.state === 'expired' && (
      <View accessibilityRole="alert" style={styles.card}>
        <Text variant="title" weight="bold">{tx('초대 링크가 만료되었어요', 'This invite link has expired')}</Text>
        <Text color={color.text.body}>{tx('초대 링크는 발급 후 7일 동안만 사용할 수 있어요. 초대한 사람에게 새 링크를 요청해 주세요.', 'Invite links are valid for 7 days after they’re created. Please ask for a new link.')}</Text>
        <Button label={tx('홈으로', 'Go home')} onPress={() => router.replace('/home')} />
      </View>
    )}
    {status.state === 'not-found' && (
      <View accessibilityRole="alert" style={styles.card}>
        <Text variant="title" weight="bold">{tx('초대를 찾을 수 없어요', "Couldn't find this invite")}</Text>
        <Text color={color.text.body}>{tx('링크가 잘못됐거나, 기록이 지워졌을 수 있어요.', 'The link may be wrong, or the record may have been deleted.')}</Text>
        <Button label={tx('홈으로', 'Go home')} onPress={() => router.replace('/home')} />
      </View>
    )}
    {status.state === 'error' && (
      <View accessibilityRole="alert" style={styles.card}>
        <Text variant="title" weight="bold">{tx('초대를 처리하지 못했어요', "Couldn't process this invite")}</Text>
        <Text color={color.text.body}>{status.message}</Text>
        <Button label={tx('홈으로', 'Go home')} onPress={() => router.replace('/home')} />
      </View>
    )}
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { alignItems: 'center', justifyContent: 'center', backgroundColor: color.canvas },
  card: { gap: spacing[3], width: '100%', maxWidth: 420, padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
});
