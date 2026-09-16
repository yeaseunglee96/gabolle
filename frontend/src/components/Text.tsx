// 화면마다 폰트 크기·줄높이·기본 색을 반복해서 고르지 않으려고 타이포 토큰을 감싼 텍스트.
// variant 를 고르면 색까지 기본값이 따라온다 — 색이 필요하면 그때만 color prop 으로 덮어쓴다.
import { Text as RNText, type TextProps as RNTextProps } from 'react-native';

import { color, fontFamily, type as typeTokens } from '@/design/tokens';

type Variant = 'hero' | 'display' | 'title' | 'body' | 'util' | 'caption' | 'eyebrow';

type Weight = 'regular' | 'medium' | 'bold';

export type TextProps = RNTextProps & {
  variant?: Variant;
  /** 토큰 기본 색을 덮어써야 할 때만 쓴다 (예: 버튼 배경 위 흰 글자). */
  color?: string;
  weight?: Weight;
};

const DEFAULT_COLOR: Record<Variant, string> = {
  hero: color.text.onAction,
  display: color.text.heading,
  title: color.text.heading,
  body: color.text.body,
  util: color.text.muted,
  caption: color.text.muted,
  eyebrow: color.text.eyebrow,
};

const SIZE: Record<Variant, { size: number; lineHeight: number; letterSpacing: number }> = {
  hero: typeTokens.hero,
  display: typeTokens.display,
  title: typeTokens.title,
  body: typeTokens.body,
  util: typeTokens.util,
  caption: typeTokens.caption,
  eyebrow: typeTokens.caption,
};

const FONT_WEIGHT: Record<Weight, '400' | '500' | '700'> = {
  regular: '400',
  medium: '500',
  bold: '700',
};

const FONT_FAMILY: Record<Weight, string> = {
  regular: fontFamily.regular,
  medium: fontFamily.medium,
  bold: fontFamily.bold,
};

export function Text({ variant = 'body', color: colorOverride, weight = 'regular', style, ...rest }: TextProps) {
  const { size, lineHeight, letterSpacing } = SIZE[variant];
  return (
    <RNText
      {...rest}
      style={[
        {
          fontFamily: FONT_FAMILY[weight],
          fontSize: size,
          lineHeight,
          letterSpacing,
          fontWeight: FONT_WEIGHT[weight],
          color: colorOverride ?? DEFAULT_COLOR[variant],
        },
        style,
      ]}
    />
  );
}
