// 이 앱이 아는 언어 다섯 — S15P21E201-1109.

export const LANGUAGE_CODES = ['ko', 'en', 'ja', 'zh-Hans', 'zh-Hant'] as const;

export type LanguageCode = (typeof LANGUAGE_CODES)[number];

/** 화면 문구를 실제로 가진 언어. */
export type TranslatedLanguage = 'ko' | 'en';

export type LanguageOption = {
  code: LanguageCode;
  /** 그 언어를 쓰는 사람이 읽을 수 있는 이름. 한국어로 "일본어" 라고 쓰지 않는다. */
  endonym: string;
  /** 그 언어를 모르는 사람이 읽을 수 있는 이름. */
  englishName: string;
  /**
   * 화면 문구가 이 언어로 번역돼 있나.
   * 🔴 2026-09-21 — 다섯 언어 전부 참이 됐다. 번역표(translations.ts)가 tx 원문 전부를 덮고(S15P21E201-1356·1359·1361)
   *    일본어·번체로 22 화면을 돌며 잰 결과 화면 문구에 영어가 0줄이었다. 남은 영어는 서버 데이터뿐이다(S15P21E201-1363).
   *    그래서 「메뉴는 아직 영어로 나와요」 안내를 걷어냈다 — 사실이 아닌 안내는 없는 것보다 나쁘다.
   */
  uiTranslated: boolean;
};

// 국기 이미지 자산(assets/flags/*.png)은 여기 두지 않고 app/index.tsx 의 FLAG_IMAGES 에서
// code 로 바로 매핑한다 — require 는 번들러가 정적으로 읽어야 해서 이 배열처럼 값이 동적으로
// 도는 곳에 넣으면 번들에 안 잡힌다.
export const LANGUAGE_OPTIONS: readonly LanguageOption[] = [
  { code: 'ko', endonym: '한국어', englishName: 'Korean', uiTranslated: true },
  { code: 'en', endonym: 'English', englishName: 'English', uiTranslated: true },
  { code: 'ja', endonym: '日本語', englishName: 'Japanese', uiTranslated: true },
  { code: 'zh-Hans', endonym: '简体中文', englishName: 'Chinese (Simplified)', uiTranslated: true },
  { code: 'zh-Hant', endonym: '繁體中文', englishName: 'Chinese (Traditional)', uiTranslated: true },
];

/** 화면 문구를 어느 언어로 그릴 것인가. */
export function resolveTextLanguage(language: LanguageCode): TranslatedLanguage {
  return language === 'ko' ? 'ko' : 'en';
}


/** 기기·서버에 넘길 표기. */
export function toBcp47(language: LanguageCode): string {
  if (language === 'ko') return 'ko-KR';
  if (language === 'en') return 'en-US';
  if (language === 'ja') return 'ja-JP';
  return language === 'zh-Hans' ? 'zh-CN' : 'zh-TW';
}

/**
 * 웹의 `<html lang>` 에 넣을 표기. `toBcp47` 과 값이 다르다 — 음성(TTS)은 한국어도
 * 지역을 붙여야 자연스럽지만, `lang` 속성은 중국어만 붙인다. 간체·번체는 같은 언어
 * 코드(zh)에 문자만 다르므로 지역(CN·TW)이 없으면 브라우저가 폰트·문자 방향을 하나로
 * 뭉뚱그려 고른다 — 나머지 언어는 문자 체계가 언어마다 하나뿐이라 그럴 일이 없다.
 */
export function toHtmlLang(language: LanguageCode): string {
  if (language === 'zh-Hans') return 'zh-CN';
  if (language === 'zh-Hant') return 'zh-TW';
  return language;
}

/** 저장돼 있던 값·주소 칸의 값을 코드로 되돌린다. 모르는 값이면 한국어다. */
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
