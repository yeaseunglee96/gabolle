import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { getApiLanguage, setApiLanguage } from '@/api/client';

// 이 컨텍스트 밖에서 부르면 언어 정보를 이 컨텍스트에서 얻을 수 없다 — 그래서 이 에러 메시지 자체는
// setApiLanguage로 동기화되는 모듈 변수(기본값 'ko')를 대신 읽는다.
const tx = (ko: string, en: string) => (getApiLanguage() === 'en' ? en : ko);

export const LANGUAGE_CODES = ['ko', 'en'] as const;
export const MOBILITY_CODES = ['none', 'wheelchair', 'stroller', 'slow'] as const;

export type LanguageCode = (typeof LANGUAGE_CODES)[number];
export type MobilityCode = (typeof MOBILITY_CODES)[number];

type OnboardingPreferencesValue = {
  language: LanguageCode;
  mobility: MobilityCode;
  hydrated: boolean;
  setLanguage: (language: LanguageCode) => void;
  setPreferences: (language: LanguageCode, mobility: MobilityCode) => void;
  reset: () => void;
};

const OnboardingPreferencesContext = createContext<OnboardingPreferencesValue | null>(null);
const STORAGE_KEY = 'gabolle:onboarding-preferences';

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
  const [hydrated, setHydrated] = useState(false);
  const changedBeforeHydration = useRef(false);

  useEffect(() => {
    let active = true;
    void AsyncStorage.getItem(STORAGE_KEY).then((raw) => {
      if (!active || !raw || changedBeforeHydration.current) return;
      try {
        const stored = JSON.parse(raw) as { language?: unknown; mobility?: unknown };
        if (typeof stored.language === 'string' && LANGUAGE_CODES.some((code) => code === stored.language)) {
          setLanguage(stored.language as LanguageCode);
        }
        if (typeof stored.mobility === 'string' && MOBILITY_CODES.some((code) => code === stored.mobility)) {
          setMobility(stored.mobility as MobilityCode);
        }
      } catch {
        void AsyncStorage.removeItem(STORAGE_KEY);
      }
    }).finally(() => {
      if (active) setHydrated(true);
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    setApiLanguage(language);
    void AsyncStorage.setItem(STORAGE_KEY, JSON.stringify({ language, mobility }));
  }, [hydrated, language, mobility]);

  const value = useMemo<OnboardingPreferencesValue>(
    () => ({ language, mobility, hydrated, setLanguage: (nextLanguage) => {
      if (!hydrated) changedBeforeHydration.current = true;
      setLanguage(nextLanguage);
    }, setPreferences: (nextLanguage, nextMobility) => {
      if (!hydrated) changedBeforeHydration.current = true;
      setLanguage(nextLanguage);
      setMobility(nextMobility);
    },
    // 로그아웃·계정 삭제 때 부른다 — 같은 기기에서 다음 사람이 로그인하면 이 값들이
    // 그 사람 것처럼 보인다(S15P21E201-740). 기본값으로 되돌리고 저장된 값도 지운다.
    reset: () => {
      setLanguage('ko');
      setMobility('none');
      void AsyncStorage.removeItem(STORAGE_KEY);
    } }),
    [hydrated, language, mobility],
  );

  return <OnboardingPreferencesContext.Provider value={value}>{children}</OnboardingPreferencesContext.Provider>;
}

export function useOnboardingPreferences() {
  const value = useContext(OnboardingPreferencesContext);
  if (!value) throw new Error(tx('useOnboardingPreferences는 OnboardingPreferencesProvider 안에서 사용해야 합니다.', 'useOnboardingPreferences must be used inside OnboardingPreferencesProvider.'));
  return value;
}
