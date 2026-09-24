// 작성자 이름 뒤의 공동 작성자 — 「· 이예승」(S15P21E201-1583). 피드 카드와 기록 상세가 같이 쓴다.
// 누르면 참여자 화면(app/feed/[id]/coauthors.tsx)으로 간다.
//
// 🔴 작성자 이름(프로필로 가는 버튼) «안»에 넣지 않는다 — 옆에 따로 둔다. 버튼 안에 버튼을 두면 웹에서
//    <button> 이 <button> 을 품는 잘못된 HTML 이 되고, 어느 쪽이 눌릴지 기기마다 다르다.
import { Pressable, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';

import { Text } from '@/components/Text';
import { color } from '@/design/tokens';
import { useI18n } from '@/i18n';
import type { StoryDto } from '@/social/stories';

/** 이름은 이만큼까지 적고, 넘으면 「외 N명」. */
const SHOWN_NAMES = 2;

/**
 * 「이예승, 진미리 외 2명」 — 적을 사람이 없으면 null.
 * 🔴 탈퇴한 사람(displayName null)은 이름에서도 「외 N명」에서도 뺀다. 「(탈퇴한 사용자)」를 카드 머리에
 *    늘어놓지 않는다 — 누가 있는지는 참여자 화면이 말한다.
 */
export function coauthorNames(coauthors: StoryDto['coauthors'], tx: (ko: string, en: string) => string): string | null {
  const names = (coauthors ?? []).map((person) => person.displayName?.trim()).filter((name): name is string => Boolean(name));
  if (names.length === 0) return null;
  const rest = names.length - SHOWN_NAMES;
  const shown = names.slice(0, SHOWN_NAMES).join(', ');
  return rest > 0 ? `${shown} ${tx(`외 ${rest}명`, `+${rest} more`)}` : shown;
}

export function CoauthorByline({ story, large = false }: { story: StoryDto; large?: boolean }) {
  const router = useRouter();
  const { tx } = useI18n();
  const names = coauthorNames(story.coauthors, tx);
  if (!names) return null;
  return (
    <Pressable
      accessibilityRole="link"
      accessibilityLabel={`${tx('공동 작성자', 'Co-authors')} ${names}`}
      hitSlop={8}
      onPress={() => router.push(`/feed/${story.id}/coauthors`)}
      style={styles.byline}
    >
      <Text variant={large ? 'body' : 'caption'} weight="bold" color={color.text.body} numberOfLines={1}>· {names}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  // 자리가 모자라면 작성자 이름보다 더 많이 줄어든다 — 누가 쓴 글인지가 먼저다.
  byline: { flexShrink: 3, minWidth: 0 },
});
