// 좁은 한 줄의 장소 이름 — 한글은 자르지 않고 로마자(또는 영어 이름)만 줄임표로 자른다(S15P21E201-1735).
//
// 한 덩어리 글자로 「돈반 (Donban)」을 numberOfLines={1} 로 자르면 넘칠 때 뒤쪽부터 잘려, 영어 이름이 앞인
// 곳(「Gwangalli Beach (광안리해수욕장)」)은 한글이 잘린다. 한글은 택시 기사·길 묻기에 그대로 보여 줘야 해서
// 두 조각으로 나눠 잘리는 쪽을 정한다. 한국어 화면은 한글 한 조각뿐이라 전과 같다.
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Text, type TextProps } from '@/components/Text';
import { stopNameParts } from '@/discovery/romanize';
import { useI18n } from '@/i18n';

export function StopName({ title, nameEn, style, ...text }: {
  title: string;
  nameEn?: string | null;
  style?: StyleProp<ViewStyle>;
} & Pick<TextProps, 'variant' | 'weight' | 'color'>) {
  const { language } = useI18n();
  const { hangul, other, otherFirst } = stopNameParts(title, nameEn, language);
  if (!other) return <Text {...text} numberOfLines={1} style={[styles.shrink, style]}>{hangul}</Text>;
  const first = otherFirst ? other : hangul;
  const second = otherFirst ? ` (${hangul})` : ` (${other})`;
  return (
    <View style={[styles.row, style]}>
      {/* 잘리는 쪽은 로마자·영어 이름 — otherFirst 면 앞, 아니면 뒤 */}
      <Text {...text} numberOfLines={1} style={otherFirst ? styles.shrink : styles.keep}>{first}</Text>
      <Text {...text} numberOfLines={1} style={otherFirst ? styles.keep : styles.shrink}>{second}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', flexShrink: 1, minWidth: 0 },
  keep: { flexShrink: 0 },
  shrink: { flexShrink: 1, minWidth: 0 },
});
