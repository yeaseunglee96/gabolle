// 사진이 **무엇을 찍은 것인지** 말하는 표 — S15P21E201-1206.
//
// 🔴 이게 왜 필요한가.
//
//    장소 사진에는 **그 장소를 직접 찍은 것**과 **그 장소가 들어 있는 건물·동네를 찍은 것**
//    이 섞여 있다. 실측으로는 부산 축제 사진 35건 중 **실제로 그 축제를 찍은 것이 1건**
//    이었다(S15P21E201-1021). 나머지는 그 축제가 열리는 곳을 찍은 사진이다.
//
//    말해 주지 않으면 사용자는 **「이 장소가 이렇게 생겼구나」로 읽는다.** 다른 것을 같게
//    그리는 것이라, 안 조사한 것을 「없다」로 그렸던 결함(S15P21E201-996)과 같은 종류다.
//
// 🔴 「보여줘야 할 때만」은 이 부품이 아니라 photoLabels 가 정한다.
//
//    그 장소를 **직접 찍은 사진에는 아무것도 안 나온다.** 여기서 따로 조건을 두지 않는다 —
//    판정이 두 곳에 있으면 한쪽만 고쳐져서 어긋난다.
//
// 🔴 왜 부품으로 뺐나.
//
//    사진을 그리는 화면이 셋인데(장소 상세 · 부산 둘러보기 · 로컬 탐색) **셋 다 사진을
//    그리는 방식이 다르다.** 그래서 「무엇을 말할지」만 여기 모으고 「어디에 얹을지」는
//    부르는 쪽이 정한다. 뱃지가 화면마다 다른 말을 하면 그게 이 결함의 재발이다.

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
    backgroundColor: 'rgba(11,29,58,0.72)',
  },
});
