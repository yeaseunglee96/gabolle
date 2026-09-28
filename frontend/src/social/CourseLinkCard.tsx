// 기록 속 코스 카드 — 본문의 읽기 전용 공유 링크를 여행 이름 · 일수 · 장소 수로 그린다(S15P21E201-1593).
// 누르면 공유 화면(/s/<토큰>)으로 간다 — 거기에 이미 「내 여행으로 가져가기」가 있다.
// 공유 일정은 로그인 없이 읽힌다(getSharedItinerary). 만료된 링크는 「만료된 코스」로 두고 누를 수 없다.
import { useQuery } from '@tanstack/react-query';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { enPlural, txf } from '@/i18n/format';
import { getSharedItinerary } from '@/share/sharedItinerary';

export function CourseLinkCard({ token }: { token: string }) {
  const router = useRouter();
  const { tx } = useI18n();
  // 같은 코스가 피드와 상세에 거듭 나와도 한 번만 묻는다. 공유 일정은 자주 안 바뀐다.
  const query = useQuery({ queryKey: ['shared-itinerary', token], queryFn: () => getSharedItinerary(token), staleTime: 5 * 60 * 1000 });
  const result = query.data;

  if (!result) {
    return <View accessibilityLabel={tx('코스를 불러오는 중', 'Loading the course')} style={styles.card}><ActivityIndicator color={color.action.primary} /><Text variant="caption" color={color.text.muted}>{tx('코스를 불러오는 중…', 'Loading the course…')}</Text></View>;
  }
  if (result.state === 'expired' || result.state === 'not_found') {
    return (
      <View style={[styles.card, styles.cardOff]}>
        <Text variant="caption" weight="bold" color={color.text.muted}>{tx('코스', 'Course')}</Text>
        <Text weight="bold" color={color.text.muted}>{result.state === 'expired' ? tx('만료된 코스', 'Expired course') : tx('볼 수 없는 코스', 'Course unavailable')}</Text>
      </View>
    );
  }
  const open = () => router.push(`/s/${encodeURIComponent(token)}`);
  if (result.state === 'error') {
    return (
      <Pressable accessibilityRole="link" accessibilityLabel={tx('코스 열기', 'Open course')} onPress={open} style={({ pressed }) => [styles.card, pressed && styles.pressed]}>
        <Text variant="caption" weight="bold" color={color.text.muted}>{tx('코스', 'Course')}</Text>
        <Text weight="bold">{tx('코스를 불러오지 못했어요 — 눌러서 열기', "Couldn't load the course — tap to open")}</Text>
      </Pressable>
    );
  }
  const { data } = result;
  const stops = data.days.reduce((sum, day) => sum + day.items.length, 0);
  const summary = [txf(tx, '%s일', `%s ${enPlural(data.days.length, 'day', 'days')}`, String(data.days.length)), txf(tx, '장소 %s곳', '%s places', String(stops))].join(' · ');
  return (
    <Pressable accessibilityRole="link" accessibilityLabel={txf(tx, '%s 코스 보기', 'View course %s', data.title)} onPress={open} style={({ pressed }) => [styles.card, pressed && styles.pressed]}>
      <Text variant="caption" weight="bold" color={color.action.primary}>{tx('코스', 'Course')}</Text>
      <Text weight="bold" numberOfLines={1}>{data.title}</Text>
      <View style={styles.row}>
        <Text variant="caption" color={color.text.muted} style={styles.grow}>{summary}</Text>
        <Text variant="caption" weight="bold" color={color.text.heading}>{tx('코스 보기 ›', 'View course ›')}</Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing[1], padding: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.tint },
  cardOff: { opacity: 0.8 },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  grow: { flex: 1 },
  pressed: { opacity: 0.72 },
});
