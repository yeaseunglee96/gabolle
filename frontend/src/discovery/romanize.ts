// 언어 다섯을 다 받는다 이 함수가 'ko' | 'en' 만 받으면 부르는 쪽
// 열다섯 곳이 각자 떨어뜨려야 하고, 한 곳만 빠뜨리면 일본어 사용자가 한국어 이름을 본다.
// 떨어뜨리는 일은 여기 한 자리에서 한다.
import { resolveTextLanguage, type LanguageCode } from '@/i18n/languages';
// 한글 이름을 소리 나는 대로 적어 준다 — 명세 4절 /.
const CHOSEONG = ['g', 'kk', 'n', 'd', 'tt', 'r', 'm', 'b', 'pp', 's', 'ss', '', 'j', 'jj', 'ch', 'k', 't', 'p', 'h'];
const JUNGSEONG = ['a', 'ae', 'ya', 'yae', 'eo', 'e', 'yeo', 'ye', 'o', 'wa', 'wae', 'oe', 'yo', 'u', 'wo', 'we', 'wi', 'yu', 'eu', 'ui', 'i'];
const JONGSEONG = ['', 'k', 'k', 'k', 'n', 'n', 'n', 't', 'l', 'k', 'm', 'l', 'l', 'l', 'p', 'l', 'm', 'p', 't', 't', 'ng', 't', 't', 'k', 't', 'p', 't'];

const HANGUL_START = 0xac00;
const HANGUL_END = 0xd7a3;

/** 한글 음절 하나를 초성·중성·종성으로 풀어 적는다. 한글이 아니면 그대로 둔다. */
function romanizeSyllable(character: string): string {
  const code = character.charCodeAt(0);
  if (code < HANGUL_START || code > HANGUL_END) return character;
  const offset = code - HANGUL_START;
  const cho = Math.floor(offset / 588);
  const jung = Math.floor((offset % 588) / 28);
  const jong = offset % 28;
  return `${CHOSEONG[cho]}${JUNGSEONG[jung]}${JONGSEONG[jong]}`;
}

/** @returns 한글이 하나도 없으면 null — 적을 것이 없는데 빈 괄호를 만들지 않는다. */
export function romanizeKorean(text: string): string | null {
  if (!/[가-힣]/.test(text)) return null;
  const romanized = [...text].map(romanizeSyllable).join('');
  // 낱말의 첫 글자만 올려 준다 — 장소 이름이라 문장이 아니다.
  return romanized.replace(/(^|\s)([a-z])/g, (_match, space: string, letter: string) => `${space}${letter.toUpperCase()}`).trim() || null;
}

/** 화면에 보여줄 장소 이름. */
export function placeNameForLanguage(nameKo: string, nameEn: string | null | undefined, language: LanguageCode): string {
  const trimmedEn = nameEn?.trim();
  if (resolveTextLanguage(language) === 'ko') return trimmedEn ? `${nameKo} (${trimmedEn})` : nameKo;
  if (trimmedEn) return `${trimmedEn} (${nameKo})`;
  const reading = romanizeKorean(nameKo);
  return reading ? `${nameKo} (${reading})` : nameKo;
}
