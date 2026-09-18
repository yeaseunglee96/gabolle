// 이 시험이 지키는 것은 「화면 안에서 로그인을 누르면, 로그인에 성공한 뒤 그 화면으로
// 돌아온다」다
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
