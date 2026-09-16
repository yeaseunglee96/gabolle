import { loadCollections, mergeCollections, serverToDevice, type DeviceCollections, type ServerCollection } from '../collectionsApi';

// 부슐랭을 서버로 옮긴다 (S15P21E201-1071).
//
// 🔴 이 시험이 지키는 것은 하나다 — **기기에 쌓인 것을 버리지 않는다.**
//
// 하트(S15P21E201-1013)가 이미 같은 문제를 겪었다. 기기 것을 버리면 사용자는 리스트가
// 지워진 줄 안다 — 아무도 지운 적 없는데도. 그리고 그건 아무 오류도 안 내고 일어난다.

const place = (id: string, name: string) => ({ id, name, category: null, locality: null, photoUri: null, note: null, addedAt: '2026-09-16T00:00:00Z', lat: null, lng: null });
const list = (id: string, name: string, placeIds: string[]) => ({ id, name, description: null, placeIds, createdAt: '2026-09-16T00:00:00Z' });

const device: DeviceCollections = {
  lists: [list('local-1', '바다 보러', ['p1', 'p2']), list('local-2', '기기에만 있는 리스트', ['p3'])],
  places: { p1: place('p1', '광안리'), p2: place('p2', '해운대'), p3: place('p3', '감천문화마을') },
};

const serverPage = (items: ServerCollection[]) => ({ items, count: items.length });

const serverList = (id: string, name: string, items: Array<{ placeId: string; name: string; position: number }>): ServerCollection => ({
  collectionId: id,
  name,
  description: null,
  count: items.length,
  items: items.map((item, index) => ({
    itemId: `${id}-item-${index}`, kind: 'PLACE', placeId: item.placeId, name: item.name,
    locality: null, lat: null, lng: null, photoUrl: null, note: null, position: item.position,
  })),
  updatedAt: '2026-09-16T01:00:00Z',
});

function mockServer(handler: (method: string, url: string) => Response) {
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    return handler(init?.method ?? 'GET', url);
  }) as unknown as typeof fetch;
}

const ok = (data: unknown) => new Response(JSON.stringify({ data, error: null, meta: { requestId: 'r1' } }), { status: 200, headers: { 'content-type': 'application/json' } });

describe('서버 자료를 기기 모양으로 옮긴다', () => {
  it('항목을 position 순서대로 늘어놓는다', () => {
    const converted = serverToDevice([serverList('s1', '바다 보러', [
      { placeId: 'p2', name: '해운대', position: 1 },
      { placeId: 'p1', name: '광안리', position: 0 },
    ])]);
    expect(converted.lists[0].placeIds).toEqual(['p1', 'p2']);
  });

  // 🔴 장소 id 가 없는 항목도 버리지 않는다. 서버가 준 항목 id 를 열쇠로 쓴다.
  it('장소 id 가 없는 항목도 버리지 않는다', () => {
    const raw = serverList('s1', '메모만', [{ placeId: 'x', name: '이름만 있는 것', position: 0 }]);
    raw.items[0].placeId = null;
    const converted = serverToDevice([raw]);
    expect(converted.lists[0].placeIds).toEqual(['s1-item-0']);
    expect(converted.places['s1-item-0'].name).toBe('이름만 있는 것');
  });
});

describe('기기 것과 서버 것을 합친다', () => {
  it('🔴 기기에만 있던 리스트를 버리지 않는다', () => {
    const server = serverToDevice([serverList('s1', '바다 보러', [{ placeId: 'p1', name: '광안리', position: 0 }])]);
    const { merged, onlyOnDevice } = mergeCollections(device, server);
    expect(merged.lists.map((l) => l.name)).toEqual(expect.arrayContaining(['바다 보러', '기기에만 있는 리스트']));
    expect(onlyOnDevice.map((l) => l.name)).toEqual(['기기에만 있는 리스트']);
  });

  it('🔴 같은 이름의 리스트에서 기기에만 있던 장소를 버리지 않는다', () => {
    const server = serverToDevice([serverList('s1', '바다 보러', [{ placeId: 'p1', name: '광안리', position: 0 }])]);
    const { merged } = mergeCollections(device, server);
    const sea = merged.lists.find((l) => l.name === '바다 보러');
    // 서버에 p1 만 있었지만 기기의 p2 가 살아남는다.
    expect(sea?.placeIds).toEqual(['p1', 'p2']);
  });

  it('🔴 어느 쪽 장소 자료도 버리지 않는다', () => {
    const server = serverToDevice([serverList('s1', '바다 보러', [{ placeId: 'p1', name: '광안리', position: 0 }])]);
    const { merged } = mergeCollections(device, server);
    expect(Object.keys(merged.places).sort()).toEqual(['p1', 'p2', 'p3']);
  });

  it('서버에만 있는 리스트도 그대로 들어온다', () => {
    const server = serverToDevice([serverList('s9', '다른 기기에서 만든 것', [{ placeId: 'p9', name: '태종대', position: 0 }])]);
    const { merged } = mergeCollections(device, server);
    expect(merged.lists.map((l) => l.name)).toContain('다른 기기에서 만든 것');
  });
});

describe('불러오기', () => {
  it('로그인 안 했으면 기기 것을 그대로 준다', async () => {
    const result = await loadCollections(device, null);
    expect(result.state).toBe('device-only');
    expect(result.data.lists).toHaveLength(2);
  });

  // 🔴 여기가 핵심이다. 서버가 안 되면 빈 화면이 아니라 기기 것을 보여준다.
  it('서버를 못 물어봐도 기기 것이 사라지지 않는다', async () => {
    mockServer(() => new Response(JSON.stringify({ data: null, error: { code: 'BOOM', message: '서버 오류' }, meta: { requestId: 'r' } }), { status: 500, headers: { 'content-type': 'application/json' } }));
    const result = await loadCollections(device, 'token');
    expect(result.state).toBe('device-only');
    expect(result.data.lists.map((l) => l.name)).toEqual(['바다 보러', '기기에만 있는 리스트']);
  });

  it('모양이 어긋나도 기기 것이 사라지지 않는다', async () => {
    mockServer(() => ok({ items: '목록이 아님', count: 0 }));
    const result = await loadCollections(device, 'token');
    expect(result.state).toBe('device-only');
    expect(result.data.lists).toHaveLength(2);
  });

  it('기기에만 있던 리스트를 서버로 올린다', async () => {
    const created: string[] = [];
    mockServer((method, url) => {
      if (method === 'GET') return ok(serverPage([serverList('s1', '바다 보러', [{ placeId: 'p1', name: '광안리', position: 0 }])]));
      if (method === 'POST' && url.endsWith('/collections')) {
        created.push('list');
        return ok({ collectionId: 'new-1', name: '기기에만 있는 리스트', description: null, count: 0, items: [], updatedAt: '' });
      }
      return ok({});
    });
    const result = await loadCollections(device, 'token');
    expect(result.state).toBe('success');
    expect(result.state === 'success' && result.uploaded).toBe(1);
    expect(created).toHaveLength(1);
  });

  it('🔴 올리다 실패해도 합쳐진 목록은 그대로 준다', async () => {
    mockServer((method, url) => {
      if (method === 'GET') return ok(serverPage([serverList('s1', '바다 보러', [{ placeId: 'p1', name: '광안리', position: 0 }])]));
      if (method === 'POST' && url.endsWith('/collections')) {
        return new Response(JSON.stringify({ data: null, error: { code: 'BOOM', message: '실패' }, meta: { requestId: 'r' } }), { status: 500, headers: { 'content-type': 'application/json' } });
      }
      return ok({});
    });
    const result = await loadCollections(device, 'token');
    expect(result.state).toBe('success');
    expect(result.state === 'success' && result.uploaded).toBe(0);
    // 못 올렸어도 화면에서는 안 사라진다.
    expect(result.data.lists.map((l) => l.name)).toContain('기기에만 있는 리스트');
  });
});
