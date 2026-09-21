// 22 현장 도구·번역 — Figma 22_현장 도구·번역 실측 그대로.
import { isValidElement, type ReactElement } from 'react';
import { Image, Pressable, StyleSheet, View, type ImageSourcePropType } from 'react-native';
import Svg, { Circle, Path, Rect } from 'react-native-svg';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { useI18n } from '@/i18n';

const sunIcon = require('../../assets/icons/common/sun.png');
// 「판」「말」「버」 글자 아이콘은 무엇인지 한 번 더 읽어야 했다(2026-09-21 실측, S15P21E201-1372) — 그림으로.
const cameraIcon = require('../../assets/icons/common/camera.png');
const speakerIcon = require('../../assets/icons/common/speaker.png');

function BusIcon() {
  return (
    <Svg width={24} height={24} viewBox="0 0 24 24" fill="none">
      <Rect x={4} y={3.5} width={16} height={15} rx={3} stroke={color.action.secondary} strokeWidth={1.9} />
      <Path d="M4 10.5h16M7 18.5v2M17 18.5v2" stroke={color.action.secondary} strokeWidth={1.9} strokeLinecap="round" />
      <Circle cx={8} cy={14.5} r={1.2} fill={color.action.secondary} />
      <Circle cx={16} cy={14.5} r={1.2} fill={color.action.secondary} />
    </Svg>
  );
}

type Tool = {
  key: string;
  icon: string | ImageSourcePropType | ReactElement;
  title: string;
  desc: string;
  onPress: () => void;
};

export default function Translate() {
  const router = useRouter();
  const { tx } = useI18n();

  const tools: Tool[] = [
    {
      key: 'menu',
      icon: cameraIcon,
      title: tx('메뉴판 읽기', 'Read a menu'),
      desc: tx('찍으면 적힌 글자를 읽어 드려요. 알레르기 낱말도 같이 찾아요', 'Take a photo and we read the text, including allergy-related words'),
      onPress: () => router.push('/field/menu-scan'),
    },
    {
      key: 'phrase',
      icon: speakerIcon,
      title: tx('장소별 한국어', 'Korean phrases by situation'),
      desc: tx('택시·식당에서 바로 보여주는 문장', 'Sentences to show right away at taxis and restaurants'),
      onPress: () => router.push('/field/speak'),
    },
    {
      // 백엔드(GET /api/v1/exchange-rates,가 있는데 프론트가 없던 자리다.
      // 외국인이 부산에서 가장 자주 하는 계산이라 현장 도구의 첫 줄 가까이에 둔다.
      key: 'exchange',
      icon: '₩',
      title: tx('환율 계산', 'Currency'),
      desc: tx('가격표를 보고 바로 내 돈으로 바꿔 보세요', 'Turn a price tag into your own money'),
      onPress: () => router.push('/field/exchange-rate'),
    },
    {
      // 백엔드(GET /api/v1/transit/nearby-bus-arrivals,가 있는데 프론트가
      // 없던 자리다. 정류소 앞에서 하는 판단은 "기다릴까, 택시 탈까" 하나라 현장 도구에 둔다.
      key: 'bus',
      icon: <BusIcon />,
      title: tx('주변 버스', 'Buses nearby'),
      desc: tx('몇 분 뒤에 오는지 보고 기다릴지 정하세요', 'See how long the wait is before you decide'),
      onPress: () => router.push('/field/transit'),
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
            // 🔴 자동화가 찾는 이름표. 화면 글자는 언어를 바꾸면 통째로 달라지지만
            //    이 이름은 안 변한다 — src/components/__tests__/automationTestIds.test.ts 참고.
            testID={`field-${tool.key}`}
            accessibilityRole="button"
            onPress={tool.onPress}
            style={styles.card}
          >
            <View style={styles.iconBox}>
              {typeof tool.icon === 'string' ? (
                <Text variant="title" weight="bold" color={color.action.secondary}>
                  {tool.icon}
                </Text>
              ) : isValidElement(tool.icon) ? (
                tool.icon
              ) : (
                <Image source={tool.icon as ImageSourcePropType} resizeMode="contain" style={styles.toolIconImage} />
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
    backgroundColor: color.surface.blush,
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
