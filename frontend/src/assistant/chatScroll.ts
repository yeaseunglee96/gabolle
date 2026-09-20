// 대화 목록을 언제 맨 아래로 끌어내릴 것인가 — S15P21E201-1349.
//
// 🔴 **답이 와도 화면이 안 내려갔다.** 실기(2026-09-20, versionCode 23)에서 조수에게
//    무언가를 물으면 사용자가 보는 것은 잘린 한 문장뿐이었고, 그 아래의
//    「일정에 적용하고 확인하기」 단추는 화면 밖에 통째로 숨어 있었다. 조수가 하는 일의
//    결론이 그 단추 하나인데 손으로 내려야만 보였다.
//
// 🔴 **그렇다고 언제나 끌어내리면 안 된다.** 사용자가 위로 올려 지난 말을 읽고 있는데
//    새 말풍선이 붙을 때마다 바닥으로 끌려가면 읽던 자리를 잃는다. 그래서 규칙은
//    「**바닥 근처에 있었으면** 따라 내려간다」다. 내가 방금 보낸 것에 대한 답은 예외로
//    무조건 따라간다 — 그건 부르는 쪽(chat.tsx 의 send)이 정한다.

/** 이 거리(px) 안쪽이면 「바닥에 붙어 있다」로 본다. 한 줄 높이보다 넉넉하게 둔다. */
export const STICK_TO_END_SLACK_PX = 96;

/** `ScrollView` 의 `onScroll` 이 주는 것 가운데 이 판단에 필요한 것만. */
export type ScrollGeometry = {
  layoutMeasurement: { height: number };
  contentOffset: { y: number };
  contentSize: { height: number };
};

/**
 * 지금 목록이 바닥 근처인가.
 *
 * <p>내용이 화면보다 짧으면(스크롤할 것이 없으면) 언제나 참이다 — 그때는 「올려 둔 상태」
 * 라는 것이 없기 때문이다. 음수 여백이 나오는 것은 튕김(bounce) 중이라 그때도 참이다.
 */
export function isNearBottom(geometry: ScrollGeometry, slack: number = STICK_TO_END_SLACK_PX): boolean {
  const { layoutMeasurement, contentOffset, contentSize } = geometry;
  const remaining = contentSize.height - contentOffset.y - layoutMeasurement.height;
  return remaining <= slack;
}
