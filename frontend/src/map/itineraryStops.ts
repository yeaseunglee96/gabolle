// — 지도에 내 일정을 띄운다.
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
    // (장소 이름 병기 규칙,.
    name: tx(seed.nameKo, seed.nameEn ?? seed.nameKo),
    latitude: seed.latitude,
    longitude: seed.longitude,
  }));
}

function hasCoordinates(place: Place | null): place is Place {
  return place !== null && Number.isFinite(place.lat) && Number.isFinite(place.lng);
}

/** 일정 번호로 그 일정의 정류지를 날짜별로 가져온다. */
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
        // 지도의 번호는 지도에 실제로 찍힌 순서다. 빠진 곳이 있어도 1,2,4 처럼
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
