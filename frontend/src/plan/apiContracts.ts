import type { RecommendationJobPollDto } from './recommendationJob';
import type { ItineraryDto } from './itinerary';

/**
 * 백엔드 응답이 프론트가 기대하는 모양인지 실행 시점에 확인하는 수기 타입 가드
 * .
 */

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

const JOB_STATUSES = ['QUEUED', 'PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED', 'CANCELLED', 'EXPIRED'];

export function isRecommendationJobPollDto(value: unknown): value is RecommendationJobPollDto {
  if (!isRecord(value)) return false;
  if (typeof value.jobId !== 'string') return false;
  if (typeof value.status !== 'string' || !JOB_STATUSES.includes(value.status)) return false;
  if (!isRecord(value.progress)) return false;
  if (typeof value.progress.stage !== 'string') return false;
  if (typeof value.progress.percent !== 'number') return false;
  if (value.failure !== null) {
    if (!isRecord(value.failure)) return false;
    if (typeof value.failure.code !== 'string') return false;
    if (value.failure.detail !== null && typeof value.failure.detail !== 'string') return false;
  }
  if (typeof value.retryable !== 'boolean') return false;
  if (value.pollAfterSeconds !== null && typeof value.pollAfterSeconds !== 'number') return false;
  return true;
}

function isItineraryItemDto(value: unknown): boolean {
  if (!isRecord(value)) return false;
  if (typeof value.id !== 'string') return false;
  // 시각을 못 깐 항목은 null 이다(서버 계약) — 그것을 깨진 응답으로 보지 않는다.
  if (value.startsAt !== null && typeof value.startsAt !== 'string') return false;
  if (typeof value.title !== 'string') return false;
  if (typeof value.locked !== 'boolean') return false;
  if (typeof value.placeId !== 'string') return false;
  return true;
}

export function isItineraryDto(value: unknown): value is ItineraryDto {
  if (!isRecord(value)) return false;
  if (typeof value.id !== 'string') return false;
  if (typeof value.title !== 'string') return false;
  if (typeof value.version !== 'number') return false;
  if (!Array.isArray(value.days)) return false;
  for (const day of value.days) {
    if (!isRecord(day)) return false;
    if (typeof day.date !== 'string') return false;
    if (!Array.isArray(day.items) || !day.items.every(isItineraryItemDto)) return false;
  }
  return true;
}
