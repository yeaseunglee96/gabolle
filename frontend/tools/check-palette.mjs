// 색을 화면에 직접 쓰지 않았는가 — S15P21E201-1343.
//
// 🔴 왜 검사로 두는가
//
// 이 저장소는 색을 `src/design/tokens.ts` 한 곳에서만 읽기로 했다. 그 규칙을 지켜 온
// 덕분에 배색 전환이 **값 한 벌 갈아 끼우기**로 끝났다 — 화면 126개를 안 건드렸다.
//
// 그런데 전부 그런 것은 아니었다. 실측(2026-09-19): 화면에 직접 박힌 색이 **19곳**
// 남아 있었고, 그 자리들만 **옛 배색 그대로 남았다.** 토글의 꺼짐 색, 홈 시작줄의 비활성
// 버튼, 티켓 프린터의 몸통 — 전부 회색 바탕 위에 베이지·카키로 떠 있었다.
//
// 🔴 **이것은 눈으로 안 잡힌다.** 화면은 멀쩡히 그려지고, 색 하나가 조금 다를 뿐이다.
//    한국어로 보면 끝까지 멀쩡한 번역 누락과 같은 종류다 — 아무도 오류로 안 읽는다.
//
// 보는 것이 둘이다.
//   ① 화면 파일에 박힌 색값(#rrggbb)
//   ② 값이 바뀌었는데 이름이 그대로인 토큰 — 아래 LEGACY
//   ③ 이름은 맞는데 자리가 틀린 것 — 아래 MISUSE
//
// 사용법: node tools/check-palette.mjs   (종료 코드 0 통과 · 1 남은 자리 있음)
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = join(decodeURIComponent(new URL('.', import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1'), '..');

/**
 * 🔴 여기 적힌 것은 **색 토큰이 될 수 없어서** 봐주는 자리다. 「고치기 귀찮아서」가 아니다.
 *    새로 넣을 때는 왜 될 수 없는지를 함께 적는다.
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

/**
 * 🔴 값이 바뀌었는데 이름이 그대로인 자리.
 *
 * `brand.orange` 는 옛 배색에서 **자유롭게 쓰는 강조색**이었다. 새 배색에서 그 값은
 * **동백 빨강**이 됐고, 규칙은 「동백 채움은 화면당 하나」다. 예전처럼 두면 한 화면에
 * 빨강이 여섯 개가 된다 — 사람은 어느 것이 「그다음에 할 일」인지 못 고른다.
 *
 * 🔴 **값만 갈아 끼우면 화면은 멀쩡히 그려지고 규칙만 조용히 깨진다.** 그래서 쓰는
 *    자리마다 무엇을 뜻했는지 다시 고르게 한다.
 */
const LEGACY = [
  {
    pattern: /color\.brand\.orange/,
    name: 'color.brand.orange',
    why: '이 이름의 값이 동백 빨강이 됐습니다. 예전의 「강조색」 자리에 그대로 두면 한 화면에 빨강이 여러 개가 됩니다.',
    pick: [
      'action.primary    그 화면의 주 버튼 하나. 둘째부터는 아래 것을 쓰세요',
      'action.outline    붉은 선 버튼 — 큰 면적이 부담스러울 때',
      'action.secondary  선택 상태·보조 행동(짙은 회색). 🔴 선택에는 빨강을 쓰지 않습니다',
      'state.dot         점·램프처럼 글자가 없는 표시',
      'state.danger      경고·삭제 글자',
      'text.eyebrow      눈썹 문구(회색). 🔴 눈썹은 빨갛지 않습니다',
      'text.muted        그냥 보조 글자였다면',
    ],
  },
];

/**
 * 🔴 이름은 맞는데 **자리가 틀린** 것.
 *
 * 시안이 「자주 틀리는 것」으로 따로 적어 둔 항목이다. 이름을 고르는 것과 달리 이쪽은
 * **화면을 봐도 안 틀려 보인다** — 색은 시안에 있는 색이고, 다만 있으면 안 되는 자리에 있다.
 */
const MISUSE = [
  {
    pattern: /color=\{color\.state\.dot\}/,
    name: '글자 색으로 쓴 state.dot',
    why: 'state.dot(#F25454)은 글자가 없는 표시 전용입니다 — 램프·동그라미·탭 밑의 점. 글자와 채움에는 동백 빨강(#D83A48)을 씁니다. 두 빨강이 한 화면에 섞이면 둘 중 하나가 바랜 것처럼 보입니다.',
    pick: [
      'text.muted       글머리·구분점처럼 읽기를 돕는 글자라면',
      'action.primary   눌러야 할 것을 가리키는 글자라면',
      'state.danger     경고·삭제라면',
      '점을 그대로 두려면 글자를 빼고 View 로 그리세요',
    ],
  },
];

/**
 * 🔴 **동백 채움은 화면당 하나.** 시안이 「자주 틀리는 것」의 첫 줄로 꼽은 규칙이다.
 *
 * 앞의 두 검사는 **색을 토큰으로 썼는가**만 본다. 그런데 전부 토큰으로 써도 한 화면에
 * 빨강 채움이 셋이면 규칙은 깨진다 — 그리고 그때 **검사는 초록이다.** 화면도 멀쩡히
 * 그려진다. 다만 사람이 「그다음에 할 일」을 못 고른다.
 *
 * 그래서 세는 것을 사람에게 맡기지 않는다. 사람은 화면 하나를 고칠 때 그 화면만 보고,
 * 빨강이 둘이 되는 것은 **두 번째 사람이 다른 날 한 줄 더할 때** 생긴다.
 *
 * 🔴 **이 검사가 못 잡는 것** (검사를 만들 때 함께 적는다 — 초록인데 아무것도 안 막는
 *    검사가 제일 위험하다):
 *
 *    ① **파일 단위로 센다.** 화면 하나가 화면 파일 + 부품 파일로 나뉘어 각각 하나씩
 *       가지고 있으면, 실제 화면에는 둘인데 여기서는 통과한다
 *    ② **서로 배타적인 두 갈래**(A 상태면 이 버튼, B 상태면 저 버튼)도 둘로 센다.
 *       그건 진짜로 화면에 하나뿐이므로, 아래 ALLOWED_MULTI_FILL 에 이유를 적는다
 *    ③ `backgroundColor:` 와 값이 **줄이 나뉘어 있으면** 못 잡는다
 *
 *    ①과 ③은 사람이 화면을 봐야 한다. 이 검사는 사람을 대신하지 않고, 사람이 놓치는
 *    종류만 맡는다.
 */
const FILL = /backgroundColor:\s*color\.action\.primary\b/;

/** 🔴 화면에는 하나뿐인데 파일에는 둘로 보이는 자리. 왜 그런지를 함께 적는다. */
const ALLOWED_MULTI_FILL = {
  // 예: 'app/x.tsx': '로그인 전/후로 갈리는 같은 버튼이라 화면에는 하나만 그려진다',
};

/** 색으로 안 보는 것 — 투명도만 얹은 흰검. */
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
const legacy = [];
const fills = new Map();

for (const file of [...sources(join(ROOT, 'app')), ...sources(join(ROOT, 'src'))]) {
  const rel = file.slice(ROOT.length + 1).replace(/\\/g, '/');
  const skipHex = ALLOWED.includes(rel);
  readFileSync(file, 'utf8').split('\n').forEach((line, index) => {
    // 🔴 주석은 뺀다. 「전에는 이 색이었다」고 적어 둔 기록까지 잡으면, 왜 바꿨는지를
    //    적지 못하게 된다 — 검사가 기록을 막는 꼴이다.
    const code = line.replace(/\/\/.*$/, '').replace(/\/\*.*?\*\//g, '');
    const where = `${rel}:${index + 1}`;
    if (!skipHex && !IGNORED.test(code)) {
      const hit = code.match(/#[0-9a-fA-F]{3,8}\b/);
      if (hit) offenders.push(`${where}  ${hit[0]}  ${line.trim().slice(0, 70)}`);
    }
    for (const rule of [...LEGACY, ...MISUSE]) {
      if (rule.pattern.test(code)) legacy.push({ rule, where, line: line.trim().slice(0, 78) });
    }
    // 🔴 한 줄에 여러 번 나온다. 이 저장소의 StyleSheet.create 는 통째로 한 줄인 곳이
    //    많아서, 줄 단위로 있나 없나만 보면 셋을 하나로 센다.
    const hits = code.match(new RegExp(FILL.source, 'g'));
    if (hits) {
      const found = fills.get(rel) ?? [];
      for (const _ of hits) found.push(where);
      fills.set(rel, found);
    }
  });
}

let failed = false;

if (offenders.length) {
  failed = true;
  console.error(`\n🔴 화면에 색이 직접 박혀 있습니다 — ${offenders.length}곳.\n`);
  for (const o of offenders) console.error('   ' + o);
  console.error('\n   이 자리들은 tokens.ts 를 갈아 끼워도 안 따라옵니다. 배색을 바꾸면 여기만');
  console.error('   옛 색으로 남고, 화면은 멀쩡해 보여서 아무도 못 찾습니다.');
  console.error('   토큰으로 바꾸거나, 될 수 없는 색이면 이 파일의 ALLOWED 에 이유와 함께 적으세요.');
}

for (const rule of [...LEGACY, ...MISUSE]) {
  const hits = legacy.filter((item) => item.rule === rule);
  if (!hits.length) continue;
  failed = true;
  const files = new Set(hits.map((h) => h.where.split(':')[0]));
  console.error(`\n🔴 ${rule.name} 를 아직 ${hits.length}곳에서 씁니다 (파일 ${files.size}개).\n`);
  console.error('   ' + rule.why);
  console.error('\n   무엇을 뜻했는지 골라 바꾸세요.');
  for (const choice of rule.pick) console.error('     · ' + choice);
  console.error('');
  for (const hit of hits) console.error(`   ${hit.where}  ${hit.line}`);
  if (LEGACY.includes(rule)) console.error('\n   다 고른 뒤에는 tokens.ts 에서 그 이름을 지우세요 — 남겨 두면 다음 사람이 또 씁니다.');
}

const crowded = [...fills].filter(([rel, at]) => at.length > 1 && !ALLOWED_MULTI_FILL[rel]);

if (crowded.length) {
  failed = true;
  console.error(`\n🔴 동백 채움이 한 파일에 둘 이상입니다 — 파일 ${crowded.length}개.\n`);
  console.error('   동백(action.primary) 채움은 **그 화면에서 그다음에 할 일** 하나를 가리킵니다.');
  console.error('   둘이 되는 순간 둘 다 그 뜻을 잃습니다 — 화면은 멀쩡하고 색도 전부 토큰이라,');
  console.error('   이건 눈으로도 앞의 검사로도 안 잡힙니다.');
  console.error('\n   둘째부터 이 중에서 고르세요.');
  console.error('     · action.outline    붉은 1.5px 선 + 붉은 글자. 큰 면적이 부담스러울 때');
  console.error('     · action.secondary  짙은 회색 채움. 보조 행동·고른 것');
  console.error('     · action.tertiary   연회색 채움. 「다음에 하기」류');
  console.error('     · action.field      현장 기능 전폭 버튼(짙은 회색). 🔴 동백이 아닙니다');
  console.error('');
  for (const [rel, at] of crowded) console.error(`   ${rel}  ${at.length}곳  →  ${at.map((w) => w.split(':')[1]).join(', ')}번째 줄`);
  console.error('\n   화면에는 하나뿐인데 파일에 둘로 보이는 자리라면(서로 배타적인 두 갈래),');
  console.error('   이 파일의 ALLOWED_MULTI_FILL 에 **왜 그런지와 함께** 적으세요.');
}

if (failed) {
  console.error('');
  process.exit(1);
}

console.log('배색 검사 통과 — 화면에 직접 박힌 색도, 옛 이름도, 겹친 동백 채움도 없습니다.');
