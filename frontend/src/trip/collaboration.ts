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
