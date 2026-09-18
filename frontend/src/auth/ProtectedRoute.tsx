import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { Redirect, Slot, useGlobalSearchParams, usePathname } from 'expo-router';
import { color } from '@/design/tokens';
import { useAuth } from './AuthProvider';

export function buildProtectedReturnTo(pathname: string, open?: string) {
  // /trips?open=prepare 는 날씨·준비물 도구에서 시작한 사용자의 의도다.
  // 인증 때문에 잠깐 멈추더라도 로그인 뒤 같은 작업을 바로 이어가게 한다.
  if (pathname === '/trips' && open === 'prepare') return '/trips?open=prepare';
  return pathname;
}

export function ProtectedRoute({ publicPaths = [] }: { publicPaths?: string[] }) {
  const { user, ready } = useAuth();
  const pathname = usePathname();
  const { preview, open } = useGlobalSearchParams<{ preview?: string; open?: string }>();
  const visualPreview = __DEV__ && preview === 'ui';
  if (publicPaths.includes(pathname) || visualPreview) return <Slot />;
  if (!ready) return <View style={styles.loading}><ActivityIndicator color={color.action.primary} /></View>;
  // 🔴 gated=1 — 「이 returnTo 는 로그인을 요구하는 화면이다」는 표시다 (S15P21E201-1116).
  //
  //    로그인 화면의 「비회원으로 둘러보기」는 returnTo 로 되돌려 보낸다. 그 자리가
  //    보호된 화면이면 다시 이리로 튕겨 와 무한 왕복이 된다 — 강제 종료 외에 빠져나올
  //    길이 없었다. 그렇다고 returnTo 를 늘 버리면, 여행 만들기 4단계처럼 로그인이
  //    필요 없는 자리에서 온 사람까지 홈으로 떨어져 채우던 것을 잃는다.
  //
  //    어느 쪽인지 아는 것은 여기뿐이다. 보호 화면 목록을 따로 만들어 맞춰 보는 방법도
  //    있지만, 목록은 화면이 늘 때마다 낡고 낡은 목록은 없는 것보다 나쁘다. 막은 쪽이
  //    막았다고 말하게 한다.
  if (!user) return <Redirect href={{ pathname: '/sign-in', params: { returnTo: buildProtectedReturnTo(pathname, open), gated: '1' } }} />;
  return <Slot />;
}
const styles = StyleSheet.create({ loading: { flex: 1, alignItems: 'center', justifyContent: 'center' } });
