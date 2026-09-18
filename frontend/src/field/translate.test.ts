// — 현장 말하기가 한국어를 못 하는 사람에게 한국어를 입력하라고 했다.
// 방향을 뒤집는 것이 이 티켓이고, 여기서 재는 것은 방향과 안 될 때의 태도 둘이다.
import { directionForLanguage, speechLanguageFor, translateText } from '@/field/translate';

type Call = { url: string; method: string; body: unknown };
let calls: Call[] = [];

function mockServer(options: { status?: number; translatedText?: string | null } = {}) {
  calls = [];
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    calls.push({ url, method: init?.method ?? 'GET', body: init?.body ? JSON.parse(String(init.body)) : null });
    if (options.status) {
      return new Response(JSON.stringify({ data: null, error: { code: 'X', message: '안 돼요' }, meta: { requestId: 'r1' } }), { status: options.status, headers: { 'content-type': 'application/json' } });
    }
    const translatedText = options.translatedText === undefined ? '얼음 빼주세요' : options.translatedText;
    return new Response(JSON.stringify({ data: { translatedText, cached: false, provider: 'test' }, error: null, meta: { requestId: 'r1' } }), { status: 200, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

beforeEach(() => mockServer());

describe('방향 — 이 화면은 한국어를 못 하는 사람을 위한 것이다', () => {
  it('🔴 영어 화면이면 영어로 쓰고 한국어로 말한다', () => {
    expect(directionForLanguage('en')).toBe('EN_TO_KO');
  });

  it('한국어 화면이면 번역할 것이 없다 — 부르지 않는다', () => {
    expect(directionForLanguage('ko')).toBeNull();
  });

  it('🔴 EN_TO_KO 로 번역한 문장은 한국어 음성으로 읽는다', () => {
    expect(speechLanguageFor('EN_TO_KO')).toBe('ko-KR');
  });

  it('KO_TO_EN 로 번역한 문장은 영어 음성으로 읽는다 — 문장의 언어를 따라간다', () => {
    expect(speechLanguageFor('KO_TO_EN')).toBe('en-US');
  });
});

describe('번역 요청', () => {
  it('서버 계약대로 sourceText·direction 을 보낸다', async () => {
    await translateText('No ice, please', 'EN_TO_KO', 'token');

    expect(calls).toHaveLength(1);
    expect(calls[0].method).toBe('POST');
    expect(calls[0].url).toContain('/api/v1/tools/translate');
    expect(calls[0].body).toEqual({ sourceText: 'No ice, please', direction: 'EN_TO_KO' });
  });

  it('번역된 한국어를 돌려준다', async () => {
    await expect(translateText('No ice, please', 'EN_TO_KO', 'token'))
      .resolves.toMatchObject({ state: 'translated', text: '얼음 빼주세요' });
  });

  it('앞뒤 공백은 보내기 전에 걷는다', async () => {
    await translateText('  No ice, please  ', 'EN_TO_KO', 'token');

    expect(calls[0].body).toEqual({ sourceText: 'No ice, please', direction: 'EN_TO_KO' });
  });

  it('🔴 빈 번역을 성공이라고 하지 않는다 — 화면이 빈 칸을 읽어 주게 된다', async () => {
    mockServer({ translatedText: '   ' });

    await expect(translateText('No ice, please', 'EN_TO_KO', 'token'))
      .resolves.toEqual({ state: 'blocked', reason: 'vendor' });
  });
});

// 이 경로는 없을 수 있다 — 컨트롤러가 back/dev 에만 있고, 운영에는 번역 업체 열쇠가
// 아직 안 들어가 있다. 그래서 "안 될 때 무엇을 하는가" 가 기능의 절반이다.
describe('안 될 때 — 이유마다 다르게 말해야 하므로 뭉뚱그리지 않는다', () => {
  it('🔴 로그인 안 했으면 서버를 부르지도 않는다 — 기다렸다 「안 됐어요」가 제일 나쁘다', async () => {
    await expect(translateText('No ice, please', 'EN_TO_KO', null))
      .resolves.toEqual({ state: 'blocked', reason: 'signed-out' });
    expect(calls).toHaveLength(0);
  });

  it.each([401, 403])('HTTP %i 는 signed-out 이다', async (status) => {
    mockServer({ status });
    await expect(translateText('x', 'EN_TO_KO', 'token')).resolves.toEqual({ state: 'blocked', reason: 'signed-out' });
  });

  it.each([404, 501])('HTTP %i 는 not-built 다 — 기다리면 생긴다', async (status) => {
    mockServer({ status });
    await expect(translateText('x', 'EN_TO_KO', 'token')).resolves.toEqual({ state: 'blocked', reason: 'not-built' });
  });

  it.each([500, 502, 503])('HTTP %i 는 vendor 다 — 잠시 뒤 될 수 있다', async (status) => {
    mockServer({ status });
    await expect(translateText('x', 'EN_TO_KO', 'token')).resolves.toEqual({ state: 'blocked', reason: 'vendor' });
  });

  it('빈 문장은 부르지 않는다', async () => {
    await expect(translateText('   ', 'EN_TO_KO', 'token')).resolves.toEqual({ state: 'blocked', reason: 'error' });
    expect(calls).toHaveLength(0);
  });
});
