// 여행 페이지 폰(2단계) — 어느 판을 여나 · 타임라인 날짜 원 위의 요일 (S15P21E201-1535).
//
// 이 시험이 지키는 것:
//   · 폰은 새 판, 폭 1024~ 는 넓은 화면 판, 1024 에 못 미치는 태블릿은 지금까지의 화면
//   · 🔴 ?classic=1 은 어느 폭이든 지금까지의 화면 — 새 판의 「일정 편집」이 이 길로 편집하러 온다
//   · 요일은 언어마다 운영체제에 맡긴다(손으로 조립하지 않는다). 못 읽는 날짜는 지어내지 않는다
import { formatWeekdayShort } from '@/i18n/datetime';
import { tripPageKind } from '@/trip/page/tripPageModel';

describe('어느 판을 여나', () => {
  it('폰(390)은 새 폰 판', () => {
    expect(tripPageKind(390, 'phone', false)).toBe('mobile');
  });

  it('폭 1024 이상은 넓은 화면 판 — 1단계와 같다', () => {
    expect(tripPageKind(1024, 'tablet', false)).toBe('desktop');
    expect(tripPageKind(1440, 'tablet', false)).toBe('desktop');
    expect(tripPageKind(1023, 'tablet', false)).toBe('classic');
  });

  it('🔴 1024 에 못 미치는 태블릿은 지금까지의 화면 — 폰 판의 «탭바가 늘어난 창» 은 탭바가 없는 태블릿에 안 맞는다', () => {
    expect(tripPageKind(820, 'tablet', false)).toBe('classic');
  });

  it('가로로 돌린 폰도 폰 판 — 짧은 변으로 가르므로 돌려도 안 바뀐다', () => {
    expect(tripPageKind(844, 'phone', false)).toBe('mobile');
  });

  it('🔴 ?classic=1 은 어느 폭이든 지금까지의 화면 — 순서·고정·제외·다시 계산은 거기에만 있다', () => {
    expect(tripPageKind(390, 'phone', true)).toBe('classic');
    expect(tripPageKind(1440, 'tablet', true)).toBe('classic');
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
