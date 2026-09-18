import type { Href } from 'expo-router';

import type { AuthTokens, OAuthCompleteResult, OAuthProvider } from './authApi';
import { resolveDestination } from './pendingReturnTo';

// 소셜 인증 완료(LOGGED_IN/SIGNUP_REQUIRED/LINK_REQUIRED) 뒤 어디로 갈지는 sign-in.tsx의
// 팝업 흐름과 oauth/[provider]/callback.tsx의 웹 전체 페이지 이동 흐름(S15P21E201-830)
// 둘 다에서 똑같아야 한다 — 한쪽에만 있으면 다른 쪽이 그 사이 어긋난다.
export async function navigateAfterOAuthComplete(input: {
  result: OAuthCompleteResult;
  provider: OAuthProvider;
  returnTo?: string | null;
  router: { replace: (href: Href) => void; push: (href: Href) => void };
  acceptTokens: (tokens: AuthTokens) => Promise<void>;
}) {
  const { result, provider, returnTo, router, acceptTokens } = input;
  if (result.status === 'LOGGED_IN') {
    await acceptTokens(result);
    router.replace((await resolveDestination(returnTo)) as Href);
  } else if (result.status === 'SIGNUP_REQUIRED') {
    router.push({
      pathname: '/oauth-signup',
      params: {
        provider,
        signupTicket: result.signupTicket,
        email: result.prefill.email ?? '',
        displayName: result.prefill.displayName,
        language: result.prefill.language,
        emailProvided: String(result.prefill.emailProvided),
        ...(returnTo ? { returnTo } : {}),
      },
    } as Href);
  } else {
    router.push({
      pathname: '/oauth-link',
      params: { provider: result.provider, linkTicket: result.linkTicket, maskedEmail: result.maskedEmail, ...(returnTo ? { returnTo } : {}) },
    } as Href);
  }
}
