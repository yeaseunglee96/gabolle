// 여행 핵심 화면의 CTA_다음 · CTA_로그인 · 수정 등 전폭 버튼을 하나로 통일한다.
// 기본 primary 는 피그마 APP/12~13의 길찾기·안내 CTA와 같은 브랜드 네이비를 쓴다.
//
// kakao variant 는 04 로그인에 있었지만 로그인 수단이 Google 하나로 확정되면서 걷어냈다
// (카카오는 지도·리뷰 API 로만 쓴다). 나중에 다시 필요해지면 그때 복원한다.
//
// secondary·field 는 16~23(여행 준비 이후 화면들) 실측에서 추가했다 — 그 화면들의 전폭 CTA 가
// action.primary 와 다른 파랑(action.secondary·action.field)을 쓴다(tokens.ts 주석 참고).
import { Pressable, StyleSheet, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';

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

export function Button({ label, variant = 'primary', disabled, containerStyle, accessibilityRole, accessibilityState, ...rest }: ButtonProps) {
  return (
    <Pressable
      {...rest}
      accessibilityRole={accessibilityRole ?? 'button'}
      accessibilityState={{ ...accessibilityState, disabled: Boolean(disabled) }}
      disabled={disabled}
      style={({ pressed }) => [
        styles.base,
        variant === 'primary' && styles.primary,
        variant === 'secondary' && styles.secondary,
        variant === 'field' && styles.field,
        variant === 'ghost' && styles.ghost,
        disabled && styles.disabled,
        pressed && !disabled && styles.pressed,
        containerStyle,
      ]}
    >
      <Text variant="body" weight="bold" color={LABEL_COLOR[variant]}>
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    width: '100%',
    borderRadius: radius.md,
    paddingVertical: spacing[3],
    alignItems: 'center',
    justifyContent: 'center',
  },
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
  pressed: {
    opacity: 0.82,
    transform: [{ scale: 0.98 }],
  },
});
