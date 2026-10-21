// 여행 기분 설명 — 일본어·중국어 화면에서도 그 언어로 — S15P21E201-1914(실기기 10/2).
import { pickLanguage } from '@/i18n/pick';
import type { LanguageCode } from '@/i18n/languages';

const createTranslator = (language: LanguageCode) => (ko: string, en: string) => pickLanguage(language, { ko, en });
import { PACE_OPTIONS, paceSubtitle } from '@/plan/planOptions';

it('🔴 일본어 화면에서 영어가 아니라 일본어 첫 마디', () => {
  const tx = createTranslator('ja');
  expect(paceSubtitle(PACE_OPTIONS[0], tx)).toBe('3時間に1か所ほど');
  expect(paceSubtitle(PACE_OPTIONS[2], createTranslator('zh-Hant'))).toBe('約每 1.5 小時一處');
});
