// 홈 머리의 알림 종이 날씨 칩에 밀려 사라지던 것 — S15P21E201-1390 후속.
//
// 🔴 2026-09-21 실기(SM-G973N, 1080×2280, versionCode 27, 한국어, 로그인 상태).
//    머리줄은 [언어 알약][날씨 칩][종] 셋이 나란히 선다. 한국어 날씨는
//    「구름 조금 21° / 28°」처럼 길어서 줄이 넘쳤고, 그때 종의 경계가
//
//        bounds=[1076,154][1080,270]     ← 폭 4px
//
//    였다. 화면 폭이 1080 이니 종은 오른쪽 끝에 4px 만 남아 «보이지도 눌리지도»
//    않았다. 손님일 때는 날씨 칩이 없어서 멀쩡했다 — 그래서 로그인해야만 보인다.
//
// 🔴 원인은 React Native 의 기본값이다. flex 자식은 flexShrink 가 «1» 이라, 줄이
//    넘치면 고정폭을 준 것까지 줄어든다. width:44 는 «이만큼 달라»는 뜻이지
//    «이만큼은 지켜라»가 아니다. 지키게 하는 것은 flexShrink:0 이다.
//
// 🔴 이 시험이 파일 내용을 보는 이유. 렌더러로는 못 잡는다 — jsdom 에는 flexbox 가
//    없어서 폭이 언제나 0 으로 나오고, 넘침이 재현되지 않는다. 실제로 이 결함은
//    타입·시험·폭 검사가 전부 초록인 채로 스토어까지 올라갔다.
declare const require: (id: string) => any;
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');

const HOME = readFileSync(join(__dirname, '..', '(tabs)', 'home.tsx'), 'utf8') as string;

/** `styles.<이름>: { ... }` 한 줄을 통째로 집어 온다. */
function styleLine(name: string): string {
  const found = HOME.split('\n').find((line) => line.trim().startsWith(`${name}: {`));
  if (!found) throw new Error(`${name} 스타일을 못 찾았습니다 — 이름이 바뀌었으면 이 시험도 같이 고치십시오`);
  return found;
}

describe('홈 머리 — 줄이 넘쳐도 종은 줄지 않는다', () => {
  it('🔴 종에 flexShrink: 0 이 있다 — 없으면 날씨가 길어질 때 4px 로 찌부러진다', () => {
    expect(styleLine('bell')).toContain('flexShrink: 0');
  });

  it('🔴 종은 눌릴 수 있는 크기를 지킨다 — 44 는 손가락이 닿는 최소 크기다', () => {
    const bell = styleLine('bell');
    expect(bell).toContain('width: 44');
    expect(bell).toContain('height: 44');
  });

  it('언어 알약도 안 줄어든다 — 글자가 「한국어」에서 「日本語」로 바뀌어도 자리가 같아야 한다', () => {
    expect(styleLine('langPill')).toContain('flexShrink: 0');
  });

  it('🔴 줄어들 쪽은 «글자가 든» 날씨 칩이다 — 줄일 곳을 안 정하면 줄이 그냥 넘친다', () => {
    const chip = styleLine('weatherChip');
    expect(chip).toContain('flexShrink: 1');
    // minWidth: 0 이 없으면 flexShrink:1 이 있어도 안 줄어든다 — 글자의 «최소 내용 폭»이 바닥이 된다.
    expect(chip).toContain('minWidth: 0');
  });
});
