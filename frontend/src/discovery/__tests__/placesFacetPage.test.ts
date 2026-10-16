// 갈래 목록을 커서로 이어 받는다 — S15P21E201-2060 (2026-10-16 웹 점검: 축제 76곳이 20곳에서 끊겼다).
//
// 🔴 이 시험이 지키는 것
//    1. 다음 쪽은 서버가 준 커서를 `cursor` 로 되돌려 보내 받는다.
//    2. 옛 서버는 hasNext:true 에 nextCursor:null 이었다 — 이어 받을 길이 없으니 «끝»이다(눌러도 안 되는 「더 보기」를 그리지 않는다).
//    3. 첫 쪽만 필요한 곳(홈 칸·섞어 보기)이 쓰는 getPlacesByFacet 은 그대로 첫 쪽의 장소 목록을 준다.
import { apiRequest } from '@/api/client';
import { getPlacesByFacet, getPlacesByFacetPage } from '../places';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn() }));
const request = jest.mocked(apiRequest);

const dto = (names: string[], next: { hasNext: boolean; nextCursor: string | null }) => ({
  items: names.map((nameKo, index) => ({ placeId: `${nameKo}-${index}`, nameKo, nameEn: null, category: 'FESTIVAL_EVENT', address: '부산', lat: 35.1, lng: 129.0, matchedField: null })),
  limit: 20,
  rankTruncated: false,
  ...next,
});

const lastPath = () => String(request.mock.calls[request.mock.calls.length - 1][0]);
const lastQuery = () => new URLSearchParams(lastPath().split('?')[1]);

beforeEach(() => request.mockReset());

describe('getPlacesByFacetPage', () => {
  it('🔴 첫 쪽은 커서 없이 부르고, 서버가 준 nextCursor 를 그대로 돌려준다', async () => {
    request.mockResolvedValue(dto(['부산연등회', '부산항축제'], { hasNext: true, nextCursor: 'abc_-123' }));

    const page = await getPlacesByFacetPage('INTEREST_TAG', 'FESTIVAL', 20);

    expect(lastPath().startsWith('/api/v1/places?')).toBe(true);
    expect(Object.fromEntries(lastQuery())).toEqual({ facetType: 'INTEREST_TAG', facetKey: 'FESTIVAL', limit: '20' });
    expect(page.items.map((item) => item.nameKo)).toEqual(['부산연등회', '부산항축제']);
    expect(page.nextCursor).toBe('abc_-123');
  });

  it('🔴 다음 쪽은 받은 커서를 cursor 로 되돌려 보낸다', async () => {
    request.mockResolvedValue(dto(['금정산성축제'], { hasNext: false, nextCursor: null }));

    const page = await getPlacesByFacetPage('INTEREST_TAG', 'FESTIVAL', 20, 'abc_-123');

    expect(Object.fromEntries(lastQuery())).toEqual({ facetType: 'INTEREST_TAG', facetKey: 'FESTIVAL', limit: '20', cursor: 'abc_-123' });
    expect(page.nextCursor).toBeNull();
  });

  it('🔴 옛 서버(hasNext:true 인데 nextCursor:null)는 끝으로 본다 — 이어 받을 길이 없다', async () => {
    request.mockResolvedValue(dto(['부산연등회'], { hasNext: true, nextCursor: null }));

    expect((await getPlacesByFacetPage('INTEREST_TAG', 'FESTIVAL')).nextCursor).toBeNull();
  });

  it('hasNext 가 거짓이면 커서가 와도 끝이다', async () => {
    request.mockResolvedValue(dto(['부산연등회'], { hasNext: false, nextCursor: 'stale' }));

    expect((await getPlacesByFacetPage('INTEREST_TAG', 'FESTIVAL')).nextCursor).toBeNull();
  });

  it('요청이 실패하면 그대로 던진다 — 화면이 「더 불러오지 못했어요」를 보인다', async () => {
    request.mockRejectedValue(new Error('boom'));

    await expect(getPlacesByFacetPage('INTEREST_TAG', 'FESTIVAL', 20, 'c1')).rejects.toThrow('boom');
  });
});

describe('getPlacesByFacet — 첫 쪽만 필요한 곳', () => {
  it('장소 목록만 돌려주고 커서는 보내지 않는다', async () => {
    request.mockResolvedValue(dto(['부산연등회'], { hasNext: true, nextCursor: 'abc' }));

    const items = await getPlacesByFacet('INTEREST_TAG', 'FESTIVAL', 5);

    expect(items.map((item) => item.nameKo)).toEqual(['부산연등회']);
    expect(Object.fromEntries(lastQuery())).toEqual({ facetType: 'INTEREST_TAG', facetKey: 'FESTIVAL', limit: '5' });
  });
});
