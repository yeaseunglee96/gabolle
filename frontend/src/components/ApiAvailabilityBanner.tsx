import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { subscribeApiAvailability } from '@/api/client';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { Text } from '@/components/Text';

export function ApiAvailabilityBanner() {
  const [unavailable, setUnavailable] = useState(false);
  const insets = useSafeAreaInsets();
  const { tx } = useI18n();

  useEffect(() => subscribeApiAvailability(setUnavailable), []);

  if (!unavailable) return null;

  return (
    <View
      accessibilityRole="alert"
      accessibilityLiveRegion="assertive"
      style={[styles.position, { top: insets.top + spacing[2] }]}
      pointerEvents="none"
    >
      <View style={styles.banner}>
        <View style={styles.dot} />
        <View style={styles.copy}>
          <Text weight="bold" color={color.text.heading}>
            {tx('서버 연결을 확인하고 있어요', 'Checking the server connection')}
          </Text>
          <Text variant="caption" color={color.text.body}>
            {tx(
              '보고 있던 화면은 그대로 유지돼요. 연결되면 자동으로 사라집니다.',
              'This screen will stay available and the notice will disappear automatically.',
            )}
          </Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  position: {
    position: 'absolute',
    left: spacing[4],
    right: spacing[4],
    zIndex: 1000,
    alignItems: 'center',
  },
  banner: {
    width: '100%',
    maxWidth: 520,
    minHeight: 64,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: color.brand.orange,
    backgroundColor: color.brand.ivory,
    shadowColor: color.brand.navy,
    shadowOpacity: 0.12,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 4,
  },
  dot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: color.brand.orange,
  },
  copy: { flex: 1, gap: spacing[1] },
});
