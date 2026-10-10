// 현지인 장소 제안 — S15P21E201-2031. 서버 계약은 backend/.../placesuggestion/presentation 그대로다.
//
// 🔴 제안은 바로 장소가 되지 않는다. 운영자가 승인해야 추천에 쓰이는 장소가 되고, 그때 장소 화면에 「현지인 추천」이 붙는다.
//    그래서 보낸 뒤의 말은 「추가됐어요」가 아니라 「검토 뒤에 올라가요」다.
import { apiRequest, ApiClientError, ApiUnavailableError } from '@/api/client';

/** 사람이 고를 수 있는 갈래 — 서버 PlaceSuggestionService.CATEGORIES 와 같다. 숙소·축제는 뺐다. */
export const SUGGEST_CATEGORIES: ReadonlyArray<{ code: string; ko: string; en: string }> = [
  { code: 'FOOD', ko: '맛집', en: 'Food' },
  { code: 'CAFE_HEALING', ko: '카페', en: 'Cafe' },
  { code: 'SEA_BEACH', ko: '바다', en: 'Beach' },
  { code: 'NATURE_WALK', ko: '자연', en: 'Nature' },
  { code: 'CULTURE_TEMPLE', ko: '문화', en: 'Culture' },
  { code: 'SHOPPING', ko: '쇼핑', en: 'Shopping' },
  { code: 'NIGHT_VIEW', ko: '야경', en: 'Night view' },
  { code: 'ACTIVITY', ko: '액티비티', en: 'Activity' },
  { code: 'MARKET', ko: '전통시장', en: 'Market' },
];

export const NAME_MAX = 60;
export const REASON_MAX = 80;
/** 추천 이유 최소 글자 수 — 사용자 지시(2026-10-10): 장소·사진·추천 이유를 모두 적게 한다. 서버 REASON_MIN 과 같다. */
export const REASON_MIN = 10;

export type SuggestionStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type PlaceSuggestion = {
  suggestionId: string;
  name: string;
  category: string;
  lat: number;
  lng: number;
  reason: string;
  photoUrl: string | null;
  openingHours: string | null;
  phone: string | null;
  status: SuggestionStatus;
  rejectReason: string | null;
  placeId: string | null;
  suggesterUserId: string;
  suggesterName: string | null;
  createdAt: string;
};

export type DuplicatePlace = { placeId: string | null; name: string; distanceM: number };

export type LocalPick = { placeId: string; suggesterName: string | null; reason: string; openingHours: string | null; phone: string | null };

export type SuggestionDraft = {
  name: string;
  category: string;
  lat: number;
  lng: number;
  reason: string;
  photoUrl?: string | null;
  openingHours?: string | null;
  phone?: string | null;
};

/** 보내기 전에 화면이 막을 것 — 서버도 같은 것을 다시 본다. 비어 있으면 보낼 수 있다. */
export type DraftProblem = 'name' | 'category' | 'location' | 'reason' | 'photo';

export function draftProblems(draft: Partial<SuggestionDraft>): DraftProblem[] {
  const problems: DraftProblem[] = [];
  const name = draft.name?.trim() ?? '';
  if (!name || name.length > NAME_MAX) problems.push('name');
  if (!draft.category || !SUGGEST_CATEGORIES.some((c) => c.code === draft.category)) problems.push('category');
  if (typeof draft.lat !== 'number' || typeof draft.lng !== 'number') problems.push('location');
  const reason = draft.reason?.trim() ?? '';
  if ([...reason].length < REASON_MIN || reason.length > REASON_MAX) problems.push('reason');
  if (!draft.photoUrl?.trim()) problems.push('photo');
  return problems;
}

export type SubmitResult =
  | { state: 'success'; suggestion: PlaceSuggestion }
  /** 이미 있는 장소다. placeId 가 있으면 그 장소로 보낸다 — 대기 중인 남의 제안이면 null. */
  | { state: 'duplicate'; placeId: string | null }
  | { state: 'outside' }
  | { state: 'limited' }
  | { state: 'blocked' }
  | { state: 'photo' }
  | { state: 'photoRequired' }
  | { state: 'login' }
  | { state: 'error'; message: string };

function trimOrNull(value: string | null | undefined): string | null {
  const t = value?.trim();
  return t ? t : null;
}

export async function submitPlaceSuggestion(draft: SuggestionDraft, accessToken: string | null): Promise<SubmitResult> {
  try {
    const suggestion = await apiRequest<PlaceSuggestion>('/api/v1/place-suggestions', {
      method: 'POST',
      accessToken,
      body: {
        name: draft.name.trim(),
        category: draft.category,
        lat: draft.lat,
        lng: draft.lng,
        reason: draft.reason.trim(),
        photoUrl: trimOrNull(draft.photoUrl),
        openingHours: trimOrNull(draft.openingHours),
        phone: trimOrNull(draft.phone),
      },
    });
    return { state: 'success', suggestion };
  } catch (error) {
    if (error instanceof ApiClientError && !(error instanceof ApiUnavailableError)) {
      if (error.status === 401) return { state: 'login' };
      switch (error.code) {
        case 'PLACE_SUGGESTION_DUPLICATE': return { state: 'duplicate', placeId: error.fields[0] ?? null };
        case 'PLACE_SUGGESTION_OUTSIDE_BUSAN': return { state: 'outside' };
        case 'PLACE_SUGGESTION_RATE_LIMITED': return { state: 'limited' };
        case 'PLACE_SUGGESTION_TEXT_BLOCKED': return { state: 'blocked' };
        case 'PLACE_SUGGESTION_PHOTO_INVALID': return { state: 'photo' };
        case 'PLACE_SUGGESTION_PHOTO_REQUIRED': return { state: 'photoRequired' };
        default: break;
      }
    }
    return { state: 'error', message: error instanceof Error ? error.message : '' };
  }
}

/** 이 이름·위치에 이미 있는 장소. 실패하면 빈 목록 — 서버가 보낼 때 다시 보므로 여기서 막지 않는다. */
export async function findDuplicates(name: string, lat: number, lng: number, accessToken: string | null, signal?: AbortSignal): Promise<DuplicatePlace[]> {
  if (name.trim().length < 2) return [];
  try {
    const query = `name=${encodeURIComponent(name.trim())}&lat=${lat}&lng=${lng}`;
    const dto = await apiRequest<{ items: DuplicatePlace[] }>(`/api/v1/place-suggestions/duplicates?${query}`, { accessToken, signal });
    return dto.items ?? [];
  } catch {
    return [];
  }
}

export async function fetchMySuggestions(accessToken: string | null): Promise<PlaceSuggestion[] | null> {
  try {
    const dto = await apiRequest<{ items: PlaceSuggestion[] }>('/api/v1/place-suggestions/mine', { accessToken });
    return dto.items ?? [];
  } catch {
    return null;
  }
}

/** 이 장소를 추천한 현지인. 제안으로 생긴 장소가 아니면(404) null. */
export async function fetchLocalPick(placeId: string, accessToken: string | null, signal?: AbortSignal): Promise<LocalPick | null> {
  try {
    return await apiRequest<LocalPick>(`/api/v1/places/${encodeURIComponent(placeId)}/local-pick`, { accessToken, signal });
  } catch {
    return null;
  }
}

// ── 운영자 검토 ─────────────────────────────────────────────

export type ReviewListResult = { state: 'success'; items: PlaceSuggestion[] } | { state: 'forbidden' } | { state: 'error'; message: string };

export async function fetchReviewQueue(status: SuggestionStatus, accessToken: string | null): Promise<ReviewListResult> {
  try {
    const dto = await apiRequest<{ items: PlaceSuggestion[] }>(`/api/v1/admin/place-suggestions?status=${status}`, { accessToken });
    return { state: 'success', items: dto.items ?? [] };
  } catch (error) {
    if (error instanceof ApiClientError && (error.status === 403 || error.status === 401)) return { state: 'forbidden' };
    return { state: 'error', message: error instanceof Error ? error.message : '' };
  }
}

export type ReviewActionResult = { state: 'success'; suggestion: PlaceSuggestion } | { state: 'forbidden' } | { state: 'error'; message: string };

export async function reviewSuggestion(suggestionId: string, action: 'approve' | 'reject', accessToken: string | null, reason?: string): Promise<ReviewActionResult> {
  try {
    const suggestion = await apiRequest<PlaceSuggestion>(`/api/v1/admin/place-suggestions/${encodeURIComponent(suggestionId)}/${action}`, {
      method: 'POST',
      accessToken,
      body: action === 'reject' ? { reason: reason?.trim() || null } : undefined,
    });
    return { state: 'success', suggestion };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 403) return { state: 'forbidden' };
    return { state: 'error', message: error instanceof Error ? error.message : '' };
  }
}
