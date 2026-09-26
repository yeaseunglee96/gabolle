// 여행 페이지의 「예산 대비」 카드 — 넓은 화면과 폰이 같은 것을 그린다 (S15P21E201-1535).
// 1단계 때 TripPageDesktop 안에 있던 것을 그대로 옮겼다. 자리(폭·비율)는 부르는 쪽이 style 로 준다.
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import type { BudgetCategoryKey, BudgetSummary } from '@/plan/itineraryBudget';

import { formatManwon } from './tripPageModel';

const BUDGET_LABEL: Record<BudgetCategoryKey, [string, string]> = {
  FOOD: ['식비', 'Food'],
  CAFE: ['카페', 'Cafés'],
  ADMISSION: ['입장·체험', 'Admission'],
  TRANSIT: ['교통 (추정)', 'Transit (est.)'],
};

export function TripBudgetCard({ budget, style }: { budget: BudgetSummary | null; style?: StyleProp<ViewStyle> }) {
  const { tx } = useI18n();
  const known = budget && budget.known > 0;
  const limit = budget?.budgetKrw ?? null;
  const remaining = budget?.remainingKrw ?? null;
  const used = known && limit ? Math.min(1, budget.krw / limit) : 0;
  const breakdown = budget?.categories.filter((entry) => entry.krw > 0).map((entry) => `${tx(...BUDGET_LABEL[entry.key])} ${txf(tx, '%s원', '₩%s', entry.krw.toLocaleString())}`).join(' · ');
  return (
    <View style={[styles.card, style]}>
      <View style={styles.rowBetween}>
        <Text variant="caption" weight="bold" color={color.text.muted}>{tx('예산 대비', 'Against budget')}</Text>
        {remaining !== null && known ? (
          <Text variant="caption" weight="bold" color={remaining >= 0 ? color.state.success : color.state.danger}>
            {remaining >= 0 ? txf(tx, '%s 남음', '%s left', formatManwon(remaining, tx)) : txf(tx, '%s 넘음', '%s over', formatManwon(-remaining, tx))}
          </Text>
        ) : null}
      </View>
      <View style={styles.rowBaseline}>
        <Text variant="display" weight="bold">{known ? txf(tx, '%s원', '₩%s', budget.krw.toLocaleString()) : tx('비용 미정', 'Cost unknown')}</Text>
        {limit !== null ? <Text variant="caption" color={color.text.muted}>{`/ ${txf(tx, '%s원', '₩%s', limit.toLocaleString())}`}</Text> : null}
      </View>
      {limit !== null ? <View style={styles.budgetTrack}><View style={[styles.budgetUsed, { flex: used }]} /><View style={{ flex: 1 - used }} /></View> : <Text variant="caption" color={color.text.muted}>{tx('예산을 정하지 않은 여행이에요', 'No budget set for this trip')}</Text>}
      {breakdown ? <Text variant="caption" color={color.text.muted}>{breakdown}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { padding: spacing[4], gap: 6, borderRadius: radius.lg, backgroundColor: color.surface.card },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing[2] },
  rowBaseline: { flexDirection: 'row', alignItems: 'baseline', gap: 6 },
  budgetTrack: { flexDirection: 'row', height: 10, borderRadius: radius.full, overflow: 'hidden', backgroundColor: color.surface.soft },
  budgetUsed: { backgroundColor: color.action.secondary },
});
