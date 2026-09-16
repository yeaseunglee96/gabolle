// 여행 핵심 화면의 CTA_다음 · CTA_로그인 · 수정 등 전폭 버튼을 하나로 통일한다.
// 기본 primary 는 피그마 APP/12~13의 길찾기·안내 CTA와 같은 브랜드 네이비를 쓴다.
//
// kakao variant 는 04 로그인에 있었지만 로그인 수단이 Google 하나로 확정되면서 걷어냈다
// (카카오는 지도·리뷰 API 로만 쓴다). 나중에 다시 필요해지면 그때 복원한다.
//
// secondary·field 는 16~23(여행 준비 이후 화면들) 실측에서 추가했다 — 그 화면들의 전폭 CTA 가
// action.primary 와 다른 파랑(action.secondary·action.field)을 쓴다(tokens.ts 주석 참고).
import { useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, Animated, Pressable, StyleSheet, type GestureResponderEvent, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { Text } from './Text';

type ButtonVariant = 'primary' | 'secondary' | 'field' | 'ghost';

export type ButtonProps = Omit<PressableProps, 'style'> & {
  label: string;
  variant?: ButtonVariant;
  /** 버튼 자체의 모양은 안 바꾸고, 화면에서 위아래 여백만 줄 때 쓴다(예: marginTop). */
  containerStyle?: StyleProp<ViewStyle>;
};

const LABEL_COLOR: Record<ButtonVariant, string> = {
  primary: color.text.onAction,
  secondary: color.text.onAction,
  field: color.text.onAction,
  ghost: color.brand.navy,
};

// 사용자 요청(2026-09-16, 토스 참고): 예전엔 눌림 스타일이 Pressable의 pressed 값으로 순간
// 전환됐다(스타일이 그 프레임에 바로 바뀜) — 토스 버튼 특유의 "살짝 부드럽게 눌리는" 느낌이
// 없었다. RN 내장 Animated로 스케일·투명도에 시간(duration)을 줘서 부드럽게 만든다.
// 🔴 reanimated(react-native-worklets)는 이 저장소 Jest 설정에 목(mock)이 없어 Button을
// 쓰는 화면 테스트가 통째로 깨진다 — 그래서 별도 설정이 필요 없는 RN 내장 Animated를 쓴다.
export function Button({ label, variant = 'primary', disabled, containerStyle, accessibilityRole, accessibilityState, onPressIn, onPressOut, ...rest }: ButtonProps) {
  const [reducedMotion, setReducedMotion] = useState(false);
  useEffect(() => {
    let active = true;
    void AccessibilityInfo.isReduceMotionEnabled?.().then((value) => { if (active) setReducedMotion(value); });
    return () => { active = false; };
  }, []);
  const pressProgress = useRef(new Animated.Value(0)).current;
  const animatedStyle = {
    opacity: pressProgress.interpolate({ inputRange: [0, 1], outputRange: [1, 0.82] }),
    transform: [{ scale: pressProgress.interpolate({ inputRange: [0, 1], outputRange: [1, 0.98] }) }],
  };
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
  disabled: {
    opacity: 0.4,
  },
});
