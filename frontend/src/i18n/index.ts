import { useCallback, useMemo } from 'react';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { resolveTextLanguage, toBcp47, type LanguageCode } from '@/i18n/languages';
import { getTranslation } from '@/i18n/translations';

export type LocalizedText = { ko: string; en: string };

/**
 * 사용자 요청(2026-09-17): 일본어·중국어(간체·번체)도 그 언어로 보여준다.
 *
 * translations.ts 표에서 그 언어의 번역을 먼저 찾는다. 없으면 예전 규칙(S15P21E201-1109)
 * 그대로 **영어**로 떨어진다 — 한국어를 보여주면 읽을 수 없는 글자를 미는 것이 된다.
 * 표를 계속 채우는 동안에도 화면이 항상 읽을 수 있는 글자를 보여주는 이유다.
 */
export function pickLanguage(language: LanguageCode, text: LocalizedText) {
  if (language === 'ja' || language === 'zh-Hans' || language === 'zh-Hant') {
    const field = language === 'ja' ? 'ja' : language === 'zh-Hans' ? 'zhHans' : 'zhHant';
    const translated = getTranslation(text.ko, field);
    if (translated) return translated;
  }
  return text[resolveTextLanguage(language)] ?? text.ko;
}

export function useI18n() {
  const preferences = useOnboardingPreferences();
  const language = preferences.language;
  // tx 는 언어가 바뀔 때만 새로 만든다 — 매 렌더 새 함수를 주면 이걸 의존성 배열에 넣는
  // 곳(useCallback/useEffect)마다 무한 리렌더로 이어진다 (S15P21E201-658 에서 실측).
  const tx = useCallback((ko: string, en: string) => pickLanguage(language, { ko, en }), [language]);
  return useMemo(() => ({
    language,
    // 숫자·날짜 표기는 **고른 언어 그대로** 쓴다. 문구가 영어여도 12,000 을 읽는 방식은
    // 그 나라 방식이 맞다 — 이건 번역이 없어도 바로 맞출 수 있는 것이다.
    locale: toBcp47(language),
    tx,
    setLanguage: preferences.setLanguage,
  }), [language, preferences.setLanguage, tx]);
}
