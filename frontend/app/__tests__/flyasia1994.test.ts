// S15P21E201-1994 — 같은 조건 새 여행, 초안 이어짐, 유아차 미확인 창, 한국어 문장 카드.

declare const __dirname: string;
const { readFileSync } = require('fs');

const { join } = require('path');
import { stableIdempotencyKey, toCreateTripPayload } from '@/api/tripApi';
import { EMPTY_PLAN } from '@/plan/PlanProvider';

const root = join(__dirname, '..', '..') as string;
const read = (rel: string) => readFileSync(join(root, rel), 'utf8') as string;

describe('9 — 같은 조건이라도 새 시도는 새 키', () => {
  const draft = { ...EMPTY_PLAN, startDate: '2026-10-16', endDate: '2026-10-17' };
  const payload = toCreateTripPayload(draft as never);
  it('시도 이름표가 다르면 키가 다르다', () => {
    expect(stableIdempotencyKey(payload, 'a1')).not.toBe(stableIdempotencyKey(payload, 'b2'));
  });
  it('같은 시도의 재전송은 같은 키다', () => {
    expect(stableIdempotencyKey(payload, 'a1')).toBe(stableIdempotencyKey(payload, 'a1'));
  });
});

describe('10 — 새로 켠 앱에서 중간 단계면 먼저 묻는다', () => {
  it('질문 화면에 「이어서 / 처음부터」 창이 있다', () => {
    const src = read('app/(plan)/questions.tsx');
    expect(src).toContain('plan-resume-prompt');
    expect(src).toContain("tx('처음부터 새로', 'Start over')");
  });
});

describe('3 — 유아차 미확인은 창이 아니라 결과 화면 안 안내', () => {
  it('승차권 화면이 inline 으로 쓴다', () => {
    expect(read('app/(plan)/generating.tsx')).toMatch(/<AccessibilityUnverifiedModal\s+inline/);
  });
});

describe('11 — 한국어 화면 문장 카드 안내', () => {
  it('한국어일 때만 한 줄 알린다', () => {
    expect(read('src/components/PlacePhraseBrowser.tsx')).toContain("language === 'ko' ? <Text testID=\"phrase-ko-note\"");
  });
});
