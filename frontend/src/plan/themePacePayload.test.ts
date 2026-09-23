// 여행 테마·여행 기분이 여행 생성 요청에 실제로 실리는가 — S15P21E201-1535.
//
// 🔴 둘 다 «물어보기만 하고 안 보내던» 입력이다(2026-09-23 운영 실측: 최근 여행 전부 테마 없음 · pace 비어 있음).
//    질문 화면의 테마 칩은 draft.preferences 만 바꾸고 답 상태(preferenceAnswerStatus.category)는 안 바꾼다.
//    서버는 SELECTED 인 답만 읽으므로, 상태를 요청을 만드는 자리에서 정한다.
import { toCreateTripPayload } from '@/api/tripApi';
import { EMPTY_PLAN } from '@/plan/PlanProvider';

const base = { ...EMPTY_PLAN, startDate: '2026-10-12', endDate: '2026-10-13' };
const answer = (payload: ReturnType<typeof toCreateTripPayload>, dimension: string) =>
  payload.preferences.find((entry) => entry.dimension === dimension);

describe('여행 생성 요청의 테마', () => {
  it('칩을 고르면 상태가 그대로(UNKNOWN)여도 SELECTED 로, 고른 코드를 싣는다', () => {
    const payload = toCreateTripPayload({ ...base, preferences: ['SEA_BEACH', 'FOOD'] });
    expect(answer(payload, 'category')).toEqual({ dimension: 'category', value: '["SEA_BEACH","FOOD"]', answerStatus: 'SELECTED' });
  });

  it('안 고르면 원래 상태 그대로 값 없이 보낸다 — 「상관없음」을 「골랐다」로 바꾸지 않는다', () => {
    expect(answer(toCreateTripPayload(base), 'category')).toEqual({ dimension: 'category', value: null, answerStatus: 'UNKNOWN' });
    const skipped = toCreateTripPayload({ ...base, preferenceAnswerStatus: { ...base.preferenceAnswerStatus, category: 'SKIPPED' } });
    expect(answer(skipped, 'category')?.answerStatus).toBe('SKIPPED');
  });
});

describe('여행 생성 요청의 여행 기분(pace)', () => {
  it('고르면 서버 어휘(RELAXED·BALANCED·PACKED) 그대로 싣는다', () => {
    expect(answer(toCreateTripPayload({ ...base, paceLevel: 'RELAXED' }), 'pace')).toEqual({ dimension: 'pace', value: '"RELAXED"', answerStatus: 'SELECTED' });
  });

  it('안 고르면 UNKNOWN — 서버가 기본(하루 4곳)을 쓴다', () => {
    expect(answer(toCreateTripPayload(base), 'pace')).toEqual({ dimension: 'pace', value: null, answerStatus: 'UNKNOWN' });
  });
});
