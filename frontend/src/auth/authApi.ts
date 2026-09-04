import { apiRequest } from '@/api/client';

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
export function completeOAuth(provider: OAuthProvider, input: {
  authorizationCode: string; redirectUri: string; codeVerifier: string; state: string; nonce: string;
}) {
  return apiRequest<AuthTokens>(`/api/v1/auth/oauth/${provider}`, {
    method: 'POST', skipUnauthorizedHandling: true, body: {
      ...input,
      ageGateAccepted: true,
      consents: { TERMS_OF_SERVICE: true, PRIVACY_POLICY: true },
      behaviorPersonalizationEnabled: false,
    },
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
