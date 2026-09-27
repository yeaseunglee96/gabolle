import { Image, Pressable, StyleSheet, type ImageStyle, type StyleProp } from 'react-native';
import { useRouter } from 'expo-router';
import { radius } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useTopNavShown } from '@/nav/TopNav';
import { enterApp } from '@/auth/enterApp';

const logo = require('../../assets/brand/gabolle-logo-hd.png');

/**
 * @param inTopNav 위쪽 메뉴가 부를 때 — 그때는 언제나 그린다(메뉴의 로고가 바로 이것이다).
 * @param enter 인증·가입 화면처럼 **앱 밖**에서 부를 때 — 쌓인 화면을 치우고 들어간다(enterApp).
 *   맨 replace 는 맨 위 한 칸만 바꿔서 홈에서 뒤로 가면 로그인 화면이 다시 나온다(S15P21E201-1801).
 */
export function BrandLogoLink({ imageStyle, href = '/home', inTopNav = false, enter = false }: { imageStyle?: StyleProp<ImageStyle>; href?: string; inTopNav?: boolean; enter?: boolean }) {
  const router = useRouter();
  const { tx } = useI18n();
  // 🔴 위쪽 메뉴가 떠 있으면 거기 로고가 이미 있다 — 두 번 그리지 않는다(S15P21E201-1547, 폴드 펼침에서 겹쳤다).
  //    자리를 비우지 않고 아예 안 그린다. 그 줄의 뒤로 단추 같은 나머지는 부르는 화면이 그대로 그린다.
  const topNavShown = useTopNavShown();
  if (topNavShown && !inTopNav) return null;
  return <Pressable accessibilityRole="link" accessibilityLabel={tx('GABOLLE 홈으로 이동', 'Go to GABOLLE home')} onPress={() => (enter ? enterApp(router, href as never) : router.replace(href as never))} style={({ pressed }) => [styles.link, pressed && styles.pressed]}><Image source={logo} resizeMode="contain" accessibilityIgnoresInvertColors style={imageStyle} /></Pressable>;
}
const styles = StyleSheet.create({ link: { borderRadius: radius.sm, minHeight: 44, minWidth: 44, alignItems: 'center', justifyContent: 'center' }, pressed: { opacity: .75 } });
