// 하루 끝에 돌아가는 이동 — 「숙소로 돌아가기 · 해운대 · 18분」 (S15P21E201-1566).
//
// 서버가 날마다 싣는 returnLeg 를 그날 목록 끝에 한 줄로 그린다(S15P21E201-1565). 마지막 날이 아니면 숙소로,
// 마지막 날이면 여행 출발지(역·집)로 돌아간다. 돌아갈 자리를 모르는 날은 서버가 비우고, 여기서도 안 그린다 —
// 모르는 곳으로 돌아가라고 지어내지 않는다.
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { txf } from '@/i18n/format';
import type { DayReturnLeg } from '@/plan/itinerary';
import { knownPlaceLabel } from '@/plan/origins';

type Tx = (ko: string, en: string) => string;

export function DayReturnRow({ leg, tx, style }: { leg: DayReturnLeg | null | undefined; tx: Tx; style?: StyleProp<ViewStyle> }) {
  if (!leg) return null;
  const title = leg.kind === 'LODGING' ? tx('숙소로 돌아가기', 'Back to your stay') : tx('출발지로 돌아가기', 'Back to where you started');
  const minutes = leg.durationMin == null ? null : Math.round(leg.durationMin);
  const time = minutes == null
    ? null
    : leg.travelDataStatus === 'ESTIMATED'
      ? txf(tx, '%s분 (어림)', '%s min (est.)', minutes)
      : txf(tx, '%s분', '%s min', minutes);
  const detail = [leg.label ? knownPlaceLabel(leg.label, tx) : null, time].filter(Boolean).join(' · ');
  return (
    <View style={[styles.row, style]}>
      <Text weight="bold" color={color.text.muted}>↩</Text>
      <View style={styles.copy}>
        <Text variant="caption" weight="bold">{title}</Text>
        {detail ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{detail}</Text> : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], paddingVertical: spacing[3], paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.card },
  copy: { flex: 1, gap: 2 },
});
