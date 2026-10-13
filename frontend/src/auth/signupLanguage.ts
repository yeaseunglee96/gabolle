// 가입 요청이 서버로 보내는 언어 표기 — S15P21E201-2053.
//
// 앱 안의 언어 코드(ko · zh-Hans …, src/i18n/languages.ts)와 서버가 가입 요청에서 받는 표기(KO · ZH_HANS …)는
// 모양이 다르다. 서버 쪽 정의는 backend 의 LanguageNormalizer.SIGNUP_LANGUAGE_REGEX 한 줄이다
// (정규식 — 문자열의 모양을 적는 짧은 규칙).
//
// 서버와 오가는 표기가 둘이고 중국어만 구분자가 다르다. 헷갈리지 않게 적어 둔다.
//   · 가입 요청에 싣는 값  KO · EN · JA · VI · ZH_HANS · ZH_HANT   ← 이 파일이 만든다 (SignupLanguage). 밑줄이다
//   · 서버가 저장해 계정 응답으로 내려 주는 값  KO · EN · JA · VI · ZH-HANS · ZH-HANT   (AccountLanguage, authApi.ts). 하이픈이다
// 서버는 가입 요청에서 중국어를 밑줄·하이픈 둘 다 받고 어느 쪽이든 ZH-HANS · ZH-HANT 로 저장한다. 가입 요청이 밑줄을 쓰는 것은
// 서버 안의 알림 문구 enum(PushCopy.Lang)이 ZH_HANS · ZH_HANT 를 쓰기 때문이다 — 같은 표기로 맞춰 둔다.
import { ApiClientError } from '@/api/client';
import type { LanguageCode } from '@/i18n/languages';

export type SignupLanguage = 'KO' | 'EN' | 'JA' | 'ZH_HANS' | 'ZH_HANT' | 'VI';

const SIGNUP_LANGUAGE: Record<LanguageCode, SignupLanguage> = {
  ko: 'KO',
  en: 'EN',
  ja: 'JA',
  'zh-Hans': 'ZH_HANS',
  'zh-Hant': 'ZH_HANT',
  vi: 'VI',
};

/** 앱 언어 → 가입 요청에 실을 값. 모르는 코드는 영어다 — 서버가 어느 판이든 받는 값이다. */
export function toSignupLanguage(code: LanguageCode): SignupLanguage {
  return SIGNUP_LANGUAGE[code] ?? 'EN';
}

/**
 * 서버가 가입 요청을 «language 칸 때문에» 돌려보냈나.
 *
 * 서버의 검증 오류 응답은 `{ code: 'INVALID_REQUEST', fields: ['language: …'] }` 모양이다 — 어느 칸이 문제인지가
 * `fields` 에 «칸이름: 이유» 로 남는다. 이메일이나 비밀번호가 틀려서 400 이 난 것과 가르려고 칸 이름까지 본다.
 */
export function isLanguageRejected(cause: unknown): boolean {
  return cause instanceof ApiClientError
    && cause.status === 400
    && cause.code === 'INVALID_REQUEST'
    && cause.fields.some((field) => field.split(':')[0].trim() === 'language');
}

/**
 * 🔴 앱이 서버보다 먼저 나갔을 때의 방어.
 *
 * 옛 서버는 가입 요청의 language 를 KO · EN 만 받는다. 새 앱이 일본어·중국어·베트남어를 보내면 400 이 나고,
 * 그러면 그 언어를 고른 사람은 가입이 «영영» 안 된다 — 이유를 알 수도 없다. 그래서 language 칸 때문에 돌려보내졌을 때만
 * 영어로 **한 번** 다시 보낸다. 화면 언어는 그대로(고른 언어)이고 계정 언어만 영어가 되며, 마이페이지 › 앱 언어에서 바꿀 수 있다.
 *
 * 한국어·영어는 옛 서버도 받으므로 다시 보내지 않는다. 다른 이유의 400(이메일 형식 등)이나 다른 오류도 그대로 던진다 —
 * 가려서 영어로 다시 보내면 진짜 오류를 영어 요청의 오류로 바꿔치기하게 된다.
 * 다시 보내도 안전하다: 검증 오류는 서버가 아무것도 만들기 전에 나므로 계정이 반쯤 생기는 일도, 소셜 가입 티켓이 소비되는 일도 없다.
 *
 * 서버가 먼저 나간 뒤에는 이 방어는 한 번도 쓰이지 않는다. 그 뒤에 걷어도 된다.
 */
export async function sendWithLanguageFallback<T>(
  language: SignupLanguage,
  send: (language: SignupLanguage) => Promise<T>,
): Promise<T> {
  try {
    return await send(language);
  } catch (cause) {
    if (language === 'KO' || language === 'EN' || !isLanguageRejected(cause)) throw cause;
    return send('EN');
  }
}
