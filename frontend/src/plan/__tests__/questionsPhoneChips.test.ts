// — 일정 질문 화면 폰 위 줄 칩(출발·날짜·인원).
//   S15P21E201-1789: 칩 줄을 가로로 밀게 두었더니 390px 에서 「성인 2」가 「수정」 뒤로 반쯤 가려졌다.
//   S15P21E201-1802: 그래서 장소 칩만 줄게 했더니 최소 폭이 없어 360dp 에서 글자 없는 빈 알약(약 18px)이 됐다.
//   지금 약속: 날짜·인원이 앞 — 「성인 N」은 언제나 보인다. 장소 칩은 줄지 않고(최대 폭 + 말줄임), 넘치면 밀어 본다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

const source: string = readFileSync(join(__dirname, '..', '..', '..', 'app', '(plan)', 'questions.tsx'), 'utf8');
const phoneTop = source.slice(source.indexOf('style={styles.phoneTop}'), source.indexOf('style={styles.phoneGivenEdit}'));

describe('일정 질문 폰 위 줄 칩', () => {
  it('「성인 N」이 「수정」 뒤로 숨지 않는다 — 날짜·인원 칩이 장소 칩보다 앞에 온다 (1789)', () => {
    expect(phoneTop).toContain('[...headerChips.slice(placeChipCount), ...headerChips.slice(0, placeChipCount)]');
  });

  it('장소 칩은 빈 알약으로 줄지 않는다 — 어떤 칩도 줄지 않고, 장소 칩은 최대 폭 + 말줄임 (1802)', () => {
    expect(source).not.toContain('phoneGivenChipShrink');
    expect(source).toMatch(/phoneGivenChip: \{[^}]*flexShrink: 0/);
    expect(source).toMatch(/phoneGivenChipPlace: \{ maxWidth: \d+ \}/);
    expect(phoneTop).toContain('numberOfLines={1}');
  });

  it('넘친 장소 칩은 가로로 밀어 본다 — 「수정」은 스크롤 밖에 고정', () => {
    expect(phoneTop).toMatch(/<ScrollView\s+horizontal\s+showsHorizontalScrollIndicator=\{false\}/);
    expect(phoneTop).toContain('</ScrollView>');
    expect(source).toMatch(/phoneChips: \{ flex: 1, minWidth: 0 \}/);
  });
});
