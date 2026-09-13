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

const DEMO_TRIP_ID = 'demo-trip';
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
      key: 'phrase',
      icon: '말',
      title: tx('장소별 한국어', 'Korean phrases by situation'),
      desc: tx('택시·식당에서 바로 보여주는 문장', 'Sentences to show right away at taxis and restaurants'),
      onPress: () => router.push('/field/speak'),
    },
    {
      key: 'weather',
      icon: sunIcon,
      title: tx('날씨·준비물', 'Weather & what to bring'),
      desc: tx('기상청 예보 기반 우산·옷차림 안내', 'Umbrella and clothing tips based on the weather forecast'),
      onPress: () => router.push(`/${DEMO_TRIP_ID}/prepare`),
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
