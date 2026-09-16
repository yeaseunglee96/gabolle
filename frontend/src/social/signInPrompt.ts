// 로그인 유도를 언제 띄우나 — S15P21E201-1012.
//
// 🔴 기준 개수는 여기 한 자리에서만 바꾼다. 화면 안에 숫자를 흩어 두면 "얼마쯤 보면 뜨나"
// 를 바꿀 때 어디를 고쳐야 하는지 아무도 모르게 된다.
export const SIGN_IN_PROMPT_AFTER_STORIES = 8;

/**
 * 닫은 뒤에도 "한 번 더 볼 때" 다시 뜨게 하되, 같은 자리에서 곧바로 다시 뜨지는 않게 한다 —
 * 닫자마자 또 뜨면 닫기가 안 먹는 것처럼 보이고, 그게 막는 창과 구분이 안 된다.
 *
 * @param seenCount 지금까지 목록에 불러온 기록 수
 * @param lastPromptedAt 마지막으로 띄웠을 때의 seenCount (한 번도 안 띄웠으면 0)
 */
export function shouldPromptSignIn(input: { signedIn: boolean; seenCount: number; lastPromptedAt: number }): boolean {
  if (input.signedIn) return false;
  return input.seenCount >= input.lastPromptedAt + SIGN_IN_PROMPT_AFTER_STORIES;
}
