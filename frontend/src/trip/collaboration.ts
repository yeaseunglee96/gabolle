import { apiRequest, ApiClientError, APP_WEB_BASE_URL } from '@/api/client';

export type CompanionRole = 'EDITOR' | 'VIEWER';
export type CompanionInvite = { inviteUrl: string; expiresAt: string };

// 서버 응답(TripInviteResponse)은 inviteId·tripId·role·token·expiresAt·acceptPath 뿐이다 —
// acceptPath는 "서버 API 경로"이지 앱 화면 주소가 아니라고 레코드 주석에 그대로 적혀 있다.
// 착지 화면은 /invite/[token].tsx(앱 라우트)이므로, 공유할 링크는 token으로 여기서 직접
// 조립한다(S15P21E201-846 — 예전에는 이 응답을 그대로 CompanionInvite로 캐스팅해 inviteUrl이
// 항상 undefined였다).
type TripInviteIssued = { inviteId: string; tripId: string; role: CompanionRole; token: string; expiresAt: string; acceptPath: string };

export async function createCompanionInvite(tripId: string, role: CompanionRole, accessToken: string): Promise<CompanionInvite> {
  // jaehyeon 님 axmap 제보(2026-09-08): 프론트는 .../members/invite를 불렀는데 서버는
  // .../invites로 만들어져 있어 지금까지 이 요청이 아예 안 붙고 있었다. 서버 쪽에 맞춘다.
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
// 링크를 두 번 열었다고 실패 화면을 보여주지 않기 위함이다(S15P21E201-302).
export function acceptTripInvite(token: string, accessToken: string) {
  return apiRequest<AcceptedInvite>(`/api/v1/trip-invites/${encodeURIComponent(token)}/accept`, {
    method: 'POST',
    accessToken,
  });
}

// jaehyeon 님(2026-09-08 axmap): 여행 삭제(DELETE /trips/{tripId})는 소유자 전용이고 동행자가
// 부르면 403이다. 동행자가 "내 화면에서 치우고 싶다"는 요청은 이 자리 것 — 자기 자신을
// 멤버 목록에서 빼는 것이라, 삭제와 버튼을 하나로 합치지 않는다(눌린 사람의 역할에 따라
// 결과가 달라지는 버튼이 되는 것을 피한다).
export function leaveTrip(tripId: string, userId: string, accessToken: string) {
  return apiRequest<void>(`/api/v1/trips/${encodeURIComponent(tripId)}/members/${encodeURIComponent(userId)}`, {
    method: 'DELETE',
    accessToken,
  });
}

// 참여자 목록·역할 관리 — S15P21E201-327·-320. 서버 계약(TripCollaborationController):
//   GET   /api/v1/trips/{tripId}/members            참여자 목록 + 내 역할 + 내 편집 가능 여부
//   PATCH /api/v1/trips/{tripId}/members/{userId}   역할 변경(EDITOR·VIEWER만, 소유자 전용)
//   DELETE .../members/{userId}                     참여자 제거(소유자 전용, 소유자 자신은 못 뺀다)
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

// 최근 변경 — S15P21E201-327·-687. 서버 계약(박재현 님, MR !251, 2026-09-07):
//   GET /api/v1/trips/{tripId}/activity?limit=
// 새 이력 표를 안 만들고 일정의 판(itinerary_versions)을 역순으로 읽은 것이다(DEC-COL-001) —
// 그래서 판을 안 만드는 변경(초대 발급, 역할 변경, 참여자 제거)은 여기 안 나온다. 지어내지
// 않는다 — 이 목록은 "일정 자체가 바뀐 기록"만 보여준다는 뜻이고, 화면 문구도 그렇게 맞춘다.
export type TripActivityOperation = 'CREATE' | 'REGENERATE' | 'REGENERATE_DAY' | 'REPLACE_ITEM' | 'REMOVE_ITEM' | 'LOCK_ITEM' | 'REORDER' | 'REVERT' | 'ADD_ITEM';
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
