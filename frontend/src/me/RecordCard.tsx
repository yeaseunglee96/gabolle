// 기록 한 장 — 프로필 화면 둘(`/me` · `/user/[id]`)이 같이 쓴다.
//
// 🔴 피드 카드(feed.tsx)와 다른 부품이다. 저쪽은 목록에서 «읽는» 카드라 본문과 반응이
//    같이 서고, 이쪽은 격자에서 «고르는» 카드라 사진과 이름만 있다. 하나로 합치려다
//    두 화면 중 하나가 반드시 억지가 된다.
import { Image, Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { relativeStoryTime, type StoryDto } from '@/social/stories';
import { regionText } from '@/social/districtNames';

export function RecordCard({
  story, onPress, tx, width,
}: {
  story: StoryDto;
  onPress: () => void;
  tx: (ko: string, en: string) => string;
  /**
   * 넓은 화면의 기록 «줄»은 4열이라 폭을 밖에서 준다.
   * 안 주면 폰 격자의 2열(48%)로 선다.
   */
  width?: number;
}) {
  const cover = story.images[0]?.url ?? null;
  // 제목 자리는 장소 이름이 먼저다 — 피드 카드와 같은 규칙.
  const title = story.place?.name ?? (story.region ? regionText(story.region, tx) : null) ?? tx('기록', 'Record');
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={[styles.card, width ? { width } : null]}>
      {cover ? (
        <Image source={{ uri: cover }} resizeMode="cover" accessibilityLabel="" style={styles.cover} />
      ) : (
        // 🔴 빈 회색 네모를 두지 않는다 — 「사진을 못 불러왔다」로 읽힌다.
        // 실제로는 사진 없이 쓴 글이므로 본문을 대신 보여 준다.
        <View style={[styles.cover, styles.coverEmpty]}>
          <Text weight="bold" numberOfLines={3} style={styles.coverText}>{story.body}</Text>
        </View>
      )}
      <View style={styles.meta}>
        <Text weight="bold" numberOfLines={1}>{title}</Text>
        <Text variant="caption" color={color.text.muted} numberOfLines={1}>{relativeStoryTime(story.createdAt, tx)}</Text>
      </View>
    </Pressable>
  );
}

/**
 * 폰 기록 격자(2열) — 카드 폭을 따로 안 주는 자리는 모두 이 격자를 쓴다.
 *
 * 🔴 가로 `gap` 을 두지 않는다. 카드가 48% 라 두 장이면 96% 이고 남는 4% 가 곧 가로 틈이다.
 *    여기에 gap(16) 을 더하면 폭 400 미만(4% < 16)인 폰에서 두 번째 카드가 다음 줄로
 *    밀려 한 줄에 한 장씩 왼쪽에만 섰다 (S15P21E201-1805). 틈은 `space-between`
 *    (**남는 폭을 카드 사이에 나눠 주는 정렬**)으로 만들고, 세로 틈만 `rowGap` 으로 준다.
 *    홀수 마지막 카드는 한 장뿐인 줄이라 왼쪽 칸에 선다.
 */
export const RECORD_PHONE_CARD_FRACTION = 0.48;
export const recordPhoneGrid = {
  flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', rowGap: spacing[4],
} as const;

const styles = StyleSheet.create({
  card: { width: '48%', borderRadius: radius.lg, backgroundColor: color.surface.card, overflow: 'hidden' },
  cover: { width: '100%', aspectRatio: 1 },
  coverEmpty: { alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: color.surface.soft },
  coverText: { textAlign: 'center' },
  meta: { padding: spacing[3], gap: 2 },
});
