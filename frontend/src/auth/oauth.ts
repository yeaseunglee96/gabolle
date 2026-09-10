import * as Crypto from 'expo-crypto';
import * as WebBrowser from 'expo-web-browser';
import { ApiClientError } from '@/api/client';
import { completeOAuth, createOAuthChallenge, type OAuthCompleteResult, type OAuthProvider } from './authApi';

WebBrowser.maybeCompleteAuthSession();

const PROVIDERS: Record<OAuthProvider, { clientId?: string; authorizationEndpoint: string; scope: string }> = {
  google: { clientId: process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID, authorizationEndpoint: 'https://accounts.google.com/o/oauth2/v2/auth', scope: 'openid email profile' },
  naver: { clientId: process.env.EXPO_PUBLIC_NAVER_CLIENT_ID, authorizationEndpoint: 'https://nid.naver.com/oauth2.0/authorize', scope: 'name email' },
  kakao: { clientId: process.env.EXPO_PUBLIC_KAKAO_CLIENT_ID, authorizationEndpoint: 'https://kauth.kakao.com/oauth/authorize', scope: 'profile_nickname account_email' },
  // scope를 email만 요청한다(name은 요청하지 않는다) — name을 요청하면 Apple이 GET 리다이렉트
  // 대신 POST(response_mode=form_post)로만 응답하는데, 이 프런트(정적 SPA)는 POST 본문을 받을
  // 서버가 없다. 백엔드 AppleOAuthProviderClient(jaehyeon님, S15P21E201-825)도 표시 이름을
  // 애초에 안 쓴다 — 가입 화면에서 사용자가 직접 적는 이름을 쓴다. 그래서 다른 provider와 똑같이
  // GET 리다이렉트(response_mode 기본값)로 처리할 수 있다.
  apple: { clientId: process.env.EXPO_PUBLIC_APPLE_CLIENT_ID, authorizationEndpoint: 'https://appleid.apple.com/auth/authorize', scope: 'email' },
};

// redirect URI 는 provider 개발자센터에 등록한 값과 백엔드 GABOLLE_OAUTH_ALLOWED_REDIRECT_URIS 와
// 문자열이 완전히 같아야 한다. 세 값 중 하나만 달라도 challenge 발급이 400 으로 거부된다.
const CALLBACK_BASE_URL = process.env.EXPO_PUBLIC_OAUTH_CALLBACK_BASE_URL ?? 'https://j15e201.p.ssafy.io';

function base64Url(value: string) { return value.replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_'); }
function verifier() { return Array.from(Crypto.getRandomBytes(48), (byte) => byte.toString(16).padStart(2, '0')).join(''); }

export async function loginWithOAuth(provider: OAuthProvider): Promise<OAuthCompleteResult> {
  const config = PROVIDERS[provider];
  if (!config.clientId) throw new ApiClientError(`${provider.toUpperCase()} 로그인 설정이 필요해요.`, 'OAUTH_NOT_CONFIGURED', 0);
  const redirectUri = `${CALLBACK_BASE_URL}/oauth/${provider}/callback`;
  const codeVerifier = verifier();
  const digest = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, codeVerifier, { encoding: Crypto.CryptoEncoding.BASE64 });
  const challenge = await createOAuthChallenge(provider, redirectUri, base64Url(digest));
  const authorizationUrl = `${config.authorizationEndpoint}?${new URLSearchParams({
    client_id: config.clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: config.scope,
    state: challenge.state,
    nonce: challenge.nonce,
    code_challenge: base64Url(digest),
    code_challenge_method: 'S256',
  }).toString()}`;
  const result = await WebBrowser.openAuthSessionAsync(authorizationUrl, redirectUri);
  if (result.type === 'cancel' || result.type === 'dismiss') throw new ApiClientError('로그인이 취소되었어요.', 'OAUTH_CANCELLED', 0);
  if (result.type !== 'success') throw new ApiClientError('소셜 로그인을 완료하지 못했어요.', 'OAUTH_FAILED', 0);
  const callback = new URL(result.url);
  const callbackError = callback.searchParams.get('error');
  const authorizationCode = callback.searchParams.get('code');
  const returnedState = callback.searchParams.get('state');
  if (callbackError) throw new ApiClientError('소셜 로그인 요청이 거절되었어요.', callbackError, 0);
  if (!authorizationCode || returnedState !== challenge.state) throw new ApiClientError('로그인 응답을 확인할 수 없어요.', 'INVALID_OAUTH_RESPONSE', 0);
  return completeOAuth(provider, { authorizationCode, redirectUri, codeVerifier, state: challenge.state, nonce: challenge.nonce });
}
