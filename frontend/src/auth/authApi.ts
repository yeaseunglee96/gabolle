import { apiRequest, ApiClientError } from '@/api/client';
import { loadBehaviorConsent } from '@/personalization/behaviorConsent';

// 가입 요청에 실을 행동 개인화 동의. 값을 코드에 박지 않고 사용자가 정한 것을 읽는다.
async function behaviorPersonalizationConsent() {
  const enabled = await loadBehaviorConsent();
  return {
    behaviorPersonalizationEnabled: enabled,
    extraConsents: enabled ? { BEHAVIOR_PERSONALIZATION: true } : {},
  };
}

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
// avatarUrl 은 레코드 맨 끝에 붙은 칸이다 — 안 고른 사람은 null 이고 화면이 기본 그림을 그린다.
export type AuthUser = { userId: string; email: string; displayName: string; language: string; status: string; avatarUrl?: string | null };
export type AuthTokens = { accessToken: string; refreshToken: string | null; expiresIn: number; sessionId: string; user: AuthUser };
export type OAuthProvider = 'google' | 'naver' | 'kakao' | 'apple';
export type OAuthChallenge = { state: string; nonce: string; expiresAt: string };

// 소셜 인증(POST /auth/oauth/{provider}) 뒤 셋 중 하나로 갈린다 —/-690.
// LOGGED_IN 은 그대로 로그인, SIGNUP_REQUIRED 는 티켓을 들고 회원가입 화면으로
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

export async function signup(input: SignupInput) {
  const { behaviorPersonalizationEnabled, extraConsents } = await behaviorPersonalizationConsent();
  return apiRequest<Registration>('/api/v1/auth/signup', { method: 'POST', body: {
    email: input.email.trim(),
    password: input.password,
    displayName: input.displayName.trim(),
    language: input.language,
    ageGateAccepted: input.ageGateAccepted,
    consents: {
      TERMS_OF_SERVICE: input.termsAccepted,
      PRIVACY_POLICY: input.privacyAccepted,
      ...extraConsents,
    },
    behaviorPersonalizationEnabled,
  } });
}

export function resendEmailVerification(email: string) {
  return apiRequest<void>('/api/v1/auth/email-verification/resend', { method: 'POST', body: { email: email.trim() } });
}
// — 메일 링크가 화면으로 오면서 확인을 화면이 부른다. skipUnauthorizedHandling 을
// 켜는 이유는 재설정 확인과 같다 — 이 요청은 로그인한 사람이 부르는 것이 아니라 메일에서 온
// 사람이 부르므로, 401 을 받았다고 로그인 화면으로 밀어내면 실패 이유를 못 보여 준다.
export function confirmEmailVerification(token: string) {
  return apiRequest<void>('/api/v1/auth/email-verification/confirm', { method: 'POST', body: { token }, skipUnauthorizedHandling: true });
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

export async function completeOAuthSignup(input: {
  signupTicket: string; displayName: string; language: SignupLanguage; ageGateAccepted: boolean;
  deviceId?: string; termsAccepted: boolean; privacyAccepted: boolean;
}) {
  const { behaviorPersonalizationEnabled, extraConsents } = await behaviorPersonalizationConsent();
  return apiRequest<OAuthLoginResult>('/api/v1/auth/oauth/signup', {
    method: 'POST', skipUnauthorizedHandling: true, body: {
      signupTicket: input.signupTicket,
      displayName: input.displayName,
      language: input.language,
      ageGateAccepted: input.ageGateAccepted,
      deviceId: input.deviceId,
      consents: { TERMS_OF_SERVICE: input.termsAccepted, PRIVACY_POLICY: input.privacyAccepted, ...extraConsents },
      behaviorPersonalizationEnabled,
    },
  });
}

export function completeOAuthLink(input: { linkTicket: string; password: string; deviceId?: string }) {
  return apiRequest<OAuthLoginResult>('/api/v1/auth/oauth/link', {
    method: 'POST', skipUnauthorizedHandling: true, body: input,
  });
}

export type OAuthIdentityLinkResult =
  | { status: 'LINKED'; providerEmail: string | null; alreadyLinked: boolean }
  | { status: 'TAKEN' };

export async function linkOAuthAccount(
  provider: OAuthProvider,
  input: { authorizationCode: string; redirectUri: string; codeVerifier: string; state: string; nonce: string },
  accessToken: string,
): Promise<OAuthIdentityLinkResult> {
  try {
    const dto = await apiRequest<{ provider: OAuthProvider; providerEmail: string | null; linkedAt: string; alreadyLinked: boolean }>(
      `/api/v1/auth/oauth/${provider}/link`,
      { method: 'POST', accessToken, body: input },
    );
    return { status: 'LINKED', providerEmail: dto.providerEmail, alreadyLinked: dto.alreadyLinked };
  } catch (cause) {
    if (cause instanceof ApiClientError && cause.code === 'OAUTH_IDENTITY_TAKEN') return { status: 'TAKEN' };
    throw cause;
  }
}
export function getMe(accessToken: string) { return apiRequest<AuthUser>('/api/v1/auth/me', { accessToken }); }
export function updateMe(accessToken: string, input: { displayName?: string; language?: SignupLanguage; avatarUrl?: string | null }) {
  return apiRequest<AuthUser>('/api/v1/auth/me', { method: 'PATCH', accessToken, body: input });
}
export function deleteMe(accessToken: string, confirmation: string) {
  return apiRequest<void>('/api/v1/auth/me', { method: 'DELETE', accessToken, body: { confirmation }, skipUnauthorizedHandling: true });
}

// 계정 삭제 전 안내 화면이 보여줄 실제 영향 수/195). 실제로 지워지는 범위와
// 같은 기준으로 센 값이다 — reviewCount는 이 백엔드에 리뷰 도메인이 없어 칸 자체가 없다.
export type AccountDeletionPreview = { ownedTripCount: number; itineraryCount: number; recordCount: number };

export function getAccountDeletionPreview(accessToken: string) {
  return apiRequest<AccountDeletionPreview>('/api/v1/auth/me/deletion-preview', { accessToken });
}

export type ConsentName = 'BEHAVIOR_PERSONALIZATION' | 'PRECISE_LOCATION' | 'HEALTH_CONSTRAINTS';
export type ConsentItem = { consentType: string; status: 'GRANTED' | 'REVOKED'; policyVersion: string; decidedAt: string };
export type MyConsents = { behaviorPersonalizationEnabled: boolean; consents: ConsentItem[] };

export function getMyConsents(accessToken: string) {
  return apiRequest<MyConsents>('/api/v1/auth/me/consents', { accessToken });
}

export function updateMyConsents(accessToken: string, consents: Partial<Record<ConsentName, boolean>>) {
  return apiRequest<MyConsents>('/api/v1/auth/me/consents', { method: 'PATCH', accessToken, body: { consents } });
}
export function refreshWebSession() { return apiRequest<AuthTokens>('/api/v1/auth/web/refresh', { method: 'POST', skipUnauthorizedHandling: true }); }
export function refreshMobileSession(refreshToken: string) { return apiRequest<AuthTokens>('/api/v1/auth/refresh', { method: 'POST', body: { refreshToken }, skipUnauthorizedHandling: true }); }
export function logoutWebSession() { return apiRequest<void>('/api/v1/auth/web/logout', { method: 'POST', skipUnauthorizedHandling: true }); }
export function logoutMobileSession(refreshToken: string) { return apiRequest<void>('/api/v1/auth/logout', { method: 'POST', body: { refreshToken, allDevices: false }, skipUnauthorizedHandling: true }); }
