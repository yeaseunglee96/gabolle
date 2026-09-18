// 이 시험이 지키는 것은 「화면 안에서 로그인을 누르면, 로그인에 성공한 뒤 그 화면으로
// 돌아온다」다 (S15P21E201-1240).
//
// 돌아갈 곳은 resolveDestination 이 returnTo 로 정한다. 그래서 로그인으로 보내는 쪽이
// returnTo 를 안 넘기면 기본값 /home 으로 떨어지고, 사용자는 보던 것을 잃는다. 오류가
// 안 나고 로그인 자체는 성공하므로 코드만 읽어서는 잘 안 보인다 — 실제로 여섯 자리가
// 그렇게 빠져 있었고(피드 상세·홈 셋·환율·대중교통) 사용자 제보로 드러났다.
//
// 한 자리를 고치는 것으로는 다시 난다. 로그인 유도는 화면이 늘어날 때마다 같이 늘고,
// returnTo 를 빠뜨려도 아무 신호가 없기 때문이다. 그래서 규칙을 기계가 지킨다.
//
// 인증 흐름 안(`app/(auth)/`·`app/auth/`)은 제외한다. 그쪽의 「로그인으로 돌아가기」는
// 로그인 화면 자체가 목적지이고, 돌아갈 자리는 이미 pendingReturnTo 에 저장돼 있다.
//
// 파일을 읽는 부분을 require 로 부르고 타입을 여기서 직접 적는 이유는 tsconfig 의
// types 가 ["jest"] 하나이기 때문이다. 검사 하나를 위해 거기에 node 를 더하면 모두의
// 타입 검사 범위가 바뀌므로, 이 파일 안에서만 좁게 선언한다.
declare const require: (id: string) => any;
declare const __dirname: string;

const { readdirSync, readFileSync, statSync } = require('fs');
const { join, relative, sep } = require('path');

const APP_DIR: string = join(__dirname, '..', '..', '..', 'app');
const EXEMPT_PREFIXES: string[] = ['(auth)', 'auth'];

function screenFiles(dir: string): string[] {
  return (readdirSync(dir) as string[]).flatMap((entry: string) => {
    const full: string = join(dir, entry);
    if (statSync(full).isDirectory()) return entry === '__tests__' ? [] : screenFiles(full);
    return entry.endsWith('.tsx') && !entry.endsWith('.test.tsx') ? [full] : [];
  });
}

function isExempt(file: string): boolean {
  const rel: string = relative(APP_DIR, file);
  return EXEMPT_PREFIXES.some((prefix) => rel === prefix || rel.startsWith(prefix + sep));
}

/** `router.push('/sign-in')` 처럼 문자열 하나만 넘기는 형태 — returnTo 가 들어갈 자리가 없다. */
const BARE_SIGN_IN = /router\.(?:push|replace|navigate)\(\s*['"`]\/sign-(?:in|up)(?:[/?#][^'"`]*)?['"`]\s*\)/;

describe('로그인으로 보낼 때 돌아올 곳', () => {
  it('인증 흐름 밖의 화면은 returnTo 없이 로그인으로 보내지 않는다', () => {
    const offenders: string[] = [];

    for (const file of screenFiles(APP_DIR)) {
      if (isExempt(file)) continue;
      (readFileSync(file, 'utf8') as string).split('\n').forEach((line: string, index: number) => {
        if (BARE_SIGN_IN.test(line)) offenders.push(`${relative(APP_DIR, file)}:${index + 1}`);
      });
    }

    expect(offenders).toEqual([]);
  });

  it('검사가 실제로 무언가를 보고 있다', () => {
    // 위 시험이 조용히 0개 파일을 훑고 통과하는 것을 막는다.
    expect(screenFiles(APP_DIR).filter((file: string) => !isExempt(file)).length).toBeGreaterThan(20);
  });

  it('찾아내려는 모양을 정확히 집는다', () => {
    expect(BARE_SIGN_IN.test("router.push('/sign-in')")).toBe(true);
    expect(BARE_SIGN_IN.test('router.replace("/sign-in")')).toBe(true);
    expect(BARE_SIGN_IN.test("router.push({ pathname: '/sign-in', params: { returnTo: '/home' } })")).toBe(false);
    expect(BARE_SIGN_IN.test("router.push('/sign-in-help')")).toBe(false);
  });
});
