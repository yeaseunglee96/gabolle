// 간체·번체 화면에서 「韩 / 元」처럼 통화 단위가 두 줄로 갈라지던 것 — S15P21E201-1980.
// 한자는 글자마다 줄을 바꿀 수 있어서, 단위 두 글자 사이와 숫자 뒤 빈칸을 붙여 둔다(WORD JOINER · NBSP).
import { TRANSLATIONS } from '@/i18n/translations';

describe('통화 단위 줄바꿈', () => {
  const rows = Object.values(TRANSLATIONS);
  it('🔴 번역 안의 韩元·韓元 은 두 글자가 붙어 있다(U+2060 로)', () => {
    for (const row of rows) for (const text of [row.zhHans, row.zhHant]) {
      if (!text) continue;
      expect(text).not.toMatch(/韩元|韓元/);
    }
  });
  it('「%s 韩元」의 숫자와 단위 사이도 끊기지 않는다', () => {
    expect(TRANSLATIONS['%s원']?.zhHans).toBe('%s\u00A0韩\u2060元');
    expect(TRANSLATIONS['%s원']?.zhHant).toBe('%s\u00A0韓\u2060元');
  });
});
