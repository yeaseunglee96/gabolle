// 피드에서 글 쓸 입구를 폭에 따라 고른다 — S15P21E201-1142.
import { isAtLeast } from '@/layout/breakpoints';

/** 글쓰기 입구 — 맨 위 입력창이거나, 헤더 버튼이거나, (비회원이면) 없다. */
export type ComposeEntry = 'inline' | 'headerButton' | 'none';

export function composeEntryFor(width: number, signedIn: boolean): ComposeEntry {
  if (!signedIn) return 'none';
  // 좁은 화면에서 본문·사진·지역·공개시점을 한 카드에 넣으면 정작 보러 온 목록이
  // 한참 밀려 내려간다. 그래서 폰은 전체 화면으로 보낸다.
  return isAtLeast(width, 'md') ? 'inline' : 'headerButton';
}
