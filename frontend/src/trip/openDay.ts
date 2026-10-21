// 일정 화면을 처음 열 때 어느 날 칸을 보일지 고른다(S15P21E201-1921).
// 여행 둘째 날에 열어도 1일차가 열려, 오늘 갈 곳을 보려면 칸을 한 번 더 눌러야 했다.

/** 기기 시간대 기준 오늘의 'YYYY-MM-DD'. */
function localDay(now: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * 주소로 고른 날(dayParam, 1부터)이 있으면 그 날, 없으면 오늘이 여행 날짜 안일 때 오늘, 그 밖에는 첫날.
 * 돌려주는 값은 0부터 센 칸 번호다.
 */
export function initialDayIndex(days: readonly { date: string }[], dayParam: unknown, now: Date = new Date()): number {
  const requested = Number(dayParam);
  if (Number.isInteger(requested) && requested >= 1) return requested - 1;
  const today = localDay(now);
  const index = days.findIndex((day) => day.date?.slice(0, 10) === today);
  return index >= 0 ? index : 0;
}
