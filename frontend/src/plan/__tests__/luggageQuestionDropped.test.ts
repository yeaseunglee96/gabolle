// 「큰 짐이 있어요」는 묻지 않는다 — S15P21E201-1855.
//
// 🔴 왜 지웠나. 백엔드에 HEAVY_LUGGAGE 를 «읽는» 코드가 한 줄도 없다(2026-09-29 back/dev 확인).
//    · 장소 판정 — 무거운 짐을 가리키는 칸이 원천 자료에 없다. BarrierFreeAccessibility 가
//      「이 키는 비운다」고 적어 두었다. 엘리베이터가 있으면 짐 들기 쉽다는 것은 추론이지
//      원천의 말이 아니다
//    · 경로 탐색 — 계단 없는 길로 묻는 STEP_FREE_KEYS 는 WHEELCHAIR·STROLLER·STAIRS_AVOIDANCE 셋이다
//    묻고서 못 지키는 약속이라 뺐다.
//
// 🔴 유아차는 «남긴다». 장소 태그는 없지만 STEP_FREE_KEYS 에 들어 있어, 고르면 그 여행의 모든
//    구간을 계단 없는 길로 묻는다. 둘을 같이 지우면 실제로 도는 기능이 사라진다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

const read = (...parts: string[]): string => readFileSync(join(__dirname, '..', '..', '..', ...parts), 'utf8');

describe('큰 짐 문항을 지웠다', () => {
  it('🔴 조건 화면이 「큰 짐」을 묻지 않는다 — 유아차는 그대로 묻는다', () => {
    const source = read('app', '(plan)', 'questions.tsx');
    expect(source).not.toContain('큰 짐이 있어요');
    expect(source).toContain('유아차가 있어요');
  });

  it('🔴 서버로 HEAVY_LUGGAGE 를 보내지 않는다 — WHEELCHAIR·STROLLER 는 보낸다', () => {
    const source = read('src', 'api', 'tripApi.ts');
    expect(source).not.toContain("mobility('HEAVY_LUGGAGE'");
    expect(source).toContain("mobility('WHEELCHAIR'");
    expect(source).toContain("mobility('STROLLER'");
  });

  it('초안에 그 칸을 두지 않는다', () => {
    const source = read('src', 'plan', 'PlanProvider.tsx');
    expect(source).not.toContain('luggage: boolean | null');
    expect(source).not.toContain('luggage: null');
  });

  it('🔴 실패 안내의 이름표는 남긴다 — 옛 여행에는 서버에 그 제약이 저장돼 있다', () => {
    // 이 줄을 지우면 옛 여행을 다시 짤 때 「큰 짐」이 갈래 이름 「이동 조건」으로 뭉개진다.
    expect(read('src', 'plan', 'blockedByMessage.ts')).toContain('HEAVY_LUGGAGE');
  });
});
