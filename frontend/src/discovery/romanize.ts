// 한글 이름을 소리 나는 대로 적어 준다 — 명세 4절 / S15P21E201-430.
//
// 🔴 이것은 **공식 영어 이름이 아니다.** 장소의 영어 이름(`nameEn`)은 대부분 null 이고
// 앞으로도 채워질 계획이 없다. 영어 화면에서 한글만 덩그러니 두면 읽지도 못하고 물어보지도
// 못하므로, 한글은 그대로 두고 **읽는 법을 옆에 적는다.**
//
// 🔴 소리 바뀜(자음 동화)은 넣지 않았다. 국어의 로마자 표기법은 앞뒤 글자가 만나 소리가
// 바뀌는 규칙이 있어서(광안리 → Gwangalli), 글자마다 옮기는 이 방식은 그 자리에서
// 어긋난다(Gwang-an-ri). 규칙을 절반만 넣으면 어디가 맞고 어디가 틀린지 아무도 모르게
// 되므로 아예 넣지 않았고, **화면에서 「읽는 법」이라고만 말한다** — 공식 표기라고 말하지
// 않는다. 정확한 표기가 필요해지면 그때는 사람이 확인한 값을 데이터로 받아야 한다.
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

/**
 * @returns 한글이 하나도 없으면 null — 적을 것이 없는데 빈 괄호를 만들지 않는다.
 */
export function romanizeKorean(text: string): string | null {
  if (!/[가-힣]/.test(text)) return null;
  const romanized = [...text].map(romanizeSyllable).join('');
  // 낱말의 첫 글자만 올려 준다 — 장소 이름이라 문장이 아니다.
  return romanized.replace(/(^|\s)([a-z])/g, (_match, space: string, letter: string) => `${space}${letter.toUpperCase()}`).trim() || null;
}

/**
 * 화면에 보여줄 장소 이름.
 *
 * - 한국어 화면: 한글 그대로. 영어 이름이 있으면 괄호로 덧붙인다(지금까지와 같다)
 * - 영어 화면: 영어 이름이 있으면 그것을, 없으면 **한글 + 읽는 법**
 */
export function placeNameForLanguage(nameKo: string, nameEn: string | null | undefined, language: 'ko' | 'en'): string {
  const trimmedEn = nameEn?.trim();
  if (language === 'ko') return trimmedEn ? `${nameKo} (${trimmedEn})` : nameKo;
  if (trimmedEn) return `${trimmedEn} (${nameKo})`;
  const reading = romanizeKorean(nameKo);
  return reading ? `${nameKo} (${reading})` : nameKo;
}
