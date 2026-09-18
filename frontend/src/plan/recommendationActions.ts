// 추천 목록에서 고른 담아두기·빼기를 남긴다 — S15P21E201-975 · 1082.
//
// 그전에는 화면 상태만 바꾸고 아무 데도 안 적었다. 버튼을 누르면 "저장됨" 으로 바뀌지만
// 화면을 나갔다 들어오면 그대로 "저장" 이었다. 사용자는 이것을 "버튼이 잘 안 들린다" 고
// 적었다 — 눌리기는 하는데 남지 않는 것이 실제 증상이었다.
//
// 🔴 2026-09-16 정정 (S15P21E201-1082). 여기 있던 "서버에 저장하지 않는다 — 받는 API 가
//    아직 없고" 는 이제 낡았다. 서버가 생겼다 (S15P21E201-1013):
//      GET    /api/v1/trips/{tripId}/recommendation-actions            내가 내린 판단 전부
//      PUT    /api/v1/trips/{tripId}/recommendation-actions/{placeId}  {"action":"SAVED"|"EXCLUDED"}
//      DELETE /api/v1/trips/{tripId}/recommendation-actions/{placeId}  판단을 거둔다
//
//    기기에만 적는 동안 **여행을 함께 짜는 사람이 서로의 판단을 못 봤다.** "동행자가 함께
//    본다" 가 그 API 의 목적인데, 판단이 기기별로 따로 남아 폰을 바꾸거나 웹에서 열면
//    통째로 사라졌다(jaehyeon 님 운영 실측 2026-09-16: 호출 0건).
//
// 🔴 기기 저장을 걷어내지 않는다. 이 컨트롤러는 지금 back/dev 에만 있고 main·back/main 에는
//    없다. 서버가 404 를 주는 동안에도 화면은 지금처럼 돌아야 하고, 비회원은 애초에 서버에
//    적을 수 없다. 그래서 **서버가 되면 서버가 진실이고, 안 되면 기기 값으로 그린다.**
//
// 🔴 여행마다 따로 적는다. 한 곳에 몰아 적으면 다른 여행에서 제외한 장소가 이번 여행에서도
//    제외된 것처럼 보인다 — 담아두기·빼기는 그 여행의 후보에 대한 판단이지 장소 자체에
//    대한 판단이 아니다.
import AsyncStorage from '@react-native-async-storage/async-storage';

import { apiRequest } from '@/api/client';

/** 되돌린 것(idle)은 안 적는다 — 아무 판단도 아닌 상태다. */
export type RecommendationAction = 'saved' | 'excluded';

export type RecommendationActions = Record<string, RecommendationAction>;

/**
 * 이 판단들이 어디에 속하는가.
 *
 * 🔴 `tripId` 와 `deviceKey` 를 따로 두는 이유가 있다. 추천 화면의 주소는
 * `/trips/{jobId}/recommendations?jobId={jobId}` 라 **경로의 칸이 작업 번호이지 여행 번호가
 * 아니다**(생성 화면이 그렇게 보낸다 — recommendations.tsx 머리말 참고). 서버 주소에는 여행
 * 번호가 필요하고, 그 값은 추천 결과 응답의 `tripId`(S15P21E201-1084)로만 온다. 아직 그 칸을
 * 안 주는 서버를 상대할 때는 `tripId` 가 null 이고, 그때도 기기에는 적어야 하므로 기기 쪽
 * 열쇠는 따로 받는다.
 */
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
//
// 화면은 saved·excluded 를 쓰고 서버는 SAVED·EXCLUDED 를 쓴다. 이 변환을 화면 쪽에 흩뿌리면
// 한 곳만 대문자를 빠뜨렸을 때 그 자리만 조용히 안 적힌다 — 그래서 여기 한 자리에 모은다.

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

/**
 * 저장된 판단을 읽는다. 서버가 되면 서버가 진실이고, 안 되면 기기 값으로 그린다.
 *
 * 없거나 모양이 깨졌으면 빈 것으로 본다 — 이 값 때문에 추천 화면이 안 열리는 일이 있으면 안 된다.
 */
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
    // 🔴 서버 것으로 기기를 덮어 쓴다. 합치지 않는다 — 다른 기기에서 **거둔** 판단은 이
    //    기기에 "지웠다" 는 기록이 아니라 그냥 없는 것으로 오므로, 합치면 되살아난다.
    //    하트(savedPlaces.ts)는 합집합이 맞지만 여기는 "거두기" 가 뜻을 가지므로 다르다.
    //
    // 🔴 그래서 옛 기기 값을 올려 보내는 이사(migration)가 없다. 필요가 없기 때문이다 —
    //    여행 번호를 알 때만 이 길로 오는데, 그때 기기 열쇠도 여행 번호다. 여행 번호를
    //    모르던 시절에 쌓인 값은 작업 번호 아래 있어 이 열쇠로는 애초에 안 읽힌다.
    //    있지도 않은 값을 옮기는 코드를 두면, 나중에 그것이 무엇을 하는지 아무도 모른다.
    await writeDevice(scope.deviceKey, server);
    return server;
  } catch {
    // 어떤 이유로 실패하든 기기 값으로 그린다 — 서버가 그 경로를 아직 모르든(404·501, 아직
    // 승격 전), 우리가 못 물어보든(401·403), 잠깐 끊겼든, 화면이 할 일은 같다. 갈래를
    // 나눠 봐야 하는 것이 같으면 나누지 않는다.
    return device;
  }
}

/**
 * 판단 하나를 적는다. {@code action} 이 없으면 그 장소의 판단을 지운다(되돌리기).
 *
 * <p>기기에는 읽고 고쳐 쓴다 — 화면이 들고 있는 값을 통째로 넘기게 하면, 아직 다 못 읽은
 * 상태에서 누른 한 번이 나머지를 전부 지운다.
 *
 * <p>서버 쪽은 PUT 이라 몇 번을 보내도 결과가 같고(멱등), 없는 판단을 지워도 성공이다.
 * 그래서 연타하거나 재시도해도 안전하다.
 */
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
    // 서버에 못 남겨도 기기의 선택은 지킨다. 버튼은 서버 응답을 기다리지 않으므로,
    // 여기서 화면에 알릴 것은 없다 — 다음에 목록을 읽을 때 서버 값으로 다시 맞춰진다.
  }
}
