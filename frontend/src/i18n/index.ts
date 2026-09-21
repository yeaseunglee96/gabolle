import { useCallback, useMemo } from 'react';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { resolveTextLanguage, toBcp47, type LanguageCode } from '@/i18n/languages';
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
  if (language === 'ja' || language === 'zh-Hans' || language === 'zh-Hant') {
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

export function useI18n() {
  const preferences = useOnboardingPreferences();
  const language = preferences.language;
  // tx 는 언어가 바뀔 때만 새로 만든다 — 매 렌더 새 함수를 주면 이걸 의존성 배열에 넣는
  // 곳(useCallback/useEffect)마다 무한 리렌더로 이어진다 에서 실측).
  const tx = useCallback((ko: string, en: string) => pickLanguage(language, { ko, en }), [language]);
  return useMemo(() => ({
    language,
    // 숫자·날짜 표기는 고른 언어 그대로 쓴다. 문구가 영어여도 12,000 을 읽는 방식은
    // 그 나라 방식이 맞다 — 이건 번역이 없어도 바로 맞출 수 있는 것이다.
    locale: toBcp47(language),
    tx,
    setLanguage: preferences.setLanguage,
  }), [language, preferences.setLanguage, tx]);
}
