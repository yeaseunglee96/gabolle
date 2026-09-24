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
  // travelDataStatus 를 반드시 함께 본다. ESTIMATED(직선거리 어림값)를 실제 소요시간처럼
  // 그리면 사용자가 그 시간에 맞춰 움직이다 늦는다. 이 기능 이전에 만들어진 판은 null 이다.
  travelDurationMin?: number | null;
  travelDataStatus?: 'VERIFIED' | 'ESTIMATED' | 'UNKNOWN' | null;
  // — 이 항목이 가리키는 장소. "다녀오셨나요" 평가를
  // 어느 장소로 보낼지 여기서 얻는다. ItineraryDetailResponse.Item 기준.
  placeId: string;
  /**
   * 이 방문지의 좌표 —-1330. 지도에 선을 그리는 재료다.
   *
   * 🔴 **모르면 `null` 이지 `0` 이 아니다.** 위도 0·경도 0 은 아프리카 서쪽 바다
   *    한가운데(기니만)라서, 0 으로 그리면 **지도에 실제로 점이 찍힌다.**
   *
   * 🔴 **옛 서버에는 이 칸이 아예 없다.** 그때는 `undefined` 다 — 화면이 장소를 따로
   *    물어서 채운다(`itineraryStops.ts`).
   */
  lat?: number | null;
  lng?: number | null;
};

/**
 * 하루 끝에 돌아가는 이동 — S15P21E201-1565. 마지막 날이 아니면 숙소(LODGING), 마지막 날이면 여행 출발지(ORIGIN).
 * 옛 서버에는 칸이 없고(undefined), 돌아갈 자리를 모르는 날은 null 이다.
 */
export type DayReturnLeg = {
  kind: 'LODGING' | 'ORIGIN';
  /** 숙소 이름 또는 동네 이름. 출발지면 null. */
  label: string | null;
  lat: number;
  lng: number;
  durationMin: number | null;
  distanceM: number | null;
  travelDataStatus: 'VERIFIED' | 'ESTIMATED' | 'UNKNOWN' | null;
};

/**
 * 그날 일정이 시작하는 자리 — S15P21E201-1580. 첫날은 여행 출발지(ORIGIN), 둘째 날부터는 숙소(LODGING, 숙소를
 * 모르면 출발지). 규칙은 서버 한 곳(ItineraryLegPlanner.dayStart)이 정한다 — 화면이 따로 추측하지 않는다.
 * 옛 서버에는 칸이 없고(undefined), 출발지도 모르는 여행은 null 이다.
 */
export type DayStart = {
  kind: 'LODGING' | 'ORIGIN';
  /** 숙소 이름 또는 동네 이름. 출발지면 null. */
  label: string | null;
  lat: number;
  lng: number;
};

export type ItineraryDto = {
  id: string;
  title: string;
  version: number;
  /**
   * 이 여행을 몇 명이 가는가 —-1339.
   *
   * 🔴 **옛 서버에는 이 칸이 아예 없다.** 그때는 `undefined` 이고, 화면은 인원 칸을
   *    **안 그린다**. 기기에 남은 초안으로 대신 채우지 않는다 — 그 값은 기본이 1 이라
   *    「모른다」와 「혼자다」가 구분이 안 된다.
   */
  partySize?: number | null;
  days: Array<{ date: string; items: ItineraryItemDto[]; returnLeg?: DayReturnLeg | null; start?: DayStart | null }>;
  totalEstimatedCostKrw?: number | null;
  totalWalkingMeters?: number | null;
  fallbackMode?: 'MODEL' | 'RULE' | 'BASELINE' | null;
  myRole?: 'OWNER' | 'EDITOR' | 'VIEWER';
  canEdit?: boolean;
  // — 이 일정이 어느 여행의 것인가.
  tripId?: string;
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

// (BE) — 사용자가 고른 장소를 그 날의 마지막에 더한다. 응답은 고정·해제와
// 같은 모양(일정 전체 + 경고)이고, 재계산 Job 은 서버가 접수하지 않는다 — 화면이 응답의
// 새 판 번호로 recalculateItineraryDay 를 이어 불러야 시각이 채워진다(서버 주석 그대로).
// dayIndex 는 0 이 첫날이다. 여행 기간을 벗어나면 400, 판이 낡았으면 409다.
export async function addItineraryItem(input: { itineraryId: string; placeId: string; dayIndex: number; baseVersion: number; accessToken: string | null }): Promise<ItineraryMutationResult> {
  try {
    const dto = await apiRequest<ItineraryDto & { warnings?: ItineraryOpeningHoursWarning[]; notChecked?: ItineraryOpeningHoursNotChecked[] }>(`/api/v1/itineraries/${encodeURIComponent(input.itineraryId)}/items`, { method: 'POST', accessToken: input.accessToken, body: { placeId: input.placeId, dayIndex: input.dayIndex, baseVersion: input.baseVersion } });
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

// — 판 목록 응답이 배열에서 { items, count, hasMore } 로 바뀐다.
type ItineraryVersionsPayload = ItineraryVersionEntryDto[] | { items: ItineraryVersionEntryDto[]; count?: number; hasMore?: boolean };

export async function loadItineraryVersions(itineraryId: string, accessToken: string | null): Promise<{ state: 'success'; versions: ItineraryVersionEntryDto[] } | { state: 'unavailable' | 'offline' | 'error'; message: string }> {
  try {
    const payload = await apiRequest<ItineraryVersionsPayload>(`/api/v1/itineraries/${encodeURIComponent(itineraryId)}/versions`, { accessToken });
    return { state: 'success', versions: Array.isArray(payload) ? payload : payload.items ?? [] };
  } catch (error) {
    const result = failure(error);
    return result.state === 'conflict' ? { state: 'error', message: result.message } : result;
  }
}

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

// — 여행 전체 리듬. travelShare·plannedVsActual은 못 재면 null이고
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

// 지나간 방문지는 그대로 두고 안 간 방문지의 시각만 다시 매긴다.
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

// — 방문지 실제 도착·출발 기록. 보낸 것이 그 방문지의 최종 상태다
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
