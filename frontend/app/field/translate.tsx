// 22 현장 도구·번역 — Figma 22_현장 도구·번역 실측 그대로.
//
// S15P21E201-907: "양방향 음성 통역"·"메뉴판 카메라 번역"은 이번 배포에서 뺐다(제품 결정,
// 2026-09-13) — 번역·음성 API 업체가 아직 안 정해져(S15P21E201-77) 출시 전까지 한 줄도
// 구현되지 않을 것으로 보여서, "준비 중" 카드로 남겨 두는 대신 통째로 뺐다. 이전에 뺀
// 지금 갈 곳·축제·기념품샵과 달리 이 둘은 애초에 구현된 적이 없어(onPress 자체가 없었다)
// 되살릴 화면이 없다 — 업체가 정해지면 그때 새로 만든다.
// "장소별 한국어"는 이미 만든 17 현장 말하기 화면(phrase 카드)과 같은 기능이라 그리로 잇고,
// "날씨·준비물"은 16 여행 준비 화면으로 잇는다.
import { Image, Pressable, StyleSheet, View, type ImageSourcePropType } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { useI18n } from '@/i18n';

const sunIcon = require('../../assets/icons/common/sun.png');

type Tool = {
  key: string;
  icon: string | ImageSourcePropType;
  title: string;
  desc: string;
  onPress: () => void;
};

export default function Translate() {
  const router = useRouter();
  const { tx } = useI18n();

  const tools: Tool[] = [
    {
      // 🔴 2026-09-16 — 아래 머리말이 "업체가 정해지면 그때 새로 만든다" 고 적어 둔 그것이다.
      // 업체가 정해진 것이 아니라, 이미 우리 저장소에서 돌고 있던 것을 찾았다 —
      // visual-geocode 가 사진 속 간판 글씨를 읽는 데 쓰는 GMS(교육용 API 중계)다.
      // "번역"이 아니라 "읽기"로 범위를 좁혔다. 지어내지 않는 만큼만 한다 (S15P21E201-329).
      key: 'menu',
      icon: '판',
      title: tx('메뉴판 읽기', 'Read a menu'),
      desc: tx('찍으면 적힌 글자를 읽어 드려요. 알레르기 낱말도 같이 찾아요', 'Take a photo and we read the text, including allergy-related words'),
      onPress: () => router.push('/field/menu-scan'),
    },
    {
      key: 'phrase',
      icon: '말',
      title: tx('장소별 한국어', 'Korean phrases by situation'),
      desc: tx('택시·식당에서 바로 보여주는 문장', 'Sentences to show right away at taxis and restaurants'),
      onPress: () => router.push('/field/speak'),
    },
    {
      // 🔴 백엔드(GET /api/v1/exchange-rates, S15P21E201-1079)가 있는데 프론트가 없던 자리다.
      // 외국인이 부산에서 가장 자주 하는 계산이라 현장 도구의 첫 줄 가까이에 둔다.
      key: 'exchange',
      icon: '₩',
      title: tx('환율 계산', 'Currency'),
      desc: tx('가격표를 보고 바로 내 돈으로 바꿔 보세요', 'Turn a price tag into your own money'),
      onPress: () => router.push('/field/exchange'),
    },
    {
      // 🔴 백엔드(GET /api/v1/transit/nearby-bus-arrivals, S15P21E201-988)가 있는데 프론트가
      // 없던 자리다. 정류소 앞에서 하는 판단은 "기다릴까, 택시 탈까" 하나라 현장 도구에 둔다.
      key: 'bus',
      icon: '버',
      title: tx('주변 버스', 'Buses nearby'),
      desc: tx('몇 분 뒤에 오는지 보고 기다릴지 정하세요', 'See how long the wait is before you decide'),
      onPress: () => router.push('/field/bus'),
    },
    {
      key: 'weather',
      icon: sunIcon,
      title: tx('내 여행 날씨·준비물', 'Weather & packing for my trip'),
      desc: tx('여행을 고르면 출발일 예보와 준비물을 보여드려요', 'Choose a trip to see its departure forecast and packing tips'),
      // 준비 화면은 여행 식별자가 꼭 필요하다. 고정된 demo-trip을 넘기면 실제 사용자에게
      // 항상 "일정을 못 불러왔어요"가 보이므로, 먼저 본인의 여행을 고르게 한다.
      onPress: () => router.push({ pathname: '/trips', params: { open: 'prepare' } }),
    },
  ];

  return (
    <Screen scroll>
      <Text variant="display" weight="bold">
        {tx('현장 도구', 'On-the-go tools')}
      </Text>
      <Text variant="caption" style={styles.subtitle}>
        {tx('여행 중 필요한 기능을 한곳에서 바로 사용해요', 'Everything you need on your trip, in one place')}
      </Text>

      <View style={styles.list}>
        {tools.map((tool) => (
          <Pressable
            key={tool.key}
            accessibilityRole="button"
            onPress={tool.onPress}
            style={styles.card}
          >
            <View style={styles.iconBox}>
              {typeof tool.icon === 'string' ? (
                <Text variant="title" weight="bold" color={color.action.secondary}>
                  {tool.icon}
                </Text>
              ) : (
                <Image source={tool.icon} resizeMode="contain" style={styles.toolIconImage} />
              )}
            </View>
            <View style={styles.cardBody}>
              <Text variant="body" weight="bold">
                {tool.title}
              </Text>
              <Text variant="caption" style={styles.cardDesc}>
                {tool.desc}
              </Text>
            </View>
            <Text variant="title" weight="bold" color={color.action.secondary}>›</Text>
          </Pressable>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  subtitle: {
    marginTop: spacing[1],
    marginBottom: spacing[4],
    color: color.text.body,
  },
  list: {
    gap: spacing[3],
    marginTop: spacing[4],
  },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    padding: spacing[3],
  },
  iconBox: {
    width: 50,
    height: 50,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: color.surface.tint,
  },
  toolIconImage: { width: 24, height: 24 },
  cardBody: {
    flex: 1,
    gap: spacing[1],
  },
  cardDesc: {
    color: color.text.body,
  },
});
