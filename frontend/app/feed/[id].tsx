// 기록 상세 — 피드 카드를 누르면 오는 화면 (S15P21E201-238).
//
// GET /api/v1/stories/:id 계약이 아직 없다. 그래서 새 요청을 지어내지 않고, 피드 목록을
// 불러올 때 이미 받은 전체 StoryDto를 stories.ts의 클라이언트 캐시에서 그대로 읽는다 —
// 목록에서 곧장 눌러 들어온 경우만 지원하고, 캐시에 없는 id(딥링크 등)는 "찾을 수 없음"으로
// 정직하게 보여준다.
import { Image, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { getCachedStory, relativeStoryTime, VISIBILITY_LABEL } from '@/social/stories';

export default function StoryDetail() {
  const router = useRouter();
  const { tx } = useI18n();
  const { id } = useLocalSearchParams<{ id: string }>();
  const story = id ? getCachedStory(id) : null;

  return (
    <Screen scroll>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('피드로 돌아가기', 'Back to feed')} onPress={() => (router.canGoBack() ? router.back() : router.replace('/feed'))} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
        <Text variant="title" weight="bold">‹ {tx('피드', 'Feed')}</Text>
      </Pressable>

      {story ? (
        <View style={styles.card}>
          <View style={styles.headerRow}>
            <Pressable accessibilityRole="link" accessibilityLabel={tx(`${story.author.displayName} 프로필 보기`, `View ${story.author.displayName}'s profile`)} onPress={() => router.push(`/user/${story.author.id}`)} style={styles.grow}>
              <Text variant="title" weight="bold">{story.author.displayName}</Text>
              <Text variant="caption" color={color.text.muted}>
                {relativeStoryTime(story.createdAt, tx)}
                {story.place?.name ? ` · ${story.place.name}` : story.region ? ` · ${story.region}` : ''}
              </Text>
            </Pressable>
            {story.mine && story.visibility !== 'PUBLIC' ? (
              <View style={styles.visibilityBadge}><Text variant="caption" weight="bold" color={color.text.muted}>{tx(...VISIBILITY_LABEL[story.visibility])}</Text></View>
            ) : null}
          </View>

          <Text color={color.text.body} style={styles.body}>{story.body}</Text>

          {story.images.length ? (
            <View style={styles.images}>
              {story.images.map((image) => (
                <Image key={image.url} source={{ uri: image.url }} resizeMode="cover" accessibilityLabel={tx('여행 기록 사진', 'Trip record photo')} style={styles.image} />
              ))}
            </View>
          ) : null}
        </View>
      ) : (
        <View style={styles.notice} accessibilityRole="alert">
          <Text variant="title" weight="bold">{tx('기록을 찾을 수 없어요', 'Could not find this record')}</Text>
          <Text color={color.text.body}>{tx('피드 목록에서 다시 눌러 들어와 주세요.', 'Please open it again from the feed list.')}</Text>
          <Button label={tx('피드로 돌아가기', 'Back to feed')} onPress={() => router.replace('/feed')} containerStyle={styles.recoveryButton} />
        </View>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  back: { minHeight: 44, alignSelf: 'flex-start', justifyContent: 'center', marginBottom: spacing[3] },
  pressed: { opacity: 0.72 },
  card: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  headerRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2] },
  grow: { flex: 1, gap: spacing[1] },
  visibilityBadge: { minHeight: 28, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  body: { lineHeight: 24 },
  images: { gap: spacing[2] },
  image: { width: '100%', aspectRatio: 4 / 3, borderRadius: radius.md, backgroundColor: color.surface.soft },
  notice: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: '#eee5da', borderRadius: radius.lg, backgroundColor: color.surface.card },
  recoveryButton: { marginTop: spacing[2] },
});
