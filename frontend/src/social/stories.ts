import { apiRequest, ApiClientError, ApiUnavailableError, API_BASE_URL, APP_WEB_BASE_URL } from '@/api/client';
import { singleFileFormData } from '@/api/multipart';

export type StoryVisibility = 'PUBLIC' | 'FOLLOWERS' | 'PRIVATE';
export type FeedScope = 'ALL' | 'FOLLOWING';

export const VISIBILITY_LABEL: Record<StoryVisibility, [string, string]> = {
  PUBLIC: ['전체 공개', 'Public'],
  FOLLOWERS: ['팔로워 공개', 'Followers only'],
  PRIVATE: ['나만 보기', 'Only me'],
};

export type StoryDto = {
  id: string;
  author: { id: string; displayName: string };
  body: string;
  region?: string | null;
  place?: { id: string; name: string; lat: number | null; lng: number | null } | null;
  tripId?: string | null;
  images: Array<{ url: string; position: number }>;
  visibility: StoryVisibility;
  publishAt: string;
  createdAt: string;
  updatedAt: string;
  mine: boolean;
  published: boolean;
  /** 댓글이면 부모 글의 id, 원글이면 null —. */
  parentId?: string | null;
  /** 이 글에 직접 달린 댓글 수. 손자는 안 센다 — 서버 주석과 같은 규칙이다. */
  replyCount?: number;
  /** 이 글을 연 횟수 —. */
  viewCount?: number;
  /** 이 글의 링크를 복사한 횟수. */
  linkCopyCount?: number;
  /** 좋아요·싫어요 —(kojh0124 님, MR !1145). */
  likeCount?: number;
  dislikeCount?: number;
  /**
   * 세 값이다 — `null`(안 누름) · `"LIKE"` · `"DISLIKE"`. `!myReaction`로 한 번에
   * 묶으면 싫어요 상태가 "안 누름"으로 보인다 — kojh0124 님 경고 그대로다.
   */
  myReaction?: 'LIKE' | 'DISLIKE' | null;
};

// GET /api/v1/stories/:id 계약이 생기기 전이전)에는 목록에서 받은
// StoryDto를 캐시해서 상세 화면이 그걸 읽었다. 지금은 실제 상세 조회 API가 있어서
// (getStory) 그걸로 다시 받아오지만, 캐시는 그대로 남긴다 — 목록에서 곧장 눌러
// 들어왔을 때 API 응답을 기다리지 않고 먼저 보여주는 자리표시로 쓴다.
const storyCache = new Map<string, StoryDto>();

export function resolveStoryImageUrl(url: string) {
  if (/^(https?:|data:|blob:|file:)/i.test(url)) return url;
  return `${API_BASE_URL}${url.startsWith('/') ? '' : '/'}${url}`;
}

function withDisplayImageUrls(story: StoryDto): StoryDto {
  return { ...story, images: story.images.map((image) => ({ ...image, url: resolveStoryImageUrl(image.url) })) };
}

/** 글에 붙일 지표 문구들 —. */
export function storyMetricLabels(
  story: Pick<StoryDto, 'viewCount' | 'linkCopyCount'>,
  tx: (ko: string, en: string) => string,
): string[] {
  const labels: string[] = [];
  if (typeof story.viewCount === 'number') {
    labels.push(tx(`조회 ${story.viewCount}`, `${story.viewCount} views`));
  }
  if (typeof story.linkCopyCount === 'number') {
    labels.push(tx(`인용 ${story.linkCopyCount}`, `${story.linkCopyCount} quotes`));
  }
  return labels;
}

export function cacheStories(items: StoryDto[]) {
  for (const item of items) storyCache.set(item.id, withDisplayImageUrls(item));
}

export function getCachedStory(id: string): StoryDto | null {
  return storyCache.get(id) ?? null;
}

export type StoryRepliesResult = { state: 'success'; replies: StoryDto[] } | FeedFailure;

export type StoryLoadResult = { state: 'success'; story: StoryDto } | { state: 'not-found' } | FeedFailure;

export async function getStory(id: string, accessToken: string | null): Promise<StoryLoadResult> {
  try {
    const story = withDisplayImageUrls(await apiRequest<StoryDto>(`/api/v1/stories/${encodeURIComponent(id)}`, { accessToken }));
    storyCache.set(story.id, story);
    return { state: 'success', story };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 404) return { state: 'not-found' };
    return failure(error);
  }
}

export type DeleteStoryResult = { state: 'success' } | FeedFailure;

/** 이 글에 직접 달린 댓글 —(서버는. */
export async function getStoryReplies(storyId: string, accessToken: string | null): Promise<StoryRepliesResult> {
  try {
    const replies = await apiRequest<StoryDto[]>(`/api/v1/stories/${encodeURIComponent(storyId)}/replies`, { accessToken });
    return { state: 'success', replies: replies.map(withDisplayImageUrls) };
  } catch (error) {
    return failure(error);
  }
}

/** 글(또는 댓글 — 같은 표라 같은 경로다) 본문을 고친다 — PATCH /api/v1/stories/{id}. */
export async function updateStory(id: string, body: string, accessToken: string | null): Promise<StoryMutationResult> {
  try {
    const story = withDisplayImageUrls(await apiRequest<StoryDto>(`/api/v1/stories/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      accessToken,
      body: { body },
    }));
    storyCache.set(story.id, story);
    return { state: 'success', story };
  } catch (error) {
    return failure(error);
  }
}

export async function deleteStory(id: string, accessToken: string | null): Promise<DeleteStoryResult> {
  try {
    await apiRequest<void>(`/api/v1/stories/${encodeURIComponent(id)}`, { method: 'DELETE', accessToken });
    storyCache.delete(id);
    return { state: 'success' };
  } catch (error) {
    return failure(error);
  }
}

export function relativeStoryTime(iso: string, tx: (ko: string, en: string) => string) {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  const minutes = Math.round((Date.now() - date.getTime()) / 60000);
  if (minutes < 1) return tx('방금', 'just now');
  if (minutes < 60) return tx(`${minutes}분 전`, `${minutes}m ago`);
  const hours = Math.round(minutes / 60);
  if (hours < 24) return tx(`${hours}시간 전`, `${hours}h ago`);
  const days = Math.round(hours / 24);
  if (days < 7) return tx(`${days}일 전`, `${days}d ago`);
  return `${date.getFullYear()}.${date.getMonth() + 1}.${date.getDate()}`;
}

type FeedFailure = { state: 'unavailable' | 'offline' | 'error'; message: string };

function failure(error: unknown): FeedFailure {
  if (error instanceof ApiClientError && error.code === 'NETWORK_ERROR') return { state: 'offline', message: error.message };
  if (error instanceof ApiClientError && error.code === 'INVALID_RESPONSE') return { state: 'unavailable', message: '기록 피드 API가 아직 준비되지 않았어요.' };
  return { state: 'error', message: error instanceof Error ? error.message : '요청을 처리하지 못했어요.' };
}

/** 피드를 보관소(react-query)에서 찾는 열쇠 —. */
export const FEED_QUERY_PREFIX = ['feed'] as const;
export const feedQueryKey = (scope: FeedScope, signedIn: boolean) =>
  [...FEED_QUERY_PREFIX, scope, signedIn] as const;
export type FeedLoadResult = { state: 'success'; items: StoryDto[]; nextCursor: string | null } | FeedFailure;

export async function loadFeed(input: { scope: FeedScope; cursor?: string | null; limit?: number; accessToken: string | null }): Promise<FeedLoadResult> {
  try {
    const params = new URLSearchParams({ scope: input.scope });
    if (input.cursor) params.set('cursor', input.cursor);
    params.set('limit', String(input.limit ?? 20));
    const dto = await apiRequest<{ items: StoryDto[]; nextCursor: string | null }>(`/api/v1/stories?${params.toString()}`, { accessToken: input.accessToken });
    const items = dto.items.map(withDisplayImageUrls);
    cacheStories(items);
    return { state: 'success', items, nextCursor: dto.nextCursor };
  } catch (error) {
    return failure(error);
  }
}

export type StoryMutationResult = { state: 'success'; story: StoryDto } | FeedFailure;

export async function createStory(input: {
  body: string;
  imageUrls: string[];
  region?: string;
  visibility?: StoryVisibility;
  placeId?: string;
  tripId?: string;
  // 없으면 서버가 "여행 종료 다음 날 0시, 여행도 없으면 지금"으로 정한다.
  // "지금 바로 공개"를 고른 경우에만 현재 시각을 실어 보낸다.
  publishAt?: string;
  /**
   * 있으면 이 글의 댓글로 들어간다 —.
   * 없으면 지금까지처럼 원글이다. 서버의 StoryCreateRequest 가 같은 이름의 칸을 받는다.
   */
  parentStoryId?: string;
  accessToken: string | null;
}): Promise<StoryMutationResult> {
  try {
    const story = withDisplayImageUrls(await apiRequest<StoryDto>('/api/v1/stories', {
      method: 'POST',
      accessToken: input.accessToken,
      body: {
        body: input.body,
        imageUrls: input.imageUrls,
        region: input.region || undefined,
        visibility: input.visibility,
        placeId: input.placeId,
        tripId: input.tripId,
        publishAt: input.publishAt,
        parentStoryId: input.parentStoryId,
      },
    }));
    return { state: 'success', story };
  } catch (error) {
    return failure(error);
  }
}

export type ImagePickResult = { uri: string; fileName?: string | null; mimeType?: string | null };
export type ImageUploadResult = { state: 'success'; imageUrl: string } | FeedFailure;

export type VideoUploadResult = { state: 'success'; videoUrl: string; byteSize: number } | FeedFailure;

/**
 * 동영상을 올린다 — POST /api/v1/uploads/story-video.
 *
 * 🔴 여기까지가 「파일이 올라갔고 주소는 이것이다」다. 그 주소를 기록에 붙이는 것은
 *    다음 단계이고, 그 계약은 아직 안 나갔다(서버 VideoUploadResponse 머리말과 같은 말).
 *
 * `durationSec` 은 **앱이 잰 값**이다. 서버는 파일을 열지 않으므로 확인하지 않고 그대로
 * 돌려준다. 못 쟀으면 안 보낸다 — 0 을 지어내면 「0초짜리 영상」이 된다.
 */
export async function uploadStoryVideo(
  asset: { uri: string; fileName?: string | null; mimeType?: string | null; durationSec?: number | null },
  accessToken: string | null,
): Promise<VideoUploadResult> {
  try {
    const name = asset.fileName ?? `story-${Date.now()}.mp4`;
    const type = asset.mimeType ?? 'video/mp4';
    const formData = await singleFileFormData('file', { uri: asset.uri, name, type });
    const query = typeof asset.durationSec === 'number' ? `?durationSec=${Math.round(asset.durationSec)}` : '';
    const dto = await apiRequest<{ videoId: string; videoUrl: string; contentType: string; byteSize: number; durationSec?: number }>(
      `/api/v1/uploads/story-video${query}`,
      { method: 'POST', accessToken, body: formData },
    );
    return { state: 'success', videoUrl: dto.videoUrl, byteSize: dto.byteSize };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 413) return { state: 'error', message: '동영상이 너무 커요. 더 짧은 영상으로 올려주세요.' };
    if (error instanceof ApiClientError && error.status === 415) return { state: 'error', message: 'mp4 동영상만 올릴 수 있어요.' };
    if (error instanceof ApiUnavailableError && error.cause) return { state: 'offline', message: `${error.message} (${error.cause})` };
    return failure(error);
  }
}

export async function uploadStoryImage(asset: ImagePickResult, accessToken: string | null): Promise<ImageUploadResult> {
  try {
    const name = asset.fileName ?? `story-${Date.now()}.jpg`;
    const type = asset.mimeType ?? 'image/jpeg';
    // — 보내기 전에 파일을 Blob 으로 바꿔야 한다.
    // 왜 그래야 하는지는 src/api/multipart.ts 에 적혀 있다.
    const formData = await singleFileFormData('file', { uri: asset.uri, name, type });
    const dto = await apiRequest<{ imageId: string; imageUrl: string; contentType: string; byteSize: number }>('/api/v1/uploads/story-image', {
      method: 'POST',
      accessToken,
      body: formData,
    });
    return { state: 'success', imageUrl: dto.imageUrl };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 413) return { state: 'error', message: '사진이 너무 커요. 3MB 이하로 올려주세요.' };
    if (error instanceof ApiClientError && error.status === 415) return { state: 'error', message: 'JPEG, PNG, WebP 사진만 올릴 수 있어요.' };
    // — 사진이 안 올라갈 때는 왜인지를 화면에 붙인다.
    if (error instanceof ApiUnavailableError && error.cause) {
      return { state: 'offline', message: `${error.message} (${error.cause})` };
    }
    return failure(error);
  }
}

export type UserProfileDto = {
  userId: string;
  displayName: string;
  followerCount: number;
  followingCount: number;
  storyCount: number;
  following: boolean;
  blocked?: boolean;
  blockedByUser?: boolean;
};

export type ProfileLoadResult = { state: 'success'; profile: UserProfileDto } | FeedFailure;

export async function getUserProfile(userId: string, accessToken: string | null): Promise<ProfileLoadResult> {
  try {
    const profile = await apiRequest<UserProfileDto>(`/api/v1/users/${encodeURIComponent(userId)}/profile`, { accessToken });
    return { state: 'success', profile };
  } catch (error) {
    return failure(error);
  }
}

export async function loadUserStories(userId: string, accessToken: string | null, cursor?: string | null): Promise<FeedLoadResult> {
  try {
    const params = new URLSearchParams();
    if (cursor) params.set('cursor', cursor);
    const query = params.toString();
    const dto = await apiRequest<{ items: StoryDto[]; nextCursor: string | null }>(`/api/v1/users/${encodeURIComponent(userId)}/stories${query ? `?${query}` : ''}`, { accessToken });
    const items = dto.items.map(withDisplayImageUrls);
    cacheStories(items);
    return { state: 'success', items, nextCursor: dto.nextCursor };
  } catch (error) {
    return failure(error);
  }
}

export type TripStoriesResult = { state: 'success'; items: StoryDto[] } | { state: 'not-found' } | FeedFailure;

export async function getTripStories(tripId: string, accessToken: string | null): Promise<TripStoriesResult> {
  try {
    const dto = await apiRequest<{ items: StoryDto[] }>(`/api/v1/trips/${encodeURIComponent(tripId)}/stories`, { accessToken });
    return { state: 'success', items: dto.items.map(withDisplayImageUrls) };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 404) return { state: 'not-found' };
    return failure(error);
  }
}

// jaehyeon 님 계약: POST /api/v1/stories/{storyId}/reports.
// 처음 신고든 같은 사람의 중복 신고든 서버는 항상 204를 준다 — "이미 신고했습니다" 같은
// 오류로 갈라 보여주지 않는다(신고 여부가 새어 나가지 않게 하려는 의도). 그래서 화면도
// 성공/실패만 가르고, 신고를 받으면 서버가 그 자리에서 글을 검토 대기로 옮겨 즉시
// 비노출하므로 화면에서는 카드를 낙관적으로 지우기만 하면 된다.
export type StoryReportReason = 'PRIVACY' | 'OFFENSIVE' | 'SPAM' | 'OTHER';

export const REPORT_REASON_LABEL: Record<StoryReportReason, [string, string]> = {
  PRIVACY: ['개인정보 노출', 'Personal information exposed'],
  OFFENSIVE: ['불쾌한 내용', 'Offensive content'],
  SPAM: ['스팸', 'Spam'],
  OTHER: ['기타', 'Other'],
};

export type ReportResult = { state: 'success' } | FeedFailure;

export async function reportStory(storyId: string, reason: StoryReportReason, detail: string | undefined, accessToken: string | null): Promise<ReportResult> {
  try {
    await apiRequest<void>(`/api/v1/stories/${encodeURIComponent(storyId)}/reports`, {
      method: 'POST',
      accessToken,
      body: { reason, detail: reason === 'OTHER' ? detail : undefined },
    });
    return { state: 'success' };
  } catch (error) {
    return failure(error);
  }
}

/**
 * 기록의 공개 주소. 복사와 공유가 같은 값을 쓰도록 한 자리에 둔다 — 두 곳에서 따로
 * 조립하면 한쪽만 고쳐졌을 때 복사한 링크와 실제로 열리는 링크가 달라진다.
 */
export function storyShareUrl(storyId: string) {
  return `${APP_WEB_BASE_URL}/feed/${encodeURIComponent(storyId)}`;
}

export type LinkCopyResult = { state: 'success'; story: StoryDto } | FeedFailure;

/**
 * 링크를 복사했다고 서버에 알린다. 세는 쪽은 S15P21E201-1215 에 이미 들어가 있다.
 *
 * 🔴 이 요청은 204 가 아니라 200 에 글 전체를 돌려준다. 그래서 누른 뒤 상세를 다시
 * 부르면 안 된다 — 그 호출이 조회수를 올려서 「복사한 것」이 「본 것」으로 세어진다.
 * 돌아온 글을 그대로 그린다.
 *
 * 🔴 수가 안 올라가도 200 이다 — 오늘 이미 센 사람이 또 눌렀거나 작성자 본인일 때다.
 * 실패가 아니므로 오류로 그리지 않는다.
 */
export async function recordStoryLinkCopy(storyId: string, accessToken: string | null): Promise<LinkCopyResult> {
  try {
    const story = withDisplayImageUrls(
      await apiRequest<StoryDto>(`/api/v1/stories/${encodeURIComponent(storyId)}/link-copies`, {
        method: 'POST',
        accessToken,
      }),
    );
    storyCache.set(story.id, story);
    return { state: 'success', story };
  } catch (error) {
    return failure(error);
  }
}

export type FollowResult = { state: 'success'; following: boolean; followerCount: number; followingCount: number } | FeedFailure;

export async function setFollowing(userId: string, following: boolean, accessToken: string | null): Promise<FollowResult> {
  try {
    const dto = await apiRequest<{ userId: string; following: boolean; followerCount: number; followingCount: number }>(
      `/api/v1/users/${encodeURIComponent(userId)}/follow`,
      { method: following ? 'PUT' : 'DELETE', accessToken },
    );
    return { state: 'success', following: dto.following, followerCount: dto.followerCount, followingCount: dto.followingCount };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 400 && error.code === 'FOLLOW_SELF') return { state: 'error', message: '자기 자신은 팔로우할 수 없어요.' };
    return failure(error);
  }
}

// 차단 —(서버)·-991(화면). 차단은 「내가 이 사람을 안 본다」가 아니라
// 「이 사람에게 내 것을 안 보여준다」다. 그래서 차단한 쪽 화면에서는 상대가 그대로 보이고
// 거르는 일은 전부 서버가 한다 — 프론트는 목록에서 아무것도 빼지 않는다.
export type BlockResult = { state: 'success'; blocked: boolean } | FeedFailure;

export async function setBlocked(userId: string, blocked: boolean, accessToken: string | null): Promise<BlockResult> {
  try {
    const dto = await apiRequest<{ userId: string; blocked: boolean }>(
      `/api/v1/users/${encodeURIComponent(userId)}/block`,
      { method: blocked ? 'PUT' : 'DELETE', accessToken },
    );
    return { state: 'success', blocked: dto.blocked };
  } catch (error) {
    return failure(error);
  }
}

// 팔로워·팔로잉·차단 목록 —(서버)·-1180/-1181(화면).
export type RelationItem = { userId: string; displayName: string; avatarUrl?: string; following: boolean };
export type RelationListResult = { state: 'success'; items: RelationItem[]; nextCursor: string | null } | FeedFailure;

async function loadRelationList(path: string, accessToken: string | null, cursor?: string | null): Promise<RelationListResult> {
  try {
    const params = new URLSearchParams();
    if (cursor) params.set('cursor', cursor);
    const query = params.toString();
    const dto = await apiRequest<{ items: RelationItem[]; nextCursor: string | null }>(`${path}${query ? `?${query}` : ''}`, { accessToken });
    return { state: 'success', items: dto.items, nextCursor: dto.nextCursor };
  } catch (error) {
    return failure(error);
  }
}

/** 이 사람을 팔로우하는 사람들. */
export function loadFollowers(userId: string, accessToken: string | null, cursor?: string | null): Promise<RelationListResult> {
  return loadRelationList(`/api/v1/users/${encodeURIComponent(userId)}/followers`, accessToken, cursor);
}

/** 이 사람이 팔로우하는 사람들. */
export function loadFollowing(userId: string, accessToken: string | null, cursor?: string | null): Promise<RelationListResult> {
  return loadRelationList(`/api/v1/users/${encodeURIComponent(userId)}/following`, accessToken, cursor);
}

/** 내가 차단한 사람들 — userId는 반드시 본인이어야 한다(서버가 아니면 403). */
export function loadMyBlocks(userId: string, accessToken: string | null, cursor?: string | null): Promise<RelationListResult> {
  return loadRelationList(`/api/v1/users/${encodeURIComponent(userId)}/blocks`, accessToken, cursor);
}

// 기록(글) 저장(북마크) — 사용자 리포트: "마이페이지에 저장 누르면 저장했던 피드들 뜨게".
export type SavedStoryIdsResult = { state: 'success'; ids: Set<string> } | FeedFailure;

export async function loadSavedStoryIds(accessToken: string | null): Promise<SavedStoryIdsResult> {
  try {
    const dto = await apiRequest<{ items: Array<{ storyId: string }> }>('/api/v1/me/saved-stories', { accessToken });
    return { state: 'success', ids: new Set(dto.items.map((item) => item.storyId)) };
  } catch (error) {
    return failure(error);
  }
}

/** 저장한 기록 전부 — 마이페이지 "저장한 기록" 화면이 목록을 그릴 때 쓴다. */
export async function loadSavedStories(accessToken: string | null): Promise<FeedLoadResult> {
  try {
    const dto = await apiRequest<{ items: Array<{ storyId: string }> }>('/api/v1/me/saved-stories', { accessToken });
    // 저장 목록 응답에는 글 내용이 없다(StorySaveResponse — 장소 저장 목록과 같은 이유
    // 두 곳에 같은 값을 들면 한쪽이 낡는다). 아이디마다 상세를 불러 카드로 그릴 내용을 채운다.
    const stories = await Promise.all(dto.items.map((item) => getStory(item.storyId, accessToken)));
    const items = stories
      .filter((result): result is { state: 'success'; story: StoryDto } => result.state === 'success')
      .map((result) => result.story);
    return { state: 'success', items, nextCursor: null };
  } catch (error) {
    return failure(error);
  }
}

export type StorySaveResult = { state: 'success'; saved: boolean } | FeedFailure;

export async function setStorySaved(storyId: string, saved: boolean, accessToken: string | null): Promise<StorySaveResult> {
  try {
    await apiRequest<void>(`/api/v1/stories/${encodeURIComponent(storyId)}/save`, {
      method: saved ? 'PUT' : 'DELETE',
      accessToken,
    });
    return { state: 'success', saved };
  } catch (error) {
    return failure(error);
  }
}

// 좋아요·싫어요 —(서버, kojh0124 님)·화면은 여기.
export type StoryReactionResult = { state: 'success' } | FeedFailure;

export async function setStoryReaction(
  storyId: string,
  reaction: 'LIKE' | 'DISLIKE' | null,
  accessToken: string | null,
): Promise<StoryReactionResult> {
  try {
    if (reaction === null) {
      await apiRequest<void>(`/api/v1/stories/${encodeURIComponent(storyId)}/reaction`, { method: 'DELETE', accessToken });
    } else {
      await apiRequest<void>(`/api/v1/stories/${encodeURIComponent(storyId)}/reaction`, {
        method: 'PUT',
        accessToken,
        body: { reaction },
      });
    }
    return { state: 'success' };
  } catch (error) {
    return failure(error);
  }
}
