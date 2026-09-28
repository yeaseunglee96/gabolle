// 여행 핵심 화면의 CTA_다음 · CTA_로그인 · 수정 등 전폭 버튼을 하나로 통일한다.
//
// 🔴 2026-09-19 (S15P21E201-1343) — 부산은행 톤으로 갈래를 다시 짰다.
//
//    **primary(동백 채움)는 화면당 하나다.** 둘째부터는 outline(붉은 선)이나
//    secondary(짙은 회색)를 쓴다. 빨강이 둘이면 사람은 어느 쪽이 「그다음에 할 일」인지
//    고를 수 없다 — 그게 이 배색이 지키려는 것의 거의 전부다.
//
//    없앤 것 둘. `accent` 는 새 배색에서 primary 와 **같은 색**이 되어 이름만 둘이 됐다.
//    `ghost` 는 흰 바탕 + 선이었는데, 새 배색은 카드에 선을 안 두므로 연회색 채움
//    (`tertiary`)이 그 자리를 대신한다. 호출부 102곳을 함께 옮겼다.
import { useMemo, useRef } from 'react';
import { AccessibilityInfo, Animated, Pressable, StyleSheet, type GestureResponderEvent, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { Text } from './Text';

// 화면들이 이 색을 낼 방법이 없어서 `containerStyle` 에 backgroundColor 를 줬는데, 그건
// 바깥 껍데기(Animated.View) 에 붙는다. 안쪽 Pressable 은 자기 색(남색)을 그대로 그리므로
// 버튼 뒤에 더 크고 더 둥근 도형이 하나 더 남았다 — 실기기에서 「남색 버튼 아래로 주황색이
// 삐져나온다」로 보였다. containerStyle 로 넘어간 26곳 중 색이 다른 6곳이 그랬다
// (남색 13곳은 안쪽과 같은 색이라 안 보였을 뿐 같은 결함이다).
type ButtonVariant = 'primary' | 'secondary' | 'field' | 'tertiary' | 'outline' | 'danger';

export type ButtonProps = Omit<PressableProps, 'style'> & {
  label: string;
  variant?: ButtonVariant;
  /** 온보딩·연령확인의 큰 알약 모양 CTA (높이 54 · 완전 둥근 모서리). */
  pill?: boolean;
  /** 줄(row) 안에 다른 것과 나란히 설 때. 전폭 대신 글자 폭이 된다. */
  compact?: boolean;
  /** 버튼 자체의 모양은 안 바꾸고, 화면에서 위아래 여백만 줄 때 쓴다(예: marginTop). */
  containerStyle?: StyleProp<ViewStyle>;
};

const LABEL_COLOR: Record<ButtonVariant, string> = {
  primary: color.text.onAction,
  secondary: color.text.onAction,
  field: color.text.onAction,
  tertiary: color.text.heading,
  /** 선과 글자가 같은 색이다 — 채움이 없으므로 글자가 그 역할을 대신한다. */
  outline: color.action.outline,
  /** 🔴 채움이 아니라 옅은 배경 + 붉은 글자다. 지우는 버튼이 화면에서 가장 붉으면 안 된다. */
  danger: color.state.danger,
};

let reducedMotion = false;
void Promise.resolve(AccessibilityInfo.isReduceMotionEnabled?.())
  .then((value) => { reducedMotion = Boolean(value); })
  .catch(() => { /* 조회할 수 없는 환경이면 애니메이션을 그대로 둔다 */ });
AccessibilityInfo.addEventListener?.('reduceMotionChanged', (value) => { reducedMotion = Boolean(value); });

export function Button({ label, variant = 'primary', pill = false, compact = false, disabled, containerStyle, accessibilityRole, accessibilityState, onPressIn, onPressOut, ...rest }: ButtonProps) {
  const pressProgress = useRef(new Animated.Value(0)).current;
  // 보간 객체를 렌더마다 새로 만들지 않는다 — 이것이 느림의 진짜 주범이었다.
  const animatedStyle = useMemo(() => ({
    opacity: pressProgress.interpolate({ inputRange: [0, 1], outputRange: [1, 0.82] }),
    transform: [{ scale: pressProgress.interpolate({ inputRange: [0, 1], outputRange: [1, 0.98] }) }],
  }), [pressProgress]);
  const animateTo = (value: number, duration: number) => {
    Animated.timing(pressProgress, { toValue: value, duration: reducedMotion ? 0 : duration, useNativeDriver: true }).start();
  };
  const handlePressIn = (event: GestureResponderEvent) => { animateTo(1, 90); onPressIn?.(event); };
  const handlePressOut = (event: GestureResponderEvent) => { animateTo(0, 150); onPressOut?.(event); };

  return (
    <Animated.View style={[containerStyle, !disabled && animatedStyle]}>
      <Pressable
        {...rest}
        accessibilityRole={accessibilityRole ?? 'button'}
        accessibilityState={{ ...accessibilityState, disabled: Boolean(disabled) }}
        disabled={disabled}
        onPressIn={handlePressIn}
        onPressOut={handlePressOut}
        style={[
          styles.base,
          variant === 'primary' && styles.primary,
          variant === 'secondary' && styles.secondary,
          variant === 'field' && styles.field,
          variant === 'tertiary' && styles.tertiary,
          variant === 'outline' && styles.outline,
          variant === 'danger' && styles.danger,
          // pill 은 색 뒤에 둔다 — 모양(높이·모서리)만 덮어쓰고 색은 건드리지 않는다.
          pill && styles.pill,
          // compact 는 맨 뒤다 — 폭과 가로 여백만 덮어쓰고 색·높이·모서리는 그대로 둔다.
          compact && styles.compact,
          disabled && styles.disabled,
        ]}
      >
        <Text variant="body" weight="bold" color={LABEL_COLOR[variant]} style={styles.label}>
          {label}
        </Text>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  base: {
    width: '100%',
    minHeight: 48,
    borderRadius: radius.md,
    paddingVertical: spacing[3],
    // 🔴 S15P21E201-1717 — 가로 여백이 없었다. 폭 100% 전폭 버튼에서는 안 보이지만, 가운데 정렬 부모(alignItems: center)
    //    안에서 글자 폭으로 줄어드는 자리(「내 여행」 빈 화면의 「첫 여행 만들기」)에서는 글자가 알약 끝에 그대로 닿았다
    //    (안드로이드 실기, 2026-09-26). 여백은 폭 안쪽이라 전폭 버튼의 모습은 그대로다.
    paddingHorizontal: spacing[4],
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: { textAlign: 'center' },
  primary: {
    backgroundColor: color.action.primary,
  },
  secondary: {
    backgroundColor: color.action.secondary,
  },
  field: {
    backgroundColor: color.action.field,
  },
  // 🔴 선이 없다. 새 배색은 카드에도 버튼에도 선을 안 둔다 — 바탕과의 밝기 차이로만 뜬다.
  tertiary: {
    backgroundColor: color.action.tertiary,
  },
  // 🔴 큰 면적이 부담스러울 때 채움 대신 쓴다. 선이 1.5 인 것은 1 이면 흰 카드 위에서
  //    거의 안 보이고 2 면 채움처럼 무거워지기 때문이다.
  outline: {
    backgroundColor: color.surface.card,
    borderWidth: 1.5,
    borderColor: color.action.outline,
  },
  // 🔴 채움이 아니다. 「지우기」가 화면에서 가장 눈에 띄는 버튼이면 사람이 그것을 먼저 누른다.
  danger: {
    backgroundColor: color.state.dangerBg,
  },
  pill: {
    minHeight: 54,
    borderRadius: radius.full,
  },
  // 줄(row) 안에 다른 것과 나란히 설 때 쓴다.
  compact: {
    width: 'auto',
    paddingHorizontal: spacing[4],
  },
  disabled: {
    opacity: 0.4,
  },
});
