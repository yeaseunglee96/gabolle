import { Platform } from 'react-native';
import * as Crypto from 'expo-crypto';
import * as WebBrowser from 'expo-web-browser';
import { ApiClientError } from '@/api/client';
import { completeOAuth, createOAuthChallenge, linkOAuthAccount, type OAuthCompleteResult, type OAuthIdentityLinkResult, type OAuthProvider } from './authApi';
import { savePendingOAuth, type PendingOAuthIntent } from './pendingOAuth';

WebBrowser.maybeCompleteAuthSession();

const PROVIDERS: Record<OAuthProvider, { clientId?: string; authorizationEndpoint: string; scope?: string }> = {
  google: { clientId: process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID, authorizationEndpoint: 'https://accounts.google.com/o/oauth2/v2/auth', scope: 'openid email profile' },
  naver: { clientId: process.env.EXPO_PUBLIC_NAVER_CLIENT_ID, authorizationEndpoint: 'https://nid.naver.com/oauth2.0/authorize', scope: 'name email' },
  kakao: { clientId: process.env.EXPO_PUBLIC_KAKAO_CLIENT_ID, authorizationEndpoint: 'https://kauth.kakao.com/oauth/authorize', scope: 'profile_nickname account_email' },
  // 애플에는 scope 를 아예 안 보낸다. 이름이든 이메일이든 하나라도 요청하면 애플이
  //    response_mode=form_post 를 요구하고, 안 보내면 요청 자체를 거절한다 —
  //    "response_mode must be form_post when name or email scope is requested" (2026-09-10 실측).
  //    그런데 이 프런트는 정적 SPA 라 POST 본문을 받을 서버가 없다.
  //
  //    그래서 이메일을 안 받기로 한다. 서버는 애초에 이메일 없는 소셜 가입을 지원한다 —
  //    auth_identity.provider_email 이 NULL 허용이고, 가입 화면에는 emailProvided=false 로
  //    알려 준다(OAuthAccountService 클래스 주석). 사용자는 가입 화면에서 이메일을 직접 적는다.
  //
  //    이메일을 애플에서 받아 오려면 redirect_uri 를 POST 를 받을 수 있는 서버 경로로 옮기고
  //    거기서 다시 화면으로 넘겨야 한다. 애플 개발자 콘솔에도 그 주소를 새로 등록해야 해서
  //    이번 변경 범위 밖이다.
  apple: { clientId: process.env.EXPO_PUBLIC_APPLE_CLIENT_ID, authorizationEndpoint: 'https://appleid.apple.com/auth/authorize' },
};

// redirect URI 는 provider 개발자센터에 등록한 값과 백엔드 GABOLLE_OAUTH_ALLOWED_REDIRECT_URIS 와
// 문자열이 완전히 같아야 한다. 세 값 중 하나만 달라도 challenge 발급이 400 으로 거부된다.
const CALLBACK_BASE_URL = process.env.EXPO_PUBLIC_OAUTH_CALLBACK_BASE_URL ?? 'https://j15e201.p.ssafy.io';

function base64Url(value: string) { return value.replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_'); }
function verifier() { return Array.from(Crypto.getRandomBytes(48), (byte) => byte.toString(16).padStart(2, '0')).join(''); }

// 로그인(loginWithOAuth)과 이미 로그인한 계정에 소셜을 붙이는 연결(linkOAuthProvider,
// S15P21E201-832) 둘 다 여기까지는 완전히 같다 — 제공자에게 던질 인증 URL을 만드는
// PKCE 절차. 둘이 갈리는 것은 그 뒤 코드를 받아 어느 서버 경로로 보내는지뿐이다.
async function beginOAuthChallenge(provider: OAuthProvider) {
  const config = PROVIDERS[provider];
  if (!config.clientId) throw new ApiClientError(`${provider.toUpperCase()} 로그인 설정이 필요해요.`, 'OAUTH_NOT_CONFIGURED', 0);
  const redirectUri = `${CALLBACK_BASE_URL}/oauth/${provider}/callback`;
  const codeVerifier = verifier();
  const digest = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, codeVerifier, { encoding: Crypto.CryptoEncoding.BASE64 });
  const challenge = await createOAuthChallenge(provider, redirectUri, base64Url(digest));
  // scope 가 없는 제공자(애플)는 그 칸을 아예 빼고 보낸다. 빈 문자열로 보내면 요청한 것으로
  // 읽혀서 같은 거절을 받는다.
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
  const authorizationUrl = `${config.authorizationEndpoint}?${params.toString()}`;
  return { redirectUri, codeVerifier, challenge, authorizationUrl };
}

// 네이티브(앱)는 팝업 차단이 끼어들 자리가 없는 앱 안 브라우저 화면을 쓰므로
// S15P21E201-830 이전 방식 그대로 코드를 바로 받아 온다.
async function runNativeAuthSession(authorizationUrl: string, redirectUri: string, expectedState: string) {
  const result = await WebBrowser.openAuthSessionAsync(authorizationUrl, redirectUri);
  if (result.type === 'cancel' || result.type === 'dismiss') throw new ApiClientError('로그인이 취소되었어요.', 'OAUTH_CANCELLED', 0);
  if (result.type !== 'success') throw new ApiClientError('소셜 로그인을 완료하지 못했어요.', 'OAUTH_FAILED', 0);
  const callback = new URL(result.url);
  const callbackError = callback.searchParams.get('error');
  const authorizationCode = callback.searchParams.get('code');
  const returnedState = callback.searchParams.get('state');
  if (callbackError) throw new ApiClientError('소셜 로그인 요청이 거절되었어요.', callbackError, 0);
  if (!authorizationCode || returnedState !== expectedState) throw new ApiClientError('로그인 응답을 확인할 수 없어요.', 'INVALID_OAUTH_RESPONSE', 0);
  return authorizationCode;
}

// 웹에서 팝업 대신 현재 페이지를 그대로 제공자 인증 화면으로 넘긴다(S15P21E201-830).
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

// S15P21E201-830 — 모바일 웹 브라우저는 window.open(WebBrowser.openAuthSessionAsync가
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
  const authorizationCode = await runNativeAuthSession(built.authorizationUrl, redirectUri, challenge.state);
  return completeOAuth(provider, { authorizationCode, redirectUri, codeVerifier, state: challenge.state, nonce: challenge.nonce });
}

// S15P21E201-832 — 이미 로그인한 계정(설정 화면)에 소셜 신원을 붙인다. 흐름은
// loginWithOAuth와 같은 PKCE 왕복이고, 마지막에 completeOAuth 대신 linkOAuthAccount를
// 부르는 것만 다르다. accessToken은 호출 시점(설정 화면)의 것을 그대로 쓴다 — 웹에서는
// 페이지가 넘어갔다 돌아오지만, AuthProvider가 부팅 때마다 세션 쿠키로 다시 복원하므로
// 착지 화면에서 useAuth()로 새로 받으면 된다(oauth/[provider]/callback.tsx).
export async function linkOAuthProvider(provider: OAuthProvider, accessToken: string, returnTo?: string | null): Promise<OAuthIdentityLinkResult> {
  const built = await beginOAuthChallenge(provider);
  const { redirectUri, codeVerifier, challenge } = built;
  if (Platform.OS === 'web') {
    await beginWebRedirect('link', provider, returnTo, built);
    return new Promise<OAuthIdentityLinkResult>(() => {});
  }
  const authorizationCode = await runNativeAuthSession(built.authorizationUrl, redirectUri, challenge.state);
  return linkOAuthAccount(provider, { authorizationCode, redirectUri, codeVerifier, state: challenge.state, nonce: challenge.nonce }, accessToken);
}
