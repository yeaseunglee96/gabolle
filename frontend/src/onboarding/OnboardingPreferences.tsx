import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { getApiLanguage, setApiLanguage } from '@/api/client';
// 언어 정의는 src/i18n/languages.ts 한 곳에 있다 여기 두면 화면 문구
// 번역 규칙과 떨어져서, 언어를 늘릴 때 한쪽만 늘어난다. 쓰던 이름은 그대로 내보낸다.
import { LANGUAGE_CODES, parseLanguageCode, resolveTextLanguage, type LanguageCode } from '@/i18n/languages';

export { LANGUAGE_CODES };
export type { LanguageCode };

// 이 컨텍스트 밖에서 부르면 언어 정보를 이 컨텍스트에서 얻을 수 없다 — 그래서 이 에러 메시지 자체는
// setApiLanguage로 동기화되는 모듈 변수(기본값 'ko')를 대신 읽는다.
const tx = (ko: string, en: string) => (getApiLanguage() === 'en' ? en : ko);

export const MOBILITY_CODES = ['none', 'wheelchair', 'stroller', 'slow'] as const;

export type MobilityCode = (typeof MOBILITY_CODES)[number];

type OnboardingPreferencesValue = {
  language: LanguageCode;
  mobility: MobilityCode;
  hydrated: boolean;
  hasEnteredApp: boolean;
  markEnteredApp: () => void;
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
  const [hasEnteredApp, setHasEnteredApp] = useState(false);
  const changedBeforeHydration = useRef(false);

  useEffect(() => {
    let active = true;
    void AsyncStorage.getItem(STORAGE_KEY).then((raw) => {
      if (!active || !raw) return;
      try {
        const stored = JSON.parse(raw) as { language?: unknown; mobility?: unknown; hasEnteredApp?: unknown };
        if (stored.hasEnteredApp === true) setHasEnteredApp(true);
        if (changedBeforeHydration.current) return;
        if (typeof stored.language === 'string') {
          // 언어가 둘뿐이던 시절의 값도 그대로 산다 — parseLanguageCode 가 받아 준다.
          setLanguage(parseLanguageCode(stored.language));
        }
        if (typeof stored.mobility === 'string' && MOBILITY_CODES.some((code) => code === stored.mobility)) {
          setMobility(stored.mobility as MobilityCode);
        }
      } catch {
        void AsyncStorage.removeItem(STORAGE_KEY).catch(() => {});
      }
    }).catch(() => {
      // 저장소를 읽지 못해도 첫 화면을 영원히 가로막지 않는다.
    }).finally(() => {
      if (active) setHydrated(true);
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    // 서버에 넘기는 Accept-Language 는 번역이 있는 언어다. 일본어를 넘기면 서버가
    // 일본어 오류 문구를 줄 것처럼 보이지만 지금 서버에는 그 문구가 없다.
    setApiLanguage(resolveTextLanguage(language));
    void AsyncStorage.setItem(STORAGE_KEY, JSON.stringify({ language, mobility, hasEnteredApp })).catch(() => {});
  }, [hydrated, language, mobility, hasEnteredApp]);

  const value = useMemo<OnboardingPreferencesValue>(
    () => ({ language, mobility, hydrated, hasEnteredApp, markEnteredApp: () => setHasEnteredApp(true), setLanguage: (nextLanguage) => {
      if (!hydrated) changedBeforeHydration.current = true;
      setApiLanguage(resolveTextLanguage(nextLanguage));
      setLanguage(nextLanguage);
    }, setPreferences: (nextLanguage, nextMobility) => {
      if (!hydrated) changedBeforeHydration.current = true;
      setApiLanguage(resolveTextLanguage(nextLanguage));
      setLanguage(nextLanguage);
      setMobility(nextMobility);
    },
    // 로그아웃·계정 삭제 때 부른다 — 같은 기기에서 다음 사람이 로그인하면 이 값들이
    // 그 사람 것처럼 보인다. 개인 설정만 초기화하고 앱 이용 이력은 유지한다.
    reset: () => {
      setApiLanguage('ko');
      setLanguage('ko');
      setMobility('none');
      void AsyncStorage.setItem(STORAGE_KEY, JSON.stringify({ language: 'ko', mobility: 'none', hasEnteredApp })).catch(() => {});
    } }),
    [hydrated, language, mobility, hasEnteredApp],
  );

  return <OnboardingPreferencesContext.Provider value={value}>{children}</OnboardingPreferencesContext.Provider>;
}

export function useOnboardingPreferences() {
  const value = useContext(OnboardingPreferencesContext);
  if (!value) throw new Error(tx('useOnboardingPreferences는 OnboardingPreferencesProvider 안에서 사용해야 합니다.', 'useOnboardingPreferences must be used inside OnboardingPreferencesProvider.'));
  return value;
}
