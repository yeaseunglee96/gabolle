// 일정 항목의 «다른 이름» — 일정 응답이 실은 이름이 먼저, 없으면 따로 받은 장소 정보(사진 묶음)의 이름(S15P21E201-1938).
//
// 전에는 일정 화면이 장소 이름을 사진 묶음에서만 찾아, 그 묶음에 없거나 아직 안 온 곳은 다른 언어에서도 한국어만 보였다.
import { otherNameFor, type LocalNames } from '@/discovery/localNames';
import type { LanguageCode } from '@/i18n/languages';

type Named = { nameEn?: string | null; localNames?: LocalNames } | null | undefined;

/** stopNameForLanguage·StopName 의 nameEn 자리에 넘길 이름 — 일본어·중국어는 번역 이름, 그 밖은 영어 이름. */
export function itemOtherName(item: Named, place: Named, language: LanguageCode): string | null {
  return otherNameFor(item?.nameEn ?? place?.nameEn, item?.localNames ?? place?.localNames, language);
}
