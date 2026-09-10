import { Platform, StyleSheet, Text, View } from 'react-native';

const buildSha = process.env.EXPO_PUBLIC_BUILD_SHA?.slice(0, 8);
const buildRef = process.env.EXPO_PUBLIC_BUILD_REF;
const alwaysShow = process.env.EXPO_PUBLIC_SHOW_BUILD_INFO === '1';

function shouldDisplay() {
  // 🔴 실측(2026-09-10, S15P21E201-177 첫 실기기 빌드) — React Native의 Hermes
  // 런타임은 `window`를 전역 별칭으로 제공해서 네이티브(Android/iOS)에서도
  // `typeof window === 'undefined'`가 false로 나온다. 그런데 `window.location`은
  // 웹 전용이라 네이티브엔 없어서, 바로 아래에서 `window.location.search`를
  // 읽는 순간 TypeError로 죽었다 — 앱이 켜지자마자 모든 네이티브 빌드가
  // AppErrorBoundary로 떨어지는 크래시였다. window 존재 여부가 아니라
  // Platform.OS로 웹인지 직접 판별한다.
  if (Platform.OS !== 'web') return false;
  if (alwaysShow) return true;

  const params = new URLSearchParams(window.location.search);
  return params.get('build-debug') === '1' || params.get('debug') === '1';
}

export function BuildInfoBadge() {
  if (!shouldDisplay()) return null;

  const identity = [
    buildRef ? `ref=${buildRef}` : null,
    buildSha ? `sha=${buildSha}` : null,
  ].filter(Boolean);

  return (
    <View style={styles.overlay} pointerEvents="none" accessibilityLiveRegion="polite">
      <View style={styles.badge}>
        <Text style={styles.text}>
          {identity.length > 0 ? `build: ${identity.join(' · ')}` : 'build: unknown'}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    top: 8,
    right: 8,
    zIndex: 9999,
    alignItems: 'flex-end',
  },
  badge: {
    maxWidth: 240,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 10,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
  },
  text: {
    color: '#fff',
    fontSize: 11,
    fontWeight: '700',
  },
});
