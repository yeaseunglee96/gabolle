/**
 * 일정의 한 구간을 경로 상세로 여는 주소 — S15P21E201-1831.
 *
 * 일정에는 「이동 25분」만 있고 무엇을 타는지·택시로 얼마인지 볼 곳이 없었다. 경로 상세(app/route-detail.tsx)는
 * 대중교통·택시·도보를 나란히 보여주는데 어디서도 열리지 않았다. 구간 줄을 누르면 여기서 만든 주소로 간다.
 *
 * 들어오는 구간이다 — 앞 곳에서 이 곳으로. 그날 첫 곳이면 하루 시작(첫날 출발지, 둘째 날부터 숙소)에서 온다.
 * 🔴 좌표를 하나라도 모르면 null — 화면은 누를 수 없는 글자로 둔다. 모르는 좌표를 0 으로 채우면 기니만으로 길을 찾는다.
 */
import type { DayStart, ItineraryItemDto } from '@/plan/itinerary';

type Tx = (ko: string, en: string) => string;

export type LegRouteParams = {
  originLat: string;
  originLng: string;
  originName: string;
  destLat: string;
  destLng: string;
  destName: string;
  destPlaceId: string;
};

const known = (value: number | null | undefined): value is number => typeof value === 'number' && Number.isFinite(value);

export function legRouteParams(
  items: readonly ItineraryItemDto[],
  index: number,
  start: DayStart | null | undefined,
  nameOf: (item: ItineraryItemDto) => string,
  tx: Tx,
): LegRouteParams | null {
  const to = items[index];
  if (!to || !known(to.lat) || !known(to.lng)) return null;
  const prev = index > 0 ? items[index - 1] : null;
  const from = prev
    ? (known(prev.lat) && known(prev.lng) ? { lat: prev.lat, lng: prev.lng, name: nameOf(prev) } : null)
    : start && known(start.lat) && known(start.lng)
      ? { lat: start.lat, lng: start.lng, name: start.label ?? (start.kind === 'LODGING' ? tx('숙소', 'Your stay') : tx('출발지', 'Starting point')) }
      : null;
  if (!from) return null;
  return {
    originLat: String(from.lat),
    originLng: String(from.lng),
    originName: from.name,
    destLat: String(to.lat),
    destLng: String(to.lng),
    destName: nameOf(to),
    destPlaceId: to.placeId,
  };
}
