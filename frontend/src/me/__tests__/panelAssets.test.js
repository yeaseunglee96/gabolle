// 패널이 가리키는 그림이 실제로 있는가.
//
// 🔴 화면 파일을 panels/ 로 옮기면 `require('../assets/…')` 같은 상대 경로가 어긋난다.
//    **타입 검사도 시험도 안 잡는다** — require 안의 글자는 타입이 안 보고, 그 패널을
//    그리는 시험이 없으면 아무도 안 부른다. 묶는 단계에서만 500 으로 터진다.
//
//    실제로 두 번 겪었다: bell.png(시험이 우연히 잡음) · dongbaek-thinking.png(아무도 못 잡음).
//
// 타입스크립트가 아니라 평범한 자바스크립트인 이유는 이 저장소의 tsconfig 가 node 타입을
// 일부러 안 넣기 때문이다 — noControlCharacters 와 같다.
const { existsSync, readdirSync, readFileSync } = require('fs');
const { dirname, join, resolve } = require('path');

const PANELS = join(__dirname, '..', 'panels');

function panelFiles() {
  return readdirSync(PANELS).filter((name) => name.endsWith('.tsx')).map((name) => join(PANELS, name));
}

/** `require('...')` 안의 경로 중 파일을 가리키는 것들. */
function requiredPaths(file) {
  const source = readFileSync(file, 'utf8');
  return [...source.matchAll(/require\('(\.[^']+)'\)/g)].map((m) => m[1]);
}

describe('패널이 가리키는 그림', () => {
  it('훑을 패널이 실제로 있다', () => {
    // 이 줄이 없으면 폴더 이름이 틀려도 「전부 통과」가 된다.
    expect(panelFiles().length).toBeGreaterThan(5);
  });

  it('🔴 모든 그림 경로가 실제 파일을 가리킨다', () => {
    const broken = [];
    for (const file of panelFiles()) {
      for (const rel of requiredPaths(file)) {
        if (!existsSync(resolve(dirname(file), rel))) broken.push(`${file} → ${rel}`);
      }
    }
    expect(broken).toEqual([]);
  });

  it('찾는 방법 자체가 살아 있다 — 없는 경로는 없다고 말한다', () => {
    expect(existsSync(resolve(PANELS, '../../../assets/이런그림은없다.png'))).toBe(false);
  });
});
