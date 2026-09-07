import { apiRequest, ApiClientError } from '@/api/client';

export type SignupLanguage = 'KO' | 'EN';

export type SignupInput = {
  email: string;
  password: string;
  displayName: string;
  language: SignupLanguage;
  ageGateAccepted: boolean;
  termsAccepted: boolean;
  privacyAccepted: boolean;
};

export type Registration = {
  userId: string;
  email: string;
  status: string;
};
export type AuthUser = { userId: string; email: string; displayName: string; language: string; status: string };
export type AuthTokens = { accessToken: string; refreshToken: string | null; expiresIn: number; sessionId: string; user: AuthUser };
export type OAuthProvider = 'google' | 'naver' | 'kakao';
export type OAuthChallenge = { state: string; nonce: string; expiresAt: string };

// 소셜 인증(POST /auth/oauth/{provider}) 뒤 셋 중 하나로 갈린다 — S15P21E201-689/-690.
// LOGGED_IN 은 그대로 로그인, SIGNUP_REQUIRED 는 티켓을 들고 회원가입 화면으로,
// LINK_REQUIRED 는 이미 같은 이메일로 가입된 계정이 있어 비밀번호로 연결해야 한다는 뜻이다.
export type OAuthLoginResult = { status: 'LOGGED_IN' } & AuthTokens;
export type OAuthSignupRequiredResult = {
  status: 'SIGNUP_REQUIRED';
  signupTicket: string;
  ticketExpiresAt: string;
  prefill: { email: string | null; displayName: string; language: SignupLanguage; emailProvided: boolean };
};
export type OAuthLinkRequiredResult = {
  status: 'LINK_REQUIRED';
  linkTicket: string;
  maskedEmail: string;
  provider: OAuthProvider;
};
export type OAuthCompleteResult = OAuthLoginResult | OAuthSignupRequiredResult | OAuthLinkRequiredResult;

export function signup(input: SignupInput) {
  return apiRequest<Registration>('/api/v1/auth/signup', { method: 'POST', body: {
    email: input.email.trim(),
    password: input.password,
    displayName: input.displayName.trim(),
    language: input.language,
    ageGateAccepted: input.ageGateAccepted,
    consents: {
      TERMS_OF_SERVICE: input.termsAccepted,
      PRIVACY_POLICY: input.privacyAccepted,
    },
    behaviorPersonalizationEnabled: false,
  } });
}

export function resendEmailVerification(email: string) {
  return apiRequest<void>('/api/v1/auth/email-verification/resend', { method: 'POST', body: { email: email.trim() } });
}
export function requestPasswordReset(email: string) {
  return apiRequest<void>('/api/v1/auth/password-reset/request', { method: 'POST', body: { email: email.trim() }, skipUnauthorizedHandling: true });
}
export function confirmPasswordReset(token: string, newPassword: string) {
  return apiRequest<void>('/api/v1/auth/password-reset/confirm', { method: 'POST', body: { token, newPassword }, skipUnauthorizedHandling: true });
}
export function login(email: string, password: string) { return apiRequest<AuthTokens>('/api/v1/auth/login', { method: 'POST', body: { email: email.trim(), password }, skipUnauthorizedHandling: true }); }
export function createOAuthChallenge(provider: OAuthProvider, redirectUri: string, codeChallenge: string) {
  return apiRequest<OAuthChallenge>(`/api/v1/auth/oauth/${provider}/challenge`, {
    method: 'POST', body: { redirectUri, codeChallenge, codeChallengeMethod: 'S256' }, skipUnauthorizedHandling: true,
  });
}
// ageGateAccepted/consents 를 보내지 않으면 서버가 2단계 흐름(SIGNUP_REQUIRED/LINK_REQUIRED)을 탄다 — MR !288.
// LINK_REQUIRED 는 409 로 오고 응답의 data 를 함께 싣는데, apiRequest 는 에러일 때 원래 data 를 버리므로
// ApiClientError.data(client.ts) 로 건네받아 여기서 정상 결과로 접어 넣는다.
export function completeOAuth(provider: OAuthProvider, input: {
  authorizationCode: string; redirectUri: string; codeVerifier: string; state: string; nonce: string;
}): Promise<OAuthCompleteResult> {
  return apiRequest<OAuthCompleteResult>(`/api/v1/auth/oauth/${provider}`, {
    method: 'POST', skipUnauthorizedHandling: true, body: input,
  }).catch((cause) => {
    if (cause instanceof ApiClientError && cause.code === 'OAUTH_ACCOUNT_LINK_REQUIRED' && cause.data) {
      return cause.data as OAuthLinkRequiredResult;
    }
    throw cause;
  });
}

export function completeOAuthSignup(input: {
  signupTicket: string; displayName: string; language: SignupLanguage; ageGateAccepted: boolean;
  deviceId?: string; termsAccepted: boolean; privacyAccepted: boolean;
}) {
  return apiRequest<OAuthLoginResult>('/api/v1/auth/oauth/signup', {
    method: 'POST', skipUnauthorizedHandling: true, body: {
      signupTicket: input.signupTicket,
      displayName: input.displayName,
      language: input.language,
      ageGateAccepted: input.ageGateAccepted,
      deviceId: input.deviceId,
      consents: { TERMS_OF_SERVICE: input.termsAccepted, PRIVACY_POLICY: input.privacyAccepted },
      behaviorPersonalizationEnabled: false,
    },
  });
}

export function completeOAuthLink(input: { linkTicket: string; password: string; deviceId?: string }) {
  return apiRequest<OAuthLoginResult>('/api/v1/auth/oauth/link', {
    method: 'POST', skipUnauthorizedHandling: true, body: input,
  });
}
export function getMe(accessToken: string) { return apiRequest<AuthUser>('/api/v1/auth/me', { accessToken }); }
export function updateMe(accessToken: string, input: { displayName?: string; language?: SignupLanguage }) {
  return apiRequest<AuthUser>('/api/v1/auth/me', { method: 'PATCH', accessToken, body: input });
}
export function deleteMe(accessToken: string, password: string) {
  return apiRequest<void>('/api/v1/auth/me', { method: 'DELETE', accessToken, body: { password }, skipUnauthorizedHandling: true });
}
export function refreshWebSession() { return apiRequest<AuthTokens>('/api/v1/auth/web/refresh', { method: 'POST', skipUnauthorizedHandling: true }); }
export function refreshMobileSession(refreshToken: string) { return apiRequest<AuthTokens>('/api/v1/auth/refresh', { method: 'POST', body: { refreshToken }, skipUnauthorizedHandling: true }); }
export function logoutWebSession() { return apiRequest<void>('/api/v1/auth/web/logout', { method: 'POST', skipUnauthorizedHandling: true }); }
export function logoutMobileSession(refreshToken: string) { return apiRequest<void>('/api/v1/auth/logout', { method: 'POST', body: { refreshToken, allDevices: false }, skipUnauthorizedHandling: true }); }
