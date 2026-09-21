// 폰에서 탭바가 늘어난 시트 안에 들어가는 것 (시안 04·06).
//
// 늘어나는 것 자체는 TabBar 가 한다 — 피드의 지도 시트와 **같은 부품**이다. 여기 있는 것은
// 그 안에 들어가는 머리와 본문뿐이다.
import { ScrollView, StyleSheet, View } from 'react-native';
import { Pressable } from 'react-native';

import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

export function MyPageSheetBody({
  title,
  description,
  onClose,
  children,
  tx,
}: {
  title: string;
  description: string;
  onClose: () => void;
  children: React.ReactNode;
  tx: (ko: string, en: string) => string;
}) {
  return <>
    {/* 위 손잡이 — 누르면 내린다. 누르는 자리는 보이는 막대보다 넓다. */}
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={tx('내리기', 'Close')}
      onPress={onClose}
      style={styles.handleHit}
    >
      <View style={styles.handle} />
    </Pressable>

    <View style={styles.head}>
      <View style={styles.headCopy}>
        <Eyebrow>{tx('내 계정', 'Account')}</Eyebrow>
        <Text variant="display" weight="bold" numberOfLines={1}>{title}</Text>
        {description ? <Text variant="caption">{description}</Text> : null}
      </View>
      <Pressable accessibilityRole="button" onPress={onClose} style={({ pressed }) => [styles.closeChip, pressed && styles.pressed]}>
        <Text variant="caption" weight="bold" color={color.text.heading} numberOfLines={1}>{tx('내리기', 'Close')}</Text>
      </Pressable>
    </View>

    <ScrollView style={styles.body} contentContainerStyle={styles.bodyContent}>{children}</ScrollView>
  </>;
}

const styles = StyleSheet.create({
  handleHit: { alignSelf: 'center', width: 44, height: 20, alignItems: 'center', justifyContent: 'center' },
  handle: { width: 36, height: 4, borderRadius: 2, backgroundColor: color.surface.field },
  head: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: spacing[3] },
  headCopy: { flex: 1, gap: spacing[1], minWidth: 0 },
  closeChip: { minHeight: 32, justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.soft },
  pressed: { opacity: 0.72 },
  // 머리와 본문 사이에 선을 긋는다 — 본문이 길면 머리가 어디까지인지 안 보인다.
  body: { flex: 1, marginTop: spacing[3], borderTopWidth: 1, borderTopColor: color.surface.border, backgroundColor: color.canvas },
  bodyContent: { paddingTop: spacing[4], paddingBottom: spacing[4] },
});
