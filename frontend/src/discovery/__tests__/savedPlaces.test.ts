import AsyncStorage from '@react-native-async-storage/async-storage';

import { isDemoPlaceId, loadSavedPlaceIds, SAVED_PLACES_KEY, setSavedPlace } from '../savedPlaces';

// S15P21E201-1013 — 하트가 기기에만 있어서 기기를 바꾸면 사라졌다. 서버가 생겨 잇는다.
// 🔴 기기에 쌓인 것을 버리지 않는다. 버리면 사용자는 하트가 지워진 줄 안다 — 아무도 지운
// 적 없는데도. 그래서 합집합이고, 기기에만 있던 것은 한 번 올린다.
const REAL_A = '11111111-1111-1111-1111-111111111111';
const REAL_B = '22222222-2222-2222-2222-222222222222';

type Call = { url: string; method: string };
let calls: Call[] = [];

function mockServer(serverIds: string[], options: { putStatus?: number } = {}) {
  calls = [];
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    const method = init?.method ?? 'GET';
    calls.push({ url, method });
    if (method === 'GET') {
      const items = serverIds.map((placeId) => ({ placeId, savedAt: '2026-09-16T00:00:00Z' }));
      return new Response(JSON.stringify({ data: { items, count: items.length }, error: null, meta: { requestId: 'r1' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    if (method === 'PUT' && options.putStatus) {
      return new Response(JSON.stringify({ data: null, error: { code: 'PLACE_NOT_FOUND', message: '없는 장소' }, meta: { requestId: 'r1' } }), { status: options.putStatus, headers: { 'content-type': 'application/json' } });
    }
    return new Response(null, { status: 204 });
  }) as unknown as typeof fetch;
}

beforeEach(async () => {
  await AsyncStorage.clear();
});

describe('기기 하트와 계정 하트를 합친다', () => {
  it('🔴 기기에만 있던 것을 버리지 않는다 — 합치고, 서버로 한 번 올린다', async () => {
    await AsyncStorage.setItem(SAVED_PLACES_KEY, JSON.stringify([REAL_A]));
    mockServer([REAL_B]);
    const merged = await loadSavedPlaceIds('token');
    expect(merged).toContain(REAL_A);
    expect(merged).toContain(REAL_B);
    expect(calls.some((call) => call.method === 'PUT' && call.url.includes(REAL_A))).toBe(true);
  });

  it('이미 서버에 있는 것은 다시 올리지 않는다', async () => {
    await AsyncStorage.setItem(SAVED_PLACES_KEY, JSON.stringify([REAL_B]));
    mockServer([REAL_B]);
    await loadSavedPlaceIds('token');
    expect(calls.some((call) => call.method === 'PUT')).toBe(false);
  });

  it('🔴 데모 장소는 서버에 올리지 않는다 — 서버에 없는 이름표다', async () => {
    expect(isDemoPlaceId('haeundae')).toBe(true);
    await AsyncStorage.setItem(SAVED_PLACES_KEY, JSON.stringify(['haeundae']));
    mockServer([]);
    const merged = await loadSavedPlaceIds('token');
    expect(merged).toContain('haeundae');
    expect(calls.some((call) => call.method === 'PUT')).toBe(false);
  });

  it('서버를 못 물어보면 기기 것으로 보여준다 — 하트가 통째로 사라지는 것보다 낫다', async () => {
    await AsyncStorage.setItem(SAVED_PLACES_KEY, JSON.stringify([REAL_A]));
    globalThis.fetch = jest.fn(async () => new Response('', { status: 500 })) as unknown as typeof fetch;
    await expect(loadSavedPlaceIds('token')).resolves.toEqual([REAL_A]);
  });

  it('로그인 안 했으면 서버를 아예 안 부른다', async () => {
    await AsyncStorage.setItem(SAVED_PLACES_KEY, JSON.stringify([REAL_A]));
    mockServer([REAL_B]);
    await expect(loadSavedPlaceIds(null)).resolves.toEqual([REAL_A]);
    expect(calls).toHaveLength(0);
  });
});

describe('하트를 켜고 끄기', () => {
  it('켜면 서버에 PUT, 끄면 DELETE 가 간다', async () => {
    mockServer([]);
    await setSavedPlace(REAL_A, true, 'token');
    expect(calls.some((call) => call.method === 'PUT' && call.url.includes(REAL_A))).toBe(true);
    mockServer([]);
    await setSavedPlace(REAL_A, false, 'token');
    expect(calls.some((call) => call.method === 'DELETE' && call.url.includes(REAL_A))).toBe(true);
  });

  it('서버가 없는 장소라고 해도 기기의 선택은 지킨다', async () => {
    mockServer([], { putStatus: 404 });
    await expect(setSavedPlace(REAL_A, true, 'token')).resolves.toContain(REAL_A);
  });
});
