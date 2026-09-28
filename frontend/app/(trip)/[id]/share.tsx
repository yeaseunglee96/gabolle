// 동행 초대 — 주소로 들어오는 옛 화면. 알맹이는 src/trip/TripInvitePanel.tsx 에 있고,
// 여행 페이지는 같은 것을 창으로 띄운다(S15P21E201-1561). 이 주소는 로그인 뒤 돌아오기가 쓴다.
import { Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { TripInvitePanel } from '@/trip/TripInvitePanel';

export default function TripShare() {
  const router = useRouter();
  const { tx } = useI18n();
  const { id } = useLocalSearchParams<{ id?: string }>();

  return <Screen scroll wide style={styles.screen}>
    <View style={styles.topBar}><Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/trips')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}><Text variant="title" weight="bold">‹</Text></Pressable><BrandLogoLink href="/home" imageStyle={styles.logo} /><View style={styles.spacer} /></View>
    {id ? <TripInvitePanel tripId={id} /> : null}
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.canvas }, topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, pressed: { opacity: 0.72, transform: [{ scale: 0.97 }] }, logo: { width: 154, height: 28 }, spacer: { width: 44 },
});
