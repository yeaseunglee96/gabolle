// 02 메인 홈 · 15 내 정보 아래에 반복되는 하단 탭. 이 셸은 아직 React Navigation 의 진짜
// 탭 내비게이션이 아니라 Stack 하나뿐이라(app/_layout.tsx), 각 화면이 이 바를 직접 그려 붙인다.
// 1차 배포에서는 모든 탭이 유효한 화면으로 이동한다. 서버 데이터가 없어도 각 화면에서
// 빈 상태와 다음 행동을 안내해 사용자가 막히지 않게 한다.
import { Image, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { Text } from './Text';

export type TabKey = 'home' | 'schedule' | 'map' | 'saved' | 'me';

type Tab = {
  key: TabKey;
  icon: number;
  label: string;
  route?: string;
};

// APP 01에서 내보낸 실제 아이콘을 사용한다.
const TABS: Tab[] = [
  { key: 'home', icon: require('../../assets/icons/home/home.png'), label: '홈', route: '/home' },
  { key: 'schedule', icon: require('../../assets/icons/home/plus.png'), label: '여행 만들기', route: '/plan/basic' },
  { key: 'map', icon: require('../../assets/icons/home/map.png'), label: '내 여행', route: '/trips' },
  { key: 'me', icon: require('../../assets/icons/home/user.png'), label: '마이페이지', route: '/me' },
];

export function TabBar({ active }: { active: TabKey }) {
  const router = useRouter();
  const { language } = useI18n();
  const englishLabels: Record<TabKey, string> = { home: 'Home', schedule: 'Create', map: 'My trips', saved: 'Saved', me: 'Profile' };

  return (
    <View style={styles.bar}>
      {TABS.map((tab) => {
        const selected = tab.key === active;
        return (
          <Pressable
            key={tab.key}
            accessibilityRole="tab"
            accessibilityLabel={language === 'en' ? englishLabels[tab.key] : tab.label}
            accessibilityState={{ selected, disabled: !tab.route }}
            style={({ pressed }) => [styles.item, pressed && styles.itemPressed]}
            disabled={!tab.route}
            onPress={() => {
              if (tab.route) router.replace(tab.route);
            }}
          >
            {selected && <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.activeMarker} />}
            <View style={[styles.iconWrap, tab.key === 'schedule' && styles.createIconWrap]}><Image source={tab.icon} resizeMode="contain" style={[styles.icon, tab.key !== 'schedule' && (selected ? styles.iconSelected : styles.iconInactive)]} /></View>
            <Text variant="caption" weight={selected ? 'bold' : 'regular'} color={selected ? color.brand.navy : color.text.muted}>
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
    alignItems: 'center',
    alignSelf: 'center',
    width: '100%',
    maxWidth: 328,
    height: 64,
    marginHorizontal: spacing[4],
    marginBottom: spacing[2],
    paddingHorizontal: spacing[2],
    backgroundColor: color.surface.card,
    borderWidth: 1,
    borderColor: '#f0ebe3',
    borderRadius: 20,
    shadowColor: color.brand.navy,
    shadowOpacity: 0.13,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: -2 },
    elevation: 8,
  },
  item: {
    position: 'relative',
    flex: 1,
    minHeight: 53,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing[1],
  },
  itemPressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  activeMarker: { position: 'absolute', top: 0, width: 18, height: 3, borderRadius: 2, backgroundColor: color.brand.orange },
  iconWrap: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center' },
  createIconWrap: { width: 32, height: 32, borderRadius: 16, backgroundColor: color.brand.orange },
  icon: { width: 20, height: 20 },
  iconSelected: { tintColor: color.brand.navy },
  iconInactive: { tintColor: color.text.muted },
});
