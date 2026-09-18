import { loadItineraryVersions } from './itinerary';

// 판 목록 응답이 배열 → { items, count, hasMore } 로 바뀐다.
// 서버와 화면은 따로 배포되므로 둘 다 읽어야 한다. 한쪽만 맞추면 그 사이에 목록이
// 비고, 되돌리기 버튼이 사라진다 — 돌아갈 수 있던 판이 없어진 것으로 보인다.
const entry = { version: 3, changeType: 'ITEM_REMOVE', changedAt: '2026-09-15T00:00:00Z' };

function respondWith(payload: unknown) {
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL) => {
    if (String(input).includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    return new Response(JSON.stringify({ data: payload, error: null, meta: { requestId: 'r1' } }), { status: 200, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

describe('판 목록 응답 읽기', () => {
  it('옛 모양(배열)을 그대로 읽는다', async () => {
    respondWith([entry]);
    const result = await loadItineraryVersions('it-1', 'token');
    if (result.state !== 'success') throw new Error('성공을 기대했다');
    expect(result.versions).toHaveLength(1);
    expect(result.versions[0].version).toBe(3);
  });

  it('새 모양(items 봉투)도 읽는다', async () => {
    respondWith({ items: [entry], count: 1, hasMore: false });
    const result = await loadItineraryVersions('it-1', 'token');
    if (result.state !== 'success') throw new Error('성공을 기대했다');
    expect(result.versions).toHaveLength(1);
    expect(result.versions[0].version).toBe(3);
  });

  it('items 가 비어 와도 빈 목록으로 다룬다', async () => {
    respondWith({ items: [], count: 0, hasMore: false });
    const result = await loadItineraryVersions('it-1', 'token');
    if (result.state !== 'success') throw new Error('성공을 기대했다');
    expect(result.versions).toEqual([]);
  });
});
