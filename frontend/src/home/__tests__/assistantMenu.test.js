// AI 도우미가 데려다주는 화면이 정말 있는가.
//
// 고친 결함이 이것이었다 — 단추 옆 라벨은 「일정 · 통역 · 여행 도움」이라고 적혀 있는데
// 누르면 챗봇 한 곳으로만 갔다. 셋을 약속하고 하나만 줬다.
//
// 🔴 주소는 그냥 문자열이라 타입이 안 잡는다. 오타를 내면 눌렀을 때 빈 화면이 뜨고,
// 시험도 빌드도 초록이다. 화면을 눌러 봐야만 안다.
//
// 타입스크립트가 아니라 평범한 자바스크립트인 이유는 이 저장소의 tsconfig 가 types 를
// ["jest"] 로 묶어 node 타입을 일부러 안 넣기 때문이다 — noControlCharacters 와 같다.

const { existsSync } = require('fs');
const { join } = require('path');

const { ASSISTANT_PATHS } = require('@/home/AssistantMenu');

const APP = join(__dirname, '..', '..', '..', 'app');

/** expo-router 는 app/ 아래 파일 자리가 곧 주소다. `/field/speak` → `app/field/speak.tsx` */
function routeExists(path) {
  const rel = path.replace(/^\//, '');
  return ['.tsx', '.ts', '/index.tsx', '/index.ts'].some((suffix) => existsSync(join(APP, rel + suffix)));
}

describe('AI 도우미 메뉴', () => {
  it('데려다주는 곳이 하나가 아니다 — 라벨이 여럿을 약속한다', () => {
    // 하나로 줄면 라벨이 다시 거짓말을 시작한다.
    expect(ASSISTANT_PATHS.length).toBeGreaterThan(1);
  });

  it('같은 곳을 두 번 넣지 않는다', () => {
    expect(new Set(ASSISTANT_PATHS).size).toBe(ASSISTANT_PATHS.length);
  });

  it.each(ASSISTANT_PATHS)('%s 화면이 실제로 있다', (path) => {
    expect(routeExists(path)).toBe(true);
  });

  it('찾는 방법 자체가 살아 있다 — 없는 주소는 없다고 말한다', () => {
    // 이 줄이 없으면 routeExists 가 늘 true 를 돌려줘도 위 시험이 전부 통과한다.
    expect(routeExists('/이런-화면은-없다')).toBe(false);
  });
});
