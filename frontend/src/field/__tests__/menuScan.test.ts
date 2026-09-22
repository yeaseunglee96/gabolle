import { allergenNotice, emptyNotice, scanMenu, unreadNotice, type MenuLine, type MenuScan } from '../menuScan';

// — 이 화면은 사람이 먹는 것 앞에 선다.

const tx = (ko: string) => ko;
const txEn = (_ko: string, en: string) => en;

function scan(partial: Partial<MenuScan> = {}): MenuScan {
  return { lines: [], unreadLineCount: 0, evidenceStatus: 'ESTIMATED', ...partial };
}

/**
 * 시험용 한 줄. 서버가 칸을 늘려도 이 헬퍼 하나만 고치면 되게 둔다 —
 * 줄을 손으로 만들어 두면 칸이 늘 때마다 시험 수십 곳이 함께 빨개진다.
 */
function line(partial: Partial<MenuLine> = {}): MenuLine {
  return { text: '', name: '', price: '', translatedName: '', translatedText: '', allergenWords: [], ...partial };
}

/** 이 말들이 화면에 나오면 사람이 다칠 수 있다. */
const FORBIDDEN = ['안전', '확인됨', '알레르기 없음', '유발 성분이 없', '없습니다', 'safe', 'verified', 'none found'];

function assertNeverSaysNone(text: string) {
  for (const phrase of FORBIDDEN) {
    expect(text.toLowerCase()).not.toContain(phrase.toLowerCase());
  }
}

describe('알레르기 안내는 「없다」를 말하지 않는다', () => {
  it('낱말을 찾았으면 그대로 나열하고, 직접 확인하라는 말이 반드시 붙는다', () => {
    const notice = allergenNotice(scan({ lines: [
      line({ text: '새우튀김 12,000', translatedText: '새우튀김 12,000', allergenWords: ['새우'] }),
      line({ text: '우유푸딩 6,000', translatedText: '우유푸딩 6,000', allergenWords: ['우유'] }),
    ] }), tx)!;
    expect(notice.words).toEqual(['새우', '우유']);
    expect(notice.caution).toContain('직원에게 확인');
    assertNeverSaysNone(notice.headline + notice.caution);
  });

  it('🔴 못 찾았을 때 「없다는 뜻이 아니에요」가 반드시 붙는다', () => {
    const notice = allergenNotice(scan({ lines: [line({ text: '김치찌개 9,000', translatedText: '김치찌개 9,000', allergenWords: [] })] }), tx)!;
    expect(notice.words).toEqual([]);
    expect(notice.caution).toContain('없다는 뜻이 아니');
    expect(notice.caution).toContain('직원에게 확인');
    assertNeverSaysNone(notice.headline + notice.caution);
  });

  it('🔴 영어에서도 「없다」를 말하지 않고 확인을 요구한다', () => {
    const notice = allergenNotice(scan({ lines: [line({ text: 'Kimchi stew', translatedText: 'Kimchi stew', allergenWords: [] })] }), txEn)!;
    expect(notice.caution.toLowerCase()).toContain('does not mean');
    expect(notice.caution.toLowerCase()).toContain('check with the staff');
    assertNeverSaysNone(notice.headline + notice.caution);
  });

  /**
   * 🔴 글자를 한 줄도 못 읽었으면 알레르기 안내 자체를 안 낸다 — S15P21E201-1489(B-14).
   *
   * <p>실기기(iOS build 39)에서 두 문장이 한 화면에 같이 떴다.
   *
   * <pre>
   *   읽은 글자에서는 알레르기와 관련된 낱말을 찾지 못했어요
   *   글자를 찾지 못했어요. 더 밝은 곳에서 …
   * </pre>
   *
   * <p>앞뒤가 안 맞는 것을 넘어 위험하다 — 「못 찾았다」는 찾아본 뒤에야 할 수 있는 말인데,
   * 아무것도 못 읽은 상태에서 그렇게 말하면 알레르기가 있는 사람이 «확인됐다»로 읽는다.
   */
  it('🔴 글자를 한 줄도 못 읽었으면 아무 말도 안 한다 — null 이다', () => {
    expect(allergenNotice(scan({ lines: [] }), tx)).toBeNull();
    expect(allergenNotice(scan({ lines: [] }), txEn)).toBeNull();
  });

  it('한 줄이라도 읽었으면 낱말이 없어도 안내는 낸다 — 「못 읽음」과 「낱말 없음」은 다르다', () => {
    const notice = allergenNotice(scan({ lines: [line({ text: '김치찌개', translatedText: '김치찌개', allergenWords: [] })] }), tx);
    expect(notice).not.toBeNull();
    expect(notice!.words).toEqual([]);
  });

  it('같은 낱말이 여러 줄에 있어도 한 번만 나온다', () => {
    const notice = allergenNotice(scan({ lines: [
      line({ text: '새우튀김', translatedText: '새우튀김', allergenWords: ['새우'] }),
      line({ text: '새우볶음밥', translatedText: '새우볶음밥', allergenWords: ['새우'] }),
    ] }), tx)!;
    expect(notice.words).toEqual(['새우']);
  });
});

describe('못 읽은 줄은 숨기지 않는다', () => {
  it('못 읽은 줄이 있으면 개수를 말한다', () => {
    expect(unreadNotice(scan({ unreadLineCount: 3 }), tx)).toContain('3개');
  });

  it('없으면 줄을 만들지 않는다 — 「전부 읽었어요」라고 말하지 않는다', () => {
    expect(unreadNotice(scan({ unreadLineCount: 0 }), tx)).toBeNull();
  });

  it('🔴 못 읽은 줄이 0이어도 알레르기 주의는 그대로 붙는다', () => {
    const notice = allergenNotice(scan({ unreadLineCount: 0, lines: [line({ text: '된장찌개', translatedText: '된장찌개', allergenWords: [] })] }), tx)!;
    expect(notice.caution).toContain('직원에게 확인');
  });
});

describe('글자를 못 찾았을 때', () => {
  it('빈 화면 대신 다음에 할 일을 말한다', () => {
    expect(emptyNotice(scan(), tx)).toContain('다시 찍어');
  });

  it('글자가 있으면 이 안내는 안 나온다', () => {
    expect(emptyNotice(scan({ lines: [line({ text: '비빔밥', translatedText: '비빔밥', allergenWords: [] })] }), tx)).toBeNull();
  });
});

// ── 서버가 준 것을 그대로 믿지 않는다 ────────────────────────────────────────

// — 사진은 보내기 전에 진짜 파일을 읽어 Blob 으로 바뀜다.
// 여기서 재는 것은 서버가 준 것을 어떻게 다루는가라, 파일 읽기는 흔든다.
// 그 자리 자체의 시험은 src/api/__tests__/multipart.test.ts 에 따로 있다.
jest.mock('@/api/multipart', () => ({ singleFileFormData: jest.fn(async () => new FormData()) }));
jest.mock('@/social/imageResize', () => ({
  MAX_UPLOAD_BYTES: 3 * 1024 * 1024,
  measureBytes: jest.fn(async () => 1000),
  resizeForUpload: jest.fn(async (uri: string) => ({ uri, width: 1600, height: 1200 })),
}));

function respondWith(payload: unknown, status = 200) {
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL) => {
    if (String(input).includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    return new Response(JSON.stringify(payload), { status, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

describe('메뉴판 사진 보내기', () => {
  it('서버가 준 줄과 못 읽은 줄 수를 그대로 싣는다', async () => {
    respondWith({ data: { lines: [{ text: '새우튀김', allergenWords: ['새우'] }], unreadLineCount: 2, evidenceStatus: 'ESTIMATED' }, error: null, meta: { requestId: 'r1' } });
    const result = await scanMenu('file:///menu.jpg', 'token', tx, 'ko');
    expect(result.state).toBe('success');
    if (result.state !== 'success') return;
    expect(result.scan.lines).toHaveLength(1);
    expect(result.scan.unreadLineCount).toBe(2);
  });

  it('🔴 계약에 없는 칸은 버린다 — 사진에 인쇄된 지시가 화면까지 오지 않는다', async () => {
    respondWith({ data: {
      lines: [{ text: '김밥', allergenWords: ['달걀'], safe: true, link: 'https://example.test', spicy: 5 }],
      unreadLineCount: 0,
      evidenceStatus: 'VERIFIED',
      hasAllergen: false,
    }, error: null, meta: { requestId: 'r1' } });
    const result = await scanMenu('file:///menu.jpg', 'token', tx, 'ko');
    expect(result.state).toBe('success');
    if (result.state !== 'success') return;
    // 서버가 translatedText 를 안 줬다 — 원문으로 물러선다(normalizeScan 의 물러섬).
    expect(result.scan.lines[0]).toEqual(line({ text: '김밥', translatedText: '김밥', allergenWords: ['달걀'] }));
    expect(Object.keys(result.scan)).toEqual(['lines', 'unreadLineCount', 'evidenceStatus']);
    // 서버가 VERIFIED 라고 해도 사진에서 읽은 값은 추정이다.
    expect(result.scan.evidenceStatus).toBe('ESTIMATED');
  });

  it('🔴 한도를 넘으면 조용한 빈 결과가 아니라 거절이 온다', async () => {
    respondWith({ data: null, error: { code: 'RATE_LIMITED', message: '한도' }, meta: { requestId: 'r1' } }, 429);
    const result = await scanMenu('file:///menu.jpg', 'token', tx, 'ko');
    expect(result.state).toBe('error');
    if (result.state !== 'error') return;
    expect(result.message).toContain('다 썼어요');
  });

  it('🔴 S15P21E201-1236 — 서버가 번역한 문구를 그대로 싣는다', async () => {
    respondWith({ data: {
      lines: [line({ text: '돼지국밥', translatedText: 'Pork bone soup', allergenWords: [] })],
      unreadLineCount: 0,
      evidenceStatus: 'ESTIMATED',
    }, error: null, meta: { requestId: 'r1' } });
    const result = await scanMenu('file:///menu.jpg', 'token', tx, 'en');
    expect(result.state).toBe('success');
    if (result.state !== 'success') return;
    expect(result.scan.lines[0]).toEqual(line({ text: '돼지국밥', translatedText: 'Pork bone soup', allergenWords: [] }));
  });

  it('로그인 안 했으면 서버를 아예 안 부른다', async () => {
    const spy = jest.fn();
    globalThis.fetch = spy as unknown as typeof fetch;
    const result = await scanMenu('file:///menu.jpg', null, tx, 'ko');
    expect(result.state).toBe('error');
    expect(spy).not.toHaveBeenCalled();
  });
});
