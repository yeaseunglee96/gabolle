// 작은 단추는 hitSlop 으로 44pt 를 채운다 (S15P21E201-1794).
//
// 🔴 왜 소스 검사인가 — 터치 영역은 그려도 안 보인다. jsdom 에는 높이가 없고,
//    실기기에서도 «눌러 봐야» 안다. 그래서 「값이 붙어 있는가」를 글자로 본다.
//
// 🔴 웹에서는 이 고침이 «안 듣는다». react-native-web 의 Pressable 에는 hitSlop
//    구현이 없다(옛 Touchable 에만 있다). 실제로 웹으로 재 보니 점 위 15pt 는
//    안 눌리고 정중앙만 눌렸다. iOS·안드로이드에서는 듣는다 — 앱이 나가는 곳이
//    거기이므로 그대로 둔다. 다만 «웹으로 확인했다» 고 적으면 안 된다.
//
// 두 자리 다 실제로 사람이 겪었다.
//
//  · 사진 삭제 × (24pt) — 누를 수 있는 사진 타일 «위»에 얹혀 있어서, 빗나가면
//    아무 일도 안 나는 게 아니라 «뒤의 타일이 눌려 사진이 열린다». 한 장 빼는 데
//    두세 번 눌러야 했다.
//  · 가입 단계 점 (높이 4) — 「○○ 단계로 이동」 이라고 읽어 주는데 거의 안 눌렸다.
//    44 기준의 1/11 이다.
// 앱 tsconfig 에는 node 타입이 없다 — 다른 소스 검사 시험과 같은 방식으로 읽는다
// (S15P21E201-1782 에서 맞춰 둔 모양).
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');

const app = (...parts: string[]) => join(__dirname, '..', '..', '..', 'app', ...parts) as string;
const read = (file: string) => readFileSync(file, 'utf8') as string;

describe('작은 터치 영역', () => {
  it.each([
    ['글쓰기 사진 삭제', app('(tabs)', 'feed.tsx')],
    ['댓글 사진 삭제', app('feed', '[id].tsx')],
  ])('%s × 에 hitSlop 이 있다', (_name, file) => {
    // 「사진 삭제」 단추를 그리는 줄에 hitSlop 이 같이 있어야 한다.
    const line = read(file).split('\n').find((l: string) => l.includes("tx('사진 삭제', 'Remove photo')"));
    expect(line).toBeDefined();
    expect(line).toContain('hitSlop');
  });

  it('가입 단계 점에 위아래 hitSlop 이 있다 — 높이가 4 라서 없으면 못 누른다', () => {
    const line = read(app('(auth)', 'sign-up.tsx')).split('\n').find((l: string) => l.includes('styles.questionDot,'));
    expect(line).toBeDefined();
    expect(line).toMatch(/hitSlop=\{\{[^}]*top:\s*20[^}]*bottom:\s*20/);
  });

  it('점의 «모양»은 안 건드린다 — 시안의 얇은 진행 막대 그대로', () => {
    expect(read(app('(auth)', 'sign-up.tsx'))).toMatch(/questionDot:\s*\{[^}]*height:\s*4\b/);
  });
});
