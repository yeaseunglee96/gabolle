import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { useI18n } from '@/i18n';

const LINKS = [
  { labelKo: '홈', labelEn: 'Home', path: '/home' },
  { labelKo: '여행 만들기', labelEn: 'Plan a trip', path: '/plan/basic' },
  { labelKo: '내 여행', labelEn: 'My trips', path: '/trips' },
  { labelKo: '마이페이지', labelEn: 'Profile', path: '/me' },
] as const;

// 🔴 이 내비는 여행 만들기 흐름 다섯 화면(basics · taste · constraints · confirm · generating)에
// (plan)/_layout 이 붙인다. 로그인 상태를 안 보고 "로그인" 버튼을 늘 그리고 있었고, 그래서
// 로그인한 사람이 여행 만들기로 들어가면 세션이 풀린 것처럼 보였다(S15P21E201-763).
// 세션이 실제로 끊긴 적은 없다 — 이 화면들은 API 를 부르지 않는다.
//
// `ready` 를 함께 보는 이유: 앱이 뜰 때 저장된 세션을 되살리는 동안에는 accessToken 이
// 잠시 null 이다. 그때 버튼을 그리면 로그인한 사람에게 "로그인" 이 한 번 번쩍인다.
export function PlanWebNav() {
  const { kind } = useLayout();
  const router = useRouter();
  const { tx } = useI18n();
  const { accessToken, ready } = useAuth();
  const signedOut = ready && !accessToken;
  if (kind !== 'tablet') return null;
  return <View style={styles.nav}>
    <BrandLogoLink href="/" imageStyle={styles.logo} />
    <View style={styles.links}>{LINKS.map((item) => <Pressable key={item.path} accessibilityRole="link" onPress={() => router.push(item.path)} style={styles.link}><Text variant="caption" weight={item.path === '/plan/basic' ? 'bold' : 'regular'} color={item.path === '/plan/basic' ? color.text.heading : color.text.muted}>{tx(item.labelKo, item.labelEn)}</Text></Pressable>)}{signedOut ? <Pressable accessibilityRole="link" onPress={() => router.push('/sign-in')} style={styles.login}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('로그인', 'Sign in')}</Text></Pressable> : null}</View>
  </View>;
}

const styles = StyleSheet.create({ nav: { minHeight: 64, paddingHorizontal: 64, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: color.surface.card, borderBottomWidth: 1, borderBottomColor: color.surface.field }, logo: { width: 120, height: 28 }, links: { flexDirection: 'row', alignItems: 'center', gap: spacing[6] }, link: { minHeight: 44, justifyContent: 'center' }, login: { minHeight: 36, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' } });
