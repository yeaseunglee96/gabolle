import { isItineraryDto, isRecommendationJobPollDto } from './apiContracts';

/** 실제 백엔드가 보낸 응답 예시 — S15P21E201-776. */
const REAL_JOB_POLL_RESPONSE = {
  jobId: '02ae7ea5-0688-4d8c-a310-b42031ff47a2',
  status: 'SUCCEEDED',
  progress: { stage: 'PERSISTENCE', percent: 100 },
  failure: null,
  retryable: false,
  pollAfterSeconds: null,
};

const REAL_ITINERARY_RESPONSE = {
  id: 'c24e4b07-c11d-4c7b-5c57-af1bbc339f83',
  title: '9월 20일 - 9월 21일 부산 여행',
  version: 1,
  days: [
    {
      date: '2026-09-20',
      items: [
        {
          id: 'a1b2c3d4-0000-4000-8000-000000000001',
          startsAt: '2026-09-20T10:00:00+09:00',
          title: '여정테스트장소0',
          locked: false,
          placeId: '39d7f73b-b915-4f49-a4fc-a572d3a24216',
        },
      ],
    },
  ],
  totalEstimatedCostKrw: 0,
  totalWalkingMeters: 0,
  fallbackMode: 'BASELINE',
  myRole: 'OWNER',
  canEdit: true,
};

describe('isRecommendationJobPollDto', () => {
  it('실제 백엔드 응답을 받아들인다 — GET /api/v1/jobs/{jobId}', () => {
    expect(isRecommendationJobPollDto(REAL_JOB_POLL_RESPONSE)).toBe(true);
  });

  it('🔴 백엔드가 jobId를 id로 이름을 바꾸면 빨개진다', () => {
    const { jobId, ...rest } = REAL_JOB_POLL_RESPONSE;
    const renamed = { ...rest, id: jobId };
    expect(isRecommendationJobPollDto(renamed)).toBe(false);
  });

  it('🔴 백엔드가 progress를 숫자 하나로 납작하게 바꾸면 빨개진다', () => {
    const flattened = { ...REAL_JOB_POLL_RESPONSE, progress: 100 };
    expect(isRecommendationJobPollDto(flattened)).toBe(false);
  });

  it('모르는 status 값은 거부한다 — 서버가 새 상태를 추가했는데 프론트가 아직 모르는 경우', () => {
    const unknownStatus = { ...REAL_JOB_POLL_RESPONSE, status: 'RETRYING' };
    expect(isRecommendationJobPollDto(unknownStatus)).toBe(false);
  });
});

describe('isItineraryDto', () => {
  it('실제 백엔드 응답을 받아들인다 — GET /api/v1/itineraries/{id}', () => {
    expect(isItineraryDto(REAL_ITINERARY_RESPONSE)).toBe(true);
  });

  it('🔴 백엔드가 days를 items로 이름을 바꾸면 빨개진다', () => {
    const { days, ...rest } = REAL_ITINERARY_RESPONSE;
    const renamed = { ...rest, itineraryDays: days };
    expect(isItineraryDto(renamed)).toBe(false);
  });

  it('🔴 항목의 placeId가 빠지면 빨개진다 — S15P21E201-744가 "다녀오셨나요" 평가에 쓰는 칸이다', () => {
    const withoutPlaceId = {
      ...REAL_ITINERARY_RESPONSE,
      days: [{ date: '2026-09-20', items: [{ ...REAL_ITINERARY_RESPONSE.days[0].items[0], placeId: undefined }] }],
    };
    expect(isItineraryDto(withoutPlaceId)).toBe(false);
  });

  it('선택 칸(fallbackMode 등)이 빠져도 필수 칸만 있으면 통과한다', () => {
    const { fallbackMode: _fallbackMode, myRole: _myRole, canEdit: _canEdit, ...withoutOptionals } =
      REAL_ITINERARY_RESPONSE;
    expect(isItineraryDto(withoutOptionals)).toBe(true);
  });
});
