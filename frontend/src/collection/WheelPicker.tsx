// 돌려서 고르는 휠. 시안 `design_handoff_collection` 의 B안.

import { useEffect, useRef } from 'react';
import { ScrollView, StyleSheet, View, type NativeScrollEvent, type NativeSyntheticEvent } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

/** 한 줄 높이. 드럼 높이(160)와 위아래 여백 계산이 전부 이 값에서 나온다. */
export const ROW_HEIGHT = 32;
/** 보이는 줄 수. 가운데가 고른 것이고 위아래로 두 줄씩 보인다. */
const VISIBLE_ROWS = 5;
const DRUM_HEIGHT = ROW_HEIGHT * VISIBLE_ROWS;
/** 가운데 칸에 오려면 위아래로 두 줄만큼 빈 자리가 필요하다. */
const PAD = ROW_HEIGHT * Math.floor(VISIBLE_ROWS / 2);

export type WheelOption = { value: string; label: string };

export type WheelPickerProps = {
  label: string;
  options: readonly WheelOption[];
  value: string;
  onChange: (value: string) => void;
  /** 화면 낭독기가 읽을 말. 없으면 label 을 쓴다. */
  accessibilityLabel?: string;
};

/** 고른 줄에서 몇 번째인지 계산한다. */
export function indexFromOffset(offsetY: number, count: number) {
  const raw = Math.round(offsetY / ROW_HEIGHT);
  return Math.min(Math.max(raw, 0), Math.max(count - 1, 0));
}

export function WheelPicker({ label, options, value, onChange, accessibilityLabel }: WheelPickerProps) {
  const ref = useRef<ScrollView>(null);
  const selected = Math.max(options.findIndex((option) => option.value === value), 0);

  // 밖에서 값이 바뀌면(예: 검색 후보를 고르면 지역이 자동으로 채워진다) 휠도 그 줄로 간다.
  useEffect(() => {
    ref.current?.scrollTo({ y: selected * ROW_HEIGHT, animated: true });
  }, [selected]);

  const onSettled = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const index = indexFromOffset(event.nativeEvent.contentOffset.y, options.length);
    const next = options[index];
    if (next && next.value !== value) onChange(next.value);
  };

  return (
    <View style={styles.column}>
      <Text variant="caption" weight="bold" color={color.text.muted} style={styles.label}>{label}</Text>
      <View style={styles.drum}>
        {/* 가운데 선택 밴드 — 어느 줄이 골라진 것인지 눈으로 보이게 한다. */}
        <View pointerEvents="none" style={styles.band} />
        <ScrollView
          ref={ref}
          accessibilityLabel={accessibilityLabel ?? label}
          showsVerticalScrollIndicator={false}
          nestedScrollEnabled
          snapToInterval={ROW_HEIGHT}
          decelerationRate="fast"
          contentContainerStyle={styles.content}
          onMomentumScrollEnd={onSettled}
          // 웹에서는 관성 끝 신호가 안 오는 브라우저가 있어 둘 다 듣는다.
          onScrollEndDrag={onSettled}
        >
          {options.map((option, index) => {
            const distance = Math.abs(index - selected);
            return (
              <View key={option.value} style={styles.row}>
                <Text
                  variant={distance === 0 ? 'body' : 'caption'}
                  weight={distance === 0 ? 'bold' : 'regular'}
                  color={distance === 0 ? color.text.heading : color.text.muted}
                  numberOfLines={1}
                  style={distance === 1 ? styles.near : distance >= 2 ? styles.far : undefined}
                >
                  {option.label}
                </Text>
              </View>
            );
          })}
        </ScrollView>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  column: { flex: 1, gap: spacing[1] },
  label: { textAlign: 'center' },
  drum: { height: DRUM_HEIGHT, borderRadius: radius.md, backgroundColor: color.surface.soft, overflow: 'hidden' },
  band: {
    position: 'absolute', left: 0, right: 0, top: PAD, height: ROW_HEIGHT,
    backgroundColor: color.surface.card, borderRadius: radius.sm,
    borderWidth: 1, borderColor: color.surface.field,
  },
  content: { paddingVertical: PAD },
  row: { height: ROW_HEIGHT, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[2] },
  near: { opacity: 0.7 },
  far: { opacity: 0.35 },
});
