import { Image, StyleSheet, View, type ImageSourcePropType, type StyleProp, type ViewStyle } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

const PLACE_IMAGES = {
  haeundae: require('../../assets/home/haeundae.png'),
  gamcheon: require('../../assets/home/gamcheon.png'),
  gwangalli: require('../../assets/home/gwangalli.png'),
} satisfies Record<string, ImageSourcePropType>;

export type PlaceVisualKey = keyof typeof PLACE_IMAGES | null;

/** 사진과 장소가 실제로 일치할 때만 번들 이미지를 쓴다. 모르는 장소에 임의 사진을 붙이지 않는다. */
export function resolvePlaceVisual(name: string, address?: string | null): PlaceVisualKey {
  const haystack = `${name} ${address ?? ''}`.toLocaleLowerCase();
  if (/해운대|haeundae/.test(haystack)) return 'haeundae';
  if (/감천|gamcheon/.test(haystack)) return 'gamcheon';
  if (/광안|gwangalli|gwangan/.test(haystack)) return 'gwangalli';
  return null;
}

type Props = {
  name: string;
  address?: string | null;
  style?: StyleProp<ViewStyle>;
};

export function PlaceVisual({ name, address, style }: Props) {
  const visualKey = resolvePlaceVisual(name, address);
  return (
    <View style={[styles.frame, style]}>
      {visualKey ? (
        <Image
          source={PLACE_IMAGES[visualKey]}
          resizeMode="cover"
          accessibilityLabel={`${name} 장소 사진`}
          style={styles.image}
        />
      ) : (
        <View accessibilityLabel={`${name} 장소 이미지 준비 중`} style={styles.fallback}>
          <View style={styles.sun} />
          <View style={styles.waveBack} />
          <View style={styles.waveFront} />
          <View style={styles.pin}>
            <View style={styles.pinCore} />
          </View>
          <Text variant="caption" weight="bold" color={color.brand.navy} style={styles.fallbackLabel}>BUSAN</Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  frame: { position: 'relative', width: '100%', aspectRatio: 4 / 3, overflow: 'hidden', borderRadius: radius.md, backgroundColor: color.surface.soft },
  image: { width: '100%', height: '100%' },
  fallback: { flex: 1, overflow: 'hidden', backgroundColor: '#dceff2' },
  sun: { position: 'absolute', top: spacing[3], right: spacing[3], width: 28, height: 28, borderRadius: radius.full, backgroundColor: '#ffd391' },
  waveBack: { position: 'absolute', left: -24, right: -24, bottom: -28, height: '58%', borderRadius: radius.full, backgroundColor: '#8fcbd3', transform: [{ rotate: '-4deg' }] },
  waveFront: { position: 'absolute', left: -32, right: -20, bottom: -48, height: '58%', borderRadius: radius.full, backgroundColor: '#4da8b5', transform: [{ rotate: '5deg' }] },
  pin: { position: 'absolute', top: '27%', left: '44%', width: 30, height: 30, borderRadius: radius.full, borderBottomRightRadius: radius.sm, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.orange, transform: [{ rotate: '45deg' }] },
  pinCore: { width: 10, height: 10, borderRadius: radius.full, backgroundColor: color.surface.card },
  fallbackLabel: { position: 'absolute', left: spacing[3], top: spacing[3], letterSpacing: 1.2 },
});
