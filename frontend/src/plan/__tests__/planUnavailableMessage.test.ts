// 일정 생성 서버가 없거나 닿지 않을 때 사용자에게 하는 말 — S15P21E201-1669.
//
// 🔴 이 시험이 지키는 것: 서버가 404·501 을 주면 서버 오류의 원문(「No static resource …」)이 그대로 화면에 떴다.
//    쓰이지 않던 기본 문구도 「일정 생성 서버가 아직 준비되지 않았어요」였다. S15P21E201-1664 와 같은 갈래로
//    원인을 가르지 않고 한 문장만 말한다 — 무엇을 하면 되는지(잠시 뒤 다시)와, 입력한 것은 남아 있다는 것.
import { ApiClientError, ApiUnavailableError } from '@/api/client';
import { PLAN_UNAVAILABLE_MESSAGE } from '@/api/errorText';
import { MESSAGE_EN } from '@/i18n/messages';
import { TRANSLATIONS } from '@/i18n/translations';
import { createRecommendationJobAdapter, unavailableJob } from '@/plan/recommendationJob';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn() }));
const { apiRequest } = jest.requireMock('@/api/client') as { apiRequest: jest.Mock };

describe('일정을 만들 수 없을 때의 한 문장', () => {
  it('정해진 문장이다 — 번역도 있다', () => {
    expect(PLAN_UNAVAILABLE_MESSAGE).toBe('지금은 일정을 만들 수 없어요. 잠시 뒤 다시 시도해 주세요. 입력한 조건은 그대로 남아 있어요.');
    expect(MESSAGE_EN[PLAN_UNAVAILABLE_MESSAGE]).toBeTruthy();
    expect(TRANSLATIONS[PLAN_UNAVAILABLE_MESSAGE]).toBeTruthy();
  });

  it('🔴 서버가 기능을 모르면(404) 서버 원문 대신 그 문장이다', async () => {
    apiRequest.mockRejectedValueOnce(new ApiClientError('No static resource api/v1/jobs/job-1.', 'NOT_FOUND', 404));
    await expect(createRecommendationJobAdapter('token').poll('job-1')).resolves.toMatchObject({ state: 'unavailable', jobId: 'job-1', errorMessage: PLAN_UNAVAILABLE_MESSAGE });
  });

  it('연결이 안 될 때도 같은 문장이다', async () => {
    apiRequest.mockRejectedValueOnce(new ApiUnavailableError());
    await expect(createRecommendationJobAdapter('token').poll('job-1')).resolves.toMatchObject({ state: 'unavailable', errorMessage: PLAN_UNAVAILABLE_MESSAGE });
  });

  it('기본값도 그 문장이다 — 「서버」라는 말이 없다', () => {
    expect(unavailableJob().errorMessage).toBe(PLAN_UNAVAILABLE_MESSAGE);
    expect(Object.keys(TRANSLATIONS).filter((ko) => ko.includes('일정 생성 서버'))).toEqual([]);
  });
});
