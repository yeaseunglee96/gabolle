// 이 시험이 지키는 것은 「앱 안으로 들어갈 때 쌓인 화면을 치우고 간다」다 (S15P21E201-1242).
//
// `router.replace` 는 **맨 위 한 칸만** 바꾼다. 그래서 로그인·온보딩 화면 위에 홈을
// 얹어도 그 화면들이 아래에 그대로 남고, 홈에서 뒤로 가기를 누르면 다시 보인다.
// `enterApp` 은 `dismissAll` 로 걷어낸 뒤 `replace` 한다 — 그러려고 만든 함수다
// (S15P21E201-1199, src/auth/enterApp.ts).
//
// 🔴 그때는 로그인 경로만 고쳤다. 비회원 경로(「비회원으로 먼저 둘러보기」)와 온보딩의
//    「홈으로」 단추들은 맨 replace 인 채로 남았고, 2026-09-18 iOS 실기기 시험에서
//    「비회원이 장소 상세에서 뒤로 가면 로그인 화면이 뜬다」로 다시 올라왔다.
//
// 한 자리를 고치는 것으로는 다시 난다. 앱으로 들어가는 자리는 화면이 늘 때마다 같이
// 늘고, 빠뜨려도 오류가 안 나기 때문이다 — 뒤로 가 보기 전에는 아무 신호가 없다.
// 그래서 규칙을 기계가 지킨다 (signInReturnTo.test.ts 와 같은 방식).
//
// 🔴 **보는 곳을 진입 흐름으로 좁힌다.** 처음에는 app/ 전체를 봤더니 32곳이 걸렸는데,
//    거의 전부가 이런 모양이었다.
//
//        onPress={() => router.canGoBack() ? router.back() : router.replace('/home')}
//
//    이건 「뒤로 갈 곳이 없으면 홈으로」라는 대비책이고 정상이다. 그 화면들은 이미 앱
//    안이라 아래에 온보딩·로그인이 깔려 있지 않다. 거기까지 걸면 고칠 것이 없는 자리를
//    고치게 만들고, 그런 검사는 곧 무시당한다.
//
//    쌓인 화면이 남는 문제가 실제로 있는 곳은 **온보딩과 인증 흐름**뿐이다 — 거기서만
//    홈 아래에 지나온 화면들이 깔린다.
//
// 파일을 읽는 부분을 require 로 부르고 타입을 여기서 직접 적는 이유는 tsconfig 의
// types 가 ["jest"] 하나이기 때문이다. 검사 하나를 위해 거기에 node 를 더하면 모두의
// 타입 검사 범위가 바뀌므로, 이 파일 안에서만 좁게 선언한다.
declare const require: (id: string) => any;
declare const __dirname: string;

const { readdirSync, readFileSync, statSync, existsSync } = require('fs');
const { join, relative, sep } = require('path');

const APP_DIR: string = join(__dirname, '..', '..', '..', 'app');

/** 지나온 화면이 아래에 쌓이는 흐름. 여기서 앱으로 들어갈 때만 검사한다. */
const ENTRY_FLOWS: string[] = ['(onboarding)', '(auth)', 'auth'];

/** 탭 뿌리 — 여기로 바꿔치는 것은 「앱 안으로 들어간다」는 뜻이다. */
const ENTRY_TARGETS: string[] = ['/home', '/feed', '/trips', '/me'];

function screenFiles(dir: string): string[] {
  if (!existsSync(dir)) return [];
  return (readdirSync(dir) as string[]).flatMap((entry: string) => {
    const full: string = join(dir, entry);
    if (statSync(full).isDirectory()) return entry === '__tests__' ? [] : screenFiles(full);
    return entry.endsWith('.tsx') && !entry.endsWith('.test.tsx') ? [full] : [];
  });
}

/** `router.replace('/home')` 처럼 치우지 않고 탭 뿌리로 바꿔치는 형태. */
const BARE_ENTRY = new RegExp(
  'router\\.(?:replace|navigate)\\(\\s*[\'"`](?:' + ENTRY_TARGETS.join('|') + ')(?:[?#][^\'"`]*)?[\'"`]\\s*\\)',
);

/** 「뒤로 갈 곳이 없으면」 대비책은 앱으로 들어가는 것이 아니다. */
const BACK_FALLBACK = /canGoBack\(\)/;

describe('온보딩·인증에서 앱 안으로 들어갈 때', () => {
  it('쌓인 화면을 치우지 않고 탭 뿌리로 바꿔치지 않는다', () => {
    const offenders: string[] = [];
    for (const flow of ENTRY_FLOWS) {
      for (const file of screenFiles(join(APP_DIR, flow))) {
        const source: string = readFileSync(file, 'utf8');
        source.split('\n').forEach((line: string, index: number) => {
          if (BARE_ENTRY.test(line) && !BACK_FALLBACK.test(line)) {
            offenders.push(`${relative(APP_DIR, file).split(sep).join('/')}:${index + 1}`);
          }
        });
      }
    }
    expect(offenders).toEqual([]);
  });

  it('검사할 진입 흐름 파일을 실제로 찾는다', () => {
    // 폴더 이름이 바뀌면 위 시험이 조용히 0건을 보고 언제나 통과한다 — 그것을 막는다.
    const found: number = ENTRY_FLOWS.reduce(
      (total: number, flow: string) => total + screenFiles(join(APP_DIR, flow)).length,
      0,
    );
    expect(found).toBeGreaterThan(0);
  });
});
