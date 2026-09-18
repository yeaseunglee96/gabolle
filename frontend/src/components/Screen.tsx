// 모든 화면이 거치는 뼈대: 세이프에어리어 + 배경 + 좌우 여백.
// 폴드8 을 펼쳐 태블릿 폭이 되면 글자가 화면 끝까지 늘어나 못 읽으므로 최대 폭을 제한하고
// 가운데 정렬한다. 2단 레이아웃은 아직 정해지지 않았다(Split.tsx 참고) — 그건 여기 몫이 아니다.
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  View,
  type StyleProp,
  type ViewStyle,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import type { ReactNode } from 'react';

import { color, gutter, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { TAB_BAR_HEIGHT, tabBarBottomMargin } from './TabBar';

// 웹의 일반 화면도 휴대폰 폭(480px)으로 고정하면 넓은 모니터에서 앱 미리보기처럼 보인다.
// 폼의 가독성은 유지하면서 카드·목록이 웹답게 숨 쉴 수 있는 폭으로 제한한다.
const MAX_CONTENT_WIDTH = 720;

// 2단(Split) 화면은 480 으로 묶으면 안 된다. 실제로 한 번 깨졌다
// master 320dp 를 빼고 나면 detail 에 112dp 만 남아 글자가 한 글자씩 줄바꿈됐다
// ("최 단 경 로"). `tsc` 도 안드로이드 번들도 종료 코드 0 이었다.
// 폭이 좁은 것은 문법 오류가 아니다. 브라우저로 실제로 띄워 보고서야 찾았다.
const MAX_SPLIT_WIDTH = 1440;

type ScreenProps = {
  children: ReactNode;
  /** 내용이 화면 높이를 넘는 화면(제약·접근성, 여행 결과)만 켠다. */
  scroll?: boolean;
  /** 2단 레이아웃(Split)을 쓰는 화면만 켠다. 태블릿 최대폭이 480 → 1024 로 넓어진다. */
  wide?: boolean;
  /** 이 화면이 TabBar 를 형제로 그리는가 */
  withTabBar?: boolean;
  style?: StyleProp<ViewStyle>;
};

export function Screen({ children, scroll = false, wide = false, withTabBar = false, style }: ScreenProps) {
  const { kind } = useLayout();
  const insets = useSafeAreaInsets();

  // 하단만 SafeAreaView 에 안 맡기고 내용 여백으로 처리한다
  const contentStyle = [
    styles.content,
    kind === 'tablet' && (wide ? styles.tabletWide : styles.tablet),
    style,
    { paddingBottom: screenBottomPadding(insets.bottom, withTabBar) },
  ];

  // 안드로이드에는 behavior 를 주지 않는다. 키보드가 올라올 때 화면을 밀어 올리는 일은
  // app.json 의 softwareKeyboardLayoutMode="pan" 이 맡는다 — edge-to-edge 에서는 RN 의
  // 회피 계산과 시스템의 창 크기 조정이 겹쳐 두 번 밀리는 일이 알려져 있어서, 한쪽에만
  // 맡긴다. iOS 는 그 설정이 없으므로 여기서 padding 으로 민다.
  const keyboardBehavior = Platform.OS === 'ios' ? 'padding' : undefined;

  const body = scroll ? (
    <ScrollView contentContainerStyle={contentStyle} keyboardShouldPersistTaps="handled">
      {children}
    </ScrollView>
  ) : (
    <View style={[styles.flex, ...contentStyle]}>{children}</View>
  );

  return (
    <SafeAreaView style={styles.screen} edges={['top', 'left', 'right']}>
      <KeyboardAvoidingView style={styles.flex} behavior={keyboardBehavior}>
        {body}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

export function screenBottomPadding(bottomInset: number, withTabBar: boolean) {
  // 지금은 탭바가 떠 있어서 레이아웃 자리를 안 먹는다. 그래서 이 화면이 그만큼을
  // 대신 비워 주지 않으면 스크롤 맨 끝 내용이 알약 밑에 영원히 깔린다 — 더 스크롤할
  // 것이 없으니 드러낼 방법도 없다.
  if (withTabBar) return spacing[8] + TAB_BAR_HEIGHT + tabBarBottomMargin(bottomInset);
  return spacing[8] + bottomInset;
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: color.canvas,
  },
  flex: {
    flex: 1,
  },
  content: {
    width: '100%',
    paddingHorizontal: gutter,
    paddingTop: spacing[6],
    paddingBottom: spacing[8],
  },
  tablet: {
    alignSelf: 'center',
    maxWidth: MAX_CONTENT_WIDTH,
  },
  tabletWide: {
    alignSelf: 'center',
    maxWidth: MAX_SPLIT_WIDTH,
  },
});
