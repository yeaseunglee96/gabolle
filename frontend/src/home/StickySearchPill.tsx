// 웹 홈 — 내리면 큰 검색창이 위쪽 알약으로 접혀 따라온다(S15P21E201-1931, 사용자 의견 2026-10-02 · UI 캔버스 ㉖).
// 전에는 조금만 내려도 여행 조건 검색창이 위로 사라져, 일정을 물어보려면 맨 위까지 다시 올라가야 했다.
//
// 🔴 알약은 「다시 펼치기」 단추다 — 여기서 값을 고치지 않는다. 누르면 맨 위로 올라가 원래 검색창을 쓴다.
//    두 곳에서 같은 값을 고치게 하면 어느 쪽이 맞는지 헷갈리고, 고른 값이 한쪽에만 남는다.
import { Pressable, StyleSheet, View } from 'react-native';
import Svg, { Circle, Path } from 'react-native-svg';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import type { PillLabels } from '@/home/stickySearchStore';

/** 큰 검색창의 아래 끝을 지나면 알약을 띄운다 — 지나기 전에 띄우면 같은 검색창이 두 개 보인다. */
export function shouldShowStickySearch(scrollY: number, searchBottom: number | null): boolean {
  if (searchBottom === null || searchBottom <= 0) return false;
  return scrollY > searchBottom;
}

/**
 * 위쪽 메뉴 가운데에 앉는 작은 검색 알약(S15P21E201-1931). 누르면 맨 위로 올라가 큰 검색창을 쓴다.
 * 🔴 흰 띠(밴드)는 없앴다 — 「흰 칸이 생기는 게 아니라 검색창 자체가 작아지면서」(사용자 요청 2026-10-02). 띠 대신
 *    위쪽 메뉴가 이 알약을 직접 품고, 큰 검색창은 스크롤에 맞춰 줄어들며 사라진다(stickySearchStore 의 searchCollapse).
 */
export function SearchPillButton({ onPress, narrow = false, labels = null }: { onPress: () => void; narrow?: boolean; labels?: PillLabels | null }) {
  const { tx } = useI18n();
  // 고른 값이 있으면 그것을, 없으면 빈 칸 안내 — ㉖ 시안 「어디서 출발 · 날짜 추가 · 성인 2」
  const origin = labels?.origin ?? tx('어디서 출발', 'Where from');
  const dates = labels?.dates ?? tx('날짜 추가', 'Add dates');
  const people = labels?.people ?? tx('인원', 'Travellers');
  return (
    <Pressable
      testID="sticky-search"
      accessibilityRole="button"
      accessibilityLabel={tx('여행 조건 다시 펼치기 — 출발지, 날짜, 인원', 'Open trip search again — start, dates, travellers')}
      onPress={onPress}
      style={({ pressed }) => [styles.pill, narrow && styles.pillNarrow, pressed && styles.pressed]}
    >
      <Text weight="bold" numberOfLines={1} style={styles.shrink}>{origin}</Text>
      <View style={styles.divider} />
      <Text weight="bold" numberOfLines={1} style={styles.shrink}>{dates}</Text>
      {narrow ? null : <><View style={styles.divider} /><Text weight="medium" color={color.text.muted} numberOfLines={1} style={styles.shrink}>{people}</Text></>}
      <View style={styles.searchDot}>
        <Svg width={16} height={16} viewBox="0 0 24 24" fill="none" stroke={color.text.onAction} strokeWidth={2.6} strokeLinecap="round"><Circle cx={11} cy={11} r={6.5} /><Path d="M20 20l-4-4" /></Svg>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  pillNarrow: { gap: spacing[3], paddingLeft: spacing[4] },
  shrink: { flexShrink: 1, minWidth: 0 },
  pill: { maxWidth: 440, minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: spacing[4], paddingLeft: spacing[6], paddingRight: spacing[1], borderRadius: radius.full, borderWidth: 1.5, borderColor: color.action.outline, backgroundColor: color.surface.card },
  divider: { width: 1, height: 20, backgroundColor: color.surface.field },
  searchDot: { width: 40, height: 40, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.primary },
  pressed: { opacity: 0.8 },
});
