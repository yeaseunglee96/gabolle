import type { PlanDraft } from './PlanProvider';

// REC-01의 공개 계약은 정수 판 번호다. UUID는 서버가 (tripId, version)으로 찾아 DB 내부에서만 사용한다.
export type RecommendationRequest = {
  preferenceSnapshotVersion: number;
  trip: Omit<PlanDraft, 'maxCompletedStep'>;
};

export function buildRecommendationRequest(draft: PlanDraft, preferenceSnapshotVersion: number): RecommendationRequest {
  const { maxCompletedStep: _progress, ...trip } = draft;
  return { preferenceSnapshotVersion, trip };
}
