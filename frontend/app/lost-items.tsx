// 물건을 잃어버렸어요 — S15P21E201-1879 (UI 캔버스 ㉒). 긴급 도움에서 들어온다.
//
// 🔴 어디서 잃어버렸는지에 따라 물을 곳이 다르다. 번호·주소는 emergencyContacts.ts 의 공식 출처 확인분만 쓴다 —
//    부산 택시 회사 번호는 공식 출처를 못 찾아서 적지 않고, 차량 번호로 묻는 길과 1330 대리 문의만 알린다.
import { Linking, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { METRO_LOST_FOUND, POLICE_LOST_FOUND_URL, SHOW_KOREAN } from '@/field/emergencyContacts';
import { ShowKoreanCard } from '@/field/ShowKoreanCard';
import { useI18n } from '@/i18n';

type Case = { key: string; title: [string, string]; body: [string, string]; action?: { label: [string, string]; url: string; testID: string } };

export default function LostItems() {
  const router = useRouter();
  const { tx } = useI18n();
  const cases: Case[] = [
    {
      key: 'taxi', title: ['택시에서', 'In a taxi'],
      body: ['카드·앱 결제 내역에 차량 번호가 있어요. 그 번호로 택시 앱이나 회사에 물어보세요. 번호를 모르면 1330이 대신 물어봐 줘요.', 'Your card or app receipt shows the car number — ask the taxi app or company with it. If you do not have it, 1330 can ask for you.'],
      action: { label: ['1330 전화', 'Call 1330'], url: 'tel:1330', testID: 'lost-call-1330' },
    },
    {
      key: 'metro', title: ['지하철에서', 'On the subway'],
      // 🔴 문장을 이어 붙이지 않는다 — 번역표 열쇠가 한국어 문장 그대로라, 이어 붙이면 일본어·중국어에서 영어로 떨어진다.
      body: ['부산도시철도 유실물센터 — 서면역 지하 2층 1·2호선 환승 통로 · 평일 09:00~18:00 · 7일 보관 뒤 경찰로 넘어가요. 몇 호선, 몇 시쯤 탔는지 알려 주면 찾기 쉬워요.', 'Busan Metro Lost & Found — Seomyeon Station B2, Line 1–2 transfer passage · weekdays 9am–6pm · kept 7 days, then handed to the police. Tell them the line and roughly when you rode.'],
      action: { label: ['051-640-7339 전화', 'Call 051-640-7339'], url: METRO_LOST_FOUND.tel, testID: 'lost-call-metro' },
    },
    {
      key: 'bus', title: ['버스에서', 'On a bus'],
      body: ['버스 번호와 내린 시간·정류장을 적어 두세요. 1330에 말하면 대신 물어봐 줘요.', 'Note the bus number, when and where you got off. 1330 can make the call for you.'],
      action: { label: ['1330 전화', 'Call 1330'], url: 'tel:1330', testID: 'lost-call-1330-bus' },
    },
    {
      key: 'other', title: ['길·가게 등 그 밖', 'Street, shop or elsewhere'],
      body: ['경찰·지하철·버스에 맡겨진 물건은 경찰민원24 유실물 검색에 모여요(옛 LOST112).', 'Items handed to the police, subway or buses are listed in Police Minwon24 lost & found (formerly LOST112).'],
      action: { label: ['유실물 검색 열기', 'Open lost & found search'], url: POLICE_LOST_FOUND_URL, testID: 'lost-open-police' },
    },
  ];
  return <Screen scroll>
    <View style={styles.top}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/emergency')} style={styles.back}><Text variant="title">‹</Text></Pressable>
      <BrandLogoLink imageStyle={styles.logo} />
      <View style={styles.spacer} />
    </View>
    <Text variant="display" weight="bold">{tx('물건을 잃어버렸어요', 'I lost something')}</Text>
    <Text color={color.text.body} style={styles.lead}>{tx('어디서 잃어버렸는지에 따라 물을 곳이 달라요.', 'Where to ask depends on where you lost it.')}</Text>

    <View style={styles.cases}>
      {cases.map((item) => (
        <View key={item.key} style={styles.case}>
          <Text variant="body" weight="bold" color={color.text.heading}>{tx(...item.title)}</Text>
          <Text variant="caption" color={color.text.body} style={styles.caseBody}>{tx(...item.body)}</Text>
          {item.action ? (
            <Pressable testID={item.action.testID} accessibilityRole={item.action.url.startsWith('tel:') ? 'button' : 'link'} onPress={() => void Linking.openURL(item.action!.url)} style={({ pressed }) => [styles.action, pressed && styles.pressed]}>
              <Text variant="caption" weight="bold" color={color.text.heading}>{tx(...item.action.label)} ›</Text>
            </Pressable>
          ) : null}
        </View>
      ))}
    </View>

    <ShowKoreanCard testID="lost-show-korean" korean={SHOW_KOREAN.lost.ko} gloss={SHOW_KOREAN.lost.gloss} />

    <Text variant="caption" color={color.state.danger} style={styles.danger}>{tx('도둑맞았거나 위험하면 뒤로 가서 112에 바로 전화하세요.', 'If it was stolen or you are in danger, go back and call 112 right away.')}</Text>
  </Screen>;
}

const styles = StyleSheet.create({
  top: { minHeight: 52, marginBottom: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  logo: { width: 154, height: 28 },
  spacer: { width: 44 },
  lead: { marginTop: spacing[2], marginBottom: spacing[6], lineHeight: 24 },
  cases: { gap: spacing[3], marginBottom: spacing[4] },
  case: { gap: spacing[1], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  caseBody: { lineHeight: 20 },
  action: { alignSelf: 'flex-start', minHeight: 40, justifyContent: 'center', marginTop: spacing[2], paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.tint },
  pressed: { opacity: 0.7 },
  danger: { marginTop: spacing[4], marginBottom: spacing[8] },
});
