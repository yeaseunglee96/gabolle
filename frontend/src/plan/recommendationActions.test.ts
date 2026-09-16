// 추천 목록의 담아두기·빼기가 남는가 — S15P21E201-975 · 1082.
//
// 그전에는 화면 상태만 바꿔서, 버튼을 눌러 "저장됨" 이 돼도 다시 들어오면 "저장" 이었다(-975).
// 그다음에는 기기에만 남아서, **여행을 함께 짜는 사람이 서로의 판단을 못 봤다**(-1082).
import AsyncStorage from '@react-native-async-storage/async-storage';

import { loadRecommendationActions, saveRecommendationAction, type RecommendationActionScope } from '@/plan/recommendationActions';

/** 여행 번호를 모르는 상태 — 서버가 tripId 를 안 주거나 비회원이다. 기기에만 적는다. */
function deviceOnly(deviceKey: string): RecommendationActionScope {
  return { tripId: null, deviceKey, accessToken: null };
}

/** 여행 번호도 출입증도 있는 상태 — 서버에 적는다. */
function onServer(tripId: string): RecommendationActionScope {
  return { tripId, deviceKey: tripId, accessToken: 'token' };
}

type Call = { url: string; method: string; body: unknown };
let calls: Call[] = [];

function mockServer(options: { items?: Array<{ placeId: string; action: string }>; status?: number } = {}) {
  calls = [];
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    const method = init?.method ?? 'GET';
    calls.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : null });
    if (options.status) {
      return new Response(JSON.stringify({ data: null, error: { code: 'NOT_FOUND', message: '없어요' }, meta: { requestId: 'r1' } }), { status: options.status, headers: { 'content-type': 'application/json' } });
    }
    if (method === 'GET') {
      const items = options.items ?? [];
      return new Response(JSON.stringify({ data: { items, count: items.length, hasMore: false }, error: null, meta: { requestId: 'r1' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    return new Response(null, { status: 204 });
  }) as unknown as typeof fetch;
}

beforeEach(async () => {
  await AsyncStorage.clear();
  mockServer();
});

describe('여행 번호를 모를 때 — 기기에만 적는다 (종전 동작)', () => {
  it('저장한 판단이 다시 읽힌다', async () => {
    await saveRecommendationAction(deviceOnly('trip-1'), 'place-a', 'saved');
    await saveRecommendationAction(deviceOnly('trip-1'), 'place-b', 'excluded');

    expect(await loadRecommendationActions(deviceOnly('trip-1'))).toEqual({
      'place-a': 'saved',
      'place-b': 'excluded',
    });
  });

  // 담아두기·빼기는 그 여행의 후보에 대한 판단이지 장소 자체에 대한 판단이 아니다.
  it('여행이 다르면 서로 안 보인다', async () => {
    await saveRecommendationAction(deviceOnly('trip-1'), 'place-a', 'excluded');

    expect(await loadRecommendationActions(deviceOnly('trip-2'))).toEqual({});
  });

  it('되돌리면 그 장소의 판단만 지워진다', async () => {
    await saveRecommendationAction(deviceOnly('trip-1'), 'place-a', 'saved');
    await saveRecommendationAction(deviceOnly('trip-1'), 'place-b', 'saved');

    await saveRecommendationAction(deviceOnly('trip-1'), 'place-a', null);

    expect(await loadRecommendationActions(deviceOnly('trip-1'))).toEqual({ 'place-b': 'saved' });
  });

  it('저장된 값이 깨져 있어도 화면이 죽지 않는다', async () => {
    await AsyncStorage.setItem('@gabolle/recommendation-actions:trip-1', '{ 망가진 값');

    expect(await loadRecommendationActions(deviceOnly('trip-1'))).toEqual({});
  });

  it('🔴 서버를 아예 안 부른다 — 여행 번호가 없으면 보낼 주소가 없다', async () => {
    await saveRecommendationAction(deviceOnly('trip-1'), 'place-a', 'saved');
    await loadRecommendationActions(deviceOnly('trip-1'));

    expect(calls).toHaveLength(0);
  });
});

// S15P21E201-1082 — 백엔드(RecommendationActionController, -1013)는 있는데 프론트가
// 한 번도 부르지 않았다. 운영 실측 호출 건수 0건.
describe('여행 번호를 알 때 — 서버에 적는다', () => {
  it('🔴 담아두기는 PUT {"action":"SAVED"} 를 한 번 보낸다', async () => {
    await saveRecommendationAction(onServer('trip-1'), 'place-a', 'saved');

    const put = calls.filter((call) => call.method === 'PUT');
    expect(put).toHaveLength(1);
    expect(put[0].url).toContain('/api/v1/trips/trip-1/recommendation-actions/place-a');
    expect(put[0].body).toEqual({ action: 'SAVED' });
  });

  it('🔴 빼기는 PUT {"action":"EXCLUDED"} 를 보낸다 — 화면값과 서버값 표기가 다르다', async () => {
    await saveRecommendationAction(onServer('trip-1'), 'place-a', 'excluded');

    expect(calls.find((call) => call.method === 'PUT')?.body).toEqual({ action: 'EXCLUDED' });
  });

  it('되돌리기는 DELETE 를 보낸다', async () => {
    await saveRecommendationAction(onServer('trip-1'), 'place-a', null);

    const remove = calls.filter((call) => call.method === 'DELETE');
    expect(remove).toHaveLength(1);
    expect(remove[0].url).toContain('/recommendation-actions/place-a');
  });

  it('서버 목록의 SAVED·EXCLUDED 가 화면값으로 바뀌어 나온다', async () => {
    mockServer({ items: [{ placeId: 'place-a', action: 'SAVED' }, { placeId: 'place-b', action: 'EXCLUDED' }] });

    expect(await loadRecommendationActions(onServer('trip-1'))).toEqual({
      'place-a': 'saved',
      'place-b': 'excluded',
    });
  });

  it('서버가 모르는 값을 주면 그 줄만 버린다 — 화면 전체를 비우지 않는다', async () => {
    mockServer({ items: [{ placeId: 'place-a', action: 'SAVED' }, { placeId: 'place-b', action: 'MAYBE' }] });

    expect(await loadRecommendationActions(onServer('trip-1'))).toEqual({ 'place-a': 'saved' });
  });

  // 🔴 서버 것으로 기기를 덮어 쓴다. 합치면, 다른 기기에서 **거둔** 판단이 되살아난다.
  it('다른 기기에서 거둔 판단이 되살아나지 않는다', async () => {
    await saveRecommendationAction(onServer('trip-1'), 'place-a', 'saved');
    mockServer({ items: [] });

    expect(await loadRecommendationActions(onServer('trip-1'))).toEqual({});
  });
});

// RecommendationActionController 는 아직 back/dev 에만 있다 — main·back/main 에는 없다.
// 그래서 404 대비는 선택이 아니라 필수다.
describe('서버가 그 경로를 아직 모를 때 — 기기 값으로 그린다', () => {
  it('🔴 404 여도 화면은 앞서 내린 판단을 그대로 보여준다', async () => {
    await saveRecommendationAction(deviceOnly('trip-1'), 'place-a', 'saved');
    mockServer({ status: 404 });

    expect(await loadRecommendationActions(onServer('trip-1'))).toEqual({ 'place-a': 'saved' });
  });

  it('🔴 404 여도 기기에는 적힌다 — 버튼이 안 들리는 것처럼 보이면 안 된다', async () => {
    mockServer({ status: 404 });
    await saveRecommendationAction(onServer('trip-1'), 'place-a', 'excluded');

    mockServer({ status: 404 });
    expect(await loadRecommendationActions(onServer('trip-1'))).toEqual({ 'place-a': 'excluded' });
  });

  it('서버가 500 이어도 같다', async () => {
    await saveRecommendationAction(deviceOnly('trip-1'), 'place-a', 'saved');
    mockServer({ status: 500 });

    expect(await loadRecommendationActions(onServer('trip-1'))).toEqual({ 'place-a': 'saved' });
  });
});
