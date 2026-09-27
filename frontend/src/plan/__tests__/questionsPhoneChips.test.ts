// — 일정 질문 화면 폰 위 줄 칩(출발·날짜·인원)이 「수정」 뒤로 잘리던 것(S15P21E201-1789).
//   390px 에서 칩 줄을 가로로 밀게 두었더니 「성인 2」가 반쯤 가려졌고, 밀 수 있다는 표시가 없어 아무도 몰랐다.
//   줄 바꿈은 S15P21E201-1401 이 막았으므로 «한 줄 안에서 줄어드는가» 를 잰다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

const source: string = readFileSync(join(__dirname, '..', '..', '..', 'app', '(plan)', 'questions.tsx'), 'utf8');
const phoneTop = source.slice(source.indexOf('style={styles.phoneTop}'), source.indexOf('style={styles.phoneGivenEdit}'));

describe('일정 질문 폰 위 줄 칩', () => {
  it('가로 스크롤로 넘기지 않는다 — 넘친 칩은 「수정」 뒤로 숨는다', () => {
    expect(phoneTop).not.toContain('<ScrollView');
  });

  it('장소 칩만 말줄임으로 줄고 날짜·인원은 줄지 않는다', () => {
    expect(phoneTop).toContain('index < placeChipCount && styles.phoneGivenChipShrink');
    expect(source).toMatch(/phoneGivenChip: \{[^}]*flexShrink: 0/);
    expect(source).toMatch(/phoneGivenChipShrink: \{ flexShrink: 1, minWidth: 0 \}/);
    expect(source).toMatch(/phoneChips: \{ flex: 1, minWidth: 0,/);
  });
});
