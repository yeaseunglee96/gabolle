// 출발지를 묻는 자리가 없어 일정이 안 만들어지던 것 — S15P21E201-1342.
//
// 🔴 2026-09-21 실기(SM-G973N, versionCode 25, 한국어). 탭바 「여행 만들기」로 들어가
//    필수 질문을 다 답하고 「지금 이대로 만들기」를 눌렀더니 이렇게 끝났다.
//
//      서버가 이 칸을 받지 못했어요 — originLat: 출발지 좌표가 없다.
//      목록에서 출발지를 골라 주세요 (TRIP_VALIDATION_FAILED)
//
//    화면에는 「목록」도 출발지 칸도 없어서 시키는 대로 할 방법이 없었다.
//    문항이 세 장으로 줄면서 「지금 이대로 만들기」라는 빠른 길이 생겨 **더 자주** 만난다.
//
// 🔴 이 시험이 잡는 것은 «누르기 전에 막는가»와 «고치러 갈 길이 있는가» 둘이다.
//    서버까지 갔다 와서 막히는 것은 고친 것이 아니다.
declare const require: (id: string) => any;
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');

const QUESTIONS = readFileSync(
  join(__dirname, '..', '..', '..', 'app', '(plan)', 'questions.tsx'), 'utf8') as string;
// 🔴 S15P21E201-1865 부터 출발지는 여행 만들기 2단계가 묻는다 — 단계 몸은 PlanSteps.tsx.
const STEPS = readFileSync(join(__dirname, '..', 'PlanSteps.tsx'), 'utf8') as string;

describe('출발지가 없으면 만들기를 막는다', () => {
  it('🔴 출발지가 있는지 화면이 «직접» 본다 — 서버에 물어보고 알지 않는다', () => {
    expect(QUESTIONS).toContain('originMissing: draft.originLat === null || draft.originLng === null,');
  });

  it('🔴 2단계에서 못 넘어가고, 만들기 단추도 잠긴다 — 한쪽만 막으면 다른 길로 새어 나간다', () => {
    expect(STEPS).toContain('case 1: return !r.originMissing && !r.lodgingMissing');
    expect(QUESTIONS).toContain('const readyToBuild = missing.length === 0 && !readiness.datesMissing && !readiness.originMissing && !readiness.lodgingMissing;');
    expect(QUESTIONS).toContain('!readyToBuild');
  });
});

describe('고치러 갈 길이 있다', () => {
  it('🔴 없을 때 그 자리에서 묻는다 — 추천 출발지를 바로 고르고, 나머지는 검색 시트로', () => {
    expect(STEPS).toContain('어디서 출발하세요?');
    expect(STEPS).toContain('ORIGIN_SHORTLIST');
    expect(STEPS).toContain("onSearchPlace('origin')");
    expect(QUESTIONS).toContain('onSearchPlace={openBar}');
  });

  it('🔴 홈으로 튕겨 보내지 않는다 — 돌아오면 답하던 자리가 흐트러졌다(UI 캔버스 ⑤)', () => {
    expect(QUESTIONS).not.toContain("params: { edit: 'origin' }");
  });

  it('골라 둔 뒤에도 다시 고칠 수 있다', () => {
    expect(STEPS).toMatch(/hasOrigin \? tx\('바꾸기', 'Change'\)/);
  });

  it('잠긴 단추가 이유를 말한다 — 「날짜만 정하면」이라고 하지 않는다', () => {
    expect(STEPS).toContain("tx('출발지를 골라 주세요'");
  });
});
