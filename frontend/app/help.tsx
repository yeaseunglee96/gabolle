// 도움말·문의 화면.
//
// 본문은 HelpBody 가 그린다 — 마이페이지에서 패널로 열 때도 같은 것을 쓴다.
import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { HelpBody } from '@/me/panels/HelpBody';

export default function Help() {
  const router = useRouter();
  const { tx } = useI18n();
  return <Screen scroll>
    <View style={styles.top}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/me')} style={styles.back}><Text variant="title">‹</Text></Pressable>
      <BrandLogoLink imageStyle={styles.logo} />
      <View style={styles.spacer} />
    </View>
    <Eyebrow>{tx('처음이어도 괜찮아요', 'Start here')}</Eyebrow>
    <Text variant="display" weight="bold" style={styles.title}>{tx('도움말·문의', 'Help & support')}</Text>
    <Text color={color.text.body} style={styles.lead}>{tx('가볼래가 무엇을 하는지 다시 보고, 막힌 문제를 해결해 보세요.', 'Replay the app tour or find an answer when you get stuck.')}</Text>
    <HelpBody />
  </Screen>;
}

const styles = StyleSheet.create({
  top: { minHeight: 52, marginBottom: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  logo: { width: 96, height: 28 },
  spacer: { width: 44 },
  title: { marginTop: spacing[2] },
  lead: { marginTop: spacing[2], marginBottom: spacing[6], lineHeight: 24 },
});
