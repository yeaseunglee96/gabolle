// CTA_다음 · CTA_로그인 · 수정 등 전폭 버튼을 하나로 통일한다. variant 로 배경·글자색만 바뀐다.
import { Pressable, StyleSheet, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';

import { color, radius, spacing, thirdPartyBrand } from '@/design/tokens';
import { Text } from './Text';

type ButtonVariant = 'primary' | 'kakao' | 'ghost';

export type ButtonProps = Omit<PressableProps, 'style'> & {
  label: string;
  variant?: ButtonVariant;
  /** 버튼 자체의 모양은 안 바꾸고, 화면에서 위아래 여백만 줄 때 쓴다(예: marginTop). */
  containerStyle?: StyleProp<ViewStyle>;
};

const LABEL_COLOR: Record<ButtonVariant, string> = {
  primary: color.text.onAction,
  kakao: thirdPartyBrand.kakaoText,
  ghost: color.action.primary,
};

export function Button({ label, variant = 'primary', disabled, containerStyle, ...rest }: ButtonProps) {
  return (
    <Pressable
      {...rest}
      disabled={disabled}
      style={({ pressed }) => [
        styles.base,
        variant === 'primary' && styles.primary,
        variant === 'kakao' && styles.kakao,
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
    backgroundColor: color.action.primary,
  },
  kakao: {
    backgroundColor: thirdPartyBrand.kakaoBg,
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
    opacity: 0.85,
  },
});
