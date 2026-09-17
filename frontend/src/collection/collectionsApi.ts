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

/**
 * 서버가 받아 주는 글자 수. 넘기면 400 이라 올릴 방법이 없다.
 *
 * 값의 주인은 서버다 — `Collection.NAME_MAX_LENGTH`·`DESCRIPTION_MAX_LENGTH` 와
 * `CollectionItem` 의 이름·지역·메모 상한을 그대로 옮겨 적었다. 자바 상수를 타입스크립트에서
 * 읽을 방법이 없어 두 곳에 적히는 것은 감수한다. 저쪽이 바뀌면 여기도 바꾼다.
 *
 * 화면은 입력칸의 `maxLength` 로 이 값을 쓰고, 여기서는 이미 기기에 남아 있는 긴 값을
 * 걸러내는 데 쓴다. 막는 자리가 둘인 이유는 상한이 생기기 전에 만들어진 리스트가 기기에
 * 남아 있기 때문이다 — 입력칸만 막으면 그것들은 계속 400 을 받는다.
 */
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

/**
 * 보내 보기 전에 서버가 거절할 것을 알 수 있는 경우를 가른다.
 *
 * 다시 시도해서 될 실패와 안 될 실패는 다르게 다뤄야 한다. 서버가 끊겼거나 500 이면 다음에
 * 다시 올리면 되지만, 글자 수가 넘친 이름은 같은 값을 몇 번을 보내도 같은 400 이다. 그런
 * 리스트를 재시도 목록에 두면 화면을 열 때마다 실패하는 요청이 한 번씩 나가고, 사용자는
 * 자기 리스트가 계정에 없다는 것을 끝내 모른다.
 */
export function uploadBlockReason(list: CollectionList): UploadBlockReason | null {
  if (codePoints(list.name.trim()) > COLLECTION_LIMITS.name) return 'name-too-long';
  if (codePoints(list.description?.trim() ?? '') > COLLECTION_LIMITS.description) return 'description-too-long';
  return null;
}

/**
 * 서버와 같은 방식으로 센다.
 *
 * 자바스크립트의 `.length` 는 글자가 아니라 UTF-16 칸 수라, 이모지 하나가 둘로 세어진다.
 * 서버는 `codePointCount` 로 세므로 그대로 쓰면 서버가 받아 줄 이름을 우리가 막는다.
 * 입력칸의 `maxLength` 는 UTF-16 칸으로만 셀 수 있는데, 그쪽은 더 빡빡하게 막는 것이라
 * 서버가 거절할 값이 새어 나가지는 않는다.
 */
function codePoints(value: string): number {
  return [...value].length;
}

/**
 * 아직 서버에 못 보낸 지우기 — S15P21E201-1148.
 *
 * <p>기기에서 지운 것을 서버에도 지워야 하는데, 그때 서버에 못 닿을 수 있다.
 * 그 사실을 잃어버리면 다음 동기화에서 지운 것이 되살아난다. 그래서 적어 두고
 * 기기에 함께 저장한다 — 앱을 껐다 켜도 살아남는다.
 */
/**
 * 아직 서버에 못 보낸 이름·설명 고치기 — S15P21E201-1153.
 *
 * <p>지우기(PendingDelete)와 같은 이유로 둔다. 고친 사실을 잃어버리면 다음 동기화에서
 * 서버의 옛 이름이 내려와 기기의 새 이름을 덮는다.
 */
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
export function mergeCollections(device: DeviceCollections, server: DeviceCollections, pendingDeletes: PendingDelete[] = [], pendingRenames: PendingRename[] = []) {
  // 🔴 S15P21E201-1148 — 아직 못 지운 것을 서버 응답에서 미리 걷어낸다.
  //
  //    지우기를 서버에 못 보낸 동안에도 서버는 그것을 계속 돌려준다. 그대로 합치면
  //    사용자가 지운 것이 화면에 다시 나타났다가, 다음에 지우기가 성공하면 또
  //    사라진다 — 깜빡이는 화면이 된다. 보류 중인 것은 처음부터 없는 셈 친다.
  if (pendingDeletes.length > 0) {
    const deletedLists = new Set(pendingDeletes.filter((entry) => entry.kind === 'list').map((entry) => entry.collectionId));
    const deletedItems = new Set(
      pendingDeletes.filter((entry) => entry.kind === 'item').map((entry) => `${entry.collectionId} ${entry.itemId}`),
    );
    server = {
      ...server,
      lists: server.lists
        .filter((list) => !deletedLists.has(list.id))
        .map((list) => ({
          ...list,
          placeIds: list.placeIds.filter((placeId) => !deletedItems.has(`${list.id} ${server.places[placeId]?.serverItemId ?? placeId}`)),
        })),
    };
  }
  // 🔴 S15P21E201-1153 — id 로 먼저 맞추고, 못 찾으면 이름으로 맞춘다.
  //
  //    전에는 이름으로만 맞췄다. 그러면 사용자가 리스트 이름을 바꾸는 순간 서버의 같은
  //    리스트와 짝이 안 맞아 「기기에만 있는 리스트」로 보이고, 같은 내용이 서버에
  //    하나 더 만들어진다.
  //
  //    그렇다고 id 로만 맞추면 반대쪽이 깨진다 — 서버에 이미 있는 이름으로 기기에서
  //    새로 만든 리스트(오프라인에서 만든 경우)는 id 가 uid() 라 안 맞고, 역시 하나 더
  //    만들어진다. 둘 다 막으려면 둘 다 본다.
  // 🔴 S15P21E201-1153 — 아직 못 보낸 이름 고치기를 서버 응답 위에 얹는다.
  //
  //    못 보낸 동안 서버는 옛 이름을 계속 돌려준다. 그대로 쓰면 사용자가 고친 이름이
  //    잠깐 옛것으로 돌아갔다가 다음에 성공하면 또 바뀐다 — 깜빡이는 화면이 된다.
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
      // 🔴 S15P21E201-1133 — 화면에 더하는 것만으로는 부족하다. 올려야 한다.
      //    여기까지 와 놓고 올리지 않아서, 이미 서버에 있는 리스트에 담은 장소는
      //    영영 기기에만 남았다. 리스트는 한 번 만들고 장소를 계속 담는 것이
      //    부슐랭의 일상적인 쓰임이라, 사실상 대부분이 안 올라가고 있었다.
      pendingUploads.push({ collectionId: match.id, placeIds: extra });
    }
  }

  // 장소는 서버 것을 우선하되, 기기에만 있던 것은 그대로 남긴다.
  const places: Record<string, CollectionPlace> = { ...device.places, ...server.places };
  return { merged: { lists: merged, places }, onlyOnDevice, pendingUploads };
}

/**
 * 서버가 아는 것인가 — S15P21E201-1117.
 *
 * <p>장소와 리스트 둘 다에 쓴다. 서버가 만든 id 는 UUID 이고, 기기가 만든 것은 아니다 —
 * 그 한 가지만 본다. (S15P21E201-1148 에서 리스트에도 쓰게 되면서 이름을 넓혔다.)
 *
 * <p>서버 장소 id 는 UUID 다. 그런데 부슐랭에서 손으로 추가한 장소는 기기가 만든다 —
 * {@code `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`}, 예를 들면
 * {@code mfjk2x-a7b3c1}. 이것은 어떤 경우에도 UUID 가 아니다.
 *
 * <p>🔴 그런데 올릴 때 이것을 {@code kind: PLACE} 의 {@code placeId} 로 보내고 있었다.
 * 서버는 {@code UUID placeId} 로 받으므로 값을 읽는 단계에서 400 이 나고, 바깥의 catch 가
 * 그것을 삼켜서 그 장소는 조용히 사라진다. 사용자는 올라간 줄 안다.
 * (2026-09-16 iOS 실기기 스윕에서 발견)
 */
const SERVER_PLACE_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isServerId(value: string): boolean {
  return SERVER_PLACE_ID.test(value);
}

/**
 * 담을 것 하나를 서버 말로 옮긴다 — S15P21E201-1117.
 *
 * <p>서버는 처음부터 두 종류를 받게 돼 있었다. {@code PLACE} 는 서버에 있는 장소를
 * 가리키는 것이고, {@code CUSTOM} 은 사용자가 손으로 적은 것이다 — 이름·지역·좌표·메모를
 * 그대로 싣고 {@code placeId} 를 안 쓴다(CollectionController.addItem).
 *
 * <p>손으로 추가한 장소는 처음부터 {@code CUSTOM} 이었다. 클라이언트가 늘 {@code PLACE}
 * 로 보낸 것이 잘못이다. 계약을 바꿀 일이 아니라 맞는 칸에 넣으면 되는 일이었다.
 */
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
/**
 * 보류해 둔 지우기를 서버로 보낸다 — S15P21E201-1148.
 *
 * @returns 아직 못 보낸 것들. 다음에 다시 시도한다.
 */
/**
 * 보류해 둔 이름·설명 고치기를 서버로 보낸다 — S15P21E201-1153.
 *
 * @returns 아직 못 보낸 것들. 다음에 다시 시도한다.
 */
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
      // 지우기와 같은 규칙이다 — 4xx 는 이 값으로는 안 된다는 뜻이라 다시 보내도 같고,
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
      // 🔴 404 는 이미 없다는 뜻이다 — 우리가 바라던 상태이므로 지운 것으로 친다.
      //    다른 4xx 도 다시 보낸다고 달라지지 않으므로 목록에서 뺀다. 계속 들고
      //    있으면 영영 안 지워질 것을 매번 시도하게 된다.
      //    5xx 와 끊김만 남긴다 — 그건 다음에 되는 종류다.
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

  // 🔴 S15P21E201-1148 — 올리기보다 지우기가 먼저다.
  //
  //    순서를 바꾸면 방금 지운 것을 다시 올리는 일이 생긴다. 지우기를 먼저 보내고,
  //    아직 못 보낸 것은 아래 합치기에서 서버 응답에서 걷어낸다.
  const remainingDeletes = await sendPendingDeletes(pendingDeletes, accessToken);
  // 지운 리스트의 이름을 고칠 일은 없으므로 지우기 뒤에 보낸다.
  const remainingRenames = await sendPendingRenames(pendingRenames, accessToken);

  const { merged, onlyOnDevice, pendingUploads } = mergeCollections(device, server, remainingDeletes, remainingRenames);
  let uploaded = 0;
  let blocked = 0;
  let changedServer = false;
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

  // 🔴 S15P21E201-1133 — 이미 서버에 있는 리스트에 담은 장소도 올린다.
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

  // 🔴 올렸으면 다시 받아온다 — 서버가 부르는 이름으로 기기의 이름표를 바꾸기 위해서다.
  //
  //    기기가 만든 장소 id 는 uid() 라 서버 것과 다르다. 서버는 올린 뒤 자기 itemId 를
  //    붙여 돌려준다. 그래서 다시 받아오지 않으면, 같은 장소를 기기는 mfjk2x-a7b3c1 로
  //    서버는 7b8cd3bc-… 로 불러 다음번에도 「기기에만 있다」로 판정된다.
  //    그대로 두면 앱을 켤 때마다 같은 장소가 하나씩 불어난다.
  //
  //    기기에 「올린 것 ↔ 서버 이름」 표를 따로 저장하는 방법도 있다. 안 쓴다 —
  //    상태를 하나 더 들고 있으면 그것이 또 어긋나고, 어긋난 것을 고칠 자리가 는다.
  try {
    const refreshed = await apiRequest<{ items: ServerCollection[]; count: number }>('/api/v1/me/collections', { accessToken });
    if (!Array.isArray(refreshed?.items)) return { state: 'success', data: merged, uploaded, blocked, pendingDeletes: remainingDeletes, pendingRenames: remainingRenames };
    return { state: 'success', data: restoreUnuploaded(mergeCollections({ lists: [], places: {} }, serverToDevice(refreshed.items), remainingDeletes, remainingRenames).merged, merged, unuploaded), uploaded, blocked, pendingDeletes: remainingDeletes, pendingRenames: remainingRenames };
  } catch {
    // 다시 받아오는 데 실패해도 올린 것은 올라갔다. 앞서 합친 것을 그대로 쓴다 —
    // 이름표는 다음 실행에서 맞춰진다.
    return { state: 'success', data: merged, uploaded, blocked, pendingDeletes: remainingDeletes, pendingRenames: remainingRenames };
  }
}

/**
 * 못 올린 장소를 서버 것 위에 얹는다 — S15P21E201-1133.
 *
 * <p>🔴 이 파일의 규칙이 「기기에 쌓인 것을 버리지 않는다」이다. 다시 받아온 것으로
 * 그냥 갈아끼우면, 올리다 실패한 장소가 <b>기기에서도 사라진다.</b> 사용자는 자기가
 * 담은 것이 지워졌다고 느끼고, 실제로 다시 올릴 기회도 없어진다.
 */
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
