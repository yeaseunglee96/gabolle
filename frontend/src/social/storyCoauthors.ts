// 기록(Story) 공동 작성 — 초대 발급·수락·참여자 관리 — S15P21E201-770(백엔드, 완료)·-845(프론트).
// 서버 계약(StoryCoauthorController)은 여행 협업(trip/collaboration.ts)과 모양이 같다 — 다섯
// 경로도, 예외도(StoryExceptionHandler가 StoryController·StoryCoauthorController를 함께 쓴다).
import { apiRequest, ApiClientError, APP_WEB_BASE_URL } from '@/api/client';

export type StoryInvite = { inviteUrl: string; expiresAt: string };

// 서버 응답(StoryInviteResponse)은 inviteId·storyId·token·expiresAt·acceptPath뿐이다 —
// acceptPath는 서버 API 경로이지 앱 화면 주소가 아니다(레코드 주석). 착지 화면은
// /story-invite/[token].tsx이므로 공유 링크는 token으로 여기서 조립한다 — trip/collaboration.ts의
// createCompanionInvite와 같은 이유·같은 모양이다(S15P21E201-846에서 그쪽의 같은 실수를 고쳤다).
type StoryInviteIssued = { inviteId: string; storyId: string; token: string; expiresAt: string; acceptPath: string };

export type StoryInviteResult = { state: 'success' } & StoryInvite | { state: 'forbidden' | 'error'; message: string };

function coauthorFailure(error: unknown): { state: 'forbidden' | 'error'; message: string } {
  if (error instanceof ApiClientError && error.status === 403) return { state: 'forbidden', message: error.message };
  return { state: 'error', message: error instanceof Error ? error.message : '요청을 처리하지 못했어요.' };
}

export async function createStoryInvite(storyId: string, accessToken: string | null): Promise<StoryInviteResult> {
  try {
    const issued = await apiRequest<StoryInviteIssued>(`/api/v1/stories/${encodeURIComponent(storyId)}/invites`, {
      method: 'POST',
      accessToken,
    });
    return { state: 'success', inviteUrl: `${APP_WEB_BASE_URL}/story-invite/${issued.token}`, expiresAt: issued.expiresAt };
  } catch (error) {
    return coauthorFailure(error);
  }
}

// 서버 응답(AcceptStoryInviteResponse): alreadyJoined가 true면 이번 호출로 새로 합류한 게
// 아니다(이미 참여 중 · 만든 사람이 자기 링크를 눌렀다 · 동시에 두 번 눌러 진 쪽) — 셋 다
// 실패가 아니라 성공으로 다룬다(서버가 이미 그렇게 답한다).
export type AcceptStoryInviteOutcome = { state: 'success'; storyId: string; alreadyJoined: boolean } | { state: 'expired' } | { state: 'not-found' } | { state: 'error'; message: string };

export async function acceptStoryInvite(token: string, accessToken: string | null): Promise<AcceptStoryInviteOutcome> {
  try {
    const dto = await apiRequest<{ storyId: string; alreadyJoined: boolean; joinedAt: string | null }>(
      `/api/v1/story-invites/${encodeURIComponent(token)}/accept`,
      { method: 'POST', accessToken },
    );
    return { state: 'success', storyId: dto.storyId, alreadyJoined: dto.alreadyJoined };
  } catch (error) {
    if (error instanceof ApiClientError && error.code === 'STORY_INVITE_EXPIRED') return { state: 'expired' };
    if (error instanceof ApiClientError && (error.code === 'STORY_INVITE_NOT_FOUND' || error.status === 404)) return { state: 'not-found' };
    return { state: 'error', message: error instanceof Error ? error.message : '초대를 처리하지 못했어요.' };
  }
}

export type StoryCoauthor = { userId: string; displayName: string | null; isAuthor: boolean; joinedAt: string };
export type StoryCoauthorsResult = { state: 'success'; coauthors: StoryCoauthor[] } | { state: 'forbidden' | 'error'; message: string };

export async function listStoryCoauthors(storyId: string, accessToken: string | null): Promise<StoryCoauthorsResult> {
  try {
    const dto = await apiRequest<{ coauthors: StoryCoauthor[] }>(`/api/v1/stories/${encodeURIComponent(storyId)}/coauthors`, { accessToken });
    return { state: 'success', coauthors: dto.coauthors };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 404) return { state: 'error', message: error.message };
    return coauthorFailure(error);
  }
}

export type StoryCoauthorActionResult = { state: 'success' } | { state: 'forbidden' | 'not-trip-member' | 'error'; message: string };

// 여행 동행자를 골라 공동 작성자로 넣는다 — 만든 사람만. 서버가 각 사용자를 그 기록의 여행
// 동행자인지 검사한다(STORY_COAUTHOR_NOT_TRIP_MEMBER) — 목록 중 하나라도 아니면 전체 거부다.
export async function addStoryCoauthors(storyId: string, userIds: string[], accessToken: string | null): Promise<StoryCoauthorActionResult> {
  try {
    await apiRequest<void>(`/api/v1/stories/${encodeURIComponent(storyId)}/coauthors`, {
      method: 'POST',
      accessToken,
      body: { userIds },
    });
    return { state: 'success' };
  } catch (error) {
    if (error instanceof ApiClientError && error.code === 'STORY_COAUTHOR_NOT_TRIP_MEMBER') return { state: 'not-trip-member', message: error.message };
    if (error instanceof ApiClientError && error.status === 403) return { state: 'forbidden', message: error.message };
    return { state: 'error', message: error instanceof Error ? error.message : '요청을 처리하지 못했어요.' };
  }
}

// 제거(만든 사람이 남을 뺀다) 또는 나가기(공동 작성자가 자기 자신을 뺀다) — 서버가 같은
// 경로로 둘 다 받는다. 만든 사람 자신은 못 뺀다(그럼 지울 수 있는 사람이 없어진다) — 403.
export async function removeStoryCoauthor(storyId: string, userId: string, accessToken: string | null): Promise<StoryCoauthorActionResult> {
  try {
    await apiRequest<void>(`/api/v1/stories/${encodeURIComponent(storyId)}/coauthors/${encodeURIComponent(userId)}`, {
      method: 'DELETE',
      accessToken,
    });
    return { state: 'success' };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 403) return { state: 'forbidden', message: error.message };
    return { state: 'error', message: error instanceof Error ? error.message : '요청을 처리하지 못했어요.' };
  }
}
