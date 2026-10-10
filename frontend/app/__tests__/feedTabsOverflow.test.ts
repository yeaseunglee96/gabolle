declare const require: (id: string) => any; declare const __dirname: string; const { readFileSync } = require('fs');
import { TRANSLATIONS as translations } from '@/i18n/translations';

// S15P21E201-2027 — 일본어·베트남어에서 「루트」 탭이 폰 폭 밖으로 밀려 누를 수 없었다.
const src: string = readFileSync(`${__dirname}/../(tabs)/feed.tsx`, 'utf8');

describe('피드 탭 줄 — 좁은 폰에서도 네 탭 모두 닿는다', () => {
  it('폰에서는 탭 줄을 가로 스크롤로 감싸고, 넓은 화면은 그대로 둔다', () => {
    expect(src).toMatch(/compact \? \(\s*<ScrollView ref=\{tabScroll\} horizontal showsHorizontalScrollIndicator=\{false\}/);
    expect(src).toMatch(/\) : tabRow\(\)\}/);
    expect(src).toMatch(/tabButton\('ROUTES', tx\('루트', 'Routes'\)\)/);
  });
  it('고른 탭을 화면 안으로 스크롤한다', () => {
    expect(src).toMatch(/tabScroll\.current\?\.scrollTo\(/);
  });
  it.each(['ja', 'vi'] as const)('%s 탭 이름 넷이 모두 있고 지나치게 길지 않다', (lang) => {
    const t = translations as Record<string, Record<string, string>>;
    const labels = ['실시간 인기', '맞춤 추천', '내 피드', '루트'].map((k) => t[k]?.[lang]);
    labels.forEach((l) => expect(l).toBeTruthy());
    expect(t['루트'][lang].length).toBeGreaterThan(0);
    expect(labels.join('').length).toBeLessThanOrEqual(45);
  });
});
