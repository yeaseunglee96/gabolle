// 모든 화면이 거치는 뼈대: 세이프에어리어 + 배경 + 좌우 여백.
// 폴드8 을 펼쳐 태블릿 폭이 되면 글자가 화면 끝까지 늘어나 못 읽으므로 최대 폭을 제한하고
// 가운데 정렬한다. 2단 레이아웃은 아직 정해지지 않았다(Split.tsx 참고) — 그건 여기 몫이 아니다.
import { ScrollView, StyleSheet, View, type ViewStyle } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { ReactNode } from 'react';

import { color, gutter, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';

const MAX_CONTENT_WIDTH = 480;

type ScreenProps = {
  children: ReactNode;
  /** 내용이 화면 높이를 넘는 화면(제약·접근성, 여행 결과)만 켠다. */
  scroll?: boolean;
  style?: ViewStyle;
};

export function Screen({ children, scroll = false, style }: ScreenProps) {
  const { kind } = useLayout();
  const contentStyle = [styles.content, kind === 'tablet' && styles.tablet, style];

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
});
