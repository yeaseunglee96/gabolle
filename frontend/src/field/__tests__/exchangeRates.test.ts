// — 환율 계산기. 여기서 재는 것은 틀리면 사용자가 못 알아채는 것들이다.
import {
  defaultCurrencyFor,
  displayCode,
  foreignToKrw,
  krwToForeign,
  loadExchangeRates,
  pickRate,
  unitsPerQuote,
  type ExchangeRate,
} from '@/field/exchangeRates';

const usd: ExchangeRate = { currencyCode: 'USD', currencyName: '미국 달러', baseRate: 1390, buyingRate: 1376, sellingRate: 1404 };
const jpy: ExchangeRate = { currencyCode: 'JPY(100)', currencyName: '일본 옌', baseRate: 940, buyingRate: 930, sellingRate: 950 };

type Call = { url: string };
let calls: Call[] = [];

function mockServer(options: { status?: number; body?: unknown } = {}) {
  calls = [];
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    calls.push({ url });
    if (options.status) {
      return new Response(JSON.stringify({ data: null, error: { code: 'X', message: '안 돼요' }, meta: { requestId: 'r1' } }), { status: options.status, headers: { 'content-type': 'application/json' } });
    }
    const body = options.body ?? { asOf: '2026-09-17', rates: [usd, jpy] };
    return new Response(JSON.stringify({ data: body, error: null, meta: { requestId: 'r1' } }), { status: 200, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

beforeEach(() => mockServer());

describe('고른 언어로 기본 통화를 정한다', () => {
  it.each([
    ['ja', 'JPY'], ['zh-Hans', 'CNY'], ['zh-Hant', 'TWD'], ['en', 'USD'], ['ko', 'USD'],
  ] as const)('%s → %s', (lang, code) => {
    expect(defaultCurrencyFor(lang)).toBe(code);
  });

  it('🔴 간체와 번체가 서로 다른 통화다 — 중국어라고 묶으면 대만 사람이 위안을 본다', () => {
    expect(defaultCurrencyFor('zh-Hans')).not.toBe(defaultCurrencyFor('zh-Hant'));
  });
});

// 이 묶음이 이 파일의 본체다. 100배 틀려도 화면은 멀쩡해 보인다.
describe('100단위로 고시되는 통화', () => {
  it('괄호 안 숫자를 단위로 읽는다', () => {
    expect(unitsPerQuote('JPY(100)')).toBe(100);
    expect(unitsPerQuote('USD')).toBe(1);
  });

  it.each(['IDR(100)', 'VND(100)'])('%s 도 100단위다', (code) => {
    expect(unitsPerQuote(code)).toBe(100);
  });

  it('모양이 이상하면 1로 본다 — 0으로 나누지 않는다', () => {
    expect(unitsPerQuote('JPY(0)')).toBe(1);
    expect(unitsPerQuote('JPY()')).toBe(1);
    expect(unitsPerQuote('')).toBe(1);
  });

  it('보여줄 때는 괄호를 걷는다', () => {
    expect(displayCode('JPY(100)')).toBe('JPY');
    expect(displayCode('USD')).toBe('USD');
  });

  it('🔴 1,000엔이 9,400원이다 — 100으로 안 나누면 94원이 된다', () => {
    expect(Math.round(foreignToKrw(1000, jpy))).toBe(9400);
  });

  it('달러는 단위가 1이라 그대로다', () => {
    expect(Math.round(foreignToKrw(10, usd))).toBe(13900);
  });
});

describe('양방향 환산', () => {
  it('원 → 외화', () => {
    expect(Math.round(krwToForeign(13900, usd))).toBe(10);
    expect(Math.round(krwToForeign(9400, jpy))).toBe(1000);
  });

  it('왕복하면 제자리로 온다', () => {
    expect(Math.round(krwToForeign(foreignToKrw(250, jpy), jpy))).toBe(250);
    expect(Math.round(foreignToKrw(krwToForeign(50000, usd), usd))).toBe(50000);
  });

  it('숫자가 아니면 0 이다 — 빈 칸에 NaN 을 그리지 않는다', () => {
    expect(foreignToKrw(NaN, usd)).toBe(0);
    expect(krwToForeign(NaN, usd)).toBe(0);
  });
});

describe('통화 고르기', () => {
  it('원하는 코드를 찾는다 — 괄호가 붙어 있어도', () => {
    expect(pickRate([usd, jpy], 'JPY')?.currencyCode).toBe('JPY(100)');
  });

  it('없으면 첫 번째를 준다 — 고를 것이 있는데 빈 화면을 주지 않는다', () => {
    expect(pickRate([usd, jpy], 'EUR')?.currencyCode).toBe('USD');
  });

  it('목록이 비면 null 이다', () => {
    expect(pickRate([], 'USD')).toBeNull();
  });
});

describe('안 될 때 — 이유마다 다르게 말한다', () => {
  it('🔴 로그인 안 했으면 서버를 부르지도 않는다', async () => {
    await expect(loadExchangeRates(null)).resolves.toEqual({ state: 'blocked', reason: 'signed-out' });
    expect(calls).toHaveLength(0);
  });

  it.each([401, 403])('HTTP %i 는 signed-out', async (status) => {
    mockServer({ status });
    await expect(loadExchangeRates('t')).resolves.toEqual({ state: 'blocked', reason: 'signed-out' });
  });

  it.each([404, 501])('HTTP %i 는 not-built', async (status) => {
    mockServer({ status });
    await expect(loadExchangeRates('t')).resolves.toEqual({ state: 'blocked', reason: 'not-built' });
  });

  it.each([500, 502])('HTTP %i 는 vendor', async (status) => {
    mockServer({ status });
    await expect(loadExchangeRates('t')).resolves.toEqual({ state: 'blocked', reason: 'vendor' });
  });

  it('🔴 빈 목록을 성공이라 하지 않는다 — 고를 것이 없는 선택기를 그리게 된다', async () => {
    mockServer({ body: { asOf: '2026-09-17', rates: [] } });
    await expect(loadExchangeRates('t')).resolves.toEqual({ state: 'blocked', reason: 'vendor' });
  });

  it('값이 0이거나 모양이 깨진 줄은 버린다', async () => {
    mockServer({ body: { asOf: '2026-09-17', rates: [usd, { currencyCode: 'XXX', currencyName: '', baseRate: 0 }] } });
    const out = await loadExchangeRates('t');
    expect(out.state).toBe('ready');
    if (out.state === 'ready') expect(out.rates.map((r) => r.currencyCode)).toEqual(['USD']);
  });

  it('정상이면 날짜와 목록을 준다', async () => {
    const out = await loadExchangeRates('t');
    expect(out).toMatchObject({ state: 'ready', asOf: '2026-09-17' });
  });
});
