// 카드형 배경 조각. 08 접근성 카드처럼 강조가 필요한 자리는 tinted 를 켠다.
import { StyleSheet, View, type ViewProps } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';

type CardProps = ViewProps & {
  tinted?: boolean;
  padded?: boolean;
};

export function Card({ tinted, padded = true, style, ...rest }: CardProps) {
  return (
    <View
      {...rest}
      style={[
        styles.base,
        { backgroundColor: tinted ? color.surface.tint : color.surface.card },
        padded && styles.padded,
        style,
      ]}
    />
  );
}

const styles = StyleSheet.create({
  base: {
    borderRadius: radius.md,
  },
  padded: {
    padding: spacing[4],
  },
});
