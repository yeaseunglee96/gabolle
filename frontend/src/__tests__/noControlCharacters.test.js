// 소스에 보이지 않는 제어문자가 섞이는 것을 막는다.
//
// 왜 필요한가. 주석 정리가 티켓 번호를 걷어내면서 그 자리에 0x01 을 남겼다 — 107개 파일
// 254군데. 정리 직전 커밋에는 0군데였다. 그대로 배포까지 갔다.
//
// 그 작업의 안전장치는 "주석을 걷어낸 뒤의 코드가 이전과 같은지" 였는데, 깨진 것이 코드가
// 아니라 주석 자체라 원리상 통과한다. 컴파일러도 안 잡는다 — 주석 안의 제어문자는 문법
// 오류가 아니다. 사람 눈에도 폭 0으로 그려진다. 빌드는 내내 초록이었다.
//
// 그래서 "코드가 같은가" 가 아니라 "글자가 성한가" 를 따로 본다.
//
// NUL 이 섞이면 git 이 그 파일을 이진 파일로 보고 diff 를 아예 안 보여준다. 리뷰에서
// 그 파일의 변경은 아무도 못 읽는다는 뜻이다 — collectionsApi.ts 가 실제로 그랬다.
//
// 타입스크립트가 아니라 평범한 자바스크립트인 이유: 이 저장소의 tsconfig 는
// types 를 ["jest"] 로 묶어 node 타입을 일부러 안 넣는다. fs 를 쓰려고 그 설정을
// 건드리면 저장소 전체의 타입 판정이 같이 움직인다. tools/check-*.mjs 와 같은 결이다.

const { readdirSync, readFileSync, statSync } = require('fs');
const { join, relative } = require('path');

const ROOT = join(__dirname, '..', '..');

// 훑을 곳. 그림·글꼴과 받아 온 꾸러미는 소스가 아니라서 뺀다.
const SCAN = ['app', 'src', 'tools', 'plugins'];
const SKIP_DIRS = new Set(['node_modules', '.expo', '.expo-shared', 'assets', 'dist', 'build']);
const SOURCE = /\.(ts|tsx|js|jsx|json|md)$/;

// 탭(9)·줄바꿈(10)·복귀(13) 말고는 소스에 나올 이유가 없다.
const ALLOWED = new Set([9, 10, 13]);
const isForbidden = (code) => (code < 32 || code === 127) && !ALLOWED.has(code);

function walk(dir, out = []) {
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return out; // 없는 폴더는 건너뛴다 — 폴더가 늘고 주는 것을 검사가 막지 않는다
  }
  for (const name of entries) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) {
      if (!SKIP_DIRS.has(name)) walk(full, out);
    } else if (SOURCE.test(name)) {
      out.push(full);
    }
  }
  return out;
}

describe('소스에 보이지 않는 제어문자가 없다', () => {
  it('훑을 파일이 실제로 있다', () => {
    // 이 줄이 없으면 경로가 어긋나 0개를 훑고도 초록이 된다 — 아무것도 안 막는 검사가 된다.
    expect(walk(join(ROOT, 'src')).length).toBeGreaterThan(50);
  });

  it.each(SCAN)('%s/ 아래', (folder) => {
    const found = [];

    for (const file of walk(join(ROOT, folder))) {
      readFileSync(file, 'utf8')
        .split('\n')
        .forEach((line, i) => {
          for (let col = 0; col < line.length; col += 1) {
            const code = line.charCodeAt(col);
            if (!isForbidden(code)) continue;

            const hex = code.toString(16).toUpperCase().padStart(2, '0');
            const around = `${line.slice(Math.max(0, col - 30), col)}<여기>${line.slice(col + 1, col + 30)}`;
            found.push(`${relative(ROOT, file)}:${i + 1}:${col + 1}  0x${hex}  ${around.trim()}`);
          }
        });
    }

    expect(found).toEqual([]);
  });
});
