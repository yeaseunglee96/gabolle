// 「찾는 곳이 없나요? 장소 제안하기」 — 검색 결과가 없을 때·둘러보기·마이페이지에서 제안 화면으로 보낸다(S15P21E201-2031).
// 검색에서 왔으면 친 이름을 들고 간다 — 같은 글자를 두 번 치게 하지 않는다.
import { Pressable, StyleSheet, View } from 'react-native';
import { router } from 'expo-router';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

export function suggestHref(name?: string | null): string {
  const trimmed = name?.trim();
  return trimmed ? `/place-suggest?name=${encodeURIComponent(trimmed)}` : '/place-suggest';
}

export function SuggestPlaceLink({ tx, name, variant = 'inline', onNavigate }: {
  /** 부르는 화면의 tx — 언어 맥락 없이 그려지는 시트(시험 포함)에서도 쓰이도록 받아 온다. */
  tx: (ko: string, en: string) => string;
  name?: string | null;
  /** inline: 검색 결과 없음 아래 한 줄 · card: 둘러보기·마이페이지의 안내 카드 */
  variant?: 'inline' | 'card';
  /** 시트 안에서 부를 때 — 옮겨 가기 전에 시트를 닫는다. */
  onNavigate?: () => void;
}) {
  const go = () => { onNavigate?.(); router.push(suggestHref(name)); };
  if (variant === 'card') {
    return (
      <Pressable accessibilityRole="button" onPress={go} style={({ pressed }) => [styles.card, pressed && styles.pressed]}>
        <View style={styles.flex}>
          <Text weight="bold">{tx('현지인이세요? 장소 제안하기', 'Local? Suggest a place')}</Text>
          <Text variant="caption" color={color.text.body}>{tx('앱에 없는 맛집·명소를 알려 주세요. 확인 뒤 「현지인 추천」으로 올라가요.', 'Share a spot we are missing. After review it appears as a "Local pick".')}</Text>
        </View>
        <Text variant="title" weight="bold" color={color.text.muted}>›</Text>
      </Pressable>
    );
  }
  return (
    <Pressable accessibilityRole="link" onPress={go} style={styles.inline}>
      <Text variant="caption" weight="bold" color={color.action.primary}>{tx('찾는 곳이 없나요? 장소 제안하기', "Can't find it? Suggest a place")}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  inline: { paddingVertical: spacing[2] },
  card: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  pressed: { opacity: 0.7 },
  flex: { flex: 1, minWidth: 0, gap: spacing[1] },
});
