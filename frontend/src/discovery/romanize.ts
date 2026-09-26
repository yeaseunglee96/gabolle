// 언어 다섯을 다 받는다. 이 함수가 'ko' | 'en' 만 받으면 부르는 쪽
// 열다섯 곳이 각자 떨어뜨려야 하고, 한 곳만 빠뜨리면 일본어 사용자가 한국어 이름을 본다.
// 떨어뜨리는 일은 여기 한 자리에서 한다.
import { resolveTextLanguage, type LanguageCode } from '@/i18n/languages';
// 한글 이름을 소리 나는 대로 적어 준다 — 명세 4절 /.
const CHOSEONG = ['g', 'kk', 'n', 'd', 'tt', 'r', 'm', 'b', 'pp', 's', 'ss', '', 'j', 'jj', 'ch', 'k', 't', 'p', 'h'];
const JUNGSEONG = ['a', 'ae', 'ya', 'yae', 'eo', 'e', 'yeo', 'ye', 'o', 'wa', 'wae', 'oe', 'yo', 'u', 'wo', 'we', 'wi', 'yu', 'eu', 'ui', 'i'];
// 🔴 받침은 «없음»까지 28칸이다 — S15P21E201-1543. 예전 표는 27칸이라 18번(ㅄ)이 빠져 그 뒤가 한 칸씩
//    밀렸다: 받침 ㅇ 이 t 가 되어 「송정해수욕장」이 「Sotjeothaesuyokjat」으로, ㅎ 받침은 칸이 없어
//    「undefined」가 글자 속에 들어갔다. 가장 흔한 받침이라 한국어가 아닌 화면의 장소 이름 상당수가 틀렸다.
//                     없음 ㄱ   ㄲ   ㄳ   ㄴ   ㄵ   ㄶ   ㄷ   ㄹ   ㄺ   ㄻ   ㄼ   ㄽ   ㄾ   ㄿ   ㅀ   ㅁ   ㅂ   ㅄ   ㅅ   ㅆ   ㅇ    ㅈ   ㅊ   ㅋ   ㅌ   ㅍ   ㅎ
const JONGSEONG = ['', 'k', 'k', 'k', 'n', 'n', 'n', 't', 'l', 'k', 'm', 'l', 'l', 'l', 'p', 'l', 'm', 'p', 'p', 't', 't', 'ng', 't', 't', 'k', 't', 'p', 't'];

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

/**
 * 일정·여행 중 카드에 적을 장소 이름 — S15P21E201-1735.
 *
 * 🔴 한국어 화면은 일정 제목 그대로다(장소 상세와 달리 영어 이름을 덧붙이지 않는다 — 한국어 화면은 바꾸지 않기로 했다).
 *    그 밖의 언어는 장소 상세와 같은 규칙: 영어 이름이 있으면 「영어 (한글)」, 없으면 「한글 (로마자)」.
 *    일정 항목에는 영어 이름 칸이 없어서, 부르는 쪽이 장소 사진 조회(placePhotos)에서 받은 영어 이름을 넘긴다.
 */
export function stopNameForLanguage(title: string, nameEn: string | null | undefined, language: LanguageCode): string {
  if (resolveTextLanguage(language) === 'ko') return title;
  return placeNameForLanguage(title, nameEn, language);
}

/**
 * 좁은 한 줄에 쓸 때 쪼갠 것 — 한글(hangul)은 자르지 않고 나머지(other: 영어 이름이나 로마자)만 줄임표로 자른다.
 * 한글은 택시·길 묻기에 그대로 보여 줘야 해서 잘리면 안 된다. otherFirst 면 「영어 (한글)」 순서.
 */
export function stopNameParts(title: string, nameEn: string | null | undefined, language: LanguageCode): { hangul: string; other: string | null; otherFirst: boolean } {
  if (resolveTextLanguage(language) === 'ko') return { hangul: title, other: null, otherFirst: false };
  const trimmedEn = nameEn?.trim();
  if (trimmedEn) return { hangul: title, other: trimmedEn, otherFirst: true };
  return { hangul: title, other: romanizeKorean(title), otherFirst: false };
}
