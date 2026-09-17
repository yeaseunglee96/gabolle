// S15P21E201-1113 — 지도에 내 일정을 띄운다.
//
// 이 화면은 여태 고정된 예시 일정(map.tsx 의 DAY_STOPS_SEED)만 그렸다. 막고 있던 것은
// **일정 응답에 좌표가 없다**는 것이었는데, 항목에 장소 번호(placeId)가 들어 있고
// 장소 상세(GET /api/v1/places/{placeId})에는 lat·lng 가 있다. 그래서 장소를 한 번 더
// 물으면 된다 — 서버를 고치지 않고 지금 이을 수 있다.
//
// 🔴 호출이 장소 수만큼 늘어난다. 하루 대여섯 곳 규모라 지금은 보이지 않지만, 같은 장소가
// 여러 날에 나오면 두 번 묻지 않도록 한 번 받은 것은 아래에서 재사용한다. 일정 응답이
// 좌표를 직접 실어 주게 되면(백엔드에 요청해 둔 tripId 와 같은 자리) 이 파일은 지워도 된다.
//
// 🔴 좌표를 못 받은 장소를 **조용히 빼지 않는다.** 몇 곳이 빠졌는지 함께 돌려주고 화면이
// 그것을 말한다. 지도에 네 곳이 찍혔는데 일정에는 여섯 곳인 것을 사용자가 모르면,
// 「이 지도가 내 일정 전부」라고 믿게 된다.
import { getPlace, type Place } from '@/discovery/places';
import { loadItinerary } from '@/plan/itinerary';
import type { MapStop } from '@/map/types';

/** 화면이 언어를 고르기 전의 정류지 — map.tsx 의 MapStopSeed 와 같은 모양이다. */
export type ItineraryStopSeed = {
  id: string;
  number: number;
  nameKo: string;
  nameEn: string | null;
  latitude: number;
  longitude: number;
};

export type ItineraryDaySeed = {
  /** 1부터 센 날짜 번호. 「1일차」·「DAY 1」로 쓴다. */
  index: number;
  /** 서버가 준 날짜 문자열 그대로(YYYY-MM-DD). */
  date: string;
  stops: ItineraryStopSeed[];
};

export type ItineraryStopsResult =
  | { state: 'success'; days: ItineraryDaySeed[]; missingCount: number }
  | { state: 'unavailable'; message: string };

export function localizeItineraryStops(
  tx: (ko: string, en: string) => string,
  seeds: ItineraryStopSeed[],
): MapStop[] {
  return seeds.map((seed) => ({
    id: seed.id,
    number: seed.number,
    // 영문 이름이 없으면 괄호 없이 한국어 원문만 — 빈 문자열을 보여주지 않는다
    // (장소 이름 병기 규칙, S15P21E201-264).
    name: tx(seed.nameKo, seed.nameEn ?? seed.nameKo),
    latitude: seed.latitude,
    longitude: seed.longitude,
  }));
}

function hasCoordinates(place: Place | null): place is Place {
  return place !== null && Number.isFinite(place.lat) && Number.isFinite(place.lng);
}

/**
 * 일정 번호로 그 일정의 정류지를 날짜별로 가져온다.
 *
 * 실패하면 `unavailable` 을 돌려준다 — 화면은 그때 예시 일정으로 돌아가고 「샘플」이라고
 * 말한다. **못 가져온 것을 빈 지도로 그리지 않는다.**
 */
export async function loadItineraryStops(
  itineraryId: string,
  accessToken: string | null,
  signal?: AbortSignal,
): Promise<ItineraryStopsResult> {
  const loaded = await loadItinerary(itineraryId, accessToken);
  if (loaded.state !== 'success') return { state: 'unavailable', message: loaded.message };

  // 같은 장소를 두 번 묻지 않는다. 값이 아니라 약속(Promise)을 담아 두어야 동시에 나간
  // 요청도 하나로 합쳐진다.
  const pending = new Map<string, Promise<Place | null>>();
  const lookup = (placeId: string) => {
    const cached = pending.get(placeId);
    if (cached) return cached;
    const request = getPlace(placeId, signal).catch(() => null);
    pending.set(placeId, request);
    return request;
  };

  let missingCount = 0;
  const days: ItineraryDaySeed[] = [];

  for (const [dayIndex, day] of loaded.itinerary.days.entries()) {
    const places = await Promise.all(day.items.map((item) => lookup(item.placeId)));
    const stops: ItineraryStopSeed[] = [];
    day.items.forEach((item, itemIndex) => {
      const place = places[itemIndex];
      if (!hasCoordinates(place)) {
        missingCount += 1;
        return;
      }
      stops.push({
        id: item.id,
        // 지도의 번호는 **지도에 실제로 찍힌 순서**다. 빠진 곳이 있어도 1,2,4 처럼
        // 건너뛰지 않는다 — 몇 곳이 빠졌는지는 missingCount 로 따로 말한다.
        number: stops.length + 1,
        nameKo: place.nameKo,
        nameEn: place.nameEn,
        latitude: place.lat,
        longitude: place.lng,
      });
    });
    if (stops.length > 0) days.push({ index: dayIndex + 1, date: day.date, stops });
  }

  if (days.length === 0) {
    return { state: 'unavailable', message: '일정의 장소 위치를 한 곳도 받지 못했어요.' };
  }
  return { state: 'success', days, missingCount };
}
