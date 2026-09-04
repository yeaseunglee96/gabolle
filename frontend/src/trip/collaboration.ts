import { apiRequest } from '@/api/client';

export type CompanionRole = 'EDITOR' | 'VIEWER';
export type CompanionInvite = { inviteUrl: string; expiresAt: string };

export function createCompanionInvite(tripId: string, role: CompanionRole, accessToken: string) {
  return apiRequest<CompanionInvite>(`/api/v1/trips/${encodeURIComponent(tripId)}/members/invite`, {
    method: 'POST',
    accessToken,
    body: { role, expiresInDays: 7 },
  });
}
