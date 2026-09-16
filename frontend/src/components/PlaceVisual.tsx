import { Image, StyleSheet, View, type ImageSourcePropType, type StyleProp, type ViewStyle } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { photoLabels } from '@/discovery/places';
import { useI18n } from '@/i18n';

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
  // S15P21E201-1125 — 서버가 실제로 찍은 사진. 목록 응답에 사진 칸이 아예 없어서
  // 여태 이 부품은 장소 **이름**으로 번들 사진 셋(해운대·감천·광안)만 골랐고,
  // 나머지는 전부 회색 판이었다. 이제 서버 사진이 오면 그것을 먼저 쓴다.
  photoUrl?: string | null;
  // 🔴 photoUrl 과 짝이다. 관광공사 공공누리 제1유형이라 **출처 표기가 이용 조건**이다.
  // 사진만 그리고 이 문구를 빼면 라이선스 위반이다. 문구는 서버가 주므로 지어내지 않는다.
  photoSource?: string | null;
};

export function PlaceVisual({ name, address, style, photoUrl, photoSource }: Props) {
  const { tx } = useI18n();
  const visualKey = resolvePlaceVisual(name, address);
  const credit = photoUrl ? photoLabels({ photoSource }, tx).credit : null;

  // 서버 사진이 있으면 그것이 먼저다 — 번들 사진은 이름이 우연히 맞은 것이고,
  // 서버 사진은 그 장소를 가리켜 붙은 것이다.
  //
  // 🔴 credit 이 없으면 서버 사진을 **안 쓴다.** 출처 표기가 공공누리 이용 조건이라,
  // 「사진은 그리고 문구만 빠지는」 경우가 생길 수 있으면 안 된다. 사람이 기억해서
  // 지키는 규칙은 언젠가 깨지므로, 표기 없는 사진이 나갈 길 자체를 없앤다.
  // 출처가 안 왔으면 번들 사진이나 자리표시로 내려간다 — 화면은 비지 않는다.
  if (photoUrl && credit) {
    return (
      <View style={[styles.frame, style]}>
        <Image source={{ uri: photoUrl }} resizeMode="cover" accessibilityLabel={`${name} 장소 사진`} style={styles.image} />
        {credit ? (
          <View style={styles.creditBar}>
            <Text variant="caption" numberOfLines={1} color={color.text.onAction}>{credit}</Text>
          </View>
        ) : null}
      </View>
    );
  }

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
  // 출처 문구는 사진 위에 얹는다 — 카드가 작아 아래에 줄을 더하면 이름이 밀린다.
  // 어두운 띠를 깔아 밝은 사진 위에서도 읽히게 한다 (공공누리 표기 의무).
  creditBar: { position: 'absolute', left: 0, right: 0, bottom: 0, paddingHorizontal: spacing[2], paddingVertical: spacing[1], backgroundColor: 'rgba(0, 0, 0, 0.45)' },
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
