// 부슐랭 — 다녀온 장소를 내 기준으로 모으는 개인 아카이브.
//
// SNS(피드·코스)와는 별개다. 공개·공유, 다른 사람 리스트 저장, 코스로 재구성,
// 공동 리스트, 만족도·방문 날짜는 지금 확정 범위가 아니다 — 리스트·장소·한줄메모만
// 다룬다. 서버가 없어도 되는 기능이라 PlanProvider와 같은 방식으로 기기에만 저장한다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

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
  // S15P21E201-919: 카카오 지도 자동완성으로 고른 장소만 좌표가 있다 — 직접 타이핑한
  // 장소는 이전처럼 null이다. VERSION을 안 올린 이유: 기존 저장 데이터는 이 두 칸이
  // 없을 뿐 그대로 유효하고(선택 필드), 읽는 쪽은 항상 null 가능성을 이미 대비해야 한다.
  lat: number | null;
  lng: number | null;
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

// 장소는 여러 리스트가 같이 쓸 수 있어 리스트 하나를 지운다고 장소까지 지우면 안 된다 —
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

  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY).then((raw) => {
      if (!raw) return;
      try {
        const stored = JSON.parse(raw) as { version?: number; data?: CollectionData };
        if (stored.version === VERSION && stored.data) setData(stored.data);
      } catch {
        void AsyncStorage.removeItem(STORAGE_KEY);
      }
    }).finally(() => setReady(true));
  }, []);

  useEffect(() => {
    if (ready) void AsyncStorage.setItem(STORAGE_KEY, JSON.stringify({ version: VERSION, data }));
  }, [data, ready]);

  const createList = useCallback((name: string, description?: string) => {
    const list: CollectionList = { id: uid(), name, description: description?.trim() || null, placeIds: [], createdAt: new Date().toISOString() };
    setData((current) => ({ ...current, lists: [list, ...current.lists] }));
    return list;
  }, []);

  const renameList = useCallback((listId: string, name: string, description?: string) => {
    setData((current) => ({ ...current, lists: current.lists.map((list) => list.id === listId ? { ...list, name, description: description?.trim() || null } : list) }));
  }, []);

  const deleteList = useCallback((listId: string) => {
    // 장소 자체는 다른 리스트에서도 쓸 수 있으므로 지우지 않는다 — 이 리스트의 참조만 없애고,
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
    setData((current) => {
      const lists = current.lists.map((list) => list.id === listId ? { ...list, placeIds: list.placeIds.filter((id) => id !== placeId) } : list);
      return { lists, places: pruneOrphanedPlaces(current.places, lists) };
    });
  }, []);

  const listsContaining = useCallback((placeId: string) => data.lists.filter((list) => list.placeIds.includes(placeId)), [data.lists]);

  const value = useMemo<CollectionContextValue>(() => ({
    ready,
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
  }), [ready, data, createList, renameList, deleteList, addNewPlaceToList, addExistingPlaceToList, removePlaceFromList, listsContaining]);

  return <CollectionContext.Provider value={value}>{children}</CollectionContext.Provider>;
}

export function useCollection() {
  const value = useContext(CollectionContext);
  if (!value) throw new Error('useCollection은 CollectionProvider 안에서 사용해야 합니다.');
  return value;
}
