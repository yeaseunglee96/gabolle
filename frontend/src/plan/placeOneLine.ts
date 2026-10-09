// 장소 한 줄 소개 — 서버가 일정 항목에 oneLine 으로 싣는다(S15P21E201-2029). 관광공사 개요를 줄였거나 저장된 사실만으로 쓴 문장이다.
//
// 🔴 외국어 화면에 한국어 문장을 대신 보이지 않는다 — 읽지 못하는 글이 한 줄 더 늘 뿐이다. 그 언어가 없으면
//    같은 한자를 읽는 언어(간체↔번체) → 영어로 물러서고, 그것도 없으면 줄째 안 그린다.
import type { LanguageCode } from '@/i18n/languages';

export type OneLine = Partial<Record<'ko' | 'en' | 'ja' | 'zh-Hans' | 'zh-Hant' | 'vi', string>>;

const ORDER: Record<string, Array<keyof OneLine>> = {
  ko: ['ko'],
  en: ['en'],
  ja: ['ja', 'en'],
  'zh-Hans': ['zh-Hans', 'zh-Hant', 'en'],
  'zh-Hant': ['zh-Hant', 'zh-Hans', 'en'],
  vi: ['vi', 'en'],
};

/** 이 언어로 보일 한 줄 소개. 없으면 null. */
export function oneLineFor(oneLine: OneLine | null | undefined, language: LanguageCode): string | null {
  for (const each of ORDER[language] ?? ['en']) {
    const text = oneLine?.[each]?.trim();
    if (text) return text;
  }
  return null;
}
