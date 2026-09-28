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

describe('출발지가 없으면 만들기를 막는다', () => {
  it('🔴 출발지가 있는지 화면이 «직접» 본다 — 서버에 물어보고 알지 않는다', () => {
    expect(QUESTIONS).toContain('const originMissing = draft.originLat === null || draft.originLng === null;');
  });

  it('🔴 마지막 장의 「만들기」가 잠긴다', () => {
    // 1박 이상이면 숙소도 잠금 조건이다(S15P21E201-1584).
    expect(QUESTIONS).toContain("missing.length > 0 || datesMissing || originMissing || lodgingMissing || job?.state === 'submitting'");
  });

  it('🔴 「지금 이대로 만들기」 갈림길도 같이 막힌다 — 한쪽만 막으면 빠른 길로 새어 나간다', () => {
    // 그 갈림길은 readyToBuild 로 그려진다. 거기에 출발지가 들어 있어야 한다.
    expect(QUESTIONS).toContain('const readyToBuild = missing.length === 0 && !datesMissing && !originMissing && !lodgingMissing;');
  });
});

describe('고치러 갈 길이 있다', () => {
  it('🔴 출발지 칸이 «열린 채로» 열린다 — 그냥 홈으로 보내면 -1350 과 같은 실수다', () => {
    expect(QUESTIONS).toContain("params: { edit: 'origin' }");
  });

  it('🔴 없을 때 묻는 자리를 그린다 — 막아 놓고 문을 안 주지 않는다', () => {
    expect(QUESTIONS).toContain('어디에서 출발하세요?');
    expect(QUESTIONS).toContain('goPickOrigin');
  });

  it('골라 둔 뒤에는 ✓ 줄로 보이고 다시 고칠 수 있다', () => {
    expect(QUESTIONS).toContain("tx('출발지 수정', 'Edit starting point')");
  });

  it('필수를 다 채웠는데 출발지만 없으면 그렇게 말한다 — 「날짜만 정하면」이라고 하지 않는다', () => {
    expect(QUESTIONS).toContain('출발지만 고르면 만들 수 있어요');
  });
});
