// 지도 위 경로 색의 범례 — 초록·노랑·빨강·회색이 무엇이고, 어떤 규칙으로 칠했는지 한 줄 (S15P21E201-1896).
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

/** 범례에 적을 말 — 색마다 이름, 칠한 규칙 한 줄, (있으면) 덧붙일 한 마디. 그리는 것과 떼어 두어 시험이 글자를 바로 본다. */
export function legendCopy(legend: RouteLegend, tx: Tx): { labels: Record<Grade, string>; rule: string; note: string | null } {
  const labels: Record<Grade, string> = {
    good: tx('잘 맞아요', 'Good fit'),
    fair: tx('보통', 'Fair'),
    bad: tx('안 맞아요', 'Poor fit'),
    unknown: tx('모름', 'Unknown'),
  };
  const { slope, shade } = legend.grading;
  if (slope && shade) {
    return {
      labels,
      rule: tx(
        '경사 60% + 그늘 40%를 합친 점수 · 계단은 빨강 · 그늘 자료가 없는 곳은 경사만',
        'Slope 60% + shade 40% combined · stairs are red · slope only where shade data is missing',
      ),
      // 그늘을 아는 조각이 하나도 없으면 사실상 경사만으로 칠한 것이다 — 그렇게 말한다.
      note: legend.hasShade ? null : tx('지금 보이는 길은 그늘 자료가 아직 없어 경사로만 칠했어요', 'The routes shown have no shade data yet, so they are colored by slope only'),
    };
  }
  if (shade) {
    return {
      labels,
      rule: tx(
        '‘그늘 많은 곳 우선’ 기준 · 그늘이 많을수록 초록, 적을수록 빨강 · 계단은 빨강',
        'By “Prefer shadier places” · more shade is greener, less is red · stairs are red',
      ),
      note: legend.hasShade ? null : tx('지금 보이는 길은 그늘 자료가 아직 없어 회색으로 보여요', 'The routes shown have no shade data yet, so they show in gray'),
    };
  }
  return {
    labels,
    rule: tx(
      '‘가파른 경사 피하기’ 기준 · 초록 5% 미만 · 노랑 5~8.33% · 빨강 8.33% 초과 또는 계단',
      'By “Avoid steep slopes” · green under 5% · yellow 5–8.33% · red over 8.33% or stairs',
    ),
    note: null,
  };
}

export function RouteColorLegend({ legend, tx, style }: { legend: RouteLegend | null | undefined; tx: Tx; style?: StyleProp<ViewStyle> }) {
  if (!legend) return null;
  const { labels, rule, note } = legendCopy(legend, tx);
  return (
    // 눌리지 않는 안내라 지도의 끌기·누르기를 막지 않는다.
    <View pointerEvents="none" style={style}>
      <View
        accessible
        accessibilityLabel={`${ORDER.map((grade) => labels[grade]).join(', ')}. ${rule}${note ? `. ${note}` : ''}`}
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
        <Text variant="micro" color={color.text.body}>{rule}</Text>
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
