// 화면별 첫 안내의 말풍선 — UI 캔버스 ㉔(S15P21E201-1885).
//
// 시안의 모양(동백이 + 제목 한 줄 + 두 줄 설명 + 「처음 한 번만 보여요」 · 「알겠어요」)을 화면 «안»에 둔다.
// 🔴 화면 전체를 흐리게 덮는 비추기로 만들지 않았다 — 장소 정보처럼 내려 보는 화면에서는 가리킬 단추가
//    화면 밖에 있을 때가 많아, 덮으면 가리킬 것 없는 막만 뜬다. 말풍선을 그 단추 바로 위에 두고 꼬리로 가리킨다.
// 제목은 누르면 생기는 일로, 설명은 두 줄 안쪽(㉔-5 규칙).
import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { GabolleMascot } from '@/components/DongbaekMascot';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

export function GuideCallout({ title, body, onDone, pointDown = false, style }: {
  title: string;
  body: string;
  onDone: () => void;
  /** 아래에 있는 단추를 가리키는 꼬리 */
  pointDown?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const { tx } = useI18n();
  return (
    <View accessibilityRole="alert" style={[styles.card, style]}>
      <View style={styles.head}>
        <GabolleMascot state="open" still style={styles.mascot} />
        <View style={styles.copy}>
          <Text variant="body" weight="bold">{title}</Text>
          <Text variant="util" color={color.text.body}>{body}</Text>
        </View>
      </View>
      <View style={styles.foot}>
        <Text variant="caption" color={color.text.muted}>{tx('처음 한 번만 보여요', 'Shown only once')}</Text>
        <Pressable accessibilityRole="button" onPress={onDone} style={({ pressed }) => [styles.ok, pressed && styles.pressed]}>
          <Text variant="util" weight="bold" color={color.text.onAction}>{tx('알겠어요', 'Got it')}</Text>
        </Pressable>
      </View>
      {pointDown ? <View style={styles.tail} /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1.5, borderColor: color.surface.field },
  head: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3] },
  mascot: { width: 40, height: 40 },
  copy: { flex: 1, minWidth: 0, gap: spacing[1] },
  foot: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] },
  // 「알겠어요」는 짙은 회색 — 동백 채움은 그 화면의 주 단추 몫이다(화면당 하나).
  ok: { minHeight: 40, paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.action.secondary, alignItems: 'center', justifyContent: 'center' },
  pressed: { opacity: 0.8 },
  tail: { position: 'absolute', bottom: -8, left: spacing[6], width: 14, height: 14, backgroundColor: color.surface.card, borderRightWidth: 1.5, borderBottomWidth: 1.5, borderColor: color.surface.field, transform: [{ rotate: '45deg' }] },
});
