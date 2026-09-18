import { shouldPromptSignIn, SIGN_IN_PROMPT_AFTER_STORIES } from '../signInPrompt';

// 로그인 유도 —. 처음부터 막지 않고 얼마쯤 보고 나서 권한다.
// 닫으면 계속 볼 수 있어야 하고, 한 번 더 보면 다시 뜬다.
const N = SIGN_IN_PROMPT_AFTER_STORIES;

describe('로그인 유도를 언제 띄우나', () => {
  it('로그인한 사람에게는 안 띄운다', () => {
    expect(shouldPromptSignIn({ signedIn: true, seenCount: N * 10, lastPromptedAt: 0 })).toBe(false);
  });

  it('기준보다 적게 봤으면 안 띄운다 — 처음부터 막지 않는다', () => {
    expect(shouldPromptSignIn({ signedIn: false, seenCount: N - 1, lastPromptedAt: 0 })).toBe(false);
  });

  it('기준을 넘으면 띄운다', () => {
    expect(shouldPromptSignIn({ signedIn: false, seenCount: N, lastPromptedAt: 0 })).toBe(true);
  });

  it('닫은 직후 같은 자리에서 다시 뜨지 않는다 — 닫기가 안 먹는 것처럼 보인다', () => {
    expect(shouldPromptSignIn({ signedIn: false, seenCount: N + 1, lastPromptedAt: N })).toBe(false);
  });

  it('닫은 뒤 한 번 더 보면 다시 뜬다', () => {
    expect(shouldPromptSignIn({ signedIn: false, seenCount: N * 2, lastPromptedAt: N })).toBe(true);
  });
});
