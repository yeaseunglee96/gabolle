// 여행 중 카드의 「몇 곳 중 몇 곳 다녀옴」 — 영어 문구도 값 순서가 맞는가(S15P21E201-1728).
//
// 🔴 2026-09-26 발표 시연 점검(폰 390 영어). 네 곳 중 한 곳을 다녀왔는데 카드가 「4 of 1 stops done」이라고 적었다.
//    값은 앞에서부터 차례로 끼워진다(format.ts — 「값의 순서를 번역에서 바꾸지 않는다」). 한국어 틀
//    「%s곳 중 %s곳 다녀옴」은 (전체, 다녀온 수) 순서인데, 영어 틀 「%s of %s stops done」은 (다녀온 수, 전체)로
//    읽혀 조용히 뒤집혔다. 같은 틀이 폰 여행 화면과 일정 화면 두 곳에 있어 둘 다 본다.
//    영어는 값 순서대로 읽히는 「4 stops · 1 done」 — 「Of 4 stops, 1 done」은 한 줄에서 끝말이 넘쳐 꺾였다.
declare const require: (id: string) => any;
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');

import { fillValues } from '@/i18n/format';

const FILES = [
  join(__dirname, '..', 'TripPageMobile.tsx'),
  join(__dirname, '..', '..', '..', '..', 'app', 'trips', '[id]', 'itinerary.tsx'),
];
const KO = '%s곳 중 %s곳 다녀옴';

describe('「몇 곳 중 몇 곳 다녀옴」 영어 문구의 값 순서', () => {
  it.each(FILES)('🔴 %s — (전체 4, 다녀온 1)이 영어로 「4 stops · 1 done」', (file) => {
    const source = readFileSync(file, 'utf8') as string;
    const calls = [...source.matchAll(/txf\(tx, '%s곳 중 %s곳 다녀옴', '([^']+)'/g)];
    expect(calls.length).toBeGreaterThan(0);
    for (const [, en] of calls) {
      expect(fillValues(KO, [4, 1])).toBe('4곳 중 1곳 다녀옴');
      expect(fillValues(en, [4, 1])).toBe('4 stops · 1 done');
    }
  });
});
