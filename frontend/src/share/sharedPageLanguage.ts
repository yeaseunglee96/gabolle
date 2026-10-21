import { parseLanguageCode, type LanguageCode } from '@/i18n/languages';

/**
 * 공유 링크를 처음 여는 사람의 언어 — S15P21E201-1777(고지혁 QA).
 * 링크는 대개 브라우저로 열리고, 브라우저에는 앱에서 고른 언어가 없어 기본값(한국어)이 떴다.
 * 이 서비스에 들어온 적 없는(hasEnteredApp=false) 웹 방문자면 브라우저 언어를 따른다.
 * 들어온 적이 있으면 그 사람이 고른 언어가 저장돼 있으니 건드리지 않는다.
 * @returns 바꿀 언어. 바꿀 것이 없으면 null
 */
export function sharedPageInitialLanguage(input: { web: boolean; hydrated: boolean; hasEnteredApp: boolean; current: LanguageCode; browserLanguages: readonly string[] | undefined }): LanguageCode | null {
  if (!input.web || !input.hydrated || input.hasEnteredApp) return null;
  const first = input.browserLanguages?.find((tag) => typeof tag === 'string' && tag.trim() !== '');
  if (!first) return null;
  const lower = first.toLowerCase();
  // parseLanguageCode 는 모르는 언어를 한국어로 떨어뜨린다 — 모르는 브라우저 언어(fr 등)는 한국어보다 영어가 낫다.
  const next = /^(ko|ja|en|zh)/.test(lower) ? parseLanguageCode(first) : 'en';
  return next === input.current ? null : next;
}

export function browserLanguages(): readonly string[] | undefined {
  if (typeof navigator === 'undefined') return undefined;
  return navigator.languages?.length ? navigator.languages : navigator.language ? [navigator.language] : undefined;
}

/** 영어 문장 첫 글자를 대문자로 — 공유 페이지 「starting point, … are not shared.」(S15P21E201-1915). 다른 글자는 그대로. */
export function sentenceStart(text: string): string {
  return text.replace(/^[a-z]/, (c) => c.toUpperCase());
}
