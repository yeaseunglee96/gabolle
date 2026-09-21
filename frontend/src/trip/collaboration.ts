import { apiRequest, ApiClientError, APP_WEB_BASE_URL } from '@/api/client';

export type CompanionRole = 'EDITOR' | 'VIEWER';
export type CompanionInvite = { inviteUrl: string; expiresAt: string };

type TripInviteIssued = { inviteId: string; tripId: string; role: CompanionRole; token: string; expiresAt: string; acceptPath: string };

export async function createCompanionInvite(tripId: string, role: CompanionRole, accessToken: string): Promise<CompanionInvite> {
  const issued = await apiRequest<TripInviteIssued>(`/api/v1/trips/${encodeURIComponent(tripId)}/invites`, {
    method: 'POST',
    accessToken,
    body: { role, expiresInDays: 7 },
  });
  return { inviteUrl: `${APP_WEB_BASE_URL}/invite/${issued.token}`, expiresAt: issued.expiresAt };
}

export type AcceptedInvite = { tripId: string; role: CompanionRole | 'OWNER'; alreadyMember: boolean; joinedAt: string };

// 서버 계약(TripCollaborationController#acceptInvite): 로그인만 하면 되고 표(token) 자체가
// 잠금이다. 이미 참여 중이어도 실패가 아니라 alreadyMember: true 로 200을 돌려준다 — 같은
// 링크를 두 번 열었다고 실패 화면을 보여주지 않기 위함이다.
export function acceptTripInvite(token: string, accessToken: string) {
  return apiRequest<AcceptedInvite>(`/api/v1/trip-invites/${encodeURIComponent(token)}/accept`, {
    method: 'POST',
    accessToken,
  });
}

export function leaveTrip(tripId: string, userId: string, accessToken: string) {
  return apiRequest<void>(`/api/v1/trips/${encodeURIComponent(tripId)}/members/${encodeURIComponent(userId)}`, {
    method: 'DELETE',
    accessToken,
  });
}

// 참여자 목록·역할 관리 —·-320. 서버 계약(TripCollaborationController)
// GET /api/v1/trips/{tripId}/members 참여자 목록 + 내 역할 + 내 편집 가능 여부
// PATCH /api/v1/trips/{tripId}/members/{userId} 역할 변경(EDITOR·VIEWER만, 소유자 전용)
// DELETE .../members/{userId} 참여자 제거(소유자 전용, 소유자 자신은 못 뺀다)
export type TripMemberRole = 'OWNER' | CompanionRole;
export type TripMember = { userId: string; displayName: string | null; role: TripMemberRole; joinedAt: string; invitedBy: string | null; invitedAt: string | null; isMe: boolean };
export type TripMembersView = { members: TripMember[]; myRole: TripMemberRole; canEdit: boolean };

export type TripMembersResult = { state: 'success' } & TripMembersView | { state: 'forbidden' | 'error'; message: string };

function membersFailure(error: unknown): Exclude<TripMembersResult, { state: 'success' }> {
  if (error instanceof ApiClientError && error.status === 403) return { state: 'forbidden', message: error.message };
  return { state: 'error', message: error instanceof Error ? error.message : '참여자 목록을 불러오지 못했어요.' };
}

export async function listTripMembers(tripId: string, accessToken: string | null): Promise<TripMembersResult> {
  try {
    const view = await apiRequest<TripMembersView>(`/api/v1/trips/${encodeURIComponent(tripId)}/members`, { accessToken });
    return { state: 'success', ...view };
  } catch (error) {
    return membersFailure(error);
  }
}

export type TripMemberActionResult = { state: 'success' } | { state: 'forbidden' | 'error'; message: string };

// 응답도 TripMember 와 같은 모양이지만, 화면은 바뀐 한 명이 아니라 목록을 다시 불러
// 그린다(그새 다른 사람이 참여했을 수 있어서) — 그래서 응답 값 자체는 버리고 성공
// 여부만 쓴다.
export async function changeTripMemberRole(tripId: string, userId: string, role: CompanionRole, accessToken: string | null): Promise<TripMemberActionResult> {
  try {
    await apiRequest<TripMember>(`/api/v1/trips/${encodeURIComponent(tripId)}/members/${encodeURIComponent(userId)}`, {
      method: 'PATCH',
      accessToken,
      body: { role },
    });
    return { state: 'success' };
  } catch (error) {
    return membersActionFailure(error);
  }
}

export async function removeTripMember(tripId: string, userId: string, accessToken: string | null): Promise<TripMemberActionResult> {
  try {
    await apiRequest<void>(`/api/v1/trips/${encodeURIComponent(tripId)}/members/${encodeURIComponent(userId)}`, {
      method: 'DELETE',
      accessToken,
    });
    return { state: 'success' };
  } catch (error) {
    return membersActionFailure(error);
  }
}

function membersActionFailure(error: unknown): Exclude<TripMemberActionResult, { state: 'success' }> {
  if (error instanceof ApiClientError && error.status === 403) return { state: 'forbidden', message: error.message };
  return { state: 'error', message: error instanceof Error ? error.message : '요청을 처리하지 못했어요.' };
}

export type TripActivityOperation = 'CREATE' | 'REGENERATE' | 'REGENERATE_DAY' | 'REPLAN_DAY' | 'REPLACE_ITEM' | 'REMOVE_ITEM' | 'LOCK_ITEM' | 'REORDER' | 'REVERT' | 'ADD_ITEM';
export type TripActivityEntry = {
  itineraryId: string;
  version: number;
  operation: TripActivityOperation;
  actorId: string;
  actorName: string | null;
  isMe: boolean;
  at: string;
  baseVersion: number | null;
  revertedFromVersion: number | null;
  warningCodes: string[];
};
export type TripActivityView = { tripId: string; entries: TripActivityEntry[]; limit: number; myRole: TripMemberRole };
export type TripActivityResult = { state: 'success' } & TripActivityView | { state: 'forbidden' | 'error'; message: string };

export async function getTripActivity(tripId: string, accessToken: string | null, limit = 20): Promise<TripActivityResult> {
  try {
    const view = await apiRequest<TripActivityView>(`/api/v1/trips/${encodeURIComponent(tripId)}/activity?limit=${limit}`, { accessToken });
    return { state: 'success', ...view };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 403) return { state: 'forbidden', message: error.message };
    return { state: 'error', message: error instanceof Error ? error.message : '최근 변경을 불러오지 못했어요.' };
  }
}
