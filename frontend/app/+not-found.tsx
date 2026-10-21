import { Platform, StyleSheet, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useRouter } from 'expo-router';

import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';

export default function NotFoundScreen() {
  const router = useRouter();
  const { tx } = useI18n();
  const { width } = useLayout();
  const isDesktop = Platform.OS === 'web' && isAtLeast(width, 'md');
  const homeHref = Platform.OS === 'web' ? '/' : '/home';

  return (
    <Screen wide style={isDesktop ? styles.desktopScreen : styles.screen}>
      {/* 🔴 위쪽 안전 영역은 Screen 의 SafeAreaView 가 밝은 바탕(canvas)으로 그린다 — 남색은 그 아래부터라 밝은 글자가 묻혔다(S15P21E201-1988). 어두운 글자로. */}
      <StatusBar style="dark" />
      <View style={[styles.shell, isDesktop && styles.desktopShell]}>
        <View style={[styles.messagePanel, isDesktop && styles.desktopMessagePanel]}>
          <BrandLogoLink href={homeHref} imageStyle={styles.logo} />
          <View style={styles.message}>
            <Eyebrow>{tx('404 · 길을 잃었어요', '404 · Lost in Busan')}</Eyebrow>
            <Text variant="hero" weight="bold" color={color.text.onAction}>
              {tx('길을 잠깐\n벗어났어요', "You've strayed off\nthe path")}
            </Text>
            <Text variant="body" color={color.text.onDarkMuted}>
              {tx('요청한 화면이 없거나 주소가 변경됐어요.\n안전하게 홈에서 여행을 다시 이어가세요.', "The page you're looking for doesn't exist or moved.\nHead back home to continue your trip safely.")}
            </Text>
          </View>
          <View accessibilityElementsHidden style={styles.routeLine}>
            <View style={styles.routeDot} />
            <View style={styles.routeDash} />
            <View style={styles.routeDot} />
            <View style={styles.routeDash} />
            <View style={[styles.routeDot, styles.routeDotActive]} />
          </View>
        </View>

        <View style={[styles.actionPanel, isDesktop && styles.desktopActionPanel]}>
          <View>
            <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('경로 재안내', 'Rerouting')}</Text>
            <Text variant="display" weight="bold" color={color.text.heading} style={styles.actionTitle}>
              {tx('홈으로 돌아갈까요?', 'Head back home?')}
            </Text>
            <Text variant="body" color={color.text.body}>
              {tx('입력한 주소는 표시하거나 저장하지 않아요. 이전에 둘러보던 여행 정보도 그대로 유지돼요.', "We don't display or store the address you entered. Any trip you were browsing is still right where you left it.")}
            </Text>
          </View>
          <Button
            label={isDesktop ? tx('웹 홈으로 돌아가기', 'Back to the web home') : tx('앱 홈으로 돌아가기', 'Back to the app home')}
            onPress={() => router.replace(homeHref as never)}
            accessibilityHint={tx('GABOLLE 홈 화면으로 이동합니다', 'Goes to the GABOLLE home screen')}
          />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  // 위쪽 안전 영역도 남색 — 밝은 바탕이면 밝은(light) 상태표시줄 글자가 묻힌다(S15P21E201-1985).
  screen: { paddingHorizontal: 0, paddingTop: 0, paddingBottom: 0, backgroundColor: color.brand.navy },
  desktopScreen: { justifyContent: 'center', paddingHorizontal: spacing[6], paddingVertical: spacing[8] },
  shell: { flex: 1, backgroundColor: color.brand.navy },
  desktopShell: { flex: 0, minHeight: 560, flexDirection: 'row', borderRadius: radius.lg, overflow: 'hidden' },
  messagePanel: { flex: 1, paddingHorizontal: spacing[6], paddingTop: spacing[4], paddingBottom: spacing[8], justifyContent: 'space-between' },
  desktopMessagePanel: { padding: spacing[8] },
  logo: { width: 176, height: 32, tintColor: color.text.onAction },
  message: { gap: spacing[4] },
  routeLine: { flexDirection: 'row', alignItems: 'center' },
  routeDot: { width: 10, height: 10, borderRadius: radius.full, borderWidth: 2, borderColor: color.text.onAction },
  routeDotActive: { backgroundColor: color.state.dot, borderColor: color.state.dot },
  routeDash: { flex: 1, height: 1, marginHorizontal: spacing[2], backgroundColor: color.text.muted },
  actionPanel: { gap: spacing[8], backgroundColor: color.brand.ivory, padding: spacing[6], paddingBottom: spacing[8] },
  desktopActionPanel: { flex: 1, justifyContent: 'center', padding: 48 },
  actionTitle: { marginTop: spacing[2], marginBottom: spacing[3] },
});
