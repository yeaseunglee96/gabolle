// 🔴 이 시험이 막는 것은 「서버가 준 것을 검사 없이 화면에 찍는 것」이다.
//    2026-09-18 실기기에서 `error.trip.validation` 이 그대로 보였다.
import { ApiClientError } from '@/api/client';
import { looksLikeMessageKey, readableApiError } from '@/api/errorText';

describe('메시지 키를 알아본다', () => {
  it('점으로 이어진 영문은 키다', () => {
    expect(looksLikeMessageKey('error.trip.validation')).toBe(true);
    expect(looksLikeMessageKey('trip.notFound')).toBe(true);
  });

  it('사람이 쓴 문장은 키가 아니다', () => {
    expect(looksLikeMessageKey('요청을 처리하지 못했어요.')).toBe(false);
    expect(looksLikeMessageKey('Could not reach the server.')).toBe(false);
    expect(looksLikeMessageKey('')).toBe(false);
  });

  it('🔴 점이 있어도 띄어쓰기가 있으면 문장이다', () => {
    expect(looksLikeMessageKey('서버에 닿지 못했어요. 다시 시도해 주세요.')).toBe(false);
  });
});

describe('화면에 쓸 문장', () => {
  it('🔴 어느 칸이 막혔는지가 오면 그것을 먼저 보여준다', () => {
    const error = new ApiClientError('error.trip.validation', 'TRIP_VALIDATION_FAILED', 400, ['startDate: 비어 있습니다', 'travelers: 1 이상이어야 합니다']);
    const text = readableApiError(error, true);
    expect(text).toContain('startDate: 비어 있습니다');
    expect(text).toContain('travelers: 1 이상이어야 합니다');
  });

  it('🔴 키는 절대 그대로 안 보여준다', () => {
    const error = new ApiClientError('error.trip.validation', 'TRIP_VALIDATION_FAILED', 400, []);
    expect(readableApiError(error, true)).not.toContain('error.trip.validation');
  });

  it('코드는 문장 뒤 괄호에 남긴다 — 지우면 로그에서 못 되찾는다', () => {
    const error = new ApiClientError('error.trip.validation', 'TRIP_VALIDATION_FAILED', 400, []);
    expect(readableApiError(error, true)).toContain('(TRIP_VALIDATION_FAILED)');
  });

  it('서버가 진짜 문장을 주면 그대로 쓴다 — auth·place 는 한국어 문장을 준다', () => {
    const error = new ApiClientError('이미 가입된 이메일이에요.', 'EMAIL_TAKEN', 409, []);
    expect(readableApiError(error, true)).toBe('이미 가입된 이메일이에요.');
  });

  it('모르는 코드는 일반 문구로 간다', () => {
    const error = new ApiClientError('some.unknown.key', 'WHAT_IS_THIS', 500, []);
    expect(readableApiError(error, true)).toBe('요청을 처리하지 못했어요. (WHAT_IS_THIS)');
  });

  it('ApiClientError 가 아닌 것도 안전하게 다룬다', () => {
    expect(readableApiError(new Error('error.trip.validation'), true)).toBe('요청을 처리하지 못했어요.');
    expect(readableApiError(null, true)).toBe('요청을 처리하지 못했어요.');
    expect(readableApiError(new Error('그냥 실패했어요'), true)).toBe('그냥 실패했어요');
  });

  it('영어도 된다', () => {
    const error = new ApiClientError('error.trip.validation', 'TRIP_VALIDATION_FAILED', 400, []);
    expect(readableApiError(error, false)).toContain('rejected some of the details');
  });
});
