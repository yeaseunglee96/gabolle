// 여행 조건(알레르기 · 식단 · 길 환경)을 실제로 저장한다 — S15P21E201-1245.
import AsyncStorage from '@react-native-async-storage/async-storage';

import { apiRequest } from '@/api/client';
import type { ConstraintSelectionStatus, PlanDraft } from '@/plan/PlanProvider';

/** 사람에게 붙는 조건. 여행마다 달라지는 것(휠체어·유아차·큰 짐)은 여기 없다. */
export type TravelConditions = {
  allergyStatus: ConstraintSelectionStatus;
  allergies: string[];
  dietStatus: ConstraintSelectionStatus;
  dietTypes: string[];
  /** 0 = 제한 없음, null = 아직 안 정함. 둘은 다른 뜻이다 — 확인 화면이 null 을 「미확인」으로 그린다. */
  maxWalkingDistanceM: number | null;
  slopeConstraint: 'AVOID' | 'ALLOW' | null;
  stairsConstraint: 'AVOID' | 'ALLOW' | null;
  shadePreference: 'PREFER' | 'NO_PREFERENCE' | null;
};

/** 모달을 언제 다시 띄울지. 「안 물어봄」은 값이 아니라 행이 없는 것이라 null 이다. */
export type ConditionsAnswerStatus = 'SAVED' | 'LATER' | 'NEVER';

export type TravelConditionsRecord = { status: ConditionsAnswerStatus | null; conditions: TravelConditions | null };

// 비건·페스코는 뺐다(S15P21E201-1828). 서버가 달걀·유제품을 가릴 자료가 없어 비건과 채식을 구분하지
// 못하고, 셋 다 고기·해산물 중심 집을 빼는 같은 규칙으로 판정한다. 옛 저장값은 travelConditions 가 채식으로 읽는다.
/** 식단 칩 — 여행 만들기 조건 창과 마이페이지 음식 취향이 같이 쓴다. 한 곳에 둬야 서버 판정(!1851)과 안 어긋난다. */
export const DIETS = [
  ['VEGETARIAN', '채식', 'Vegetarian'], ['HALAL', '할랄', 'Halal'], ['GLUTEN_FREE', '글루텐 프리', 'Gluten-free'],
] as const;

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

/** 저장된 조건을 초안에 얹을 모양으로 바꾼다 — 모든 새 여행의 기본값이 된다. */
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

// 앱에서 뺀 식단 코드(S15P21E201-1828). 칩이 없어 사용자가 끌 수 없는 값으로 남지 않게 채식으로 읽는다.
const MERGED_INTO_VEGETARIAN = ['VEGAN', 'PESCATARIAN'];

const dietCodes = (value: unknown): string[] =>
  [...new Set(codes(value).map((code) => (MERGED_INTO_VEGETARIAN.includes(code) ? 'VEGETARIAN' : code)))];

const oneOf = <T extends string>(value: unknown, allowed: readonly T[]): T | null =>
  allowed.includes(value as T) ? (value as T) : null;

/** 글자 한 덩어리로 만든다. 판 번호를 같이 적는다. */
export function encodeConditions(conditions: TravelConditions): string {
  return JSON.stringify({ v: VALUE_VERSION, ...conditions });
}

/** 글자 한 덩어리를 되읽는다. */
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
      dietTypes: parsed.dietStatus === 'VALUES' ? dietCodes(parsed.dietTypes) : [],
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
 * 🔴 홈의 「물을까」 판정과 새 여행 초안(PlanProvider)이 로그인 직후 거의 동시에 조건을 읽는다 — 요청이 둘 나갔다
 *    (S15P21E201-1686, 로컬 실측에서 둘째는 45ms 뒤). 같은 계정·같은 표로 잠깐 사이에 다시 읽으면 앞의 요청을 같이 쓴다.
 *    저장하면 버린다 — 저장 전 답을 돌려주지 않게.
 */
const SHARE_MS = 5000;
let shared: { key: string; at: number; request: Promise<TravelConditionsRecord | null> } | null = null;

/** 서버의 답. 못 닿으면 null. */
function fetchServerRecord(userId: string | null, accessToken: string): Promise<TravelConditionsRecord | null> {
  const key = `${userId}:${accessToken}`;
  if (shared && shared.key === key && Date.now() - shared.at < SHARE_MS) return shared.request;
  const request = apiRequest<{ status?: string | null; value?: string | null }>(PATH, { accessToken })
    .then((dto) => {
      const record: TravelConditionsRecord = {
        status: oneOf(dto?.status, ['SAVED', 'LATER', 'NEVER'] as const),
        conditions: decodeConditions(dto?.value),
      };
      void writeLocal(userId, record);
      return record;
    })
    .catch(() => null);
  shared = { key, at: Date.now(), request };
  return request;
}

/** 저장된 조건을 읽는다. 서버가 정본, 기기가 사본이다. */
export async function loadTravelConditions(
  userId: string | null,
  accessToken: string | null,
): Promise<TravelConditionsRecord> {
  if (accessToken) {
    const record = await fetchServerRecord(userId, accessToken);
    if (record) return record;
    // 서버에 못 닿았다. 아래 기기 사본으로 내려간다 — 「안 물어봤다」고 단정하지 않는다.
  }
  return (await readLocal(userId)) ?? { status: null, conditions: null };
}

/** 조건과 그 답 상태를 저장한다. */
export async function saveTravelConditions(input: {
  userId: string | null;
  accessToken: string | null;
  status: ConditionsAnswerStatus;
  /** `SAVED` 일 때만 채운다. 서버 계약이 그렇다. */
  conditions: TravelConditions | null;
}): Promise<{ synced: boolean }> {
  shared = null;
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

// 「다시 묻기」는 이 기기에만 적는다 — 아래 두 함수.
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
