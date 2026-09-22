import { COLLECTION_LIMITS, loadCollections, mergeCollections, serverToDevice, uploadBlockReason, type DeviceCollections, type ServerCollection } from '../collectionsApi';

// 부슐랭을 서버로 옮긴다.

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

  // 장소 id 가 없는 항목도 버리지 않는다. 서버가 준 항목 id 를 열쇠로 쓴다.
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

  // 여기가 핵심이다. 서버가 안 되면 빈 화면이 아니라 기기 것을 보여준다.
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
    expect(created).toHaveLength(1);
    // 2 다 — 리스트 하나(기기에만 있던 것)와 장소 하나(p2, 이미 서버에 있는 리스트에
    // 기기에서 담은 것). p2 는전에는 영영 안 올라갔다.
    expect(result.state === 'success' && result.uploaded).toBe(2);
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
    // 리스트 만들기는 실패했고(500), 기존 리스트에 담은 장소 p2 는 올라갔다
    // 둘은 서로 다른 요청이라 하나가 실패해도 다른 하나는 간다.
    expect(result.state === 'success' && result.uploaded).toBe(1);
    // 못 올린 리스트는 화면에서 안 사라진다. 올린 뒤 서버 것을 다시 받아오더라도
    // 이것만은 얹어서 남긴다 — 「기기에 쌓인 것을 버리지 않는다」.
    expect(result.data.lists.map((l) => l.name)).toContain('기기에만 있는 리스트');
  });
});

describe('서버가 받아 줄 수 없는 리스트', () => {
  const tooLongName = '가'.repeat(COLLECTION_LIMITS.name + 1);

  it('이름이 상한을 넘으면 올릴 수 없다고 판정한다', () => {
    expect(uploadBlockReason(list('x', tooLongName, []))).toBe('name-too-long');
    expect(uploadBlockReason(list('x', '가'.repeat(COLLECTION_LIMITS.name), []))).toBeNull();
  });

  it('설명이 상한을 넘어도 판정한다', () => {
    const withDescription = { ...list('x', '짧은 이름', []), description: '나'.repeat(COLLECTION_LIMITS.description + 1) };
    expect(uploadBlockReason(withDescription)).toBe('description-too-long');
  });

  it('보내 보지도 않는다 — 몇 번을 보내도 같은 400 이다', async () => {
    const posted: string[] = [];
    mockServer((method, url) => {
      if (method === 'GET') return ok(serverPage([]));
      if (method === 'POST' && url.endsWith('/collections')) { posted.push(url); return ok({ collectionId: 'n1', name: '', description: null, count: 0, items: [], updatedAt: '' }); }
      return ok({});
    });
    const stuck: DeviceCollections = { lists: [list('local-long', tooLongName, [])], places: {} };
    const result = await loadCollections(stuck, 'token');
    expect(posted).toHaveLength(0);
    expect(result.state === 'success' && result.blocked).toBe(1);
    // 올리지 못해도 화면에서는 안 사라진다.
    expect(result.data.lists.map((l) => l.name)).toContain(tooLongName);
  });

  it('400 을 받은 것은 못 올린 것으로 세고, 500 은 다음에 다시 올릴 것으로 남긴다', async () => {
    const reject = (status: number) => mockServer((method, url) => {
      if (method === 'GET') return ok(serverPage([]));
      if (method === 'POST' && url.endsWith('/collections')) {
        return new Response(JSON.stringify({ data: null, error: { code: 'BAD', message: '거절' }, meta: { requestId: 'r' } }), { status, headers: { 'content-type': 'application/json' } });
      }
      return ok({});
    });
    const one: DeviceCollections = { lists: [list('local-1', '바다 보러', [])], places: {} };

    reject(400);
    const rejected = await loadCollections(one, 'token');
    expect(rejected.state === 'success' && rejected.blocked).toBe(1);

    reject(500);
    const retryable = await loadCollections(one, 'token');
    expect(retryable.state === 'success' && retryable.blocked).toBe(0);
    expect(retryable.state === 'success' && retryable.uploaded).toBe(0);
  });
});

// — 손으로 추가한 장소가 서버로 안 올라가던 것.
import { buildItemRequest, isServerId } from '../collectionsApi';

describe('isServerId — 서버가 아는 장소인가', () => {
  it('서버 장소 id(UUID)를 알아본다', () => {
    expect(isServerId('7b8cd3bc-7cef-48ef-bda0-335bec095fc2')).toBe(true);
    expect(isServerId('7B8CD3BC-7CEF-48EF-BDA0-335BEC095FC2')).toBe(true);
  });

  it('기기가 만든 id 를 서버 것으로 오해하지 않는다', () => {
    // CollectionProvider 의 uid 가 실제로 만드는 모양이다.
    expect(isServerId('mfjk2x-a7b3c1')).toBe(false);
    expect(isServerId('p1')).toBe(false);
    expect(isServerId('')).toBe(false);
    // 자릿수가 하나 모자란 것도 통과시키지 않는다.
    expect(isServerId('7b8cd3bc-7cef-48ef-bda0-335bec095fc')).toBe(false);
  });
});

describe('buildItemRequest — 담을 것을 서버 말로 옮긴다', () => {
  const custom = { ...place('mfjk2x-a7b3c1', '할매국밥'), locality: '부산 서구', lat: 35.1, lng: 129.0, note: '아침에' };
  const fromServer = { ...place('7b8cd3bc-7cef-48ef-bda0-335bec095fc2', '감천문화마을'), note: '오후에' };

  it('손으로 추가한 장소는 CUSTOM 으로 보낸다 — 이름과 좌표가 그대로 실린다', () => {
    expect(buildItemRequest(custom.id, custom)).toEqual({
      kind: 'CUSTOM', name: '할매국밥', locality: '부산 서구', lat: 35.1, lng: 129.0, note: '아침에',
    });
  });

  it('CUSTOM 에는 placeId 를 아예 넣지 않는다 — 그게 400 의 원인이었다', () => {
    expect(buildItemRequest(custom.id, custom)).not.toHaveProperty('placeId');
  });

  it('서버 장소는 PLACE 로 보낸다', () => {
    expect(buildItemRequest(fromServer.id, fromServer)).toEqual({
      kind: 'PLACE', placeId: '7b8cd3bc-7cef-48ef-bda0-335bec095fc2', note: '오후에',
    });
  });

  it('메모가 없으면 null 로 채운다', () => {
    const noNote = place('mfjk2x-a7b3c1', '이름만 있는 곳');
    expect(buildItemRequest(noNote.id, noNote)).toMatchObject({ kind: 'CUSTOM', note: null, locality: null, lat: null, lng: null });
  });
});

// — 이미 서버에 있는 리스트에 담은 장소가 서버로 안 올라가던 것.
import { restoreUnuploaded } from '../collectionsApi';

describe('loadCollections — 기존 리스트에 담은 장소 올리기 (S15P21E201-1133)', () => {
  const deviceWithExtra: DeviceCollections = {
    lists: [list('local-1', '바다 보러', ['7b8cd3bc-7cef-48ef-bda0-335bec095fc2', 'mfjk2x-a7b3c1'])],
    places: {
      '7b8cd3bc-7cef-48ef-bda0-335bec095fc2': place('7b8cd3bc-7cef-48ef-bda0-335bec095fc2', '광안리'),
      'mfjk2x-a7b3c1': place('mfjk2x-a7b3c1', '할매국밥'),
    },
  };

  // 서버에는 광안리만 있다. 할매국밥은 기기에서 손으로 담은 것이다.
  const before = serverList('srv-1', '바다 보러', [{ placeId: '7b8cd3bc-7cef-48ef-bda0-335bec095fc2', name: '광안리', position: 0 }]);
  const after: ServerCollection = {
    ...before,
    count: 2,
    items: [
      ...before.items,
      { itemId: 'srv-1-item-9', kind: 'CUSTOM', placeId: null, name: '할매국밥', locality: null, lat: null, lng: null, photoUrl: null, note: null, position: 1 },
    ],
  };

  it('기존 리스트에 담은 장소를 POST 로 올리고, 서버 열쇠로 이름표를 바꾼다', async () => {
    const posted: string[] = [];
    let gets = 0;
    mockServer((method, url) => {
      if (method === 'POST' && url.includes('/items')) {
        posted.push(url);
        return new Response(JSON.stringify({ data: {}, error: null, meta: { requestId: 'r' } }), { status: 201, headers: { 'content-type': 'application/json' } });
      }
      gets += 1;
      const page = serverPage([gets === 1 ? before : after]);
      return new Response(JSON.stringify({ data: page, error: null, meta: { requestId: 'r' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    });

    const result = await loadCollections(deviceWithExtra, 'token');

    expect(posted).toHaveLength(1);
    expect(posted[0]).toContain('/me/collections/srv-1/items');
    expect(gets).toBe(2); // 올린 뒤 다시 받아왔다

    if (result.state !== 'success') throw new Error('성공이어야 한다');
    const ids = result.data.lists[0].placeIds;
    // 기기가 만든 uid 는 사라지고 서버가 준 itemId 가 자리를 잡는다
    expect(ids).toContain('srv-1-item-9');
    expect(ids).not.toContain('mfjk2x-a7b3c1');
    expect(result.uploaded).toBe(1);
  });

  it('올릴 것이 없으면 다시 받아오지 않는다', async () => {
    let gets = 0;
    mockServer((method, url) => {
      if (method === 'POST') throw new Error('올릴 것이 없는데 POST 했다');
      gets += 1;
      return new Response(JSON.stringify({ data: serverPage([after]), error: null, meta: { requestId: 'r' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    });

    const device: DeviceCollections = {
      lists: [list('local-1', '바다 보러', ['7b8cd3bc-7cef-48ef-bda0-335bec095fc2', 'srv-1-item-9'])],
      places: {
        '7b8cd3bc-7cef-48ef-bda0-335bec095fc2': place('7b8cd3bc-7cef-48ef-bda0-335bec095fc2', '광안리'),
        'srv-1-item-9': place('srv-1-item-9', '할매국밥'),
      },
    };
    const result = await loadCollections(device, 'token');

    expect(gets).toBe(1);
    if (result.state !== 'success') throw new Error('성공이어야 한다');
    expect(result.uploaded).toBe(0);
  });

  it('🔴 올리다 실패한 장소는 기기에 남는다 — 기기에 쌓인 것을 버리지 않는다', async () => {
    let gets = 0;
    mockServer((method, url) => {
      if (method === 'POST' && url.includes('/items')) {
        return new Response(JSON.stringify({ data: null, error: { code: 'BAD', message: '안 됨' }, meta: { requestId: 'r' } }), { status: 400, headers: { 'content-type': 'application/json' } });
      }
      gets += 1;
      return new Response(JSON.stringify({ data: serverPage([before]), error: null, meta: { requestId: 'r' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    });

    const result = await loadCollections(deviceWithExtra, 'token');

    if (result.state !== 'success') throw new Error('성공이어야 한다');
    expect(result.blocked).toBe(1);
    expect(result.data.lists[0].placeIds).toContain('mfjk2x-a7b3c1');
    expect(result.data.places['mfjk2x-a7b3c1'].name).toBe('할매국밥');
  });
});

describe('restoreUnuploaded — 못 올린 것을 서버 것 위에 얹는다', () => {
  const fresh: DeviceCollections = {
    lists: [list('srv-1', '바다 보러', ['a'])],
    places: { a: place('a', '광안리') },
  };
  const previous: DeviceCollections = {
    lists: [list('srv-1', '바다 보러', ['a', 'zzz'])],
    places: { a: place('a', '광안리'), zzz: place('zzz', '못 올라간 곳') },
  };

  it('못 올린 것이 없으면 서버 것을 그대로 준다', () => {
    expect(restoreUnuploaded(fresh, previous, new Set())).toBe(fresh);
  });

  it('못 올린 장소를 같은 이름의 리스트에 되돌려 놓는다', () => {
    const out = restoreUnuploaded(fresh, previous, new Set(['zzz']));
    expect(out.lists[0].placeIds).toEqual(['a', 'zzz']);
    expect(out.places.zzz.name).toBe('못 올라간 곳');
  });

  it('리스트째 못 올라갔으면 그 리스트를 통째로 남긴다', () => {
    const onlyDevice: DeviceCollections = {
      lists: [list('local-9', '기기에만', ['q'])],
      places: { q: place('q', '기기 장소') },
    };
    const out = restoreUnuploaded(fresh, onlyDevice, new Set(['q']));
    expect(out.lists).toHaveLength(2);
    expect(out.lists[1].name).toBe('기기에만');
    expect(out.lists[1].placeIds).toEqual(['q']);
  });

  it('이미 서버에 있는 것을 두 번 넣지 않는다', () => {
    const out = restoreUnuploaded(fresh, previous, new Set(['a', 'zzz']));
    expect(out.lists[0].placeIds).toEqual(['a', 'zzz']);
  });
});

// — 지운 것이 되살아나던 것.
import { samePendingDelete, type PendingDelete } from '../collectionsApi';

describe('지우기를 서버로 보낸다 (S15P21E201-1148)', () => {
  const srvList = serverList('srv-1', '바다 보러', [{ placeId: 'p1', name: '광안리', position: 0 }]);
  const empty: DeviceCollections = { lists: [], places: {} };

  it('보류해 둔 리스트 지우기를 DELETE 로 보낸다', async () => {
    const deleted: string[] = [];
    mockServer((method, url) => {
      if (method === 'DELETE') { deleted.push(url); return ok({}); }
      return ok(serverPage([srvList]));
    });

    const result = await loadCollections(empty, 'token', [{ kind: 'list', collectionId: 'srv-1' }]);

    expect(deleted).toHaveLength(1);
    expect(deleted[0]).toContain('/me/collections/srv-1');
    if (result.state !== 'success') throw new Error('성공이어야 한다');
    expect(result.pendingDeletes).toEqual([]);
  });

  it('항목 지우기는 itemId 로 보낸다', async () => {
    const deleted: string[] = [];
    mockServer((method, url) => {
      if (method === 'DELETE') { deleted.push(url); return ok({}); }
      return ok(serverPage([srvList]));
    });

    await loadCollections(empty, 'token', [{ kind: 'item', collectionId: 'srv-1', itemId: 'srv-1-item-0' }]);

    expect(deleted[0]).toContain('/me/collections/srv-1/items/srv-1-item-0');
  });

  it('🔴 5xx 면 버리지 않는다 — 다음에 다시 보낸다', async () => {
    mockServer((method) => {
      if (method === 'DELETE') return new Response(JSON.stringify({ data: null, error: { code: 'BOOM', message: '실패' }, meta: { requestId: 'r' } }), { status: 500, headers: { 'content-type': 'application/json' } });
      return ok(serverPage([srvList]));
    });

    const queued: PendingDelete[] = [{ kind: 'list', collectionId: 'srv-1' }];
    const result = await loadCollections(empty, 'token', queued);

    if (result.state !== 'success') throw new Error('성공이어야 한다');
    expect(result.pendingDeletes).toEqual(queued);
  });

  it('404 는 이미 없다는 뜻이라 지운 것으로 친다', async () => {
    mockServer((method) => {
      if (method === 'DELETE') return new Response(JSON.stringify({ data: null, error: { code: 'NOT_FOUND', message: '없음' }, meta: { requestId: 'r' } }), { status: 404, headers: { 'content-type': 'application/json' } });
      return ok(serverPage([srvList]));
    });

    const result = await loadCollections(empty, 'token', [{ kind: 'list', collectionId: 'srv-1' }]);

    if (result.state !== 'success') throw new Error('성공이어야 한다');
    expect(result.pendingDeletes).toEqual([]);
  });

  it('🔴 못 보낸 동안에도 화면에 다시 안 나타난다', async () => {
    mockServer((method) => {
      if (method === 'DELETE') return new Response(JSON.stringify({ data: null, error: { code: 'BOOM', message: '실패' }, meta: { requestId: 'r' } }), { status: 503, headers: { 'content-type': 'application/json' } });
      return ok(serverPage([srvList]));
    });

    const result = await loadCollections(empty, 'token', [{ kind: 'list', collectionId: 'srv-1' }]);

    if (result.state !== 'success') throw new Error('성공이어야 한다');
    // 서버는 계속 돌려주지만 지운 것이므로 없는 셈 친다.
    expect(result.data.lists.map((l) => l.id)).not.toContain('srv-1');
  });

  it('로그인 전에는 보내지 않고 그대로 들고 있는다', async () => {
    const queued: PendingDelete[] = [{ kind: 'list', collectionId: 'srv-1' }];
    const result = await loadCollections(empty, null, queued);

    expect(result.state).toBe('device-only');
    expect(result.pendingDeletes).toEqual(queued);
  });
});

describe('samePendingDelete — 같은 것을 두 번 적지 않으려고', () => {
  it('같은 리스트 지우기는 같다고 본다', () => {
    expect(samePendingDelete({ kind: 'list', collectionId: 'a' }, { kind: 'list', collectionId: 'a' })).toBe(true);
    expect(samePendingDelete({ kind: 'list', collectionId: 'a' }, { kind: 'list', collectionId: 'b' })).toBe(false);
  });

  it('항목은 리스트와 항목이 둘 다 같아야 같다', () => {
    expect(samePendingDelete({ kind: 'item', collectionId: 'a', itemId: 'x' }, { kind: 'item', collectionId: 'a', itemId: 'x' })).toBe(true);
    expect(samePendingDelete({ kind: 'item', collectionId: 'a', itemId: 'x' }, { kind: 'item', collectionId: 'a', itemId: 'y' })).toBe(false);
  });

  it('종류가 다르면 다르다', () => {
    expect(samePendingDelete({ kind: 'list', collectionId: 'a' }, { kind: 'item', collectionId: 'a', itemId: 'a' })).toBe(false);
  });
});

// — 이름·설명을 고쳐도 서버에 안 가던 것.
import { type PendingRename } from '../collectionsApi';

describe('이름 고치기를 서버로 보낸다 (S15P21E201-1153)', () => {
  const srv = serverList('7b8cd3bc-7cef-48ef-bda0-335bec095fc2', '바다 보러', [{ placeId: 'p9', name: '광안리', position: 0 }]);
  const renamed: PendingRename[] = [{ collectionId: '7b8cd3bc-7cef-48ef-bda0-335bec095fc2', name: '여름에 갈 곳', description: null }];
  const empty: DeviceCollections = { lists: [], places: {} };

  it('보류해 둔 이름 고치기를 PATCH 로 보낸다', async () => {
    const patched: string[] = [];
    mockServer((method, url) => {
      if (method === 'PATCH') { patched.push(url); return ok({}); }
      return ok(serverPage([srv]));
    });

    const result = await loadCollections(empty, 'token', [], renamed);

    expect(patched).toHaveLength(1);
    expect(patched[0]).toContain('/me/collections/7b8cd3bc-7cef-48ef-bda0-335bec095fc2');
    if (result.state !== 'success') throw new Error('성공이어야 한다');
    expect(result.pendingRenames).toEqual([]);
  });

  it('🔴 못 보낸 동안에도 화면에는 새 이름이 보인다', async () => {
    mockServer((method) => {
      if (method === 'PATCH') return new Response(JSON.stringify({ data: null, error: { code: 'BOOM', message: '실패' }, meta: { requestId: 'r' } }), { status: 503, headers: { 'content-type': 'application/json' } });
      return ok(serverPage([srv]));
    });

    const result = await loadCollections(empty, 'token', [], renamed);

    if (result.state !== 'success') throw new Error('성공이어야 한다');
    // 서버는 옛 이름을 돌려주지만 화면은 새 이름이어야 한다.
    expect(result.data.lists[0].name).toBe('여름에 갈 곳');
    expect(result.pendingRenames).toEqual(renamed);
  });

  it('🔴 보낸 뒤 다시 받아온다 — 안 그러면 화면이 한 판 뒤처진다', async () => {
    // 원인은 순서였다 — 목록을 먼저 받고(옛 이름), 그 뒤에 PATCH 를 보내고, 합칠 때는
    // 먼저 받아 둔 옛 이름을 쓴다. 보낸 것이 있으면 다시 받아와야 한다.
    let gets = 0;
    mockServer((method) => {
      if (method === 'PATCH') return ok({});
      gets += 1;
      // 첫 번째 GET 은 옛 이름, PATCH 뒤의 GET 은 새 이름을 돌려준다 — 진짜 서버와 같다.
      return ok(serverPage([gets === 1
        ? srv
        : serverList('7b8cd3bc-7cef-48ef-bda0-335bec095fc2', '여름에 갈 곳', [{ placeId: 'p9', name: '광안리', position: 0 }])]));
    });

    const result = await loadCollections(empty, 'token', [], renamed);

    if (result.state !== 'success') throw new Error('성공이어야 한다');
    expect([gets, result.data.lists[0].name]).toEqual([2, '여름에 갈 곳']);
  });

  it('보낼 이름이 없으면 공연히 다시 받아오지 않는다', async () => {
    let gets = 0;
    mockServer((method) => {
      if (method === 'PATCH') throw new Error('PATCH 가 나가면 안 된다');
      gets += 1;
      return ok(serverPage([srv]));
    });

    const result = await loadCollections(empty, 'token', [], []);

    if (result.state !== 'success') throw new Error('성공이어야 한다');
    expect(gets).toBe(1);
  });

  it('4xx 면 다시 보내지 않는다 — 같은 값으로는 계속 실패한다', async () => {
    mockServer((method) => {
      if (method === 'PATCH') return new Response(JSON.stringify({ data: null, error: { code: 'TOO_LONG', message: '이름이 너무 김' }, meta: { requestId: 'r' } }), { status: 400, headers: { 'content-type': 'application/json' } });
      return ok(serverPage([srv]));
    });

    const result = await loadCollections(empty, 'token', [], renamed);

    if (result.state !== 'success') throw new Error('성공이어야 한다');
    expect(result.pendingRenames).toEqual([]);
  });
});

describe('짝짓기 — id 먼저, 이름은 그다음 (S15P21E201-1153)', () => {
  const uuid = '7b8cd3bc-7cef-48ef-bda0-335bec095fc2';
  const server: DeviceCollections = {
    lists: [list(uuid, '바다 보러', ['p1'])],
    places: { p1: place('p1', '광안리') },
  };

  it('🔴 이름을 바꿔도 id 가 같으면 같은 리스트다 — 서버에 하나 더 안 만든다', () => {
    const device: DeviceCollections = {
      lists: [list(uuid, '여름에 갈 곳', ['p1'])],   // 같은 id, 바뀐 이름
      places: { p1: place('p1', '광안리') },
    };

    const { merged, onlyOnDevice } = mergeCollections(device, server);

    expect(onlyOnDevice).toHaveLength(0);   // 새 리스트로 안 본다
    expect(merged.lists).toHaveLength(1);
  });

  it('id 가 달라도 이름이 같으면 같은 리스트다 — 오프라인에서 만든 경우', () => {
    const device: DeviceCollections = {
      lists: [list('mfjk2x-a7b3c1', '바다 보러', ['p1'])],   // 기기가 만든 id, 같은 이름
      places: { p1: place('p1', '광안리') },
    };

    const { onlyOnDevice } = mergeCollections(device, server);

    expect(onlyOnDevice).toHaveLength(0);
  });

  it('id 도 이름도 다르면 새 리스트다', () => {
    const device: DeviceCollections = {
      lists: [list('mfjk2x-a7b3c1', '전혀 다른 리스트', [])],
      places: {},
    };

    const { onlyOnDevice } = mergeCollections(device, server);

    expect(onlyOnDevice).toHaveLength(1);
    expect(onlyOnDevice[0].name).toBe('전혀 다른 리스트');
  });
});
