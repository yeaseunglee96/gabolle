// 실기기에서 「`error.trip.validation`」이 화면에 그대로 찍혔다. 조건을 다 채웠는데
// 일정이 안 만들어졌고, 무엇이 문제인지 알 길이 없었다.
import { ApiClientError } from '@/api/client';

/** 「error.trip.validation」처럼 문장이 아니라 키인가. */
export function looksLikeMessageKey(message: string): boolean {
  const text = message.trim();
  if (!text || /\s/.test(text)) return false;
  // 점으로 이어진 영문 토큰 둘 이상 — error.trip.validation · trip.notFound
  return /^[a-z][a-z0-9]*(\.[a-zA-Z][a-zA-Z0-9]*)+$/.test(text);
}

/**
 * 🔴 서버가 짚은 «칸 이름»을 우리 문장으로 바꾸는 자리 — S15P21E201-1342.
 *
 * 서버는 칸마다 `"originLat: 출발지 좌표가 없다. 목록에서 출발지를 골라 주세요"` 꼴로 준다
 * (백엔드 `TripExceptionHandler`: `f.getField() + ": " + f.getDefaultMessage()`).
 * 그것을 그대로 이어 붙이면 화면에 이렇게 뜬다 — 2026-09-21 실기.
 *
 *   서버가 이 칸을 받지 못했어요 — originLat: 출발지 좌표가 없다.
 *   목록에서 출발지를 골라 주세요 (TRIP_VALIDATION_FAILED)
 *
 * 한 줄에 세 가지가 섞여 있다: 우리 머리말, 서버 원문, 내부 칸 이름과 오류 코드.
 * `originLat` 과 `TRIP_VALIDATION_FAILED` 는 **사람에게 할 말이 아니다.** 그리고 서버 원문은
 * 우리가 언어를 못 고른다 — 일본어로 쓰는 사람에게도 한국어로 나간다.
 *
 * 아는 칸이면 우리 문장 하나만 낸다. 모르는 칸은 예전처럼 둔다 — 문장을 못 지어낸 채
 * 단서까지 지우면 무엇이 잘못됐는지 아무도 모르게 된다.
 */
const BY_FIELD: Record<string, [string, string]> = {
  // 출발지는 홈 시작 바에서만 고를 수 있다. 어디서 고치는지까지 말해 준다.
  originLat: [
    '출발지를 아직 안 골랐어요. 홈에서 출발지를 고르면 일정을 만들 수 있어요.',
    'No starting point yet. Pick one on the home screen and we can build your trip.',
  ],
  originLng: [
    '출발지를 아직 안 골랐어요. 홈에서 출발지를 고르면 일정을 만들 수 있어요.',
    'No starting point yet. Pick one on the home screen and we can build your trip.',
  ],
  startDate: ['가는 날을 아직 안 정했어요.', 'You have not picked a departure date yet.'],
  finishDate: ['오는 날을 아직 안 정했어요.', 'You have not picked a return date yet.'],
  partySize: ['인원을 확인해 주세요.', 'Please check the number of travellers.'],
  // 1박 이상 여행은 숙소가 있어야 서버가 만든다(S15P21E201-1584). 숙소도 홈 시작 바에서 고른다.
  accommodation: [
    '1박 이상 여행은 숙소를 골라야 일정을 만들 수 있어요. 홈에서 숙소를 골라 주세요.',
    'Trips with an overnight stay need a place to stay. Pick one on the home screen.',
  ],
};

/** `"originLat: 출발지 좌표가 없다…"` 에서 칸 이름만 뗀다. 구분자가 없으면 칸 이름이 없는 것이다. */
function fieldNameOf(entry: string): string | null {
  const at = entry.indexOf(':');
  if (at <= 0) return null;
  const name = entry.slice(0, at).trim();
  return /^[A-Za-z][A-Za-z0-9_.]*$/.test(name) ? name : null;
}

const BY_CODE: Record<string, [string, string]> = {
  TRIP_VALIDATION_FAILED: [
    '입력한 조건 중 서버가 받지 못한 것이 있어요.',
    'The server rejected some of the details you entered.',
  ],
  TRIP_NOT_FOUND: ['그 여행을 찾지 못했어요.', 'We could not find that trip.'],
  ITINERARY_NOT_FOUND: ['그 일정을 찾지 못했어요.', 'We could not find that itinerary.'],
  NETWORK_ERROR: ['서버에 닿지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not reach the server. Please try again shortly.'],
};

/** 화면에 쓸 문장 하나를 만든다. */
export function readableApiError(error: unknown, ko: boolean): string {
  const generic = ko ? '요청을 처리하지 못했어요.' : 'We could not complete that request.';
  if (!(error instanceof ApiClientError)) {
    return error instanceof Error && error.message && !looksLikeMessageKey(error.message)
      ? error.message
      : generic;
  }

  const fields = error.fields.filter((line) => line.trim().length > 0);
  if (fields.length) {
    // 🔴 아는 칸이면 우리 문장만 낸다 — 칸 이름도 오류 코드도 사람에게 할 말이 아니다.
    const known: string[] = [];
    let unknown = false;
    for (const entry of fields) {
      const name = fieldNameOf(entry);
      const sentence = name ? BY_FIELD[name] : undefined;
      if (!sentence) { unknown = true; continue; }
      const line = ko ? sentence[0] : sentence[1];
      // 같은 말을 두 번 하지 않는다 — originLat 과 originLng 은 한 가지 문제다.
      if (!known.includes(line)) known.push(line);
    }
    if (known.length && !unknown) return known.join(' ');
    // 모르는 칸이 섞여 있으면 예전처럼 둔다. 단서까지 지우면 아무도 원인을 못 찾는다.
    const head = ko ? '서버가 이 칸을 받지 못했어요' : 'The server rejected these fields';
    return `${head} — ${fields.join(' · ')} (${error.code})`;
  }

  if (error.message && !looksLikeMessageKey(error.message)) return error.message;

  const known = BY_CODE[error.code];
  const body = known ? (ko ? known[0] : known[1]) : generic;
  return error.code ? `${body} (${error.code})` : body;
}
