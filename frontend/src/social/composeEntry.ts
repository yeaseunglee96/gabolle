// 피드에서 글 쓸 입구를 폭에 따라 고른다 — S15P21E201-1142.
//
// 🔴 이 파일이 있는 이유는 **입구가 통째로 사라진 적이 있어서**다.
//
// 입구가 둘인데 서로 다른 경계값을 쓰고 있었다 — 헤더의 「기록」 버튼은 1024 미만,
// 맨 위 인라인 글쓰기는 1440 이상. **그 사이 1024~1439 에는 아무것도 없었다.**
// 흔한 데스크톱 창 폭이 통째로 비어 있었고, 목록이 비었을 때만 빈 화면 안내에 버튼이
// 있어서 **글이 하나라도 쌓이면 입구가 사라졌다.**
//
// 조건을 두 곳에 나눠 적으면 그 사이가 비어도 아무도 모른다. 여기 한 곳에서 고르고,
// 아래 시험이 «모든 폭에 입구가 하나는 있다» 를 지킨다.
import { isAtLeast } from '@/layout/breakpoints';

/** 글쓰기 입구 — 맨 위 입력창이거나, 헤더 버튼이거나, (비회원이면) 없다. */
export type ComposeEntry = 'inline' | 'headerButton' | 'none';

export function composeEntryFor(width: number, signedIn: boolean): ComposeEntry {
  if (!signedIn) return 'none';
  // 좁은 화면에서 본문·사진·지역·공개시점을 한 카드에 넣으면 정작 보러 온 목록이
  // 한참 밀려 내려간다. 그래서 폰은 전체 화면으로 보낸다.
  return isAtLeast(width, 'md') ? 'inline' : 'headerButton';
}
