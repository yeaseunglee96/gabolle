// 문장 하나를 번역한다 — 현장 말하기가 쓴다.
import { resolveTextLanguage, type LanguageCode } from '@/i18n/languages';
import { apiRequest, ApiClientError } from '@/api/client';
import { isVendorNotReady } from '@/api/vendorReady';

// 🔴 서버(back/dev 의 TranslationDirection)는 아직 EN_TO_KO · KO_TO_EN 둘뿐이다. 일본어·중국어 방향은
//    백엔드에 요청해 둔 것(S15P21E201-1363)이고, 서버가 모르는 방향을 받으면 400 을 준다 — 그때는
//    영어 방향으로 한 번 더 부른다(아래 translateText). 업체가 LLM 이라 일본어 입력도 대개 번역된다.
export type TranslationDirection = 'EN_TO_KO' | 'KO_TO_EN' | 'JA_TO_KO' | 'ZH_HANS_TO_KO' | 'ZH_HANT_TO_KO';

/** 번역이 안 되는 이유. 화면이 이유마다 다르게 말해야 해서 뭉뚱그리지 않는다. */
export type TranslationBlockedReason =
  /** 로그인해야 부를 수 있는 경로다 — 우리 업체 열쇠로 남이 번역을 돌리는 것을 막으려는 것이다. */
  | 'signed-out'
  /** 서버에 그 경로가 아직 없다(404·501). 기다리면 생긴다. */
  | 'not-built'
  /** 바깥 업체 열쇠가 서버에 안 꽂혔다. 다시 시도해도 매한가지다. */
  | 'not-ready'
  /** 번역 업체 쪽이 실패했다(5xx). 잠시 뒤 될 수 있다. */
  | 'vendor'
  /** 그 밖 — 끊김 등. */
  | 'error';

export type TranslationOutcome =
  /** viaEnglish — 고른 언어 방향을 서버가 몰라 영어 방향으로 번역했다. 화면이 한 줄 알린다. */
  | { state: 'translated'; text: string; provider: string; cached: boolean; viaEnglish?: boolean }
  | { state: 'blocked'; reason: TranslationBlockedReason };

type TranslateResponseDto = { translatedText: string; cached: boolean; provider: string };

/** 이 화면이 받는 최대 길이. */
export const TRANSLATE_MAX_LENGTH = 120;

/** 앱 언어에 맞는 번역 방향. */
export function directionForLanguage(language: LanguageCode): TranslationDirection | null {
  // 고른 언어 그대로 쓰게 한다 — 일본어 화면에서 「영어로 적어라」는 말이 안 된다. 서버가 그 방향을
  // 아직 모르면 translateText 가 영어 방향으로 물러선다.
  switch (language) {
    case 'ko': return null;
    case 'ja': return 'JA_TO_KO';
    case 'zh-Hans': return 'ZH_HANS_TO_KO';
    case 'zh-Hant': return 'ZH_HANT_TO_KO';
    default: return resolveTextLanguage(language) === 'en' ? 'EN_TO_KO' : null;
  }
}

/** 번역된 문장을 읽을 때 쓸 음성. 영어 문장을 한국어 음성으로 읽으면 알아들을 수 없다. */
export function speechLanguageFor(direction: TranslationDirection): string {
  return direction === 'KO_TO_EN' ? 'en-US' : 'ko-KR';
}

function blockedReason(error: unknown): TranslationBlockedReason {
  // 열쇠가 안 꽂힌 것도 5xx 로 온다 — 숫자만 보면 몸 가른다.
  if (isVendorNotReady(error)) return 'not-ready';
  if (!(error instanceof ApiClientError)) return 'error';
  if (error.status === 401 || error.status === 403) return 'signed-out';
  if (error.status === 404 || error.status === 501) return 'not-built';
  if (error.status >= 500) return 'vendor';
  return 'error';
}

export async function translateText(
  sourceText: string,
  direction: TranslationDirection,
  accessToken: string | null,
  signal?: AbortSignal,
): Promise<TranslationOutcome> {
  const text = sourceText.trim();
  if (!text) return { state: 'blocked', reason: 'error' };
  // 로그인 없이 부르면 서버가 401 을 준다. 갔다 와서 알기보다 여기서 바로 말해 준다
  // 기다렸다가 "안 됐어요" 를 듣는 것이 제일 나쁘다.
  if (!accessToken) return { state: 'blocked', reason: 'signed-out' };
  const call = async (dir: TranslationDirection) => {
    const dto = await apiRequest<TranslateResponseDto>('/api/v1/tools/translate', {
      method: 'POST',
      accessToken,
      signal,
      body: { sourceText: text.slice(0, TRANSLATE_MAX_LENGTH), direction: dir },
    });
    return dto;
  };
  try {
    let dto: TranslateResponseDto; let viaEnglish = false;
    try {
      dto = await call(direction);
    } catch (error) {
      // 서버가 모르는 방향(400) — 영어 방향으로 한 번 더. 다른 실패는 그대로 올린다.
      const unknownDirection = error instanceof ApiClientError && error.status === 400 && direction !== 'EN_TO_KO' && direction !== 'KO_TO_EN';
      if (!unknownDirection) throw error;
      dto = await call('EN_TO_KO'); viaEnglish = true;
    }
    const translated = dto?.translatedText?.trim();
    // 빈 번역을 성공이라고 하지 않는다 — 화면이 빈 칸을 읽어 주게 된다.
    if (!translated) return { state: 'blocked', reason: 'vendor' };
    return { state: 'translated', text: translated, provider: dto.provider, cached: dto.cached, viaEnglish };
  } catch (error) {
    return { state: 'blocked', reason: blockedReason(error) };
  }
}
