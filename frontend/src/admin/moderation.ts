// 신고 검토(운영자) API — S15P21E201-599. 백엔드 계약은
// backend/.../moderation/presentation/AdminModerationController.java 그대로다
import { apiRequest, ApiClientError } from '@/api/client';

export type ModerationQueueItem = {
  storyId: string;
  authorName: string | null;
  body: string;
  reasons: string[];
  reportCount: number;
  oldestReportedAt: string;
  elapsedSeconds: number;
};

export type ModerationQueueResult =
  | { state: 'success'; items: ModerationQueueItem[] }
  | { state: 'forbidden' }
  | { state: 'error'; message: string };

export async function fetchModerationQueue(accessToken: string | null): Promise<ModerationQueueResult> {
  try {
    const dto = await apiRequest<{ items: ModerationQueueItem[] }>('/api/v1/admin/story-reports', { accessToken });
    return { state: 'success', items: dto.items };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 403) return { state: 'forbidden' };
    return { state: 'error', message: error instanceof Error ? error.message : '검토 목록을 불러오지 못했어요.' };
  }
}

export type ModerationActionResult =
  | { state: 'success'; resolvedReportCount: number }
  | { state: 'forbidden' }
  | { state: 'error'; message: string };

async function resolveReport(storyId: string, action: 'remove' | 'dismiss', accessToken: string | null): Promise<ModerationActionResult> {
  try {
    const dto = await apiRequest<{ storyId: string; resolvedReportCount: number }>(
      `/api/v1/admin/story-reports/${encodeURIComponent(storyId)}/${action}`,
      { method: 'POST', accessToken },
    );
    return { state: 'success', resolvedReportCount: dto.resolvedReportCount };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 403) return { state: 'forbidden' };
    return { state: 'error', message: error instanceof Error ? error.message : '처리하지 못했어요.' };
  }
}

export function removeStory(storyId: string, accessToken: string | null) {
  return resolveReport(storyId, 'remove', accessToken);
}

export function dismissReport(storyId: string, accessToken: string | null) {
  return resolveReport(storyId, 'dismiss', accessToken);
}
