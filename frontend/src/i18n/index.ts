import { useCallback, useMemo } from 'react';
import { useOnboardingPreferences, type LanguageCode } from '@/onboarding/OnboardingPreferences';

export type LocalizedText = { ko: string; en: string };

export function pickLanguage(language: LanguageCode, text: LocalizedText) {
  return text[language] ?? text.ko;
}

export function useI18n() {
  const preferences = useOnboardingPreferences();
  const language = preferences.language;
  // tx 는 언어가 바뀔 때만 새로 만든다 — 매 렌더 새 함수를 주면 이걸 의존성 배열에 넣는
  // 곳(useCallback/useEffect)마다 무한 리렌더로 이어진다 (S15P21E201-658 에서 실측).
  const tx = useCallback((ko: string, en: string) => pickLanguage(language, { ko, en }), [language]);
  return useMemo(() => ({
    language,
    locale: language === 'en' ? 'en-US' : 'ko-KR',
    tx,
    setLanguage: preferences.setLanguage,
  }), [language, preferences.setLanguage, tx]);
}
