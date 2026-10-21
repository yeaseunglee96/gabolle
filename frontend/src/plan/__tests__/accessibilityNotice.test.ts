// 휠체어 안내 창의 숫자 — 승차권에 보이는 일정만 센다(S15P21E201-1732).
//
// 🔴 2026-09-26 발표 시연 점검. 휠체어를 고르고 만든 일정의 승차권은 방문지 8곳인데, 그 위에 뜬 창이
//    「이번 일정 24곳 중 23곳은 … 확인되지 않았어요」라고 했다. 추천 결과 목록(코스 A·B·C 세 안을 합친 24곳)으로
//    셌기 때문이다. 서버는 일정 응답의 항목마다 warningCodes 를 준다(실측: 8곳 중 7곳, 일정 단위
//    accessibilityUnverifiedCount 도 7). 사용자 결정: 지금 보이는 일정만 센다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');

import { accessibilityHeadline, itineraryAccessibilityCounts, selectedMobilityAids } from '../accessibilityNotice';

const item = (warningCodes?: string[]) => ({ warningCodes });
const day = (...items: Array<{ warningCodes?: string[] }>) => ({ items });

describe('보이는 일정의 휠체어 미확인 수', () => {
  it('🔴 실측 — 이틀 8곳 중 7곳이 미확인이면 「8곳 중 7곳」', () => {
    const W = ['ACCESSIBILITY_UNVERIFIED'];
    expect(itineraryAccessibilityCounts({ days: [day(item(W), item(W), item(W), item(W)), day(item([]), item(W), item(W), item(W))] }))
      .toEqual({ unverified: 7, total: 8 });
  });

  it('보이는 일정이 모두 확인됐으면 0 — 다른 코스의 미확인은 세지 않는다', () => {
    expect(itineraryAccessibilityCounts({ days: [day(item([]), item([]))] })).toEqual({ unverified: 0, total: 2 });
  });

  it('항목 경고 칸이 없는 옛 서버면 null(모른다) — 0곳이라고 말하지 않는다', () => {
    expect(itineraryAccessibilityCounts({ days: [day(item(), item())] })).toBeNull();
  });

  it('다른 경고만 있는 곳은 미확인으로 세지 않는다', () => {
    expect(itineraryAccessibilityCounts({ days: [day(item(['STEEP_SLOPE']), item(['ACCESSIBILITY_UNVERIFIED']))] })).toEqual({ unverified: 1, total: 2 });
  });
});

// 되돌려도 창은 뜨고 숫자만 다시 커진다 — 그래서 화면이 이 셈을 «쓰는지»도 잰다.
describe('생성 완료 화면이 이 셈을 쓴다', () => {
  it('일정을 읽은 뒤 itineraryAccessibilityCounts 로 센다', () => {
    const { join } = require('path');
    const source = readFileSync(join(__dirname, '..', '..', '..', 'app', '(plan)', 'generating.tsx'), 'utf8') as string;
    expect(source).toContain('itineraryAccessibilityCounts(result.itinerary)');
  });
});

// S15P21E201-1814 — 유아차만 골라도 창이 「휠체어로 들어갈 수 있는지」라고 했다(QA, 진미리).
// 🔴 「큰 짐」 갈래는 없다 — 서버가 큰 짐에는 「확인 안 됨」을 안 붙인다(경사로만 가른다). 이 창은 휠체어·유아차만 센다.
describe('안내 창 첫 문장은 고른 이동 보조의 말로 쓴다', () => {
  const ko = (k: string) => k;
  const pick = (wheelchair: boolean | null, stroller: boolean | null) =>
    selectedMobilityAids({ wheelchair, stroller });

  it('유아차만 고르면 휠체어라는 말이 없다', () => {
    const text = accessibilityHeadline(ko, pick(false, true), 3, 8);
    expect(text).toBe('이번 일정 8곳 중 3곳은 유아차로 다니기 편한지 아직 확인되지 않았어요.');
    expect(text).not.toContain('휠체어');
  });
  it('휠체어만 고르면 휠체어의 말 그대로', () => {
    expect(accessibilityHeadline(ko, pick(true, false), 7, 8)).toBe('이번 일정 8곳 중 7곳은 휠체어로 들어갈 수 있는지 아직 확인되지 않았어요.');
  });
  it('둘 이상이거나 모르면 휠체어를 지어내지 않는다', () => {
    expect(accessibilityHeadline(ko, pick(true, true), 1, 4)).not.toContain('휠체어');
    expect(accessibilityHeadline(ko, [], 1, 4)).toBe('이번 일정 4곳 중 1곳은 고르신 이동 조건으로 다니기 편한지 아직 확인되지 않았어요.');
  });
  it('영어도 고른 보조를 말한다', () => {
    const en = (_k: string, e: string) => e;
    expect(accessibilityHeadline(en, pick(false, true), 3, 8)).toBe('Of the 8 places in this trip, 3 have not been checked for stroller access yet.');
  });
  it('안내 창은 이 함수로 첫 문장을 만든다 — 휠체어 문장을 직접 박지 않는다', () => {
    const src: string = readFileSync(`${__dirname}/../../components/AccessibilityUnverifiedModal.tsx`, 'utf8');
    expect(src).toContain('accessibilityHeadline(');
    expect(src).not.toContain('휠체어로 들어갈 수 있는지');
  });
});
