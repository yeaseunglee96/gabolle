// 부슐랭 — 다녀온 장소를 내 기준으로 모으는 개인 아카이브.
import AsyncStorage from '@react-native-async-storage/async-storage';

import { useAuth } from '@/auth/AuthProvider';

import { isServerId, loadCollections, samePendingDelete, type PendingDelete, type PendingRename } from './collectionsApi';
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

const STORAGE_KEY = '@gabolle/collection';
const VERSION = 1;

export type CollectionPlace = {
  id: string;
  name: string;
  category: string | null;
  locality: string | null;
  photoUri: string | null;
  note: string | null;
  addedAt: string;
  // : 카카오 지도 자동완성으로 고른 장소만 좌표가 있다 — 직접 타이핑한
  // 장소는 이전처럼 null이다. VERSION을 안 올린 이유: 기존 저장 데이터는 이 두 칸이
  // 없을 뿐 그대로 유효하고(선택 필드), 읽는 쪽은 항상 null 가능성을 이미 대비해야 한다.
  lat: number | null;
  lng: number | null;
  // — 서버가 이 장소를 뭐라고 부르는가.
  serverItemId?: string | null;
  /**
   * 리스트마다 서버가 부르는 이름표 — S15P21E201-1530. 같은 장소가 두 리스트에 있으면 항목도 둘이라
   * 이름표도 둘인데, 위 한 칸은 나중 리스트 것으로 덮였다. 그래서 첫 리스트에서 빼면 남의 이름표로
   * 지우기를 보내 404 가 났고(404 는 「이미 없음」으로 친다), 새로고침하면 되살아났다.
   */
  serverItemIds?: Record<string, string>;
};

export type CollectionList = {
  id: string;
  name: string;
  description: string | null;
  placeIds: string[];
  createdAt: string;
};

type CollectionData = {
  lists: CollectionList[];
  places: Record<string, CollectionPlace>;
};

const EMPTY: CollectionData = { lists: [], places: {} };

function uid() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

// 장소는 여러 리스트가 같이 쓸 수 있어 리스트 하나를 지운다고 장소까지 지우면 안 된다
// 하지만 그 장소를 쓰는 리스트가 하나도 안 남으면 얘기가 다르다. 그대로 두면 places에
// 고아로 계속 쌓여 totalPlaceCount와 "최근 추가한 장소"에 리스트 하나 없는 유령 장소로
// 영원히 남는다(리스트에서 빼거나 리스트를 지우는 것 말고는 places를 건드릴 방법이
// 아예 없어서 사용자가 직접 치울 수도 없다). 리스트가 바뀔 때마다 이 함수로 정리한다.
function pruneOrphanedPlaces(places: Record<string, CollectionPlace>, lists: CollectionList[]): Record<string, CollectionPlace> {
  const referenced = new Set(lists.flatMap((list) => list.placeIds));
  const next: Record<string, CollectionPlace> = {};
  for (const [id, place] of Object.entries(places)) if (referenced.has(id)) next[id] = place;
  return next;
}

type NewPlaceInput = { name: string; category?: string | null; locality?: string | null; photoUri?: string | null; note?: string | null; lat?: number | null; lng?: number | null };

type CollectionContextValue = {
  ready: boolean;
  /** 서버와 합쳐서 보여주고 있는가. 화면이 안내 문구를 고르는 데 쓴다. */
  syncedToServer: boolean;
  /** 계정 것과 맞추는 일이 지금 어디까지 왔나. */
  syncState: 'loading' | 'synced' | 'anonymous' | 'unreachable';
  /** 못 맞췄을 때 다시 해 본다. */
  retrySync: () => void;
  lists: CollectionList[];
  places: Record<string, CollectionPlace>;
  totalPlaceCount: number;
  recentPlaces: CollectionPlace[];
  createList: (name: string, description?: string) => CollectionList;
  renameList: (listId: string, name: string, description?: string) => void;
  deleteList: (listId: string) => void;
  addNewPlaceToList: (listId: string, input: NewPlaceInput) => void;
  addExistingPlaceToList: (listId: string, placeId: string) => void;
  removePlaceFromList: (listId: string, placeId: string) => void;
  listsContaining: (placeId: string) => CollectionList[];
};

const CollectionContext = createContext<CollectionContextValue | null>(null);

export function CollectionProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<CollectionData>(EMPTY);
  const [ready, setReady] = useState(false);
  // 로그인했으면 서버와 합친 것이 보인다. 안 했으면 지금까지처럼 기기 것만 보인다.
  // 화면이 이 값을 보고 안내 문구를 고른다 — 안 그러면 "계정에 저장돼요" 가 거짓이 된다.
  const [syncedToServer, setSyncedToServer] = useState(false);
  const [syncState, setSyncState] = useState<'loading' | 'synced' | 'anonymous' | 'unreachable'>('loading');
  // 「다시 시도」를 누르면 이 숫자가 올라가고, 아래 useEffect 가 다시 돈다.
  const [syncAttempt, setSyncAttempt] = useState(0);
  // — 아직 서버에 못 보낸 지우기.
  const [pendingDeletes, setPendingDeletesState] = useState<PendingDelete[]>([]);
  const pendingDeletesRef = useRef<PendingDelete[]>([]);
  // 지금 담긴 것을 보는 사본. setData 의 갱신 함수 안에서 다른 상태를 건드리면 안 되고
  // useCallback 의 가둔 값은 낡을 수 있어서 둔다.
  const dataRef = useRef<CollectionData>(EMPTY);
  const setPendingDeletes = (next: PendingDelete[]) => { pendingDeletesRef.current = next; setPendingDeletesState(next); };
  // — 아직 서버에 못 보낸 이름·설명 고치기.
  // 지우기와 달리 같은 리스트를 여러 번 고칠 수 있다. 그때는 쌓지 않고 마지막 값으로
  // 덮는다 — 중간 이름을 서버에 보낼 이유가 없다.
  const [pendingRenames, setPendingRenamesState] = useState<PendingRename[]>([]);
  const pendingRenamesRef = useRef<PendingRename[]>([]);
  const setPendingRenames = (next: PendingRename[]) => { pendingRenamesRef.current = next; setPendingRenamesState(next); };
  const queueRename = (entry: PendingRename) => {
    const rest = pendingRenamesRef.current.filter((existing) => existing.collectionId !== entry.collectionId);
    setPendingRenames([...rest, entry]);
  };

  const queueDelete = (entry: PendingDelete) => {
    const current = pendingDeletesRef.current;
    if (current.some((existing) => samePendingDelete(existing, entry))) return;
    setPendingDeletes([...current, entry]);
  };
  const { accessToken } = useAuth();
  dataRef.current = data;

  // 기기에서 읽고, 로그인했으면 서버와 합친다.
  useEffect(() => {
    let alive = true;
    void (async () => {
      let device: CollectionData = EMPTY;
      try {
        const raw = await AsyncStorage.getItem(STORAGE_KEY);
        if (raw) {
          const stored = JSON.parse(raw) as { version?: number; data?: CollectionData; pendingDeletes?: PendingDelete[]; pendingRenames?: PendingRename[] };
          if (stored.version === VERSION && stored.data) device = stored.data;
          if (Array.isArray(stored.pendingDeletes)) setPendingDeletes(stored.pendingDeletes);
          if (Array.isArray(stored.pendingRenames)) setPendingRenames(stored.pendingRenames);
        }
      } catch {
        void AsyncStorage.removeItem(STORAGE_KEY);
      }
      if (!alive) return;
      setData(device);
      setSyncState('loading');
      const result = await loadCollections(device, accessToken, pendingDeletesRef.current, pendingRenamesRef.current);
      if (!alive) return;
      setData(result.data);
      setPendingDeletes(result.pendingDeletes);
      setPendingRenames(result.pendingRenames);
      setSyncedToServer(result.state === 'success');
      setSyncState(result.state === 'success' ? 'synced' : result.reason);
      setReady(true);
    })();
    return () => { alive = false; };
  }, [accessToken, syncAttempt]);

  useEffect(() => {
    if (ready) void AsyncStorage.setItem(STORAGE_KEY, JSON.stringify({ version: VERSION, data, pendingDeletes, pendingRenames }));
  }, [data, pendingDeletes, pendingRenames, ready]);

  const createList = useCallback((name: string, description?: string) => {
    const list: CollectionList = { id: uid(), name, description: description?.trim() || null, placeIds: [], createdAt: new Date().toISOString() };
    setData((current) => ({ ...current, lists: [list, ...current.lists] }));
    return list;
  }, []);

  const renameList = useCallback((listId: string, name: string, description?: string) => {
    const nextDescription = description?.trim() || null;
    // — 서버가 아는 리스트면 서버에도 고치라고 적어 둔다.
    // 기기에만 있는 리스트는 아직 서버에 없으니, 나중에 통째로 올라갈 때 새 이름으로 간다.
    if (isServerId(listId)) queueRename({ collectionId: listId, name, description: nextDescription });
    setData((current) => ({ ...current, lists: current.lists.map((list) => list.id === listId ? { ...list, name, description: nextDescription } : list) }));
  }, []);

  const deleteList = useCallback((listId: string) => {
    // — 서버가 아는 리스트면 서버에서도 지우라고 적어 둔다.
    // 기기에만 있던 리스트는 서버에 없으니 보낼 것도 없다.
    if (isServerId(listId)) queueDelete({ kind: 'list', collectionId: listId });
    // 장소 자체는 다른 리스트에서도 쓸 수 있으므로 지우지 않는다 — 이 리스트의 참조만 없애고
    // 그 결과 어느 리스트에도 안 남은 장소만 pruneOrphanedPlaces가 함께 정리한다.
    setData((current) => {
      const lists = current.lists.filter((list) => list.id !== listId);
      return { lists, places: pruneOrphanedPlaces(current.places, lists) };
    });
  }, []);

  const addNewPlaceToList = useCallback((listId: string, input: NewPlaceInput) => {
    const place: CollectionPlace = { id: uid(), name: input.name, category: input.category ?? null, locality: input.locality ?? null, photoUri: input.photoUri ?? null, note: input.note ?? null, addedAt: new Date().toISOString(), lat: input.lat ?? null, lng: input.lng ?? null };
    setData((current) => ({
      places: { ...current.places, [place.id]: place },
      lists: current.lists.map((list) => list.id === listId ? { ...list, placeIds: [place.id, ...list.placeIds] } : list),
    }));
  }, []);

  const addExistingPlaceToList = useCallback((listId: string, placeId: string) => {
    setData((current) => ({ ...current, lists: current.lists.map((list) => list.id === listId && !list.placeIds.includes(placeId) ? { ...list, placeIds: [placeId, ...list.placeIds] } : list) }));
  }, []);

  const removePlaceFromList = useCallback((listId: string, placeId: string) => {
    // — 서버가 아는 리스트의, 서버가 아는 장소일 때만 보낸다.
    // serverItemId 는 서버에서 받아온 장소에만 있다(collectionsApi 의 serverToDevice).
    const stored = dataRef.current.places[placeId];
    const serverItemId = stored?.serverItemIds?.[listId] ?? stored?.serverItemId;
    if (isServerId(listId) && serverItemId) queueDelete({ kind: 'item', collectionId: listId, itemId: serverItemId });
    setData((current) => {
      const lists = current.lists.map((list) => list.id === listId ? { ...list, placeIds: list.placeIds.filter((id) => id !== placeId) } : list);
      return { lists, places: pruneOrphanedPlaces(current.places, lists) };
    });
  }, []);

  const listsContaining = useCallback((placeId: string) => data.lists.filter((list) => list.placeIds.includes(placeId)), [data.lists]);

  const value = useMemo<CollectionContextValue>(() => ({
    ready,
    syncedToServer,
    syncState,
    retrySync: () => setSyncAttempt((count) => count + 1),
    lists: data.lists,
    places: data.places,
    totalPlaceCount: Object.keys(data.places).length,
    recentPlaces: Object.values(data.places).sort((a, b) => b.addedAt.localeCompare(a.addedAt)).slice(0, 5),
    createList,
    renameList,
    deleteList,
    addNewPlaceToList,
    addExistingPlaceToList,
    removePlaceFromList,
    listsContaining,
  }), [ready, syncedToServer, syncState, data, createList, renameList, deleteList, addNewPlaceToList, addExistingPlaceToList, removePlaceFromList, listsContaining]);

  return <CollectionContext.Provider value={value}>{children}</CollectionContext.Provider>;
}

export function useCollection() {
  const value = useContext(CollectionContext);
  if (!value) throw new Error('useCollection은 CollectionProvider 안에서 사용해야 합니다.');
  return value;
}
