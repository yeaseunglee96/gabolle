// 음식 이름을 화면 언어로 (S15P21E201-1776). 서버용 언어(ko|en)로 고르면 일본어 화면에 영어가 나갔다.
import { setApiLanguage } from '@/api/client';
import { setCurrentLanguage } from '@/i18n/languages';
import { getTranslation } from '@/i18n/translations';
import { foodLabel } from '@/plan/foodConflicts';

afterEach(() => { setApiLanguage('ko'); setCurrentLanguage('ko'); });

it('🔴 일본어 화면(서버용 언어는 en)에서 영어가 아니라 번역표의 일본어다', () => {
  setApiLanguage('en'); setCurrentLanguage('ja');
  const ja = getTranslation('돼지국밥', 'ja');
  expect(ja).toBeTruthy();
  expect(foodLabel('PORK_SOUP')).toBe(ja);
});

it('영어·한국어는 그대로', () => {
  setApiLanguage('en'); setCurrentLanguage('en');
  expect(foodLabel('PORK_SOUP')).toBe('Pork bone soup');
  setApiLanguage('ko'); setCurrentLanguage('ko');
  expect(foodLabel('PORK_SOUP')).toBe('돼지국밥');
});
