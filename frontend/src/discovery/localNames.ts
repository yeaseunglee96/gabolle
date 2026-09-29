// 장소 이름의 일본어·중국어 — 서버가 관광공사 번역 이름을 localNames 로 싣는다(S15P21E201-1859).
//
// 🔴 전에는 장소 이름이 한국어·영어만 있어서 일본어·중국어 화면의 장소 이름이 전부 한국어(또는 영어)였다
//    (사용자 지적 2026-09-30). 관광공사가 번역해 둔 곳만 있어 언어마다 150곳 안팎이다 — 없으면 예전처럼 영어·한국어로 물러선다.
import type { LanguageCode } from '@/i18n/languages';

/** 키는 앱의 언어 코드. 없는 언어는 빠져 온다. */
export type LocalNames = Partial<Record<'ja' | 'zh-Hans' | 'zh-Hant', string>>;

/** 이 언어의 번역 이름 — 한국어·영어 화면이거나 없으면 null. */
export function localNameFor(localNames: LocalNames | null | undefined, language: LanguageCode): string | null {
  if (language !== 'ja' && language !== 'zh-Hans' && language !== 'zh-Hant') return null;
  const name = localNames?.[language]?.trim();
  return name || null;
}

/**
 * 한국어 곁에 적을 «다른 이름» — 일본어·중국어는 번역 이름이 있으면 그것, 없으면 영어 이름.
 * placeNameForLanguage·stopNameForLanguage 의 nameEn 자리에 넘긴다(「国際市場 (국제시장)」).
 */
export function otherNameFor(nameEn: string | null | undefined, localNames: LocalNames | null | undefined, language: LanguageCode): string | null {
  return localNameFor(localNames, language) ?? nameEn ?? null;
}
