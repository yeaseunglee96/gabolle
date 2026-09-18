import { loadTrips } from '../trips';

// 여행 ID 없이 열린 화면이 'demo-trip' 이라는 가짜 식별자를 보내면 서버가 그것을 UUID 로
// 못 읽고 「Invalid UUID string: demo-trip」 같은 개발자용 문장을 냈다. 그 문장이 화면에
// 그대로 나왔다 —.
function respondWith(payload: unknown, status: number) {
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL) => {
    if (String(input).includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    return new Response(JSON.stringify(payload), { status, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

describe('여행 목록 실패 문구', () => {
  it('예상 못 한 서버 오류의 원문을 화면 문구로 쓰지 않는다', async () => {
    respondWith({ data: null, error: { code: 'BAD_REQUEST', message: 'Invalid UUID string: demo-trip' }, meta: { requestId: 'r1' } }, 400);
    const result = await loadTrips('token');
    expect(result.state).toBe('error');
    if (result.state === 'success') throw new Error('실패 결과를 기대했다');
    expect(result.message).not.toContain('UUID');
    expect(result.message).toBe('여행 목록을 불러오지 못했어요.');
  });
});
