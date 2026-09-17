// 문장 하나를 번역한다 — 현장 말하기가 쓴다 (S15P21E201-1088).
//
// 🔴 이 화면이 누구를 위한 것인지가 이 파일의 전부다. **현장 말하기는 한국어를 못 하는
//    사람을 위한 도구다.** 그런데 화면이 "한국어로 입력하면 그대로 읽어드려요" 라고 적혀
//    있었다 — 영어 화면에서도 그랬다. 한국어를 모르는 사람에게 한국어를 입력하라고 한 것이라
//    도구가 목적과 정반대로 서 있었다(사용자 지적, 2026-09-16).
//
//    맞는 방향은 **내 말로 쓰고, 한국어로 들려주는 것**이다.
//
// 🔴 서버가 대신 부른다. 업체 열쇠를 화면에 두면 그대로 노출되기 때문이다
//    (TranslateController javadoc). 우리는 그 경로만 부른다:
//      POST /api/v1/tools/translate   { sourceText, direction }
//                                     -> { translatedText, cached, provider }
//
// 🔴 이 경로는 없을 수 있다. 컨트롤러가 지금 back/dev 에만 있고 main·back/main 에는 없으며,
//    운영에는 번역 업체 열쇠가 아직 안 들어가 있다(jaehyeon 님 실측, 2026-09-16 — 메뉴판
//    읽기가 같은 열쇠를 쓴다). 그래서 **안 될 때 무엇을 할지가 기능의 절반**이다.
//    안 되면 조용히 실패하지 않고, 왜 안 되는지 말하고 원문이라도 읽을 수 있게 한다.
// 🔴 언어 다섯을 다 받는다 (S15P21E201-1109). 이 함수가 'ko' | 'en' 만 받으면 부르는 쪽
// 열다섯 곳이 각자 떨어뜨려야 하고, 한 곳만 빠뜨리면 일본어 사용자가 한국어 이름을 본다.
// 떨어뜨리는 일은 **여기 한 자리**에서 한다.
import { resolveTextLanguage, type LanguageCode } from '@/i18n/languages';
import { apiRequest, ApiClientError } from '@/api/client';
import { isVendorNotReady } from '@/api/vendorReady';

export type TranslationDirection = 'EN_TO_KO' | 'KO_TO_EN';

/** 번역이 안 되는 이유. 화면이 이유마다 다르게 말해야 해서 뭉뚱그리지 않는다. */
export type TranslationBlockedReason =
  /** 로그인해야 부를 수 있는 경로다 — 우리 업체 열쇠로 남이 번역을 돌리는 것을 막으려는 것이다. */
  | 'signed-out'
  /** 서버에 그 경로가 아직 없다(404·501). 기다리면 생긴다. */
  | 'not-built'
  /** 바깥 업체 열쇠가 서버에 안 꽂혔다. **다시 시도해도 매한가지다** (S15P21E201-1200). */
  | 'not-ready'
  /** 번역 업체 쪽이 실패했다(5xx). 잠시 뒤 될 수 있다. */
  | 'vendor'
  /** 그 밖 — 끊김 등. */
  | 'error';

export type TranslationOutcome =
  | { state: 'translated'; text: string; provider: string; cached: boolean }
  | { state: 'blocked'; reason: TranslationBlockedReason };

type TranslateResponseDto = { translatedText: string; cached: boolean; provider: string };

/**
 * 이 화면이 받는 최대 길이.
 *
 * 🔴 **서버 한도와 같은 값이 아니다.** 서버는 2000자까지 받는다
 * (`TranslationRequest.MAX_SOURCE_LENGTH`). 120 은 이 화면이 스스로 정한 값이고, 원래
 * 있던 입력칸 상한을 그대로 이어받은 것이다.
 *
 * 짧게 두는 이유는 **소리로 나가기 때문**이다. 현장에서 상대에게 들려주는 한마디인데
 * 길면 듣는 쪽이 못 따라오고, 화면에 띄워 내밀기에도 길다. 서버가 더 받는다고 해서
 * 이 칸을 늘릴 이유가 되지는 않는다.
 *
 * (앞선 주석에 "서버가 받는 최대 길이와 맞춘다" 고 적혀 있었는데 사실이 아니었다.
 *  실제로 서버 코드를 열어 보고 2026-09-16 에 고쳤다.)
 */
export const TRANSLATE_MAX_LENGTH = 120;

/**
 * 앱 언어에 맞는 번역 방향.
 *
 * 🔴 영어 화면이면 **영어로 쓰고 한국어로 말한다**(EN_TO_KO). 이것이 이 티켓이 바로잡는
 * 방향이다. 한국어 화면이면 번역할 것이 없다 — 한국어로 써서 한국어로 말하면 되므로
 * {@code null} 을 돌려주고, 화면은 번역을 아예 부르지 않는다.
 */
export function directionForLanguage(language: LanguageCode): TranslationDirection | null {
  // 일본어·중국어를 고른 사람도 번역이 필요하다. 서버가 받는 방향은 아직 EN_TO_KO 뿐이라
  // 그분들은 영어로 쓴다 — 화면 문구도 영어로 나오므로 어긋나지 않는다.
  return resolveTextLanguage(language) === 'en' ? 'EN_TO_KO' : null;
}

/** 번역된 문장을 읽을 때 쓸 음성. 🔴 영어 문장을 한국어 음성으로 읽으면 알아들을 수 없다. */
export function speechLanguageFor(direction: TranslationDirection): string {
  return direction === 'EN_TO_KO' ? 'ko-KR' : 'en-US';
}

function blockedReason(error: unknown): TranslationBlockedReason {
  // 🔴 열쇠가 안 꽂힌 것도 5xx 로 온다 — 숫자만 보면 몸 가른다 (S15P21E201-1200).
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
  // 로그인 없이 부르면 서버가 401 을 준다. 갔다 와서 알기보다 여기서 바로 말해 준다 —
  // 기다렸다가 "안 됐어요" 를 듣는 것이 제일 나쁘다.
  if (!accessToken) return { state: 'blocked', reason: 'signed-out' };
  try {
    const dto = await apiRequest<TranslateResponseDto>('/api/v1/tools/translate', {
      method: 'POST',
      accessToken,
      signal,
      body: { sourceText: text.slice(0, TRANSLATE_MAX_LENGTH), direction },
    });
    const translated = dto?.translatedText?.trim();
    // 빈 번역을 성공이라고 하지 않는다 — 화면이 빈 칸을 읽어 주게 된다.
    if (!translated) return { state: 'blocked', reason: 'vendor' };
    return { state: 'translated', text: translated, provider: dto.provider, cached: dto.cached };
  } catch (error) {
    return { state: 'blocked', reason: blockedReason(error) };
  }
}
