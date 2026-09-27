// 인증 화면에서 앱 안으로 들어가는 자리가 enterApp 을 거치는지 본다 (S15P21E201-1799).
//
// enterApp(src/auth/enterApp.ts)은 쌓인 화면을 dismissAll 로 치운 뒤 replace 한다.
// 맨 router.replace 는 맨 위 한 칸만 바꾸므로 로그인 화면이 홈 아래에 남고, 홈에서
// 뒤로 가면 다시 나온다. enterAppOnEntry.test.ts 는 '/home' 같은 **글자 그대로의**
// 주소만 잡아서, 아래처럼 주소를 함수로 계산하는 자리는 빠져 있었다.
//
// 파일 읽기를 require 로 하는 이유는 enterAppOnEntry.test.ts 와 같다 (tsconfig types 가 jest 뿐).
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');

const AUTH_DIR: string = __dirname + '/../../../app/(auth)/';

/** [파일, 앱 안으로 가는 주소 식] — 이 식은 enterApp 으로만 넘어가야 한다. */
const ENTRIES: Array<[string, string]> = [
  ['sign-in.tsx', 'guestDestination(returnTo, gated)'],
  ['sign-up.tsx', "isSafeReturnPath(returnTo) ? returnTo : '/home'"],
  ['oauth-link.tsx', 'await resolveDestination(params.returnTo)'],
  ['oauth-signup.tsx', 'await resolveDestination(params.returnTo)'],
];

describe('인증 화면의 비회원·소셜 가입 진입', () => {
  it.each(ENTRIES)('%s 는 %s 로 갈 때 enterApp 을 쓴다', (file: string, expr: string) => {
    const source: string = readFileSync(AUTH_DIR + file, 'utf8');
    expect(source).toContain(expr);
    const lines: string[] = source.split('\n').filter((line: string) => line.includes(expr));
    expect(lines.length).toBeGreaterThan(0);
    for (const line of lines) {
      expect(line).toContain('enterApp(router,');
      expect(line).not.toMatch(/router\.replace\(/);
    }
  });
});
