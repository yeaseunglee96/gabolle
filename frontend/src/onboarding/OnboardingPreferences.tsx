import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';

export const LANGUAGE_CODES = ['ko', 'en', 'ja', 'zh-Hans', 'zh-Hant'] as const;
export const MOBILITY_CODES = ['none', 'wheelchair', 'stroller', 'slow'] as const;

export type LanguageCode = (typeof LANGUAGE_CODES)[number];
export type MobilityCode = (typeof MOBILITY_CODES)[number];

type OnboardingPreferencesValue = {
  language: LanguageCode;
  mobility: MobilityCode;
  setPreferences: (language: LanguageCode, mobility: MobilityCode) => void;
};

const OnboardingPreferencesContext = createContext<OnboardingPreferencesValue | null>(null);

export function parseLanguage(value: string | string[] | undefined): LanguageCode {
  if (typeof value === 'string' && LANGUAGE_CODES.some((code) => code === value)) {
    return value as LanguageCode;
  }
  throw new Error(`지원하지 않는 언어 선택값입니다: ${String(value)}`);
}

export function parseMobility(value: string | string[] | undefined): MobilityCode {
  if (typeof value === 'string' && MOBILITY_CODES.some((code) => code === value)) {
    return value as MobilityCode;
  }
  throw new Error(`지원하지 않는 이동 조건 선택값입니다: ${String(value)}`);
}

export function OnboardingPreferencesProvider({ children }: { children: ReactNode }) {
  const [language, setLanguage] = useState<LanguageCode>('ko');
  const [mobility, setMobility] = useState<MobilityCode>('none');
  const value = useMemo<OnboardingPreferencesValue>(
    () => ({ language, mobility, setPreferences: (nextLanguage, nextMobility) => {
      setLanguage(nextLanguage);
      setMobility(nextMobility);
    } }),
    [language, mobility],
  );

  return <OnboardingPreferencesContext.Provider value={value}>{children}</OnboardingPreferencesContext.Provider>;
}

export function useOnboardingPreferences() {
  const value = useContext(OnboardingPreferencesContext);
  if (!value) throw new Error('useOnboardingPreferences는 OnboardingPreferencesProvider 안에서 사용해야 합니다.');
  return value;
}
