// 웹 첫 HTML 언어 — S15P21E201-1980.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { setHtmlLang } = require('../../../tools/set-html-lang.cjs') as { setHtmlLang: (html: string, lang?: string) => string };

describe('setHtmlLang', () => {
  it('expo 가 굽는 lang="en" 을 ko 로 바꾼다', () => {
    expect(setHtmlLang('<!DOCTYPE html><html lang="en"><head></head></html>')).toContain('<html lang="ko">');
  });
  it('lang 이 없으면 넣는다', () => {
    expect(setHtmlLang('<html><body></body></html>')).toContain('<html lang="ko">');
  });
  it('다른 속성은 그대로 둔다', () => {
    expect(setHtmlLang('<html class="x" lang=\'en\'>')).toBe('<html class="x" lang=\'ko\'>');
  });
});
