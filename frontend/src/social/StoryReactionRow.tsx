// 기록의 반응 줄 — 좋아요 · 인용 · 저장.
//
// 싫어요(👎)를 없앴다. 사람이 남긴 여행 기록에 싫어요를 다는 칸이 서비스의 성격과 맞지
// 않는다는 판단이고, 시안 design_handoff 의 screens/03·04 가 그 자리에 「인용 N」을 둔다.
//
// 🔴 화면에서 없앴을 뿐 서버에는 이미 눌린 싫어요가 남아 있다. 그래서 아래 수 계산은
//    DISLIKE 를 그대로 다룬다 — 안 그러면 예전에 싫어요를 누른 사람이 👍 를 누를 때
//    수가 어긋난다.
//
// 저장 버튼도 여기 있다 — 목록(feed.tsx)에만 있고 상세([id].tsx)에는 없던 것을 한 부품에
// 모았다. 두 화면이 같은 줄을 그리는데 한쪽에만 버튼을 두면 사람은 「상세에서는 저장이
// 안 되나」로 읽는다. `onToggleSave` 를 주면 그려지고, 안 주면 그 칸이 없다.
//
// 그림문자(👍 ☆)를 선(SVG) 아이콘으로 바꿨다 — 그림문자는 기기마다 모양과 굵기가 달라
// 어떤 폰에서는 회색 알약 안에서 흐릿했다. 켜진 상태는 글자색만 바꾸지 않고 알약을
// 짙은 회색으로 채운다(선택 = 짙은 회색, tokens.ts 규칙). 켜짐과 꺼짐이 한눈에 갈린다.
import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import Svg, { Path } from 'react-native-svg';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import type { StoryDto } from '@/social/stories';

export type Reaction = 'LIKE' | 'DISLIKE';

/** 반응 판정과 그리기에 필요한 최소 칸 — 목록 항목도 상세 응답도 이 모양을 만족한다. */
export type ReactableStory = Pick<StoryDto, 'myReaction' | 'likeCount' | 'dislikeCount' | 'linkCopyCount'> & Partial<Pick<StoryDto, 'mine'>>;

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
 * 알약은 36 으로 그리는데 손가락이 닿는 자리는 44 여야 한다(팀 UX 기준). 둘을 따로 둔다 —
 * 위아래로 4 씩 넓히면 36 + 8 = 44 다.
 *
 * 같은 줄에 버튼을 덧붙이는 화면도 이것을 써야 눌리는 영역이 들쭉날쭉하지 않다.
 * 웹에서는 무시되는데(마우스는 정확히 찍는다) 폰에서는 이게 없으면 자주 헛누른다.
 */
export const storyReactionTouchSlop = { top: 4, bottom: 4, left: 0, right: 0 };

const ICON = 16;
const STROKE = { strokeWidth: 1.9, strokeLinecap: 'round', strokeLinejoin: 'round' } as const;

/** 엄지 — 켜지면 채운다. */
function ThumbIcon({ tint, filled }: { tint: string; filled: boolean }) {
  return (
    <Svg width={ICON} height={ICON} viewBox="0 0 24 24" fill={filled ? tint : 'none'}>
      <Path d="M7 10v11H4a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1h3zm0 0l4.5-7.2a1.6 1.6 0 0 1 2.9 1l-1 4.2H19a2 2 0 0 1 2 2.3l-1.2 8A2 2 0 0 1 17.8 20H7" stroke={tint} {...STROKE} />
    </Svg>
  );
}

/** 책갈피 — 저장. 켜지면 채운다. */
function BookmarkIcon({ tint, filled }: { tint: string; filled: boolean }) {
  return (
    <Svg width={ICON} height={ICON} viewBox="0 0 24 24" fill={filled ? tint : 'none'}>
      <Path d="M6 4.5A1.5 1.5 0 0 1 7.5 3h9A1.5 1.5 0 0 1 18 4.5V21l-6-4-6 4V4.5z" stroke={tint} {...STROKE} />
    </Svg>
  );
}

/** 인용 아이콘 — 사슬 고리 둘. 시안의 인라인 SVG 를 그대로 옮겼다(24 격자, 굵기 2, 둥근 끝). */
function QuoteIcon({ tint }: { tint: string }) {
  return (
    // fill 을 안 주면 react-native-svg 가 검게 채운다 — 선만 있는 그림이라 none 이 필요하다.
    <Svg width={ICON} height={ICON} viewBox="0 0 24 24" fill="none">
      <Path d="M10 13a5 5 0 0 0 7.07 0l2.83-2.83a5 5 0 0 0-7.07-7.07L11.5 4.4" stroke={tint} {...STROKE} />
      <Path d="M14 11a5 5 0 0 0-7.07 0L4.1 13.83a5 5 0 0 0 7.07 7.07l1.3-1.3" stroke={tint} {...STROKE} />
    </Svg>
  );
}

export function StoryReactionRow({
  story,
  reacting,
  onReact,
  saved,
  saving = false,
  onToggleSave,
  onQuote,
  children,
  style,
}: {
  story: ReactableStory;
  /** 서버 응답을 기다리는 중인가 — 연타로 수가 어긋나는 것을 막는다. */
  reacting: boolean;
  onReact: (reaction: Reaction) => void;
  /** 내가 저장한 기록인가 — StoryDto 엔 없는 칸이라 화면이 따로 들고 온다(S15P21E201-1221). */
  saved?: boolean;
  /** 저장 요청을 기다리는 중인가. */
  saving?: boolean;
  /** 주면 저장 버튼이 생긴다. 안 주면 그 칸이 없다. */
  onToggleSave?: () => void;
  /**
   * 인용 = 이 기록의 링크를 복사해 남에게 보내는 것. 주면 인용 알약이 눌리고, 누르면 링크를 복사한다.
   * 🔴 전에는 수만 보여 주는 칸이라 눌러도 아무 일이 없었다 — 사용자가 「고장」으로 읽었다(2026-09-21).
   */
  onQuote?: () => void;
  /** 같은 줄에 덧붙일 것. 스타일은 `storyReactionStyles.button` 을 쓴다. */
  children?: React.ReactNode;
  /** 줄 여백을 덮어쓴다 — 목록 카드는 사진이 끝까지 차서 줄에 좌우 여백이 있는데, 여백 있는 카드 안에 넣으면 두 번 들여써진다. */
  style?: StyleProp<ViewStyle>;
}) {
  const { tx } = useI18n();
  const liked = story.myReaction === 'LIKE';
  // 🔴 내 글(함께 쓰는 글 포함)에는 좋아요를 못 단다 — 서버가 막는다(StoryReactionService). 전에는 단추가 눌리는데
  //    아무 일도 없어서 고장으로 보였다(S15P21E201-1766, Play 35 실기기). 잠그고 이유를 읽어 준다.
  const own = story.mine === true;
  const quotes = story.linkCopyCount;
  const likeLabel = tx('좋아요', 'Like');
  const onTint = color.text.onAction;
  const offTint = color.text.body;

  return (
    <View style={[storyReactionStyles.row, style]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={own ? tx('내 글에는 좋아요를 누를 수 없어요', "You can't like your own post") : liked ? tx('좋아요 취소', 'Remove like') : likeLabel}
        accessibilityState={{ selected: liked, busy: reacting, disabled: own }}
        disabled={reacting || own}
        hitSlop={storyReactionTouchSlop}
        onPress={() => onReact('LIKE')}
        style={[storyReactionStyles.button, liked && storyReactionStyles.buttonOn, (reacting || own) && storyReactionStyles.busy]}
      >
        <ThumbIcon tint={liked ? onTint : offTint} filled={liked} />
        <Text variant="util" weight="bold" color={liked ? onTint : offTint}>
          {typeof story.likeCount === 'number' ? `${likeLabel} ${story.likeCount}` : likeLabel}
        </Text>
      </Pressable>

      {/* 🔴 수가 안 오면 칸 자체를 안 그린다. 0 을 박아 넣지 않는다 —
          0 은 「아무도 안 했다」이고 안 오는 것은 「모른다」다. 둘은 다르다. */}
      {typeof quotes === 'number' ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={tx('링크 복사해서 인용하기', 'Copy link to quote')}
          accessibilityHint={onQuote ? undefined : tx('상세 화면에서 복사할 수 있어요', 'You can copy it on the detail screen')}
          disabled={!onQuote}
          hitSlop={storyReactionTouchSlop}
          onPress={onQuote}
          style={storyReactionStyles.button}
        >
          <QuoteIcon tint={offTint} />
          <Text variant="util" weight="bold" color={offTint}>
            {tx(`인용 ${quotes}`, quotes === 1 ? '1 quote' : `${quotes} quotes`)}
          </Text>
        </Pressable>
      ) : null}

      {onToggleSave ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={saved ? tx('저장 취소', 'Remove from saved') : tx('저장', 'Save')}
          accessibilityState={{ selected: Boolean(saved), busy: saving }}
          disabled={saving}
          hitSlop={storyReactionTouchSlop}
          onPress={onToggleSave}
          style={[storyReactionStyles.button, saved && storyReactionStyles.buttonOn, saving && storyReactionStyles.busy]}
        >
          <BookmarkIcon tint={saved ? onTint : offTint} filled={Boolean(saved)} />
          <Text variant="util" weight="bold" color={saved ? onTint : offTint}>
            {saved ? tx('저장됨', 'Saved') : tx('저장', 'Save')}
          </Text>
        </Pressable>
      ) : null}

      {children}
    </View>
  );
}

export const storyReactionStyles = StyleSheet.create({
  row: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: spacing[2], paddingHorizontal: spacing[4], paddingBottom: spacing[3], marginTop: -spacing[2] },
  // 알약 — 높이 36, 좌우 12, 완전한 둥근 모서리, 연한 바탕(시안 그대로).
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[1],
    minHeight: 36,
    paddingHorizontal: spacing[3],
    borderRadius: radius.full,
    backgroundColor: color.surface.soft,
    justifyContent: 'center',
  },
  // 켜짐 — 짙은 회색으로 채운다. 선택 상태에 빨강을 쓰지 않는다(tokens.ts).
  buttonOn: { backgroundColor: color.action.secondary },
  busy: { opacity: 0.6 },
});
