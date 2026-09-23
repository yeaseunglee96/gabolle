// 폰에서 탭바가 늘어난 시트 안에 들어가는 것 (시안 04·06).
//
// 늘어나는 것 자체는 TabBar 가 한다 — 피드의 지도 시트와 **같은 부품**이다. 여기 있는 것은
// 그 안에 들어가는 머리와 본문뿐이다.
import { ScrollView, StyleSheet, View } from 'react-native';
import { Pressable } from 'react-native';

import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { tabBarBottomMargin } from '@/components/TabBar';
import { color, radius, spacing } from '@/design/tokens';

/** 아무리 좁은 기기에서도 머리와 본문 한 줄은 들어가야 한다. */
const MIN_SHEET_HEIGHT = 320;

/**
 * 이 시트를 얼마나 높이 세울까.
 *
 * 🔴 **위쪽 안전영역을 반드시 뺀다** — S15P21E201-1490(B-01). 전에는 부르는 쪽이
 * `height - spacing[2] - spacing[8]`(40 고정)으로 셌다. `height` 는 상태바·다이내믹
 * 아일랜드를 포함한 **화면 전체 높이**인데 40 만 빼서 시트가 화면 위로 넘쳤고,
 * 그러면 시트의 첫 요소인 **손잡이와 「내리기」 단추가 상태바 뒤로 숨는다.**
 *
 * <p>시트는 아래(`TabBar` 의 `dock` 이 `bottom: 0`)에서 자라므로 윗변은
 * `height - 아래여백 - 이 값` 이다. 실기기(iPhone 16 Pro)로 재면:
 *
 * <pre>
 *   세로  height 874 · top 62 · bottom 34
 *         옛 값 834 → 윗변 y=6   🔴 손잡이가 56px 묻힌다
 *         새 값 770 → 윗변 y=70  ✅ 안전영역(62) 아래
 *   가로  height 402 · top 0 · bottom 21
 *         옛 값 362 → 윗변 y=19  → top 이 0 이라 «우연히» 보였다
 * </pre>
 *
 * <p>그래서 **세로에서만** 닫는 수단이 안 보였다. 실기기 확인(2026-09-22)에서도
 * 가로에서는 손잡이가 보이고 세로에서는 안 보였다 — 이 차이가 그것이다.
 *
 * <p>아래여백은 탭바가 쓰는 것과 **같은 함수**로 구한다. 두 곳에서 따로 계산하면
 * 한쪽만 고쳐지고, 그 차이는 실기기에서만 보인다.
 */
export function myPageSheetHeight(screenHeight: number, insetTop: number, insetBottom: number): number {
  return Math.max(MIN_SHEET_HEIGHT, screenHeight - insetTop - tabBarBottomMargin(insetBottom) - spacing[2]);
}

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
