declare const require: (id: string) => any; declare const __dirname: string; const { readFileSync } = require('fs');

// 🔴 S15P21E201-1941 — 여행 셋째 날(10/2)에 운영 웹에서 여행 페이지를 열었더니 1일차(9/30)가 열렸다.
//    !1969(S15P21E201-1921)는 옛 일정 화면(ItineraryClassic)만 고쳤고, 폰·넓은 화면이 함께 쓰는 여행 페이지는
//    useTripPage 의 dayIndex 를 0 으로만 시작했다.
const src: string = readFileSync(`${__dirname}/../useTripPage.ts`, 'utf8');

describe('여행 페이지는 여행 중이면 오늘 칸을 연다', () => {
  it('🔴 일정을 처음 받으면 initialDayIndex 로 날을 고른다', () => {
    expect(src).toMatch(/import \{ initialDayIndex \} from '@\/trip\/openDay'/);
    expect(src).toMatch(/setDayIndex\(initialDayIndex\(loaded\.days, undefined\)\)/);
  });
  it('여행마다 한 번만 — 사람이 고른 날을 다시 끌어오지 않는다', () => {
    expect(src).toMatch(/if \(!loaded \|\| openedDayFor\.current === sourceKey\) return;/);
    expect(src).toMatch(/openedDayFor\.current = sourceKey;/);
  });
});
