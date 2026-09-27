// 일정 만들기 실패 문구는 화면 언어로 (S15P21E201-1776).
import { setCurrentLanguage } from '@/i18n/languages';
import { adaptPolledJob, unavailableJob } from '@/plan/recommendationJob';

afterEach(() => setCurrentLanguage('ko'));

const failed = (code: string) => adaptPolledJob('j1', { status: 'FAILED', progress: { stage: null, percent: 0 }, failure: { code, detail: null } } as never);

it('🔴 일본어 화면에서는 영어가 아니라 일본어다', () => {
  setCurrentLanguage('ja');
  const message = failed('SERVER_BUSY').errorMessage ?? '';
  expect(message).not.toMatch(/[A-Za-z]{4,}/);
  expect(message).not.toMatch(/[가-힣]/);
});

it('🔴 영어 화면에서 「지금은 일정을 만들 수 없어요」가 한국어로 남지 않는다', () => {
  setCurrentLanguage('en');
  expect(unavailableJob().errorMessage).toMatch(/can't build an itinerary/i);
});

it('🔴 만료 문구도 영어로', () => {
  setCurrentLanguage('en');
  const job = adaptPolledJob('j1', { status: 'EXPIRED', progress: { stage: null, percent: 0 }, failure: null } as never);
  expect(job.errorMessage).toMatch(/expired/i);
});

it('한국어 화면은 그대로', () => {
  setCurrentLanguage('ko');
  expect(failed('SERVER_BUSY').errorMessage).toBe('지금 요청이 많아요. 잠시 뒤 다시 시도해 주세요.');
});
