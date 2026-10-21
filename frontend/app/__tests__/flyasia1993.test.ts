// S15P21E201-1993·1994 — FLY ASIA 시연 녹화(10/5, iOS build 54)에서 나온 앱 문제.

declare const __dirname: string;
const { readFileSync } = require('fs');

const { join } = require('path');

const root = join(__dirname, '..', '..') as string;
const read = (rel: string) => readFileSync(join(root, rel), 'utf8') as string;

describe('1993 — 현장 도구 화면에서 나갈 길', () => {
  it.each(['speak', 'translate', 'dialect', 'exchange-rate', 'transit'])('app/field/%s.tsx 에 뒤로 단추가 있다', (name) => {
    expect(read(`app/field/${name}.tsx`)).toMatch(/<FieldBackButton\b/);
  });
});

describe('1993 — 가입 4/4 단추가 튀지 않는다', () => {
  it('남은 것이 없어도 안내 상자를 지우지 않는다', () => {
    const src = read('app/(auth)/sign-up.tsx');
    expect(src).not.toMatch(/panelIndex === 3\) && blockers\.length > 0 &&/);
    expect(src).toContain('가입할 준비가 됐어요');
  });
});

describe('1993 — 접근성 트리에 잡힌다', () => {
  it('여행 이름 시트를 감싼 Pressable 이 안의 요소를 묶지 않는다', () => {
    expect(read('src/trip/TripNameSheet.tsx')).toMatch(/<Pressable accessible=\{false\} onPress=\{\(\) => \{\}\}/);
  });
  it('펼친 문장 카드는 듣기 단추를 묶지 않고, 단추 이름에 보이는 글자가 들어 있다', () => {
    const src = read('src/components/PlacePhraseBrowser.tsx');
    expect(src).toContain('accessible={!expanded}');
    expect(src).toContain("'Normal, listen at normal speed'");
  });
});
