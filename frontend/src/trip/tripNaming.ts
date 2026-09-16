// 여행 이름 짓기 — 후보 받기와 이름 저장 (S15P21E201-1036).
//
// 서버는 2026-09-16 에 양쪽 다 준비를 끝냈는데(S15P21E201-1025 · -1023) 화면이 둘 다
// 부르지 않고 있었다. 그래서 여행에 이름이 붙을 길이 아예 없었다 — 여행 카드에 이름을
// 그리는 작업(-1023)을 해도 사용자에게는 아무것도 달라지지 않았다는 뜻이다.
//
// 이 파일은 그중 **연결 층만** 한다. 화면은 디자인이 나온 뒤에 붙인다.

import { apiRequest, ApiClientError } from '@/api/client';

/**
 * 이름 길이 상한. 🔴 서버의 `Trip.TITLE_MAX_LENGTH` 와 같은 값이어야 한다.
 *
 * 서버가 최종 판정을 하므로 여기서 미리 막는 것은 **사용자를 덜 기다리게 하려는 것**이지
 * 검사를 대신하는 것이 아니다. 서버가 거부하면 그 응답(`TRIP_TITLE_INVALID`)을 그대로 쓴다.
 *
 * 글자 수는 코드포인트로 센다 — 서버가 `codePointCount` 로 세기 때문이다. `length` 로 세면
 * 이모지 하나가 2로 세어져 60자를 못 채운 이름이 거부당한 것처럼 보인다.
 */
export const TRIP_TITLE_MAX_LENGTH = 60;

/**
 * 이 이름이 어디서 나왔는가.
 *
 * 🔴 `MODEL` 과 `TEMPLATE` 을 화면이 **같게 그리면 안 된다.** `TEMPLATE` 은 모델이
 * 실패해서 정해진 틀로 만든 이름이다. 그것을 「AI 가 지어 줬어요」처럼 보이게 하는 것은
 * 이 저장소의 원칙(모르는 것을 아는 척하지 않는다)을 어기는 것이다.
 *
 * `| string` 으로 열어 둔다 — 서버가 갈래를 늘렸을 때 화면이 죽지 않게. 지금 아는 둘만
 * 분기하고 나머지는 「어디서 왔는지 모른다」로 다룬다.
 */
export type TripNameSource = 'MODEL' | 'TEMPLATE' | (string & {});

export type TripNameSuggestionsDto = {
  suggestions: string[];
  source: TripNameSource;
  /**
   * 🔴 **가 보지도 않은 장소가 섞여 들어와서 버린 후보 수**다.
   *
   * 서버(`PlaceWordGuard`)가 일정에 없는 장소 이름이 든 후보를 버린다. 부산 여행에
   * 「경주 불국사에서 보낸 이틀」이 붙으면 사용자가 자기가 만들지 않은 일정을 자기
   * 것으로 기억하게 되기 때문이다. 이 숫자가 0 이 아니라는 것은 **막혔다**는 뜻이지
   * 고장났다는 뜻이 아니다.
   */
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
    // 🔴 서버가 준 문장을 그대로 쓰는 갈래는 이 둘뿐이다. 이 둘은 사람에게 보여줄 말로
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
  // 있다 (S15P21E201-1000).
  return { state: 'error', message: '여행 이름을 처리하지 못했어요.' };
}

/**
 * 이름 후보를 받아 온다 — `POST /api/v1/trips/{tripId}/name-suggestions`.
 *
 * 🔴 **빈 후보는 실패가 아니다.** 모델이 못 지으면 서버가 빈 목록이나 틀로 만든 이름을
 * 준다. 이름을 못 지은 것은 사람을 다치게 하지 않으므로 서버가 오류로 만들지 않는다
 * (메뉴판 읽기는 정반대다 — 거기서 빈 결과는 「알레르기가 없다」로 읽혀 위험하다).
 * 그러니 화면도 이것을 오류 화면으로 그리면 안 된다.
 */
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
    // 세므로 여기서 안 막으면 화면이 통째로 안 열린다 (S15P21E201-762 와 같은 자리다).
    if (!Array.isArray(dto?.suggestions)) {
      return { state: 'error', message: '이름 후보의 형식이 예상과 달라요.' };
    }
    return {
      state: 'success',
      suggestions: dto.suggestions,
      source: dto.source,
      // 없으면 0 으로 둔다. 🔴 「몇 개 버렸는지 모른다」와 「0개 버렸다」는 다른 말이지만,
      // 이 값은 화면에 숫자로 쓰이지 않고 "버린 것이 있었나" 만 가른다.
      discardedCount: typeof dto.discardedCount === 'number' ? dto.discardedCount : 0,
    };
  } catch (error) {
    return failure(error);
  }
}

/** 이름이 모델이 지은 것인가. `TEMPLATE` 과 갈라 그리라고 있는 함수다. */
export const isModelNamed = (source: TripNameSource) => source === 'MODEL';

/**
 * 후보를 화면에 몇 개까지 그리나 (시안 `design_handoff_trip_name`).
 *
 * 더 와도 앞에서 자른다. 고르는 일이 일이 되면 사람은 고르지 않고 건너뛴다.
 */
export const MAX_SHOWN_SUGGESTIONS = 5;

export type TripNameStep = {
  step: 'suggestions' | 'empty';
  suggestions: string[];
  source: TripNameSource;
  discardedCount: number;
};

/**
 * 후보를 받아 온 결과로 화면이 어느 자리에 서는지 정한다 (S15P21E201-1036).
 *
 * 🔴 **실패해도 오류 화면으로 가지 않는다.** 이름을 못 지은 것은 사람을 다치게 하지
 * 않는다 — 직접 쓰거나 건너뛸 수 있는 자리로 보내면 된다. 그리고 그때 **후보를 지어내지
 * 않는다.** 빈 목록으로 간다.
 *
 * 화면이 아니라 여기 두는 이유는 이 판단이 시험으로 지켜져야 하기 때문이다. 화면 안에
 * 두면 「실패하면 오류를 띄우자」로 조용히 바뀌어도 아무것도 빨개지지 않는다.
 */
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

/**
 * 보내기 전에 미리 보는 검사. 🔴 서버의 `Trip.rename` 과 같은 규칙이다.
 *
 * - 앞뒤 공백을 뗀다
 * - **공백만 있으면 `null`** — 「이름을 지운다」는 뜻이다. 서버에 지우기 전용 경로가
 *   따로 없는 이유가 이것이다. 빈 이름과 이름 없음을 다른 것으로 다루지 않는다
 * - 줄바꿈·제어문자가 있으면 거부
 * - 60자(코드포인트)를 넘으면 거부
 */
export function checkTripTitle(raw: string | null | undefined): TripTitleCheck {
  const trimmed = (raw ?? '').trim();
  if (trimmed === '') return { ok: true, title: null };
  if ([...trimmed].some((ch) => {
    const code = ch.codePointAt(0);
    // ISO 제어문자 — C0(0x00~0x1F) · DEL(0x7F) · C1(0x80~0x9F). 서버의
    // Character.isISOControl 과 같은 범위다.
    return code !== undefined && ((code <= 0x1f) || (code >= 0x7f && code <= 0x9f));
  })) {
    return { ok: false, reason: 'controlChar' };
  }
  const length = [...trimmed].length;
  if (length > TRIP_TITLE_MAX_LENGTH) return { ok: false, reason: 'tooLong', length };
  return { ok: true, title: trimmed };
}

/**
 * 이름을 저장한다 — `PUT /api/v1/trips/{tripId}/title`.
 *
 * 🔴 **빈 문자열이나 공백만 보내면 이름이 지워진다.** 그게 서버의 계약이고, 그래서
 * 「이름 지우기」에 별도 함수를 만들지 않았다 — 두 벌이 되면 어느 쪽이 진짜인지
 * 모르게 된다. 지우려면 `''` 를 보낸다.
 */
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
      // 🔴 서버가 돌려준 값을 쓴다. 우리가 보낸 것을 그대로 믿지 않는다 — 서버가 앞뒤
      // 공백을 떼고 저장하므로, 보낸 것과 저장된 것이 다를 수 있다.
      title: dto?.title ?? null,
      updatedAt: dto?.updatedAt ?? '',
    };
  } catch (error) {
    return failure(error);
  }
}
