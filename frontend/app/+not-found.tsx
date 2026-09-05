import { Platform, StyleSheet, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useRouter } from 'expo-router';

import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';

export default function NotFoundScreen() {
  const router = useRouter();
  const { width } = useLayout();
  const isDesktop = Platform.OS === 'web' && width >= 900;
  const homeHref = Platform.OS === 'web' ? '/' : '/home';

  return (
    <Screen wide style={isDesktop ? styles.desktopScreen : styles.screen}>
      <StatusBar style={isDesktop ? 'dark' : 'light'} />
      <View style={[styles.shell, isDesktop && styles.desktopShell]}>
        <View style={[styles.messagePanel, isDesktop && styles.desktopMessagePanel]}>
          <BrandLogoLink href={homeHref} imageStyle={styles.logo} />
          <View style={styles.message}>
            <Text variant="caption" weight="bold" color={color.brand.orange}>404 · LOST IN BUSAN</Text>
            <Text variant="hero" weight="bold" color={color.text.onAction}>
              길을 잠깐{`\n`}벗어났어요
            </Text>
            <Text variant="body" color={color.text.onDarkMuted}>
              요청한 화면이 없거나 주소가 변경됐어요.{`\n`}안전하게 홈에서 여행을 다시 이어가세요.
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
            <Text variant="caption" weight="bold" color={color.text.eyebrow}>경로 재안내</Text>
            <Text variant="display" weight="bold" color={color.text.heading} style={styles.actionTitle}>
              홈으로 돌아갈까요?
            </Text>
            <Text variant="body" color={color.text.body}>
              입력한 주소는 표시하거나 저장하지 않아요. 이전에 둘러보던 여행 정보도 그대로 유지돼요.
            </Text>
          </View>
          <Button
            label={isDesktop ? '웹 홈으로 돌아가기' : '앱 홈으로 돌아가기'}
            onPress={() => router.replace(homeHref as never)}
            accessibilityHint="GABOLLE 홈 화면으로 이동합니다"
          />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  screen: { paddingHorizontal: 0, paddingTop: 0, paddingBottom: 0 },
  desktopScreen: { justifyContent: 'center', paddingHorizontal: spacing[6], paddingVertical: spacing[8] },
  shell: { flex: 1, backgroundColor: color.brand.navy },
  desktopShell: { flex: 0, minHeight: 560, flexDirection: 'row', borderRadius: radius.lg, overflow: 'hidden' },
  messagePanel: { flex: 1, paddingHorizontal: spacing[6], paddingTop: spacing[4], paddingBottom: spacing[8], justifyContent: 'space-between' },
  desktopMessagePanel: { padding: spacing[8] },
  logo: { width: 94, height: 32, tintColor: color.text.onAction },
  message: { gap: spacing[4] },
  routeLine: { flexDirection: 'row', alignItems: 'center' },
  routeDot: { width: 10, height: 10, borderRadius: radius.full, borderWidth: 2, borderColor: color.text.onAction },
  routeDotActive: { backgroundColor: color.brand.orange, borderColor: color.brand.orange },
  routeDash: { flex: 1, height: 1, marginHorizontal: spacing[2], backgroundColor: color.text.muted },
  actionPanel: { gap: spacing[8], backgroundColor: color.brand.ivory, padding: spacing[6], paddingBottom: spacing[8] },
  desktopActionPanel: { flex: 1, justifyContent: 'center', padding: 48 },
  actionTitle: { marginTop: spacing[2], marginBottom: spacing[3] },
});
