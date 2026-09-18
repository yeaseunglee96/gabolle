// 02 메인 홈 15 내 정보 아래에 반복되는 하단 탭. 이 셸은 아직 React Navigation 의 진짜
// 탭 내비게이션이 아니라 Stack 하나뿐이라(app/_layout.tsx), 각 화면이 이 바를 직접 그려 붙인다.
// 1차 배포에서는 모든 탭이 유효한 화면으로 이동한다. 서버 데이터가 없어도 각 화면에서
// 빈 상태와 다음 행동을 안내해 사용자가 막히지 않게 한다.
import { Image, Platform, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { useI18n } from '@/i18n';
import { Text } from './Text';

export type TabKey = 'home' | 'feed' | 'schedule' | 'map' | 'saved' | 'me';

/** 막대 자체의 높이. */
export const TAB_BAR_HEIGHT = 64;

type Tab = {
  key: TabKey;
  icon: number;
  labelKo: string;
  labelEn: string;
  route?: string;
};

const TABS: Tab[] = [
  { key: 'home', icon: require('../../assets/icons/home/home.png'), labelKo: '홈', labelEn: 'Home', route: '/home' },
  { key: 'feed', icon: require('../../assets/icons/home/heart.png'), labelKo: '피드', labelEn: 'Feed', route: '/feed' },
  { key: 'schedule', icon: require('../../assets/icons/home/plus.png'), labelKo: '여행 만들기', labelEn: 'Create', route: '/plan' },
  { key: 'map', icon: require('../../assets/icons/home/map.png'), labelKo: '내 여행', labelEn: 'My trips', route: '/trips' },
  { key: 'me', icon: require('../../assets/icons/home/user.png'), labelKo: '마이페이지', labelEn: 'Profile', route: '/me' },
];

export function TabBar({ active }: { active: TabKey }) {
  const router = useRouter();
  const { tx } = useI18n();
  const { width } = useLayout();
  // : 이 바는 각 화면에서 Screen(SafeAreaView) 밖의 형제 노드로 그려져
  // 그 보호를 못 받는다 — 고정 margin만 쓰면 안드로이드 엣지투엣지 렌더링에서 기기
  // 시스템 하단 내비게이션 바(제스처바·버튼바)에 가려진다. 하단 인셋을 직접 더한다.
  const insets = useSafeAreaInsets();

  // 이 바는 휴대폰 폭(하단 고정 탭) 전용이다 — breakpoints.ts 의 반응형 표를 보면
  // 600px 부터는 상단 가로 바로 바뀌어야 한다. 그 화면은 아직 없으니, 없는 것을
  // 지어내 보여주는 대신 desktop 폭에서는 아무것도 안 그린다(home.tsx 의 데스크톱
  // 리다이렉트와 같은 판단). 화면 가운데 붕 뜬 모바일 탭바보다는 없는 쪽이 낫다.
  if (isAtLeast(width, 'md')) return null;

  // 받침(dock)에 담아 띄운다
  return (
    <View pointerEvents="box-none" style={[styles.dock, { paddingBottom: tabBarBottomMargin(insets.bottom) }]}>
    <View style={styles.bar}>
      {TABS.map((tab) => {
        const selected = tab.key === active;
        return (
          <Pressable
            key={tab.key}
            testID={`tab-${tab.key}`}
            accessibilityRole="tab"
            accessibilityLabel={tx(tab.labelKo, tab.labelEn)}
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
              {tx(tab.labelKo, tab.labelEn)}
            </Text>
          </Pressable>
        );
      })}
    </View>
    </View>
  );
}

export function tabBarBottomMargin(bottomInset: number) {
  // 안전영역 자체가 탭바와 시스템 바 사이의 간격이다. 고정 여백을 더하지 않고 최소 8px만 보장한다.
  return Math.max(spacing[2], bottomInset);
}

const styles = StyleSheet.create({
  // 받침 — 화면 아래에 깔리되 자기는 아무것도 안 그린다. 알약을 가운데 세우는 일만 한다.
  dock: {
    // `absolute` 는 「가장 가까운 배치된 조상」 기준이다. 이 앱의 화면은 대부분
    // `<View flex:1>` 안에 스크롤 영역과 탭바가 형제로 들어 있는데, 모바일 브라우저는
    // 문서 자체가 스크롤되고 주소창이 접히며 뷰포트 높이까지 바뀐다. 그래서 빠르게
    // 스크롤하면 탭바가 바닥에 안 붙고 내용과 같이 올라와 카드 위를 덮었다.
    position: Platform.OS === 'web' ? ('fixed' as 'absolute') : 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
  },
  bar: {
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'center',
    width: '100%',
    maxWidth: 328,
    height: TAB_BAR_HEIGHT,
    marginHorizontal: spacing[4],
    paddingHorizontal: spacing[2],
    backgroundColor: color.surface.card,
    borderWidth: 1,
    borderColor: color.surface.border,
    borderRadius: radius.lg,
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
