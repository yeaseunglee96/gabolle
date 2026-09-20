// 여행 카드의 상태와 「언제인가」 — S15P21E201-1367.
//
// 서버는 status(PLANNING · READY · IN_PROGRESS · COMPLETED)와 날짜만 준다. 카드가 그것을 안 읽어서
// 다음 주 여행과 지난달 여행이 똑같이 보였다. 상태 이름 하나와 「3일 뒤 출발」 한 줄이면 고른다.
import type { TripSummaryDto, TripStatus } from '@/trip/trips';

type Tx = (ko: string, en: string) => string;

export function tripStatusLabel(status: TripStatus, tx: Tx): string {
  switch (status) {
    case 'IN_PROGRESS': return tx('진행 중', 'In progress');
    case 'READY': return tx('예정', 'Upcoming');
    case 'COMPLETED': return tx('다녀옴', 'Completed');
    case 'PLANNING': return tx('일정 준비 중', 'Itinerary pending');
    default: return tx('예정', 'Upcoming');
  }
}

const DAY_MS = 24 * 60 * 60 * 1000;

/** YYYY-MM-DD 를 그 날 0시(로컬)로. 못 읽으면 null. */
function dayStart(key: string | null | undefined): Date | null {
  if (!key) return null;
  const date = new Date(`${key}T00:00:00`);
  return Number.isNaN(date.getTime()) ? null : date;
}

/**
 * 「오늘 출발」 「3일 뒤 출발」 「여행 2일째」 「지난 여행」. 날짜가 없으면 null — 지어내지 않는다.
 * 🔴 상태가 아니라 날짜로 센다. 서버 상태는 배치로 바뀌어 하루쯤 늦을 수 있는데, 날짜는 그 자리에서 맞다.
 */
export function tripTimingLabel(trip: Pick<TripSummaryDto, 'startDate' | 'endDate'>, tx: Tx, now: Date = new Date()): string | null {
  const start = dayStart(trip.startDate);
  if (!start) return null;
  const end = dayStart(trip.endDate) ?? start;
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const untilStart = Math.round((start.getTime() - today.getTime()) / DAY_MS);
  if (untilStart > 0) return untilStart === 1 ? tx('내일 출발', 'Departs tomorrow') : tx(`${untilStart}일 뒤 출발`, `Departs in ${untilStart} days`);
  if (untilStart === 0) return tx('오늘 출발', 'Departs today');
  const sinceEnd = Math.round((today.getTime() - end.getTime()) / DAY_MS);
  if (sinceEnd > 0) return tx('지난 여행', 'Past trip');
  const dayIndex = Math.round((today.getTime() - start.getTime()) / DAY_MS) + 1;
  return tx(`여행 ${dayIndex}일째`, `Day ${dayIndex} of the trip`);
}
