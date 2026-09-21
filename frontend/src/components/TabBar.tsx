// 02 메인 홈 · 15 내 정보 아래에 반복되는 하단 탭. 이 셸은 아직 React Navigation 의 진짜
// 탭 내비게이션이 아니라 Stack 하나뿐이라(app/_layout.tsx), 각 화면이 이 바를 직접 그려 붙인다.
// 1차 배포에서는 모든 탭이 유효한 화면으로 이동한다. 서버 데이터가 없어도 각 화면에서
// 빈 상태와 다음 행동을 안내해 사용자가 막히지 않게 한다.
import { useEffect, useRef } from 'react';
import { Animated, Easing, Image, Platform, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { useI18n } from '@/i18n';
import { Text } from './Text';

export type TabKey = 'home' | 'feed' | 'schedule' | 'map' | 'saved' | 'me';

/** 막대 자체의 높이. */
export const TAB_BAR_HEIGHT = 64;

/**
 * 지도 시트로 늘어났을 때의 기본 높이. 피드의 시안 04b 값이다.
 *
 * 🔴 화면마다 다르다 — 마이페이지 시트는 화면을 거의 다 채운다(시안 06). 그래서 값을
 *    받고, 안 주면 이 값을 쓴다. 여기 박아 두면 두 화면 중 하나는 반드시 틀린다.
 */
export const TAB_BAR_SHEET_HEIGHT = 560;

const BAR_MAX_WIDTH = 328;
const SHEET_MAX_WIDTH = 361;

/** 늘어나고 줄어드는 데 걸리는 시간. 시안의 .42s cubic-bezier(.34,1.3,.64,1). */
const GROW_MS = 420;

/** 시트가 열릴 때 탭바가 내려가는 시간과 거리. 자기 높이(64)보다 넉넉히 내려 그림자까지 치운다. */
const HIDE_MS = 500;
const HIDE_DROP = 110;
/** 탭 항목이 사라지는 시간. 자라는 것보다 빨리 비켜 준다. */
const FADE_MS = 200;

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

export function TabBar({
  active,
  expanded = false,
  hidden = false,
  onCollapse,
  children,
  sheetHeight = TAB_BAR_SHEET_HEIGHT,
}: {
  active: TabKey;
  /**
   * 이 막대가 시트로 늘어나 있는가. **피드 화면만 쓴다.**
   *
   * 기본값이 false 라서 아무것도 안 넘기는 화면은 지금과 똑같이 동작한다 — 이 부품은
   * 홈·내 여행·마이페이지가 전부 그리므로, 그쪽 동작이 달라지면 안 된다.
   */
  expanded?: boolean;
  /** 시트를 내릴 때 부른다. 안을 누르는 자리(손잡이·칩)는 화면이 직접 연결한다. */
  onCollapse?: () => void;
  /** 늘어났을 때 안에 그릴 것. */
  children?: React.ReactNode;
  /** 늘어났을 때의 높이. 화면마다 다르다 — 지도 시트 560, 마이페이지 시트는 거의 전체. */
  sheetHeight?: number;
  /**
   * 아래로 미끄러져 사라지는가. **홈의 시작 바 시트만 쓴다.**
   *
   * <p>`expanded` 와 다르다 — 그쪽은 이 막대가 «시트로 자라는» 것이고, 이쪽은 막대가
   * «치워지는» 것이다. 화면 전체를 덮는 시트가 올라올 때, 그 위에 탭바가 남아 있으면
   * 시트 바닥의 단추와 자리를 다툰다.
   *
   * <p>기본값이 false 라 아무것도 안 넘기는 화면은 지금과 똑같이 동작한다.
   */
  hidden?: boolean;
}) {
  const router = useRouter();
  const { tx } = useI18n();
  const { kind } = useLayout();
  const grow = useRef(new Animated.Value(0)).current;
  /** 0 이면 제자리, 1 이면 화면 아래로 내려가 사라진 상태. */
  const hide = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(grow, {
      toValue: expanded ? 1 : 0,
      duration: GROW_MS,
      easing: Easing.bezier(0.34, 1.3, 0.64, 1),
      // 🔴 높이는 네이티브 드라이버로 못 움직인다. 켜면 폰에서 아예 안 자란다.
      useNativeDriver: false,
    }).start();
  }, [expanded, grow]);

  useEffect(() => {
    Animated.timing(hide, {
      toValue: hidden ? 1 : 0,
      duration: HIDE_MS,
      easing: Easing.bezier(0.22, 1, 0.36, 1),
      // grow 와 같은 이유로 네이티브 드라이버를 안 쓴다 — 한 부품 안에서 드라이버를
      // 섞으면 웹에서 경고가 난다.
      useNativeDriver: false,
    }).start();
  }, [hidden, hide]);
  // : 이 바는 각 화면에서 Screen(SafeAreaView) 밖의 형제 노드로 그려져
  // 그 보호를 못 받는다 — 고정 margin만 쓰면 안드로이드 엣지투엣지 렌더링에서 기기
  // 시스템 하단 내비게이션 바(제스처바·버튼바)에 가려진다. 하단 인셋을 직접 더한다.
  const insets = useSafeAreaInsets();

  // 이 바는 폰 전용이다. 태블릿에서는 상단 바(TopNav)가 그 자리를 맡는다.
  //
  // 🔴 숨기는 기준은 TopNav 와 **같은 것**(useLayout 의 kind)이어야 한다 — 2026-09-21.
  //    전에는 여기가 폭(width > 599)이고 TopNav 는 kind(짧은 변 >= 600)였다. 기준이
  //    둘이면 둘 다 안 그리는 구간이 생긴다 — 아이폰 가로(932×430)가 그랬다. 폭은 932 라
  //    이 바가 숨고, 짧은 변은 430 이라 TopNav 도 안 떠서, 화면 안 「‹」 말고는 어디로도
  //    못 갔다(app.json 이 orientation: default 라 실제로 돌아간다). kind 는 짧은 변으로
  //    정하므로 돌려도 안 바뀌고, 폴드는 펼칠 때 짧은 변이 커지므로 그때만 태블릿이 된다
  //    (frontend/CLAUDE.md 의 「폭 분기는 useLayout」 규칙이 바로 이 자리다).
  if (kind === 'tablet') return null;

  // 받침(dock)에 담아 띄운다.
  return (
    <Animated.View
      // 🔴 다 내려가면 안 보일 뿐 아니라 «눌리지도» 않아야 한다. 투명도만 0으로 두면
      //    시트 바닥의 「일정 물어보기」를 누르려던 손가락이 내려간 탭바를 누른다.
      pointerEvents={hidden ? 'none' : 'box-none'}
      style={[
        styles.dock,
        { paddingBottom: tabBarBottomMargin(insets.bottom) },
        {
          opacity: hide.interpolate({ inputRange: [0, 1], outputRange: [1, 0] }),
          transform: [{ translateY: hide.interpolate({ inputRange: [0, 1], outputRange: [0, HIDE_DROP] }) }],
        },
      ]}
    >
    <Animated.View
      style={[
        styles.bar,
        {
          height: grow.interpolate({ inputRange: [0, 1], outputRange: [TAB_BAR_HEIGHT, sheetHeight] }),
          maxWidth: grow.interpolate({ inputRange: [0, 1], outputRange: [BAR_MAX_WIDTH, SHEET_MAX_WIDTH] }),
        },
      ]}
    >
      {/* 시트 내용 — 막대를 덮는다. 자라는 동안 탭 항목과 같은 자리를 다투지 않게
          절대 위치로 띄운다(시안도 그렇게 그린다). */}
      {children ? (
        <Animated.View
          // 🔴 안 보이는 동안에는 눌리지도 않아야 한다. 투명도만 0으로 두면 지도를
          //    누르려던 손가락이 탭을 누르고, 화면 읽기 프로그램은 둘 다 읽는다.
          pointerEvents={expanded ? 'auto' : 'none'}
          style={[StyleSheet.absoluteFill, styles.sheet, { opacity: grow }]}
        >
          {children}
        </Animated.View>
      ) : null}

      <Animated.View
        pointerEvents={expanded ? 'none' : 'auto'}
        style={[
          styles.row,
          { opacity: grow.interpolate({ inputRange: [0, 1], outputRange: [1, 0] }) },
        ]}
      >
      {TABS.map((tab) => {
        const selected = tab.key === active;
        return (
          <Pressable
            key={tab.key}
            testID={`tab-${tab.key}`}
            // 늘어나 있는 동안에는 이 다섯이 화면에 없는 것과 같아야 한다.
            //
            // 🔴 세 가지를 다 준다. 앞의 둘은 **폰 전용**이라 웹에서는 아무 일도 안 한다 —
            //    웹에서는 투명도 0으로 안 보이고 손짓도 안 통하지만 **화면 읽기 프로그램에는
            //    그대로 읽혔다**(실측). aria-hidden 이 그 자리를 메운다.
            accessibilityElementsHidden={expanded}
            importantForAccessibility={expanded ? 'no-hide-descendants' : 'auto'}
            aria-hidden={expanded || undefined}
            accessibilityRole="tab"
            accessibilityLabel={tx(tab.labelKo, tab.labelEn)}
            accessibilityState={{ selected, disabled: !tab.route }}
            style={({ pressed }) => [styles.item, pressed && styles.itemPressed]}
            disabled={!tab.route}
            onPress={() => {
              if (tab.route) router.replace(tab.route);
            }}
          >
            {/* 현재 탭 표시는 굵은 글자 + 진한 아이콘뿐이다. 글자 밑의 붉은 점은 글자를 가렸다(2026-09-21 지적, S15P21E201-1390). */}
            <View style={[styles.iconWrap, tab.key === 'schedule' && styles.createIconWrap]}><Image source={tab.icon} resizeMode="contain" style={[styles.icon, tab.key !== 'schedule' && (selected ? styles.iconSelected : styles.iconInactive)]} /></View>
            <Text variant="micro" weight={selected ? 'bold' : 'regular'} color={selected ? color.text.heading : color.text.inactiveTab}>
              {tx(tab.labelKo, tab.labelEn)}
            </Text>
          </Pressable>
        );
      })}
      </Animated.View>
    </Animated.View>
    </Animated.View>
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
    // 🔴 화면 내용 위에 있어야 한다. 마이페이지가 시트를 열 때 어둠막(20)을 깔므로
    //    그보다 높아야 시트가 가려지지 않는다 — 안 주면 나중에 그린 것이 이긴다.
    zIndex: 30,
  },
  bar: {
    alignSelf: 'center',
    width: '100%',
    marginHorizontal: spacing[4],
    overflow: 'hidden',
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    // 🔴 선을 뺐다 (S15P21E201-1343). 새 배색은 어디에도 카드 선을 안 둔다 — 이 막대가
    //    떠 보이는 것은 그림자가 한다. 선과 그림자를 같이 두면 테두리가 두 겹으로 보인다.
    //    🔴 **그림자는 여기만 예외다.** 다른 카드에는 안 둔다.
    shadowColor: color.brand.navy,
    shadowOpacity: 0.10,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: -2 },
    elevation: 8,
  },
  // 탭 다섯 줄. 막대가 자라도 이 줄의 높이는 그대로다 — 자라는 것은 시트 자리다.
  row: { flexDirection: 'row', alignItems: 'center', height: TAB_BAR_HEIGHT, paddingHorizontal: spacing[2] },
  // 시트 내용이 들어갈 자리. 안쪽 여백만 준다 — 무엇을 그릴지는 화면이 정한다.
  sheet: { paddingTop: spacing[3], paddingHorizontal: spacing[3], paddingBottom: spacing[4], gap: spacing[3] },
  item: {
    position: 'relative',
    flex: 1,
    minHeight: 53,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing[1],
  },
  itemPressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  // 🔴 위의 막대에서 **아래의 점**으로 바뀌었다 (S15P21E201-1343). 빨강의 자리를 「채움」이
  //    아니라 「점·선·글자」로 옮기는 규칙을 따른다 — 탭은 글자가 이미 검정으로 굵어지므로
  //    표시는 점 하나면 된다.
  iconWrap: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center' },
  createIconWrap: { width: 32, height: 32, borderRadius: 16, backgroundColor: color.action.primary },
  icon: { width: 20, height: 20 },
  iconSelected: { tintColor: color.text.heading },
  iconInactive: { tintColor: color.text.muted },
});
