// S15P21E201-1138 — 주변 버스 도착.
//
// 🔴 이 화면에서 가장 나쁜 실패는 크래시가 아니라 **틀린 시간을 자신 있게 말하는 것**이다.
//    서버가 모른다고 한 것을 「곧 도착」으로 채우면, 그 말을 믿은 사람이 정류소로 뛴다.
//    그래서 여기서 재는 것의 절반이 "모르는 것을 모른다고 하는가" 다.
import {
  arrivalLabel,
  loadNearbyBusArrivals,
  sortArrivals,
  sortStops,
  type BusArrival,
  type BusStop,
} from '@/field/busArrivals';

const at = (routeNo: string, arrivalSeconds: number | null): BusArrival =>
  ({ routeNo, arrivalSeconds, remainingStops: null, vehicleType: null });

const BUSAN = { latitude: 35.1796, longitude: 129.0756 };

let calls: string[] = [];
function mockServer(options: { status?: number; body?: unknown } = {}) {
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
    return new Response(JSON.stringify({ data: options.body ?? { stops: [] }, error: null, meta: { requestId: 'r1' } }), { status: 200, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

beforeEach(() => mockServer());

describe('🔴 모르는 시간을 지어내지 않는다', () => {
  it.each([null, undefined, NaN, -1])('%p 는 「모름」이다 — 0분도 곧 도착도 아니다', (value) => {
    expect(arrivalLabel(value as number | null)).toEqual({ kind: 'unknown' });
  });

  it('60초 미만은 「곧 도착」이다 — 0분이라고 쓰지 않는다', () => {
    expect(arrivalLabel(0)).toEqual({ kind: 'imminent' });
    expect(arrivalLabel(59)).toEqual({ kind: 'imminent' });
  });

  it('1분부터 분으로 센다 — 내림이다. 3분 59초를 4분이라 하면 놓친다', () => {
    expect(arrivalLabel(60)).toEqual({ kind: 'minutes', minutes: 1 });
    expect(arrivalLabel(239)).toEqual({ kind: 'minutes', minutes: 3 });
    expect(arrivalLabel(600)).toEqual({ kind: 'minutes', minutes: 10 });
  });
});

describe('빠른 것부터 — 모르는 것은 맨 뒤', () => {
  it('🔴 시간을 모르는 노선이 첫 줄을 차지하지 않는다', () => {
    const sorted = sortArrivals([at('1001', null), at('40', 300), at('139', 120)]);
    expect(sorted.map((a) => a.routeNo)).toEqual(['139', '40', '1001']);
  });

  it('같은 시간이면 노선 번호 순 — 목록이 흔들리지 않는다', () => {
    const sorted = sortArrivals([at('307', 120), at('40', 120)]);
    expect(sorted.map((a) => a.routeNo)).toEqual(['40', '307']);
  });

  it('전부 모르면 노선 번호 순', () => {
    const sorted = sortArrivals([at('307', null), at('40', null)]);
    expect(sorted.map((a) => a.routeNo)).toEqual(['40', '307']);
  });

  it('원본을 안 건드린다', () => {
    const input = [at('1001', null), at('40', 300)];
    sortArrivals(input);
    expect(input.map((a) => a.routeNo)).toEqual(['1001', '40']);
  });
});

describe('정류소도 빨리 오는 곳부터', () => {
  const stop = (nodeName: string, arrivals: BusArrival[]): BusStop =>
    ({ nodeId: nodeName, nodeName, lat: 0, lng: 0, arrivals });

  it('가장 빨리 오는 버스가 이른 정류소가 위로', () => {
    const sorted = sortStops([stop('먼곳', [at('40', 600)]), stop('가까운곳', [at('139', 60)])]);
    expect(sorted.map((s) => s.nodeName)).toEqual(['가까운곳', '먼곳']);
  });

  it('아무것도 안 오는 정류소는 뒤로', () => {
    const sorted = sortStops([stop('없음', [at('40', null)]), stop('있음', [at('139', 300)])]);
    expect(sorted.map((s) => s.nodeName)).toEqual(['있음', '없음']);
  });
});

describe('조회', () => {
  it('좌표를 그대로 보낸다', async () => {
    await loadNearbyBusArrivals(BUSAN, 'token');
    expect(calls[0]).toContain('lat=35.1796');
    expect(calls[0]).toContain('lng=129.0756');
  });

  it('🔴 빈 목록은 실패가 아니다 — 정말 근처에 정류소가 없을 수 있다', async () => {
    await expect(loadNearbyBusArrivals(BUSAN, 'token')).resolves.toEqual({ state: 'ready', stops: [] });
  });

  it('받은 것을 정렬해서 준다', async () => {
    mockServer({ body: { stops: [{ nodeId: 'A', nodeName: '해운대역', lat: 1, lng: 2, arrivals: [at('1001', null), at('139', 120)] }] } });
    const out = await loadNearbyBusArrivals(BUSAN, 'token');
    if (out.state !== 'ready') throw new Error('ready 여야 한다');
    expect(out.stops[0].arrivals.map((a) => a.routeNo)).toEqual(['139', '1001']);
  });

  it('이름이 없는 정류소·노선 줄은 버린다 — 빈 칸을 그리지 않는다', async () => {
    mockServer({ body: { stops: [{ nodeId: '', nodeName: '', lat: 0, lng: 0, arrivals: [] }, { nodeId: 'B', nodeName: '광안리', lat: 1, lng: 2, arrivals: [at('', 60), at('40', 60)] }] } });
    const out = await loadNearbyBusArrivals(BUSAN, 'token');
    if (out.state !== 'ready') throw new Error('ready 여야 한다');
    expect(out.stops).toHaveLength(1);
    expect(out.stops[0].arrivals.map((a) => a.routeNo)).toEqual(['40']);
  });
});

describe('안 될 때 — 이유마다 다르게 말한다', () => {
  it('🔴 로그인 안 했으면 서버를 부르지도 않는다', async () => {
    await expect(loadNearbyBusArrivals(BUSAN, null)).resolves.toEqual({ state: 'blocked', reason: 'signed-out' });
    expect(calls).toHaveLength(0);
  });

  it.each([401, 403])('HTTP %i 는 signed-out', async (status) => {
    mockServer({ status });
    await expect(loadNearbyBusArrivals(BUSAN, 't')).resolves.toEqual({ state: 'blocked', reason: 'signed-out' });
  });

  it.each([404, 501])('HTTP %i 는 not-built', async (status) => {
    mockServer({ status });
    await expect(loadNearbyBusArrivals(BUSAN, 't')).resolves.toEqual({ state: 'blocked', reason: 'not-built' });
  });

  it.each([500, 502])('HTTP %i 는 vendor — TAGO 쪽 실패다', async (status) => {
    mockServer({ status });
    await expect(loadNearbyBusArrivals(BUSAN, 't')).resolves.toEqual({ state: 'blocked', reason: 'vendor' });
  });
});
