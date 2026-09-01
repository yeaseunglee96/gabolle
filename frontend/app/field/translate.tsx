// 22 현장 도구·번역 — Figma 22_현장 도구·번역 실측 그대로.
//
// 번역·음성 API 업체가 아직 안 정해졌다(Jira S15P21E201-77). "메뉴판 카메라 번역"·
// "양방향 음성 통역" 은 그 업체가 정해져야 만들 수 있어 지금은 눌러도 이동하지 않는다.
// "장소별 한국어" 는 이미 만든 17 현장 말하기 화면(phrase 카드)과 같은 기능이라 그리로 잇고,
// "날씨·준비물" 은 16 여행 준비 화면으로 잇는다.
import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Button } from '@/components/Button';

const DEMO_TRIP_ID = 'demo-trip';

type Tool = {
  key: string;
  icon: string;
  title: string;
  desc: string;
  tinted?: boolean;
  onPress?: () => void;
};

export default function Translate() {
  const router = useRouter();

  const tools: Tool[] = [
    {
      key: 'menu-camera',
      icon: '▣',
      title: '메뉴판 카메라 번역',
      desc: '사진을 찍으면 음식명·가격·알레르기를 번역',
      tinted: true,
      // TODO: 번역 업체 미정(S15P21E201-77) — 카메라 진입이 아직 없다.
    },
    {
      key: 'voice',
      icon: '◉',
      title: '양방향 음성 통역',
      desc: '한국어 ↔ English 실시간 대화',
      // TODO: 실시간 음성 통역 업체 미정(S15P21E201-77).
    },
    {
      key: 'phrase',
      icon: '말',
      title: '장소별 한국어',
      desc: '택시·식당에서 바로 보여주는 문장',
      onPress: () => router.push('/field/speak'),
    },
    {
      key: 'weather',
      icon: '☀',
      title: '날씨·준비물',
      desc: '기상청 예보 기반 우산·옷차림 안내',
      onPress: () => router.push(`/${DEMO_TRIP_ID}/prepare`),
    },
  ];

  return (
    <Screen scroll>
      <Text variant="display" weight="bold">
        현장 도구
      </Text>
      <Text variant="caption" style={styles.subtitle}>
        여행 중 필요한 기능을 한곳에서 바로 사용해요
      </Text>

      <View style={styles.list}>
        {tools.map((tool) => (
          <Pressable
            key={tool.key}
            disabled={!tool.onPress}
            onPress={tool.onPress}
            style={[styles.card, tool.tinted && styles.cardTinted]}
          >
            <View style={[styles.iconBox, tool.tinted ? styles.iconBoxDark : styles.iconBoxLight]}>
              <Text variant="title" weight="bold" color={tool.tinted ? color.text.onAction : color.action.secondary}>
                {tool.icon}
              </Text>
            </View>
            <View style={styles.cardBody}>
              <Text variant="body" weight="bold">
                {tool.title}
              </Text>
              <Text variant="caption" style={styles.cardDesc}>
                {tool.desc}
              </Text>
            </View>
            <Text variant="title" weight="bold" color={color.action.secondary}>
              ›
            </Text>
          </Pressable>
        ))}
      </View>

      {/* TODO: 번역 업체 미정(S15P21E201-77) — 카메라 진입이 아직 없다. */}
      <Button label="카메라로 메뉴판 번역 시작" variant="secondary" containerStyle={styles.cta} />
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
  },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    padding: spacing[3],
  },
  cardTinted: {
    backgroundColor: color.surface.tint,
  },
  iconBox: {
    width: 50,
    height: 50,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconBoxLight: {
    backgroundColor: color.surface.tint,
  },
  iconBoxDark: {
    backgroundColor: color.action.secondary,
  },
  cardBody: {
    flex: 1,
    gap: spacing[1],
  },
  cardDesc: {
    color: color.text.body,
  },
  cta: {
    marginTop: spacing[6],
  },
});
