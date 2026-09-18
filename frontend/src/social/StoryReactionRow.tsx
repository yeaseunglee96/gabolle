// 기록의 반응 줄 — 👍 · 인용 · (저장).
//
// 싫어요(👎)를 없앴다. 사람이 남긴 여행 기록에 싫어요를 다는 칸이 서비스의 성격과 맞지
// 않는다는 판단이고, 시안 design_handoff 의 screens/03·04 가 그 자리에 「인용 N」을 둔다.
//
// 🔴 화면에서 없앴을 뿐 서버에는 이미 눌린 싫어요가 남아 있다. 그래서 아래 수 계산은
//    DISLIKE 를 그대로 다룬다 — 안 그러면 예전에 싫어요를 누른 사람이 👍 를 누를 때
//    수가 어긋난다.
import { Pressable, StyleSheet, View } from 'react-native';
import Svg, { Path } from 'react-native-svg';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import type { StoryDto } from '@/social/stories';

export type Reaction = 'LIKE' | 'DISLIKE';

/** 반응 판정과 그리기에 필요한 최소 칸 — 목록 항목도 상세 응답도 이 모양을 만족한다. */
export type ReactableStory = Pick<StoryDto, 'myReaction' | 'likeCount' | 'dislikeCount' | 'linkCopyCount'>;

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

/**
 * 알약은 32 로 그리는데 손가락이 닿는 자리는 44 여야 한다(팀 UX 기준). 둘을 따로 둔다 —
 * 위아래로 6 씩 넓히면 32 + 12 = 44 다.
 *
 * 같은 줄에 버튼을 덧붙이는 화면도 이것을 써야 눌리는 영역이 들쭉날쭉하지 않다.
 * 웹에서는 무시되는데(마우스는 정확히 찍는다) 폰에서는 이게 없으면 자주 헛누른다.
 */
export const storyReactionTouchSlop = { top: 6, bottom: 6, left: 0, right: 0 };

/** 인용 아이콘 — 사슬 고리 둘. 시안의 인라인 SVG 를 그대로 옮겼다(24 격자, 굵기 2, 둥근 끝). */
function QuoteIcon({ tint }: { tint: string }) {
  return (
    // fill 을 안 주면 react-native-svg 가 검게 채운다 — 선만 있는 그림이라 none 이 필요하다.
    <Svg width={14} height={14} viewBox="0 0 24 24" fill="none">
      <Path
        d="M10 13a5 5 0 0 0 7.07 0l2.83-2.83a5 5 0 0 0-7.07-7.07L11.5 4.4"
        stroke={tint}
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <Path
        d="M14 11a5 5 0 0 0-7.07 0L4.1 13.83a5 5 0 0 0 7.07 7.07l1.3-1.3"
        stroke={tint}
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Svg>
  );
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
  const quotes = story.linkCopyCount;

  return (
    <View style={storyReactionStyles.row}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={liked ? tx('좋아요 취소', 'Remove like') : tx('좋아요', 'Like')}
        accessibilityState={{ selected: liked, busy: reacting }}
        disabled={reacting}
        hitSlop={storyReactionTouchSlop}
        onPress={() => onReact('LIKE')}
        style={[storyReactionStyles.button, reacting && storyReactionStyles.busy]}
      >
        <Text variant="caption" weight="bold" color={liked ? color.brand.orange : color.text.muted}>
          {'👍'}{typeof story.likeCount === 'number' ? ` ${story.likeCount}` : ''}
        </Text>
      </Pressable>

      {/* 🔴 수가 안 오면 칸 자체를 안 그린다. 0 을 박아 넣지 않는다 —
          0 은 「아무도 안 했다」이고 안 오는 것은 「모른다」다. 둘은 다르다. */}
      {typeof quotes === 'number' ? (
        <View style={storyReactionStyles.button}>
          <QuoteIcon tint={color.text.muted} />
          <Text variant="caption" weight="bold" color={color.text.muted}>
            {tx(`인용 ${quotes}`, `${quotes} quotes`)}
          </Text>
        </View>
      ) : null}

      {children}
    </View>
  );
}

export const storyReactionStyles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[1], paddingHorizontal: spacing[4], paddingBottom: spacing[3], marginTop: -spacing[2] },
  // 알약 — 높이 32, 좌우 12, 완전한 둥근 모서리, 연한 바탕(시안 그대로).
  // 저장 버튼도 이 스타일을 받아 같은 모양이 된다.
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[1],
    minHeight: 32,
    paddingHorizontal: spacing[3],
    borderRadius: radius.full,
    backgroundColor: color.surface.soft,
    justifyContent: 'center',
  },
  busy: { opacity: 0.6 },
});
