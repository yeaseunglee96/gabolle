import type { Href } from 'expo-router';

import type { AuthTokens, OAuthCompleteResult, OAuthProvider } from './authApi';
import { enterApp, type StackRouter } from './enterApp';
import { resolveDestination } from './pendingReturnTo';

// 소셜 인증 완료(LOGGED_IN/SIGNUP_REQUIRED/LINK_REQUIRED) 뒤 어디로 갈지는 sign-in.tsx의
// 팝업 흐름과 oauth/[provider]/callback.tsx의 웹 전체 페이지 이동 흐름
// 둘 다에서 똑같아야 한다 — 한쪽에만 있으면 다른 쪽이 그 사이 어긋난다.
export async function navigateAfterOAuthComplete(input: {
  result: OAuthCompleteResult;
  provider: OAuthProvider;
  returnTo?: string | null;
  router: StackRouter & { push: (href: Href) => void };
  acceptTokens: (tokens: AuthTokens) => Promise<void>;
}) {
  const { result, provider, returnTo, router, acceptTokens } = input;
  if (result.status === 'LOGGED_IN') {
    await acceptTokens(result);
    // 쌓인 로그인 화면을 치우고 간다 소셜 로그인은 착지하면서
    // 화면이 한 칸 더 쌓이는 판이 있어 여기가 특히 중요하다.
    enterApp(router, (await resolveDestination(returnTo)) as Href);
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
