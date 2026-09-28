// 홈 처음 안내 — 동백이 설명이 강조된 시작 바 위에 겹치던 것(S15P21E201-1731).
//
// 🔴 2026-09-26 발표 시연 점검(폰 390×844 = 아이폰 12~15 크기, 가입 직후 홈). 위에 「첫 여행 준비」 카드가 있어
//    시작 바가 아래로 밀렸고(구멍 y 485~591), 그래서 시작 바 설명은 바 «위»로 올라갔다(-1448). 그 경우 「위로
//    올리면 동백이 설명과 안 만난다」고 보고 접기 검사를 건너뛰었는데, 동백이 설명은 오른쪽 아래 제자리라
//    이번엔 «시작 바 자체»를 덮었다(제목 y 544~568 — 칩 줄 위). 겹침을 설명끼리만이 아니라 구멍과도 잰다.
import { shouldFoldAssistantCopy } from '@/onboarding/HomeCoach';

declare const require: (id: string) => any;
declare const __dirname: string;

const GAP = 12;

describe('동백이 설명을 접을지', () => {
  it('🔴 390×844 실측 — 시작 바 설명이 위로 올라가도, 동백이 설명이 시작 바를 덮으면 접는다', () => {
    expect(shouldFoldAssistantCopy({
      placement: 'above', startBarBottom: 591, startCopyTop: 607, startCopyHeight: 250, assistantCopyTop: 534, gap: GAP,
    })).toBe(true);
  });

  it('키 큰 폰 — 위로 올라가고 동백이 설명이 시작 바보다 한참 아래면 안 접는다', () => {
    expect(shouldFoldAssistantCopy({
      placement: 'above', startBarBottom: 700, startCopyTop: 716, startCopyHeight: 250, assistantCopyTop: 1500, gap: GAP,
    })).toBe(false);
  });

  it('아래에 둔 시작 바 설명이 동백이 설명 자리까지 내려오면 접는다 — 전부터 있던 규칙(-1403)', () => {
    expect(shouldFoldAssistantCopy({
      placement: 'below', startBarBottom: 300, startCopyTop: 316, startCopyHeight: 250, assistantCopyTop: 534, gap: GAP,
    })).toBe(true);
  });

  it('아래에 두어도 들어가면 안 접는다', () => {
    expect(shouldFoldAssistantCopy({
      placement: 'below', startBarBottom: 300, startCopyTop: 316, startCopyHeight: 150, assistantCopyTop: 534, gap: GAP,
    })).toBe(false);
  });

  it('높이를 아직 못 쟀고 시작 바와도 안 만나면 안 접는다', () => {
    expect(shouldFoldAssistantCopy({
      placement: 'below', startBarBottom: 300, startCopyTop: 316, startCopyHeight: 0, assistantCopyTop: 534, gap: GAP,
    })).toBe(false);
  });

  it('시작 바를 못 쟀으면(구멍 없음) 구멍과는 안 잰다', () => {
    expect(shouldFoldAssistantCopy({
      placement: 'below', startBarBottom: null, startCopyTop: 236, startCopyHeight: 250, assistantCopyTop: 534, gap: GAP,
    })).toBe(false);
  });
});

// 되돌려도 화면은 그려지고 겹침만 돌아온다 — 그래서 화면이 이 판정을 «쓰는지»도 잰다.
describe('안내 화면이 이 판정을 쓴다', () => {
  it('접기를 shouldFoldAssistantCopy 로 정한다', () => {
    const { readFileSync } = require('fs');
    const { join } = require('path');
    const source = readFileSync(join(__dirname, '..', 'HomeCoach.tsx'), 'utf8') as string;
    expect(source).toContain('const folded = shouldFoldAssistantCopy({');
  });
});
