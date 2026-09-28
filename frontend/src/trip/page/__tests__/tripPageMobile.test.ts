// 여행 페이지 폰(2단계) — 어느 판을 여나 · 타임라인 날짜 원 위의 요일 (S15P21E201-1535).
//
// 이 시험이 지키는 것:
//   · 데스크톱 판정이면 넓은 화면 판, 아니면 새 폰 판 — 그 사이 칸(옛 화면)은 없다
//     판정 자체(폴드 네 모양)는 src/layout/__tests__/useLayout.test.ts 가 지킨다
//   · 🔴 ?classic=1 은 어느 폭이든 지금까지의 화면 — 새 판의 「일정 편집」이 이 길로 편집하러 온다
//   · 요일은 언어마다 운영체제에 맡긴다(손으로 조립하지 않는다). 못 읽는 날짜는 지어내지 않는다
import { formatWeekdayShort } from '@/i18n/datetime';
import { isDesktopWindow } from '@/layout/useLayout';
import { tripPageKind } from '@/trip/page/tripPageModel';

describe('어느 판을 여나', () => {
  it('폰은 새 폰 판, 데스크톱은 넓은 화면 판', () => {
    expect(tripPageKind(false, false)).toBe('mobile');
    expect(tripPageKind(true, false)).toBe('desktop');
  });

  it('🔴 폴드를 펼치면 옛 화면이 아니다 — 세로는 폰 판, 가로는 넓은 화면 판 (S15P21E201-1563)', () => {
    expect(tripPageKind(isDesktopWindow(717, 795), false)).toBe('mobile');
    expect(tripPageKind(isDesktopWindow(795, 717), false)).toBe('desktop');
  });

  it('🔴 ?classic=1 은 어느 폭이든 지금까지의 화면 — 순서·고정·제외·다시 계산은 거기에만 있다', () => {
    expect(tripPageKind(false, true)).toBe('classic');
    expect(tripPageKind(true, true)).toBe('classic');
  });
});

describe('타임라인 날짜 원 위의 요일', () => {
  it('언어마다 그 언어의 짧은 요일 — 2026-09-23 은 수요일', () => {
    expect(formatWeekdayShort('2026-09-23', 'ko-KR')).toBe('수');
    expect(formatWeekdayShort('2026-09-23', 'en-US')).toBe('Wed');
    expect(formatWeekdayShort('2026-09-23', 'ja-JP')).toBe('水');
  });

  it('못 읽는 날짜는 null — 요일을 지어내지 않는다', () => {
    expect(formatWeekdayShort('어제', 'ko-KR')).toBeNull();
  });
});
