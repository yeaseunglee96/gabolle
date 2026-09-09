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
  place?: { id: string; name: string } | null;
  tripId?: string | null;
  images: Array<{ url: string; position: number }>;
  visibility: StoryVisibility;
  publishAt: string;
  createdAt: string;
  updatedAt: string;
  mine: boolean;
  published: boolean;
};

// 기록 상세 화면(GET /api/v1/stories/:id)은 아직 계약이 없다. 목록에서 이미 받은 전체
// StoryDto를 그대로 들고 있다가 상세 화면이 같은 세션 안에서 그걸 읽게 한다 — 새 API를
// 지어내지 않고, 목록에서 곧장 눌러 들어온 경우를 실제로 지원한다.
const storyCache = new Map<string, StoryDto>();

export function cacheStories(items: StoryDto[]) {
  for (const item of items) storyCache.set(item.id, item);
}

export function getCachedStory(id: string): StoryDto | null {
  return storyCache.get(id) ?? null;
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
