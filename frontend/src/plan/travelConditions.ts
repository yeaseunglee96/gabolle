// 여행 조건(알레르기 · 식단 · 길 환경)을 **실제로 저장한다** — S15P21E201-1245.
//
// 🔴 2026-09-18 — 모달에 다 적고 저장을 눌러도 **한 톨도 안 남았다.**
//
//    모달은 `usePlan().update()` 로 초안(draft)에만 적었는데, 그 초안의 이 칸들은
//    `PlanProvider` 의 `VOLATILE_CONSTRAINTS` 가 **저장할 때도 읽어올 때도 통째로 비운다.**
//    그래서 새로고침 한 번, 또는 일정을 한 번 만들고 나면(`clear()`) 사라졌고, 확인 화면은
//    다시 「미확인 필수 조건이 있어요」로 막았다. 모달은 「한 번만 알려주시면 다음부터
//    안 물어봐요」라고 적어 놓고 **다시 묻지도 않았다** — 물어본 기록(`SAVED`)만 남아서다.
//    빠져나갈 길이 없는 상태였다.
//
// 🔴 **자리는 이미 있었다.** 백엔드가 같은 날 아침에 만들어 back/dev 에 넣어 뒀다
//    (S15P21E201-1231).
//
//      GET /api/v1/me/preferences/constraints  →  { status, value }
//      PUT /api/v1/me/preferences/constraints  ←  { answerStatus, value }
//
//    `value` 는 **JSON 글자 한 덩어리**다 — 모양을 정하는 것은 이 파일이다. 그래서 칸을
//    늘릴 때 서버를 안 고쳐도 되지만, **판 번호(`v`)를 같이 적어** 다음 사람이 옛 글자를
//    알아볼 수 있게 한다.
//
// 🔴 기기에도 한 벌 적는다. 로그인 안 한 사람과 서버가 잠깐 안 될 때를 위해서다.
//    **서버가 정본이고 기기는 사본**이다 — 둘이 다르면 서버를 믿는다.
import AsyncStorage from '@react-native-async-storage/async-storage';

import { apiRequest } from '@/api/client';
import type { ConstraintSelectionStatus, PlanDraft } from '@/plan/PlanProvider';

/** 사람에게 붙는 조건. **여행마다 달라지는 것(휠체어·유아차·큰 짐)은 여기 없다.** */
export type TravelConditions = {
  allergyStatus: ConstraintSelectionStatus;
  allergies: string[];
  dietStatus: ConstraintSelectionStatus;
  dietTypes: string[];
  /** 0 = 제한 없음, null = 아직 안 정함. 🔴 둘은 다른 뜻이다 — 확인 화면이 null 을 「미확인」으로 그린다. */
  maxWalkingDistanceM: number | null;
  slopeConstraint: 'AVOID' | 'ALLOW' | null;
  stairsConstraint: 'AVOID' | 'ALLOW' | null;
  shadePreference: 'PREFER' | 'NO_PREFERENCE' | null;
};

/** 모달을 언제 다시 띄울지. 「안 물어봄」은 값이 아니라 **행이 없는 것**이라 null 이다. */
export type ConditionsAnswerStatus = 'SAVED' | 'LATER' | 'NEVER';

export type TravelConditionsRecord = { status: ConditionsAnswerStatus | null; conditions: TravelConditions | null };

const PATH = '/api/v1/me/preferences/constraints';
const LOCAL_KEY = 'gabolle.travel-conditions';
const VALUE_VERSION = 1;

export const EMPTY_CONDITIONS: TravelConditions = {
  allergyStatus: 'UNKNOWN', allergies: [], dietStatus: 'UNKNOWN', dietTypes: [],
  maxWalkingDistanceM: null, slopeConstraint: null, stairsConstraint: null, shadePreference: null,
};

/** 초안에서 조건만 떼어낸다. */
export function conditionsFromDraft(draft: PlanDraft): TravelConditions {
  return {
    allergyStatus: draft.allergyStatus,
    allergies: draft.allergyStatus === 'VALUES' ? draft.allergies : [],
    dietStatus: draft.dietStatus,
    dietTypes: draft.dietStatus === 'VALUES' ? draft.dietTypes : [],
    maxWalkingDistanceM: draft.maxWalkingDistanceM,
    slopeConstraint: draft.slopeConstraint,
    stairsConstraint: draft.stairsConstraint,
    shadePreference: draft.shadePreference,
  };
}

/**
 * 저장된 조건을 초안에 얹을 모양으로 바꾼다 — **모든 새 여행의 기본값**이 된다.
 *
 * 🔴 `allergyAnswered` · `dietAnswered` 를 같이 켠다. 이 둘이 꺼져 있으면 모달의 저장
 *    단추가 다시 잠기고, 사용자는 이미 답한 것을 또 답해야 한다.
 */
export function conditionsToDraftPatch(conditions: TravelConditions): Partial<PlanDraft> {
  return {
    ...conditions,
    allergyAnswered: conditions.allergyStatus !== 'UNKNOWN',
    dietAnswered: conditions.dietStatus !== 'UNKNOWN',
  };
}

const isSelection = (value: unknown): value is ConstraintSelectionStatus =>
  value === 'UNKNOWN' || value === 'NONE' || value === 'VALUES';

const codes = (value: unknown): string[] =>
  Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];

const oneOf = <T extends string>(value: unknown, allowed: readonly T[]): T | null =>
  allowed.includes(value as T) ? (value as T) : null;

/** 글자 한 덩어리로 만든다. 판 번호를 같이 적는다. */
export function encodeConditions(conditions: TravelConditions): string {
  return JSON.stringify({ v: VALUE_VERSION, ...conditions });
}

/**
 * 글자 한 덩어리를 되읽는다.
 *
 * 🔴 **못 알아보면 null 이다 — 반쯤 읽은 값을 돌려주지 않는다.** 알레르기는 안전에 걸리는
 *    자리라, 「일부만 맞는 값」이 「없는 값」보다 위험하다.
 */
export function decodeConditions(raw: string | null | undefined): TravelConditions | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    if (!parsed || typeof parsed !== 'object') return null;
    if (parsed.v !== VALUE_VERSION) return null;
    if (!isSelection(parsed.allergyStatus) || !isSelection(parsed.dietStatus)) return null;
    const walking = parsed.maxWalkingDistanceM;
    return {
      allergyStatus: parsed.allergyStatus,
      allergies: parsed.allergyStatus === 'VALUES' ? codes(parsed.allergies) : [],
      dietStatus: parsed.dietStatus,
      dietTypes: parsed.dietStatus === 'VALUES' ? codes(parsed.dietTypes) : [],
      maxWalkingDistanceM: typeof walking === 'number' && Number.isFinite(walking) && walking >= 0 ? walking : null,
      slopeConstraint: oneOf(parsed.slopeConstraint, ['AVOID', 'ALLOW'] as const),
      stairsConstraint: oneOf(parsed.stairsConstraint, ['AVOID', 'ALLOW'] as const),
      shadePreference: oneOf(parsed.shadePreference, ['PREFER', 'NO_PREFERENCE'] as const),
    };
  } catch {
    return null;
  }
}

const localKeyFor = (userId: string | null) => `${LOCAL_KEY}:${userId ?? 'anonymous'}`;

type StoredLocal = { status: ConditionsAnswerStatus | null; value: string | null };

async function readLocal(userId: string | null): Promise<TravelConditionsRecord | null> {
  try {
    const raw = await AsyncStorage.getItem(localKeyFor(userId));
    if (!raw) return null;
    const stored = JSON.parse(raw) as StoredLocal;
    const status = oneOf(stored.status, ['SAVED', 'LATER', 'NEVER'] as const);
    return { status, conditions: decodeConditions(stored.value) };
  } catch {
    return null;
  }
}

async function writeLocal(userId: string | null, record: TravelConditionsRecord): Promise<void> {
  try {
    const stored: StoredLocal = {
      status: record.status,
      value: record.conditions ? encodeConditions(record.conditions) : null,
    };
    await AsyncStorage.setItem(localKeyFor(userId), JSON.stringify(stored));
  } catch {
    // 못 적어도 이번 실행 동안의 답은 화면 상태로 지켜진다. 서버에는 이미 갔다.
  }
}

/**
 * 저장된 조건을 읽는다. **서버가 정본, 기기가 사본**이다.
 *
 * 🔴 서버가 안 되면 기기 사본으로 답한다 — 비행기 안에서 조건을 잃지 않게.
 * 🔴 서버가 「한 번도 저장 안 함」(status null)을 주면 그것을 그대로 믿는다. 기기 사본이
 *    남아 있어도 서버에 없으면 다른 기기에서 지운 것이다.
 */
export async function loadTravelConditions(
  userId: string | null,
  accessToken: string | null,
): Promise<TravelConditionsRecord> {
  if (accessToken) {
    try {
      const dto = await apiRequest<{ status?: string | null; value?: string | null }>(PATH, { accessToken });
      const record: TravelConditionsRecord = {
        status: oneOf(dto?.status, ['SAVED', 'LATER', 'NEVER'] as const),
        conditions: decodeConditions(dto?.value),
      };
      void writeLocal(userId, record);
      return record;
    } catch {
      // 서버에 못 닿았다. 아래 기기 사본으로 내려간다 — 「안 물어봤다」고 단정하지 않는다.
    }
  }
  return (await readLocal(userId)) ?? { status: null, conditions: null };
}

/**
 * 조건과 그 답 상태를 저장한다.
 *
 * 🔴 **기기에 먼저 적는다.** 서버가 실패해도 이번 기기에서는 남아야 한다 — 사용자가 방금
 *    다 적은 것을 「저장했다」고 말해 놓고 잃는 일이 이 버그의 본체였다.
 * 🔴 서버 저장의 성공 여부를 돌려준다. 화면이 그것을 **말할 수 있어야** 한다.
 */
export async function saveTravelConditions(input: {
  userId: string | null;
  accessToken: string | null;
  status: ConditionsAnswerStatus;
  /** `SAVED` 일 때만 채운다. 서버 계약이 그렇다. */
  conditions: TravelConditions | null;
}): Promise<{ synced: boolean }> {
  const record: TravelConditionsRecord = {
    status: input.status,
    conditions: input.status === 'SAVED' ? input.conditions : null,
  };
  await writeLocal(input.userId, record);
  if (!input.accessToken) return { synced: false };
  try {
    await apiRequest(PATH, {
      method: 'PUT',
      accessToken: input.accessToken,
      body: {
        answerStatus: input.status,
        value: record.conditions ? encodeConditions(record.conditions) : null,
      },
    });
    return { synced: true };
  } catch {
    return { synced: false };
  }
}

// 「다시 묻기」는 **이 기기에만** 적는다 — 아래 두 함수.
//
// 🔴 서버 상태를 `LATER` 로 바꾸는 방법은 쓸 수 없다. 백엔드가 `SAVED` 가 아닌 요청의
//    **값을 버리기** 때문이다(`TravelConstraintService.put` 의 주석). 다시 묻게 하려다
//    적어 둔 알레르기·식단을 통째로 날리게 된다 — 이 파일이 고치고 있는 바로 그 사고다.
//
// 🔴 서버에는 「안 물어본 상태로 되돌리기」가 아예 없다. 그 상태는 값이 아니라 **행이
//    없는 것**이라서다. 그래서 되살리는 것은 **묻기**뿐이고, 그건 기기의 일이다.
const ASK_AGAIN_KEY = 'gabolle.conditions-ask-again';

export async function askConditionsAgain(userId: string | null): Promise<void> {
  try {
    await AsyncStorage.setItem(`${ASK_AGAIN_KEY}:${userId ?? 'anonymous'}`, '1');
  } catch {
    // 못 적으면 다음에 안 뜬다. 「켰다」고 거짓말하지 않도록 부르는 쪽이 결과를 본다.
  }
}

/** 한 번 뜨고 나면 스스로 꺼진다 — 안 그러면 홈을 열 때마다 뜬다. */
export async function consumeAskAgain(userId: string | null): Promise<boolean> {
  const key = `${ASK_AGAIN_KEY}:${userId ?? 'anonymous'}`;
  try {
    const raw = await AsyncStorage.getItem(key);
    if (raw !== '1') return false;
    await AsyncStorage.removeItem(key);
    return true;
  } catch {
    return false;
  }
}
