/**
 * S15P21E201-1817 — 여행 기분 설명이 고정 개수(「하루 2–3곳」)를 약속하면 안 된다.
 * 서버(S15P21E201-1816)가 하루 길이로 곳 수를 세게 되어, 09–21시 여유롭게는 4곳이 나온다.
 * 화면이 「2–3곳」이라 적어 두면 앱이 거짓말한 것이 된다.
 */
declare const require: (id: string) => any; declare const __dirname: string; const { readFileSync } = require('fs');

const source: string = readFileSync(`${__dirname}/../planOptions.ts`, 'utf8');

describe('여행 기분 설명 문구', () => {
  it('고정 개수를 약속하지 않는다', () => {
    expect(source).not.toMatch(/하루 \d+[–~-]\d+곳|하루 \d+곳 이상|\d+[–~-]\d+ places a day|\d\+ places a day/);
  });

  it('기분마다 몇 시간에 한 곳 꼴인지 적는다 — 서버 규칙(3시간·2시간·1시간 반)과 같다', () => {
    expect(source).toContain("['RELAXED', '여유롭게', 'Relaxed', '3시간에 한 곳 꼴");
    expect(source).toContain("['BALANCED', '균형 있게', 'Balanced', '2시간에 한 곳 꼴");
    expect(source).toContain("['PACKED', '알차게', 'Packed', '1시간 반에 한 곳 꼴");
  });
});
