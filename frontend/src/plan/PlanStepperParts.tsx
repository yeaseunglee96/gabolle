// 조건 화면의 조각들 — 진행 점, 선택지 카드, 「이렇게 반영돼요」 띠, 답한 질문 칩.
//
// 화면 파일이 이미 크다. 눈에 보이는 부품을 떼어 두면 질문 본문 로직과 섞이지 않는다.
import { Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

/**
 * 진행 점 — 지금 질문만 길쭉하다.
 *
 * 🔴 색이 셋이다. 지금(orange) · 지나며 답한 것(green) · 아직(회색). 지나온 것과 아직인
 *    것을 같은 색으로 두면 「어디까지 왔나」가 안 보이고, 그러면 점이 장식이 된다.
 */
export function StepDots({
  total, index, settled, onJump, label,
}: {
  total: number;
  index: number;
  settled: (i: number) => boolean;
  onJump: (i: number) => void;
  label: (i: number) => string;
}) {
  return (
    <View accessibilityRole="tablist" style={styles.dots}>
      {Array.from({ length: total }, (_, i) => {
        const now = i === index;
        const done = !now && i < index && settled(i);
        return (
          <Pressable
            key={i}
            accessibilityRole="tab"
            accessibilityState={{ selected: now }}
            accessibilityLabel={label(i)}
            onPress={() => onJump(i)}
            style={[styles.dot, now && styles.dotNow, done && styles.dotDone]}
          />
        );
      })}
    </View>
  );
}

/**
 * 선택지 카드 — 라벨 아래에 **부제**가 붙는다.
 *
 * 🔴 부제가 이 카드의 전부다. 「해운대」만 있으면 고르는 사람이 무엇이 들어오는지 모르고,
 *    일정이 나온 뒤에야 안다. 그때는 되돌리기 비싸다.
 */
export function OptionCard({
  label, sub, selected, disabled, onPress,
}: {
  label: string;
  sub: string;
  selected: boolean;
  disabled?: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityState={{ checked: selected, disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.option,
        selected && styles.optionOn,
        disabled && styles.optionOff,
        pressed && !disabled && styles.optionPressed,
      ]}
    >
      <Text weight="bold" color={disabled ? color.text.muted : selected ? color.text.onAction : color.text.heading} numberOfLines={1}>{label}</Text>
      {sub ? (
        <Text variant="caption" color={selected ? color.text.onDarkMuted : color.text.muted} numberOfLines={2}>{sub}</Text>
      ) : null}
    </Pressable>
  );
}

/** 「이렇게 반영돼요」 — 지금 답으로 일정이 어떻게 달라지는지. */
export function EffectBand({ text, tx }: { text: string; tx: (ko: string, en: string) => string }) {
  return (
    <View style={styles.effect}>
      <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('이렇게 반영돼요', 'What this changes')}</Text>
      <Text variant="caption" color={color.text.heading} style={styles.effectBody}>{text}</Text>
    </View>
  );
}

/** 답한 질문 요약 칩 — 누르면 그 질문으로 돌아간다. */
export function AnsweredChip({ label, value, onPress }: { label: string; value: string; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [styles.answered, pressed && styles.optionPressed]}>
      <Text variant="caption" weight="bold" color={color.state.success}>✓</Text>
      <Text variant="caption" color={color.text.muted} numberOfLines={1}>{label}</Text>
      <Text variant="caption" weight="bold" numberOfLines={1} style={styles.answeredValue}>{value}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  dots: { flexDirection: 'row', alignItems: 'center', gap: spacing[1] },
  dot: { width: 6, height: 6, borderRadius: radius.full, backgroundColor: color.surface.field },
  dotNow: { width: 22, backgroundColor: color.action.secondary },
  dotDone: { backgroundColor: color.state.success },

  option: {
    minHeight: 72, justifyContent: 'center', gap: 2, paddingHorizontal: spacing[3], paddingVertical: spacing[3],
    borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.brand.ivory,
  },
  optionOn: { borderColor: color.brand.navy, backgroundColor: color.brand.navy },
  optionOff: { opacity: 0.5 },
  optionPressed: { opacity: 0.82 },

  effect: {
    flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2],
    paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.tint,
  },
  effectBody: { flex: 1, minWidth: 0 },

  answered: {
    flexDirection: 'row', alignItems: 'center', gap: spacing[1], minHeight: 32, paddingHorizontal: spacing[3],
    borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card,
  },
  answeredValue: { maxWidth: 180 },
});
