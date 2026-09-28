// 여행 날짜 달력의 «몇 번째 달» 계산 — S15P21E201-1539.
//
// 두 달력(홈 시작 바 · 여행 만들기의 DateRangeCard)이 같은 규칙을 쓰도록 여기 한 곳에 둔다.
// 예전에는 여행 만들기 달력만 12개월로 막혀 있고 홈 달력은 끝없이 넘어갔다 — 홈에서 13개월 뒤를
// 고르면 여행 만들기 달력은 그 달을 보여 줄 수 없었다.

/** 오늘이 든 달을 0 으로, 고를 수 있는 마지막 달. 12개월(0~11)이다. */
export const MAX_MONTH_OFFSET = 11;

/** 이번 달에서 몇 달 뒤인가 — 날짜가 없거나 범위 밖이면 가까운 끝으로 붙인다. */
export function monthOffsetOf(dateKey: string, today: Date): number {
  const found = /^(\d{4})-(\d{2})-\d{2}$/.exec(dateKey ?? '');
  if (!found) return 0;
  const offset = (Number(found[1]) - today.getFullYear()) * 12 + (Number(found[2]) - 1 - today.getMonth());
  return Math.min(MAX_MONTH_OFFSET, Math.max(0, offset));
}

/** 몇 달 뒤의 연·월 (월은 0부터). */
export function monthAt(today: Date, offset: number): { year: number; month: number } {
  const base = new Date(today.getFullYear(), today.getMonth() + offset, 1);
  return { year: base.getFullYear(), month: base.getMonth() };
}
