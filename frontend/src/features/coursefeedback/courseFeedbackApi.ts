// 코스별 품질 피드백 — 서버 왕복(S15P21E201-2020). 서버: CourseFeedbackController.
import { apiRequest } from '@/api/client';

export type CourseRating = 'UP' | 'DOWN';

export type CourseFeedbackReason =
  | 'LONG_TRANSIT' | 'NOT_MY_TASTE' | 'TOO_TIGHT' | 'PLACE_CLOSED' | 'HOURS_MISMATCH'
  | 'GREAT_PLACES' | 'GOOD_FLOW' | 'GOOD_PACE';

export type CourseFeedbackDto = {
  itineraryId: string;
  version: number;
  courseLabel: string | null;
  rating: CourseRating;
  reasons: CourseFeedbackReason[];
  comment: string | null;
  updatedAt: string;
};

export const COMMENT_MAX = 200;

/** 쪽마다 어울리는 이유만 보여 준다. 서버는 어느 쪽에서도 다 받는다. */
export const REASONS_BY_RATING: Record<CourseRating, CourseFeedbackReason[]> = {
  UP: ['GREAT_PLACES', 'GOOD_FLOW', 'GOOD_PACE'],
  DOWN: ['LONG_TRANSIT', 'NOT_MY_TASTE', 'TOO_TIGHT', 'PLACE_CLOSED', 'HOURS_MISMATCH'],
};

const path = (itineraryId: string) => `/api/v1/itineraries/${encodeURIComponent(itineraryId)}/course-feedback`;

export async function loadMyCourseFeedback(itineraryId: string, accessToken: string | null): Promise<CourseFeedbackDto | null> {
  const data = await apiRequest<{ feedback: CourseFeedbackDto | null }>(path(itineraryId), { accessToken });
  return data?.feedback ?? null;
}

export async function saveCourseFeedback(input: {
  itineraryId: string;
  rating: CourseRating;
  reasons: CourseFeedbackReason[];
  comment: string;
  courseLabel: string | null;
  accessToken: string | null;
}): Promise<CourseFeedbackDto> {
  const comment = input.comment.trim();
  return apiRequest<CourseFeedbackDto>(path(input.itineraryId), {
    method: 'PUT',
    accessToken: input.accessToken,
    body: { rating: input.rating, reasons: input.reasons, comment: comment || null, courseLabel: input.courseLabel },
  });
}
