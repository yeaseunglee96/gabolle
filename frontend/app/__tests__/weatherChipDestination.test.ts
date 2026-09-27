// 홈 머리의 날씨 칩은 「내 여행 날씨·준비물」로 간다 — S15P21E201-1791.
//
// 🔴 이름표는 「내 여행 날씨·준비물」인데 현장 도구 허브(/field/translate, 번역이 먼저 뜬다)로 보냈다.
//    날씨·준비물의 입구는 여행 고르기(/trips?open=prepare)다 — 허브의 날씨 칸도 그리로 간다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

const source: string = readFileSync(join(__dirname, '..', '(tabs)', 'home.tsx'), 'utf8');

describe('홈 날씨 칩의 목적지', () => {
  const chip = source.slice(source.indexOf("'내 여행 날씨·준비물'"), source.indexOf('styles.weatherChip'));

  it('여행 고르기(날씨·준비물)로 간다', () => {
    expect(chip).toContain("pathname: '/trips', params: { open: 'prepare' }");
  });

  it('🔴 현장 도구 허브로 가지 않는다', () => {
    expect(chip).not.toContain('/field/translate');
  });
});
