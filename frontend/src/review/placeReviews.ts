// 장소 리뷰 — S15P21E201-406. GET·POST /api/v1/places/{placeId}/reviews 를 그대로 옮긴다
// (PlaceReviewController.java 기준). 점수는 1~5(항목별로 null 가능)이고, mine이 참인
// 리뷰가 있으면 그 사용자가 이미 이 장소를 평가한 것이다.
import { UNAVAILABLE_MESSAGE } from '@/api/errorText';
import { apiRequest, ApiClientError } from '@/api/client';

export type PlaceReviewDto = {
  placeReviewId: string;
  foodScore: number | null;
  priceScore: number | null;
  accessibilityScore: number | null;
  onsiteScore: number | null;
  body: string | null;
  verified: boolean;
  region: string | null;
  createdAt: string;
  updatedAt: string;
  mine: boolean;
};

type PlaceReviewListResponseDto = { reviews: PlaceReviewDto[]; averageScore: number | null };

export type PlaceReviewListResult =
  | { state: 'success'; reviews: PlaceReviewDto[]; averageScore: number | null; mine: PlaceReviewDto | null }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

export type PlaceReviewSubmitResult =
  | { state: 'success'; review: PlaceReviewDto }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

function failure(error: unknown): { state: 'unavailable' | 'offline' | 'error'; message: string } {
  if (error instanceof ApiClientError && (error.status === 404 || error.status === 501)) return { state: 'unavailable', message: UNAVAILABLE_MESSAGE };
  if (error instanceof ApiClientError && (error.status === 0 || error.code === 'NETWORK_ERROR')) return { state: 'offline', message: error.message };
  return { state: 'error', message: error instanceof Error ? error.message : '리뷰를 처리하지 못했어요.' };
}

export async function loadPlaceReviews(placeId: string, accessToken: string | null): Promise<PlaceReviewListResult> {
  try {
    const response = await apiRequest<PlaceReviewListResponseDto>(`/api/v1/places/${encodeURIComponent(placeId)}/reviews`, { accessToken });
    return { state: 'success', reviews: response.reviews, averageScore: response.averageScore, mine: response.reviews.find((review) => review.mine) ?? null };
  } catch (error) {
    return failure(error);
  }
}

// 3단계 UI 선택을 서버 점수(1~5)로 옮긴다 — 항목을 매기지 않으면 null을 그대로 보낸다
// (0으로 보내면 "최하점"과 "안 매김"이 서버에서 구분 안 된다, PlaceReview.java 기준).
export type ThreeStepScore = 'LOW' | 'MID' | 'HIGH' | null;
export function scoreFromStep(step: ThreeStepScore): number | null {
  if (step === 'LOW') return 1;
  if (step === 'MID') return 3;
  if (step === 'HIGH') return 5;
  return null;
}

// 방문 인증(GPS) —·-291. POST /api/v1/places/{placeId}/visit-verifications 를
// 그대로 옮긴다(VisitVerificationController.java 기준). status 는 서버가 이미 화면에 그대로
// 보여줄 수 있는 message 를 같이 주므로, 화면은 문구를 새로 짓지 않고 그대로 쓴다.
export type VisitVerificationStatus = 'VERIFIED' | 'TOO_FAR' | 'LOW_ACCURACY';
export type VisitVerificationDto = { verified: boolean; distanceM: number | null; status: VisitVerificationStatus; message: string };
export type VisitVerificationResult =
  | { state: 'success'; outcome: VisitVerificationDto }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

export async function verifyPlaceVisit(input: { placeId: string; lat: number; lng: number; accuracyM: number; accessToken: string | null }): Promise<VisitVerificationResult> {
  try {
    const outcome = await apiRequest<VisitVerificationDto>(`/api/v1/places/${encodeURIComponent(input.placeId)}/visit-verifications`, {
      method: 'POST',
      accessToken: input.accessToken,
      body: { lat: input.lat, lng: input.lng, accuracyM: input.accuracyM },
    });
    return { state: 'success', outcome };
  } catch (error) {
    return failure(error);
  }
}

export async function submitPlaceReview(input: { placeId: string; food: ThreeStepScore; price: ThreeStepScore; accessibility: ThreeStepScore; onsite: ThreeStepScore; body?: string; region?: string; accessToken: string | null }): Promise<PlaceReviewSubmitResult> {
  try {
    const review = await apiRequest<PlaceReviewDto>(`/api/v1/places/${encodeURIComponent(input.placeId)}/reviews`, {
      method: 'POST',
      accessToken: input.accessToken,
      body: {
        foodScore: scoreFromStep(input.food),
        priceScore: scoreFromStep(input.price),
        accessibilityScore: scoreFromStep(input.accessibility),
        onsiteScore: scoreFromStep(input.onsite),
        body: input.body?.trim() || null,
        region: input.region ?? null,
      },
    });
    return { state: 'success', review };
  } catch (error) {
    return failure(error);
  }
}
