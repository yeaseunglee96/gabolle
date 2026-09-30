// 여행 페이지(추천 코스 + 일정 통합) — 화면과 떨어진 계산만 모은다 (S15P21E201-1535).
//
// 시안: frontend/docs/design_handoff_trip_page/ (README · TripPageDesktop.dc.html)
// 🔴 여기에는 그리는 코드를 두지 않는다. 숫자를 짓는 규칙이 화면 안에 섞이면 시험이 화면을
//    통째로 띄워야만 그 규칙을 볼 수 있다.
import type { MapRouteLayer } from '@/map/RouteMap';
import { legKey, type LegPath } from '@/map/courseRoutePaths';
import type { RouteGrading } from '@/map/routeGrading';
import { slopeSegments } from '@/map/slopeGrades';
import type { MapStop } from '@/map/types';
import type { DayReturnLeg, DayStart, ItineraryDto, ItineraryItemDto } from '@/plan/itinerary';
import { lodgingAreaCodeOf } from '@/plan/origins';
import type { PlanDraft } from '@/plan/PlanProvider';

/**
 * 두 입구(추천·일정)가 어느 판을 여나 — 넓은 화면(1단계) · 폰(2단계) · 지금까지의 화면.
 *
 * `desktop` 은 useLayout 의 판정 그대로다(폴드 펼침 가로 포함). 그 밖은 전부 폰 판이다.
 * 🔴 전에는 「폭 1024 에 못 미치는 태블릿」이 지금까지의 화면으로 빠졌다 — 폴드를 펼치면 옛 화면이 떴다
 *    (S15P21E201-1563). 이제 그런 칸이 없다 — 판정이 둘 중 하나만 낸다.
 * 🔴 `?classic=1` 은 넓은 화면·폰 모두 지금까지의 화면이다 — 새 판의 ⋯ 「일정 편집」이 순서·고정·제외·
 *    다시 계산·되돌리기를 하러 이 길로 온다. 편집은 새 판에 자리가 없다.
 */
export function tripPageKind(desktop: boolean, classic: boolean): 'desktop' | 'mobile' | 'classic' {
  if (classic) return 'classic';
  return desktop ? 'desktop' : 'mobile';
}

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
 * 🔴 서버가 끝 시각(endsAt)을 주면 **끝 − 시작**이다(S15P21E201-1668). 곳 사이에 빈 시각이 생긴 뒤로는 위 식이
 *    자유 시간까지 머무름으로 센다 — 「머무름 약 2시간 20분」이 사실은 「머무름 1시간 30분 + 자유 시간 50분」이다.
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
  const until = minutesOfDay(current.endsAt);
  if (from !== null && until !== null) return until - from > 0 ? until - from : null;
  const to = minutesOfDay(next.startsAt);
  if (from === null || to === null || next.travelDurationMin == null) return null;
  const stay = to - from - Math.round(next.travelDurationMin);
  return stay > 0 ? stay : null;
}

/** 이만큼 비어야 「자유 시간」 줄을 그린다(조율 세션 결정, 2026-09-25). 서버는 문턱 없이 1분 단위로 남긴다. */
export const FREE_TIME_MIN_MINUTES = 30;

/**
 * 이번 곳(index)을 떠나 다음 곳에 닿기까지 남는 시간(분) — 「자유 시간 · n분」 (S15P21E201-1668, 백엔드 계약 S15P21E201-1667).
 *
 *   빈 시각 = 다음 곳 시작 − 이번 곳 끝(endsAt) − 다음 곳까지 이동
 *
 * 이동 시간을 모르면 0 으로 뺀다 — 서버도 그때는 이동 0분으로 놓고 시각을 깔았다.
 * 🔴 **끝 시각을 모르면 null** — 지금 운영 서버는 이 칸을 안 보낸다. 끝 시각 없이 「다음 시각 − 이동」으로
 *    짐작하면 머무는 시간이 자유 시간으로 둔갑한다. 새 항목 종류는 없다 — 빈 시각은 두 시각의 차이로만 드러난다.
 * 30분 미만·음수·마지막 곳 뒤(숙소로 돌아가는 길과 섞여 있다)도 null.
 */
export function freeTimeMinutes(items: ItineraryItemDto[], index: number): number | null {
  const current = items[index];
  const next = items[index + 1];
  if (!current || !next) return null;
  const leave = minutesOfDay(current.endsAt);
  const arrive = minutesOfDay(next.startsAt);
  if (leave === null || arrive === null) return null;
  const free = arrive - leave - Math.round(next.travelDurationMin ?? 0);
  return free >= FREE_TIME_MIN_MINUTES ? free : null;
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

/**
 * 하루 끝에 돌아가는 구간 — 그날 마지막 정차지 → 숙소(마지막 날은 출발지). S15P21E201-1567.
 *
 * 길을 받아 오는 열쇠(legKey)가 정차지 구간과 안 겹치게 날 번호에 {@link RETURN_DAY_OFFSET} 을 더한다.
 * 돌아갈 자리를 모르거나(returnLeg 없음) 좌표 있는 정차지가 없으면 null — 선을 지어내지 않는다.
 */
export const RETURN_DAY_OFFSET = 1000;

/** 돌아가는 구간이 닿는 자리(숙소·출발지)의 정차지 id — 길을 걷기로 물을지 고르는 열쇠({@link walkIntoStopIds})가 이것이다. */
export function returnAnchorId(dayNumber: number): string {
  return `return-${dayNumber}`;
}

/**
 * @param approximate 돌아가는 자리가 동네 중심이라 정확한 숙소를 모른다 — 길을 받아 오지 않고 곧은 점선으로 그린다.
 */
export type ReturnTrip = { day: { day: number; stops: MapStop[] }; anchor: MapStop; kind: DayReturnLeg['kind']; approximate: boolean };

export function returnTrip(map: DayMap, dayNumber: number, returnLeg: DayReturnLeg | null | undefined): ReturnTrip | null {
  const last = map.stops[map.stops.length - 1];
  if (!returnLeg || !last) return null;
  const anchor: MapStop = { id: returnAnchorId(dayNumber), number: 0, name: returnLeg.label ?? '', latitude: returnLeg.lat, longitude: returnLeg.lng };
  // 🔴 숙소를 동네(「해운대」)로 골랐으면 서버는 동네 중심을 숙소 자리로 쓴다. 해운대의 그 점은 해수욕장 모래사장
  //    위라서, 카카오 «자동차» 길찾기가 거기 닿으려고 일방통행을 돌아 동백섬까지 갔다 왔다(사용자 폰 화면 2026-09-24,
  //    S15P21E201-1570). 정확한 숙소를 모르는데 길을 지어내지 않는다 — 곧은 점선(어림)이다.
  const approximate = returnLeg.kind === 'LODGING' && lodgingAreaCodeOf(returnLeg.lat, returnLeg.lng) != null;
  return { day: { day: RETURN_DAY_OFFSET + dayNumber, stops: [last, anchor] }, anchor, kind: returnLeg.kind, approximate };
}

/** 하루 시작 구간의 열쇠 — {@link RETURN_DAY_OFFSET} 과 같은 까닭으로 날 번호에 더한다. */
export const START_DAY_OFFSET = 2000;

/**
 * 하루 시작 구간 — 그날 출발점(첫날 출발지, 둘째 날부터 숙소) → 첫 정차지. S15P21E201-1580.
 * 돌아가는 구간과 모양이 같아서 선은 {@link returnRoute} 로 그리고, 동네 숙소면 같은 까닭으로 곧은 점선이다.
 * 출발점을 모르거나(start 없음) 좌표 있는 정차지가 없으면 null.
 */
export function startTrip(map: DayMap, dayNumber: number, start: DayStart | null | undefined): ReturnTrip | null {
  const first = map.stops[0];
  if (!start || !first) return null;
  const anchor: MapStop = { id: `start-${dayNumber}`, number: 0, name: start.label ?? '', latitude: start.lat, longitude: start.lng };
  const approximate = start.kind === 'LODGING' && lodgingAreaCodeOf(start.lat, start.lng) != null;
  return { day: { day: START_DAY_OFFSET + dayNumber, stops: [anchor, first] }, anchor, kind: start.kind, approximate };
}

/**
 * 돌아가는 구간(과 하루 시작 구간)의 선 — 받아 온 길이 있으면 그 길, 없으면 곧은 점선.
 *
 * 🔴 걷기로 받은 길이면 경사·그늘 조각(pieces)과 고른 조건(grading)을 정차지 사이 선({@link dayRoutes})과 똑같이 지도에 넘긴다
 *    (S15P21E201-1899). 전에는 길(path)만 넘겨서, 조건 색으로 칠한 경로 한가운데 출발지에서 첫 곳까지의 구간만 남색 한 가지로 남았다 —
 *    그 길은 걷기로 조각까지 받아 놓고 그림에서만 버렸다(운영 실측: 조각 10개, 그늘 6개).
 *    조각이 없으면(자동차·대중교통·어림) 지도가 전처럼 이 선의 자기 색 한 가지로 그린다.
 */
export function returnRoute(back: ReturnTrip, lineColor: string, legs: Record<string, LegPath>, grading?: RouteGrading): MapRouteLayer {
  const leg = back.approximate ? undefined : legs[legKey(back.day.day, 0)];
  return {
    id: `return-${back.day.day}`,
    color: lineColor,
    stops: back.day.stops,
    path: leg?.path,
    estimated: leg ? leg.estimated : true,
    pieces: leg?.pieces,
    ...(grading ? { grading } : {}),
  };
}

/**
 * 길을 걷기로 받을 정차지 id — 「들어오는 구간이 걷기」인 일정 항목과, 걷는 날의 돌아가는 구간의 도착 자리.
 *
 * 항목은 서버가 walkingMeters 를 실어 준 곳이다(백엔드: 그 구간이 도보가 아니면 null).
 *
 * 🔴 돌아가는 구간(returnLeg)은 서버가 이동수단을 안 싣는다. 하지만 서버는 한 여행의 모든 구간을 같은 이동수단으로 잰다
 *    (ItineraryLegPlanner — 여행이 고른 첫 이동수단, 없으면 걷기)이고 walkingMeters 는 걷기일 때만 찬다. 그러니 그날 걷는 항목이 하나라도
 *    있으면 돌아가는 구간의 시간도 걸어서 잰 것이다 — 그 길을 이동수단 없이 물으면 서버가 자동차 길(조각 없음)을 답해서, 「6분 걸어서」라고
 *    적힌 구간에 찻길이 그려지고 경사·그늘 색도 못 칠했다(S15P21E201-1899). 걷는 항목이 없으면(자동차·대중교통 여행, 거리를 모르는 날)
 *    전처럼 이동수단 없이 묻는다.
 */
export function walkIntoStopIds(days: ReadonlyArray<{ items: ReadonlyArray<Pick<ItineraryItemDto, 'id' | 'walkingMeters'>> }>): Set<string> {
  const ids = new Set<string>();
  days.forEach((day, index) => {
    let walking = false;
    for (const item of day.items) {
      if (item.walkingMeters == null) continue;
      ids.add(item.id);
      walking = true;
    }
    if (walking) ids.add(returnAnchorId(index + 1));
  });
  return ids;
}

/**
 * 일정 응답에 이미 실려 온 구간 길 — 들어오는 곳의 travelPath + travelPieces. 이것이 있는 구간은 길을 다시 묻지 않고
 * 이것으로 경사·계단을 칠한다(useCourseRoutePaths 의 known).
 *
 * 🔴 조각이 있는 구간만 쓴다. 조각 없는 길까지 여기서 쓰면 지금 받아 오는 길(걷기·자동차)과 모양이 달라진다 — 그건 이 일이 아니다.
 * 🔴 지도의 이웃 정차지가 일정의 이웃 항목일 때만 쓴다. 좌표 없는 곳을 건너뛰면 지도의 두 점 사이 구간은 그 길이 아니다.
 * 🔴 조각 번호가 길 밖이면(slopeSegments 가 null) 버린다 — 그때는 전처럼 길을 받아 온다.
 */
export function itineraryLegs(items: ItineraryItemDto[], map: DayMap, dayNumber: number): Record<string, LegPath> {
  const legs: Record<string, LegPath> = {};
  for (let i = 0; i + 1 < map.stops.length; i += 1) {
    const from = map.stops[i];
    const to = map.stops[i + 1];
    if (to.number !== from.number + 1) continue;
    const item = items[to.number - 1];
    if (!item || item.id !== to.id || !Array.isArray(item.travelPath) || !item.travelPieces?.length) continue;
    const valid = item.travelPath.every((point) => Array.isArray(point) && Number.isFinite(point[0]) && Number.isFinite(point[1]));
    if (!valid || item.travelPath.length < 2) continue;
    // 서버는 [경도, 위도] 순서다(courseRoutePaths.ts 와 같은 까닭 — S15P21E201-1567).
    const path = item.travelPath.map(([lng, lat]) => ({ latitude: lat, longitude: lng }));
    if (slopeSegments(path, item.travelPieces) == null) continue;
    legs[legKey(dayNumber, i)] = { path, estimated: false, pieces: item.travelPieces };
  }
  return legs;
}

/**
 * 이 여행이 경로 선을 무엇으로 칠하나 — 사용자가 고른 «가파른 경사 피하기»·«그늘 많은 곳 우선» (S15P21E201-1896).
 *
 * 🔴 서버가 알려 준 값(일정 응답의 slopeAvoid · shadePrefer)이 **먼저**다. 칸이 없을 때만(옛 서버) 기기의 초안
 *    (slopeConstraint === 'AVOID' · shadePreference === 'PREFER')으로 대신한다. 서버가 false 라고 답했으면 초안이 무엇이든 false 다 —
 *    초안은 «지금 만드는 새 여행» 의 답이라, 서버가 아는 이 여행의 답과 다를 수 있다.
 * 🔴 둘 다 없으면(초안도 없으면) 조건을 안 고른 것이다 — 경로는 자기 색 한 가지로 그린다.
 */
export function routeGradingOf(
  itinerary: Pick<ItineraryDto, 'slopeAvoid' | 'shadePrefer'> | null | undefined,
  draft: Pick<PlanDraft, 'slopeConstraint' | 'shadePreference'> | null | undefined,
): RouteGrading {
  return {
    slope: typeof itinerary?.slopeAvoid === 'boolean' ? itinerary.slopeAvoid : draft?.slopeConstraint === 'AVOID',
    shade: typeof itinerary?.shadePrefer === 'boolean' ? itinerary.shadePrefer : draft?.shadePreference === 'PREFER',
  };
}

/**
 * 정차지 사이 선 — 받아 온 길이 있으면 그 길, 없으면 곧은 점선(estimated).
 * grading = 고른 조건. 걷기로 받은 구간의 조각을 이 조건대로 칠한다(RouteMap 이 routeGrading.ts 로).
 */
export function dayRoutes(map: DayMap, dayNumber: number, lineColor: string, legs: Record<string, LegPath>, grading?: RouteGrading): MapRouteLayer[] {
  const routes: MapRouteLayer[] = [];
  for (let i = 0; i + 1 < map.stops.length; i += 1) {
    const leg = legs[legKey(dayNumber, i)];
    routes.push({
      id: `day-${dayNumber}-leg-${i}`,
      color: lineColor,
      stops: [map.stops[i], map.stops[i + 1]],
      path: leg?.path,
      estimated: leg ? leg.estimated : true,
      // 걷기로 받은 구간의 경사·그늘 조각(S15P21E201-1658 · -1896).
      pieces: leg?.pieces,
      ...(grading ? { grading } : {}),
    });
  }
  return routes;
}
