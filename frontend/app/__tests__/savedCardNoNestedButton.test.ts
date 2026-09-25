// 저장 목록 — 카드(누르는 곳) 안에 「저장 취소」 단추가 들어 있었다(S15P21E201-1710).
//
// 🔴 웹에서 「<button> cannot contain a nested <button>」 경고가 떴다. 웹 표준은 단추 안의 단추를 허용하지 않아
//    누르기·화면 낭독이 어긋날 수 있다. 화면은 그대로 그려져서 타입 검사·다른 시험이 전부 초록이다.
//    카드는 틀(View)로 두고, 사진·글만 누르는 곳, 「저장 취소」는 그 옆 형제다.

// tsconfig 가 node 타입을 안 들고 있어서 import 로 쓰면 타입 검사가 막힌다 — 이 저장소의 다른 파일 검사 시험과 같은 방식.
declare const require: (id: string) => any;
declare const __dirname: string;

function read(relative: string): string {
  const { readFileSync } = require('fs');
  const { join } = require('path');
  return readFileSync(join(__dirname, '..', '..', relative), 'utf8') as string;
}

describe('저장 목록 카드 — 단추 안에 단추가 없다', () => {
  const source = read('app/(tabs)/saved.tsx');
  const card = source.slice(source.indexOf('<View key={card.placeId} style={styles.card}>'), source.indexOf('</View>\n        ))}'));

  it('카드는 틀이고, 사진·글만 누르는 곳이다', () => {
    expect(source).toContain('<View key={card.placeId} style={styles.card}>');
    expect(card).toContain('style={styles.cardMain}>');
  });

  it('「저장 취소」는 누르는 곳이 닫힌 뒤에 형제로 온다', () => {
    const mainClose = card.indexOf('</Pressable>');
    const unsave = card.indexOf("accessibilityLabel={txf(tx, '%s 저장 취소', 'Unsave %s', card.title)}");
    expect(mainClose).toBeGreaterThan(0);
    expect(unsave).toBeGreaterThan(mainClose);
    // 안에 들어 있지 않으니 부모로 번지는 누름을 막을 일도 없다.
    expect(card).not.toContain('stopPropagation');
  });
});
