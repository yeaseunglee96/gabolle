import { Image, StyleSheet, View, type ImageSourcePropType, type StyleProp, type ViewStyle } from 'react-native';

import { PhotoCreditBar } from '@/components/PhotoCreditBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { PhotoSubjectBadge } from '@/components/PhotoSubjectBadge';
import { photoLabels, type PhotoLicense, type PhotoSubject } from '@/discovery/places';
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
  // — 서버가 실제로 찍은 사진. 목록 응답에 사진 칸이 아예 없어서
  // 여태 이 부품은 장소 이름으로 번들 사진 셋(해운대·감천·광안)만 골랐고
  // 나머지는 전부 회색 판이었다. 이제 서버 사진이 오면 그것을 먼저 쓴다.
  photoUrl?: string | null;
  // photoUrl 과 짝이다. 관광공사 공공누리 제1유형이라 출처 표기가 이용 조건이다.
  // 사진만 그리고 이 문구를 빼면 라이선스 위반이다. 문구는 서버가 주므로 지어내지 않는다.
  photoSource?: string | null;
  // — 이 사진이 무엇을 찍은 것인가. 이 칸이 여기 없어서 여태
  // 「행사장 사진」을 축제 화면에서만 말하고 장소 화면에서는 아무 말도 안 했다.
  // 값이 없으면 아무것도 안 그린다 — 서버가 안 줘도 지금과 같다.
  photoSubject?: PhotoSubject | null;
  /** 위키미디어 사진의 라이선스 — 있으면 출처 줄에 이름을 붙이고 누르면 파일 페이지가 열린다(S15P21E201-1610). */
  photoLicense?: PhotoLicense | null;
};

export function PlaceVisual({ name, address, style, photoUrl, photoSource, photoSubject, photoLicense }: Props) {
  const { tx } = useI18n();
  const visualKey = resolvePlaceVisual(name, address);
  const labels = photoLabels({ photoSource, photoLicense }, tx);
  const credit = photoUrl ? labels.credit : null;
  const frame = frameStyle(style);

  // 서버 사진이 있으면 그것이 먼저다 — 번들 사진은 이름이 우연히 맞은 것이고
  // 서버 사진은 그 장소를 가리켜 붙은 것이다.
  if (photoUrl && credit) {
    return (
      <View testID="place-visual-frame" style={frame}>
        <Image source={{ uri: photoUrl }} resizeMode="cover" accessibilityLabel={`${name} 장소 사진`} style={styles.image} />
        <PhotoSubjectBadge photoSubject={photoSubject} style={styles.subjectBadge} />
        {/* 출처 띠는 둘러보기 카드와 같은 부품이다(S15P21E201-1682) — 「사진: …」 두 줄까지, 라이선스는 둘째 줄에 따로. */}
        {photoSource ? <PhotoCreditBar source={photoSource} license={photoLicense?.name ?? null} licenseUrl={labels.licenseUrl} tx={tx} /> : null}
      </View>
    );
  }

  return (
    <View testID="place-visual-frame" style={frame}>
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

/**
 * 틀 모양 — 기본은 폭 100% · 4:3. 부르는 쪽이 높이를 주면(칸 채우기) 4:3 을 버린다 (S15P21E201-1952).
 * 높이와 비율이 함께 있으면 네이티브는 높이에 맞춰 폭을 4/3 배로 늘려, 칸 밖으로 나간 양쪽이 잘린다 —
 * 탭 앱 일정 카드의 출처 띠가 「진: 한국관광공사」로 앞 글자가 잘렸다. 비율을 직접 주면 그 비율을 쓴다.
 */
function frameStyle(style: StyleProp<ViewStyle>): StyleProp<ViewStyle> {
  const own = StyleSheet.flatten(style) ?? {};
  if (own.height != null && own.aspectRatio == null) {
    const { aspectRatio: _dropped, ...rest } = StyleSheet.flatten([styles.frame, style]);
    return rest;
  }
  return [styles.frame, style];
}

const styles = StyleSheet.create({
  // 출처 문구는 사진 위에 얹는다 — 카드가 작아 아래에 줄을 더하면 이름이 밀린다.
  // 어두운 띠를 깔아 밝은 사진 위에서도 읽히게 한다 (공공누리 표기 의무).
  // 사진 위 왼쪽 위. 출처 띠는 아래에 있으므로 서로 안 겹친다.
  subjectBadge: { position: 'absolute', left: spacing[2], top: spacing[2] },
  frame: { position: 'relative', width: '100%', aspectRatio: 4 / 3, overflow: 'hidden', borderRadius: radius.md, backgroundColor: color.surface.soft },
  image: { width: '100%', height: '100%' },
  fallback: { flex: 1, overflow: 'hidden', backgroundColor: '#dceff2' },
  // 해는 오른쪽 위 모서리를 비운다 — 그 자리에 하트(저장) 버튼이 얹혀, 해가 하트 뒤의 동그라미로 보였다(사용자 지적 2026-10-02)
  sun: { position: 'absolute', top: spacing[3], right: 56, width: 28, height: 28, borderRadius: radius.full, backgroundColor: '#ffd391' },
  waveBack: { position: 'absolute', left: -24, right: -24, bottom: -28, height: '58%', borderRadius: radius.full, backgroundColor: '#8fcbd3', transform: [{ rotate: '-4deg' }] },
  waveFront: { position: 'absolute', left: -32, right: -20, bottom: -48, height: '58%', borderRadius: radius.full, backgroundColor: '#4da8b5', transform: [{ rotate: '5deg' }] },
  pin: { position: 'absolute', top: '27%', left: '44%', width: 30, height: 30, borderRadius: radius.full, borderBottomRightRadius: radius.sm, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.secondary, transform: [{ rotate: '45deg' }] },
  pinCore: { width: 10, height: 10, borderRadius: radius.full, backgroundColor: color.surface.card },
  fallbackLabel: { position: 'absolute', left: spacing[3], top: spacing[3], letterSpacing: 1.2 },
});
