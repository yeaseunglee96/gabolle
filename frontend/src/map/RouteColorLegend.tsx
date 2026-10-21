// 지도 위 경로 색의 범례 — 초록·노랑·빨강·회색이 무엇인지 말로 한 줄 (S15P21E201-1896 · 숫자 뺌 S15P21E201-2023).
//
// 전에 이 자리에는 「경사」·「그늘」 칩(MobilityLayerToggle)이 있었다 — 눌러야 경로와 무관한 겹이 깔렸다. 이제 고른 조건이
// 경로 선 자체를 칠하므로(routeGrading.ts) 누를 것이 없고, 색의 뜻만 알려 준다. 눌리지 않는다(pointerEvents none).
//
// 🔴 범례는 «칠한 선이 있을 때만» 나온다(routeLegendOf 가 null 이면 안 그린다). 조건을 골랐어도 그릴 조각이 없으면
//    색의 뜻을 읽히지 않는다 — 안 칠한 선의 범례는 「문제가 없구나」와 「아직 못 받았구나」를 구분하지 못하게 한다.
// 🔴 그늘 자료가 없어서 경사로만 칠했다면 그렇다고 적는다(legendCopy 의 note). 모르는 것을 아는 척하지 않는다.
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import type { RouteLegend } from '@/map/routeGrading';
import { GRADE_COLOR, type Grade } from '@/map/slopeGrades';

type Tx = (ko: string, en: string) => string;

const ORDER: Grade[] = ['good', 'fair', 'bad', 'unknown'];

/**
 * 범례에 적을 말 — 색마다 이름, (있으면) 덧붙일 한 마디. 그리는 것과 떼어 두어 시험이 글자를 바로 본다.
 *
 * 🔴 S15P21E201-2023: % 숫자와 칠한 규칙 줄을 뺐다. 「여행 갈 때 경사·그늘은 크게 신경 안 쓴다, 퍼센트 수치는 어렵고 복잡하다」(모진성)
 *    — 그리고 경사 % 자체가 ±3~5%p 오차를 품고 있어(2026-10-09 조사) 소수점 숫자는 정밀하다는 착각만 준다.
 *    색의 이름이 곧 뜻이다: 경사만이면 평탄·조금 가파름·가파름(계단 포함), 그늘만이면 그늘 많음·보통·그늘 적음.
 *    문턱(5% · 8.33%)은 그대로 slopeGrades.ts 에 있다 — 화면에 안 적을 뿐이다.
 */
export function legendCopy(legend: RouteLegend, tx: Tx): { labels: Record<Grade, string>; note: string | null } {
  const { slope, shade } = legend.grading;
  const unknown = tx('모름', 'Unknown');
  if (slope && shade) {
    return {
      labels: { good: tx('걷기 편함', 'Easy walk'), fair: tx('보통', 'Fair'), bad: tx('걷기 힘듦', 'Hard walk'), unknown },
      // 그늘을 아는 조각이 하나도 없으면 사실상 경사만으로 칠한 것이다 — 그렇게 말한다.
      note: legend.hasShade ? null : tx('그늘 자료가 없어 경사로만 칠했어요', 'No shade data here, so colored by slope only'),
    };
  }
  if (shade) {
    return {
      labels: { good: tx('그늘 많음', 'Shady'), fair: tx('보통', 'Fair'), bad: tx('그늘 적음', 'Little shade'), unknown },
      note: legend.hasShade ? null : tx('그늘 자료가 아직 없어요', 'No shade data yet'),
    };
  }
  return {
    labels: { good: tx('평탄', 'Flat'), fair: tx('조금 가파름', 'A bit steep'), bad: tx('가파름·계단', 'Steep or stairs'), unknown },
    note: null,
  };
}

export function RouteColorLegend({ legend, tx, style }: { legend: RouteLegend | null | undefined; tx: Tx; style?: StyleProp<ViewStyle> }) {
  if (!legend) return null;
  const { labels, note } = legendCopy(legend, tx);
  return (
    // 눌리지 않는 안내라 지도의 끌기·누르기를 막지 않는다.
    <View pointerEvents="none" style={style}>
      <View
        accessible
        accessibilityLabel={`${ORDER.map((grade) => labels[grade]).join(', ')}${note ? `. ${note}` : ''}`}
        style={styles.card}
      >
        <View style={styles.row}>
          {ORDER.map((grade) => (
            <View key={grade} style={styles.item}>
              <View style={[styles.swatch, { backgroundColor: GRADE_COLOR[grade] }]} />
              <Text variant="micro" weight="bold" color={color.text.heading}>{labels[grade]}</Text>
            </View>
          ))}
        </View>
        {note ? <Text variant="micro" color={color.text.muted}>{note}</Text> : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    alignSelf: 'flex-start', maxWidth: 360, gap: spacing[1], paddingHorizontal: spacing[3], paddingVertical: spacing[2],
    borderRadius: radius.md, backgroundColor: color.surface.card,
    shadowColor: color.brand.navy, shadowOpacity: 0.08, shadowRadius: 8, shadowOffset: { width: 0, height: 2 }, elevation: 2,
  },
  row: { flexDirection: 'row', flexWrap: 'wrap', columnGap: spacing[3], rowGap: 2 },
  item: { flexDirection: 'row', alignItems: 'center', gap: spacing[1] },
  swatch: { width: 16, height: 4, borderRadius: 2 },
});
