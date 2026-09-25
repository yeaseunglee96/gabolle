// 마이페이지 「내 댓글」 — GET /api/v1/users/me/replies (S15P21E201-1652, 서버 S15P21E201-1600).
//
// 원글이 지워지거나 가려져도 내 댓글은 여기서 찾는다. 원글 상태는 서버가 알려 준다.
import { apiRequest, ApiClientError } from '@/api/client';
import type { StoryDto } from '@/social/stories';

/**
 * 바로 위 글의 지금 상태.
 * HIDDEN = 신고로 가려졌거나, 나만 보기·팔로워 공개로 바뀌었거나, 작성자가 나를 차단해서 지금은 내가 못 봄.
 * VISIBLE 일 때만 미리보기·작성자가 온다 — 가려진 글을 이 목록으로 엿보면 안 된다.
 */
export type MyReplyParent = { id: string; state: 'VISIBLE' | 'DELETED' | 'HIDDEN'; bodyPreview: string | null; authorName: string | null };
export type MyReplyItem = { reply: StoryDto; parent: MyReplyParent };

export type MyRepliesResult =
  | { state: 'success'; items: MyReplyItem[]; nextCursor: string | null }
  /** 서버가 아직 이 기능을 모른다(404 — 배포 전). 오류가 아니다. */
  | { state: 'not-ready' }
  | { state: 'error' };

export async function loadMyReplies(accessToken: string | null, cursor?: string | null): Promise<MyRepliesResult> {
  const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : '';
  try {
    const page = await apiRequest<{ items: MyReplyItem[]; nextCursor: string | null }>(`/api/v1/users/me/replies${query}`, { accessToken });
    return { state: 'success', items: page.items ?? [], nextCursor: page.nextCursor ?? null };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 404) return { state: 'not-ready' };
    return { state: 'error' };
  }
}
