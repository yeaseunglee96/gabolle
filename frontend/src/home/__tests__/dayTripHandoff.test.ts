// 홈 시작 바 → 여행 만들기 — 하루만 고른 당일치기가 귀환일 없이 넘어가던 것(S15P21E201-1729).
//
// 🔴 2026-09-26 발표 시연 점검(폰 390). 시작 바 달력에서 오늘 하루만 누르면 요약은 「9.26(토) · 당일치기」,
//    「일정 물어보기」도 켜진다(시작 바는 시작일만 있으면 당일치기로 본다). 그런데 넘어간 여행 만들기 화면이
//    날짜 카드를 다시 열고 「귀환일까지 골라 주세요」로 잠갔다 — 넘길 때 귀환일을 빈 채로 넘겼기 때문이다.
//    넘기는 곳이 폰 홈·넓은 화면 홈 두 벌이라 둘 다 같은 규칙을 쓰는지도 본다.
declare const require: (id: string) => any;
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');

import { EMPTY_START_BAR, startBarEndDate } from '../startBarValue';

describe('시작 바에서 넘기는 귀환일', () => {
  it('🔴 시작일만 있으면 귀환일은 시작일 — 시작 바가 보여 준 「당일치기」 그대로', () => {
    expect(startBarEndDate({ ...EMPTY_START_BAR, startDate: '2026-09-26', endDate: '' })).toBe('2026-09-26');
  });

  it('두 날짜가 다 있으면 그대로', () => {
    expect(startBarEndDate({ ...EMPTY_START_BAR, startDate: '2026-09-27', endDate: '2026-09-28' })).toBe('2026-09-28');
  });

  it('날짜를 안 골랐으면 비어 있다 — 없는 날짜를 만들지 않는다', () => {
    expect(startBarEndDate({ ...EMPTY_START_BAR, startDate: '', endDate: '' })).toBe('');
  });

  it.each([
    ['폰 홈', join(__dirname, '..', '..', '..', 'app', '(tabs)', 'home.tsx')],
    ['넓은 화면 홈', join(__dirname, '..', '..', '..', 'app', 'index.tsx')],
  ])('🔴 %s — 여행 만들기로 넘길 때 이 규칙을 쓴다', (_name, file) => {
    const source = readFileSync(file, 'utf8') as string;
    expect(source).toContain('endDate: startBarEndDate(value),');
    expect(source).not.toContain('endDate: value.endDate,');
  });
});
