// 부슐랭 목록의 서버 저장·조회.

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

/** 서버가 받아 주는 글자 수. 넘기면 400 이라 올릴 방법이 없다. */
export const COLLECTION_LIMITS = {
  name: 100,
  description: 500,
  itemName: 200,
  locality: 100,
  note: 500,
  /**
   * 카테고리만 서버 상한이 아니라 우리가 정한 값이다. 항목을 올릴 때 카테고리는 안 보내서
   * 400 이 날 일이 없지만, 상한이 없는 입력칸을 하나만 남겨 두면 다음 사람이 그것을 규칙으로
   * 읽는다. 지역과 같은 자리의 값이라 같은 수를 쓴다.
   */
  category: 100,
} as const;

/** 올릴 수 없는 리스트인가. 올릴 수 있으면 `null`. */
export type UploadBlockReason = 'name-too-long' | 'description-too-long';

/** 보내 보기 전에 서버가 거절할 것을 알 수 있는 경우를 가른다. */
export function uploadBlockReason(list: CollectionList): UploadBlockReason | null {
  if (codePoints(list.name.trim()) > COLLECTION_LIMITS.name) return 'name-too-long';
  if (codePoints(list.description?.trim() ?? '') > COLLECTION_LIMITS.description) return 'description-too-long';
  return null;
}

/** 서버와 같은 방식으로 센다. */
function codePoints(value: string): number {
  return [...value].length;
}

/** 아직 서버에 못 보낸 지우기 — S15P21E201-1148. */
/** 아직 서버에 못 보낸 이름·설명 고치기 — S15P21E201-1153. */
export type PendingRename = { collectionId: string; name: string; description: string | null };

export type PendingDelete =
  | { kind: 'list'; collectionId: string }
  | { kind: 'item'; collectionId: string; itemId: string };

/** 두 보류 삭제가 같은 것을 가리키는가. 같은 것을 두 번 적지 않으려고 쓴다. */
export function samePendingDelete(a: PendingDelete, b: PendingDelete): boolean {
  if (a.kind !== b.kind) return false;
  if (a.kind === 'list' && b.kind === 'list') return a.collectionId === b.collectionId;
  if (a.kind === 'item' && b.kind === 'item') return a.collectionId === b.collectionId && a.itemId === b.itemId;
  return false;
}

export type CollectionsLoadResult =
  /** `blocked` 는 서버가 받아 줄 수 없어 올리기를 건너뛴 리스트 수다. */
  | { state: 'success'; data: DeviceCollections; uploaded: number; blocked: number; pendingDeletes: PendingDelete[]; pendingRenames: PendingRename[] }
  | { state: 'device-only'; data: DeviceCollections; reason: 'anonymous' | 'unreachable'; pendingDeletes: PendingDelete[]; pendingRenames: PendingRename[] };

/** 서버 리스트를 기기 모양으로 옮긴다. */
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
        // 서버는 분류를 안 준다. null 로 둔다 — 없는 값을 지어내지 않는다.
        category: null,
        locality: item.locality ?? null,
        photoUri: item.photoUrl ?? null,
        note: item.note ?? null,
        // 언제 담았는지는 항목마다 안 온다. 리스트가 마지막으로 바뀐 시각을 쓴다.
        addedAt: collection.updatedAt,
        lat: item.lat ?? null,
        lng: item.lng ?? null,
        // 지울 때 쓸 서버 이름표 — S15P21E201-1148.
        serverItemId: item.itemId,
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

/** 기기 것과 서버 것을 합친다. */
export function mergeCollections(device: DeviceCollections, server: DeviceCollections, pendingDeletes: PendingDelete[] = [], pendingRenames: PendingRename[] = []) {
  // — 아직 못 지운 것을 서버 응답에서 미리 걷어낸다.
  if (pendingDeletes.length > 0) {
    const deletedLists = new Set(pendingDeletes.filter((entry) => entry.kind === 'list').map((entry) => entry.collectionId));
    const deletedItems = new Set(
      pendingDeletes.filter((entry) => entry.kind === 'item').map((entry) => `${entry.collectionId}\u0000${entry.itemId}`),
    );
    server = {
      ...server,
      lists: server.lists
        .filter((list) => !deletedLists.has(list.id))
        .map((list) => ({
          ...list,
          placeIds: list.placeIds.filter((placeId) => !deletedItems.has(`${list.id}\u0000${server.places[placeId]?.serverItemId ?? placeId}`)),
        })),
    };
  }
  // — id 로 먼저 맞추고, 못 찾으면 이름으로 맞춘다.
  if (pendingRenames.length > 0) {
    const renameById = new Map(pendingRenames.map((entry) => [entry.collectionId, entry]));
    server = {
      ...server,
      lists: server.lists.map((list) => {
        const rename = renameById.get(list.id);
        return rename ? { ...list, name: rename.name, description: rename.description } : list;
      }),
    };
  }
  const byId = new Map(server.lists.map((list) => [list.id, list]));
  const byName = new Map(server.lists.map((list) => [list.name.trim(), list]));
  const merged: CollectionList[] = [...server.lists];
  const onlyOnDevice: CollectionList[] = [];
  /** 이미 서버에 있는 리스트에, 기기에만 있는 장소 — S15P21E201-1133. */
  const pendingUploads: Array<{ collectionId: string; placeIds: string[] }> = [];

  for (const deviceList of device.lists) {
    const match = byId.get(deviceList.id) ?? byName.get(deviceList.name.trim());
    if (!match) { merged.push(deviceList); onlyOnDevice.push(deviceList); continue; }
    // 같은 이름이 양쪽에 있다 — 서버 리스트에 기기에만 있던 장소를 더한다.
    const extra = deviceList.placeIds.filter((id) => !match.placeIds.includes(id));
    if (extra.length > 0) {
      const index = merged.findIndex((list) => list.id === match.id);
      merged[index] = { ...match, placeIds: [...match.placeIds, ...extra] };
      // — 화면에 더하는 것만으로는 부족하다. 올려야 한다.
      // 여기까지 와 놓고 올리지 않아서, 이미 서버에 있는 리스트에 담은 장소는
      // 영영 기기에만 남았다. 리스트는 한 번 만들고 장소를 계속 담는 것이
      // 부슐랭의 일상적인 쓰임이라, 사실상 대부분이 안 올라가고 있었다.
      pendingUploads.push({ collectionId: match.id, placeIds: extra });
    }
  }

  // 장소는 서버 것을 우선하되, 기기에만 있던 것은 그대로 남긴다.
  const places: Record<string, CollectionPlace> = { ...device.places, ...server.places };
  return { merged: { lists: merged, places }, onlyOnDevice, pendingUploads };
}

/** 서버가 아는 것인가 — S15P21E201-1117. */
const SERVER_PLACE_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isServerId(value: string): boolean {
  return SERVER_PLACE_ID.test(value);
}

/** 담을 것 하나를 서버 말로 옮긴다 — S15P21E201-1117. */
export function buildItemRequest(placeId: string, place: CollectionPlace) {
  if (isServerId(placeId)) {
    return { kind: 'PLACE' as const, placeId, note: place.note ?? null };
  }
  return {
    kind: 'CUSTOM' as const,
    name: place.name,
    locality: place.locality ?? null,
    lat: place.lat ?? null,
    lng: place.lng ?? null,
    note: place.note ?? null,
  };
}

/** 담을 것 하나를 서버에 올린다. 실패는 부르는 쪽이 센다. */
/** 보류해 둔 지우기를 서버로 보낸다 — S15P21E201-1148. */
/** 보류해 둔 이름·설명 고치기를 서버로 보낸다 — S15P21E201-1153. */
async function sendPendingRenames(pending: PendingRename[], accessToken: string): Promise<PendingRename[]> {
  const remaining: PendingRename[] = [];
  for (const entry of pending) {
    try {
      await apiRequest<unknown>(`/api/v1/me/collections/${encodeURIComponent(entry.collectionId)}`, {
        method: 'PATCH',
        accessToken,
        body: { name: entry.name, description: entry.description },
      });
    } catch (error) {
      // 지우기와 같은 규칙이다 — 4xx 는 이 값으로는 안 된다는 뜻이라 다시 보내도 같고
      // 계속 들고 있으면 영영 안 될 것을 매번 시도한다. 5xx 와 끊김만 남긴다.
      const status = error instanceof ApiClientError ? error.status : 0;
      if (status < 400 || status >= 500) remaining.push(entry);
    }
  }
  return remaining;
}

async function sendPendingDeletes(pending: PendingDelete[], accessToken: string): Promise<PendingDelete[]> {
  const remaining: PendingDelete[] = [];
  for (const entry of pending) {
    const path = entry.kind === 'list'
      ? `/api/v1/me/collections/${encodeURIComponent(entry.collectionId)}`
      : `/api/v1/me/collections/${encodeURIComponent(entry.collectionId)}/items/${encodeURIComponent(entry.itemId)}`;
    try {
      await apiRequest<unknown>(path, { method: 'DELETE', accessToken });
    } catch (error) {
      // 404 는 이미 없다는 뜻이다 — 우리가 바라던 상태이므로 지운 것으로 친다.
      // 다른 4xx 도 다시 보낸다고 달라지지 않으므로 목록에서 뺀다. 계속 들고
      // 있으면 영영 안 지워질 것을 매번 시도하게 된다.
      // 5xx 와 끊김만 남긴다 — 그건 다음에 되는 종류다.
      const status = error instanceof ApiClientError ? error.status : 0;
      if (status < 400 || status >= 500) remaining.push(entry);
    }
  }
  return remaining;
}

async function addItemOnServer(collectionId: string, placeId: string, place: CollectionPlace, accessToken: string) {
  await apiRequest<unknown>(`/api/v1/me/collections/${encodeURIComponent(collectionId)}/items`, {
    method: 'POST',
    accessToken,
    body: buildItemRequest(placeId, place),
  });
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
        body: buildItemRequest(placeId, place),
      });
    } catch {
      // 장소 하나가 안 올라가도 리스트 전체를 버리지 않는다. 그 장소는 기기에 남아
      // 있고, 다음에 다시 올릴 기회가 있다.
    }
  }
  return created;
}

/** 부슐랭을 불러온다 — 로그인했으면 서버와 합치고, 아니면 기기 것을 그대로 준다. */
export async function loadCollections(device: DeviceCollections, accessToken: string | null, pendingDeletes: PendingDelete[] = [], pendingRenames: PendingRename[] = []): Promise<CollectionsLoadResult> {
  // 로그인 전에는 보낼 수 없다. 지운 사실은 그대로 들고 있다가 로그인하면 보낸다.
  if (!accessToken) return { state: 'device-only', data: device, reason: 'anonymous', pendingDeletes, pendingRenames };

  let server: DeviceCollections;
  try {
    const page = await apiRequest<{ items: ServerCollection[]; count: number }>('/api/v1/me/collections', { accessToken });
    if (!Array.isArray(page?.items)) return { state: 'device-only', data: device, reason: 'unreachable', pendingDeletes, pendingRenames };
    server = serverToDevice(page.items);
  } catch (error) {
    // 인증이 끊긴 것과 서버가 안 되는 것을 가르지 않는다 — 둘 다 "지금은 기기 것으로
    // 보여준다" 가 맞는 답이다.
    if (error instanceof ApiClientError || error instanceof Error) {
      return { state: 'device-only', data: device, reason: 'unreachable', pendingDeletes, pendingRenames };
    }
    return { state: 'device-only', data: device, reason: 'unreachable', pendingDeletes, pendingRenames };
  }

  // — 올리기보다 지우기가 먼저다.
  const remainingDeletes = await sendPendingDeletes(pendingDeletes, accessToken);
  // 지운 리스트의 이름을 고칠 일은 없으므로 지우기 뒤에 보낸다.
  const remainingRenames = await sendPendingRenames(pendingRenames, accessToken);
  // — 이름을 실제로 보냈으면 서버가 바뀐 것이다.
  const renamedOnServer = pendingRenames.length > remainingRenames.length;

  const { merged, onlyOnDevice, pendingUploads } = mergeCollections(device, server, remainingDeletes, remainingRenames);
  let uploaded = 0;
  let blocked = 0;
  let changedServer = renamedOnServer;
  /** 못 올린 장소. 다시 받아올 때 이것만은 기기에 남겨 둔다. */
  const unuploaded = new Set<string>();

  for (const list of onlyOnDevice) {
    if (uploadBlockReason(list)) {
      // 보내 봐야 400 이다. 요청을 아끼려는 것이 아니라, 될 리 없는 것을 계속 시도하면
      // 화면이 「곧 올라간다」는 뜻으로 보이기 때문이다. 화면은 같은 판정을 써서 이
      // 리스트에 기기 전용이라고 적는다.
      blocked += 1;
      continue;
    }
    try {
      await createOnServer(list, merged.places, accessToken);
      uploaded += 1;
      changedServer = true;
    } catch (error) {
      // 4xx 는 이 값으로는 안 된다는 뜻이라 다시 보내도 같다. 5xx 와 끊김은 다음에 다시
      // 올린다 — 기기에 그대로 두는 것은 둘 다 같지만 세는 자리를 가른다.
      if (error instanceof ApiClientError && error.status >= 400 && error.status < 500) blocked += 1;
      for (const placeId of list.placeIds) unuploaded.add(placeId);
    }
  }

  // — 이미 서버에 있는 리스트에 담은 장소도 올린다.
  for (const { collectionId, placeIds } of pendingUploads) {
    for (const placeId of placeIds) {
      const place = merged.places[placeId];
      if (!place) continue;
      try {
        await addItemOnServer(collectionId, placeId, place, accessToken);
        uploaded += 1;
        changedServer = true;
      } catch (error) {
        if (error instanceof ApiClientError && error.status >= 400 && error.status < 500) blocked += 1;
        unuploaded.add(placeId);
      }
    }
  }

  if (!changedServer) return { state: 'success', data: merged, uploaded, blocked, pendingDeletes: remainingDeletes, pendingRenames: remainingRenames };

  // 올렸으면 다시 받아온다 — 서버가 부르는 이름으로 기기의 이름표를 바꾸기 위해서다.
  try {
    const refreshed = await apiRequest<{ items: ServerCollection[]; count: number }>('/api/v1/me/collections', { accessToken });
    if (!Array.isArray(refreshed?.items)) return { state: 'success', data: merged, uploaded, blocked, pendingDeletes: remainingDeletes, pendingRenames: remainingRenames };
    return { state: 'success', data: restoreUnuploaded(mergeCollections({ lists: [], places: {} }, serverToDevice(refreshed.items), remainingDeletes, remainingRenames).merged, merged, unuploaded), uploaded, blocked, pendingDeletes: remainingDeletes, pendingRenames: remainingRenames };
  } catch {
    return { state: 'success', data: merged, uploaded, blocked, pendingDeletes: remainingDeletes, pendingRenames: remainingRenames };
  }
}

/** 못 올린 장소를 서버 것 위에 얹는다 — S15P21E201-1133. */
export function restoreUnuploaded(fresh: DeviceCollections, previous: DeviceCollections, unuploaded: Set<string>): DeviceCollections {
  if (unuploaded.size === 0) return fresh;

  const places = { ...fresh.places };
  const byName = new Map(fresh.lists.map((list) => [list.name.trim(), list]));
  const lists = [...fresh.lists];

  for (const oldList of previous.lists) {
    const keep = oldList.placeIds.filter((id) => unuploaded.has(id));
    if (keep.length === 0) continue;
    for (const id of keep) {
      const place = previous.places[id];
      if (place) places[id] = place;
    }
    const match = byName.get(oldList.name.trim());
    if (match) {
      const index = lists.findIndex((list) => list.id === match.id);
      lists[index] = { ...match, placeIds: [...match.placeIds, ...keep.filter((id) => !match.placeIds.includes(id))] };
    } else {
      // 리스트째 못 올라갔다. 기기 것을 그대로 남긴다.
      lists.push({ ...oldList, placeIds: keep });
    }
  }

  return { lists, places };
}
