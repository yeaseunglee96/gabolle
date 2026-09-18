// 여행 이름 짓기 — 후보 조회와 이름 저장

import { apiRequest, ApiClientError } from '@/api/client';

/** 이름 길이 상한. 서버의 `Trip.TITLE_MAX_LENGTH` 와 같은 값이어야 한다. */
export const TRIP_TITLE_MAX_LENGTH = 60;

/** 이 이름이 어디서 나왔는가. */
export type TripNameSource = 'MODEL' | 'TEMPLATE' | (string & {});

export type TripNameSuggestionsDto = {
  suggestions: string[];
  source: TripNameSource;
  /** 가 보지도 않은 장소가 섞여 들어와서 버린 후보 수다. */
  discardedCount: number;
};

/**
 * 실패 갈래. `trips.ts` 의 것과 나눠 둔다 — 이름 저장은 권한(403)과 값 거부(400)가
 * 따로 오는데, 목록 읽기에는 그 두 갈래가 없다.
 */
export type TripNamingFailure = {
  state: 'unavailable' | 'offline' | 'forbidden' | 'invalid' | 'error';
  message: string;
};

export type TripNameSuggestionsResult = ({ state: 'success' } & TripNameSuggestionsDto) | TripNamingFailure;

export type UpdateTripTitleResult =
  | { state: 'success'; title: string | null; updatedAt: string }
  | TripNamingFailure;

function failure(error: unknown): TripNamingFailure {
  if (error instanceof ApiClientError) {
    if (error.code === 'NETWORK_ERROR') return { state: 'offline', message: error.message };
    // 서버가 준 문장을 그대로 쓰는 갈래는 이 둘뿐이다. 이 둘은 사람에게 보여줄 말로
    // 쓰였다 — 권한이 없다는 안내와, 이름이 왜 거부됐는지(길이·줄바꿈)를 알려주는 말이다.
    if (error.status === 403 || error.code === 'TRIP_FORBIDDEN') {
      return { state: 'forbidden', message: error.message };
    }
    if (error.status === 400 || error.code === 'TRIP_TITLE_INVALID') {
      return { state: 'invalid', message: error.message };
    }
    if (error.status === 404 || error.code === 'TRIP_NOT_FOUND') {
      return { state: 'unavailable', message: '이 여행을 찾을 수 없어요.' };
    }
  }
  // 예상 못 한 실패다. 서버가 준 문장이 사람에게 읽히는 말이라는 보장이 없어 우리 문구를
  // 쓴다 — 같은 저장소에서 「Invalid UUID string: demo-trip」 이 화면에 그대로 나온 적이
  // 있다
  return { state: 'error', message: '여행 이름을 처리하지 못했어요.' };
}

/** 이름 후보를 받아 온다 — `POST /api/v1/trips/{tripId}/name-suggestions`. */
export async function loadTripNameSuggestions(
  tripId: string,
  accessToken: string | null,
): Promise<TripNameSuggestionsResult> {
  try {
    const dto = await apiRequest<TripNameSuggestionsDto>(
      `/api/v1/trips/${encodeURIComponent(tripId)}/name-suggestions`,
      { method: 'POST', accessToken },
    );
    // 모양이 어긋나면 화면을 죽이지 말고 안내로 떨어뜨린다. 부르는 쪽이 곧바로 개수를
    // 세므로 여기서 안 막으면 화면이 통째로 안 열린다 와 같은 자리다).
    if (!Array.isArray(dto?.suggestions)) {
      return { state: 'error', message: '이름 후보의 형식이 예상과 달라요.' };
    }
    return {
      state: 'success',
      suggestions: dto.suggestions,
      source: dto.source,
      // 없으면 0 으로 둔다. 「몇 개 버렸는지 모른다」와 「0개 버렸다」는 다른 말이지만
      // 이 값은 화면에 숫자로 쓰이지 않고 "버린 것이 있었나" 만 가른다.
      discardedCount: typeof dto.discardedCount === 'number' ? dto.discardedCount : 0,
    };
  } catch (error) {
    return failure(error);
  }
}

/** 이름이 모델이 지은 것인가. `TEMPLATE` 과 갈라 그리라고 있는 함수다. */
export const isModelNamed = (source: TripNameSource) => source === 'MODEL';

/** 후보를 화면에 몇 개까지 그리나 (시안 `design_handoff_trip_name`). */
export const MAX_SHOWN_SUGGESTIONS = 5;

export type TripNameStep = {
  step: 'suggestions' | 'empty';
  suggestions: string[];
  source: TripNameSource;
  discardedCount: number;
};

/** 후보를 받아 온 결과로 화면이 어느 자리에 서는지 정한다 */
export function planNameStep(result: TripNameSuggestionsResult): TripNameStep {
  if (result.state !== 'success') {
    return { step: 'empty', suggestions: [], source: 'TEMPLATE', discardedCount: 0 };
  }
  const shown = result.suggestions.slice(0, MAX_SHOWN_SUGGESTIONS);
  return {
    step: shown.length > 0 ? 'suggestions' : 'empty',
    suggestions: shown,
    source: result.source,
    discardedCount: result.discardedCount,
  };
}

export type TripTitleCheck =
  | { ok: true; title: string | null }
  | { ok: false; reason: 'tooLong' | 'controlChar'; length?: number };

/** 보내기 전에 미리 보는 검사. 서버의 `Trip.rename` 과 같은 규칙이다. */
export function checkTripTitle(raw: string | null | undefined): TripTitleCheck {
  const trimmed = (raw ?? '').trim();
  if (trimmed === '') return { ok: true, title: null };
  if ([...trimmed].some((ch) => {
    const code = ch.codePointAt(0);
    // ISO 제어문자 — C0(0x00~0x1F) DEL(0x7F) C1(0x80~0x9F). 서버의
    // Character.isISOControl 과 같은 범위다.
    return code !== undefined && ((code <= 0x1f) || (code >= 0x7f && code <= 0x9f));
  })) {
    return { ok: false, reason: 'controlChar' };
  }
  const length = [...trimmed].length;
  if (length > TRIP_TITLE_MAX_LENGTH) return { ok: false, reason: 'tooLong', length };
  return { ok: true, title: trimmed };
}

/** 이름을 저장한다 — `PUT /api/v1/trips/{tripId}/title`. */
export async function updateTripTitle(
  tripId: string,
  title: string | null,
  accessToken: string | null,
): Promise<UpdateTripTitleResult> {
  const checked = checkTripTitle(title);
  if (!checked.ok) {
    // 서버까지 안 가고 여기서 막는다. 문구는 화면이 정한다 — 이 층은 무엇이 잘못됐는지만
    // 말하고, 사람에게 할 말은 고르지 않는다(말투·번역이 화면 몫이다).
    return {
      state: 'invalid',
      message: checked.reason === 'tooLong'
        ? `여행 이름은 ${TRIP_TITLE_MAX_LENGTH}자까지 쓸 수 있어요.`
        : '여행 이름에 줄바꿈이나 특수한 문자를 넣을 수 없어요.',
    };
  }
  try {
    const dto = await apiRequest<{ tripId: string; title: string | null; updatedAt: string }>(
      `/api/v1/trips/${encodeURIComponent(tripId)}/title`,
      { method: 'PUT', accessToken, body: { title: checked.title ?? '' } },
    );
    return {
      state: 'success',
      // 서버가 돌려준 값을 쓴다. 우리가 보낸 것을 그대로 믿지 않는다 — 서버가 앞뒤
      // 공백을 떼고 저장하므로, 보낸 것과 저장된 것이 다를 수 있다.
      title: dto?.title ?? null,
      updatedAt: dto?.updatedAt ?? '',
    };
  } catch (error) {
    return failure(error);
  }
}

// ── 같은 질문을 두 번 하지 않는다 ────────────────────────────────────────────

import AsyncStorage from '@react-native-async-storage/async-storage';

const NAME_ASKED_PREFIX = '@gabolle/nameAsked:';

export async function markTripNameAsked(tripId: string) {
  try {
    await AsyncStorage.setItem(`${NAME_ASKED_PREFIX}${tripId}`, '1');
  } catch {
    // 못 적어도 흐름을 막지 않는다. 다음에 한 번 더 묻게 될 뿐이다.
  }
}

export async function wasTripNameAsked(tripId: string) {
  try {
    return (await AsyncStorage.getItem(`${NAME_ASKED_PREFIX}${tripId}`)) === '1';
  } catch {
    // 못 읽었으면 「물어봤다」로 친다. 반대로 두면 저장소가 막힌 기기에서 매번 묻는다.
    return true;
  }
}

/** 이름을 물어볼 자리인가. */
export function shouldAskTripName(input: { title: string | null | undefined; alreadyAsked: boolean }) {
  if (input.alreadyAsked) return false;
  return !(input.title ?? '').trim();
}
