// 색을 화면에 직접 쓰지 않았는가 — S15P21E201-1343.
//
// 🔴 왜 검사로 두는가
//
// 이 저장소는 색을 `src/design/tokens.ts` 한 곳에서만 읽기로 했다. 그 규칙을 지켜 온
// 덕분에 배색 전환이 **값 한 벌 갈아 끼우기**로 끝났다 — 화면 126개를 안 건드렸다.
//
// 그런데 새 다 그런 것은 아니었다. 실측(2026-09-19): 화면에 직접 박힌 색이 **20곳**
// 남아 있었고, 그 자리들만 **옛 배색 그대로 남았다.** 토글의 꺼짐 색, 홈 시작줄의 비활성
// 버튼, 티켓 프린터의 몸통 — 전부 회색 바탕 위에 베이지·카키로 떠 있었다.
//
// 🔴 **이것은 눈으로 안 잡힌다.** 화면은 멀쩡히 그려지고, 색 하나가 조금 다를 뿐이다.
//    한국어로 보면 끝까지 멀쩡한 번역 누락과 같은 종류다 — 아무도 오류로 안 읽는다.
//
// 사용법: node tools/check-palette.mjs
//
// 종료 코드 0 이면 통과, 1 이면 새로 박힌 색이 있다.
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = join(decodeURIComponent(new URL('.', import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1'), '..');

/**
 * 🔴 여기 적힌 것은 **색 토큰이 아니어서** 봐주는 자리다. 「고치기 귀찮아서」가 아니다.
 *    새로 넣을 때는 왜 토큰이 될 수 없는지를 함께 적는다.
 */
const ALLOWED = [
  // 토큰 원본. 여기가 색의 유일한 출처다.
  'src/design/tokens.ts',
  // 소셜 로그인 버튼 — 각 회사가 정한 브랜드색이다. 우리 배색으로 바꾸면 심사에서 걸린다.
  'app/(auth)/sign-in.tsx',
  'src/components/SocialProviderIcon.tsx',
  'src/me/panels/IdentitiesBody.tsx',
  // 카카오 지도에 넘기는 HTML — 우리 스타일시트가 안 닿는 남의 문서다.
  'src/map/kakaoMapHtml.ts',
  // 사진 없는 자리에 그리는 바다 그림. 색이 아니라 **그림의 일부**다.
  'src/components/PlaceVisual.tsx',
];

/** 색으로 안 보는 것 — 투명도만 얹은 흰검, 그림자, 그러데이션 정지점. */
const IGNORED = /rgba?\(\s*(0|255)\s*,\s*(0|255)\s*,\s*(0|255)\s*,/;

function sources(dir, found = []) {
  for (const name of readdirSync(dir)) {
    if (name === 'node_modules' || name === '__tests__') continue;
    const full = join(dir, name);
    if (statSync(full).isDirectory()) sources(full, found);
    else if (/\.(ts|tsx)$/.test(name) && !/\.(test|spec)\./.test(name)) found.push(full);
  }
  return found;
}

const offenders = [];
for (const file of [...sources(join(ROOT, 'app')), ...sources(join(ROOT, 'src'))]) {
  const rel = file.slice(ROOT.length + 1).replace(/\\/g, '/');
  if (ALLOWED.includes(rel)) continue;
  const lines = readFileSync(file, 'utf8').split('\n');
  lines.forEach((line, index) => {
    // 🔴 주석은 뺀다. 「전에는 이 색이었다」고 적어 둔 기록까지 잡으면, 왜 바꿨는지를
    //    적지 못하게 된다 — 검사가 기록을 막는 꼴이다.
    const code = line.replace(/\/\/.*$/, '').replace(/\/\*.*?\*\//g, '');
    if (IGNORED.test(code)) return;
    const hit = code.match(/#[0-9a-fA-F]{3,8}\b/);
    if (hit) offenders.push(`${rel}:${index + 1}  ${hit[0]}  ${line.trim().slice(0, 70)}`);
  });
}

if (offenders.length) {
  console.error(`\n🔴 화면에 색이 직접 박혀 있습니다 — ${offenders.length}곳.\n`);
  for (const o of offenders) console.error('   ' + o);
  console.error('\n이 자리들은 src/design/tokens.ts 를 갈아 끼워도 **안 따라옵니다.**');
  console.error('배색을 바꾸면 여기만 옛 색으로 남고, 화면은 멀쩡해 보여서 아무도 못 찾습니다.');
  console.error('\n토큰으로 바꾸세요. 토큰이 될 수 없는 색이라면 이 파일의 ALLOWED 에');
  console.error('**왜 될 수 없는지와 함께** 적으세요.\n');
  process.exit(1);
}

console.log('배색 검사 통과 — 화면에 직접 박힌 색이 없습니다.');
