import type { ReactNode } from 'react';
import { ImageBackground, Platform, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';

export function PlanDesktopShell({ children }: { children: ReactNode }) {
  const { kind, width } = useLayout();
  const { tx } = useI18n();
  const desktop = kind === 'tablet' && isAtLeast(width, 'lg');
  if (!desktop) return <>{children}</>;
  return <View style={styles.page}>
    <ImageBackground source={require('../../assets/images/welcome-busan.png')} resizeMode="cover" style={styles.hero}>
      <View style={styles.shade} />
      <View style={styles.copy}>
        <View style={styles.tag}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('✈ AI 여행 플래너', '✈ AI Trip Planner')}</Text></View>
        <Text variant="display" weight="bold" color={color.text.onAction}>{tx('부산의 모든 여행,\n가볼래?', 'Every Busan trip,\nshall we go?')}</Text>
        <Text variant="caption" color={color.text.onAction} style={styles.description}>{tx('AI가 취향과 이동 조건에 맞춘\n부산 여행 일정을 만들어드려요.', 'AI builds a Busan itinerary matched to your taste and mobility needs.')}</Text>
      </View>
    </ImageBackground>
    <View style={styles.content}>{children}</View>
  </View>;
}

const styles = StyleSheet.create({
  page: { flex: 1, flexDirection: 'row', backgroundColor: color.brand.ivory },
  // 🔴 `minHeight: '100%'`는 조상 체인에 실제 픽셀 높이를 가진 것이 하나도 없으면(이 화면이
  // 그렇다 — Screen이 스크롤 컨테이너라 높이를 위로 못 물려준다) 퍼센트가 못 풀려서, 웹에서
  // 배경 이미지가 자기 원본 픽셀 높이(1844px)를 그대로 떠안는 사고가 났다. 그러면 cover 크롭이
  // 하늘만 남기고 다리·바다를 전부 잘라낸다. `100vh`는 조상 체인과 무관하게 뷰포트 기준으로
  // 바로 풀려서 이 문제 자체가 안 생긴다. 네이티브에는 vh 단위가 없으므로 웹에서만 쓴다.
  hero: { width: 420, justifyContent: 'flex-end', ...Platform.select({ web: { height: '100vh' as unknown as number }, default: { flex: 1 } }) },
  shade: { position: 'absolute', inset: 0, backgroundColor: 'rgba(11,29,58,0.35)' },
  copy: { gap: spacing[3], paddingHorizontal: spacing[8], paddingBottom: 48 },
  tag: { alignSelf: 'flex-start', paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: 'rgba(242,101,50,0.72)' },
  description: { opacity: 0.86 },
  content: { flex: 1 },
});
