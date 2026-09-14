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
  // S15P21E201-833 — 애플에만 이메일을 요청하고, 그래서 애플에만 돌아오는 자리가 둘로 갈린다.
  //
  //    애플은 이름이든 이메일이든 하나라도 요청하면 결과를 쿼리가 아니라 HTTP POST 로 보내겠다고
  //    요구한다(response_mode=form_post). 안 보내면 요청 자체를 거절한다 —
  //    "response_mode must be form_post when name or email scope is requested" (2026-09-10 실측).
  //    이 프런트는 정적 화면이라 POST 본문을 받을 수 없어서 그동안 이메일을 아예 안 받았고,
  //    그 결과 애플로 가입한 계정에는 이메일이 어디에도 없었다.
  //
  //    이제 그 POST 를 받는 서버 경로가 있다(AppleFormPostController, S15P21E201-833). 그래서
  //    애플에 넘기는 redirect_uri 만 그 경로로 옮긴다. 서버는 받은 code·state 를 그대로 붙여
  //    아래 CALLBACK_BASE_URL 의 콜백 화면으로 302 로 넘기므로, 화면이 착지하는 자리는 그대로다.
  //
  //    🔴 name 은 요청하지 않는다. 이름은 서명 밖의 값이라 신원에 쓸 수 없고, email 만 요청해도
  //    POST 요구는 똑같이 생기므로 잃는 것이 없다. 이메일도 이 POST 본문에서 읽지 않는다 —
  //    서명된 id_token 의 email 클레임으로 들어온다.
  //
  //    🔴 이 주소는 애플 개발자 콘솔의 Return URLs 에 등록돼 있어야 한다(2026-09-13 등록 확인).
  //    콘솔에 없으면 애플이 "Invalid web redirect url" 로 거절해 애플 로그인이 통째로 죽는다.
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

// 로그인(loginWithOAuth)과 이미 로그인한 계정에 소셜을 붙이는 연결(linkOAuthProvider,
// S15P21E201-832) 둘 다 여기까지는 완전히 같다 — 제공자에게 던질 인증 URL을 만드는
// PKCE 절차. 둘이 갈리는 것은 그 뒤 코드를 받아 어느 서버 경로로 보내는지뿐이다.
async function beginOAuthChallenge(provider: OAuthProvider) {
  const config = PROVIDERS[provider];
  if (!config.clientId) throw new ApiClientError(`${provider.toUpperCase()} 로그인 설정이 필요해요.`, 'OAUTH_NOT_CONFIGURED', 0);
  // 화면이 실제로 착지하는 자리. 넷 다 같고 애플도 여기로 온다 — 애플의 POST 를 받은 서버가
  // code·state 를 붙여 이 주소로 302 로 넘기기 때문이다.
  const landingUri = `${CALLBACK_BASE_URL}/oauth/${provider}/callback`;
  // 제공자에게 넘기는 주소. 애플만 서버의 POST 수신 경로로 갈린다(S15P21E201-833).
  // 🔴 코드 교환에도 이 값을 그대로 써야 한다 — 제공자는 인증 때 받은 redirect_uri 와
  //    교환 때 받은 값이 글자까지 같기를 요구하고, 다르면 코드 교환이 거절된다.
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

// 네이티브(앱)는 팝업 차단이 끼어들 자리가 없는 앱 안 브라우저 화면을 쓰므로
// S15P21E201-830 이전 방식 그대로 코드를 바로 받아 온다.
// 두 번째 인자는 제공자에게 넘긴 redirect_uri 가 아니라 **앱 안 브라우저가 착지하기를 기다리는
// 주소**다. 애플만 둘이 다르다 — 애플은 서버의 POST 수신 경로로 보내고 그 서버가 여기로 넘긴다.
async function runNativeAuthSession(authorizationUrl: string, landingUri: string, expectedState: string) {
  // preferUniversalLinks: true 가 없으면 expo-web-browser 는 iOS 에서 https 리다이렉트를
  // ASWebAuthenticationSession 의 callbackURLScheme(옛 커스텀 스킴 전용 방식)으로 열어서
  // Associated Domains(앱과 j15e201.p.ssafy.io 를 연결하는 iOS 기능, S15P21E201-872)를 아예
  // 안 쓴다 — 그래서 콜백 화면이 "처리하고 있어요"에서 안 닫혔다(node_modules/expo-web-browser/
  // ios/WebAuthSession.swift 확인, 2026-09-12).
  const result = await WebBrowser.openAuthSessionAsync(authorizationUrl, landingUri, { preferUniversalLinks: true });
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
  const authorizationCode = await runNativeAuthSession(built.authorizationUrl, built.landingUri, challenge.state);
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
  const authorizationCode = await runNativeAuthSession(built.authorizationUrl, built.landingUri, challenge.state);
  return linkOAuthAccount(provider, { authorizationCode, redirectUri, codeVerifier, state: challenge.state, nonce: challenge.nonce }, accessToken);
}
