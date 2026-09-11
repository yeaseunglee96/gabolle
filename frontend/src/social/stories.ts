import { Platform } from 'react-native';

import { apiRequest, ApiClientError } from '@/api/client';

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
};

// GET /api/v1/stories/:id 계약이 생기기 전(S15P21E201-228 이전)에는 목록에서 받은
// StoryDto를 캐시해서 상세 화면이 그걸 읽었다. 지금은 실제 상세 조회 API가 있어서
// (getStory) 그걸로 다시 받아오지만, 캐시는 그대로 남긴다 — 목록에서 곧장 눌러
// 들어왔을 때 API 응답을 기다리지 않고 먼저 보여주는 자리표시로 쓴다.
const storyCache = new Map<string, StoryDto>();

export function cacheStories(items: StoryDto[]) {
  for (const item of items) storyCache.set(item.id, item);
}

export function getCachedStory(id: string): StoryDto | null {
  return storyCache.get(id) ?? null;
}

export type StoryLoadResult = { state: 'success'; story: StoryDto } | { state: 'not-found' } | FeedFailure;

export async function getStory(id: string, accessToken: string | null): Promise<StoryLoadResult> {
  try {
    const story = await apiRequest<StoryDto>(`/api/v1/stories/${encodeURIComponent(id)}`, { accessToken });
    storyCache.set(story.id, story);
    return { state: 'success', story };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 404) return { state: 'not-found' };
    return failure(error);
  }
}

export type DeleteStoryResult = { state: 'success' } | FeedFailure;

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

export type FeedLoadResult = { state: 'success'; items: StoryDto[]; nextCursor: string | null } | FeedFailure;

export async function loadFeed(input: { scope: FeedScope; cursor?: string | null; limit?: number; accessToken: string | null }): Promise<FeedLoadResult> {
  try {
    const params = new URLSearchParams({ scope: input.scope });
    if (input.cursor) params.set('cursor', input.cursor);
    params.set('limit', String(input.limit ?? 20));
    const dto = await apiRequest<{ items: StoryDto[]; nextCursor: string | null }>(`/api/v1/stories?${params.toString()}`, { accessToken: input.accessToken });
    cacheStories(dto.items);
    return { state: 'success', items: dto.items, nextCursor: dto.nextCursor };
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
  accessToken: string | null;
}): Promise<StoryMutationResult> {
  try {
    const story = await apiRequest<StoryDto>('/api/v1/stories', {
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
      },
    });
    return { state: 'success', story };
  } catch (error) {
    return failure(error);
  }
}

export type ImagePickResult = { uri: string; fileName?: string | null; mimeType?: string | null };
export type ImageUploadResult = { state: 'success'; imageUrl: string } | FeedFailure;

export async function uploadStoryImage(asset: ImagePickResult, accessToken: string | null): Promise<ImageUploadResult> {
  try {
    const formData = new FormData();
    const name = asset.fileName ?? `story-${Date.now()}.jpg`;
    const type = asset.mimeType ?? 'image/jpeg';
    if (Platform.OS === 'web') {
      const blob = await (await fetch(asset.uri)).blob();
      formData.append('file', blob, name);
    } else {
      // React Native의 FormData는 { uri, name, type } 형태를 파일로 받는다 (web의 File/Blob과 다르다).
      formData.append('file', { uri: asset.uri, name, type } as unknown as Blob);
    }
    const dto = await apiRequest<{ imageId: string; imageUrl: string; contentType: string; byteSize: number }>('/api/v1/uploads/story-image', {
      method: 'POST',
      accessToken,
      body: formData,
    });
    return { state: 'success', imageUrl: dto.imageUrl };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 413) return { state: 'error', message: '사진이 너무 커요. 3MB 이하로 올려주세요.' };
    if (error instanceof ApiClientError && error.status === 415) return { state: 'error', message: 'JPEG, PNG, WebP 사진만 올릴 수 있어요.' };
    return failure(error);
  }
}

// jaehyeon 님 계약(2026-09-08 axmap): GET /api/v1/users/{userId}/profile. following은 서버가
// "요청자가 이 사람을 팔로우 중인가"를 판정해 주므로 화면에서 따로 물어보지 않는다.
export type UserProfileDto = {
  userId: string;
  displayName: string;
  followerCount: number;
  followingCount: number;
  storyCount: number;
  following: boolean;
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
    cacheStories(dto.items);
    return { state: 'success', items: dto.items, nextCursor: dto.nextCursor };
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
    return { state: 'success', items: dto.items };
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
