// 「뒤로」 단추가 갈 곳이 없을 때 로그인 화면이 뜨던 것 — S15P21E201-1292.
//
// `router.back()` 은 **돌아갈 곳이 있을 때만** 쓸 수 있다. 딥링크·공유링크로 화면에
// 바로 들어오면 돌아갈 곳이 없고, 그때 `back()` 은 아무 데도 못 가서 로그인 화면이 떴다.
//
// 🔴 실기기(SM-G973N, 비회원)에서 같은 방식으로 두 화면을 눌러 확인했다.
//
//      /legal/open-source  (가드 없음)  → 로그인 화면
//      /festivals          (가드 있음)  → 홈
//
// 차이는 가드 하나뿐이었다. 그래서 규칙을 기계가 지킨다 — 뒤로 단추는 화면이 늘 때마다
// 같이 늘고, 빠뜨려도 오류가 안 난다(뒤로 가 보기 전에는 아무 신호가 없다).
//
// 지켜야 하는 모양은 둘 중 하나다.
//
//      onPress={() => router.canGoBack() ? router.back() : router.replace('/home')}
//      if (router.canGoBack()) router.back(); else router.replace('/trips');
declare const require: (id: string) => any;
declare const __dirname: string;

const { readdirSync, readFileSync } = require('fs');
const { join, relative, sep } = require('path');

const ROOT = join(__dirname, '..', '..');

function sourceFiles(dir: string): string[] {
  return (readdirSync(dir, { withFileTypes: true }) as any[]).flatMap((entry: any) => {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules' || entry.name === '__tests__') return [];
      return sourceFiles(full);
    }
    return entry.name.endsWith('.tsx') && !entry.name.includes('.test.') ? [full] : [];
  });
}

const NEWLINE = new RegExp(String.fromCharCode(92) + 'r?' + String.fromCharCode(92) + 'n');
const offenders: string[] = [];
const guarded: string[] = [];

for (const file of [...sourceFiles(join(ROOT, 'app')), ...sourceFiles(join(ROOT, 'src'))]) {
  const lines = (readFileSync(file, 'utf8') as string).split(NEWLINE);
  lines.forEach((line: string, index: number) => {
    if (!line.includes('router.back()')) return;
    const where = `${(relative(ROOT, file) as string).split(sep).join('/')}:${index + 1}`;
    if (line.includes('canGoBack()')) guarded.push(where);
    else offenders.push(where);
  });
}

describe('뒤로 가기는 갈 곳이 없을 때를 반드시 막는다', () => {
  it('🔴 canGoBack() 없이 router.back() 을 부르는 곳이 없다', () => {
    expect(offenders).toEqual([]);
  });

  it('🔴 이 검사가 실제로 파일을 읽고 있다 — 폴더가 바뀌어도 조용히 통과하지 않는다', () => {
    // 가드를 갖춘 자리가 한 움큼은 있어야 한다. 0 이면 아무것도 못 읽은 것이다.
    expect(guarded.length).toBeGreaterThan(20);
  });
});
