import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { usePlan } from './PlanProvider';
import { useI18n } from '@/i18n';

const STEPS = [
  { labelKo: '기본', labelEn: 'Basics', path: '/plan/basic' },
  { labelKo: '취향', labelEn: 'Taste', path: '/plan/taste' },
  { labelKo: '제약', labelEn: 'Needs', path: '/plan/conditions' },
  { labelKo: '확인', labelEn: 'Review', path: '/plan/confirm' },
] as const;

export function PlanStepHeader({ current }: { current: number }) {
  const router = useRouter();
  const { draft } = usePlan();
  const { kind } = useLayout();
  const { tx } = useI18n();
  if (kind === 'phone') {
    return <View accessibilityRole="progressbar" accessibilityValue={{ min: 1, max: 4, now: current }} accessibilityLabel={tx(`여행 만들기 ${current}단계`, `Create trip, step ${current} of 4`)} style={styles.progressTrack}>
      <View style={[styles.progressValue, { width: `${current * 25}%` }]} />
    </View>;
  }
  return (
    <View accessibilityRole="progressbar" accessibilityValue={{ min: 1, max: 4, now: current }} style={styles.row}>
      {STEPS.map((step, index) => {
        const number = index + 1;
        const enabled = number <= Math.max(current, draft.maxCompletedStep + 1);
        return (
          <Pressable key={step.path} disabled={!enabled || number === current} onPress={() => router.push(step.path)} style={styles.step}>
            <View style={[styles.circle, number <= current && styles.activeCircle]}><Text variant="caption" weight="bold" color={number <= current ? color.text.onAction : color.text.muted}>{number}</Text></View>
            <Text variant="caption" weight={number === current ? 'bold' : 'regular'} color={number === current ? color.brand.orange : color.text.muted}>{tx(step.labelKo, step.labelEn)}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  progressTrack: { height: 3, marginHorizontal: -spacing[6], marginTop: spacing[2], marginBottom: spacing[6], backgroundColor: color.surface.field, overflow: 'hidden' },
  progressValue: { height: '100%', backgroundColor: color.brand.orange },
  row: { flexDirection: 'row', justifyContent: 'space-between', marginVertical: spacing[4] },
  step: { flex: 1, alignItems: 'center', gap: spacing[1] },
  circle: { width: 28, height: 28, borderRadius: radius.full, backgroundColor: color.surface.field, alignItems: 'center', justifyContent: 'center' },
  activeCircle: { backgroundColor: color.brand.orange },
});
