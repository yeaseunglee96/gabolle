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

// 웹의 일반 화면도 휴대폰 폭(480px)으로 고정하면 넓은 모니터에서 앱 미리보기처럼 보인다.
// 폼의 가독성은 유지하면서 카드·목록이 웹답게 숨 쉴 수 있는 폭으로 제한한다.
const MAX_CONTENT_WIDTH = 720;

// 🔴 2단(Split) 화면은 480 으로 묶으면 안 된다. 실제로 한 번 깨졌다 —
//    master 320dp 를 빼고 나면 detail 에 112dp 만 남아 글자가 한 글자씩 줄바꿈됐다
//    ("최 단 경 로"). `tsc` 도 안드로이드 번들도 종료 코드 0 이었다.
//    **폭이 좁은 것은 문법 오류가 아니다.** 브라우저로 실제로 띄워 보고서야 찾았다.
//
// 🔴 2026-09-16 (S15P21E201-1100) — 1180 에서 1440 으로 올렸다. 그리고 이 주석이
//    틀려 있었다: "1024 로 둔다" 고 적혀 있는데 값은 1180 이었다. 언젠가 값만 고치고
//    주석을 안 고친 것이다. 낡은 주석은 없는 주석보다 나쁘다 — 다음 사람이 그것을 믿는다.
//
//    왜 1440 인가. `layout/breakpoints.ts` 의 경계값표가 「1440 ~ = 사이드바 + 본문 +
//    지도 패널」이라고 못박아 두었고, 일정 화면 시안도 데스크톱 1440 기준으로 그려졌다.
//    1180 은 그 표 어디에도 없는 숫자여서, 창이 1646 일 때 좌우에 각 233px 씩 빈 띠가
//    남았다(배포본 실측). 폴드8 펼침(약 717dp)은 어차피 이 값보다 한참 아래라 영향이 없다.
const MAX_SPLIT_WIDTH = 1440;

type ScreenProps = {
  children: ReactNode;
  /** 내용이 화면 높이를 넘는 화면(제약·접근성, 여행 결과)만 켠다. */
  scroll?: boolean;
  /** 2단 레이아웃(Split)을 쓰는 화면만 켠다. 태블릿 최대폭이 480 → 1024 로 넓어진다. */
  wide?: boolean;
  /**
   * 이 화면이 TabBar 를 형제로 그리는가 (S15P21E201-939).
   *
   * TabBar는 Screen 아래의 형제라 하단 안전영역을 이미 차지한다. 이 값을 알려 주면 Screen이
   * 같은 안전영역을 다시 더하지 않아 갤럭시 내비게이션 영역이 두 배로 벌어지지 않는다.
   */
  withTabBar?: boolean;
  style?: StyleProp<ViewStyle>;
};

export function Screen({ children, scroll = false, wide = false, withTabBar = false, style }: ScreenProps) {
  const { kind } = useLayout();
  const insets = useSafeAreaInsets();

  // 하단만 SafeAreaView 에 안 맡기고 내용 여백으로 처리한다 (S15P21E201-939).
  //
  // SafeAreaView 가 아래쪽에 패딩을 넣으면 그 띠는 **스크롤 밖**이라 내용이 거기까지
  // 올라오지 못한다. 화면 아래가 그냥 비는 것으로 끝나면 괜찮은데, 안드로이드는 최근
  // 판부터 화면을 시스템 버튼 아래까지 깔기 때문에(edge-to-edge 가 기본) 그 띠만큼
  // **내용이 잘린 채로 스크롤이 끝난다.** 탭바가 없는 화면은 여백으로 넣어 보호하고,
  // 탭바가 있는 화면은 그 형제 요소가 이미 안전영역을 차지하므로 또 넣지 않는다.
  //
  // style 보다 뒤에 두는 것이 중요하다 — 화면이 style 로 준 paddingBottom 이 이 값을
  // 덮으면 그 화면만 다시 가린다. 아래쪽 여백은 여기가 소유한다.
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
  // 탭바는 absolute가 아니라 Screen 아래의 형제라 자기 높이와 안전영역을 이미 차지한다.
  // 이 화면에서도 다시 더하면 갤럭시의 큰 내비게이션 영역이 두 번 들어간다.
  return spacing[8] + (withTabBar ? 0 : bottomInset);
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
