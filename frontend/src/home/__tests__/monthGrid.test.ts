// 달력 날짜가 요일과 어긋나던 것 — S15P21E201-1434.
//
// 🔴 2026-09-21 실기(SM-G973N, 1080×2280, versionCode 28). 9월 달력이 한 줄에 여섯 칸씩
//    접혀서 토요일 칸이 늘 비고, 21일(월)이 「목」 칸에, 25일(금)이 「화」 칸에 있었다.
//    누르면 「9.25(금)」으로 제대로 들어갔으니 데이터가 아니라 «배치»만 틀린 것인데,
//    사람은 요일을 보고 고르므로 엉뚱한 날을 고르게 된다.
//
// 🔴 원인은 칸 폭이 100/7 % 였던 것이다. Yoga 는 칸마다 픽셀 격자에 맞춰 반올림해서
//    일곱 칸 합이 부모 폭을 넘고 마지막 칸이 다음 줄로 밀린다. 웹(react-native-web)은
//    CSS 백분율이라 재현되지 않는다 — 그래서 웹 확인으로도 못 잡았다.
//
// 🔴 이 시험은 «한 줄에 일곱 칸이 들어 있는가»를 잰다. 폭 계산 자체는 렌더러가 하는 일이라
//    jsdom 으로 못 재지만, 줄을 미리 일곱씩 묶어 두면 폭이 어떻든 어긋날 자리가 없어진다.
//    그래서 묶는 규칙을 직접 잰다.
import { readFileSync } from 'fs';
import { join } from 'path';

import { DAYS_IN_WEEK, monthWeeks } from '@/home/PlanStartBar';

const SOURCE = readFileSync(join(__dirname, '..', 'PlanStartBar.tsx'), 'utf8');

/** 0=일 … 6=토 */
function weekdayOf(dateKey: string): number {
  const [year, month, day] = dateKey.split('-').map(Number);
  return new Date(year, month - 1, day).getDay();
}

describe('달력을 주 단위로 묶는다', () => {
  it('🔴 모든 줄이 일곱 칸이다 — 마지막 주도 빈 칸으로 채운다', () => {
    for (let month = 0; month < 12; month += 1) {
      for (const year of [2026, 2027, 2028]) {
        expect(monthWeeks(year, month).every((week) => week.length === DAYS_IN_WEEK)).toBe(true);
      }
    }
  });

  it('🔴 칸의 자리가 그 날의 요일과 같다 — 21일(월)이 「목」 칸에 있던 것이 이 결함이었다', () => {
    for (const [year, month] of [[2026, 8], [2026, 9], [2027, 1], [2028, 1]] as const) {
      monthWeeks(year, month).forEach((week) => {
        week.forEach((key, column) => {
          if (key) expect(weekdayOf(key)).toBe(column);
        });
      });
    }
  });

  it('첫 줄의 앞 빈칸 수가 1일의 요일이다', () => {
    // 2026-09-01 은 화요일(2) — 앞에 빈칸 둘.
    const first = monthWeeks(2026, 8)[0];
    expect(first.slice(0, 2)).toEqual([null, null]);
    expect(first[2]).toBe('2026-09-01');
  });

  it('그 달의 날짜를 하나도 빠뜨리거나 더하지 않는다', () => {
    const days = monthWeeks(2026, 8).flat().filter(Boolean);
    expect(days).toHaveLength(30);
    expect(days[0]).toBe('2026-09-01');
    expect(days[days.length - 1]).toBe('2026-09-30');
  });

  it('🔴 빈칸은 뒤쪽 줄에만 있고 날짜 사이에는 없다 — 중간에 끼면 뒤 날짜가 전부 밀린다', () => {
    const flat = monthWeeks(2026, 8).flat();
    const firstDay = flat.findIndex(Boolean);
    const lastDay = flat.length - 1 - [...flat].reverse().findIndex(Boolean);
    expect(flat.slice(firstDay, lastDay + 1).every(Boolean)).toBe(true);
  });
});

// 🔴 위 시험은 새 함수(monthWeeks)만 잰다 — 누가 렌더를 옛 방식으로 되돌려도 초록이다.
//    되돌림 자체를 막는 검사를 따로 둔다. 폭 계산은 렌더러의 일이라 jsdom 으로 못 재기 때문이다.
describe('옛 방식으로 되돌리지 않는다', () => {
  function styleLine(name: string): string {
    return SOURCE.split('\n').find((line) => line.trim().startsWith(`${name}: {`)) ?? '';
  }

  it('🔴 달력 칸 폭을 백분율로 되돌리지 않는다 — 폭 반올림이 일곱째 칸을 다음 줄로 민다', () => {
    expect(styleLine('cell')).not.toContain('100 / 7');
    expect(styleLine('cell')).toContain('flex: 1');
  });

  it('🔴 줄바꿈(flexWrap)으로 달력을 접지 않는다', () => {
    expect(styleLine('grid')).not.toContain('flexWrap');
  });

  it('요일 머리도 칸과 같은 방식으로 폭을 나눈다 — 한쪽만 바꾸면 다시 어긋난다', () => {
    expect(styleLine('headCell')).toContain('flex: 1');
    expect(styleLine('headCell')).not.toContain('100 / 7');
  });
});
