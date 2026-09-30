import { apiRequest } from '@/api/client';
import { getCurrentLanguage } from '@/i18n/languages';
import { pickLanguage } from '@/i18n/pick';
import type { PlanDraft, PreferenceAnswerStatus } from '@/plan/PlanProvider';
import { lodgingAreaCodeOf, type PlaceSnapshot } from '@/plan/origins';

// 화면 언어로 고른다 — 서버용 언어(ko|en 뿐)로 고르면 일본어·중국어 화면에 영어가 나갔다(S15P21E201-1776).
const tx = (ko: string, en: string) => pickLanguage(getCurrentLanguage(), { ko, en });

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
  /**
   * 홈 시작 바에서 고른 숙소 — S15P21E201-1536 (서버 S15P21E201-1522).
   * 🔴 예전에는 accommodationLat·Lng·Name 을 보냈는데 서버에 그 칸이 없어 **조용히 버려졌다** —
   *    그날 만들어진 여행은 고른 숙소 없이 저장됐다. 서버가 이 스냅샷으로 장소를 찾거나 만들어
   *    trip.accommodation_place_id 에 넣는다. accommodationPlaceId 가 있으면 서버는 이 칸을 안 본다.
   */
  accommodation: PlaceSnapshot | null;
  /**
   * 추천 동네를 숙소로 골랐을 때 그 동네 코드(HAEUNDAE 등) — S15P21E201-1566. 서버가 동네 중심을 숙소 자리로 쓴다.
   * 우리 표의 숙소나 검색한 숙소가 있으면 null — 그쪽이 더 정확하다.
   */
  accommodationArea: string | null;
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
    // 🔴 ALLERGY 를 «일부러» 안 보낸다 (S15P21E201-1497, 결정은 -1468 의 ㄱ).
    //
    //    운영 place_feature 에 ALLERGEN_TAG 가 0행이다. 채점기는 표식 없는 후보를
    //    「확인 못 함」으로 남기고 unknown-exclusion-threshold=REQUIRED 가 그것을 전부
    //    뺀다 — 보내는 순간 후보가 0건이 되어 일정 생성이 실패한다.
    //    실측(2026-09-22): 알레르기·식단을 고른 작업 8건 중 성공 0건.
    //
    //    🔴 질문을 지우는 것만으로는 부족해서 여기도 막는다. draft.allergies 에는
    //    «전에 답해 둔 사람»의 값이 그대로 남아 있고, 그 사람들은 질문이 사라져도
    //    계속 실패하게 된다. 지우지 않고 안 보내기만 한다 — 다시 열 때 그대로 쓴다.
    //
    //    다시 보내는 조건: ALLERGEN_TAG 가 VERIFIED 로 쌓였을 때. 그때 이 블록을 되살린다.
    // 「해당 없음」으로 답했으면 초안에 예전 식단 코드가 남아 있어도 고른 것으로 안 보낸다. 그 밖에는 예전 그대로 —
    // 식단은 안전에 걸리므로, 보내던 것을 조용히 안 보내게 만들지 않는다.
    ...(draft.dietStatus === 'NONE' ? [] : draft.dietTypes).map<ConstraintInput>((code) => ({
      type: 'DIET', constraintKey: code, severity: 'HARD', operator: 'EXCLUDES',
      value: null, threshold: null, answerStatus: 'SELECTED', dietRequirement: 'REQUIRED',
    })),
    // 🔴 식단 「해당 없음」도 보낸다 (S15P21E201-1878). 서버가 음식 취향의 채식·할랄을 식단 조건으로 더하는데
    //    (S15P21E201-1873), 이번 여행 식단을 NONE 으로 답한 여행만은 더하지 않는다 — 그 답을 «DIET 제약의 NONE» 으로
    //    읽는다. 전에는 고른 식단만 보내서 「이번엔 해당 없음」이라고 해도 계정 취향의 채식이 일정을 걸렀다.
    //    모양은 서버 시험(TripCreationTest)이 받는 그대로 — 값 없음, SOFT, dietRequirement 없음. 그래서 건강·식이
    //    동의 검사(isSensitive = 필수 식단)에도 안 걸린다. 키는 필수라 아무 식단 코드나 하나 적는다(읽지 않는다).
    ...(draft.dietStatus === 'NONE' ? [{
      type: 'DIET' as const, constraintKey: 'VEGETARIAN', severity: 'SOFT' as const, operator: null,
      value: null, threshold: null, answerStatus: 'NONE' as const, dietRequirement: null,
    }] : []),
    ...(draft.maxWalkingDistanceM && draft.maxWalkingDistanceM > 0 ? [{
      type: 'MOBILITY' as const, constraintKey: 'MAX_WALKING_METERS', severity: 'HARD' as const,
      operator: 'LTE' as const, value: null, threshold: draft.maxWalkingDistanceM,
      answerStatus: 'SELECTED' as const, dietRequirement: null,
    }] : []),
    mobility('WHEELCHAIR', draft.wheelchair),
    mobility('STROLLER', draft.stroller),
    // 큰 짐은 서버가 경사로만 가른다 — 「반드시」면 가파른 곳을 빼고, 「확인 안 됨」 경고는 안 붙인다.
    // (S15P21E201-1855 에서 「읽는 코드가 없다」고 뺐다가 되살렸다. 이름으로 따로 읽지 않을 뿐, 휠체어·유아차와
    //  같은 이동 조건 갈래에서 경사 판정을 받고 있었다.)
    mobility('HEAVY_LUGGAGE', draft.luggage),
    mobility('STAIRS_AVOIDANCE', draft.stairsConstraint === null ? null : draft.stairsConstraint === 'AVOID'),
  ];

  return {
    mustVisitPlaceIds: draft.mustVisitPlaces.map((place) => place.placeId),
    travelAreas: draft.travelAreas,
    accommodationPlaceId: draft.accommodationPlace?.placeId ?? null,
    // 우리 표의 숙소가 있으면 그것만 — 둘 다 보내도 서버가 placeId 를 먼저 보지만, 보내는 쪽에서도 하나만 싣는다.
    accommodation: draft.accommodationPlace?.placeId ? null : draft.lodgingPlace ?? null,
    accommodationArea: draft.accommodationPlace?.placeId || draft.lodgingPlace ? null : lodgingAreaCodeOf(draft.lodgingLat, draft.lodgingLng),
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
      // 🔴 칩을 골랐으면 「고름(SELECTED)」으로 보낸다 (2026-09-23, S15P21E201-1535). 질문 화면의 칩은
      //    draft.preferences 만 바꾸고 preferenceAnswerStatus.category 는 안 바꿔서, 테마를 골라도
      //    UNKNOWN·값 null 로 나갔다 — 서버는 SELECTED 만 읽으므로 **테마가 한 번도 반영되지 않았다.**
      //    (운영 실측: 최근 여행 전부 테마 없음.) 칩이 비었으면 원래 상태(모름·상관없음)를 그대로 둔다.
      preference('category', draft.preferences.length ? 'SELECTED' : draft.preferenceAnswerStatus.category, draft.preferences),
      preference('locality', draft.preferenceAnswerStatus.locality, draft.localityLevel),
      preference('quietness', draft.preferenceAnswerStatus.quietness, draft.quietLevel),
      preference('foodPreference', draft.preferenceAnswerStatus.foodPreference, draft.foods),
      preference('transport', 'SELECTED', draft.transport),
      preference('slopePreference', draft.slopeConstraint === null ? 'UNKNOWN' : 'SELECTED', draft.slopeConstraint),
      preference('shadePreference', draft.shadePreference === null ? 'UNKNOWN' : 'SELECTED', draft.shadePreference),
      // 🔴 「여행 기분」을 묻기만 하고 안 보냈다 (2026-09-23, S15P21E201-1535). 서버는 이 값으로 하루에 넣을
      //    장소 수를 정한다(RELAXED 3 · BALANCED 4 · PACKED 5, ItineraryDraftService). 안 오면 누구나 4곳이었다.
      preference('pace', draft.paceLevel ? 'SELECTED' : 'UNKNOWN', draft.paceLevel),
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
        // 🔴 20 으로 박아 두지 않는다 (2026-09-23, S15P21E201-1535). 서버는 비우면 여행 길이에 맞춰
        //    (일수 × 하루 장소 수 × 3, 최소 10) 후보 수를 정하는데, 20 을 주면 그 계산을 덮는다 —
        //    5일이면 여유가 0 이고 6일부터는 날을 다 못 채웠다(RecommendationService.defaultTopKFor).
        topK: null,
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
