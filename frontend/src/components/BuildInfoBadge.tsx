import { Platform, StyleSheet, Text, View } from 'react-native';

const buildSha = process.env.EXPO_PUBLIC_BUILD_SHA?.slice(0, 8);
const buildRef = process.env.EXPO_PUBLIC_BUILD_REF;
const alwaysShow = process.env.EXPO_PUBLIC_SHOW_BUILD_INFO === '1';

function shouldDisplay() {
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
