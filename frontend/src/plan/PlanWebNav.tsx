import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { useI18n } from '@/i18n';

const LINKS = [{ label: '홈', path: '/home' }, { label: '여행 만들기', path: '/plan/basic' }, { label: '내 여행', path: '/trips' }, { label: '마이페이지', path: '/me' }] as const;

export function PlanWebNav() {
  const { kind } = useLayout();
  const router = useRouter();
  const { language, tx } = useI18n();
  if (kind !== 'tablet') return null;
  return <View style={styles.nav}>
    <BrandLogoLink href="/" imageStyle={styles.logo} />
    <View style={styles.links}>{LINKS.map((item, index) => <Pressable key={item.path} accessibilityRole="link" onPress={() => router.push(item.path)} style={styles.link}><Text variant="caption" weight={item.path === '/plan/basic' ? 'bold' : 'regular'} color={item.path === '/plan/basic' ? color.text.heading : color.text.muted}>{language === 'ko' ? item.label : ['Home', 'Plan a trip', 'My trips', 'Profile'][index]}</Text></Pressable>)}<Pressable accessibilityRole="link" onPress={() => router.push('/sign-in')} style={styles.login}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('로그인', 'Sign in')}</Text></Pressable></View>
  </View>;
}

const styles = StyleSheet.create({ nav: { minHeight: 64, paddingHorizontal: 64, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: color.surface.card, borderBottomWidth: 1, borderBottomColor: color.surface.field }, logo: { width: 120, height: 28 }, links: { flexDirection: 'row', alignItems: 'center', gap: spacing[6] }, link: { minHeight: 44, justifyContent: 'center' }, login: { minHeight: 36, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' } });
