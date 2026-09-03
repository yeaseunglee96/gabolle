// 06 기본 조건 설정 — Figma 06_기본 조건 설정 실측 그대로.
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { Button } from '@/components/Button';
import { LanguageBadge } from '@/components/LanguageBadge';
import { PlanStepHeader } from '@/plan/PlanStepHeader';
import { type Transport, usePlan } from '@/plan/PlanProvider';

const TRANSPORT_OPTIONS: { key: Transport; label: string }[] = [{ key: 'TRANSIT', label: '대중교통' }, { key: 'WALK', label: '도보 위주' }, { key: 'CAR', label: '렌터카' }];

function FieldRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.field}>
      <Text variant="caption">{label}</Text>
      <View style={styles.fieldBox}>
        <Text variant="body" weight="medium" color={color.text.heading}>
          {value}
        </Text>
      </View>
    </View>
  );
}

export default function Basics() {
  const router = useRouter();
  const { draft, update, completeStep, basicComplete } = usePlan();

  return (
    <Screen scroll>
      <LanguageBadge />
      <View style={styles.headerRow}>
        <Eyebrow>06 · 여행 만들기</Eyebrow>
        <Text variant="eyebrow" weight="bold">
          1/4
        </Text>
      </View>
      <Text variant="display" weight="bold" style={styles.title}>
        기본 조건을 설정해 주세요
      </Text>

      <PlanStepHeader current={1} />

      <View style={styles.fields}>
        <View style={styles.field}><Text variant="caption">여행 시작일 (YYYY-MM-DD)</Text><TextInput accessibilityLabel="여행 시작일" value={draft.startDate} onChangeText={(startDate) => update({ startDate })} placeholder="2026-09-10" style={styles.input} /></View>
        <View style={styles.field}><Text variant="caption">여행 종료일 (YYYY-MM-DD)</Text><TextInput accessibilityLabel="여행 종료일" value={draft.endDate} onChangeText={(endDate) => update({ endDate })} placeholder="2026-09-12" style={styles.input} /></View>
        <View style={styles.field}><Text variant="caption">여행 인원</Text><TextInput accessibilityLabel="여행 인원" value={String(draft.travelers)} keyboardType="number-pad" onChangeText={(value) => update({ travelers: Math.max(1, Number(value) || 1) })} style={styles.input} /></View>
        <View style={styles.field}><Text variant="caption">출발지</Text><TextInput accessibilityLabel="출발지" value={draft.origin} onChangeText={(origin) => update({ origin })} placeholder="예: 부산역" style={styles.input} /></View>

        <View style={styles.field}>
          <Text variant="caption">이동 수단</Text>
          <View style={styles.transportRow}>
            {TRANSPORT_OPTIONS.map((option) => {
              const selected = option.key === draft.transport;
              return (
                <Pressable
                  key={option.key}
                  onPress={() => update({ transport: option.key })}
                  style={[styles.transportChip, selected && styles.transportChipSelected]}
                >
                  <Text
                    variant="caption"
                    weight="bold"
                    color={selected ? color.text.eyebrow : color.text.body}
                  >
                    {option.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>
      </View>

      <Button label="다음" disabled={!basicComplete} containerStyle={styles.cta} onPress={() => { completeStep(1); router.push('/plan/taste'); }} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: spacing[2],
  },
  title: {
    marginTop: spacing[1],
    marginBottom: spacing[4],
  },
  fields: {
    marginTop: spacing[6],
    gap: spacing[4],
  },
  field: {
    gap: spacing[1],
  },
  fieldBox: {
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: color.surface.field,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  input: { minHeight: 50, backgroundColor: color.surface.card, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, paddingHorizontal: spacing[4], color: color.text.heading },
  transportRow: {
    flexDirection: 'row',
    gap: spacing[2],
  },
  transportChip: {
    flex: 1,
    alignItems: 'center',
    borderRadius: radius.full,
    paddingVertical: spacing[2],
    backgroundColor: color.surface.card,
    borderWidth: 1,
    borderColor: color.surface.field,
  },
  transportChipSelected: {
    backgroundColor: color.surface.tint,
    borderColor: color.surface.tint,
  },
  cta: {
    marginTop: spacing[8],
  },
});
