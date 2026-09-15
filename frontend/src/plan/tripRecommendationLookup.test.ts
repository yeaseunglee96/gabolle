import { findLatestRecommendationJob } from './recommendations';

// 추천 결과를 다시 열면 빈 화면이던 것을 고친다 — S15P21E201-1002.
// 서버 경로는 GET /api/v1/trips/{tripId}/recommendation-jobs (S15P21E201-1001):
// 최신순 목록, 없는 여행은 404, 내 여행인데 추천이 없으면 빈 배열.
function respondWith(payload: unknown, status = 200) {
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL) => {
    if (String(input).includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 't', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    return new Response(JSON.stringify(payload), { status, headers: { 'content-type': 'application/json' } });
  }) as unknown as typeof fetch;
}

const job = (jobId: string, status: string, type = 'ITINERARY_GENERATION') => ({ jobId, status, type });
const ok = (jobs: unknown[]) => ({ data: jobs, error: null, meta: { requestId: 'r1' } });

describe('여행 ID 로 다시 볼 추천 고르기', () => {
  it('가장 최근이 실패였어도 그 앞의 성공한 추천을 쓴다', async () => {
    respondWith(ok([job('newest-failed', 'FAILED'), job('older-ok', 'SUCCEEDED')]));
    await expect(findLatestRecommendationJob('trip-1', 'token')).resolves.toEqual({ state: 'found', jobId: 'older-ok' });
  });

  it('일정 편집 작업은 추천 결과로 쓰지 않는다', async () => {
    respondWith(ok([job('edit', 'SUCCEEDED', 'ITEM_REMOVE'), job('generation', 'SUCCEEDED')]));
    await expect(findLatestRecommendationJob('trip-1', 'token')).resolves.toEqual({ state: 'found', jobId: 'generation' });
  });

  it('아직 만드는 중이면 그 작업을 알려준다', async () => {
    respondWith(ok([job('running', 'RUNNING')]));
    await expect(findLatestRecommendationJob('trip-1', 'token')).resolves.toEqual({ state: 'in-progress', jobId: 'running' });
  });

  it('내 여행인데 추천을 만든 적 없으면 "없음" 이다', async () => {
    respondWith(ok([]));
    await expect(findLatestRecommendationJob('trip-1', 'token')).resolves.toEqual({ state: 'none' });
  });

  it('🔴 없는 여행(404 TRIP_NOT_FOUND)은 빈 목록과 다르게 다룬다', async () => {
    respondWith({ data: null, error: { code: 'TRIP_NOT_FOUND', message: '여행을 찾을 수 없습니다.' }, meta: { requestId: 'r1' } }, 404);
    await expect(findLatestRecommendationJob('trip-1', 'token')).resolves.toEqual({ state: 'trip-not-found' });
  });

  // 🔴 배포 전 서버는 이 주소에 405 를 낸다 — 같은 주소의 POST(추천 생성)가 예전부터 있어서
  // "없는 주소"가 아니라 "있는 주소인데 GET 은 안 받는다"이기 때문이다 (백엔드 실측 2026-09-15).
  // 이것을 「그 여행을 찾을 수 없어요」로 그리면 멀쩡한 여행을 없다고 말하게 된다.
  it('경로가 아직 배포 안 된 서버(405)는 「아직 없음」으로 다룬다', async () => {
    respondWith({ data: null, error: { code: 'METHOD_NOT_ALLOWED', message: 'Request method GET is not supported' }, meta: { requestId: 'r1' } }, 405);
    await expect(findLatestRecommendationJob('trip-1', 'token')).resolves.toEqual({ state: 'none' });
  });

  it('TRIP_NOT_FOUND 가 아닌 404 는 여행이 없다고 단정하지 않는다', async () => {
    respondWith({ data: null, error: { code: 'NOT_FOUND', message: 'No handler' }, meta: { requestId: 'r1' } }, 404);
    await expect(findLatestRecommendationJob('trip-1', 'token')).resolves.toEqual({ state: 'none' });
  });

  it('실패·만료만 남았으면 볼 수 있는 추천이 없다', async () => {
    respondWith(ok([job('a', 'FAILED'), job('b', 'EXPIRED'), job('c', 'CANCELLED')]));
    await expect(findLatestRecommendationJob('trip-1', 'token')).resolves.toEqual({ state: 'none' });
  });
});
