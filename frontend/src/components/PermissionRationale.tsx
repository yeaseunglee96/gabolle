import { Image, Linking, Pressable, StyleSheet, View, type ImageSourcePropType } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { Text } from './Text';

type Props = {
  icon: ImageSourcePropType;
  title: string;
  description: string;
  denied?: boolean;
  busy?: boolean;
  actionLabel: string;
  onRequest: () => void;
};

export function PermissionRationale({ icon, title, description, denied = false, busy = false, actionLabel, onRequest }: Props) {
  const { tx } = useI18n();
  return (
    <View accessibilityLiveRegion="polite" style={[styles.card, denied && styles.deniedCard]}>
      <View style={styles.icon}><Image source={icon} resizeMode="contain" style={styles.iconImage} /></View>
      <View style={styles.copy}>
        <Text variant="body" weight="bold">{denied ? tx('권한이 필요합니다', 'Permission needed') : title}</Text>
        <Text variant="caption" color={color.text.body}>{description}</Text>
        <Pressable
          accessibilityRole="button"
          disabled={busy}
          onPress={denied ? () => void Linking.openSettings() : onRequest}
          style={({ pressed }) => [styles.action, pressed && styles.pressed, busy && styles.busy]}
        >
          <Text variant="caption" weight="bold" color={color.text.accent}>
            {busy ? tx('확인 중…', 'Checking…') : denied ? tx('설정 열기 ›', 'Open settings ›') : `${actionLabel} ›`}
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { flexDirection: 'row', gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.soft },
  deniedCard: { borderColor: color.state.danger, backgroundColor: color.state.dangerBg },
  icon: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, backgroundColor: color.surface.card },
  iconImage: { width: 24, height: 24 },
  copy: { flex: 1, gap: spacing[1] },
  action: { minHeight: 36, alignSelf: 'flex-start', justifyContent: 'center', marginTop: spacing[1], paddingHorizontal: spacing[2], borderRadius: radius.sm, backgroundColor: color.surface.card },
  pressed: { opacity: 0.7 },
  busy: { opacity: 0.5 },
});
