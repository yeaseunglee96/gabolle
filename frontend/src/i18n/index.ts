import { useCallback, useMemo } from 'react';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { resolveTextLanguage, toBcp47, type LanguageCode } from '@/i18n/languages';

export type LocalizedText = { ko: string; en: string };

/**
 * 🔴 화면 문구는 아직 한국어·영어 두 벌뿐이다 (S15P21E201-1109). 일본어·중국어를 고른
 * 사람에게는 **영어**를 보여준다 — 한국어를 보여주면 읽을 수 없는 글자를 미는 것이 된다.
 * 왜 이 단계로 가는지는 src/i18n/languages.ts 머리말에 있다.
 */
export function pickLanguage(language: LanguageCode, text: LocalizedText) {
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
