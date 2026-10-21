// 길 안내를 로그인한 분께만 보여 줄 때의 안내 — S15P21E201-2060 (2026-10-16 웹 점검).
//
// 서버는 GET /api/v1/routes/directions 를 로그인한 사람에게만 연다. 좌표 두 개만 있으면 누구나 부를 수 있는 자리는 바깥 업체(자동차 길은
// 카카오모빌리티)를 우리 키·우리 비용으로 대신 불러 주는 창구가 되기 때문이다(RouteController 의 주석). 익명 출입증은 누구나 새로 받을 수 있어
// 그 경계를 못 지킨다 — 그래서 열지 않았다. 게스트가 이 화면에 오면 요청을 보내지 않고(어차피 401 이다) 서버의 401 원문
// 「인증 정보가 올바르지 않습니다.」 대신 이유와 길을 같이 준다. 환율(exchange-rate.tsx)·버스 도착(transit.tsx)과 같은 모양이다:
//   로그인하기(돌아오면 이 화면 그대로) + 지도 앱으로 열기(카카오맵 길찾기 — 지도 앱 단추가 이미 쓰는 listAvailableRouteMapApps).
import { StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Text } from '@/components/Text';
import { color, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { listAvailableRouteMapApps, type RouteEnds } from '@/utils/externalMaps';

export function RouteGuestNotice({ route, returnTo }: { route: RouteEnds; returnTo: string }) {
  const { tx } = useI18n();
  const router = useRouter();
  // 설치된 지도 앱이 있으면 그것, 없으면 카카오맵 웹(웹 브라우저에서는 처음부터 웹 주소). 실패해도 화면이 죽지 않는다.
  const openMapApp = () => { void listAvailableRouteMapApps(route).then((apps) => apps[0]?.open()).catch(() => undefined); };
  return (
    <Card testID="route-guest-notice" style={styles.card} accessibilityLiveRegion="polite">
      <Text variant="title" weight="bold" style={styles.centered}>{tx('로그인하면 길 안내를 보여드려요', 'Sign in to see directions')}</Text>
      <Text color={color.text.body} style={[styles.centered, styles.body]}>
        {tx('길 안내는 한도가 있는 외부 자료를 써서 로그인한 분에게만 보여드려요. 로그인하지 않아도 지도 앱에서 바로 길을 찾을 수 있어요.', 'Directions use a metered outside source, so they are for signed-in travelers. You can still find the way right away in a map app.')}
      </Text>
      <Button testID="route-guest-sign-in" label={tx('로그인하기', 'Sign in')} containerStyle={styles.cta} onPress={() => router.push({ pathname: '/sign-in', params: { returnTo } })} />
      <Button testID="route-guest-map-app" label={tx('지도 앱으로 열기', 'Open in a map app')} variant="tertiary" containerStyle={styles.cta} onPress={openMapApp} />
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing[3], alignItems: 'center' },
  centered: { textAlign: 'center' },
  body: { lineHeight: 22 },
  cta: { alignSelf: 'stretch' },
});
