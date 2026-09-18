// 서버 오류를 **사람이 읽는 문장**으로 바꾼다 — S15P21E201-1245 (2026-09-18).
//
// 🔴 실기기에서 「`error.trip.validation`」이 화면에 그대로 찍혔다. 조건을 다 채웠는데
//    일정이 안 만들어졌고, 무엇이 문제인지 알 길이 없었다.
//
//    원인은 **서버가 message 자리에 메시지 키를 넣는 것**이다. 백엔드도 알고 적어 뒀다
//    (`PlaceExceptionHandler` 의 주석: *「TripExceptionHandler 는 message 자리에
//    "error.trip.validation" 같은 키를 넣고, auth 쪽은 한국어 문장을 넣는다. 둘이 갈려 있다.
//    프런트는 error.message 를 그대로 화면에 띄운다」*). place·auth 는 문장을 넣고
//    trip 은 키를 넣는다.
//
// 🔴 **서버를 고칠 때까지 기다리지 않는다.** 키가 보이는 것은 화면의 잘못이기도 하다 —
//    받은 것을 검사 없이 그대로 찍고 있었다.
//
// 🔴 **어느 칸이 막혔는지는 이미 오고 있었다.** `ApiError` 의 `fields` 에
//    「칸이름: 사유」 줄이 담겨 오는데(TripExceptionHandler 셋 다 채운다) 화면이 버리고
//    있었다. 그게 사용자에게 가장 쓸모 있는 정보라 **그것을 먼저 보여준다.**
import { ApiClientError } from '@/api/client';

/** 「error.trip.validation」처럼 **문장이 아니라 키**인가. */
export function looksLikeMessageKey(message: string): boolean {
  const text = message.trim();
  if (!text || /\s/.test(text)) return false;
  // 점으로 이어진 영문 토큰 둘 이상 — error.trip.validation · trip.notFound
  return /^[a-z][a-z0-9]*(\.[a-zA-Z][a-zA-Z0-9]*)+$/.test(text);
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

/**
 * 화면에 쓸 문장 하나를 만든다.
 *
 * 순서가 중요하다.
 *  1. **어느 칸이 막혔는지**(`fields`)가 있으면 그것을 쓴다 — 가장 쓸모 있다
 *  2. 서버 문장이 **진짜 문장**이면 그대로 쓴다 (auth·place 는 한국어 문장을 준다)
 *  3. 키이거나 비었으면 **코드로 고른 문장**, 그것도 모르면 일반 문구
 *
 * 🔴 **코드는 문장 뒤에 괄호로 남긴다.** 지우면 사람이 로그에서 되찾을 방법이 없어진다.
 *    앞에는 뜻을 적고 기호는 뒤에 둔다 — 이 저장소의 규칙이다.
 */
export function readableApiError(error: unknown, ko: boolean): string {
  const generic = ko ? '요청을 처리하지 못했어요.' : 'We could not complete that request.';
  if (!(error instanceof ApiClientError)) {
    return error instanceof Error && error.message && !looksLikeMessageKey(error.message)
      ? error.message
      : generic;
  }

  const fields = error.fields.filter((line) => line.trim().length > 0);
  if (fields.length) {
    const head = ko ? '서버가 이 칸을 받지 못했어요' : 'The server rejected these fields';
    return `${head} — ${fields.join(' · ')} (${error.code})`;
  }

  if (error.message && !looksLikeMessageKey(error.message)) return error.message;

  const known = BY_CODE[error.code];
  const body = known ? (ko ? known[0] : known[1]) : generic;
  return error.code ? `${body} (${error.code})` : body;
}
