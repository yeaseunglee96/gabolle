// 서버가 모르는 응답일 때 화면에 가는 말 — S15P21E201-1672.
//
// 🔴 이 시험이 지키는 것(조율 세션 결정):
//    ① 서버가 우리 봉투 모양이 아닌 응답(없는 주소일 때 Spring 기본 응답 등)이나 501 을 주면, 앱은 그 자리의 정한 문장을
//       쓴다. 전에는 요청 함수가 만든 「요청을 처리하지 못했어요.」가 그대로 떴다 — 무엇이 안 됐는지도, 무엇을 하면 되는지도 없다.
//    ② 코드가 붙은 서버 문장(원래 사용자용 한국어)은 그대로 둔다 — 서버가 이유를 알고 보낸 말을 덮지 않는다.
import { ApiClientError } from '@/api/client';
import { isUnknownResponse } from '@/api/errorText';
import { getSharedItinerary } from '@/share/sharedItinerary';
import { ensureCourseItinerary, type TripCourse } from '@/plan/tripCourses';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn() }));
const { apiRequest } = jest.requireMock('@/api/client') as { apiRequest: jest.Mock };

/** 봉투 모양이 아닌 응답을 받았을 때 요청 함수가 던지는 것(client.ts) — 코드가 없어서 REQUEST_FAILED 다. */
const notEnvelope = (status: number) => new ApiClientError('요청을 처리하지 못했어요.', 'REQUEST_FAILED', status);
const course = { id: 'B', itineraryId: null } as unknown as TripCourse;

beforeEach(() => apiRequest.mockReset());

describe('서버가 모르는 응답인가', () => {
  it('봉투 모양이 아닌 응답·501 은 그렇다', () => {
    expect(isUnknownResponse(notEnvelope(404))).toBe(true);
    expect(isUnknownResponse(notEnvelope(500))).toBe(true);
    expect(isUnknownResponse(new ApiClientError('구현되지 않았어요.', 'NOT_IMPLEMENTED', 501))).toBe(true);
  });

  it('🔴 코드가 붙은 404·400 은 아니다 — 서버가 이유를 알고 보낸 말이다', () => {
    expect(isUnknownResponse(new ApiClientError('코스를 찾을 수 없어요.', 'COURSE_NOT_FOUND', 404))).toBe(false);
    expect(isUnknownResponse(new ApiClientError('여행 기간 밖이에요.', 'TRIP_DATE_OUT_OF_RANGE', 400))).toBe(false);
    expect(isUnknownResponse(new Error('boom'))).toBe(false);
  });
});

describe('코스 확정 실패', () => {
  it('🔴 서버가 모르는 응답이면 「이 코스로 일정을 만들지 못했어요.」', async () => {
    apiRequest.mockRejectedValueOnce(notEnvelope(404));
    await expect(ensureCourseItinerary('trip-1', course, 'token')).resolves.toEqual({ state: 'error', message: '이 코스로 일정을 만들지 못했어요.' });
  });

  it('코드가 붙은 서버 문장은 그대로', async () => {
    apiRequest.mockRejectedValueOnce(new ApiClientError('코스를 찾을 수 없어요.', 'COURSE_NOT_FOUND', 404));
    await expect(ensureCourseItinerary('trip-1', course, 'token')).resolves.toEqual({ state: 'error', message: '코스를 찾을 수 없어요.' });
  });
});

describe('공유 링크', () => {
  it('🔴 서버가 모르는 응답이면 「공유 일정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.」', async () => {
    apiRequest.mockRejectedValueOnce(notEnvelope(404));
    await expect(getSharedItinerary('tok')).resolves.toEqual({ state: 'error', message: '공유 일정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.' });
    apiRequest.mockRejectedValueOnce(new ApiClientError('구현되지 않았어요.', 'NOT_IMPLEMENTED', 501));
    await expect(getSharedItinerary('tok')).resolves.toEqual({ state: 'error', message: '공유 일정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.' });
  });

  it('코드가 붙은 서버 문장은 그대로 — 만료·없음은 전처럼 따로 간다', async () => {
    apiRequest.mockRejectedValueOnce(new ApiClientError('공유가 꺼진 여행이에요.', 'SHARE_DISABLED', 403));
    await expect(getSharedItinerary('tok')).resolves.toEqual({ state: 'error', message: '공유가 꺼진 여행이에요.' });
    apiRequest.mockRejectedValueOnce(new ApiClientError('만료됐어요.', 'SHARE_LINK_EXPIRED', 410));
    await expect(getSharedItinerary('tok')).resolves.toEqual({ state: 'expired' });
  });
});
