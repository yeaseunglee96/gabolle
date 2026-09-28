// 말하기 카드 안의 듣기 단추가 카드까지 누르지 않는다 (S15P21E201-1788).
//
// 🔴 이 저장소가 이미 아는 결함이다. trips.tsx 가 「행동 단추는 카드 Pressable 의
//    «형제»다. 안에 넣으면 웹에서 <button> 속 <button> 이 되어 안쪽 단추가 안 눌리거나
//    둘 다 눌린다」고 적어 뒀고, savedCardNoNestedButton 시험도 있다.
//    PlacePhraseBrowser 만 그 규칙이 안 걸려 있었다.
//
//    막지 않으면 「▶ 보통」 을 누를 때 카드의 onPress 까지 불려서, 문장이 재생되면서
//    카드가 접힌다 — 방금 누른 단추가 손가락 밑에서 사라진다.
// 앱 tsconfig 에는 node 타입이 없다 — 다른 소스 검사 시험과 같은 방식으로 읽는다
// (S15P21E201-1782 에서 맞춰 둔 모양).
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');

const source = readFileSync(join(__dirname, '..', 'PlacePhraseBrowser.tsx'), 'utf8') as string;

describe('말하기 카드', () => {
  it('듣기 단추를 감싼 줄이 누름을 바깥으로 안 흘린다', () => {
    // speedRow 를 그리는 View 에 responder 가드가 붙어 있어야 한다.
    expect(source).toMatch(/onStartShouldSetResponder=\{\(\) => true\}[^>]*styles\.speedRow/);
  });

  it('듣기 단추는 둘 다 그대로 있다 — 가드가 단추를 지우지 않았다', () => {
    expect(source).toContain("tx('보통 속도로 듣기', 'Listen at normal speed')");
    expect(source).toContain("tx('느린 속도로 듣기', 'Listen at slow speed')");
  });
});
