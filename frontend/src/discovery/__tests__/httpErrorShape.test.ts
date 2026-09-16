// S15P21E201-1081 — 「서버가 잠깐 못 받는다」와 「이 기능이 아직 없다」를 가르는 규칙을 고정한다.
//
// 2026-09-16 운영에서 배포 중 nginx 가 502(본문은 HTML)를 주는 몇십 초 동안, 로컬 탐색과
// 갈래 조회가 "API가 아직 준비되지 않았어요" 를 띄웠다. 사용자는 그것을 **아직 만들지 않은
// 기능**으로 읽고 나갔다. 두 화면이 INVALID_RESPONSE(=JSON 이 아니다)만 보고 갈랐기 때문이다.
//
// 그래서 여기서 재는 것은 문구가 아니라 **갈림길 그 자체**다 — 404·501 만 '아직 없다' 이고
// 5xx 는 '잠시 후 다시' 다. 문구는 화면마다 다르지만 갈림길이 틀리면 전부 틀린다.
import { ApiClientError, isServerError, subscribeApiAvailability } from '@/api/client';
import { getFacets } from '../localExplore';
import { getPlaceCategories } from '../placeCategories';

/** nginx 가 502·503·504 를 줄 때의 모양 — 상태는 5xx 인데 본문이 JSON 이 아니다. */
function respondWithGatewayHtml(status: number) {
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL) => {
    if (String(input).includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    return new Response(`<html><body><h1>${status} Bad Gateway</h1></body></html>`, { status, headers: { 'content-type': 'text/html' } });
  }) as unknown as typeof fetch;
}

/** 서버가 "그런 경로는 없다" 고 제대로 답하는 모양. */
function respondWithJsonStatus(status: number, code: string) {
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL) => {
    if (String(input).includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    return new Response(JSON.stringify({ data: null, error: { code, message: '없어요' }, meta: { requestId: 'r1' } }), { status, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

describe('배포 중 502 를 「아직 준비 안 됨」으로 말하지 않는다', () => {
  it.each([502, 503, 504, 500])('🔴 로컬 탐색 — HTTP %i 는 unavailable 이 아니다', async (status) => {
    respondWithGatewayHtml(status);
    const result = await getFacets();
    expect(result.state).toBe('error');
    // 이 한 줄이 이 티켓의 전부다 — 여기가 'unavailable' 이면 화면이 "아직 없는 기능" 이라 말한다.
    expect(result.state).not.toBe('unavailable');
  });

  it.each([502, 503, 504, 500])('🔴 갈래 조회 — HTTP %i 는 unavailable 이 아니다', async (status) => {
    respondWithGatewayHtml(status);
    const result = await getPlaceCategories();
    expect(result.state).toBe('error');
    expect(result.state).not.toBe('unavailable');
  });

  it('5xx 문구는 사용자의 인터넷을 탓하지 않는다', async () => {
    respondWithGatewayHtml(502);
    const result = await getFacets();
    if (result.state === 'success') throw new Error('실패 응답이 와야 한다');
    expect(result.message).toContain('잠시 후 다시');
    expect(result.message).not.toContain('인터넷');
  });
});

describe('404·501 은 종전대로 「아직 준비 안 됨」이다', () => {
  it.each([404, 501])('로컬 탐색 — HTTP %i', async (status) => {
    respondWithJsonStatus(status, 'NOT_FOUND');
    const result = await getFacets();
    expect(result.state).toBe('unavailable');
  });

  it.each([404, 501])('갈래 조회 — HTTP %i', async (status) => {
    respondWithJsonStatus(status, 'NOT_FOUND');
    const result = await getPlaceCategories();
    expect(result.state).toBe('unavailable');
  });
});

describe('서버 연결 배너', () => {
  it('🔴 5xx 를 받으면 「서버에 연결할 수 없어요」 배너가 켜진다', async () => {
    // 예전에는 응답이 오기만 하면 무조건 "다시 연결됨" 으로 봤다. nginx 가 502 를 주는
    // 동안에도 응답은 오므로, 앱은 서버가 멀쩡하다고 판단하고 배너를 끝내 안 띄웠다.
    const seen: boolean[] = [];
    const unsubscribe = subscribeApiAvailability((unavailable) => seen.push(unavailable));
    respondWithGatewayHtml(502);
    await getFacets();
    unsubscribe();
    expect(seen.at(-1)).toBe(true);
  });

  it('404 는 배너를 켜지 않는다 — 서버는 멀쩡하고 그 경로만 없는 것이다', async () => {
    const seen: boolean[] = [];
    const unsubscribe = subscribeApiAvailability((unavailable) => seen.push(unavailable));
    respondWithJsonStatus(404, 'NOT_FOUND');
    await getFacets();
    unsubscribe();
    expect(seen.at(-1)).toBe(false);
  });
});

describe('isServerError — 갈림길을 재는 함수 자체', () => {
  it.each([500, 502, 503, 504])('HTTP %i 는 서버 쪽 실패다', (status) => {
    expect(isServerError(new ApiClientError('x', 'SERVER_ERROR', status))).toBe(true);
  });

  it.each([400, 401, 404, 409])('HTTP %i 는 서버 쪽 실패가 아니다', (status) => {
    expect(isServerError(new ApiClientError('x', 'NOT_FOUND', status))).toBe(false);
  });

  // 🔴 501 은 숫자로는 5xx 지만 이 저장소에서는 404 와 한 짝으로 "아직 안 만들었다" 다.
  //    여기 들어가면 "잠시 후 다시" 라고 말하게 되는데, 기다려도 생기지 않으므로 거짓말이다.
  it('HTTP 501 은 5xx 이지만 서버 쪽 실패로 세지 않는다', () => {
    expect(isServerError(new ApiClientError('x', 'NOT_IMPLEMENTED', 501))).toBe(false);
  });

  it('ApiClientError 가 아닌 것은 거짓이다 — status 만 닮은 값에 속지 않는다', () => {
    expect(isServerError({ status: 500 })).toBe(false);
    expect(isServerError(new Error('boom'))).toBe(false);
  });
});
