// 02 메인 홈 · 15 내 정보 아래에 반복되는 하단 탭. 이 셸은 아직 React Navigation 의 진짜
// 탭 내비게이션이 아니라 Stack 하나뿐이라(app/_layout.tsx), 각 화면이 이 바를 직접 그려 붙인다.
// 1차 배포에서는 모든 탭이 유효한 화면으로 이동한다. 서버 데이터가 없어도 각 화면에서
// 빈 상태와 다음 행동을 안내해 사용자가 막히지 않게 한다.
import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { Text } from './Text';

export type TabKey = 'home' | 'schedule' | 'map' | 'saved' | 'me';

type Tab = {
  key: TabKey;
  icon: string;
  label: string;
  route?: string;
};

// 아이콘은 Figma 15 화면이 이미 단순 글자(⌂ ✓ ⌖ ♡ ●)로 뽑혀 있어 그대로 쓴다 —
// 02 화면은 커스텀 벡터 아이콘인데, 아이콘 라이브러리를 새로 추가하지 않기로 했으므로
// 두 화면이 같은 글자 아이콘 세트를 공유한다.
const TABS: Tab[] = [
  { key: 'home', icon: '⌂', label: '홈', route: '/home' },
  { key: 'schedule', icon: '✓', label: '일정', route: '/trips' },
  { key: 'map', icon: '⌖', label: '지도', route: '/map' },
  { key: 'saved', icon: '♡', label: '저장', route: '/saved' },
  { key: 'me', icon: '●', label: '내 정보', route: '/me' },
];

export function TabBar({ active }: { active: TabKey }) {
  const router = useRouter();
  const { language } = useI18n();
  const englishLabels: Record<TabKey, string> = { home: 'Home', schedule: 'Trips', map: 'Map', saved: 'Saved', me: 'Profile' };

  return (
    <View style={styles.bar}>
      {TABS.map((tab) => {
        const selected = tab.key === active;
        const tint = selected ? color.action.brand : color.text.muted;
        return (
          <Pressable
            key={tab.key}
            style={styles.item}
            disabled={!tab.route}
            onPress={() => {
              if (tab.route) router.push(tab.route);
            }}
          >
            <Text variant="body" weight="bold" color={tint}>
              {tab.icon}
            </Text>
            <Text variant="caption" weight={selected ? 'bold' : 'regular'} color={tint}>
              {language === 'en' ? englishLabels[tab.key] : tab.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    backgroundColor: color.surface.card,
    paddingVertical: spacing[2],
    borderTopWidth: 1,
    borderTopColor: color.surface.field,
  },
  item: {
    flex: 1,
    alignItems: 'center',
    gap: spacing[1],
  },
});
