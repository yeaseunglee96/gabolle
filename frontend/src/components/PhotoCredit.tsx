// 사진 출처 줄 — 라이선스가 있으면 누르면 그 사진의 파일 페이지가 열린다 (S15P21E201-1610).
//
// 🔴 위키미디어 커먼즈 사진(CC BY·CC BY-SA 등)은 출처와 함께 라이선스 이름과 링크를 보여야 쓸 수 있다.
//    글자는 photoLabels 가 만든다(「사진: … · CC BY-SA 3.0」). 카드 사진 위의 띠는 PhotoCreditBar 다(S15P21E201-1682). 여기는 그 줄을 링크로 만드는 일만 한다.
import { Linking, StyleSheet } from 'react-native';

import { Text, type TextProps } from './Text';

export function PhotoCredit({ credit, licenseUrl, style, ...rest }: TextProps & { credit: string; licenseUrl: string | null }) {
  if (!licenseUrl) return <Text {...rest} style={style}>{credit}</Text>;
  return (
    <Text
      {...rest}
      accessibilityRole="link"
      accessibilityLabel={credit}
      onPress={() => { void Linking.openURL(licenseUrl).catch(() => {}); }}
      style={[style, styles.link]}
    >
      {credit}
    </Text>
  );
}

const styles = StyleSheet.create({ link: { textDecorationLine: 'underline' } });
