import { Platform } from 'react-native';
import * as Crypto from 'expo-crypto';
import * as WebBrowser from 'expo-web-browser';
import { ApiClientError } from '@/api/client';
import { completeOAuth, createOAuthChallenge, linkOAuthAccount, type OAuthCompleteResult, type OAuthIdentityLinkResult, type OAuthProvider } from './authApi';
import { savePendingOAuth, type PendingOAuthIntent } from './pendingOAuth';

WebBrowser.maybeCompleteAuthSession();

type ProviderConfig = {
  clientId?: string;
  authorizationEndpoint: string;
  scope?: string;
  // 애플만 채운다 — 아래 apple 항목의 주석이 이유를 소유한다.
  responseMode?: string;
  authRedirectPath?: string;
};

const PROVIDERS: Record<OAuthProvider, ProviderConfig> = {
  google: { clientId: process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID, authorizationEndpoint: 'https://accounts.google.com/o/oauth2/v2/auth', scope: 'openid email profile' },
  naver: { clientId: process.env.EXPO_PUBLIC_NAVER_CLIENT_ID, authorizationEndpoint: 'https://nid.naver.com/oauth2.0/authorize', scope: 'name email' },
  kakao: { clientId: process.env.EXPO_PUBLIC_KAKAO_CLIENT_ID, authorizationEndpoint: 'https://kauth.kakao.com/oauth/authorize', scope: 'profile_nickname account_email' },
  // — 애플에만 이메일을 요청하고, 그래서 애플에만 돌아오는 자리가 둘로 갈린다.
  apple: {
    clientId: process.env.EXPO_PUBLIC_APPLE_CLIENT_ID,
    authorizationEndpoint: 'https://appleid.apple.com/auth/authorize',
    scope: 'email',
    responseMode: 'form_post',
    authRedirectPath: '/api/v1/auth/oauth/apple/form-post',
  },
};

// redirect URI 는 provider 개발자센터에 등록한 값과 백엔드 GABOLLE_OAUTH_ALLOWED_REDIRECT_URIS 와
// 문자열이 완전히 같아야 한다. 세 값 중 하나만 달라도 challenge 발급이 400 으로 거부된다.
const CALLBACK_BASE_URL = process.env.EXPO_PUBLIC_OAUTH_CALLBACK_BASE_URL ?? 'https://j15e201.p.ssafy.io';

function base64Url(value: string) { return value.replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_'); }
function verifier() { return Array.from(Crypto.getRandomBytes(48), (byte) => byte.toString(16).padStart(2, '0')).join(''); }

// 로그인(loginWithOAuth)과 이미 로그인한 계정에 소셜을 붙이는 연결(linkOAuthProvider
// 둘 다 여기까지는 완전히 같다 — 제공자에게 던질 인증 URL을 만드는
// PKCE 절차. 둘이 갈리는 것은 그 뒤 코드를 받아 어느 서버 경로로 보내는지뿐이다.
async function beginOAuthChallenge(provider: OAuthProvider) {
  const config = PROVIDERS[provider];
  if (!config.clientId) throw new ApiClientError(`${provider.toUpperCase()} 로그인 설정이 필요해요.`, 'OAUTH_NOT_CONFIGURED', 0);
  // 화면이 실제로 착지하는 자리. 넷 다 같고 애플도 여기로 온다 — 애플의 POST 를 받은 서버가
  // code·state 를 붙여 이 주소로 302 로 넘기기 때문이다.
  const landingUri = `${CALLBACK_BASE_URL}/oauth/${provider}/callback`;
  // 제공자에게 넘기는 주소. 애플만 서버의 POST 수신 경로로 갈린다.
  // 코드 교환에도 이 값을 그대로 써야 한다 — 제공자는 인증 때 받은 redirect_uri 와
  // 교환 때 받은 값이 글자까지 같기를 요구하고, 다르면 코드 교환이 거절된다.
  const redirectUri = config.authRedirectPath ? `${CALLBACK_BASE_URL}${config.authRedirectPath}` : landingUri;
  const codeVerifier = verifier();
  const digest = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, codeVerifier, { encoding: Crypto.CryptoEncoding.BASE64 });
  const challenge = await createOAuthChallenge(provider, redirectUri, base64Url(digest));
  // scope 가 없는 제공자는 그 칸을 아예 빼고 보낸다. 빈 문자열로 보내면 요청한 것으로
  // 읽혀서 거절을 받는다.
  const params = new URLSearchParams({
    client_id: config.clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    state: challenge.state,
    nonce: challenge.nonce,
    code_challenge: base64Url(digest),
    code_challenge_method: 'S256',
  });
  if (config.scope) params.set('scope', config.scope);
  if (config.responseMode) params.set('response_mode', config.responseMode);
  const authorizationUrl = `${config.authorizationEndpoint}?${params.toString()}`;
  return { redirectUri, landingUri, codeVerifier, challenge, authorizationUrl };
}

/**
 * 인증 창이 코드 없이 끝났을 때 무엇이라고 말하나 — S15P21E201-1480.
 *
 * 🔴 「로그인이 취소되었어요」라고 단정하던 것을 고친다. 결과만으로는 «사용자가 취소했다»와
 *    «못 돌아와서 닫았다»를 가를 수 없다 — expo-web-browser 57 실제 코드 기준:
 *
 *      iOS      ASWebAuthenticationSession 이 콜백 없이 끝나면 **무조건 `cancel`**
 *               (WebAuthSession.swift: callbackUrl != nil ? "success" : "cancel").
 *               사용자가 취소를 누른 것도, 제공자·착지 페이지가 안 열려 할 수 없이 닫은 것도,
 *               설치 직후라 Universal Link 연결을 아직 못 받아 와 돌아올 수 없는 것도 전부 이것이다
 *      Android  사용자가 브라우저를 닫으면 `cancel`
 *      둘 다    `dismiss` 는 **우리 코드가 창을 직접 닫을 때만** 온다(dismissAuthSession)
 *
 *    그래서 「dismiss 만 연결 문제로」 가르는 것도 답이 아니다 — 제보된 경우는 `cancel` 로 온다.
 *    둘 다에 참인 말만 한다. 사용자 탓으로 단정하면 사용자는 자기가 뭘 잘못 눌렀다고 읽고
 *    (2026-09-22 실제로 그 제보로 없는 원인을 한 시간 뒤졌다), 연결 탓으로 단정하면 일부러
 *    닫은 사람에게 틀린 말을 한다.
 */
export const AUTH_SESSION_NOT_FINISHED = '로그인을 마치지 못했어요. 창을 닫았거나 연결이 끊겼을 수 있으니 다시 시도해 주세요.';

/** `success` 가 아닌 결과를 사람이 읽는 오류로. 코드 `OAUTH_CANCELLED` 는 이름만 옛것이다 — 다른 곳에서 안 읽는다. */
export function authSessionFailure(type: string): ApiClientError {
  if (type === 'cancel' || type === 'dismiss') return new ApiClientError(AUTH_SESSION_NOT_FINISHED, 'OAUTH_CANCELLED', 0);
  return new ApiClientError('소셜 로그인을 완료하지 못했어요.', 'OAUTH_FAILED', 0);
}

// 네이티브(앱)는 팝업 차단이 끼어들 자리가 없는 앱 안 브라우저 화면을 쓰므로
// 이전 방식 그대로 코드를 바로 받아 온다.
// 두 번째 인자는 제공자에게 넘긴 redirect_uri 가 아니라 앱 안 브라우저가 착지하기를 기다리는
// 주소다. 애플만 둘이 다르다 — 애플은 서버의 POST 수신 경로로 보내고 그 서버가 여기로 넘긴다.
async function runNativeAuthSession(authorizationUrl: string, landingUri: string, expectedState: string) {
  const result = await WebBrowser.openAuthSessionAsync(authorizationUrl, landingUri, { preferUniversalLinks: true });
  if (result.type !== 'success') throw authSessionFailure(result.type);
  const callback = new URL(result.url);
  const callbackError = callback.searchParams.get('error');
  const authorizationCode = callback.searchParams.get('code');
  const returnedState = callback.searchParams.get('state');
  if (callbackError) throw new ApiClientError('소셜 로그인 요청이 거절되었어요.', callbackError, 0);
  if (!authorizationCode || returnedState !== expectedState) throw new ApiClientError('로그인 응답을 확인할 수 없어요.', 'INVALID_OAUTH_RESPONSE', 0);
  return authorizationCode;
}

// 웹에서 팝업 대신 현재 페이지를 그대로 제공자 인증 화면으로 넘긴다.
// code_verifier·state·nonce는 지역 변수라 페이지가 넘어가면 사라지므로, 넘어가기 전에
// pendingOAuth.ts에 잠깐 저장해 두고 착지 화면(oauth/[provider]/callback.tsx)이 돌아와서
// intent(login/link)에 맞는 서버 경로로 완료를 잇는다.
async function beginWebRedirect(
  intent: PendingOAuthIntent,
  provider: OAuthProvider,
  returnTo: string | null | undefined,
  built: Awaited<ReturnType<typeof beginOAuthChallenge>>,
) {
  const { redirectUri, codeVerifier, challenge, authorizationUrl } = built;
  await savePendingOAuth({ intent, provider, redirectUri, codeVerifier, state: challenge.state, nonce: challenge.nonce, returnTo });
  window.location.assign(authorizationUrl);
}

// — 모바일 웹 브라우저는 window.open(WebBrowser.openAuthSessionAsync가
// 웹에서 쓰는 방식)로 여는 팝업을 넷 다(구글·카카오·네이버·애플) 팝업 차단으로 막는다.
// 눌러도 아무 일도 안 일어난 것처럼 보인다. 그래서 웹에서는 이 함수가 결과를 반환하지
// 않는다(페이지 자체가 다시 로드되므로 이 호출의 나머지는 실행되지 않는다) — 위
// beginWebRedirect 참고.
export async function loginWithOAuth(provider: OAuthProvider, returnTo?: string | null): Promise<OAuthCompleteResult> {
  const built = await beginOAuthChallenge(provider);
  const { redirectUri, codeVerifier, challenge } = built;
  if (Platform.OS === 'web') {
    await beginWebRedirect('login', provider, returnTo, built);
    return new Promise<OAuthCompleteResult>(() => {}); // 페이지가 곧 떠난다 — 이 약속은 안 풀린다.
  }
  const authorizationCode = await runNativeAuthSession(built.authorizationUrl, built.landingUri, challenge.state);
  return completeOAuth(provider, { authorizationCode, redirectUri, codeVerifier, state: challenge.state, nonce: challenge.nonce });
}

// — 이미 로그인한 계정(설정 화면)에 소셜 신원을 붙인다. 흐름은
// loginWithOAuth와 같은 PKCE 왕복이고, 마지막에 completeOAuth 대신 linkOAuthAccount를
// 부르는 것만 다르다. accessToken은 호출 시점(설정 화면)의 것을 그대로 쓴다 — 웹에서는
// 페이지가 넘어갔다 돌아오지만, AuthProvider가 부팅 때마다 세션 쿠키로 다시 복원하므로
// 착지 화면에서 useAuth로 새로 받으면 된다(oauth/[provider]/callback.tsx).
export async function linkOAuthProvider(provider: OAuthProvider, accessToken: string, returnTo?: string | null): Promise<OAuthIdentityLinkResult> {
  const built = await beginOAuthChallenge(provider);
  const { redirectUri, codeVerifier, challenge } = built;
  if (Platform.OS === 'web') {
    await beginWebRedirect('link', provider, returnTo, built);
    return new Promise<OAuthIdentityLinkResult>(() => {});
  }
  const authorizationCode = await runNativeAuthSession(built.authorizationUrl, built.landingUri, challenge.state);
  return linkOAuthAccount(provider, { authorizationCode, redirectUri, codeVerifier, state: challenge.state, nonce: challenge.nonce }, accessToken);
}
