// 「큰 짐이 있어요」를 다시 묻는다.
//
// 🔴 S15P21E201-1855 에서 「백엔드에 HEAVY_LUGGAGE 를 읽는 코드가 한 줄도 없다」고 뺐는데, 틀린 근거였다.
//    서버는 이름으로 따로 읽지 않을 뿐, 휠체어·유아차와 같은 이동 조건 갈래(BaselineCandidateScorer.evaluateMobility)에서
//    큰 짐에도 경사 판정을 했다 — 「반드시」면 8.33% 넘게 가파른 곳을 뺐다(2026-09-25 「휠체어·유아차·큰 짐 판정을
//    경사 하나로 묶는다」). 빼면 큰 짐 여행에서 비탈 거르기가 사라진다.
//
//    뺀 까닭이던 「확인 안 됨이 거의 모든 곳에 뜬다」는 서버에서 고쳤다 — 큰 짐은 이제 접근성 표식을 찾지 않고
//    경사로만 가르므로 그 경고가 안 붙는다. 그래서 이 앱도 안내 창은 휠체어·유아차만 센다(accessibilityNotice.ts).
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

import { toCreateTripPayload } from '@/api/tripApi';
import { EMPTY_PLAN } from '@/plan/PlanProvider';

const read = (...parts: string[]): string => readFileSync(join(__dirname, '..', '..', '..', ...parts), 'utf8');
const base = { ...EMPTY_PLAN, startDate: '2026-10-12', endDate: '2026-10-13' };
const statusOf = (draft: typeof base, key: string) =>
  toCreateTripPayload(draft).constraints.find((c) => c.constraintKey === key)?.answerStatus;

describe('큰 짐 문항', () => {
  it('🔴 조건 화면이 휠체어·유아차·큰 짐을 모두 묻는다', () => {
    // 이동 보조는 여행 만들기 확인 표에서 고른다(S15P21E201-1865) — 화면 몸은 PlanSteps.tsx.
    const source = read('src', 'plan', 'PlanSteps.tsx');
    expect(source).toContain("['wheelchair', '휠체어', 'Wheelchair']");
    expect(source).toContain("['stroller', '유아차', 'Stroller']");
    expect(source).toContain("['luggage', '큰 짐', 'Large luggage']");
  });

  it('🔴 큰 짐을 고르면 서버로 HEAVY_LUGGAGE 를 「골랐다」로 보낸다', () => {
    const luggage = toCreateTripPayload({ ...base, luggage: true }).constraints.find((c) => c.constraintKey === 'HEAVY_LUGGAGE');
    expect(luggage).toMatchObject({ type: 'MOBILITY', answerStatus: 'SELECTED' });
  });

  it('안 고르면·모르면 휠체어와 같은 모양으로 보낸다', () => {
    expect(statusOf({ ...base, luggage: false }, 'HEAVY_LUGGAGE')).toBe(statusOf({ ...base, wheelchair: false }, 'WHEELCHAIR'));
    expect(statusOf({ ...base, luggage: null }, 'HEAVY_LUGGAGE')).toBe(statusOf({ ...base, wheelchair: null }, 'WHEELCHAIR'));
  });

  it('🔴 「확인 안 됨」 안내 창은 큰 짐을 세지 않는다 — 서버가 큰 짐에는 그 경고를 안 붙인다', () => {
    expect(read('src', 'plan', 'accessibilityNotice.ts')).not.toContain("'HEAVY_LUGGAGE'");
  });

  it('실패 안내의 이름표가 있다 — 경사 때문에 다 빠지면 「큰 짐」이 막았다고 말한다', () => {
    expect(read('src', 'plan', 'blockedByMessage.ts')).toContain('HEAVY_LUGGAGE');
  });
});
