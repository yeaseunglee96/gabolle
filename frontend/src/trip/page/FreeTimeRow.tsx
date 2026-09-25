// 두 곳 사이의 빈 시각 — 「자유 시간 · 50분」 (S15P21E201-1668).
//
// 서버가 곳마다 갈래별로 머무는 시간만 깔고 남는 시간을 곳 사이에 비워 두면서(S15P21E201-1667) 생긴 칸이다.
// 새 항목 종류가 아니다 — 두 시각의 차이(tripPageModel 의 freeTimeMinutes)로만 드러나고, 모르면 안 그린다.
// 폰은 구간 줄 안에, 넓은 화면은 카드 아래·지도 목록 사이에 같은 알약으로 놓는다.
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { txf } from '@/i18n/format';

import { formatDuration } from './tripPageModel';

type Tx = (ko: string, en: string) => string;

export function FreeTimeRow({ minutes, tx, style }: { minutes: number | null; tx: Tx; style?: StyleProp<ViewStyle> }) {
  if (minutes === null) return null;
  return (
    <View style={[styles.pill, style]}>
      <Text variant="micro" weight="bold" color={color.text.body}>{txf(tx, '자유 시간 · %s', 'Free time · %s', formatDuration(minutes, tx))}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  pill: { alignSelf: 'flex-start', paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, backgroundColor: color.surface.tint },
});
