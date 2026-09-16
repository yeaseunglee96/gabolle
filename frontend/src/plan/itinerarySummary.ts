import type { ItineraryDto, ItineraryItemDto } from './itinerary';

type Tx = (ko: string, en: string) => string;

// S15P21E201-1014 — 전에는 페이스 API 의 delayMinutes 를 「38분 지연」이라고 불렀다.
// 🔴 아직 떠나지도 않은 여행에 지연이 있을 수 없다. 그 값은 "계획 시각과 예측 도착의 차"
// 이지 지연이 아니고, 출발 전에는 계획이 그렇다는 말일 뿐이다.
//
// 서버가 주는 것은 이 방문지로 오는 데 걸리는 시간(travelDurationMin)이다. 그대로
// 「이동 38분」이라고 말한다. 어림값(ESTIMATED)이면 그렇다고 붙인다 — 어림을 실제
// 소요시간처럼 그리면 사용자가 그 시간에 맞춰 움직이다 늦는다.
// S15P21E201-1119 — fromOrigin 은 그날 **첫 방문지**의 구간이다. 서버가 주는 순서 1번
// 구간은 장소와 장소 사이가 아니라 **출발지 → 첫 장소** 라서, 그냥 「이동」이라고 하면
// 어디서 오는 이동인지 알 수 없다. 이 구간이 하루 중 제일 길다(실측 38분·40분).
export function formatTravelLabel(item: ItineraryItemDto, tx: Tx, fromOrigin = false): string | null {
  if (item.travelDurationMin == null) return null;
  const minutes = Math.round(item.travelDurationMin);
  const estimated = item.travelDataStatus === 'ESTIMATED';
  if (fromOrigin) {
    return estimated
      ? tx(`출발지에서 ${minutes}분 (어림)`, `${minutes}m from start (est.)`)
      : tx(`출발지에서 ${minutes}분`, `${minutes}m from start`);
  }
  return estimated
    ? tx(`이동 ${minutes}분 (어림)`, `${minutes}m travel (est.)`)
    : tx(`이동 ${minutes}분`, `${minutes}m travel`);
}

export type ItineraryStat = { key: string; value: string; label: string };

// 🔴 값이 없으면 칸을 만들지 않는다. 비용(estimatedCostKrw)과 도보(walkingMeters)는 서버에
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
