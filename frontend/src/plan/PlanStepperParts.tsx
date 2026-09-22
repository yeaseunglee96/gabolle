// 조건 화면의 조각들 — 진행 점, 선택지 카드, 「이렇게 반영돼요」 띠, 답한 질문 칩.
//
// 화면 파일이 이미 크다. 눈에 보이는 부품을 떼어 두면 질문 본문 로직과 섞이지 않는다.
import { Image, Pressable, StyleSheet, View, type ImageSourcePropType } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

/**
 * 진행 점 — 지금 질문만 길쭉하다.
 *
 * 🔴 색이 셋이다. 지금(orange) · 지나며 답한 것(green) · 아직(회색). 지나온 것과 아직인
 *    것을 같은 색으로 두면 「어디까지 왔나」가 안 보이고, 그러면 점이 장식이 된다.
 */
export function StepDots({
  total, required = total, index, settled, onJump, label,
}: {
  total: number;
  /** 앞의 몇 개가 필수인가 — 그 뒤에 틈을 두고 작게 그린다. 「열 개」가 아니라 「셋 + 나머지」로 읽힌다(S15P21E201-1376). */
  required?: number;
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
        const optional = i >= required;
        return (
          <Pressable
            key={i}
            accessibilityRole="tab"
            accessibilityState={{ selected: now }}
            accessibilityLabel={label(i)}
            onPress={() => onJump(i)}
            style={[styles.dot, optional && styles.dotOptional, i === required && styles.dotGap, now && styles.dotNow, done && styles.dotDone]}
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
  label, sub, selected, disabled, onPress, image,
}: {
  label: string;
  sub: string;
  selected: boolean;
  disabled?: boolean;
  onPress: () => void;
  /** 사진 카드 — 여행 취향(바다·도심·카페…)처럼 글자보다 그림이 빨리 읽히는 문항. 없으면 지금처럼 글자 카드. */
  image?: ImageSourcePropType;
}) {
  if (image) {
    // 🔴 사진 카드는 고른 것을 «색 채움»이 아니라 «테두리 + ✓»로 말한다 — 사진 위에 남색을 덮으면 사진이 죽는다(2026-09-21, 시안 4 Taste).
    return (
      <Pressable
        accessibilityRole="checkbox"
        accessibilityState={{ checked: selected, disabled }}
        disabled={disabled}
        onPress={onPress}
        style={({ pressed }) => [
          styles.photoOption,
          selected && styles.photoOptionOn,
          disabled && styles.optionOff,
          pressed && !disabled && styles.optionPressed,
        ]}
      >
        <View style={styles.photoWrap}>
          <Image source={image} resizeMode="cover" accessibilityLabel="" style={styles.photo} />
          {selected ? <View style={styles.photoCheck}><Text variant="caption" weight="bold" color={color.text.onAction}>✓</Text></View> : null}
        </View>
        <View style={styles.photoBody}>
          <Text weight="bold" color={disabled ? color.text.muted : color.text.heading} numberOfLines={1}>{label}</Text>
          {sub ? <Text variant="caption" color={color.text.muted} numberOfLines={2}>{sub}</Text> : null}
        </View>
      </Pressable>
    );
  }
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
  dot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.surface.field },
  dotOptional: { width: 5, height: 5, opacity: 0.7 },
  dotGap: { marginLeft: spacing[2] },
  dotNow: { width: 22, height: 8, opacity: 1, backgroundColor: color.action.secondary },
  dotDone: { backgroundColor: color.state.success },

  option: {
    minHeight: 72, justifyContent: 'center', gap: 2, paddingHorizontal: spacing[3], paddingVertical: spacing[3],
    borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.brand.ivory,
  },
  optionOn: { borderColor: color.brand.navy, backgroundColor: color.brand.navy },
  photoOption: { borderRadius: radius.md, borderWidth: 1.5, borderColor: color.surface.border, backgroundColor: color.surface.card, overflow: 'hidden' },
  photoOptionOn: { borderColor: color.action.outline },
  // 🔴 zIndex: 0 — RN-web 의 Image 는 z-index -1 로 그려져 배경 있는 부모 뒤에 숨는다(로그인 판·마이페이지 커버와 같은 결함).
  photoWrap: { height: 88, backgroundColor: color.surface.soft, zIndex: 0 },
  photo: { width: '100%', height: '100%' },
  photoCheck: { position: 'absolute', top: spacing[2], right: spacing[2], width: 24, height: 24, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.outline },
  photoBody: { gap: 2, paddingHorizontal: spacing[3], paddingVertical: spacing[2] },
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
