// 하루 시작 — 「숙소에서 시작 · 신라스테이 해운대」 (S15P21E201-1580).
//
// 서버가 날마다 싣는 start 를 그날 목록 맨 위에 한 줄로 그린다. 첫날은 여행 출발지(역·집), 둘째 날부터는 숙소다.
// 끝의 「숙소로 돌아가기」(DayReturnRow)와 짝이다 — 끝만 있고 시작이 없으니 서버가 출발지를 안 쓰는 것처럼 보였다.
// 첫 곳까지 걸리는 시간은 여기 안 적는다. 첫 카드가 이미 「숙소에서 7분」으로 적는다 — 같은 것을 두 번 그리지 않는다.
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import type { DayStart } from '@/plan/itinerary';
import { knownPlaceLabel } from '@/plan/origins';

type Tx = (ko: string, en: string) => string;

export function DayStartRow({ start, tx, style }: { start: DayStart | null | undefined; tx: Tx; style?: StyleProp<ViewStyle> }) {
  if (!start) return null;
  const title = start.kind === 'LODGING' ? tx('숙소에서 시작', 'Start from your stay') : tx('출발지에서 시작', 'Start from where you set off');
  return (
    <View style={[styles.row, style]}>
      <Text weight="bold" color={color.text.muted}>↪</Text>
      <View style={styles.copy}>
        <Text variant="caption" weight="bold">{title}</Text>
        {start.label ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{knownPlaceLabel(start.label, tx)}</Text> : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], paddingVertical: spacing[3], paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.card },
  copy: { flex: 1, gap: 2 },
});
