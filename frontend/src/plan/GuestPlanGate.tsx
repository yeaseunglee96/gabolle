// 비회원이 여행 만들기에 들어오면 질문 대신 먼저 보이는 안내 — S15P21E201-1818.
//
// 🔴 일정 만들기는 서버에 저장하므로 로그인이 필요하다(정책 유지). 예전엔 그 사실을 일곱 질문을
//    다 답한 «마지막 버튼»에서야 알렸다 — 들인 수고가 버려진다(QA 진미리). 그래서 입구에서 먼저 말한다.
//    둘러보기(홈·피드·지도)는 그대로 비회원에게 열려 있다. 이 화면은 여행 만들기 입구에만 선다.
import { StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';

export function GuestPlanGate() {
  const router = useRouter();
  const { tx } = useI18n();
  const { kind } = useLayout();
  const wide = kind !== 'phone';

  const signIn = () => router.push({ pathname: '/sign-in', params: { returnTo: '/plan' } });
  // 뒤가 없는 길(공유 링크·새로고침으로 /plan 에 바로 닿음)이면 홈으로 보낸다.
  const keepBrowsing = () => { if (router.canGoBack()) router.back(); else router.replace(wide ? '/' : '/home'); };

  return (
    <Screen scroll>
      <Card tinted style={styles.card}>
        <Text variant="title" weight="bold" accessibilityRole="header">
          {tx('일정 만들기는 로그인 후 쓸 수 있어요', 'Sign in to create a trip plan')}
        </Text>
        <Text>
          {tx('만든 일정은 계정에 저장돼요. 질문에 답하기 전에 먼저 로그인해 주세요.', 'Plans are saved to your account. Please sign in before answering the questions.')}
        </Text>
        <View style={styles.actions}>
          <Button label={tx('로그인하고 만들기', 'Sign in and create')} onPress={signIn} />
          <Button label={tx('둘러보기 계속', 'Keep browsing')} variant="secondary" onPress={keepBrowsing} />
        </View>
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing[3], marginTop: spacing[4] },
  actions: { gap: spacing[2], marginTop: spacing[2] },
});
