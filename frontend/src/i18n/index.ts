import { useOnboardingPreferences, type LanguageCode } from '@/onboarding/OnboardingPreferences';

export type LocalizedText = { ko: string; en: string };

export function pickLanguage(language: LanguageCode, text: LocalizedText) {
  return text[language] ?? text.ko;
}

export function useI18n() {
  const preferences = useOnboardingPreferences();
  return {
    language: preferences.language,
    locale: preferences.language === 'en' ? 'en-US' : 'ko-KR',
    tx: (ko: string, en: string) => pickLanguage(preferences.language, { ko, en }),
    setLanguage: preferences.setLanguage,
  };
}
