// 기록에 카카오에서 고른 장소를 싣는다 — S15P21E201-1527 (서버 S15P21E201-1426).
//
// 🔴 예전에는 장소 고르기가 카카오 결과를 합칠 때 이름·주소만 남겨서, 카카오 장소를 고르면
//    지역 글자만 저장되고 장소는 안 이어졌다(원글 28건 중 5건만 이어짐, 2026-09-23 운영 DB).
import { mergeRegionCandidates } from '../regionSearch';
import { createStory } from '../stories';

const 해운대 = {
  name: '해운대해수욕장',
  address: '부산 해운대구 우동',
  lat: 35.1585,
  lng: 129.1598,
  externalId: '7913306',
  source: 'KAKAO_LOCAL' as const,
};

let lastBody: Record<string, unknown> | null = null;

function respondOk() {
  lastBody = null;
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    lastBody = JSON.parse(String(init?.body ?? '{}'));
    const story = { id: 'st-1', author: { id: 'u', displayName: '진미리' }, body: 'b', images: [], visibility: 'PUBLIC', publishAt: '', createdAt: '', updatedAt: '', mine: true, published: true, replyCount: 0 };
    return new Response(JSON.stringify({ data: story, error: null, meta: { requestId: 'r1' } }), { status: 200, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

describe('장소 고르기 — 카카오 결과', () => {
  it('🔴 카카오 결과를 고르면 장소 스냅샷이 남는다 — source 는 바꾸지 않는다', () => {
    const [picked] = mergeRegionCandidates([], [해운대]);
    expect(picked.placeId).toBeUndefined();
    expect(picked.place).toEqual({ source: 'KAKAO_LOCAL', externalId: '7913306', name: '해운대해수욕장', address: '부산 해운대구 우동', lat: 35.1585, lng: 129.1598 });
  });

  it('대체 목록(INTERNAL_FALLBACK)도 그대로 싣는다', () => {
    const [picked] = mergeRegionCandidates([], [{ ...해운대, source: 'INTERNAL_FALLBACK' as const }]);
    expect(picked.place?.source).toBe('INTERNAL_FALLBACK');
  });

  it('🔴 서버가 거절할 모양이면 안 싣는다 — 좌표가 없거나 식별자가 없으면 지역 글자만', () => {
    expect(mergeRegionCandidates([], [{ ...해운대, lat: Number.NaN }])[0].place).toBeUndefined();
    expect(mergeRegionCandidates([], [{ ...해운대, externalId: '' }])[0].place).toBeUndefined();
    expect(mergeRegionCandidates([], [{ name: '어떤 카페', address: '부산광역시 중구' }])[0].place).toBeUndefined();
  });

  it('우리 장소에는 스냅샷이 없다 — placeId 로 잇는다', () => {
    const [ours] = mergeRegionCandidates([{ placeId: 'p-1', nameKo: '감천문화마을', address: '부산광역시 사하구' }], []);
    expect(ours.placeId).toBe('p-1');
    expect(ours.place).toBeUndefined();
  });
});

describe('createStory — place 싣기', () => {
  const { place } = mergeRegionCandidates([], [해운대])[0];

  it('🔴 placeId 가 없으면 place 를 싣는다', async () => {
    respondOk();
    await createStory({ body: '바다', imageUrls: [], place, accessToken: 'token' });
    expect(lastBody?.place).toEqual(place);
    expect(lastBody?.placeId).toBeUndefined();
  });

  it('placeId 가 있으면 place 는 안 싣는다 — 우리 장소가 이긴다', async () => {
    respondOk();
    await createStory({ body: '바다', imageUrls: [], placeId: 'p-1', place, accessToken: 'token' });
    expect(lastBody?.placeId).toBe('p-1');
    expect(lastBody).not.toHaveProperty('place');
  });

  it('둘 다 없으면 지금처럼 장소 없는 기록이다', async () => {
    respondOk();
    await createStory({ body: '바다', imageUrls: [], accessToken: 'token' });
    expect(lastBody).not.toHaveProperty('place');
    expect(lastBody).not.toHaveProperty('placeId');
  });
});
