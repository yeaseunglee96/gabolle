// 비어 있는 오늘 — 늦게 만들어 비운 날인가(S15P21E201-1739).
//
// 🔴 백엔드 !1734 계약: 여러 날 여행을 부산 20:31 뒤에 만들면 첫날(오늘) 항목이 0개이고 둘째 날부터 평소대로 짜인다.
//    화면은 그날을 「이 날에는 아직 장소가 없어요」라고 적었는데, 「아직」이 곧 채워질 것처럼 읽힌다.
//    서버가 이유를 따로 주지 않으므로 모양으로 가른다 — 오늘이고, 비었고, 뒤에 일정이 있다.
//    뒤에도 일정이 없으면 늦어서 비운 것이라고 말할 근거가 없다(그때는 원래 문구).

export function isSkippedToday({ days, dayIndex, today }: {
  days: ReadonlyArray<{ date: string; items: ReadonlyArray<unknown> }>;
  dayIndex: number;
  /** 기기 날짜(YYYY-MM-DD) — tripProgress.localDateKey */
  today: string;
}): boolean {
  const day = days[dayIndex];
  if (!day || day.items.length > 0 || day.date !== today) return false;
  return days.slice(dayIndex + 1).some((later) => later.items.length > 0);
}
