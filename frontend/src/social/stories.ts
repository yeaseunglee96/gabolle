import { apiRequest, ApiClientError, ApiUnavailableError, API_BASE_URL } from '@/api/client';
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
  // lat·lng는 S15P21E201-829(추억 지도) 계약으로 /stories · /stories/{id} · 이 아래
  // getTripStories 셋에 함께 붙었다(jaehyeon 님, 2026-09-11). 좌표 없는 장소는 null이다 —
  // 마커를 안 찍으면 된다(지어내지 않는다).
  place?: { id: string; name: string; lat: number | null; lng: number | null } | null;
  tripId?: string | null;
  images: Array<{ url: string; position: number }>;
  visibility: StoryVisibility;
  publishAt: string;
  createdAt: string;
  updatedAt: string;
  mine: boolean;
  published: boolean;
  /**
   * 댓글이면 부모 글의 id, 원글이면 null — S15P21E201-1183.
   *
   * 🔴 댓글은 별도 타입이 아니라 **같은 StoryDto** 다. 시안이 「별도 Comment 타입을 만들지
   * 말 것」을 못박았고 서버도 같은 표에 부모 칸으로 갔다. 그래서 화면이 원글과 댓글을
   * **같은 부품**으로 그린다.
   */
  parentId?: string | null;
  /** 이 글에 **직접** 달린 댓글 수. 손자는 안 센다 — 서버 주석과 같은 규칙이다. */
  replyCount?: number;
  /**
   * 이 글을 연 횟수 — S15P21E201-1213.
   *
   * 서버가 세는 규칙: **사람 × 글 × 하루 한 번** · **작성자 본인은 안 셈** · 비회원은 셈.
   * 값이 없으면 칸 자체가 안 온다(서버가 아직 안 주는 판일 때).
   */
  viewCount?: number;
  /**
   * 이 글의 링크를 복사한 횟수.
   *
   * 🔴 화면에 보이는 말은 「인용」이어도 되지만 **코드에서 `quoteCount` 로 부르지 않는다.**
   * 우리가 아는 것은 「복사 버튼을 눌렀다」뿐이고, 서버 표 이름도 그렇게 정했다.
   */
  linkCopyCount?: number;
  /**
   * 좋아요·싫어요 — S15P21E201-1174(kojh0124 님, MR !1145).
   *
   * 없으면 0(칸이 안 오는 게 아니라 값이 0) — 취소한 사람은 수에서 빠진다.
   */
  likeCount?: number;
  dislikeCount?: number;
  /**
   * 🔴 세 값이다 — `null`(안 누름) · `"LIKE"` · `"DISLIKE"`. `!myReaction`로 한 번에
   * 묶으면 싫어요 상태가 "안 누름"으로 보인다 — kojh0124 님 경고 그대로다.
   */
  myReaction?: 'LIKE' | 'DISLIKE' | null;
};

// GET /api/v1/stories/:id 계약이 생기기 전(S15P21E201-228 이전)에는 목록에서 받은
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

/**
 * 글에 붙일 지표 문구들 — S15P21E201-1213.
 *
 * 🔴 **서버가 준 것만 말한다.** 칸이 안 오면 그 지표는 아예 안 그린다. 0 을 지어내 그리면
 * 「아무도 안 봤다」는 **주장**이 되는데, 실제로는 **서버가 아직 안 세는 것**일 수 있다.
 * 모르는 것과 0 은 다르다 — 이 저장소가 여러 번 데인 자리다.
 *
 * 🔴 그래서 `if (story.viewCount)` 가 아니라 **`typeof === 'number'`** 로 본다.
 * 앞의 방식은 **진짜 0 을 없는 것으로 삼킨다.**
 *
 * 좋아요는 여기 없다 — 서버 칸 이름을 아직 못 받았다. 칸이 오는 날 한 줄 더하면 되고,
 * 그때까지 **없는 것을 있는 척하지 않는다.**
 */
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

/**
 * 이 글에 **직접** 달린 댓글 — S15P21E201-1197 (서버는 S15P21E201-1183).
 *
 * 🔴 손자는 안 딸려 온다. 어떤 댓글의 답글을 보려면 **그 댓글의 id 로 이 함수를 다시**
 * 부른다. 서버가 그렇게 정했고 이유도 적어 뒀다 — 한 번에 전부 내려주면 깊은 가지 하나
 * 때문에 응답이 통째로 커진다.
 *
 * 응답이 원글과 **같은 모양(StoryDto)** 이라 화면이 같은 부품으로 그린다.
 *
 * 🔴 실패를 조용히 빈 목록으로 바꾸지 않는다. 「댓글이 없다」와 「못 불러왔다」는 다른
 * 말이고, 둘을 같게 그리면 사용자가 없는 것으로 믿는다.
 */
export async function getStoryReplies(storyId: string, accessToken: string | null): Promise<StoryRepliesResult> {
  try {
    const replies = await apiRequest<StoryDto[]>(`/api/v1/stories/${encodeURIComponent(storyId)}/replies`, { accessToken });
    return { state: 'success', replies: replies.map(withDisplayImageUrls) };
  } catch (error) {
    return failure(error);
  }
}

/**
 * 글(또는 댓글 — 같은 표라 같은 경로다) 본문을 고친다 — PATCH /api/v1/stories/{id}.
 *
 * 🔴 `body` 만 보낸다. `visibility`·`publishAt` 은 만든 사람만 바꿀 수 있고(서버가
 * `StoryForbiddenException` 으로 막는다), 댓글에는 그 칸을 보여줄 화면 자체가 없다 —
 * 여기서 아예 안 보내는 것이 실수로 지어내 보내는 것보다 안전하다.
 */
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

/**
 * 피드를 보관소(react-query)에서 찾는 열쇠 — S15P21E201-1124.
 *
 * <p>🔴 이 열쇠가 화면 파일 안에 있던 동안, 글쓰기 화면은 올린 뒤 무엇을 버려야
 * 하는지 알 수 없었다. 그래서 목록이 그대로였고, 사용자는 안 올라간 줄 알고 같은
 * 글을 한 번 더 올렸다(2026-09-16 iOS 실기기에서 실제로 201 이 두 번 찍혔다).
 * 두 곳이 같은 것을 가리켜야 하는 값은 두 곳 중 하나에 두면 반드시 어긋난다.
 *
 * <p>범위(전체·팔로잉)마다 따로 담는다. 토큰이 아니라 "로그인했는가" 만 넣는다 —
 * 토큰은 갱신될 때마다 값이 바뀌어서, 넣으면 로그인 상태가 그대로인데도 보관한
 * 것을 버리고 다시 부르게 된다(S15P21E201-957).
 */
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
   * 있으면 이 글의 **댓글**로 들어간다 — S15P21E201-1197.
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

export async function uploadStoryImage(asset: ImagePickResult, accessToken: string | null): Promise<ImageUploadResult> {
  try {
    const name = asset.fileName ?? `story-${Date.now()}.jpg`;
    const type = asset.mimeType ?? 'image/jpeg';
    // 🔴 S15P21E201-1187 — 보내기 전에 파일을 Blob 으로 바꿔야 한다.
    //    왜 그래야 하는지는 src/api/multipart.ts 에 적혀 있다.
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
    // 🔴 S15P21E201-1187 — 사진이 안 올라갈 때는 **왜인지를 화면에 붙인다.**
    //
    // 다른 화면에서 「서버에 연결할 수 없어요」는 그것만으로 충분하다 — 사용자가 할 일은
    // 기다리는 것뿐이다. 그런데 사진 업로드가 100% 실패하는 상태에서는 그 말이 거짓이다.
    // 서버는 멀쩡하고(같은 순간 다른 요청은 다 200) 요청이 나가지도 못한 것이다.
    //
    // 이 자리는 **사용자가 제보를 남기는 자리**이기도 하다. 원인 한 줄이 화면에 있으면
    // 그 한 줄이 그대로 제보가 된다. 없으면 서버 기록에도 안 남아 아무도 못 고친다.
    if (error instanceof ApiUnavailableError && error.cause) {
      return { state: 'offline', message: `${error.message} (${error.cause})` };
    }
    return failure(error);
  }
}

// jaehyeon 님 계약(2026-09-08 axmap): GET /api/v1/users/{userId}/profile. following은 서버가
// "요청자가 이 사람을 팔로우 중인가"를 판정해 주므로 화면에서 따로 물어보지 않는다.
// blocked 와 blockedByUser 는 서로 다른 값이다 (S15P21E201-990/-991) — A 가 B 를 차단해도
// B 는 A 를 차단하지 않은 상태일 수 있다. 하나로 합치면 그 경우를 못 가른다.
// blocked: 내가 이 사람을 차단했나 → 버튼이 「차단하기」인지 「차단 해제」인지를 정한다.
// blockedByUser: 이 사람이 나를 차단했나 → 화면이 「차단되어 볼 수 없습니다」를 띄운다.
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

// GET /api/v1/users/{userId}/stories — 홈 피드(/api/v1/stories)와 같은 StoryDto 모양이라
// 카드 렌더링을 그대로 재사용할 수 있다(jaehyeon 님 2026-09-08).
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

// jaehyeon 님 계약(S15P21E201-829, 2026-09-11): GET /api/v1/trips/{tripId}/stories.
// 쓴 순서(created_at, 오래된 것부터)로 온다 — publishAt이 아니다(한 여행의 기록은
// publishAt 기본값이 전부 "여행 종료 다음 날 0시"로 같아서 그걸로 정렬하면 순서가
// 사실상 무작위가 된다). 이어 보기 칸이 없다 — 한 여행의 기록 수는 상한 200으로 한 번에
// 온다. 참여자가 아니면 404 TRIP_NOT_FOUND(그 여행이 있는지조차 알려주지 않으려고 403이
// 아니다), 기록이 없으면 404가 아니라 빈 items다.
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

// jaehyeon 님 계약(S15P21E201-254): POST /api/v1/stories/{storyId}/reports.
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

// 차단 — S15P21E201-990(서버)·-991(화면). 차단은 「내가 이 사람을 안 본다」가 아니라
// 「이 사람에게 내 것을 안 보여준다」다. 그래서 차단한 쪽 화면에서는 상대가 그대로 보이고,
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

// 팔로워·팔로잉·차단 목록 — S15P21E201-1179(서버)·-1180/-1181(화면).
//
// 🔴 following은 목록 주인이 아니라 "지금 보는 사람"(로그인한 나) 기준이다 — 서버 계약이
// 그렇다(RelationItemResponse.following 문서 참고). 남의 팔로워 목록을 보면서도 내 팔로우
// 버튼 상태가 맞게 나오는 이유가 이것이다.
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
//
// 🔴 story_reaction(좋아요/싫어요)과는 별도 서버 표다 — 한 글에 반응과 저장을 동시에 가질
// 수 있다. StoryDto에는 "내가 저장했는가" 칸이 없다(반응 카운트와 달리 얹지 않기로
// 했다 — StoryResponseAssembler를 다른 작업과 동시에 건드리지 않으려는 것). 그래서 화면은
// loadSavedStoryIds()로 받은 저장 id 집합을 따로 들고 다니며 카드마다 대조한다.
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
    // 🔴 저장 목록 응답에는 글 내용이 없다(StorySaveResponse — 장소 저장 목록과 같은 이유,
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

// 좋아요·싫어요 — S15P21E201-1174(서버, kojh0124 님)·화면은 여기.
//
// PUT .../reaction {"reaction":"LIKE"|"DISLIKE"} 로 걸고, 끄는 것은 DELETE다 — 같은
// 값을 다시 PUT 해도 안 꺼진다(그건 "좋아요를 안다시 좋아요"가 아니라 그대로 좋아요다).
// 토글(같은 걸 다시 누르면 꺼짐)은 화면이 지금 상태(myReaction)를 보고 PUT/DELETE 중
// 무엇을 부를지 정한다.
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
