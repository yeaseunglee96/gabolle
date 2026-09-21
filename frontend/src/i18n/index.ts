import { useCallback, useMemo } from 'react';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { toBcp47 } from '@/i18n/languages';
import { pickLanguage } from '@/i18n/pick';

export { fillNumbers, numericShape, pickLanguage, type LocalizedText } from '@/i18n/pick';

export function useI18n() {
  const preferences = useOnboardingPreferences();
  const language = preferences.language;
  // tx 는 언어가 바뀔 때만 새로 만든다 — 매 렌더 새 함수를 주면 이걸 의존성 배열에 넣는
  // 곳(useCallback/useEffect)마다 무한 리렌더로 이어진다 에서 실측).
  const tx = useCallback((ko: string, en: string) => pickLanguage(language, { ko, en }), [language]);
  return useMemo(() => ({
    language,
    // 숫자·날짜 표기는 고른 언어 그대로 쓴다. 문구가 영어여도 12,000 을 읽는 방식은
    // 그 나라 방식이 맞다 — 이건 번역이 없어도 바로 맞출 수 있는 것이다.
    locale: toBcp47(language),
    tx,
    setLanguage: preferences.setLanguage,
  }), [language, preferences.setLanguage, tx]);
}
