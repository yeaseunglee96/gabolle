import { apiRequest, getApiLanguage } from '@/api/client';
import type { PlanDraft, PreferenceAnswerStatus } from '@/plan/PlanProvider';

const tx = (ko: string, en: string) => (getApiLanguage() === 'en' ? en : ko);

export type PreferenceAnswerInput = {
  dimension: string;
  value: string | null;
  answerStatus: PreferenceAnswerStatus;
};

export type ConstraintInput = {
  type: 'ALLERGY' | 'DIET' | 'MOBILITY';
  constraintKey: string;
  severity: 'HARD' | 'SOFT';
  operator: 'EXCLUDES' | 'LTE' | 'GTE' | null;
  value: string | null;
  threshold: number | null;
  answerStatus: 'SELECTED' | 'NONE' | 'UNKNOWN';
  dietRequirement: 'REQUIRED' | 'PREFERRED' | null;
};

export type CreateTripPayload = {
  startDate: string;
  finishDate: string;
  originLat: number | null;
  originLng: number | null;
  budgetKrw: number | null;
  partySize: number;
  timeWindow: string;
  timezone: 'Asia/Seoul';
  preferences: PreferenceAnswerInput[];
  constraints: ConstraintInput[];
  // — 취향 단계에서 고른 "꼭 가고 싶은 장소". 서버는 이 목록을 여행 씨앗으로
  // 적고 추천이 그 장소를 후보 앞에 세운다(-973). 이 칸이 생기기 전에는 고른 장소가 기기
  // 안에만 남아서, 화면이 "일정에 반드시 포함돼요" 라고 적어 두고도 아무 영향이 없었다.
  mustVisitPlaceIds: string[];
  // — 기본 정보 화면에서 고른 여행 범위. 서버가 이 지역들에서 후보를 고른다.
  // 이 칸이 생기기 전에는 칩이 화면에서만 받고 서버로 오지 않아서, 해운대를 골라도 추천
  // 스무 곳이 전부 출발지 근처였다.
  travelAreas: string[];
  accommodationPlaceId: string | null;
  englishMenuRequired: boolean;
  foreignCardRequired: boolean;
  soloFriendlyPriority: boolean;
  maxTransitTransfers: number | null;
};

export type TripCreatedDto = {
  tripId: string;
  preferenceSnapshot: { version: number } | null;
};

export type RecommendationJobAcceptedDto = { jobId: string };

const selected = (status: PreferenceAnswerStatus, value: unknown): string | null =>
  status === 'SELECTED' ? JSON.stringify(value) : null;

function preference(
  dimension: string,
  status: PreferenceAnswerStatus,
  value: unknown,
): PreferenceAnswerInput {
  return { dimension, value: selected(status, value), answerStatus: status };
}

function mobility(
  key: string,
  value: boolean | null,
): ConstraintInput {
  return {
    type: 'MOBILITY',
    constraintKey: key,
    severity: 'HARD',
    operator: 'EXCLUDES',
    value: value ? JSON.stringify(true) : null,
    threshold: null,
    answerStatus: value === null ? 'UNKNOWN' : value ? 'SELECTED' : 'NONE',
    dietRequirement: null,
  };
}

export function toCreateTripPayload(draft: PlanDraft): CreateTripPayload {
  const constraints: ConstraintInput[] = [
    ...draft.allergies.map<ConstraintInput>((code) => ({
      type: 'ALLERGY', constraintKey: code, severity: 'HARD', operator: 'EXCLUDES',
      value: null, threshold: null, answerStatus: 'SELECTED', dietRequirement: null,
    })),
    ...draft.dietTypes.map<ConstraintInput>((code) => ({
      type: 'DIET', constraintKey: code, severity: 'HARD', operator: 'EXCLUDES',
      value: null, threshold: null, answerStatus: 'SELECTED', dietRequirement: 'REQUIRED',
    })),
    ...(draft.maxWalkingDistanceM && draft.maxWalkingDistanceM > 0 ? [{
      type: 'MOBILITY' as const, constraintKey: 'MAX_WALKING_METERS', severity: 'HARD' as const,
      operator: 'LTE' as const, value: null, threshold: draft.maxWalkingDistanceM,
      answerStatus: 'SELECTED' as const, dietRequirement: null,
    }] : []),
    mobility('WHEELCHAIR', draft.wheelchair),
    mobility('STROLLER', draft.stroller),
    mobility('HEAVY_LUGGAGE', draft.luggage),
    mobility('STAIRS_AVOIDANCE', draft.stairsConstraint === null ? null : draft.stairsConstraint === 'AVOID'),
  ];

  return {
    mustVisitPlaceIds: draft.mustVisitPlaces.map((place) => place.placeId),
    travelAreas: draft.travelAreas,
    accommodationPlaceId: draft.accommodationPlace?.placeId ?? null,
    englishMenuRequired: draft.englishMenuRequired,
    foreignCardRequired: draft.foreignCardRequired,
    soloFriendlyPriority: draft.soloDiningPreferred,
    maxTransitTransfers: draft.transport === 'CAR' ? null : draft.maxTransfers,
    startDate: draft.startDate,
    finishDate: draft.endDate,
    originLat: draft.originLat,
    originLng: draft.originLng,
    budgetKrw: draft.budgetKrw,
    partySize: draft.adults + draft.children,
    timeWindow: `${draft.dayStartTime}-${draft.dayEndTime}`,
    timezone: 'Asia/Seoul',
    preferences: [
      preference('category', draft.preferenceAnswerStatus.category, draft.preferences),
      preference('locality', draft.preferenceAnswerStatus.locality, draft.localityLevel),
      preference('quietness', draft.preferenceAnswerStatus.quietness, draft.quietLevel),
      preference('foodPreference', draft.preferenceAnswerStatus.foodPreference, draft.foods),
      preference('transport', 'SELECTED', draft.transport),
      preference('slopePreference', draft.slopeConstraint === null ? 'UNKNOWN' : 'SELECTED', draft.slopeConstraint),
      preference('shadePreference', draft.shadePreference === null ? 'UNKNOWN' : 'SELECTED', draft.shadePreference),
    ],
    constraints,
  };
}

function stableIdempotencyKey(payload: CreateTripPayload) {
  const source = JSON.stringify(payload);
  let hash = 2166136261;
  for (let index = 0; index < source.length; index += 1) {
    hash ^= source.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return `plan-${payload.startDate}-${payload.finishDate}-${(hash >>> 0).toString(16)}`;
}

export async function createTripAndRecommendationJob(
  draft: PlanDraft,
  accessToken: string | null,
): Promise<RecommendationJobAcceptedDto> {
  const payload = toCreateTripPayload(draft);
  const trip = await apiRequest<TripCreatedDto>('/api/v1/trips', {
    method: 'POST',
    accessToken,
    headers: { 'Idempotency-Key': stableIdempotencyKey(payload) },
    body: payload,
  });

  return apiRequest<RecommendationJobAcceptedDto>(
    `/api/v1/trips/${encodeURIComponent(trip.tripId)}/recommendation-jobs`,
    {
      method: 'POST',
      accessToken,
      body: {
        preferenceSnapshotVersion: trip.preferenceSnapshot?.version ?? null,
        topK: 20,
      },
    },
  );
}

type CloneTripDto = { tripId: string; jobId: string | null };

/**
 * 공유 일정을 "내 조건"으로 복제한다/-340). 백엔드가 여행 생성과 추천 Job 접수를
 * 한 번의 호출(POST /api/v1/shares/{token}/clone)로 같이 처리한다 — 일반 생성처럼 두 번 부르지 않는다.
 */
export async function cloneSharedTripAndJob(
  token: string,
  draft: PlanDraft,
  accessToken: string | null,
): Promise<RecommendationJobAcceptedDto> {
  const payload = toCreateTripPayload(draft);
  const cloned = await apiRequest<CloneTripDto>(`/api/v1/shares/${encodeURIComponent(token)}/clone`, {
    method: 'POST',
    accessToken,
    headers: { 'Idempotency-Key': stableIdempotencyKey(payload) },
    body: payload,
  });
  if (!cloned.jobId) {
    throw new Error(tx('일정 생성 요청이 접수되지 않았어요. 조건을 다시 확인한 뒤 시도해 주세요.', 'The itinerary request was not accepted. Please review your choices and try again.'));
  }
  return { jobId: cloned.jobId };
}
