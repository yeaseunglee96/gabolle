// 폰 기록 격자는 폭 343·390 에서도 카드 두 장이 한 줄에 들어가야 한다 (S15P21E201-1805).
declare const require: (id: string) => any; declare const __dirname: string; const { readFileSync } = require('fs');


import { RECORD_PHONE_CARD_FRACTION, recordPhoneGrid } from '@/me/RecordCard';

const grid = recordPhoneGrid as Record<string, unknown>;
const columnGap = Number(grid.columnGap ?? grid.gap ?? 0);

describe('폰 기록 격자 2열', () => {
  it.each([320, 343, 358, 390])('콘텐츠 폭 %i 에서 두 장이 한 줄에 선다', (w) => {
    expect(2 * RECORD_PHONE_CARD_FRACTION * w + columnGap).toBeLessThanOrEqual(w);
  });

  it('가로 틈은 space-between 이 만든다 — 홀수 마지막 카드는 왼쪽 칸', () => {
    expect(grid.gap).toBeUndefined();
    expect(grid.columnGap).toBeUndefined();
    expect(grid.justifyContent).toBe('space-between');
    expect(grid.flexWrap).toBe('wrap');
  });

  it('카드 기본 폭이 분수와 같다', () => {
    const src = readFileSync(`${__dirname}/../RecordCard.tsx`, 'utf8');
    expect(src).toContain(`card: { width: '${RECORD_PHONE_CARD_FRACTION * 100}%'`);
  });

  it.each([
    ['../../../app/user/[id].tsx', 'wide ? styles.grid : recordPhoneGrid'],
    ['../RecordsBrowser.tsx', 'cardWidth ? styles.grid : recordPhoneGrid'],
    ['../../../app/(trip)/[id]/collaborate.tsx', '<View style={recordPhoneGrid}>'],
  ])('%s 의 폰 격자는 recordPhoneGrid 를 쓴다', (rel, needle) => {
    const src = readFileSync(`${__dirname}/${rel}`, 'utf8');
    expect(src).toContain(needle);
  });
});
