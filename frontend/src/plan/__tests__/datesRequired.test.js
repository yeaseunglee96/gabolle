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
const SCREEN = fs.readFileSync(path.join(ROOT, 'app', '(plan)', 'questions.tsx'), 'utf8');

/** 주석은 뺀다 — 「전에는 이랬다」고 적어 둔 설명까지 세면 시험이 기록을 막는다. */
const code = SCREEN.split('\n')
  .map((line) => line.replace(/\/\/.*$/, ''))
  .join('\n')
  .replace(/\/\*[\s\S]*?\*\//g, '');

describe('날짜 없이 일정 만들기', () => {
  it('🔴 날짜가 없으면 마지막 단추가 안 눌린다', () => {
    const disabled = code.match(/disabled=\{last \?([^:]*):/);
    expect(disabled).not.toBeNull();
    expect(disabled[1]).toContain('datesMissing');
  });

  it('🔴 날짜가 하나라도 비면 없는 것으로 본다 — 시작만 있고 끝이 없으면 서버가 거절한다', () => {
    expect(code).toContain('!draft.startDate || !draft.endDate');
  });

  it('🔴 왜 안 눌리는지 화면에 적는다 — 안 적으면 고장으로 읽는다', () => {
    expect(SCREEN).toContain('날짜를 아직 안 정했어요 — 눌러서 정해 주세요.');
  });

  it('🔴 거기서 정하러 갈 수 있다 — 잠그기만 하고 문을 안 주지 않는다', () => {
    expect(code).toContain('goSetDates');
    // 알림 줄과 폰 위쪽 줄 둘 다 그 문을 쓴다.
    expect(code.match(/goSetDates/g).length).toBeGreaterThanOrEqual(3);
  });

  it('🔴 폰의 날짜 단추가 「받은 것이 있을 때만」에 묶여 있지 않다', () => {
    // 예전 모양: {headerChips.length ? <Pressable … 수정 …> : null}
    expect(code).not.toMatch(/headerChips\.length \?\s*\(?\s*<Pressable/);
  });

  it('🔴 날짜를 대신 지어 넣지 않는다 — 안 고른 날짜로 만든 일정을 주지 않는다', () => {
    expect(code).not.toMatch(/startDate:\s*(today|new Date|addDays)/);
  });
});
