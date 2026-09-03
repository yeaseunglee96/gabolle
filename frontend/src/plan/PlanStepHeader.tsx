import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { usePlan } from './PlanProvider';

const STEPS = [
  { label: '기본', path: '/plan/basic' },
  { label: '취향', path: '/plan/taste' },
  { label: '제약', path: '/plan/conditions' },
  { label: '확인', path: '/plan/confirm' },
] as const;

export function PlanStepHeader({ current }: { current: number }) {
  const router = useRouter();
  const { draft } = usePlan();
  return (
    <View accessibilityRole="progressbar" accessibilityValue={{ min: 1, max: 4, now: current }} style={styles.row}>
      {STEPS.map((step, index) => {
        const number = index + 1;
        const enabled = number <= Math.max(current, draft.maxCompletedStep + 1);
        return (
          <Pressable key={step.path} disabled={!enabled || number === current} onPress={() => router.push(step.path)} style={styles.step}>
            <View style={[styles.circle, number <= current && styles.activeCircle]}><Text variant="caption" weight="bold" color={number <= current ? color.text.onAction : color.text.muted}>{number}</Text></View>
            <Text variant="caption" weight={number === current ? 'bold' : 'regular'} color={number === current ? color.text.heading : color.text.muted}>{step.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', justifyContent: 'space-between', marginVertical: spacing[4] },
  step: { flex: 1, alignItems: 'center', gap: spacing[1] },
  circle: { width: 28, height: 28, borderRadius: radius.full, backgroundColor: color.surface.field, alignItems: 'center', justifyContent: 'center' },
  activeCircle: { backgroundColor: color.action.primary },
});
