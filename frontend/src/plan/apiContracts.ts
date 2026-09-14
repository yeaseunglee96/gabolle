import type { RecommendationJobPollDto } from './recommendationJob';
import type { ItineraryDto } from './itinerary';

/**
 * 백엔드 응답이 프론트가 기대하는 모양인지 실행 시점에 확인하는 수기 타입 가드 —
 * S15P21E201-776.
 *
 * <p>`RecommendationJobPollDto`·`ItineraryDto`는 TypeScript 타입일 뿐이라 컴파일 후에는
 * 사라진다. `apiRequest<ItineraryDto>(...)`는 실제로 받은 JSON이 그 모양인지 아무것도
 * 확인하지 않는다 — 백엔드가 필드 이름을 바꿔도 프론트는 컴파일도 테스트도 안 죽고
 * 런타임에만(화면이 빈 채로 죽거나 undefined를 렌더링하면서) 깨진다.
 *
 * <p>백엔드 쪽 대응 계약 검사는 backend의
 * `com.gabolle.backend.functional.ResponseBodyContractFunctionalTest`(같은 방식,
 * S15P21E201-789가 먼저 세운 패턴)에 있다 — 아래 두 함수가 확인하는 필드 이름은 그 테스트가
 * 확인하는 이름과 반드시 같아야 한다. 어느 한쪽 필드명이 바뀌면:
 * - 백엔드가 먼저 바뀌면: 이쪽(`apiContracts.test.ts`)의 실제 응답 예시 fixture가 그 이름을
 *   더는 안 담고 있으므로, 그 fixture로 도는 이 파일의 테스트가 빨개진다
 * - 프론트가 먼저(타입만) 바뀌면: 백엔드는 그대로이므로 `ResponseBodyContractFunctionalTest`
 *   쪽 실제 서버 응답과 이 파일의 fixture가 어긋나 이 파일의 테스트가 빨개진다
 *
 * <p>즉 두 계약 검사가 어긋나는 방향과 무관하게 **먼저 눈에 띄는 쪽은 언제나 이 파일**이다
 * — 백엔드 테스트는 백엔드 자신과만 비교하고, 이 파일은 "실제로 서버가 주는 것"이라고 못박은
 * fixture와 비교하기 때문이다. fixture가 실제 서버 응답과 어긋나기 시작하면 사람이 그 fixture를
 * 최신 서버 응답으로 갱신해야 하고, 그 판단이 이 파일이 존재하는 이유다.
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
  if (typeof value.startsAt !== 'string') return false;
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
