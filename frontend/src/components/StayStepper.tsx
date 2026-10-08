// 한 곳에 머무는 시간을 15분씩 줄이고 늘리는 손잡이 — S15P21E201-2017 「시간 바꾸기」.
//
// 숫자를 직접 적게 하지 않는다 — 15분 눈금 밖의 값(47분)은 서버가 거부하고, 폰에서 시각 입력은 손이 많이 간다.
// −/+ 를 누를 때마다 서버에 보낸다(뒤 곳의 시각이 바로 다시 매겨져 보여야 「무엇이 바뀌었나」가 읽힌다).
import { Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { txf } from '@/i18n/format';
import { formatStay, MAX_STAY_MINUTES, MIN_STAY_MINUTES } from '@/plan/itineraryAdjust';

type Tx = (ko: string, en: string) => string;

export function StayStepper({ minutes, name, busy = false, disabled = false, onStep, tx }: {
  /** 지금 머무는 분. 모르면 null — 그때는 손잡이를 안 그린다(시각이 없는 날은 시간을 바꿀 기준이 없다). */
  minutes: number | null;
  name: string;
  busy?: boolean;
  disabled?: boolean;
  onStep: (direction: 1 | -1) => void;
  tx: Tx;
}) {
  if (minutes == null) return null;
  const off = busy || disabled;
  const canShorten = !off && minutes > MIN_STAY_MINUTES;
  const canLengthen = !off && minutes < MAX_STAY_MINUTES;
  return (
    <View style={styles.row} testID="stay-stepper">
      <Text variant="caption" color={color.text.muted}>{tx('머무는 시간', 'Time here')}</Text>
      <View style={styles.controls}>
        <Pressable
          testID="stay-shorten"
          accessibilityRole="button"
          accessibilityLabel={txf(tx, '%s 머무는 시간 15분 줄이기', 'Shorten time at %s by 15 min', name)}
          accessibilityState={{ disabled: !canShorten, busy }}
          disabled={!canShorten}
          hitSlop={6}
          onPress={() => onStep(-1)}
          style={({ pressed }) => [styles.button, (!canShorten || pressed) && styles.dim]}
        >
          <Text weight="bold" color={color.brand.navy}>−</Text>
        </Pressable>
        <Text weight="bold" style={styles.value} accessibilityLiveRegion="polite">{busy ? '…' : formatStay(minutes, tx)}</Text>
        <Pressable
          testID="stay-lengthen"
          accessibilityRole="button"
          accessibilityLabel={txf(tx, '%s 머무는 시간 15분 늘리기', 'Lengthen time at %s by 15 min', name)}
          accessibilityState={{ disabled: !canLengthen, busy }}
          disabled={!canLengthen}
          hitSlop={6}
          onPress={() => onStep(1)}
          style={({ pressed }) => [styles.button, (!canLengthen || pressed) && styles.dim]}
        >
          <Text weight="bold" color={color.brand.navy}>+</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], marginTop: spacing[2] },
  controls: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  button: { width: 36, height: 36, borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  value: { minWidth: 76, textAlign: 'center' },
  dim: { opacity: 0.45 },
});
