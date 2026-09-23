// 여행 페이지(추천 코스 + 일정 통합) — 화면과 떨어진 계산만 모은다 (S15P21E201-1535).
//
// 시안: frontend/docs/design_handoff_trip_page/ (README · TripPageDesktop.dc.html)
// 🔴 여기에는 그리는 코드를 두지 않는다. 숫자를 짓는 규칙이 화면 안에 섞이면 시험이 화면을
//    통째로 띄워야만 그 규칙을 볼 수 있다.
import type { MapRouteLayer } from '@/map/RouteMap';
import { legKey, type LegPath } from '@/map/courseRoutePaths';
import type { MapStop } from '@/map/types';
import type { ItineraryItemDto } from '@/plan/itinerary';

/** 「HH:mm」 을 넘어 쓰는 분 — 시각 문자열(ISO)에서 그날 0시부터 센 분. 못 읽으면 null. */
export function minutesOfDay(startsAt: string | null | undefined): number | null {
  if (!startsAt) return null;
  const match = /T(\d{2}):(\d{2})/.exec(startsAt);
  if (!match) return null;
  return Number(match[1]) * 60 + Number(match[2]);
}

/**
 * 한 곳에 머무는 시간(분) — 시안 README 「머무름 = 다음 시각 − 현재 시각 − 다음 구간 이동」.
 *
 * 🔴 **마지막 곳은 null** 이다(화면이 「마지막 장소」라고 적는다). 다음 곳이 없으니 뺄 것이 없다.
 * 🔴 **다음 구간 이동 시간을 모르면 null** 이다. 0 으로 치고 빼면 이동 시간이 머무는 시간으로
 *    둔갑한다 — 「1시간 46분 머무름」이 사실은 「46분 이동 + 1시간 머무름」일 수 있다.
 * 계산이 0 이하로 나오면(시각이 겹치거나 거꾸로) 역시 null — 음수 머무름은 없다.
 */
export function stayMinutes(items: ItineraryItemDto[], index: number): number | null {
  const current = items[index];
  const next = items[index + 1];
  if (!current || !next) return null;
  const from = minutesOfDay(current.startsAt);
  const to = minutesOfDay(next.startsAt);
  if (from === null || to === null || next.travelDurationMin == null) return null;
  const stay = to - from - Math.round(next.travelDurationMin);
  return stay > 0 ? stay : null;
}

/** 「1시간 46분」·「46분」·「2시간」 — 한 시간이 넘으면 나눗셈을 읽는 사람에게 넘기지 않는다. */
export function formatDuration(minutes: number, tx: (ko: string, en: string) => string): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return tx(`${rest}분`, `${rest} min`);
  if (rest === 0) return tx(`${hours}시간`, `${hours} h`);
  return tx(`${hours}시간 ${rest}분`, `${hours} h ${rest} min`);
}

/** 「22.0만원」 — 만원 단위 한 자리. 코스 알약과 머리말이 같은 모양을 쓴다. */
export function formatManwon(krw: number, tx: (ko: string, en: string) => string): string {
  const man = Math.round(krw / 1000) / 10;
  return tx(`${man.toFixed(1)}만원`, `₩${krw.toLocaleString()}`);
}

export type DayMap = {
  /** 지도에 찍을 정차지 — id 는 **일정 항목 id** 그대로다. 카드와 지도가 같은 id 로 서로를 켠다. */
  stops: MapStop[];
  /** 구간 경로를 물을 모양 — useCourseRoutePaths 가 받는 그대로 */
  days: Array<{ day: number; stops: MapStop[] }>;
};

/**
 * 하루치 일정 항목 → 지도 정차지.
 *
 * 🔴 좌표를 모르는 곳은 **건너뛰고 번호도 안 준다**(courseMapLayers 와 같은 규칙).
 *    0,0 으로 찍으면 기니만 바다 한가운데에 점이 생긴다(ItineraryItemDto.lat 주석).
 *    번호는 카드의 번호와 같게 «그날 몇 번째 곳» 을 쓴다 — 좌표 없는 곳을 건너뛰어도
 *    3번 카드가 지도에서 2번이 되지 않게.
 */
export function dayMap(items: ItineraryItemDto[], dayNumber: number): DayMap {
  const stops: MapStop[] = [];
  items.forEach((item, index) => {
    if (typeof item.lat !== 'number' || typeof item.lng !== 'number') return;
    stops.push({ id: item.id, number: index + 1, name: item.title, latitude: item.lat, longitude: item.lng });
  });
  return { stops, days: stops.length ? [{ day: dayNumber, stops }] : [] };
}

/** 정차지 사이 선 — 받아 온 길이 있으면 그 길, 없으면 곧은 점선(estimated). */
export function dayRoutes(map: DayMap, dayNumber: number, lineColor: string, legs: Record<string, LegPath>): MapRouteLayer[] {
  const routes: MapRouteLayer[] = [];
  for (let i = 0; i + 1 < map.stops.length; i += 1) {
    const leg = legs[legKey(dayNumber, i)];
    routes.push({
      id: `day-${dayNumber}-leg-${i}`,
      color: lineColor,
      stops: [map.stops[i], map.stops[i + 1]],
      path: leg?.path,
      estimated: leg ? leg.estimated : true,
    });
  }
  return routes;
}
