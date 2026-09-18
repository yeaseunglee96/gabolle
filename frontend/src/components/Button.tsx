// 여행 핵심 화면의 CTA_다음 CTA_로그인 수정 등 전폭 버튼을 하나로 통일한다.
// 기본 primary 는 피그마 APP/12~13의 길찾기·안내 CTA와 같은 브랜드 네이비를 쓴다.
import { useMemo, useRef } from 'react';
import { AccessibilityInfo, Animated, Pressable, StyleSheet, type GestureResponderEvent, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { Text } from './Text';

// 화면들이 이 색을 낼 방법이 없어서 `containerStyle` 에 backgroundColor 를 줬는데, 그건
// 바깥 껍데기(Animated.View) 에 붙는다. 안쪽 Pressable 은 자기 색(남색)을 그대로 그리므로
// 버튼 뒤에 더 크고 더 둥근 도형이 하나 더 남았다 — 실기기에서 「남색 버튼 아래로 주황색이
// 삐져나온다」로 보였다. containerStyle 로 넘어간 26곳 중 색이 다른 6곳이 그랬다
// (남색 13곳은 안쪽과 같은 색이라 안 보였을 뿐 같은 결함이다).
type ButtonVariant = 'primary' | 'secondary' | 'field' | 'ghost' | 'accent' | 'danger';

export type ButtonProps = Omit<PressableProps, 'style'> & {
  label: string;
  variant?: ButtonVariant;
  /** 온보딩·연령확인의 큰 알약 모양 CTA (높이 54 완전 둥근 모서리). */
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
  ghost: color.brand.navy,
  accent: color.text.onAction,
  danger: color.text.onAction,
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
          variant === 'ghost' && styles.ghost,
          variant === 'accent' && styles.accent,
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
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: { textAlign: 'center' },
  primary: {
    backgroundColor: color.brand.navy,
  },
  secondary: {
    backgroundColor: color.action.secondary,
  },
  field: {
    backgroundColor: color.action.field,
  },
  ghost: {
    backgroundColor: color.surface.card,
    borderWidth: 1,
    borderColor: color.surface.field,
  },
  accent: {
    backgroundColor: color.brand.orange,
  },
  danger: {
    backgroundColor: color.state.danger,
  },
  pill: {
    minHeight: 54,
    borderRadius: radius.full,
  },
  // 줄(row) 안에 다른 것과 나란히 설 때 쓴다
  compact: {
    width: 'auto',
    paddingHorizontal: spacing[4],
  },
  disabled: {
    opacity: 0.4,
  },
});
