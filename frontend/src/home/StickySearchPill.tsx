// 웹 홈 — 내리면 큰 검색창이 위쪽 알약으로 접혀 따라온다(S15P21E201-1931, 사용자 의견 2026-10-02 · UI 캔버스 ㉖).
// 전에는 조금만 내려도 여행 조건 검색창이 위로 사라져, 일정을 물어보려면 맨 위까지 다시 올라가야 했다.
//
// 🔴 알약은 「다시 펼치기」 단추다 — 여기서 값을 고치지 않는다. 누르면 맨 위로 올라가 원래 검색창을 쓴다.
//    두 곳에서 같은 값을 고치게 하면 어느 쪽이 맞는지 헷갈리고, 고른 값이 한쪽에만 남는다.
import { Animated, Pressable, StyleSheet, View } from 'react-native';
import { useEffect, useRef } from 'react';
import Svg, { Circle, Path } from 'react-native-svg';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

/** 큰 검색창의 아래 끝을 지나면 알약을 띄운다 — 지나기 전에 띄우면 같은 검색창이 두 개 보인다. */
export function shouldShowStickySearch(scrollY: number, searchBottom: number | null): boolean {
  if (searchBottom === null || searchBottom <= 0) return false;
  return scrollY > searchBottom;
}

export function StickySearchPill({ visible, reduceMotion, onPress }: { visible: boolean; reduceMotion: boolean; onPress: () => void }) {
  const { tx } = useI18n();
  const shown = useRef(new Animated.Value(visible ? 1 : 0)).current;
  useEffect(() => {
    if (reduceMotion) { shown.setValue(visible ? 1 : 0); return; }
    Animated.timing(shown, { toValue: visible ? 1 : 0, duration: 160, useNativeDriver: false }).start();
  }, [visible, reduceMotion, shown]);

  return (
    <Animated.View
      pointerEvents={visible ? 'box-none' : 'none'}
      accessibilityElementsHidden={!visible}
      importantForAccessibility={visible ? 'auto' : 'no-hide-descendants'}
      style={[styles.band, { opacity: shown, transform: [{ translateY: shown.interpolate({ inputRange: [0, 1], outputRange: [-12, 0] }) }] }]}
    >
      <Pressable
        testID="sticky-search"
        accessibilityRole="button"
        accessibilityLabel={tx('여행 조건 다시 펼치기 — 출발지, 날짜, 인원', 'Open trip search again — start, dates, travellers')}
        onPress={onPress}
        style={({ pressed }) => [styles.pill, pressed && styles.pressed]}
      >
        <Text weight="bold" numberOfLines={1}>{tx('어디서 출발', 'Where from')}</Text>
        <View style={styles.divider} />
        <Text weight="bold" numberOfLines={1}>{tx('날짜', 'Dates')}</Text>
        <View style={styles.divider} />
        <Text color={color.text.muted} numberOfLines={1}>{tx('인원', 'Travellers')}</Text>
        <View style={styles.searchDot}>
          <Svg width={16} height={16} viewBox="0 0 24 24" fill="none" stroke={color.text.onAction} strokeWidth={2.6} strokeLinecap="round"><Circle cx={11} cy={11} r={6.5} /><Path d="M20 20l-4-4" /></Svg>
        </View>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  // 위쪽 메뉴 바로 밑에 붙는 얇은 띠 — 내용 위에 떠서 자리를 먹지 않는다
  band: { position: 'absolute', top: 0, left: 0, right: 0, zIndex: 15, alignItems: 'center', paddingVertical: spacing[3], backgroundColor: color.surface.card, borderBottomWidth: 1, borderBottomColor: color.surface.border, shadowColor: color.brand.navy, shadowOpacity: 0.06, shadowRadius: 12, shadowOffset: { width: 0, height: 6 } },
  pill: { minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: spacing[4], paddingLeft: spacing[6], paddingRight: spacing[1], borderRadius: radius.full, borderWidth: 1.5, borderColor: color.action.outline, backgroundColor: color.surface.card },
  divider: { width: 1, height: 20, backgroundColor: color.surface.field },
  searchDot: { width: 40, height: 40, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.primary },
  pressed: { opacity: 0.8 },
});
