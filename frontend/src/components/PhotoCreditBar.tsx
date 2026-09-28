// 카드 사진 위 출처 띠 — 사진 아래쪽에 얹는다 (S15P21E201-1682).
//
// 🔴 출처는 꾸밈이 아니라 이용 조건이다. 전에는 한 줄에서 잘려 「사진: 한국관광공사 공…」처럼 이용 조건(공공누리 몇 유형)이
//    안 보였고, 홈(「사진 제공:」 어두운 띠)과 둘러보기(「사진:」 흰 알약에 흐린 글자)가 서로 달랐다. 이제 둘 다 이 띠다.
//    - 문구는 「사진: …」, 어두운 띠에 흰 글자, 두 줄까지(조율 세션 결정).
//    - 위키미디어(CC BY-SA 등)처럼 라이선스가 따로 있으면 둘째 줄에 따로 둔다 — 출처가 길어도 라이선스가 잘리지 않는다.
//      라이선스가 있으면 띠를 누르면 파일 페이지가 열린다(S15P21E201-1610).
import { Linking, Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Text } from '@/components/Text';
import { color, spacing } from '@/design/tokens';
import { photoSourceShortText, photoSourceText } from '@/discovery/places';
import { txf } from '@/i18n/format';

type Tx = (ko: string, en: string) => string;

export function PhotoCreditBar({ source, license, licenseUrl, tx, style }: {
  source: string; license: string | null; licenseUrl: string | null; tx: Tx; style?: StyleProp<ViewStyle>;
}) {
  // 띠에는 짧은 출처(기관 · 공공누리 유형), 화면 낭독에는 긴 출처 전체(S15P21E201-1705, 사용자 결정).
  const credit = txf(tx, '사진: %s', 'Photo: %s', photoSourceShortText(source, tx));
  const spoken = txf(tx, '사진: %s', 'Photo: %s', photoSourceText(source, tx));
  const full = license ? `${spoken} · ${license}` : spoken;
  const lines = license ? (
    <>
      <Text variant="caption" numberOfLines={1} color={color.text.onAction}>{credit}</Text>
      <Text variant="caption" numberOfLines={1} color={color.text.onAction} style={licenseUrl ? styles.link : undefined}>{license}</Text>
    </>
  ) : (
    <Text variant="caption" numberOfLines={2} color={color.text.onAction}>{credit}</Text>
  );
  if (!licenseUrl) return <View accessible accessibilityLabel={full} style={[styles.bar, style]}>{lines}</View>;
  return (
    <Pressable accessibilityRole="link" accessibilityLabel={full} onPress={() => { void Linking.openURL(licenseUrl).catch(() => {}); }} style={[styles.bar, style]}>
      {lines}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  bar: { position: 'absolute', left: 0, right: 0, bottom: 0, paddingHorizontal: spacing[2], paddingVertical: spacing[1], backgroundColor: 'rgba(0, 0, 0, 0.45)' },
  link: { textDecorationLine: 'underline' },
});
