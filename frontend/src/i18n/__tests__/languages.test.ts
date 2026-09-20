// — 언어를 다섯으로 넓혔다. 여기서 재는 것은 떨어지는 자리다.
import {
  LANGUAGE_CODES,
  LANGUAGE_OPTIONS,
  parseLanguageCode,
  resolveTextLanguage,
  toBcp47,
} from '@/i18n/languages';

describe('언어 다섯', () => {
  it('한국어·영어·일본어·중국어 간체·번체다', () => {
    expect([...LANGUAGE_CODES]).toEqual(['ko', 'en', 'ja', 'zh-Hans', 'zh-Hant']);
  });

  it('🔴 간체와 번체를 하나로 묶지 않는다 — 대만에서 온 사람에게 간체를 밀지 않는다', () => {
    expect(LANGUAGE_CODES).toContain('zh-Hans');
    expect(LANGUAGE_CODES).toContain('zh-Hant');
  });

  it('고르는 이름은 그 언어를 쓰는 사람이 읽을 수 있는 말이다', () => {
    const byCode = Object.fromEntries(LANGUAGE_OPTIONS.map((o) => [o.code, o.endonym]));
    expect(byCode).toEqual({
      ko: '한국어', en: 'English', ja: '日本語', 'zh-Hans': '简体中文', 'zh-Hant': '繁體中文',
    });
  });

  it('목록과 코드 집합이 어긋나지 않는다', () => {
    expect(LANGUAGE_OPTIONS.map((o) => o.code)).toEqual([...LANGUAGE_CODES]);
  });
});

describe('화면 문구를 어느 언어로 그리나', () => {
  it('한국어는 한국어다', () => {
    expect(resolveTextLanguage('ko')).toBe('ko');
  });

  it.each(['en', 'ja', 'zh-Hans', 'zh-Hant'] as const)(
    '🔴 %s 는 영어로 떨어진다 — 한국어로 떨어지면 안 된다',
    (code) => {
      expect(resolveTextLanguage(code)).toBe('en');
      expect(resolveTextLanguage(code)).not.toBe('ko');
    },
  );
});

describe('기기·서버에 넘길 표기', () => {
  it.each([
    ['ko', 'ko-KR'], ['en', 'en-US'], ['ja', 'ja-JP'],
    ['zh-Hans', 'zh-CN'], ['zh-Hant', 'zh-TW'],
  ] as const)('%s → %s', (code, expected) => {
    expect(toBcp47(code)).toBe(expected);
  });
});

describe('저장돼 있던 값을 되돌린다', () => {
  it('아는 코드는 그대로다', () => {
    expect(parseLanguageCode('zh-Hant')).toBe('zh-Hant');
  });

  it.each(['ko', 'en'] as const)('옛 값 %s 를 버리지 않는다', (code) => {
    expect(parseLanguageCode(code)).toBe(code);
  });

  it.each([
    ['ko-KR', 'ko'], ['en-GB', 'en'], ['ja-JP', 'ja'],
    ['zh-CN', 'zh-Hans'], ['zh-Hans-CN', 'zh-Hans'],
    ['zh-TW', 'zh-Hant'], ['zh-HK', 'zh-Hant'], ['zh-Hant-TW', 'zh-Hant'],
  ])('기기 설정에서 오는 %s 를 %s 로 본다', (input, expected) => {
    expect(parseLanguageCode(input)).toBe(expected);
  });

  it.each([undefined, null, 42, '', 'klingon', {}])('모르는 값 %p 는 한국어다', (value) => {
    expect(parseLanguageCode(value)).toBe('ko');
  });
});
