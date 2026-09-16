// 이 앱이 아는 언어 다섯 — S15P21E201-1109.
//
// 🔴 왜 다섯인가. 부산에 오는 사람이 쓰는 말이다. 한국관광공사 TourAPI 도 국문·영문·일문·
//    중문 간체·중문 번체로 같은 다섯을 낸다(EngService2 · JpnService2 · ChsService2 ·
//    ChtService2). 화면 문구와 장소 정보가 같은 언어 축 위에 있어야 섞이지 않는다.
//
// 🔴 간체와 번체를 하나로 묶지 않는다. 대만·홍콩에서 온 사람에게 간체를 보여주는 것은
//    "중국어니까 읽겠지" 라고 미는 것이다. TourAPI 도 둘을 따로 낸다.
//
// 🔴 **지금 화면 문구는 한국어와 영어만 번역돼 있다.** 앱 안의 tx(ko, en) 호출이 2039곳이라
//    한 번에 다섯 벌이 될 수 없다. 그래서 일본어·중국어를 고르면 **화면 문구는 영어로**
//    나온다 — 이것은 임시가 아니라 **의도된 단계**다. 이유가 둘이다.
//
//    ① 외국인이 이 앱에서 실제로 읽는 글의 대부분은 UI 문구가 아니라 **장소 이름과 소개**다.
//       그건 TourAPI 가 언어별로 준다(bigData/collect/tourapi-en.mjs 와 같은 방식).
//       거기가 먼저 붙으면 체감은 그 언어로 바뀐다.
//    ② 고를 수 없으면 아무도 안 쓴다. 고르게 해 두면 어느 언어가 실제로 쓰이는지 알 수 있고,
//       그 순서대로 번역한다.
//
//    **비어 있는 것을 비어 있다고 말한다** — 고르는 화면에서 그 사실을 알린다. 다 된 척하지
//    않는다.

export const LANGUAGE_CODES = ['ko', 'en', 'ja', 'zh-Hans', 'zh-Hant'] as const;

export type LanguageCode = (typeof LANGUAGE_CODES)[number];

/**
 * 화면 문구를 실제로 가진 언어.
 *
 * 나머지는 여기 있는 것 중 하나로 떨어진다 — {@link resolveTextLanguage}.
 */
export type TranslatedLanguage = 'ko' | 'en';

export type LanguageOption = {
  code: LanguageCode;
  /** 그 언어를 쓰는 사람이 읽을 수 있는 이름. 🔴 한국어로 "일본어" 라고 쓰지 않는다. */
  endonym: string;
  /**
   * 그 언어를 모르는 사람이 읽을 수 있는 이름.
   *
   * 🔴 국기 그림문자를 안 쓴다. 안드로이드·iOS 에서는 국기로 보이지만 **윈도우 브라우저에서는
   * 나라 글자 두 개(KR·US·JP)로 보인다** — 실측으로 확인했다. 무슨 버튼인지 안 읽힌다.
   * 그리고 국기는 나라이지 말이 아니다: 영어를 고르는 사람이 미국인일 이유가 없고,
   * 🇨🇳 과 🇹🇼 은 작은 화면에서 서로 구별되지도 않는다.
   */
  englishName: string;
  /** 화면 문구가 이 언어로 번역돼 있나. 거짓이면 영어로 나온다. */
  uiTranslated: boolean;
};

export const LANGUAGE_OPTIONS: readonly LanguageOption[] = [
  { code: 'ko', endonym: '한국어', englishName: 'Korean', uiTranslated: true },
  { code: 'en', endonym: 'English', englishName: 'English', uiTranslated: true },
  { code: 'ja', endonym: '日本語', englishName: 'Japanese', uiTranslated: false },
  { code: 'zh-Hans', endonym: '简体中文', englishName: 'Chinese (Simplified)', uiTranslated: false },
  { code: 'zh-Hant', endonym: '繁體中文', englishName: 'Chinese (Traditional)', uiTranslated: false },
];

/**
 * 화면 문구를 어느 언어로 그릴 것인가.
 *
 * 🔴 한국어가 아닌 모든 언어는 영어로 떨어진다. 일본어 사용자에게 **한국어**를 보여주는 것이
 * 최악이기 때문이다 — 읽을 수 없는 글자이고, 이 앱을 쓰러 온 이유 자체를 부정한다.
 * 영어는 적어도 읽어 볼 수 있다.
 */
export function resolveTextLanguage(language: LanguageCode): TranslatedLanguage {
  return language === 'ko' ? 'ko' : 'en';
}

/** 그 언어의 화면 문구가 아직 번역되지 않았나 — 고르는 화면이 이 사실을 알린다. */
export function needsTranslationNotice(language: LanguageCode): boolean {
  return LANGUAGE_OPTIONS.find((option) => option.code === language)?.uiTranslated === false;
}

/**
 * 기기·서버에 넘길 표기.
 *
 * 🔴 `Accept-Language` 와 음성(TTS)은 BCP 47 을 쓴다. 우리 코드값이 이미 그 모양이지만
 * 한국어만 지역까지 붙여야 자연스러운 음성이 나온다.
 */
export function toBcp47(language: LanguageCode): string {
  if (language === 'ko') return 'ko-KR';
  if (language === 'en') return 'en-US';
  if (language === 'ja') return 'ja-JP';
  return language === 'zh-Hans' ? 'zh-CN' : 'zh-TW';
}

/**
 * 저장돼 있던 값·주소 칸의 값을 코드로 되돌린다. 모르는 값이면 한국어다.
 *
 * 🔴 옛 값을 버리지 않는다. 예전에는 두 개뿐이었고 그때 저장된 값이 그대로 들어온다.
 */
export function parseLanguageCode(value: unknown): LanguageCode {
  if (typeof value !== 'string') return 'ko';
  const found = LANGUAGE_CODES.find((code) => code === value);
  if (found) return found;
  // 흔한 다른 표기를 받아 준다 — 기기 설정에서 오는 값이 이 모양일 수 있다.
  const lower = value.toLowerCase();
  if (lower.startsWith('ko')) return 'ko';
  if (lower.startsWith('ja')) return 'ja';
  if (lower.startsWith('en')) return 'en';
  if (lower.startsWith('zh')) {
    // zh-TW · zh-HK · zh-Hant-* 는 번체, 그 밖의 zh 는 간체로 본다.
    return /hant|tw|hk|mo/.test(lower) ? 'zh-Hant' : 'zh-Hans';
  }
  return 'ko';
}
