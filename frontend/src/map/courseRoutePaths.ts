// 코스 지도에 **실제 길**을 그리기 위한 구간 경로 — S15P21E201-1455.
//
// 🔴 **왜 필요한가.** 지금 코스 지도는 장소를 직선으로 잇는다. 부산은 바다와 산이 사이에
//    있어서 그 선이 실제로는 못 가는 길이 되고, 화면은 「점선은 실제 길이 아니에요」라고
//    적어 두었지만 읽을 수 있는 그림은 아니었다.
//
// 🔴 **이동수단을 여기서 정하지 않는다.** 서버가 고르고, 응답의 `estimated` 로 그것이
//    실제 길인지 어림인지까지 말해 준다. 화면이 「자동차겠지」 하고 넘기면, 대중교통으로
//    다닐 사람에게 찻길을 그려 주게 된다. 모르는 것은 아는 쪽에 맡긴다.
//
// 🔴 **대중교통은 경로가 안 나온다.** 지하철·버스 경로를 주는 공개 API 가 아직 없다
//    (routeDirections.ts 주석). 그때는 `estimated: true` 로 와서 이 화면이 그 구간만
//    점선으로 남긴다 — 직선을 실제 경로인 척 그리지 않는다.
import { useEffect, useState } from 'react';
import { getRouteDirections } from '@/map/routeDirections';
import type { MapPathPoint, MapStop } from '@/map/types';

/** 한 구간의 결과. `estimated` 가 true 면 화면이 점선으로 그린다. */
export type LegPath = { path: MapPathPoint[]; estimated: boolean };

/** 구간 하나를 가리키는 열쇠 — 「그날, 그날 안에서 몇 번째 구간」. */
export function legKey(day: number, index: number) {
  return `${day}-${index}`;
}

// 같은 두 점을 두 번 묻지 않는다. 코스를 바꿔 가며 볼 때 같은 구간이 자주 겹친다.
const cache = new Map<string, Promise<LegPath | null>>();

function coordKey(a: MapStop, b: MapStop) {
  // 좌표를 다섯 자리로 줄여 센다 — 1m 남짓이라 같은 구간으로 봐도 된다.
  const r = (n: number) => n.toFixed(5);
  return `${r(a.latitude)},${r(a.longitude)}>${r(b.latitude)},${r(b.longitude)}`;
}

/**
 * 🔴 여기 요청은 **취소하지 않는다**(signal 을 안 넘긴다). 좌표 단위로 캐시에 담아 여러 화면·효과가 나눠 쓰는데,
 *    한 효과가 정리되며 취소하면 그 취소가 null(「길 없음」)로 캐시에 남아 **다른 효과도 끝까지 점선**이었다
 *    (운영 2026-09-24: 여행 페이지를 열 때 로그인 열쇠가 한 번 바뀌어 요청 3건이 142ms 에 취소 → 선 전부 점선,
 *    S15P21E201-1575). 효과는 결과만 버린다(useCourseRoutePaths 의 alive).
 * 🔴 못 받은 것(null)은 캐시에서 지운다 — 잠깐의 실패가 새로 고침 전까지 영원히 점선이 되지 않게.
 */
async function fetchLeg(a: MapStop, b: MapStop, accessToken: string | null): Promise<LegPath | null> {
  const key = coordKey(a, b);
  let pending = cache.get(key);
  if (!pending) {
    pending = getRouteDirections(
      { originLat: a.latitude, originLng: a.longitude, destLat: b.latitude, destLng: b.longitude },
      accessToken,
    ).then((result) => {
      // 🔴 못 받으면 null 이다. 빈 경로를 돌려주면 화면이 「길이 없다」와 「아직 못 받았다」를
      //    구분 못 하고, 둘 다 직선으로 떨어뜨리게 된다.
      if (result.state !== 'success') return null;
      const path = result.directions.path ?? [];
      if (path.length < 2) return null;
      // 🔴 서버는 [경도, 위도] 순서로 준다(RouteLeg.path — GeoJSON 과 같은 순서). 여기서 [위도, 경도]로 읽어서
      //    실제 도로 선이 위도 129 인 곳, 즉 지도 밖에 그려졌다 — 여행 페이지에 정차지 사이 선이 안 보이던
      //    원인(S15P21E201-1567, 운영 실측: 한 구간 점 48개가 전부 뒤바뀌어 있었다).
      return { path: path.map(([lng, lat]) => ({ latitude: lat, longitude: lng })), estimated: result.directions.estimated !== false };
    }).catch(() => null).then((leg) => {
      if (leg == null) cache.delete(key);
      return leg;
    });
    cache.set(key, pending);
  }
  return pending;
}

/** 시험·화면 새로 고침용. */
export function clearCourseRoutePathCache() { cache.clear(); }

/**
 * 코스의 모든 구간 경로를 받아 온다.
 *
 * 받는 동안에는 빈 객체를 돌려주므로 지도는 **먼저 직선으로 그려지고**, 경로가 오는 대로
 * 실선으로 바뀐다. 다 받을 때까지 지도를 비워 두지 않는다 — 빈 지도가 직선보다 낫지 않다.
 */
export function useCourseRoutePaths(
  days: Array<{ day: number; stops: MapStop[] }>,
  accessToken: string | null,
): Record<string, LegPath> {
  const [legs, setLegs] = useState<Record<string, LegPath>>({});
  // 좌표가 같으면 다시 안 부른다. 코스를 고를 때마다 새 배열이 와도 내용이 같으면 그대로 둔다.
  const shape = days.map((d) => `${d.day}:${d.stops.map((s) => coordKey(s, s)).join('|')}`).join(';');

  useEffect(() => {
    let alive = true;
    const wanted: Array<{ key: string; a: MapStop; b: MapStop }> = [];
    for (const day of days) {
      for (let i = 0; i + 1 < day.stops.length; i += 1) {
        wanted.push({ key: legKey(day.day, i), a: day.stops[i], b: day.stops[i + 1] });
      }
    }
    if (wanted.length === 0) { setLegs({}); return undefined; }

    void Promise.all(wanted.map(async (leg) => {
      const got = await fetchLeg(leg.a, leg.b, accessToken);
      return got ? ([leg.key, got] as const) : null;
    })).then((results) => {
      if (!alive) return;
      const next: Record<string, LegPath> = {};
      for (const entry of results) { if (entry) next[entry[0]] = entry[1]; }
      setLegs(next);
    });

    return () => { alive = false; };
    // shape 가 같으면 같은 코스다 — days 배열이 매번 새로 만들어져도 다시 안 부른다.
  }, [shape, accessToken]);

  return legs;
}
