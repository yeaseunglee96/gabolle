// 지도 위 「경사」·「그늘」 켜고 끄기 + 켠 것의 한 줄 풀이 (S15P21E201-1569).
// 하나만 켠다 — 빨강·파랑 선이 겹치면 어느 것이 무엇인지 못 읽는다.
import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import type { MobilityLayerKind } from '@/map/mobilityLayers';

type Tx = (ko: string, en: string) => string;

export function MobilityLayerToggle({ value, onChange, basis, tx, style }: {
  value: MobilityLayerKind | null;
  onChange: (next: MobilityLayerKind | null) => void;
  /** 그늘을 무엇으로 쟀나(파일이 준다). 받기 전이면 null. */
  basis: string | null;
  tx: Tx;
  style?: StyleProp<ViewStyle>;
}) {
  const chip = (kind: MobilityLayerKind, label: string) => {
    const on = value === kind;
    return (
      <Pressable
        accessibilityRole="switch"
        accessibilityState={{ checked: on }}
        onPress={() => onChange(on ? null : kind)}
        style={({ pressed }) => [styles.chip, on && styles.chipOn, pressed && styles.pressed]}
      >
        <View style={[styles.swatch, { backgroundColor: kind === 'slope' ? color.state.danger : color.state.info }]} />
        <Text variant="caption" weight="bold" color={on ? color.text.onAction : color.text.heading}>{label}</Text>
      </Pressable>
    );
  };
  return (
    <View style={[styles.wrap, style]}>
      <View style={styles.row}>
        {chip('slope', tx('경사', 'Slope'))}
        {chip('shade', tx('그늘', 'Shade'))}
      </View>
      {value === 'slope' ? (
        <Text variant="micro" color={color.text.body} style={styles.legend}>{tx('빨간 길: 경사 8.33% 이상 — 휠체어 경사로 기준(1:12)을 넘어요', 'Red: slope 8.33%+ — steeper than the 1:12 wheelchair ramp standard')}{basis ? ` · ${basis}` : ''}</Text>
      ) : value === 'shade' ? (
        <Text variant="micro" color={color.text.body} style={styles.legend}>{basis ? `${tx('파란 길이 짙을수록 그늘이 많아요', 'Darker blue = more shade')} · ${basis}` : tx('그늘 자료를 불러오는 중…', 'Loading shade data…')}</Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing[1], alignItems: 'flex-start' },
  row: { flexDirection: 'row', gap: spacing[2] },
  chip: {
    minHeight: 32, flexDirection: 'row', alignItems: 'center', gap: spacing[1], paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.card,
    shadowColor: color.brand.navy, shadowOpacity: 0.08, shadowRadius: 8, shadowOffset: { width: 0, height: 2 }, elevation: 2,
  },
  chipOn: { backgroundColor: color.action.secondary },
  pressed: { opacity: 0.8 },
  swatch: { width: 10, height: 3, borderRadius: 2 },
  legend: { maxWidth: 280, paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.md, backgroundColor: color.surface.card },
});
