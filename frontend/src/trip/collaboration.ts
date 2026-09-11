import { apiRequest } from '@/api/client';

export type CompanionRole = 'EDITOR' | 'VIEWER';
export type CompanionInvite = { inviteUrl: string; expiresAt: string };

export function createCompanionInvite(tripId: string, role: CompanionRole, accessToken: string) {
  // jaehyeon 님 axmap 제보(2026-09-08): 프론트는 .../members/invite를 불렀는데 서버는
  // .../invites로 만들어져 있어 지금까지 이 요청이 아예 안 붙고 있었다. 서버 쪽에 맞춘다.
  return apiRequest<CompanionInvite>(`/api/v1/trips/${encodeURIComponent(tripId)}/invites`, {
    method: 'POST',
    accessToken,
    body: { role, expiresInDays: 7 },
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
