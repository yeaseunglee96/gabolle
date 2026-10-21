// 비회원이 회원 전용 단추에 닿았을 때 — S15P21E201-317.
//
// 비회원도 자기 여행을 만들고 고치지만, 읽기 전용 링크·기록처럼 다른 사람에게 이름이 걸리는 것은 계정이 있어야 한다.
// 전에는 단추가 조용히 아무 일도 안 하거나(링크) 서버의 401 문구를 그대로 띄웠다(기록). 이 카드로 이유와 길을 같이 준다.
// 로그인하면 지금 화면으로 돌아오고, 이 기기에서 만든 여행은 계정으로 넘어온다(guestHandover).
import { StyleSheet, View } from 'react-native';
import { usePathname, useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

export function GuestSignInNotice() {
  const { tx } = useI18n();
  const router = useRouter();
  const pathname = usePathname();
  return <View style={styles.card}>
    <Text weight="bold">{tx('로그인이 필요한 기능이에요', 'Sign-in required for this feature')}</Text>
    <Button label={tx('로그인하기', 'Sign in')} variant="tertiary" onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: pathname } })} />
  </View>;
}

const styles = StyleSheet.create({
  card: { gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.subtle },
});
