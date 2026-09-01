// 02 메인 홈 · 15 내 정보 아래에 반복되는 하단 탭. 이 셸은 아직 React Navigation 의 진짜
// 탭 내비게이션이 아니라 Stack 하나뿐이라(app/_layout.tsx), 각 화면이 이 바를 직접 그려 붙인다.
// 홈·내 정보만 대응하는 화면이 있어 그 둘만 누르면 이동한다 — 일정·지도·저장은 이 작업
// 범위의 화면이 아니라 아직 도착지가 없어 눌러도 아무 일도 안 일어난다.
import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, spacing } from '@/design/tokens';
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
  { key: 'schedule', icon: '✓', label: '일정' },
  { key: 'map', icon: '⌖', label: '지도' },
  { key: 'saved', icon: '♡', label: '저장' },
  { key: 'me', icon: '●', label: '내 정보', route: '/me' },
];

export function TabBar({ active }: { active: TabKey }) {
  const router = useRouter();

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
              {tab.label}
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
