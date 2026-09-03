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
export function login(email: string, password: string) { return apiRequest<AuthTokens>('/api/v1/auth/login', { method: 'POST', body: { email: email.trim(), password }, skipUnauthorizedHandling: true }); }
export function getMe(accessToken: string) { return apiRequest<AuthUser>('/api/v1/auth/me', { accessToken }); }
export function refreshWebSession() { return apiRequest<AuthTokens>('/api/v1/auth/web/refresh', { method: 'POST', skipUnauthorizedHandling: true }); }
export function logoutWebSession() { return apiRequest<void>('/api/v1/auth/web/logout', { method: 'POST', skipUnauthorizedHandling: true }); }
export function logoutMobileSession(refreshToken: string) { return apiRequest<void>('/api/v1/auth/logout', { method: 'POST', body: { refreshToken, allDevices: false }, skipUnauthorizedHandling: true }); }
