// 「현지인 추천」 — 현지인이 제안해 운영자가 승인한 장소에 붙는다(S15P21E201-2031).
// 누가·왜 추천했는지를 같이 보인다. 제안으로 생긴 장소가 아니면 아무것도 그리지 않는다(서버 404).
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { fetchLocalPick, type LocalPick } from '@/discovery/placeSuggestions';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';

export function LocalPickBadge({ placeId, accessToken }: { placeId: string | null | undefined; accessToken: string | null }) {
  const { tx } = useI18n();
  const [pick, setPick] = useState<LocalPick | null>(null);
  useEffect(() => {
    setPick(null);
    if (!placeId || !accessToken) return;
    const controller = new AbortController();
    void fetchLocalPick(placeId, accessToken, controller.signal).then((value) => { if (!controller.signal.aborted) setPick(value); });
    return () => controller.abort();
  }, [placeId, accessToken]);
  if (!pick) return null;
  return (
    <View style={styles.box} accessibilityLabel={tx('현지인 추천', 'Local pick')}>
      <View style={styles.badge}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('현지인 추천', 'Local pick')}</Text></View>
      <Text>{`“${pick.reason}”`}</Text>
      {pick.suggesterName ? <Text variant="caption" color={color.text.body}>{txf(tx, '%s 님이 추천했어요', 'Recommended by %s', pick.suggesterName)}</Text> : null}
      {pick.openingHours ? <Text variant="caption" color={color.text.body}>{txf(tx, '영업시간 %s (추천한 분이 알려 준 값)', 'Hours %s (as shared by the local)', pick.openingHours)}</Text> : null}
      {pick.phone ? <Text variant="caption" color={color.text.body}>{txf(tx, '전화 %s', 'Phone %s', pick.phone)}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacing[1], marginBottom: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  badge: { alignSelf: 'flex-start', paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, backgroundColor: color.action.primary },
});
