// 06 기본 조건 설정 — Figma 06_기본 조건 설정 실측 그대로.
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { ProgressBar } from '@/components/ProgressBar';
import { Button } from '@/components/Button';
import { LanguageBadge } from '@/components/LanguageBadge';

type Transport = '대중교통' | '도보 위주' | '렌터카';
const TRANSPORT_OPTIONS: Transport[] = ['대중교통', '도보 위주', '렌터카'];

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
  const [transport, setTransport] = useState<Transport>('대중교통');

  return (
    <Screen scroll>
      <LanguageBadge />
      <View style={styles.headerRow}>
        <Eyebrow>06 · 여행 만들기</Eyebrow>
        <Text variant="eyebrow" weight="bold">
          1/3
        </Text>
      </View>
      <Text variant="display" weight="bold" style={styles.title}>
        기본 조건을 설정해 주세요
      </Text>

      <ProgressBar progress={1 / 3} />

      <View style={styles.fields}>
        <FieldRow label="여행 날짜" value="8월 24일(월)  →  8월 25일(화)" />
        <FieldRow label="여행 인원" value="2명" />
        <FieldRow label="출발지" value="부산역" />

        <View style={styles.field}>
          <Text variant="caption">이동 수단</Text>
          <View style={styles.transportRow}>
            {TRANSPORT_OPTIONS.map((option) => {
              const selected = option === transport;
              return (
                <Pressable
                  key={option}
                  onPress={() => setTransport(option)}
                  style={[styles.transportChip, selected && styles.transportChipSelected]}
                >
                  <Text
                    variant="caption"
                    weight="bold"
                    color={selected ? color.text.eyebrow : color.text.body}
                  >
                    {option}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>
      </View>

      <Button label="다음" containerStyle={styles.cta} onPress={() => router.push('/constraints')} />
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
