// 달력 제목을 누르면 뜨는 «달 바로 고르기» — S15P21E201-1539.
//
// 7개월 뒤 여행을 잡으려면 › 를 일곱 번 눌러야 했다(팀원 의견). 연도 고르기는 두지 않는다 —
// 고를 수 있는 날짜가 오늘부터 12개월뿐이라, 연도를 고르게 하면 대부분 못 고르는 값이 된다.
import { Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { MAX_MONTH_OFFSET, monthAt } from '@/home/monthJump';

const MONTH_EN = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

export function MonthPicker({ today, selected, onPick, tx }: {
  today: Date;
  /** 지금 보고 있는 달(몇 달 뒤) — 칩에 표시한다. */
  selected: number;
  onPick: (offset: number) => void;
  tx: (ko: string, en: string) => string;
}) {
  const months = Array.from({ length: MAX_MONTH_OFFSET + 1 }, (_, offset) => ({ offset, ...monthAt(today, offset) }));
  return (
    <View style={styles.grid}>
      {months.map(({ offset, year, month }) => {
        // 칸마다 연도를 붙인다 — 해가 바뀌는 자리가 보이고, 번역표에 이미 있는 「%d년 %d월」 한 모양으로 끝난다.
        const label = tx(`${year}년 ${month + 1}월`, `${MONTH_EN[month]} ${year}`);
        const active = offset === selected;
        return (
          <Pressable
            key={offset}
            accessibilityRole="button"
            accessibilityState={{ selected: active }}
            onPress={() => onPick(offset)}
            style={({ pressed }) => [styles.chip, active && styles.chipActive, pressed && styles.pressed]}
          >
            <Text variant="caption" weight="bold" color={active ? color.text.onAction : color.text.heading}>{label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], paddingVertical: spacing[2] },
  // 세 칸씩 — 폰 폭에서 「2027년 1월」이 한 줄에 들어가는 너비다.
  chip: { flexBasis: '30%', flexGrow: 1, minHeight: 40, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  // 선택 상태는 짙은 회색 — 빨강은 「눌러야 할 것」이라 고른 것에 쓰지 않는다(tokens.ts 규칙 2).
  chipActive: { borderColor: color.action.secondary, backgroundColor: color.action.secondary },
  pressed: { opacity: 0.8 },
});
