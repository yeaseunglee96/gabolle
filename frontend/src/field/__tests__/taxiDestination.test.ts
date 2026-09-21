// — 택시 목적지 고르기.
import {
  canSearchDestination,
  destinationSubtitle,
  normalizeDestinationQuery,
  searchTaxiDestinations,
  MIN_DESTINATION_QUERY_LENGTH,
} from '@/field/taxiDestination';
import type { PlaceSearchItem } from '@/discovery/places';

const place = (over: Partial<PlaceSearchItem> = {}): PlaceSearchItem => ({
  placeId: 'p1', nameKo: '해운대 관광특구', nameEn: null, category: 'CITY',
  address: '부산광역시 해운대구', lat: 35.16, lng: 129.16, ...over,
});

let calls: string[] = [];
function mockServer(options: { status?: number; items?: unknown[] } = {}) {
  calls = [];
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    calls.push(url);
    if (options.status) {
      return new Response(JSON.stringify({ data: null, error: { code: 'X', message: '안 돼요' }, meta: { requestId: 'r1' } }), { status: options.status, headers: { 'content-type': 'application/json' } });
    }
    const items = options.items ?? [place()];
    return new Response(JSON.stringify({ data: { items, limit: 8, nextCursor: null, hasNext: false, rankTruncated: false }, error: null, meta: { requestId: 'r1' } }), { status: 200, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

beforeEach(() => mockServer());

describe('검색어 다듬기', () => {
  it('앞뒤 공백을 뗀다', () => {
    expect(normalizeDestinationQuery('  해운대  ')).toBe('해운대');
  });

  it('🔴 사이 공백을 하나로 줄인다 — 두 칸을 친 사람이 결과를 못 받는 일이 실제로 생긴다', () => {
    expect(normalizeDestinationQuery('해운대  해수욕장')).toBe('해운대 해수욕장');
  });
});

describe('언제 검색을 쏘나', () => {
  it(`🔴 ${MIN_DESTINATION_QUERY_LENGTH}글자 미만이면 안 쏜다 — 한 글자에 부산 전체가 걸린다`, () => {
    expect(canSearchDestination('부')).toBe(false);
    expect(canSearchDestination('부산')).toBe(true);
  });

  it('🔴 공백만 친 것은 입력이 아니다 — 「결과 없음」을 띄우면 고장으로 읽힌다', async () => {
    expect(canSearchDestination('   ')).toBe(false);
    await expect(searchTaxiDestinations('   ')).resolves.toEqual({ state: 'idle' });
    expect(calls).toHaveLength(0);
  });

  it('짧은 검색어로는 서버를 부르지 않는다', async () => {
    await expect(searchTaxiDestinations('부')).resolves.toEqual({ state: 'idle' });
    expect(calls).toHaveLength(0);
  });

  it('다듬은 검색어를 보낸다', async () => {
    await searchTaxiDestinations('  해운대  ');
    expect(calls[0]).toContain(encodeURIComponent('해운대'));
    expect(calls[0]).not.toContain('%20%20');
  });
});

describe('🔴 고를 수 없는 것을 목록에 두지 않는다', () => {
  it('좌표도 주소도 없는 줄은 버린다 — 골라 봐야 기사에게 보여줄 것이 없다', async () => {
    mockServer({ items: [place({ placeId: 'a', address: '', lat: Number.NaN }), place({ placeId: 'b' })] });
    const out = await searchTaxiDestinations('해운대');
    if (out.state !== 'ready') throw new Error('ready 여야 한다');
    expect(out.items.map((i) => i.placeId)).toEqual(['b']);
  });

  it('이름이 없는 줄도 버린다', async () => {
    mockServer({ items: [place({ placeId: 'a', nameKo: '' })] });
    await expect(searchTaxiDestinations('해운대')).resolves.toEqual({ state: 'empty' });
  });

  it('주소는 없어도 좌표가 있으면 남긴다 — 택시 카드가 좌표로도 말을 만든다', async () => {
    mockServer({ items: [place({ address: '' })] });
    const out = await searchTaxiDestinations('해운대');
    expect(out.state).toBe('ready');
  });
});

describe('결과 0개는 실패가 아니다', () => {
  it('🔴 정말로 그런 이름이 없을 수 있다 — 없는 고장을 만들지 않는다', async () => {
    mockServer({ items: [] });
    await expect(searchTaxiDestinations('없는장소')).resolves.toEqual({ state: 'empty' });
  });
});

describe('안 될 때 — 이유마다 다르게 말한다', () => {
  it.each([401, 403])('HTTP %i 는 signed-out', async (status) => {
    mockServer({ status });
    await expect(searchTaxiDestinations('해운대')).resolves.toEqual({ state: 'blocked', reason: 'signed-out' });
  });

  it.each([404, 501])('HTTP %i 는 not-built', async (status) => {
    mockServer({ status });
    await expect(searchTaxiDestinations('해운대')).resolves.toEqual({ state: 'blocked', reason: 'not-built' });
  });

  it.each([500, 502])('HTTP %i 는 server', async (status) => {
    mockServer({ status });
    await expect(searchTaxiDestinations('해운대')).resolves.toEqual({ state: 'blocked', reason: 'server' });
  });
});

describe('둘째 줄', () => {
  it('주소를 적는다', () => {
    expect(destinationSubtitle(place())).toBe('부산광역시 해운대구');
  });

  it('🔴 주소가 없으면 아무것도 안 적는다 — 「정보 없음」은 줄만 차지한다', () => {
    expect(destinationSubtitle(place({ address: '' }))).toBeNull();
    expect(destinationSubtitle(place({ address: '   ' }))).toBeNull();
  });
});
