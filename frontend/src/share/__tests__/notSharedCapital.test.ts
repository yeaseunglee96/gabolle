// 공유 페이지 영어 안내 문장 첫 글자 — S15P21E201-1915(실기기 10/2).
import { sentenceStart } from '@/share/sharedPageLanguage';

it('🔴 소문자로 시작하는 영어 문장은 첫 글자만 대문자로', () => {
  expect(sentenceStart('starting point, contact info are not shared.')).toBe('Starting point, contact info are not shared.');
});

it('한국어·이미 대문자는 그대로', () => {
  expect(sentenceStart('출발지 · 연락처는 공유되지 않아요.')).toBe('출발지 · 연락처는 공유되지 않아요.');
  expect(sentenceStart('Budget is not shared.')).toBe('Budget is not shared.');
});
