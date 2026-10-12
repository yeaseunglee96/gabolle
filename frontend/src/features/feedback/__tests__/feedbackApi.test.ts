import { ApiClientError } from '@/api/client';
import { CONTENT_MAX, CONTENT_MIN, draftProblems, submitFeedback } from '../feedbackApi';

jest.mock('@/api/client', () => {
  const actual = jest.requireActual('@/api/client');
  return { ...actual, apiRequest: jest.fn() };
});

const { apiRequest } = jest.requireMock('@/api/client') as { apiRequest: jest.Mock };

const meta = { appVersion: '1.0.2', platform: 'web', osVersion: 'web', language: 'ko', screen: 'me', tripId: null };

describe('의견 보내기 — S15P21E201-2049', () => {
  beforeEach(() => apiRequest.mockReset());

  it('내용은 10~1000자, 이메일은 형식, 사진은 3장까지 — 서버와 같은 선', () => {
    expect(CONTENT_MIN).toBe(10);
    expect(CONTENT_MAX).toBe(1000);
    expect(draftProblems({ type: 'BUG', content: '짧아요', replyEmail: '', screenshotUrls: [] })).toContain('content');
    expect(draftProblems({ type: 'BUG', content: '가'.repeat(1001), replyEmail: '', screenshotUrls: [] })).toContain('content');
    expect(draftProblems({ type: 'BUG', content: '열 글자가 넘는 내용입니다', replyEmail: 'x@', screenshotUrls: [] })).toEqual(['replyEmail']);
    expect(draftProblems({ type: 'IDEA', content: '열 글자가 넘는 내용입니다', replyEmail: 'a@b.co', screenshotUrls: ['1', '2', '3', '4'] })).toEqual(['screenshotUrls']);
    expect(draftProblems({ type: 'OTHER', content: '열 글자가 넘는 내용입니다', replyEmail: '', screenshotUrls: [] })).toEqual([]);
  });

  it('보낼 때 함께 보내는 정보를 그대로 싣는다', async () => {
    apiRequest.mockResolvedValue({ feedbackId: 'f1', status: 'RECEIVED' });
    const result = await submitFeedback({ type: 'BUG', content: '  일정 화면이 안 열려요  ', replyEmail: '', screenshotUrls: [] }, meta, null);
    expect(result.state).toBe('success');
    const [path, options] = apiRequest.mock.calls[0];
    expect(path).toBe('/api/v1/feedback');
    expect(options.method).toBe('POST');
    expect(options.body).toMatchObject({ type: 'BUG', content: '일정 화면이 안 열려요', replyEmail: null, appVersion: '1.0.2', screen: 'me' });
  });

  it('하루 상한은 limited 로 바꾼다', async () => {
    apiRequest.mockRejectedValue(new ApiClientError('limit', 'FEEDBACK_RATE_LIMITED', 429, []));
    const result = await submitFeedback({ type: 'IDEA', content: '열 글자가 넘는 내용입니다', replyEmail: '', screenshotUrls: [] }, meta, 'tok');
    expect(result.state).toBe('limited');
  });
});
