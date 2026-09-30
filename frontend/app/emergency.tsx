// 긴급 도움 — S15P21E201-1879 (UI 캔버스 ㉒). AI 채팅 첫 화면에서 들어온다.
//
// 🔴 AI 가 만든 답이 아니다. 번호·언어·시간은 emergencyContacts.ts 에 공식 출처와 함께 적어 둔 것만 보인다.
import { Linking, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { EMERGENCY_LINES, SHOW_KOREAN } from '@/field/emergencyContacts';
import { ShowKoreanCard } from '@/field/ShowKoreanCard';
import { useI18n } from '@/i18n';

export default function Emergency() {
  const router = useRouter();
  const { tx } = useI18n();
  return <Screen scroll>
    <View style={styles.top}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/chat')} style={styles.back}><Text variant="title">‹</Text></Pressable>
      <BrandLogoLink imageStyle={styles.logo} />
      <View style={styles.spacer} />
    </View>
    <Text variant="display" weight="bold">{tx('긴급 도움', 'Emergency help')}</Text>
    <Text color={color.text.body} style={styles.lead}>{tx('누르면 바로 전화가 걸려요. 말이 안 통할까 걱정되면 1330에 먼저 거세요 — 통역해 주고, 급하면 119 신고도 도와줘요.', 'Tap to call. If you worry about the language, call 1330 first — they interpret and can help you reach 119.')}</Text>

    <View style={styles.lines}>
      {EMERGENCY_LINES.map((line) => (
        <Pressable
          key={line.number}
          testID={`emergency-call-${line.number}`}
          accessibilityRole="button"
          accessibilityLabel={`${line.number}, ${tx(line.title.ko, line.title.en)}`}
          accessibilityHint={tx('전화를 걸어요', 'Places a call')}
          onPress={() => void Linking.openURL(line.tel)}
          style={({ pressed }) => [styles.line, pressed && styles.pressed]}
        >
          <Text weight="bold" color={color.state.danger} style={styles.number}>{line.number}</Text>
          <View style={styles.lineBody}>
            <Text variant="body" weight="bold" color={color.text.heading}>{tx(line.title.ko, line.title.en)}</Text>
            <Text variant="caption" color={color.text.body}>{tx(line.what.ko, line.what.en)}</Text>
            <Text variant="caption" color={color.text.muted}>{tx(line.languages.ko, line.languages.en)}</Text>
          </View>
          <Text variant="caption" weight="bold" color={color.text.heading}>{tx('전화', 'Call')}</Text>
        </Pressable>
      ))}
    </View>

    <Pressable testID="emergency-lost-items" accessibilityRole="link" onPress={() => router.push('/lost-items')} style={({ pressed }) => [styles.lostRow, pressed && styles.pressed]}>
      <View style={styles.lineBody}>
        <Text variant="body" weight="bold" color={color.text.heading}>{tx('물건을 잃어버렸어요', 'I lost something')}</Text>
        <Text variant="caption" color={color.text.body}>{tx('택시·지하철·버스·길에서 — 어디에 물을지 알려 드려요', 'Taxi, subway, bus or street — where to ask')}</Text>
      </View>
      <Text variant="title" color={color.text.muted}>›</Text>
    </Pressable>

    <ShowKoreanCard testID="emergency-show-korean" korean={SHOW_KOREAN.help.ko} gloss={SHOW_KOREAN.help.gloss} />

    <Text variant="caption" color={color.text.muted} style={styles.foot}>{tx('AI가 만든 답이 아니에요 · 번호와 통역 언어는 공식 안내로 확인했어요(2026년 9월)', 'Not AI-generated · numbers and languages checked against official sources (Sep 2026)')}</Text>
  </Screen>;
}

const styles = StyleSheet.create({
  top: { minHeight: 52, marginBottom: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  logo: { width: 154, height: 28 },
  spacer: { width: 44 },
  lead: { marginTop: spacing[2], marginBottom: spacing[6], lineHeight: 24 },
  lines: { gap: spacing[3], marginBottom: spacing[4] },
  line: { flexDirection: 'row', alignItems: 'center', gap: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  number: { fontSize: 30, lineHeight: 36, minWidth: 76 },
  lineBody: { flex: 1, gap: 2 },
  lostRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[4], marginBottom: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  pressed: { opacity: 0.7 },
  foot: { marginTop: spacing[4], marginBottom: spacing[8] },
});
