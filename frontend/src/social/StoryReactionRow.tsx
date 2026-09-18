// 기록의 좋아요·싫어요 줄 —.
import { Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import type { StoryDto } from '@/social/stories';

export type Reaction = 'LIKE' | 'DISLIKE';

/** 반응 판정과 그리기에 필요한 최소 칸 — 목록 항목도 상세 응답도 이 모양을 만족한다. */
export type ReactableStory = Pick<StoryDto, 'myReaction' | 'likeCount' | 'dislikeCount'>;

/** 누른 결과가 무엇인가 — 같은 것을 다시 누르면 끄고(null), 다른 것을 누르면 바꾼다. */
export function nextReaction(was: Reaction | null | undefined, pressed: Reaction): Reaction | null {
  return (was ?? null) === pressed ? null : pressed;
}

/**
 * 서버가 세는 수를 낙관적으로 미리 맞춘다 — 누를 때마다 목록이나 상세를 다시 불러오면
 * 스크롤 위치가 튀고 응답을 기다리는 동안 버튼이 죽은 것처럼 보인다.
 */
export function applyReaction<T extends ReactableStory>(story: T, next: Reaction | null): T {
  const was = story.myReaction ?? null;
  let likeCount = story.likeCount ?? 0;
  let dislikeCount = story.dislikeCount ?? 0;
  if (was === 'LIKE') likeCount -= 1;
  if (was === 'DISLIKE') dislikeCount -= 1;
  if (next === 'LIKE') likeCount += 1;
  if (next === 'DISLIKE') dislikeCount += 1;
  return { ...story, myReaction: next, likeCount, dislikeCount };
}

export function StoryReactionRow({
  story,
  reacting,
  onReact,
  children,
}: {
  story: ReactableStory;
  /** 서버 응답을 기다리는 중인가 — 연타로 수가 어긋나는 것을 막는다. */
  reacting: boolean;
  onReact: (reaction: Reaction) => void;
  /** 같은 줄에 덧붙일 것(목록의 저장 버튼처럼). 스타일은 `storyReactionStyles.button` 을 쓴다. */
  children?: React.ReactNode;
}) {
  const { tx } = useI18n();
  const liked = story.myReaction === 'LIKE';
  const disliked = story.myReaction === 'DISLIKE';

  return (
    <View style={storyReactionStyles.row}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={liked ? tx('좋아요 취소', 'Remove like') : tx('좋아요', 'Like')}
        accessibilityState={{ selected: liked, busy: reacting }}
        disabled={reacting}
        onPress={() => onReact('LIKE')}
        style={[storyReactionStyles.button, reacting && storyReactionStyles.busy]}
      >
        <Text variant="caption" weight="bold" color={liked ? color.brand.orange : color.text.muted}>
          {'👍'}{typeof story.likeCount === 'number' ? ` ${story.likeCount}` : ''}
        </Text>
      </Pressable>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={disliked ? tx('싫어요 취소', 'Remove dislike') : tx('싫어요', 'Dislike')}
        accessibilityState={{ selected: disliked, busy: reacting }}
        disabled={reacting}
        onPress={() => onReact('DISLIKE')}
        style={[storyReactionStyles.button, reacting && storyReactionStyles.busy]}
      >
        <Text variant="caption" weight="bold" color={disliked ? color.brand.navy : color.text.muted}>
          {'👎'}{typeof story.dislikeCount === 'number' ? ` ${story.dislikeCount}` : ''}
        </Text>
      </Pressable>
      {children}
    </View>
  );
}

// 44 는 손가락이 닿는 최소 높이다(팀 UX 가이드라인). 덧붙이는 버튼도 같은 값을 쓰도록
// 내보낸다 — 한 줄 안에서 높이가 다르면 눌리는 영역이 들쭉날쭉해진다.
export const storyReactionStyles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[4], paddingHorizontal: spacing[4], paddingBottom: spacing[3], marginTop: -spacing[2] },
  button: { minHeight: 44, justifyContent: 'center' },
  busy: { opacity: 0.6 },
});
