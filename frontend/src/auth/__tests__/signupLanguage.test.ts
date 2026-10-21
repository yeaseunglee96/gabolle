// 가입 요청의 언어 — S15P21E201-2053.
//
// 🔴 이 시험이 지키는 것은 두 가지다.
//    (1) 가입 화면에서 고른 언어가 서버가 받는 표기로 나간다. 앱은 여섯 언어를 아는데 가입 요청은 KO · EN 둘뿐이었다.
//    (2) 앱이 서버보다 먼저 나가도 가입이 «영영» 막히지 않는다. 옛 서버는 language 를 KO|EN 만 받아서 400 을 준다 —
//        그때 영어로 한 번 다시 보낸다. 다만 «language 칸 때문에» 난 400 일 때만이다. 다른 오류를 영어 요청의 오류로 바꿔치지 않는다.
import { ApiClientError } from '@/api/client';
import { LANGUAGE_CODES } from '@/i18n/languages';

import { completeOAuthSignup, signup } from '../authApi';
import { isLanguageRejected, sendWithLanguageFallback, toSignupLanguage, type SignupLanguage } from '../signupLanguage';

// backend LanguageNormalizer.SIGNUP_LANGUAGE_REGEX 의 사본이다 — 서버가 가입 요청에서 받는 값. 앱이 보내는 표기가
// 오타(ZH-HANS ↔ ZH_HANS 같은)로 서버의 입구에서 막히지 않는지 본다. 서버 쪽이 바뀌면 이 줄도 같이 바꾼다.
const SERVER_ACCEPTS = /^(KO|EN|JA|VI|ZH[_-]HANS|ZH[_-]HANT)$/;

describe('앱 언어 → 가입 요청에 실리는 표기', () => {
  it.each([
    ['ko', 'KO'],
    ['en', 'EN'],
    ['ja', 'JA'],
    ['zh-Hans', 'ZH_HANS'],
    ['zh-Hant', 'ZH_HANT'],
    ['vi', 'VI'],
  ] as const)('%s 는 %s 로 나간다', (code, expected) => {
    expect(toSignupLanguage(code)).toBe(expected);
  });

  it('🔴 앱이 아는 언어는 하나도 빠지지 않는다 — 여섯이 서로 다른 값이다', () => {
    const sent = LANGUAGE_CODES.map(toSignupLanguage);
    expect(sent).toHaveLength(6);
    expect(new Set(sent).size).toBe(LANGUAGE_CODES.length);
  });

  it('🔴 전부 서버 입구(@Pattern)가 받는 모양이다', () => {
    for (const code of LANGUAGE_CODES) expect(toSignupLanguage(code)).toMatch(SERVER_ACCEPTS);
  });
});

const languageRejected = () => new ApiClientError('요청 형식이 올바르지 않습니다.', 'INVALID_REQUEST', 400, ['language: must match "KO|EN"']);
const emailRejected = () => new ApiClientError('요청 형식이 올바르지 않습니다.', 'INVALID_REQUEST', 400, ['email: must be a well-formed email address']);

describe('서버가 language 칸을 돌려보냈나', () => {
  it('language 칸 때문에 난 INVALID_REQUEST 400 이다', () => {
    expect(isLanguageRejected(languageRejected())).toBe(true);
  });

  it('여러 칸이 같이 틀렸어도 language 가 끼어 있으면 그렇다', () => {
    expect(isLanguageRejected(new ApiClientError('x', 'INVALID_REQUEST', 400, ['email: …', 'language: …']))).toBe(true);
  });

  it.each([
    ['다른 칸 때문에 난 400', emailRejected()],
    ['칸 이름이 없는 400(본문을 못 읽음)', new ApiClientError('x', 'INVALID_REQUEST', 400)],
    ['이름만 비슷한 칸(languageTag)', new ApiClientError('x', 'INVALID_REQUEST', 400, ['languageTag: …'])],
    ['400 이 아닌 오류', new ApiClientError('x', 'INVALID_REQUEST', 422, ['language: …'])],
    ['다른 코드의 400', new ApiClientError('x', 'EMAIL_ALREADY_EXISTS', 400, ['language: …'])],
    ['오류가 아닌 값', new Error('network')],
  ])('아니다 — %s', (_name, cause) => {
    expect(isLanguageRejected(cause)).toBe(false);
  });
});

describe('앱이 서버보다 먼저 나갔을 때의 방어', () => {
  it('서버가 받으면 한 번만 보낸다', async () => {
    const send = jest.fn(async (language: SignupLanguage) => `ok:${language}`);

    await expect(sendWithLanguageFallback('JA', send)).resolves.toBe('ok:JA');
    expect(send).toHaveBeenCalledTimes(1);
    expect(send).toHaveBeenCalledWith('JA');
  });

  it.each(['JA', 'ZH_HANS', 'ZH_HANT', 'VI'] as const)('🔴 옛 서버가 %s 를 language 칸 때문에 돌려보내면 영어로 한 번 다시 보낸다', async (language) => {
    const send = jest.fn(async (sent: SignupLanguage) => {
      if (sent !== 'KO' && sent !== 'EN') throw languageRejected();
      return `ok:${sent}`;
    });

    await expect(sendWithLanguageFallback(language, send)).resolves.toBe('ok:EN');
    expect(send.mock.calls.map(([sent]) => sent)).toEqual([language, 'EN']);
  });

  it.each(['KO', 'EN'] as const)('%s 는 옛 서버도 받는다 — 400 이 나도 다시 보내지 않고 그대로 던진다', async (language) => {
    const send = jest.fn(async () => { throw languageRejected(); });

    await expect(sendWithLanguageFallback(language, send)).rejects.toBeInstanceOf(ApiClientError);
    expect(send).toHaveBeenCalledTimes(1);
  });

  it('🔴 다른 칸 때문에 난 400 은 영어로 다시 보내지 않는다 — 진짜 오류를 가리지 않는다', async () => {
    const send = jest.fn(async () => { throw emailRejected(); });

    await expect(sendWithLanguageFallback('JA', send)).rejects.toMatchObject({ fields: ['email: must be a well-formed email address'] });
    expect(send).toHaveBeenCalledTimes(1);
  });

  it('이미 가입된 이메일 같은 서버의 답도 그대로 나간다', async () => {
    const send = jest.fn(async () => { throw new ApiClientError('이미 가입된 이메일입니다.', 'EMAIL_ALREADY_EXISTS', 409); });

    await expect(sendWithLanguageFallback('VI', send)).rejects.toMatchObject({ code: 'EMAIL_ALREADY_EXISTS' });
    expect(send).toHaveBeenCalledTimes(1);
  });

  it('영어로 다시 보낸 것도 실패하면 그 오류가 나간다 — 세 번째는 없다', async () => {
    const second = new ApiClientError('이미 가입된 이메일입니다.', 'EMAIL_ALREADY_EXISTS', 409);
    const send = jest.fn()
      .mockRejectedValueOnce(languageRejected())
      .mockRejectedValueOnce(second);

    await expect(sendWithLanguageFallback('ZH_HANT', send)).rejects.toBe(second);
    expect(send).toHaveBeenCalledTimes(2);
  });
});

// ── 실제 요청 본문 ───────────────────────────────────────────────────────────
// 여기부터는 authApi 의 signup · completeOAuthSignup 을 진짜로 불러 fetch 로 나가는 본문을 본다.
// 위의 시험이 «규칙»이라면 이쪽은 «규칙이 두 요청에 실제로 걸려 있는가» 다.
const originalFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = originalFetch;
  jest.restoreAllMocks();
});

function jsonResponse(status: number, body: unknown) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => 'application/json' },
    json: async () => body,
    text: async () => JSON.stringify(body),
  };
}

const OK_REGISTRATION = { data: { userId: 'u1', email: 'a@b.c', status: 'PENDING_EMAIL_VERIFICATION' }, error: null };
const OK_LOGIN = { data: { status: 'LOGGED_IN', accessToken: 'a', refreshToken: 'r', expiresIn: 900, sessionId: 's', user: { userId: 'u1', email: 'a@b.c', displayName: 'x', language: 'JA', status: 'ACTIVE' } }, error: null };
const OLD_SERVER_REJECTS_LANGUAGE = { data: null, error: { code: 'INVALID_REQUEST', message: '요청 형식이 올바르지 않습니다.', fields: ['language: must match "KO|EN"'] } };

/**
 * 가입 주소로 나간 요청의 본문들을 돌려준다. 응답은 주소별로 차례대로 준다.
 * 🔴 주소로 골라야 한다 — 가입 요청 전에 익명 세션 표를 받아 오는 요청이 나가고 그 요청에는 본문이 없다.
 */
function mockServer(path: string, replies: Array<{ status: number; body: unknown }>) {
  const queue = [...replies];
  const fetchMock = jest.fn(async (url: unknown, _init?: RequestInit) => {
    if (String(url).endsWith(path)) {
      const next = queue.shift();
      if (!next) throw new Error('예상보다 많은 가입 요청이 나갔다');
      return jsonResponse(next.status, next.body);
    }
    return jsonResponse(200, { data: {}, error: null });
  });
  globalThis.fetch = fetchMock as unknown as typeof fetch;
  return () => fetchMock.mock.calls
    .filter(([url]) => String(url).endsWith(path))
    .map(([, init]) => JSON.parse((init as RequestInit).body as string) as { language: string });
}

const SIGNUP = { email: 'a@b.c', password: 'abcd1234!', displayName: '여행자', ageGateAccepted: true, termsAccepted: true, privacyAccepted: true } as const;
const OAUTH_SIGNUP = { signupTicket: 'ticket-1', displayName: '여행자', ageGateAccepted: true, termsAccepted: true, privacyAccepted: true } as const;

describe('이메일 가입 요청 본문', () => {
  it.each(['KO', 'EN', 'JA', 'ZH_HANS', 'ZH_HANT', 'VI'] as const)('%s 는 그대로 실린다', async (language) => {
    const bodies = mockServer('/api/v1/auth/signup', [{ status: 201, body: OK_REGISTRATION }]);

    await signup({ ...SIGNUP, language });

    expect(bodies().map((body) => body.language)).toEqual([language]);
  });

  it('🔴 옛 서버가 일본어를 돌려보내면 같은 본문에서 language 만 EN 으로 바뀌어 다시 나가고, 가입이 끝난다', async () => {
    const bodies = mockServer('/api/v1/auth/signup', [
      { status: 400, body: OLD_SERVER_REJECTS_LANGUAGE },
      { status: 201, body: OK_REGISTRATION },
    ]);

    const result = await signup({ ...SIGNUP, language: 'JA' });

    expect(result).toMatchObject({ userId: 'u1' });
    const sent = bodies();
    expect(sent.map((body) => body.language)).toEqual(['JA', 'EN']);
    // 언어 말고는 같은 본문이다 — 이메일·이름·동의가 바뀌지 않는다.
    const { language: _first, ...firstRest } = sent[0];
    const { language: _second, ...secondRest } = sent[1];
    expect(secondRest).toEqual(firstRest);
  });

  it('옛 서버가 한국어를 받는 한 다시 보내는 일은 없다', async () => {
    const bodies = mockServer('/api/v1/auth/signup', [{ status: 201, body: OK_REGISTRATION }]);

    await signup({ ...SIGNUP, language: 'KO' });

    expect(bodies()).toHaveLength(1);
  });
});

describe('소셜 가입 마무리 요청 본문', () => {
  it.each(['KO', 'EN', 'JA', 'ZH_HANS', 'ZH_HANT', 'VI'] as const)('%s 는 그대로 실린다', async (language) => {
    const bodies = mockServer('/api/v1/auth/oauth/signup', [{ status: 201, body: OK_LOGIN }]);

    await completeOAuthSignup({ ...OAUTH_SIGNUP, language });

    expect(bodies().map((body) => body.language)).toEqual([language]);
  });

  it('🔴 옛 서버가 베트남어를 돌려보내면 같은 가입 티켓으로 EN 을 다시 보낸다', async () => {
    const bodies = mockServer('/api/v1/auth/oauth/signup', [
      { status: 400, body: OLD_SERVER_REJECTS_LANGUAGE },
      { status: 201, body: OK_LOGIN },
    ]);

    await completeOAuthSignup({ ...OAUTH_SIGNUP, language: 'VI' });

    const sent = bodies() as Array<{ language: string; signupTicket: string }>;
    expect(sent.map((body) => body.language)).toEqual(['VI', 'EN']);
    expect(sent.map((body) => body.signupTicket)).toEqual(['ticket-1', 'ticket-1']);
  });

  it('티켓이 만료됐다는 답(OAUTH_TICKET_INVALID)은 영어로 다시 보내지 않고 그대로 나간다 — 화면이 이 코드로 만료 안내를 띄운다', async () => {
    const bodies = mockServer('/api/v1/auth/oauth/signup', [
      { status: 400, body: { data: null, error: { code: 'OAUTH_TICKET_INVALID', message: '소셜 로그인 절차가 만료됐어요.', fields: [] } } },
    ]);

    await expect(completeOAuthSignup({ ...OAUTH_SIGNUP, language: 'JA' })).rejects.toMatchObject({ code: 'OAUTH_TICKET_INVALID' });
    expect(bodies()).toHaveLength(1);
  });
});
