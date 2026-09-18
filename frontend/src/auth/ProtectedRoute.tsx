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
  // gated=1 — 「이 returnTo 는 로그인을 요구하는 화면이다」는 표시다
  if (!user) return <Redirect href={{ pathname: '/sign-in', params: { returnTo: buildProtectedReturnTo(pathname, open), gated: '1' } }} />;
  return <Slot />;
}
const styles = StyleSheet.create({ loading: { flex: 1, alignItems: 'center', justifyContent: 'center' } });
