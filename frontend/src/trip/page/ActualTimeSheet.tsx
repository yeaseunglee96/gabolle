// 적은 도착·출발 시각 고치기 — 지금 · 5분 전 · 15분 전 · 직접(−/+5분) (S15P21E201-1690, 조율 세션 결정).
//
// 🔴 폰과 웹이 같은 모양이다. 기기의 시각 고르개(@react-native-community/datetimepicker)는 웹에서 돌지 않는다.
// 🔴 고를 수 있는 범위를 창이 지킨다 — 미래는 안 되고, 출발은 도착보다 이를 수 없다(서버가 400 을 준다).
import { useEffect, useState } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { txf } from '@/i18n/format';

import { QUICK_OFFSETS_MIN, STEP_MIN, clampActualTime, clockOf, minutesBefore, stepTime } from './actualTime';

type Tx = (ko: string, en: string) => string;

export function ActualTimeSheet({ visible, kind, placeTitle, currentMs, minMs, busy = false, onCancel, onSave, tx }: {
  visible: boolean;
  kind: 'arrival' | 'departure';
  placeTitle: string;
  /** 지금 적혀 있는 시각. */
  currentMs: number;
  /** 이보다 이르면 안 된다 — 출발이면 도착 시각, 도착이면 그날 0시. */
  minMs: number;
  busy?: boolean;
  onCancel: () => void;
  onSave: (ms: number) => void;
  tx: Tx;
}) {
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [picked, setPicked] = useState(currentMs);
  // 열 때마다 「지금」을 다시 잰다 — 창을 열어 둔 채 몇 분이 지나도 미래를 고를 수 없게.
  useEffect(() => {
    if (!visible) return;
    const now = Date.now();
    setNowMs(now);
    setPicked(clampActualTime(currentMs, { nowMs: now, minMs }));
  }, [visible, currentMs, minMs]);

  const bounds = { nowMs, minMs };
  const quickLabel = (minutes: number) => (minutes === 0 ? tx('지금', 'Now') : txf(tx, '%s분 전', '%s min ago', minutes));

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <Text variant="title" weight="bold">
            {kind === 'arrival' ? txf(tx, '%s 도착 시각', 'Arrival at %s', placeTitle) : txf(tx, '%s 출발 시각', 'Departure from %s', placeTitle)}
          </Text>
          <View style={styles.quickRow}>
            {QUICK_OFFSETS_MIN.map((minutes) => {
              const value = minutesBefore(nowMs, minutes, minMs);
              const selected = Math.abs(value - picked) < 30_000;
              return (
                <Pressable
                  key={minutes}
                  accessibilityRole="button"
                  accessibilityState={{ selected }}
                  onPress={() => setPicked(value)}
                  style={({ pressed }) => [styles.chip, selected && styles.chipSelected, pressed && styles.pressed]}
                >
                  <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.body}>{quickLabel(minutes)}</Text>
                </Pressable>
              );
            })}
          </View>
          <View style={styles.stepRow}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={txf(tx, '%s분 앞으로', '%s min earlier', STEP_MIN)}
              onPress={() => setPicked((value) => stepTime(value, -STEP_MIN, bounds))}
              style={({ pressed }) => [styles.step, pressed && styles.pressed]}
            >
              <Text weight="bold">{`−${STEP_MIN}`}</Text>
            </Pressable>
            <Text variant="display" weight="bold" accessibilityLiveRegion="polite">{clockOf(picked)}</Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={txf(tx, '%s분 뒤로', '%s min later', STEP_MIN)}
              onPress={() => setPicked((value) => stepTime(value, STEP_MIN, bounds))}
              style={({ pressed }) => [styles.step, pressed && styles.pressed]}
            >
              <Text weight="bold">{`+${STEP_MIN}`}</Text>
            </Pressable>
          </View>
          <View style={styles.actions}>
            <Button label={tx('취소', 'Cancel')} variant="tertiary" onPress={onCancel} containerStyle={styles.action} />
            <Button label={tx('저장', 'Save')} disabled={busy} onPress={() => onSave(clampActualTime(picked, bounds))} containerStyle={styles.action} />
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  card: { width: '100%', maxWidth: 420, gap: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  quickRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 40, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.soft },
  chipSelected: { backgroundColor: color.brand.navy },
  stepRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3] },
  step: { minWidth: 56, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, backgroundColor: color.surface.soft },
  actions: { flexDirection: 'row', gap: spacing[2] },
  action: { flex: 1 },
  pressed: { opacity: 0.82 },
});
