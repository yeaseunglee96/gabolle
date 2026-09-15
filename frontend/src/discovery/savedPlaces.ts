// 저장한 장소 — 홈 캐러셀의 하트, 장소 상세의 "내 여행 후보에 저장", (tabs)/saved.tsx
// 셋이 같은 키를 공유한다.
//
// 🔴 2026-09-16 정정 (S15P21E201-1013). 여기 있던 "서버 저장이 아니라 기기를 바꾸면 안
// 보인다 — 서버 개념이 생기면 그때 옮긴다" 는 옛말이 됐다. 서버가 생겼다:
//   GET    /api/v1/me/saved-places            { items: [{ placeId, savedAt }], count }
//   PUT    /api/v1/me/saved-places/{placeId}  204 (몇 번을 보내도 같다)
//   DELETE /api/v1/me/saved-places/{placeId}  204 (안 켜져 있어도 204)
//
// 🔴 기기에 쌓인 것을 버리지 않는다. 서버 목록과 합치고(합집합), 기기에만 있던 것은 한 번
// 서버로 올린다. 기기 것을 버리면 사용자는 하트가 지워진 줄 안다 — 아무도 지운 적 없는데도.
//
// 🔴 데모 장소(아래 DEMO_PLACES)는 서버에 없는 이름표라 올리지 않는다. 올리면 404
// PLACE_NOT_FOUND 가 나고, 적재가 끝나 데모가 걷히면 이 예외도 저절로 사라진다.
import AsyncStorage from '@react-native-async-storage/async-storage';

import { apiRequest, ApiClientError } from '@/api/client';

export const SAVED_PLACES_KEY = 'gabolle.saved-home-places';

type SavedPlacesDto = { items: Array<{ placeId: string; savedAt: string }>; count: number };

async function readDeviceIds(): Promise<string[]> {
  try {
    const raw = await AsyncStorage.getItem(SAVED_PLACES_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((entry): entry is string => typeof entry === 'string') : [];
  } catch {
    return [];
  }
}

async function writeDeviceIds(ids: string[]): Promise<void> {
  try {
    await AsyncStorage.setItem(SAVED_PLACES_KEY, JSON.stringify([...new Set(ids)]));
  } catch {
    // 못 적어도 이번 실행의 선택은 화면에 남는다. 로그인해 있으면 서버에는 이미 갔다.
  }
}

export function isDemoPlaceId(placeId: string): boolean {
  return placeId in DEMO_PLACES;
}

async function putSavedPlace(placeId: string, accessToken: string): Promise<void> {
  try {
    await apiRequest<void>(`/api/v1/me/saved-places/${encodeURIComponent(placeId)}`, { method: 'PUT', accessToken });
  } catch (error) {
    // 서버에 없는 장소는 조용히 넘어간다 — 기기에 남아 있던 옛 이름표일 수 있다.
    if (error instanceof ApiClientError && error.status === 404) return;
    throw error;
  }
}

/** 기기 것과 서버 것을 합치고, 기기에만 있던 것은 서버로 한 번 올린다. */
export async function loadSavedPlaceIds(accessToken: string | null): Promise<string[]> {
  const device = await readDeviceIds();
  if (!accessToken) return device;
  let server: string[];
  try {
    const dto = await apiRequest<SavedPlacesDto>('/api/v1/me/saved-places', { accessToken });
    server = dto.items.map((item) => item.placeId);
  } catch {
    // 서버를 못 물어봤으면 기기 것으로 보여준다 — 하트가 통째로 사라지는 것보다 낫다.
    return device;
  }
  const onlyOnDevice = device.filter((placeId) => !server.includes(placeId) && !isDemoPlaceId(placeId));
  await Promise.all(onlyOnDevice.map((placeId) => putSavedPlace(placeId, accessToken).catch(() => undefined)));
  const merged = [...new Set([...device, ...server])];
  await writeDeviceIds(merged);
  return merged;
}

/** 하트를 켜고 끈다. 로그인 안 했으면 지금까지처럼 기기에만 남는다. */
export async function setSavedPlace(placeId: string, saved: boolean, accessToken: string | null): Promise<string[]> {
  const device = await readDeviceIds();
  const next = saved ? [...new Set([...device, placeId])] : device.filter((entry) => entry !== placeId);
  await writeDeviceIds(next);
  if (accessToken && !isDemoPlaceId(placeId)) {
    try {
      if (saved) await putSavedPlace(placeId, accessToken);
      else await apiRequest<void>(`/api/v1/me/saved-places/${encodeURIComponent(placeId)}`, { method: 'DELETE', accessToken });
    } catch {
      // 서버에 못 남겨도 기기의 선택은 지킨다. 다음에 목록을 불러올 때 다시 맞춰진다.
    }
  }
  return next;
}

// 홈 화면의 3개 데모 카드 — place 표가 비어 있어(-547 적재 전) 실제 API로는 아직 안 나온다.
// 그 밖의 placeId는 실제 API(getPlace)로 조회한다.
export const DEMO_PLACES = {
  haeundae: { titleKo: '해운대 해수욕장', titleEn: 'Haeundae Beach', subtitleKo: '푸른 바다와 도시가 만나는 곳', subtitleEn: 'Where the blue sea meets the city', image: require('../../assets/home/haeundae.png') },
  gwangalli: { titleKo: '광안리 해수욕장', titleEn: 'Gwangalli Beach', subtitleKo: '야경과 함께하는 해변 산책', subtitleEn: 'A beach walk under the night view', image: require('../../assets/home/gwangalli.png') },
  gamcheon: { titleKo: '감천문화마을', titleEn: 'Gamcheon Culture Village', subtitleKo: '형형색색 감성 골목 여행', subtitleEn: 'A colorful walk through winding alleys', image: require('../../assets/home/gamcheon.png') },
} as const;
