import { apiRequest } from '@/api/client';
import type { PlanDraft, PreferenceAnswerStatus } from '@/plan/PlanProvider';

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
      preference('atmosphere', draft.preferenceAnswerStatus.atmosphere, draft.atmospheres),
      preference('locality', draft.preferenceAnswerStatus.locality, draft.localityLevel),
      preference('quietness', draft.preferenceAnswerStatus.quietness, draft.quietLevel),
      preference('touristPreference', draft.preferenceAnswerStatus.touristPreference, draft.touristLevel),
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
