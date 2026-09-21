// 사진이 무엇을 찍은 것인지 말하는 표 — S15P21E201-1206.

import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { photoLabels, type PhotoSubject } from '@/discovery/places';
import { useI18n } from '@/i18n';

type Props = {
  /** 값이 없으면 아무것도 안 그린다 — 서버가 이 칸을 안 줘도 화면이 지금과 같다. */
  photoSubject?: PhotoSubject | null;
  style?: StyleProp<ViewStyle>;
};

export function PhotoSubjectBadge({ photoSubject, style }: Props) {
  const { tx } = useI18n();
  const badge = photoLabels({ photoSubject }, tx).badge;
  if (!badge) return null;
  return (
    <View style={[styles.badge, style]}>
      <Text variant="caption" weight="bold" color={color.text.onAction}>{badge}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  // 사진 위에 얹히므로 배경을 깔아 글자가 읽히게 한다 — 피드 카드의 작성자 알약과 같은 이유다.
  badge: {
    alignSelf: 'flex-start',
    minHeight: 24,
    justifyContent: 'center',
    paddingHorizontal: spacing[2],
    borderRadius: radius.full,
    backgroundColor: 'rgba(25,25,25,0.72)',
  },
});
