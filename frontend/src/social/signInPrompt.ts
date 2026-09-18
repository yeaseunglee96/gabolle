// 로그인 유도를 언제 띄우나 —.
export const SIGN_IN_PROMPT_AFTER_STORIES = 8;

/**
 * 닫은 뒤에도 "한 번 더 볼 때" 다시 뜨게 하되, 같은 자리에서 곧바로 다시 뜨지는 않게 한다
 * 닫자마자 또 뜨면 닫기가 안 먹는 것처럼 보이고, 그게 막는 창과 구분이 안 된다.
 */
export function shouldPromptSignIn(input: { signedIn: boolean; seenCount: number; lastPromptedAt: number }): boolean {
  if (input.signedIn) return false;
  return input.seenCount >= input.lastPromptedAt + SIGN_IN_PROMPT_AFTER_STORIES;
}
