// 추천 이유를 고른 언어로 (S15P21E201-1767).
import { setCurrentLanguage } from '@/i18n/languages';
import { reasonLabel } from '@/plan/recommendations';

afterEach(() => setCurrentLanguage('ko'));

it('🔴 일본어 화면에서는 영어가 아니라 일본어다', () => {
  setCurrentLanguage('ja');
  expect(reasonLabel('TAG_MATCH_INTEREST')).toBe('興味カテゴリーと一致');
});

it('영어·한국어는 지금과 같다', () => {
  setCurrentLanguage('en');
  expect(reasonLabel('TAG_MATCH_INTEREST')).toBe('Matches your interests');
  setCurrentLanguage('ko');
  expect(reasonLabel('TAG_MATCH_INTEREST')).toBe('관심 카테고리와 일치');
});
