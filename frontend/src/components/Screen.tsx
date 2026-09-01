// 모든 화면이 거치는 뼈대: 세이프에어리어 + 배경 + 좌우 여백.
// 폴드8 을 펼쳐 태블릿 폭이 되면 글자가 화면 끝까지 늘어나 못 읽으므로 최대 폭을 제한하고
// 가운데 정렬한다. 2단 레이아웃은 아직 정해지지 않았다(Split.tsx 참고) — 그건 여기 몫이 아니다.
import { ScrollView, StyleSheet, View, type ViewStyle } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { ReactNode } from 'react';

import { color, gutter, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';

const MAX_CONTENT_WIDTH = 480;

// 🔴 2단(Split) 화면은 480 으로 묶으면 안 된다. 실제로 한 번 깨졌다 —
//    master 320dp 를 빼고 나면 detail 에 112dp 만 남아 글자가 한 글자씩 줄바꿈됐다
//    ("최 단 경 로"). `tsc` 도 안드로이드 번들도 종료 코드 0 이었다.
//    **폭이 좁은 것은 문법 오류가 아니다.** 브라우저로 실제로 띄워 보고서야 찾았다.
//    폴드8 펼침이 약 717dp 이니 그보다 넉넉하되, 데스크톱 폭에서 두 칸이 한없이
//    늘어나지는 않게 1024 로 둔다.
const MAX_SPLIT_WIDTH = 1024;

type ScreenProps = {
  children: ReactNode;
  /** 내용이 화면 높이를 넘는 화면(제약·접근성, 여행 결과)만 켠다. */
  scroll?: boolean;
  /** 2단 레이아웃(Split)을 쓰는 화면만 켠다. 태블릿 최대폭이 480 → 1024 로 넓어진다. */
  wide?: boolean;
  style?: ViewStyle;
};

export function Screen({ children, scroll = false, wide = false, style }: ScreenProps) {
  const { kind } = useLayout();
  const contentStyle = [
    styles.content,
    kind === 'tablet' && (wide ? styles.tabletWide : styles.tablet),
    style,
  ];

  if (scroll) {
    return (
      <SafeAreaView style={styles.screen}>
        <ScrollView contentContainerStyle={contentStyle}>{children}</ScrollView>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.screen}>
      <View style={[styles.flex, ...contentStyle]}>{children}</View>
    </SafeAreaView>
  );
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
