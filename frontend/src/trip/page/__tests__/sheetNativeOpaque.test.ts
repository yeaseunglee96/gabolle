// 폰 여행 화면 일정 창 (S15P21E201-1796) — 폰에는 흐림(backdropFilter)이 없으니 반투명 유리색을 쓰면 지도가 글자 뒤로 비친다.
// 유리색은 웹만 쓰는지 소스를 읽어 확인한다.
declare const require: (id: string) => any; declare const __dirname: string; const { readFileSync } = require('fs');

const src: string = readFileSync(`${__dirname}/../TripPageMobile.tsx`, 'utf8');

describe('TripPageMobile 일정 창 바탕', () => {
  it('유리색(sheetGlass)은 웹에서만, 폰은 불투명 card', () => {
    expect(src).toMatch(/SHEET_BG = Platform\.OS === 'web' \? color\.surface\.sheetGlass : color\.surface\.card/);
    expect(src).not.toMatch(/sheet: \{ backgroundColor: color\.surface\.sheetGlass/);
    expect(src).not.toMatch(/\[color\.surface\.card, color\.surface\.sheetGlass\]/);
  });
});
