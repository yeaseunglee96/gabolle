// 여행 경비 화면 말투(S15P21E201-1982) — 「여행 돈」·「쓴 돈 적기」·「남은 돈」은 일상 말투라 메뉴 이름으로 어색했다.
// 화면에 나가는 문구(tx·txf·실패 문구)에 옛 말이 다시 들어오지 않게 막는다. 주석은 보지 않는다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

import { TRANSLATIONS } from '../../i18n/translations';
import { MESSAGE_EN as MESSAGES } from '../../i18n/messages';

const ROOT = join(__dirname, '..', '..', '..');
const FILES = [
  'app/(trip)/[id]/money.tsx',
  'src/trip/expenses.ts',
  'src/trip/page/TripPageMobile.tsx',
  'src/trip/page/TripPageDesktop.tsx',
];
const OLD = /여행 돈|쓴 돈|남은 돈|Trip money|旅行のお金|使ったお金/;

/** 따옴표 안의 문자열만 — 주석의 「여행 돈」은 이력이라 남겨도 된다. */
function quoted(source: string): string[] {
  const noComments = source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1');
  return [...noComments.matchAll(/'([^'\\]*(?:\\.[^'\\]*)*)'/g)].map((m) => m[1]);
}

describe('여행 경비 화면 말투', () => {
  it.each(FILES)('%s 의 화면 문구에 옛 말이 없다', (file) => {
    const hits = quoted(readFileSync(join(ROOT, file), 'utf8') as string).filter((s) => OLD.test(s));
    expect(hits).toEqual([]);
  });

  it('번역표·영어 문구에도 옛 말이 없다', () => {
    const keys = Object.entries(TRANSLATIONS as Record<string, unknown>)
      .filter(([key, value]) => OLD.test(key) || OLD.test(JSON.stringify(value)))
      .map(([key]) => key);
    const english = Object.entries(MESSAGES as Record<string, string>)
      .filter(([key, value]) => OLD.test(key) || OLD.test(value))
      .map(([key]) => key);
    expect([...keys, ...english]).toEqual([]);
  });

  it('새 이름은 다섯 언어가 다 있다', () => {
    const table = TRANSLATIONS as Record<string, { ja: string; zhHans: string; zhHant: string }>;
    expect(table['여행 경비']).toEqual({ ja: '旅費', zhHans: '旅行费用', zhHant: '旅行費用' });
    expect(table['지출 추가']).toEqual({ ja: '支出を追加', zhHans: '添加支出', zhHant: '新增支出' });
    expect(table['지출']).toEqual({ ja: '支出', zhHans: '支出', zhHant: '支出' });
  });
});
