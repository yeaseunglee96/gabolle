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

it('🔴 「다른 곳보다 ○○이 돋보임」도 일본어로 — 값을 끼운 틀이 아니라 완성된 문구로 찾는다', () => {
  setCurrentLanguage('ja');
  expect(reasonLabel('TOP_CONTRIBUTOR_distance')).toBe('他より距離が際立つ');
  setCurrentLanguage('en');
  expect(reasonLabel('TOP_CONTRIBUTOR_distance')).toBe('Stands out for distance');
  setCurrentLanguage('ko');
  expect(reasonLabel('TOP_CONTRIBUTOR_distance')).toBe('다른 곳보다 거리가 돋보임');
});
