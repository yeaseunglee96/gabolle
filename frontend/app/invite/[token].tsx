// 초대 링크를 열었을 때 보이는 화면(상세설계서 Part II P-33, /invite/:token) — S15P21E201-302.
// 여기서는 아무것도 물어보지 않는다 — 들어오자마자 참여 처리를 부르고 여행으로 보낸다.
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { acceptTripInvite } from '@/trip/collaboration';
import { loadTripItineraries } from '@/trip/trips';

type Status = { state: 'checking' } | { state: 'expired' } | { state: 'error'; message: string };

export default function AcceptInvite() {
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
      router.replace({ pathname: '/sign-in', params: { returnTo: `/invite/${token}` } });
      return;
    }

    let active = true;
    (async () => {
      try {
        const accepted = await acceptTripInvite(token, accessToken);
        if (!active) return;
        // 여행 화면으로 이동 — trips.tsx 의 openTrip 과 같은 규칙이다: 일정이 하나면 곧바로
        // 그 화면으로, 아직 없거나 여럿이면(이 배열 순서는 서버 계약이 아니다) 목록에서 고르게
        // 여행 탭으로 보낸다.
        const itineraries = await loadTripItineraries(accepted.tripId, accessToken);
        if (!active) return;
        if (itineraries.state === 'success' && itineraries.itineraries.length === 1) {
          router.replace(`/trips/${itineraries.itineraries[0].itineraryId}/itinerary`);
        } else {
          router.replace('/trips');
        }
      } catch (cause) {
        if (!active) return;
        if (cause instanceof ApiClientError && cause.code === 'TRIP_INVITE_EXPIRED') {
          setStatus({ state: 'expired' });
          return;
        }
        setStatus({
          state: 'error',
          message: cause instanceof ApiClientError
            ? cause.message
            : tx('초대를 처리하지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not process the invite. Please try again shortly.'),
        });
      }
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
  screen: { alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.ivory },
  card: { gap: spacing[3], width: '100%', maxWidth: 420, padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
});
