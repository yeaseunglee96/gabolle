// 확인 표에서 고치면 바로 확인 표로 돌아오고, 이동 보조 안내는 고르기 전에 보인다 — S15P21E201-1869.
//
// 🔴 실기(갤럭시 S10) QA: 총예산 칸을 누르면 3단계로 갔고, 고친 뒤 4단계를 다시 지나야 표로 돌아왔다.
//    그리고 「접근성을 확인한 곳은 N곳 중 M곳」 안내는 휠체어·유아차를 «고른 뒤»에야 떴다.
declare const require: any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

const root = join(__dirname, '..', '..', '..');
const questions: string = readFileSync(join(root, 'app', '(plan)', 'questions.tsx'), 'utf8');
const steps: string = readFileSync(join(root, 'src', 'plan', 'PlanSteps.tsx'), 'utf8');

describe('확인 표에서 고치기', () => {
  it('확인 표의 칸이 부르는 goTo 는 «표에서 왔다»를 기억한다', () => {
    expect(questions).toContain('goTo={review ? editFromReviewAt : goTo}');
    expect(questions).toMatch(/const editFromReviewAt = \(next: number\) => \{ goTo\(next\); setEditFromReview\(true\); \};/);
  });

  it('표에서 왔으면 주 단추가 확인 표로 곧장 돌아간다', () => {
    expect(questions).toContain("backToReview ? tx('조건 확인으로 돌아가기', 'Back to trip review')");
    expect(questions).toMatch(/else if \(backToReview\) goTo\(REVIEW_STEP\);/);
  });

  it('평소 단계 이동은 그 표시를 지운다 — 처음부터 답하는 사람에게 「돌아가기」가 뜨면 안 된다', () => {
    expect(questions).toMatch(/const goTo = \(next: number\) => \{\s*setEditFromReview\(false\);/);
  });
});

describe('이동 보조 접근성 안내', () => {
  it('🔴 휠체어·유아차를 골랐는지와 상관없이 보인다', () => {
    expect(steps).not.toContain('(draft.wheelchair || draft.stroller) && accessibilityCounts');
    expect(steps).toMatch(/\{accessibilityCounts \? \(\s*<Text[^>]*>\{txf\(tx, '지금 접근성을 확인한 곳은/);
  });

  it('큰 짐은 여전히 묻지 않는다', () => {
    expect(steps).not.toContain('큰 짐');
  });
});
