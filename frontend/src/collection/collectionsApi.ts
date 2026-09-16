// 부슐랭을 서버에 저장한다 (S15P21E201-1071).
//
// 🔴 2026-09-16 정정. `CollectionProvider` 머리말의 "서버가 없어도 되는 기능이라 기기에만
// 저장한다" 는 옛말이 됐다. 서버가 생겼다(마이그레이션 V20260915130000__collection.sql):
//
//   GET    /api/v1/me/collections                      { items: [...], count }
//   POST   /api/v1/me/collections                      { name, description }
//   PATCH  /api/v1/me/collections/{id}                 { name, description }
//   DELETE /api/v1/me/collections/{id}
//   POST   /api/v1/me/collections/{id}/items           { kind, placeId, ... }
//   PATCH  /api/v1/me/collections/{id}/items/{itemId}
//   DELETE /api/v1/me/collections/{id}/items/{itemId}
//
// 🔴 **기기에 쌓인 것을 버리지 않는다.** 서버 목록과 합치고, 기기에만 있던 리스트는 한 번
// 서버로 올린다. 버리면 사용자는 **아무도 지운 적 없는 리스트가 지워졌다**고 느낀다.
// 하트(S15P21E201-1013)가 이미 같은 문제를 겪었고 같은 방식으로 풀었다 —
// `src/discovery/savedPlaces.ts` 가 본보기다.

import { apiRequest, ApiClientError } from '@/api/client';

import type { CollectionList, CollectionPlace } from './CollectionProvider';

export type ServerCollectionItem = {
  itemId: string;
  kind: string;
  placeId: string | null;
  name: string;
  locality: string | null;
  lat: number | null;
  lng: number | null;
  photoUrl: string | null;
  note: string | null;
  position: number;
};

export type ServerCollection = {
  collectionId: string;
  name: string;
  description: string | null;
  count: number;
  items: ServerCollectionItem[];
  updatedAt: string;
};

/** 기기에 있던 자료 한 벌. `CollectionProvider` 가 들고 있는 것과 같은 모양이다. */
export type DeviceCollections = {
  lists: CollectionList[];
  places: Record<string, CollectionPlace>;
};

export type CollectionsLoadResult =
  | { state: 'success'; data: DeviceCollections; uploaded: number }
  | { state: 'device-only'; data: DeviceCollections; reason: 'anonymous' | 'unreachable' };

/**
 * 서버 리스트를 기기 모양으로 옮긴다.
 *
 * 🔴 서버 항목에는 `name` 이 반드시 있고 `placeId` 는 없을 수 있다(`kind` 가 장소가 아닌
 * 것도 담기게 열려 있다). 기기 쪽은 장소 id 를 열쇠로 쓰므로, 장소 id 가 없으면 서버가 준
 * 항목 id 를 열쇠로 쓴다 — 지어내지 않고 서버가 준 것만 쓴다.
 */
export function serverToDevice(collections: ServerCollection[]): DeviceCollections {
  const places: Record<string, CollectionPlace> = {};
  const lists: CollectionList[] = collections.map((collection) => {
    const placeIds: string[] = [];
    for (const item of [...collection.items].sort((a, b) => a.position - b.position)) {
      const key = item.placeId ?? item.itemId;
      placeIds.push(key);
      places[key] = {
        id: key,
        name: item.name,
        // 서버는 분류를 안 준다. 🔴 null 로 둔다 — 없는 값을 지어내지 않는다.
        category: null,
        locality: item.locality ?? null,
        photoUri: item.photoUrl ?? null,
        note: item.note ?? null,
        // 언제 담았는지는 항목마다 안 온다. 리스트가 마지막으로 바뀐 시각을 쓴다.
        addedAt: collection.updatedAt,
        lat: item.lat ?? null,
        lng: item.lng ?? null,
      };
    }
    return {
      id: collection.collectionId,
      name: collection.name,
      description: collection.description ?? null,
      placeIds,
      createdAt: collection.updatedAt,
    };
  });
  return { lists, places };
}

/**
 * 기기 것과 서버 것을 합친다.
 *
 * 🔴 **같은 이름의 리스트는 하나로 본다.** 기기와 서버에 같은 이름이 있으면 서버 쪽을 쓰고,
 * 기기에만 있던 장소는 그 리스트에 더한다. 이름으로 맞추는 이유는 기기 리스트의 id 가
 * 이 기기에서 만든 임의값이라 서버 id 와 맞출 길이 없기 때문이다.
 *
 * 🔴 **어느 쪽도 버리지 않는다.** 애매하면 남기는 쪽으로 간다 — 잘못 남기면 사용자가 지울
 * 수 있지만, 잘못 지우면 되돌릴 방법이 없다.
 */
export function mergeCollections(device: DeviceCollections, server: DeviceCollections) {
  const byName = new Map(server.lists.map((list) => [list.name.trim(), list]));
  const merged: CollectionList[] = [...server.lists];
  const onlyOnDevice: CollectionList[] = [];

  for (const deviceList of device.lists) {
    const match = byName.get(deviceList.name.trim());
    if (!match) { merged.push(deviceList); onlyOnDevice.push(deviceList); continue; }
    // 같은 이름이 양쪽에 있다 — 서버 리스트에 기기에만 있던 장소를 더한다.
    const extra = deviceList.placeIds.filter((id) => !match.placeIds.includes(id));
    if (extra.length > 0) {
      const index = merged.findIndex((list) => list.id === match.id);
      merged[index] = { ...match, placeIds: [...match.placeIds, ...extra] };
    }
  }

  // 장소는 서버 것을 우선하되, 기기에만 있던 것은 그대로 남긴다.
  const places: Record<string, CollectionPlace> = { ...device.places, ...server.places };
  return { merged: { lists: merged, places }, onlyOnDevice };
}

async function createOnServer(list: CollectionList, places: Record<string, CollectionPlace>, accessToken: string) {
  const created = await apiRequest<ServerCollection>('/api/v1/me/collections', {
    method: 'POST',
    accessToken,
    body: { name: list.name, description: list.description ?? null },
  });
  for (const placeId of list.placeIds) {
    const place = places[placeId];
    if (!place) continue;
    try {
      await apiRequest<unknown>(`/api/v1/me/collections/${encodeURIComponent(created.collectionId)}/items`, {
        method: 'POST',
        accessToken,
        body: {
          kind: 'PLACE',
          placeId,
          name: place.name,
          locality: place.locality ?? null,
          lat: place.lat ?? null,
          lng: place.lng ?? null,
          note: place.note ?? null,
        },
      });
    } catch {
      // 🔴 장소 하나가 안 올라가도 리스트 전체를 버리지 않는다. 그 장소는 기기에 남아
      // 있고, 다음에 다시 올릴 기회가 있다.
    }
  }
  return created;
}

/**
 * 부슐랭을 불러온다 — 로그인했으면 서버와 합치고, 아니면 기기 것을 그대로 준다.
 *
 * 🔴 서버를 못 물어봤을 때 **빈 목록을 주지 않는다.** 기기 것을 그대로 준다. 리스트가
 * 통째로 사라진 화면을 보여주는 것보다 낫고, 그건 사실도 아니다.
 */
export async function loadCollections(device: DeviceCollections, accessToken: string | null): Promise<CollectionsLoadResult> {
  if (!accessToken) return { state: 'device-only', data: device, reason: 'anonymous' };

  let server: DeviceCollections;
  try {
    const page = await apiRequest<{ items: ServerCollection[]; count: number }>('/api/v1/me/collections', { accessToken });
    if (!Array.isArray(page?.items)) return { state: 'device-only', data: device, reason: 'unreachable' };
    server = serverToDevice(page.items);
  } catch (error) {
    // 인증이 끊긴 것과 서버가 안 되는 것을 가르지 않는다 — 둘 다 "지금은 기기 것으로
    // 보여준다" 가 맞는 답이다.
    if (error instanceof ApiClientError || error instanceof Error) {
      return { state: 'device-only', data: device, reason: 'unreachable' };
    }
    return { state: 'device-only', data: device, reason: 'unreachable' };
  }

  const { merged, onlyOnDevice } = mergeCollections(device, server);
  let uploaded = 0;
  for (const list of onlyOnDevice) {
    try {
      await createOnServer(list, merged.places, accessToken);
      uploaded += 1;
    } catch {
      // 못 올렸으면 기기에 그대로 둔다. 다음에 다시 올린다.
    }
  }
  return { state: 'success', data: merged, uploaded };
}
