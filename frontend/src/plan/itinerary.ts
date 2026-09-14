import { apiRequest, ApiClientError } from '@/api/client';

export type ItineraryItemDto = {
  id: string;
  startsAt: string;
  title: string;
  description?: string | null;
  estimatedCostKrw?: number | null;
  walkingMeters?: number | null;
  locked: boolean;
  dataStatus?: 'VERIFIED' | 'ESTIMATED' | 'UNKNOWN';
  // S15P21E201-744 — 이 항목이 가리키는 장소. "다녀오셨나요" 평가(S15P21E201-406)를
  // 어느 장소로 보낼지 여기서 얻는다. ItineraryDetailResponse.Item 기준.
  placeId: string;
};

export type ItineraryDto = {
  id: string;
  title: string;
  version: number;
  days: Array<{ date: string; items: ItineraryItemDto[] }>;
  totalEstimatedCostKrw?: number | null;
  totalWalkingMeters?: number | null;
  fallbackMode?: 'MODEL' | 'RULE' | 'BASELINE' | null;
  myRole?: 'OWNER' | 'EDITOR' | 'VIEWER';
  canEdit?: boolean;
};

export type ItineraryVersionEntryDto = {
  version: number;
  baseVersion: number;
  operation: string;
  createdBy: string;
  createdAt: string;
  requestId: string;
  warningCodes?: string[];
  revertedFromVersion?: number | null;
};

export type ItineraryLoadResult =
  | { state: 'success'; itinerary: ItineraryDto }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

// S15P21E201-268/-858(BE) 응답 계약: 편집 응답 다섯 갈래(순서 바꾸기·더하기·재계획·
// 고정/해제·되돌리기) 모두 ItineraryDto 위에 이 두 칸을 더 실어 보낸다. 영업시간을 실제로
// 어겼으면 warnings, 어겼는지조차 못 봤으면(자료 없음 등) notChecked — 두 목록이 함께 올 수
// 있고(한 곳은 닫혀 있고 다른 곳은 자료가 없는 경우), 하나만 보여 주면 화면이 거짓말을 한다
// (제보: jaehyeon, 2026-09-11, S15P21E201-852/-858). 이 값은 각 편집 응답에만 실려 오고
// 이후 활동 이력(loadItineraryVersions)에는 안 남으므로 저장해 두지 않으면 사라진다.
export type ItineraryOpeningHoursWarning = { code: string; itemId: string; placeId: string; at: string };
export type ItineraryOpeningHoursNotChecked = { check: string; reason: string };

function parseOpeningHoursFields(dto: { warnings?: ItineraryOpeningHoursWarning[]; notChecked?: ItineraryOpeningHoursNotChecked[] }) {
  return { warnings: dto.warnings ?? [], notChecked: dto.notChecked ?? [] };
}

export type ItineraryMutationResult =
  | { state: 'success'; itinerary: ItineraryDto; warnings: ItineraryOpeningHoursWarning[]; notChecked: ItineraryOpeningHoursNotChecked[] }
  | { state: 'conflict'; latestVersion: number; message: string }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

function failure(error: unknown): Exclude<ItineraryMutationResult, { state: 'success' }> {
  if (error instanceof ApiClientError && error.status === 409) {
    const latest = Number(error.fields.find((field) => /^latestVersion=/.test(field))?.split('=')[1]);
    return { state: 'conflict', latestVersion: Number.isFinite(latest) ? latest : 0, message: '다른 변경이 먼저 반영됐어요. 최신 일정을 불러와 다시 시도해 주세요.' };
  }
  if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return { state: 'unavailable', message: '일정 API가 아직 준비되지 않았어요.' };
  if (error instanceof ApiClientError && (error.status === 0 || error.code === 'NETWORK_ERROR')) return { state: 'offline', message: error.message };
  return { state: 'error', message: error instanceof Error ? error.message : '일정을 처리하지 못했어요.' };
}

export async function loadItinerary(id: string, accessToken: string | null): Promise<ItineraryLoadResult> {
  try {
    return { state: 'success', itinerary: await apiRequest<ItineraryDto>(`/api/v1/itineraries/${encodeURIComponent(id)}`, { accessToken }) };
  } catch (error) {
    const result = failure(error);
    return result.state === 'conflict' ? { state: 'error', message: result.message } : result;
  }
}

export async function setItineraryItemLocked(input: { itineraryId: string; itemId: string; locked: boolean; baseVersion: number; accessToken: string | null }): Promise<ItineraryMutationResult> {
  try {
    const dto = await apiRequest<ItineraryDto & { warnings?: ItineraryOpeningHoursWarning[]; notChecked?: ItineraryOpeningHoursNotChecked[] }>(`/api/v1/itineraries/${encodeURIComponent(input.itineraryId)}/items/${encodeURIComponent(input.itemId)}/lock`, { method: 'POST', accessToken: input.accessToken, body: { locked: input.locked, baseVersion: input.baseVersion } });
    return { state: 'success', itinerary: dto, ...parseOpeningHoursFields(dto) };
  } catch (error) {
    return failure(error);
  }
}

export type ItineraryJobAcceptedResult =
  | { state: 'accepted'; jobId: string }
  | { state: 'conflict'; latestVersion: number; message: string }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

export async function removeItineraryItem(input: { itineraryId: string; itemId: string; baseVersion: number; operationalReason?: string; accessToken: string | null }): Promise<ItineraryJobAcceptedResult> {
  try {
    const dto = await apiRequest<{ jobId: string }>(`/api/v1/itineraries/${encodeURIComponent(input.itineraryId)}/items/${encodeURIComponent(input.itemId)}/remove`, { method: 'POST', accessToken: input.accessToken, body: { baseVersion: input.baseVersion, operationalReason: input.operationalReason } });
    return { state: 'accepted', jobId: dto.jobId };
  } catch (error) {
    return failure(error);
  }
}

export async function recalculateItineraryDay(input: { itineraryId: string; baseVersion: number; dayIndex?: number; fromItemId?: string; accessToken: string | null }): Promise<ItineraryJobAcceptedResult> {
  try {
    const body = input.fromItemId ? { baseVersion: input.baseVersion, fromItemId: input.fromItemId } : { baseVersion: input.baseVersion, dayIndex: input.dayIndex ?? 0 };
    const dto = await apiRequest<{ jobId: string }>(`/api/v1/itineraries/${encodeURIComponent(input.itineraryId)}/recalculate`, { method: 'POST', accessToken: input.accessToken, body });
    return { state: 'accepted', jobId: dto.jobId };
  } catch (error) {
    return failure(error);
  }
}

export type ItineraryJobPollDto = {
  jobId: string;
  status: 'QUEUED' | 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED' | 'CANCELLED' | 'EXPIRED';
  itineraryVersion?: number | null;
  failure?: { code: string; detail?: string | null } | null;
};

export type ItineraryJobResult =
  | { state: 'pending' }
  | { state: 'succeeded'; version: number }
  | { state: 'conflict'; message: string }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

export async function pollItineraryJob(jobId: string, accessToken: string | null): Promise<ItineraryJobResult> {
  try {
    const dto = await apiRequest<ItineraryJobPollDto>(`/api/v1/jobs/${encodeURIComponent(jobId)}`, { accessToken });
    if (dto.status === 'SUCCEEDED') return { state: 'succeeded', version: dto.itineraryVersion ?? 0 };
    if (dto.status === 'FAILED' || dto.status === 'EXPIRED') {
      if (dto.failure?.code === 'ITINERARY_VERSION_CONFLICT') return { state: 'conflict', message: '다른 변경이 먼저 반영됐어요. 최신 일정을 불러와 다시 시도해 주세요.' };
      return { state: 'error', message: dto.failure?.detail ?? '요청을 처리하지 못했어요.' };
    }
    if (dto.status === 'CANCELED' || dto.status === 'CANCELLED') return { state: 'error', message: '요청이 취소됐어요.' };
    return { state: 'pending' };
  } catch (error) {
    const result = failure(error);
    return result.state === 'conflict' ? { state: 'conflict', message: result.message } : result;
  }
}

export type ItineraryReorderResult =
  | { state: 'success'; itinerary: ItineraryDto; warnings: ItineraryOpeningHoursWarning[]; notChecked: ItineraryOpeningHoursNotChecked[] }
  | { state: 'conflict'; latestVersion: number; message: string }
  | { state: 'mismatch'; message: string }
  | { state: 'lockedItemMoved'; message: string }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

export async function reorderItineraryDay(input: { itineraryId: string; dayIndex: number; itemKeys: string[]; baseVersion: number; accessToken: string | null }): Promise<ItineraryReorderResult> {
  try {
    const dto = await apiRequest<ItineraryDto & { warnings?: ItineraryOpeningHoursWarning[]; notChecked?: ItineraryOpeningHoursNotChecked[] }>(`/api/v1/itineraries/${encodeURIComponent(input.itineraryId)}/days/${input.dayIndex}/reorder`, {
      method: 'POST',
      accessToken: input.accessToken,
      body: { itemKeys: input.itemKeys, baseVersion: input.baseVersion },
    });
    return { state: 'success', itinerary: dto, ...parseOpeningHoursFields(dto) };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 400 && error.code === 'ITINERARY_DAY_ORDER_MISMATCH') {
      return { state: 'mismatch', message: '순서 목록이 이 날짜의 장소와 맞지 않아요. 새로고침 후 다시 시도해 주세요.' };
    }
    if (error instanceof ApiClientError && error.status === 409 && error.code === 'ITINERARY_LOCKED_ITEM_MOVED') {
      return { state: 'lockedItemMoved', message: '고정된 장소는 자리를 옮길 수 없어요. 고정을 먼저 풀어 주세요.' };
    }
    return failure(error);
  }
}

export type ItineraryRevertResult = ItineraryMutationResult | { state: 'noOp'; message: string };

export async function revertItinerary(input: { itineraryId: string; baseVersion: number; toVersion?: number; accessToken: string | null }): Promise<ItineraryRevertResult> {
  try {
    const body = input.toVersion == null ? { baseVersion: input.baseVersion } : { baseVersion: input.baseVersion, toVersion: input.toVersion };
    const dto = await apiRequest<ItineraryDto & { warnings?: ItineraryOpeningHoursWarning[]; notChecked?: ItineraryOpeningHoursNotChecked[] }>(`/api/v1/itineraries/${encodeURIComponent(input.itineraryId)}/revert`, { method: 'POST', accessToken: input.accessToken, body });
    return { state: 'success', itinerary: dto, ...parseOpeningHoursFields(dto) };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 422 && error.code === 'ITINERARY_NOTHING_TO_REVERT') return { state: 'noOp', message: '되돌릴 변경 사항이 없어요.' };
    return failure(error);
  }
}

export async function loadItineraryVersions(itineraryId: string, accessToken: string | null): Promise<{ state: 'success'; versions: ItineraryVersionEntryDto[] } | { state: 'unavailable' | 'offline' | 'error'; message: string }> {
  try {
    return { state: 'success', versions: await apiRequest<ItineraryVersionEntryDto[]>(`/api/v1/itineraries/${encodeURIComponent(itineraryId)}/versions`, { accessToken }) };
  } catch (error) {
    const result = failure(error);
    return result.state === 'conflict' ? { state: 'error', message: result.message } : result;
  }
}

// S15P21E201-96·304 — 지연 경고. 서버 응답 필드명은 origin/back/dev의
// ItineraryPaceResponse.java(2026-09-10)를 그대로 옮겼다. paceFactor는 표본이
// 모자라면 null — 그때 1.0으로 갈음해 그리면 "계획대로 간다"는 틀린 안심이 된다.
export type ItineraryPaceItemDto = {
  itemId: string;
  visited: boolean;
  predictedArrival: string | null;
  predictedDeparture: string | null;
  plannedArrival: string | null;
  delayMinutes: number | null;
  atRisk: boolean;
};

export type ItineraryPaceDto = {
  itineraryId: string;
  dayIndex: number;
  paceFactor: number | null;
  sampleCount: number;
  minSamples: number;
  plannedDayEnd: string | null;
  items: ItineraryPaceItemDto[];
  atRiskItemIds: string[];
  notChecked: Array<{ check: string; reason: string }>;
};

export type ItineraryPaceResult = { state: 'success'; pace: ItineraryPaceDto } | { state: 'unavailable' | 'offline' | 'error'; message: string };

export async function loadItineraryPace(itineraryId: string, dayIndex: number, accessToken: string | null): Promise<ItineraryPaceResult> {
  try {
    return { state: 'success', pace: await apiRequest<ItineraryPaceDto>(`/api/v1/itineraries/${encodeURIComponent(itineraryId)}/days/${dayIndex}/pace`, { accessToken }) };
  } catch (error) {
    const result = failure(error);
    return result.state === 'conflict' ? { state: 'error', message: result.message } : result;
  }
}

// S15P21E201-308 — 여행 전체 리듬. travelShare·plannedVsActual은 못 재면 null이고
// 0과는 다른 뜻이다(0 = 재 보니 그 값, null = 아직 못 쟀다). ItineraryRhythmResponse.java 기준.
export type ItineraryRhythmDto = {
  itineraryId: string;
  dayCount: number;
  averageItemsPerDay: number;
  travelShare: number | null;
  plannedVsActual: number | null;
  sampleCount: number;
  notChecked: Array<{ check: string; reason: string }>;
};

export type ItineraryRhythmResult = { state: 'success'; rhythm: ItineraryRhythmDto } | { state: 'unavailable' | 'offline' | 'error'; message: string };

export async function loadItineraryRhythm(itineraryId: string, accessToken: string | null): Promise<ItineraryRhythmResult> {
  try {
    return { state: 'success', rhythm: await apiRequest<ItineraryRhythmDto>(`/api/v1/itineraries/${encodeURIComponent(itineraryId)}/rhythm`, { accessToken }) };
  } catch (error) {
    const result = failure(error);
    return result.state === 'conflict' ? { state: 'error', message: result.message } : result;
  }
}

export type ItineraryReplanResult =
  | { state: 'success'; itinerary: ItineraryDto; warnings: ItineraryOpeningHoursWarning[]; notChecked: ItineraryOpeningHoursNotChecked[] }
  | { state: 'conflict'; latestVersion: number; message: string }
  | { state: 'overflow'; itemIds: string[]; message: string }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

// 지나간 방문지는 그대로 두고 안 간 방문지의 시각만 다시 매긴다(S15P21E201-308).
// baseVersion은 쿼리로 보낸다 — 서버가 If-Match 헤더·baseVersion 쿼리 둘 다 받는다.
export async function replanItineraryDay(input: { itineraryId: string; dayIndex: number; baseVersion: number; accessToken: string | null }): Promise<ItineraryReplanResult> {
  try {
    const dto = await apiRequest<ItineraryDto & { warnings?: ItineraryOpeningHoursWarning[]; notChecked?: ItineraryOpeningHoursNotChecked[] }>(
      `/api/v1/itineraries/${encodeURIComponent(input.itineraryId)}/days/${input.dayIndex}/replan?baseVersion=${input.baseVersion}`,
      { method: 'POST', accessToken: input.accessToken },
    );
    return { state: 'success', itinerary: dto, ...parseOpeningHoursFields(dto) };
  } catch (error) {
    // 남은 일정이 그날 안에 안 들어가면 아무것도 저장하지 않고 넘치는 항목 id를 실어 거부한다.
    if (error instanceof ApiClientError && error.status === 422 && error.code === 'ITINERARY_REPLAN_OVERFLOWS_DAY') {
      return { state: 'overflow', itemIds: error.fields, message: '남은 일정이 하루 안에 다 들어가지 않아요. 넘치는 방문지를 먼저 확인해 주세요.' };
    }
    return failure(error);
  }
}

// S15P21E201-293 — 방문지 실제 도착·출발 기록. 보낸 것이 그 방문지의 최종 상태다
// (부분 갱신이 아니다) — 출발만 다시 보내면 도착이 null로 덮인다. 그래서 둘 다
// 채워 보내야 하는 시점(출발 기록)에는 이미 아는 도착 시각을 호출부에서 함께 실어야 한다.
export async function recordItineraryItemActual(input: { itineraryId: string; itemId: string; arrivedAt: string | null; departedAt: string | null; accessToken: string | null }): Promise<ItineraryMutationResult> {
  try {
    const dto = await apiRequest<ItineraryDto & { warnings?: ItineraryOpeningHoursWarning[]; notChecked?: ItineraryOpeningHoursNotChecked[] }>(`/api/v1/itineraries/${encodeURIComponent(input.itineraryId)}/items/${encodeURIComponent(input.itemId)}/actual`, {
      method: 'PUT',
      accessToken: input.accessToken,
      body: { arrivedAt: input.arrivedAt, departedAt: input.departedAt },
    });
    return { state: 'success', itinerary: dto, ...parseOpeningHoursFields(dto) };
  } catch (error) {
    return failure(error);
  }
}
