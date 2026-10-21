// 날짜 없이 일정 만들기로 보내지 않는다 —-1337.
//
// 🔴 이 시험이 잡는 것은 **열 개를 다 답한 뒤에야 막히는 일**이다. 서버는 날짜를 반드시
//    요구한다(`TRIP_VALIDATION_FAILED`). 화면이 안 막으면 그 거절이 **서버 말투 그대로**
//    맨 마지막에 나온다 — `finishDate: 널이어서는 안됩니다`. 실제로 배포된 화면이 그랬다.
//
// 🔴 **막기만 하면 안 된다.** 잠근 단추 옆에 나갈 문이 없으면 사람은 거기서 끝난다.
//    특히 폰에서는 날짜 수정 단추가 「받은 것이 있을 때만」 그려져서, 날짜가 없는 사람에게는
//    그 길이 화면에 아예 없었다.
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..', '..', '..');
// 🔴 S15P21E201-1865 부터 날짜는 여행 만들기 1단계가 묻는다. 자리·제출은 questions.tsx, 단계 몸은 PlanSteps.tsx.
const SCREEN = fs.readFileSync(path.join(ROOT, 'app', '(plan)', 'questions.tsx'), 'utf8');
const STEPS = fs.readFileSync(path.join(ROOT, 'src', 'plan', 'PlanSteps.tsx'), 'utf8');

/** 주석은 뺀다 — 「전에는 이랬다」고 적어 둔 설명까지 세면 시험이 기록을 막는다. */
const strip = (src) => src.split('\n').map((line) => line.replace(/\/\/.*$/, '')).join('\n').replace(/\/\*[\s\S]*?\*\//g, '');
const code = strip(SCREEN);
const steps = strip(STEPS);

describe('날짜 없이 일정 만들기', () => {
  it('🔴 날짜가 하나라도 비면 없는 것으로 본다 — 시작만 있고 끝이 없으면 서버가 거절한다', () => {
    expect(code).toContain('datesMissing: !draft.startDate || !draft.endDate');
  });

  it('🔴 날짜가 없으면 1단계에서 못 넘어가고, 만들기 단추도 잠긴다', () => {
    expect(steps).toContain('case 0: return !r.datesMissing;');
    expect(code).toContain('!readiness.datesMissing');
    expect(code).toMatch(/const readyToBuild = [^;]*readiness\.datesMissing/);
  });

  it('🔴 빈 날짜를 건너뛴 채 뒤 단계(확인 표)에 서지 않는다 — 저장된 자리가 뒤여도 1단계로', () => {
    expect(code).toContain('firstIncompleteStep(readiness)');
  });

  it('🔴 왜 안 눌리는지 단추가 말한다 — 안 적으면 고장으로 읽는다', () => {
    expect(STEPS).toContain("tx('여행 날짜를 골라 주세요'");
    expect(STEPS).toContain("tx('돌아오는 날을 골라 주세요'");
  });

  it('🔴 거기서 정한다 — 1단계가 달력을 이 화면 안에 그린다(홈으로 보내지 않는다)', () => {
    expect(steps).toContain('<DateRangePicker ');
  });

  it('🔴 날짜를 대신 지어 넣지 않는다 — 안 고른 날짜로 만든 일정을 주지 않는다', () => {
    expect(code).not.toMatch(/startDate:\s*(today|new Date|addDays)/);
    expect(steps).not.toMatch(/startDate:\s*(today|new Date|addDays)/);
  });
});
