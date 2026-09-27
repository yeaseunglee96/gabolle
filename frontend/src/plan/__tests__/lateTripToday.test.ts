// 오늘 늦게 만든 여행 — 새 실패 코드 문구 · 비어 있는 오늘 문구(S15P21E201-1739, 백엔드 !1734 계약).
//
// 🔴 ① 오늘 출발 당일치기를 부산 20:31 뒤에 만들면 서버가 ITINERARY_NO_TIME_LEFT_TODAY(다시 시도 불가)로 끝낸다.
//      앱은 이 코드를 몰라 「잠시 후 다시 시도해 주세요」 + 「조건을 조금 넓혀서 다시 해 볼까요?」를 보였다 —
//      다시 해도 같은 답이고, 넓힐 조건도 없다. 사용자가 정한 말 「오늘은 남은 시간이 없어요」로 날짜를 내일로 이끈다.
// 🔴 ② 여러 날 여행을 20:31 뒤에 만들면 첫날(오늘)이 0곳이다. 화면은 「이 날에는 아직 장소가 없어요」 — 「아직」이
//      곧 채워질 것처럼 읽힌다. 비어 있는 오늘 뒤에 일정이 있으면 「오늘은 늦어서 내일부터 짰어요」.
declare const require: (id: string) => any;
declare const __dirname: string;

import { setApiLanguage } from '@/api/client';
import { setCurrentLanguage } from '@/i18n/languages';
import { adaptPolledJob } from '@/plan/recommendationJob';
import { isSkippedToday } from '@/trip/page/emptyDay';

const failed = { jobId: 'j', status: 'FAILED' as const, progress: { stage: 'PLANNING', percent: 100 }, failure: { code: 'ITINERARY_NO_TIME_LEFT_TODAY', detail: null }, retryable: false, pollAfterSeconds: null };

describe('① 오늘 남은 시간이 없을 때의 실패', () => {
  // 앱은 화면 언어와 서버용 언어를 함께 바꾼다 — 실패 문구는 화면 언어를 본다(S15P21E201-1776).
  afterEach(() => { setApiLanguage('ko'); setCurrentLanguage('ko'); });

  it('🔴 한국어 — 「오늘은 남은 시간이 없어요」로 날짜를 내일로 이끈다', () => {
    setApiLanguage('ko'); setCurrentLanguage('ko');
    const job = adaptPolledJob('j', failed);
    expect(job.state).toBe('failed');
    expect(job.errorMessage).toBe('오늘은 남은 시간이 없어요. 여행을 내일부터로 바꿔 주세요.');
  });

  it('영어', () => {
    setApiLanguage('en'); setCurrentLanguage('en');
    expect(adaptPolledJob('j', failed).errorMessage).toBe("There's no time left today. Try starting your trip tomorrow.");
  });

  it('🔴 실패 코드를 화면까지 넘긴다 — 굵은 줄(「조건을 조금 넓혀서…」)을 이 코드일 때 바꾸려면 필요하다', () => {
    expect(adaptPolledJob('j', failed).failureCode).toBe('ITINERARY_NO_TIME_LEFT_TODAY');
    expect(adaptPolledJob('j', { ...failed, status: 'SUCCEEDED' as const, failure: null }).failureCode ?? null).toBeNull();
  });
});

describe('② 비어 있는 오늘', () => {
  const TODAY = '2026-09-26';
  const days = (a: number, b: number) => [{ date: TODAY, items: Array(a).fill(0) }, { date: '2026-09-27', items: Array(b).fill(0) }];

  it('🔴 오늘이 비고 뒤에 일정이 있으면 — 늦게 만들어 비운 오늘', () => {
    expect(isSkippedToday({ days: days(0, 3), dayIndex: 0, today: TODAY })).toBe(true);
  });
  it('오늘에 일정이 있으면 아니다', () => {
    expect(isSkippedToday({ days: days(2, 3), dayIndex: 0, today: TODAY })).toBe(false);
  });
  it('뒤에도 일정이 없으면 아니다 — 늦어서 비운 것이라고 말할 근거가 없다', () => {
    expect(isSkippedToday({ days: days(0, 0), dayIndex: 0, today: TODAY })).toBe(false);
  });
  it('오늘이 아닌 빈 날은 아니다', () => {
    expect(isSkippedToday({ days: days(3, 0), dayIndex: 1, today: TODAY })).toBe(false);
  });
});

describe('화면이 이것을 쓴다', () => {
  const { readFileSync } = require('fs');
  const { join } = require('path');
  const read = (...p: string[]) => readFileSync(join(__dirname, '..', '..', '..', ...p), 'utf8') as string;

  it('🔴 생성 실패 화면(폰) — 이 코드일 때 굵은 줄을 날짜 쪽으로', () => {
    expect(read('app', '(plan)', 'generating.tsx')).toContain("job.failureCode === 'ITINERARY_NO_TIME_LEFT_TODAY'");
  });
  it('🔴 여행 화면(폰·넓은 화면) — 비어 있는 오늘', () => {
    expect(read('src', 'trip', 'page', 'TripPageMobile.tsx')).toContain('isSkippedToday({');
    expect(read('src', 'trip', 'page', 'TripPageDesktop.tsx')).toContain('isSkippedToday({');
  });
});
