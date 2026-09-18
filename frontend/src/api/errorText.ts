// 실기기에서 「`error.trip.validation`」이 화면에 그대로 찍혔다. 조건을 다 채웠는데
// 일정이 안 만들어졌고, 무엇이 문제인지 알 길이 없었다.
import { ApiClientError } from '@/api/client';

/** 「error.trip.validation」처럼 문장이 아니라 키인가. */
export function looksLikeMessageKey(message: string): boolean {
  const text = message.trim();
  if (!text || /\s/.test(text)) return false;
  // 점으로 이어진 영문 토큰 둘 이상 — error.trip.validation trip.notFound
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
    const head = ko ? '서버가 이 칸을 받지 못했어요' : 'The server rejected these fields';
    return `${head} — ${fields.join(' · ')} (${error.code})`;
  }

  if (error.message && !looksLikeMessageKey(error.message)) return error.message;

  const known = BY_CODE[error.code];
  const body = known ? (ko ? known[0] : known[1]) : generic;
  return error.code ? `${body} (${error.code})` : body;
}
