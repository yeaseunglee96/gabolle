// 연결된 소셜 계정을 읽고 뗀다 —-1329 (서버는-1317).
//
// 🔴 지금까지 화면은 **무엇이 붙어 있는지 몰랐다.** 넷을 늘 「연결하기」로 그려 두고, 눌러 봐야
//    「이미 연결되어 있어요」로 알 수 있었다. 이제 서버가 목록을 준다.
import { ApiClientError, apiRequest } from '@/api/client';
import type { OAuthProvider } from '@/auth/authApi';

const KNOWN: Record<string, OAuthProvider> = { GOOGLE: 'google', NAVER: 'naver', KAKAO: 'kakao', APPLE: 'apple' };

export type LinkedIdentity = {
  provider: OAuthProvider;
  /** 그 소셜이 알려 준 이메일. **안 주는 경우가 있다** — 카카오 기본 동의는 이메일을 안 준다. */
  providerEmail: string | null;
  /**
   * 이걸 떼도 이 계정에 들어올 길이 남는가.
   *
   * 🔴 **판정은 서버가 한다.** 화면이 「하나뿐이면 못 뗀다」를 다시 적으면 규칙이 두 벌이 되고,
   *    한쪽만 낡으면 **뗄 수 없어야 할 것을 뗄 수 있게** 그리게 된다. 서버가 다시 막으므로
   *    계정이 잠기지는 않지만, 눌렀는데 거절당하는 화면이 된다.
   */
  canUnlink: boolean;
};

export type LinkedIdentitiesResult =
  | { state: 'success'; items: LinkedIdentity[]; canSignInWithPassword: boolean }
  /** 서버에 아직 이 자리가 없다(404·501). 화면은 연결 상태를 감춘다 — 지어내지 않는다. */
  | { state: 'unavailable' }
  | { state: 'error'; message: string };

export type UnlinkResult =
  | { state: 'success' }
  /** 마지막 로그인 수단이라 서버가 막았다. */
  | { state: 'blocked'; message: string }
  | { state: 'error'; message: string };

type IdentityDto = { provider?: string; providerEmail?: string | null; linkedAt?: string; canUnlink?: boolean };

/**
 * 🔴 **모르는 판은 버린다.** 서버가 이 앱 판보다 새 소셜을 알고 있으면 이름조차 못 그린다 —
 *    빈 줄을 그리느니 안 그린다. 「뗄 수 있나」는 어차피 서버가 정하므로, 버려도 마지막 수단이
 *    잘못 떼이는 일은 없다.
 */
export function adaptIdentities(dto: { items?: IdentityDto[]; canSignInWithPassword?: boolean }): {
  items: LinkedIdentity[];
  canSignInWithPassword: boolean;
} {
  const items: LinkedIdentity[] = [];
  for (const row of dto?.items ?? []) {
    const provider = KNOWN[String(row?.provider ?? '').toUpperCase()];
    if (!provider) continue;
    items.push({
      provider,
      providerEmail: typeof row.providerEmail === 'string' && row.providerEmail !== '' ? row.providerEmail : null,
      canUnlink: row.canUnlink === true,
    });
  }
  return { items, canSignInWithPassword: dto?.canSignInWithPassword === true };
}

export async function fetchLinkedIdentities(accessToken: string | null): Promise<LinkedIdentitiesResult> {
  try {
    const dto = await apiRequest<{ items?: IdentityDto[]; canSignInWithPassword?: boolean }>(
      '/api/v1/auth/me/identities',
      { accessToken },
    );
    return { state: 'success', ...adaptIdentities(dto) };
  } catch (error) {
    if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) {
      return { state: 'unavailable' };
    }
    return { state: 'error', message: error instanceof Error ? error.message : '연결된 계정을 불러오지 못했어요.' };
  }
}

export async function unlinkIdentity(provider: OAuthProvider, accessToken: string | null): Promise<UnlinkResult> {
  try {
    await apiRequest<void>(`/api/v1/auth/me/identities/${encodeURIComponent(provider)}`, {
      method: 'DELETE',
      accessToken,
    });
    return { state: 'success' };
  } catch (error) {
    if (error instanceof ApiClientError && error.code === 'LAST_SIGN_IN_METHOD') {
      return { state: 'blocked', message: error.message };
    }
    return { state: 'error', message: error instanceof Error ? error.message : '연결을 떼지 못했어요.' };
  }
}
