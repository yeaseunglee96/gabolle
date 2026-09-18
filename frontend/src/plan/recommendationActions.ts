// 추천 목록에서 고른 담아두기·빼기를 남긴다 —· 1082.
import AsyncStorage from '@react-native-async-storage/async-storage';

import { apiRequest } from '@/api/client';

/** 되돌린 것(idle)은 안 적는다 — 아무 판단도 아닌 상태다. */
export type RecommendationAction = 'saved' | 'excluded';

export type RecommendationActions = Record<string, RecommendationAction>;

/** 이 판단들이 어디에 속하는가. */
export type RecommendationActionScope = {
  /** 서버에 적을 수 있는 여행 번호. 모르면 null — 그때는 기기에만 적는다. */
  tripId: string | null;
  /** 기기 저장 열쇠. 여행 번호를 알면 그것이고, 모르면 화면이 들고 있는 작업 번호다. */
  deviceKey: string;
  /** 로그인한 사람의 출입증. 없으면 서버에 적을 수 없다 — 기기에만 남는다. */
  accessToken: string | null;
};

const PREFIX = '@gabolle/recommendation-actions:';

function keyOf(deviceKey: string) {
  return `${PREFIX}${deviceKey}`;
}

// ── 화면값 ↔ 서버값 ────────────────────────────────────────────────────────────

type ServerAction = 'SAVED' | 'EXCLUDED';

const TO_SERVER: Record<RecommendationAction, ServerAction> = { saved: 'SAVED', excluded: 'EXCLUDED' };

function fromServer(value: unknown): RecommendationAction | null {
  if (value === 'SAVED') return 'saved';
  if (value === 'EXCLUDED') return 'excluded';
  return null;
}

type ActionItemDto = { placeId: string; action: string };
type ActionPageDto = { items: ActionItemDto[]; count: number; hasMore: boolean };

function actionsPath(tripId: string, placeId?: string) {
  const base = `/api/v1/trips/${encodeURIComponent(tripId)}/recommendation-actions`;
  return placeId ? `${base}/${encodeURIComponent(placeId)}` : base;
}

// ── 기기 저장 ─────────────────────────────────────────────────────────────────

async function readDevice(deviceKey: string): Promise<RecommendationActions> {
  if (!deviceKey) return {};
  try {
    const raw = await AsyncStorage.getItem(keyOf(deviceKey));
    if (!raw) return {};
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    const next: RecommendationActions = {};
    for (const [placeId, action] of Object.entries(parsed as Record<string, unknown>)) {
      if (action === 'saved' || action === 'excluded') next[placeId] = action;
    }
    return next;
  } catch {
    return {};
  }
}

async function writeDevice(deviceKey: string, actions: RecommendationActions): Promise<void> {
  if (!deviceKey) return;
  try {
    await AsyncStorage.setItem(keyOf(deviceKey), JSON.stringify(actions));
  } catch {
    // 기기 저장이 막혀도 화면은 계속 돈다. 표시가 안 남을 뿐이다.
  }
}

// ── 읽기·쓰기 ─────────────────────────────────────────────────────────────────

/** 저장된 판단을 읽는다. 서버가 되면 서버가 진실이고, 안 되면 기기 값으로 그린다. */
export async function loadRecommendationActions(scope: RecommendationActionScope): Promise<RecommendationActions> {
  const device = await readDevice(scope.deviceKey);
  if (!scope.tripId || !scope.accessToken) return device;
  try {
    const page = await apiRequest<ActionPageDto>(actionsPath(scope.tripId), { accessToken: scope.accessToken });
    const server: RecommendationActions = {};
    for (const item of page.items ?? []) {
      const action = fromServer(item.action);
      if (action && item.placeId) server[item.placeId] = action;
    }
    // 서버 것으로 기기를 덮어 쓴다. 합치지 않는다 — 다른 기기에서 거둔 판단은 이
    // 기기에 "지웠다" 는 기록이 아니라 그냥 없는 것으로 오므로, 합치면 되살아난다.
    // 하트(savedPlaces.ts)는 합집합이 맞지만 여기는 "거두기" 가 뜻을 가지므로 다르다.
    await writeDevice(scope.deviceKey, server);
    return server;
  } catch {
    // 어떤 이유로 실패하든 기기 값으로 그린다 — 서버가 그 경로를 아직 모르든(404·501, 아직
    // 승격 전), 우리가 못 물어보든(401·403), 잠깐 끊겼든, 화면이 할 일은 같다. 갈래를
    // 나눠 봐야 하는 것이 같으면 나누지 않는다.
    return device;
  }
}

/** 판단 하나를 적는다. {@code action} 이 없으면 그 장소의 판단을 지운다(되돌리기). */
export async function saveRecommendationAction(
  scope: RecommendationActionScope,
  placeId: string,
  action: RecommendationAction | null,
): Promise<void> {
  if (!placeId) return;
  const current = await readDevice(scope.deviceKey);
  if (action) current[placeId] = action;
  else delete current[placeId];
  await writeDevice(scope.deviceKey, current);

  if (!scope.tripId || !scope.accessToken) return;
  try {
    if (action) {
      await apiRequest<unknown>(actionsPath(scope.tripId, placeId), {
        method: 'PUT',
        accessToken: scope.accessToken,
        body: { action: TO_SERVER[action] },
      });
    } else {
      await apiRequest<void>(actionsPath(scope.tripId, placeId), { method: 'DELETE', accessToken: scope.accessToken });
    }
  } catch {
    // 서버에 못 남겨도 기기의 선택은 지킨다. 버튼은 서버 응답을 기다리지 않으므로
    // 여기서 화면에 알릴 것은 없다 — 다음에 목록을 읽을 때 서버 값으로 다시 맞춰진다.
  }
}
