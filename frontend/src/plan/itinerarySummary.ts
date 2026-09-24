import type { DayStart, ItineraryDto, ItineraryItemDto } from './itinerary';

type Tx = (ko: string, en: string) => string;

// — 전에는 페이스 API 의 delayMinutes 를 「38분 지연」이라고 불렀다.
// 아직 떠나지도 않은 여행에 지연이 있을 수 없다. 그 값은 "계획 시각과 예측 도착의 차"
// 이지 지연이 아니고, 출발 전에는 계획이 그렇다는 말일 뿐이다.
//
// `from` — 그날 첫 곳이면 어디서 오는가. `true` 는 출발지다(옛 호출).
// 🔴 둘째 날부터는 숙소에서 나서는데 「출발지에서」라고 적으면 틀린 말이다(S15P21E201-1580).
export function formatTravelLabel(item: ItineraryItemDto, tx: Tx, from: boolean | DayStart['kind'] = false): string | null {
  if (item.travelDurationMin == null) return null;
  const minutes = Math.round(item.travelDurationMin);
  const estimated = item.travelDataStatus === 'ESTIMATED';
  if (from === 'LODGING') {
    return estimated
      ? tx(`숙소에서 ${minutes}분 (어림)`, `${minutes}m from your stay (est.)`)
      : tx(`숙소에서 ${minutes}분`, `${minutes}m from your stay`);
  }
  if (from) {
    return estimated
      ? tx(`출발지에서 ${minutes}분 (어림)`, `${minutes}m from start (est.)`)
      : tx(`출발지에서 ${minutes}분`, `${minutes}m from start`);
  }
  return estimated
    ? tx(`이동 ${minutes}분 (어림)`, `${minutes}m travel (est.)`)
    : tx(`이동 ${minutes}분`, `${minutes}m travel`);
}

export type ItineraryStat = { key: string; value: string; label: string };

// 값이 없으면 칸을 만들지 않는다. 비용(estimatedCostKrw)과 도보(walkingMeters)는 서버에
// 자료가 없어 늘 null 이고(서버 주석), 그 자리를 「미확인」으로 채우면 넷 중 둘이 빈 칸인
// 채로 화면에서 제일 눈에 띈다. 빈 칸은 정보가 아니다.
export function itineraryStats(itinerary: ItineraryDto, tx: Tx): ItineraryStat[] {
  const items = itinerary.days.flatMap((entry) => entry.items);
  const travelMinutes = totalTravelMinutes(items);
  const lockedCount = items.filter((entry) => entry.locked).length;
  return [
    { key: 'places', value: tx(`${items.length}곳`, `${items.length}`), label: tx('방문지', 'Places') },
    ...(travelMinutes > 0 ? [{ key: 'travel', value: tx(`${travelMinutes}분`, `${travelMinutes}m`), label: tx('총 이동 시간', 'Total travel time') }] : []),
    { key: 'days', value: tx(`${itinerary.days.length}일`, `${itinerary.days.length} days`), label: tx('여행 기간', 'Trip length') },
    ...(lockedCount > 0 ? [{ key: 'locked', value: tx(`${lockedCount}곳`, `${lockedCount}`), label: tx('고정된 장소', 'Pinned places') }] : []),
  ];
}

// 합계 칸은 서버 응답에 없다. 방문지마다 오는 구간 시간을 더해서 낸다 — 못 잰 구간은 0 이
// 아니라 "없음" 이라, 더한 값도 "적어도 이만큼" 이다.
export function totalTravelMinutes(items: ItineraryItemDto[]): number {
  return items.reduce((sum, item) => sum + (item.travelDurationMin ?? 0), 0);
}
