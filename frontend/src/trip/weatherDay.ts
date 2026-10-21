// 여행 페이지 날씨 창이 넘길 하루 — S15P21E201-1919(웹 10/2).
// 날씨 창(TripWeatherPanel)은 시험에서 통째로 흉내 내므로, 고르는 규칙은 따로 둔다.

/**
 * 여행 중(첫날 다음 날 ~ 마지막 날)이면 오늘 날짜의 하루(그날 일정 포함), 아니면 첫날.
 * 🔴 !1959 는 준비 화면만 고쳤다. 여행 페이지는 days[0] 을 그대로 넘겨 셋째 날에도 「지난 여행 · 예보 없음」이었다.
 * 날짜는 YYYY-MM-DD 글자끼리 비교한다.
 */
export function weatherDayFor<D extends { date: string }>(days: ReadonlyArray<D>, today: string): D | undefined {
  const first = days[0];
  if (!first) return undefined;
  const start = first.date.slice(0, 10);
  const end = days[days.length - 1].date.slice(0, 10);
  if (start < today && today <= end) return days.find((day) => day.date.slice(0, 10) === today) ?? first;
  return first;
}
