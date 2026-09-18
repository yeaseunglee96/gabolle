// 두 칸짜리 범위 토글 — 「내 근처」와 「부산 전체」.
//
// 🔴 칸마다 배경을 켜고 끄지 않는다. 그러면 **어디서 어디로 옮겨 갔는지가 안 보인다** —
//    눈에 남는 것은 「지금 여기가 켜져 있다」뿐이다. 알약 하나가 움직이면 「방금 반대쪽에
//    있었다」가 남는다. 이 저장소의 홈 시작 바가 이미 같은 방식을 쓴다(PlanStartBar).
//
// 🔴 자리를 재서 옮긴다. 시안은 CSS 로 「절반에서 4 뺀 폭」이라고 적었는데 RN 에는 그 계산이
//    없다. 퍼센트로 어림잡으면 안쪽 여백만큼 알약이 칸보다 커져서 양끝이 바탕 밖으로 나간다.
import { useEffect, useRef, useState } from 'react';
import { Animated, Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

/** 바탕 안쪽 여백. 알약은 이만큼 들어가 앉는다. */
const INSET = spacing[1];

export type ScopeSwitchOption<T extends string> = { value: T; label: string };

export function ScopeSwitch<T extends string>({
  options,
  value,
  onChange,
  style,
}: {
  /** 정확히 둘. 셋 이상이면 「절반」이 아니게 된다. */
  options: readonly [ScopeSwitchOption<T>, ScopeSwitchOption<T>];
  value: T;
  onChange: (next: T) => void;
  style?: object;
}) {
  const index = options[1].value === value ? 1 : 0;
  const slide = useRef(new Animated.Value(index)).current;
  const [optionWidth, setOptionWidth] = useState(0);

  useEffect(() => {
    // 시안의 오버슈트 — 살짝 지나갔다 자리를 잡는다.
    Animated.spring(slide, { toValue: index, damping: 14, stiffness: 180, useNativeDriver: false }).start();
  }, [index, slide]);

  return (
    <View
      accessibilityRole="tablist"
      style={[styles.track, style]}
      onLayout={(event) => setOptionWidth(Math.max(0, (event.nativeEvent.layout.width - INSET * 2) / 2))}
    >
      {/* 재기 전에는 안 그린다 — 0 폭짜리 알약이 왼쪽 끝에 한 번 깜빡이지 않게. */}
      {optionWidth > 0 ? (
        <Animated.View
          // 🔴 손짓을 막는다. 알약이 글자 위를 덮고 있어서, 안 막으면 누르는 자리가
          //    알약에 가로막혀 그 칸이 안 눌린다.
          pointerEvents="none"
          style={[
            styles.pill,
            {
              width: optionWidth,
              transform: [{ translateX: slide.interpolate({ inputRange: [0, 1], outputRange: [0, optionWidth] }) }],
            },
          ]}
        />
      ) : null}

      {options.map((option) => {
        const selected = option.value === value;
        return (
          <Pressable
            key={option.value}
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            onPress={() => onChange(option.value)}
            style={styles.option}
          >
            <Text weight="bold" numberOfLines={1} color={selected ? color.text.onAction : color.text.body}>
              {selected ? '✓ ' : ''}{option.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  track: { position: 'relative', flexDirection: 'row', padding: INSET, borderRadius: radius.full, backgroundColor: color.surface.soft },
  pill: { position: 'absolute', left: INSET, top: INSET, bottom: INSET, borderRadius: radius.full, backgroundColor: color.brand.orange },
  option: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full },
});
