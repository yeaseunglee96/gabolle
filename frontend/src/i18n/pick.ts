// 🔴 훅이 없는 모듈이다 — 오류 경계(클래스 컴포넌트)나 컨텍스트 밖의 throw 처럼 useI18n 을 못 쓰는 자리도
//    이 파일은 가져올 수 있다. index.ts 는 OnboardingPreferences 를 가져오므로 그쪽에서 index.ts 를
//    가져오면 순환이 된다(S15P21E201-1340). 여기서는 번역표만 본다.
import { resolveTextLanguage, type LanguageCode } from '@/i18n/languages';
import { getTranslation } from '@/i18n/translations';

export type LocalizedText = { ko: string; en: string };

/**
 * 🔴 **숫자가 끼어 있는 문구는 표에서 «영원히» 못 찾는다** — S15P21E201-1344.
 *
 * 번역표는 한국어 원문 자체를 열쇠로 쓴다(gettext 방식). 그런데 문구에 값이 끼면
 * 열쇠가 실행할 때마다 달라진다.
 *
 * <pre>
 *   tx(`사진 ${images.length}/3`, …)  →  「사진 1/3」 「사진 2/3」 「사진 3/3」
 * </pre>
 *
 * 표에 「사진 1/3」을 넣어 봐야 두 장째부터 또 못 찾는다. 세 줄을 다 넣어도 장수가
 * 늘면 또 샌다. 그래서 **못 찾는 것이 아니라 못 넣는 것**이고, 이런 자리가 앱에
 * **208곳**이다(2026-09-20 실측). 전부 일본어·중국어에서 영어로 떨어져 있었다.
 *
 * <p>고치는 방법은 **숫자를 빼고 «모양»으로 찾는 것**이다. 「사진 2/3」에서 숫자를
 * 빼면 「사진 %d/%d」이고, 이 모양 하나만 표에 넣으면 장수가 몇이든 찾힌다. 찾은 뒤
 * 빼 두었던 숫자를 순서대로 도로 끼운다.
 *
 * <p>🔴 **이름이 끼는 자리는 이걸로 못 고친다.** `${displayName}의 기록` 처럼 값이
 * 글자면 무엇이 값이고 무엇이 문구인지 나중에는 알 수 없다. 그런 자리는 문구를
 * 쪼개서 부르는 수밖에 없다(예: 메뉴판 출처 줄을 `tx('예시','Example')` + 값으로 나눴다).
 */
const NUMBER_RUN = /\d[\d,]*/g;

/** 「사진 2/3」 → `{ shape: '사진 %d/%d', numbers: ['2', '3'] }`. 숫자가 없으면 null. */
export function numericShape(text: string): { shape: string; numbers: string[] } | null {
  const numbers: string[] = [];
  const shape = text.replace(NUMBER_RUN, (found) => {
    numbers.push(found);
    return '%d';
  });
  return numbers.length > 0 ? { shape, numbers } : null;
}

/** 「写真 %d/%d」 + `['2','3']` → 「写真 2/3」. 자리가 모자라면 그 자리는 그대로 둔다. */
export function fillNumbers(template: string, numbers: readonly string[]): string {
  let index = 0;
  return template.replace(/%d/g, () => (index < numbers.length ? numbers[index++] : '%d'));
}

export function pickLanguage(language: LanguageCode, text: LocalizedText) {
  // 🔴 S15P21E201-1725 — text.ko 는 타입상 string 이지만, 부르는 쪽이 API 값을 그대로 흘려보내면
  //    실제로는 null/undefined 가 올 수 있다(예: 주소가 없는 장소의 tx(place.address, …)). ko·en
  //    갈래는 마지막 줄(text[...] ?? text.ko)이라 null 이 그대로 통과되지만, 이 ja/zh 갈래만
  //    getTranslation·numericShape 로 text.ko 의 글자를 만지므로 null 이면 그 자리에서 죽는다 —
  //    실기(웹, 2026-09-26, 장소 상세)로 한국어·영어는 멀쩡한데 이 세 언어에서만 화면이 하얗게
  //    죽는 것으로 드러났다. typeof 가드로 다섯 언어가 같은 값(빈 텍스트)으로 수렴하게 한다.
  if ((language === 'ja' || language === 'zh-Hans' || language === 'zh-Hant') && typeof text.ko === 'string') {
    const field = language === 'ja' ? 'ja' : language === 'zh-Hans' ? 'zhHans' : 'zhHant';
    const translated = getTranslation(text.ko, field);
    if (translated) return translated;
    // 🔴 원문 그대로 못 찾았을 때만 «모양»으로 한 번 더 찾는다. 글자가 똑같은 줄이
    //    표에 있으면 그것이 언제나 이긴다 — 이 길은 지금까지 영어로 떨어지던 자리만 받는다.
    const shaped = numericShape(text.ko);
    if (shaped) {
      const template = getTranslation(shaped.shape, field);
      if (template) return fillNumbers(template, shaped.numbers);
    }
  }
  return text[resolveTextLanguage(language)] ?? text.ko;
}
