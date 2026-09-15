import { apiRequest, ApiClientError } from '@/api/client';
import { loadBehaviorConsent } from '@/personalization/behaviorConsent';

// 가입 요청에 실을 행동 개인화 동의. 값을 코드에 박지 않고 사용자가 정한 것을 읽는다.
//
// 🔴 서버는 behaviorPersonalizationEnabled 가 true 면 consents 에도
//    BEHAVIOR_PERSONALIZATION: true 가 있어야 가입을 받는다(ConsentPolicy). 둘을 같이 만든다.
// 🔴 이 값이 서버로 가는 유일한 순간이 가입이다. 가입 뒤에 토글을 바꾸면 그 변경은
//    기기에만 남는다 — 서버에 동의를 바꿀 API 가 아직 없다.
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
// avatarUrl 은 레코드 맨 끝에 붙은 칸이다(S15P21E201-844) — 안 고른 사람은 null 이고 화면이 기본 그림을 그린다.
export type AuthUser = { userId: string; email: string; displayName: string; language: string; status: string; avatarUrl?: string | null };
export type AuthTokens = { accessToken: string; refreshToken: string | null; expiresIn: number; sessionId: string; user: AuthUser };
export type OAuthProvider = 'google' | 'naver' | 'kakao' | 'apple';
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
// S15P21E201-941 — 메일 링크가 화면으로 오면서 확인을 화면이 부른다. skipUnauthorizedHandling 을
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

// jaehyeon 님 계약(S15P21E201-690, 2026-09-11): POST /auth/oauth/{provider}/link.
// completeOAuthLink(위)와 다르다 — 그건 로그인 전에 409를 받고 비밀번호로 붙이는 쪽이고,
// 이건 이미 로그인한 계정에 소셜 신원을 직접 붙이는 쪽이다(설정 화면, S15P21E201-832).
// 이메일을 전혀 안 보므로 애플·기본 동의 카카오처럼 이메일을 안 주는 제공자도 그대로 된다.
// 같은 신원을 같은 계정에 다시 연결하면 alreadyLinked=true로 200(멱등)이고, 다른 계정에
// 이미 붙어 있으면 409 OAUTH_IDENTITY_TAKEN이다 — 그건 오류로 던지지 않고 정상 결과로 접어
// 넣는다(completeOAuth의 LINK_REQUIRED 처리와 같은 방식).
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
// 박재현 님 계약(S15P21E201-837, 2026-09-11, back/dev MR !598): 소셜로만 가입한 계정은
// local_credential 행이 아예 없어 비밀번호를 못 받는다 — 본인 확인을 비밀번호에서 사용자가
// 직접 치는 확인 값으로 옮겼다. confirmation은 필수이고 "DELETE"와 대소문자·앞뒤 공백까지
// 정확히 같아야 한다("입력한 그대로 보낸다" — 화면이 다듬어 보내면 확인이 아니라 형식이
// 된다). password는 선택이고, 비밀번호로 가입한 계정에서만 의미가 있다 — 화면은 아예 비밀번호
// 칸을 안 그리는 쪽을 택했다(jaehyeon 님 권고, 두 종류 계정이 같은 화면을 쓸 수 있다).
export function deleteMe(accessToken: string, confirmation: string) {
  return apiRequest<void>('/api/v1/auth/me', { method: 'DELETE', accessToken, body: { confirmation }, skipUnauthorizedHandling: true });
}

// 계정 삭제 전 안내 화면이 보여줄 실제 영향 수(S15P21E201-188/195). 실제로 지워지는 범위와
// 같은 기준으로 센 값이다 — reviewCount는 이 백엔드에 리뷰 도메인이 없어 칸 자체가 없다.
export type AccountDeletionPreview = { ownedTripCount: number; itineraryCount: number; recordCount: number };

export function getAccountDeletionPreview(accessToken: string) {
  return apiRequest<AccountDeletionPreview>('/api/v1/auth/me/deletion-preview', { accessToken });
}

// 가입 뒤 동의를 읽고 바꾸는 자리 — S15P21E201-735(고지혁 님 · MR !585/!600, 2026-09-11).
// PATCH는 부분 수정이다 — 보낸 항목만 바뀌고 안 보낸 항목은 그대로다. TERMS_OF_SERVICE·
// PRIVACY_POLICY는 이 경로로 못 끈다(서버가 400 REQUIRED_CONSENT_NOT_REVOCABLE로 거부한다) —
// 그건 철회가 아니라 탈퇴다.
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
