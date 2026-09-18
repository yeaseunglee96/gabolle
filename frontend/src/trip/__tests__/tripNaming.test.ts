import {
  checkTripTitle,
  isModelNamed,
  loadTripNameSuggestions,
  TRIP_TITLE_MAX_LENGTH,
  updateTripTitle,
} from '../tripNaming';

// 여행 이름 짓기 연결 층

type Call = { url: string; method: string; body: unknown };
let calls: Call[] = [];

function mockServer(handler: (method: string, url: string) => Response) {
  calls = [];
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes('/auth/anonymous')) {
      return new Response(
        JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }),
        { status: 200, headers: { 'content-type': 'application/json' } },
      );
    }
    const method = init?.method ?? 'GET';
    calls.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : undefined });
    return handler(method, url);
  }) as unknown as typeof fetch;
}

const ok = (data: unknown) =>
  new Response(JSON.stringify({ data, error: null, meta: { requestId: 'r1' } }), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });

const fail = (status: number, code: string, message: string) =>
  new Response(JSON.stringify({ data: null, error: { code, message }, meta: { requestId: 'r1' } }), {
    status,
    headers: { 'content-type': 'application/json' },
  });

const TRIP = '11111111-1111-1111-1111-111111111111';

describe('이름 후보 받기', () => {
  it('후보와 출처, 버린 개수를 그대로 전한다', async () => {
    mockServer(() => ok({ suggestions: ['해운대 이틀', '광안리 밤바다'], source: 'MODEL', discardedCount: 1 }));
    const result = await loadTripNameSuggestions(TRIP, 'token');
    expect(result.state).toBe('success');
    if (result.state !== 'success') return;
    expect(result.suggestions).toEqual(['해운대 이틀', '광안리 밤바다']);
    expect(result.source).toBe('MODEL');
    expect(result.discardedCount).toBe(1);
    expect(calls[0].method).toBe('POST');
    expect(calls[0].url).toContain(`/api/v1/trips/${TRIP}/name-suggestions`);
  });

  // 여기가 핵심이다. 빈 후보를 오류로 만들면 화면이 오류 화면을 그리게 되고
  // 그건 「이름을 못 지었다」를 「무언가 고장났다」로 바꿔 말하는 것이다.
  it('후보가 하나도 없어도 실패가 아니다', async () => {
    mockServer(() => ok({ suggestions: [], source: 'TEMPLATE', discardedCount: 0 }));
    const result = await loadTripNameSuggestions(TRIP, 'token');
    expect(result.state).toBe('success');
    if (result.state !== 'success') return;
    expect(result.suggestions).toEqual([]);
  });

  it('틀로 만든 이름은 모델이 지은 것과 구분된다', () => {
    expect(isModelNamed('MODEL')).toBe(true);
    expect(isModelNamed('TEMPLATE')).toBe(false);
    // 서버가 갈래를 늘려도 「모델이 지었다」로 넘어가지 않는다.
    expect(isModelNamed('SOMETHING_NEW')).toBe(false);
  });

  it('버린 개수가 안 오면 0 으로 둔다', async () => {
    mockServer(() => ok({ suggestions: ['부산 가볍게'], source: 'MODEL' }));
    const result = await loadTripNameSuggestions(TRIP, 'token');
    expect(result.state === 'success' && result.discardedCount).toBe(0);
  });

  it('모양이 어긋나면 화면을 죽이지 않고 안내로 떨어진다', async () => {
    mockServer(() => ok({ suggestions: '해운대 이틀', source: 'MODEL', discardedCount: 0 }));
    const result = await loadTripNameSuggestions(TRIP, 'token');
    expect(result.state).toBe('error');
  });

  it('없는 여행이면 찾을 수 없다고 말한다', async () => {
    mockServer(() => fail(404, 'TRIP_NOT_FOUND', 'error.trip.notFound'));
    const result = await loadTripNameSuggestions(TRIP, 'token');
    expect(result.state).toBe('unavailable');
  });
});

describe('이름 검사 — 서버의 Trip.rename 과 같은 규칙', () => {
  it('앞뒤 공백을 뗀다', () => {
    expect(checkTripTitle('  해운대 이틀  ')).toEqual({ ok: true, title: '해운대 이틀' });
  });

  // 「이름 없음」과 「빈 이름」은 같은 것이다. 서버에 지우기 전용 경로가 없는 이유다.
  it('빈 문자열과 공백만 있는 이름은 둘 다 「이름 없음」이다', () => {
    expect(checkTripTitle('')).toEqual({ ok: true, title: null });
    expect(checkTripTitle('   ')).toEqual({ ok: true, title: null });
    expect(checkTripTitle(null)).toEqual({ ok: true, title: null });
    expect(checkTripTitle(undefined)).toEqual({ ok: true, title: null });
  });

  it('줄바꿈과 제어문자를 거부한다', () => {
    expect(checkTripTitle('해운대\n이틀')).toMatchObject({ ok: false, reason: 'controlChar' });
    expect(checkTripTitle('해운대\tㅎ')).toMatchObject({ ok: false, reason: 'controlChar' });
  });

  it('60자까지는 되고 61자부터 안 된다', () => {
    expect(checkTripTitle('가'.repeat(TRIP_TITLE_MAX_LENGTH)).ok).toBe(true);
    expect(checkTripTitle('가'.repeat(TRIP_TITLE_MAX_LENGTH + 1))).toMatchObject({ ok: false, reason: 'tooLong' });
  });

  // 서버는 코드포인트로 센다. length 로 세면 이모지 하나가 2로 세어져, 60자를 안 넘은
  // 이름이 거부당한 것처럼 보인다.
  it('이모지는 한 글자로 센다', () => {
    expect(checkTripTitle('🌊'.repeat(TRIP_TITLE_MAX_LENGTH)).ok).toBe(true);
  });
});

describe('이름 저장', () => {
  it('서버가 돌려준 이름을 쓴다 — 보낸 것을 그대로 믿지 않는다', async () => {
    mockServer(() => ok({ tripId: TRIP, title: '해운대 이틀', updatedAt: '2026-09-16T01:00:00Z' }));
    const result = await updateTripTitle(TRIP, '  해운대 이틀  ', 'token');
    expect(result).toMatchObject({ state: 'success', title: '해운대 이틀' });
    expect(calls[0].method).toBe('PUT');
    expect(calls[0].body).toEqual({ title: '해운대 이틀' });
  });

  it('공백만 보내면 이름을 지운다', async () => {
    mockServer(() => ok({ tripId: TRIP, title: null, updatedAt: '2026-09-16T01:00:00Z' }));
    const result = await updateTripTitle(TRIP, '   ', 'token');
    expect(result).toMatchObject({ state: 'success', title: null });
    expect(calls[0].body).toEqual({ title: '' });
  });

  // 너무 긴 이름은 서버까지 안 간다. 사용자를 기다리게 할 이유가 없다.
  it('60자를 넘으면 부르지도 않는다', async () => {
    mockServer(() => ok({}));
    const result = await updateTripTitle(TRIP, '가'.repeat(TRIP_TITLE_MAX_LENGTH + 1), 'token');
    expect(result.state).toBe('invalid');
    expect(calls).toHaveLength(0);
  });

  it('권한이 없으면 서버가 준 말을 그대로 쓴다', async () => {
    mockServer(() => fail(403, 'TRIP_FORBIDDEN', '이 여행을 편집할 권한이 없어요.'));
    const result = await updateTripTitle(TRIP, '해운대 이틀', 'token');
    expect(result).toMatchObject({ state: 'forbidden', message: '이 여행을 편집할 권한이 없어요.' });
  });

  it('서버가 이름을 거부하면 그 이유를 그대로 쓴다', async () => {
    mockServer(() => fail(400, 'TRIP_TITLE_INVALID', '여행 이름은 60자를 넘을 수 없다: 61자'));
    const result = await updateTripTitle(TRIP, '해운대 이틀', 'token');
    expect(result).toMatchObject({ state: 'invalid' });
  });

  it('없는 여행이면 찾을 수 없다고 말한다', async () => {
    mockServer(() => fail(404, 'TRIP_NOT_FOUND', 'error.trip.notFound'));
    const result = await updateTripTitle(TRIP, '해운대 이틀', 'token');
    expect(result.state).toBe('unavailable');
  });
});
