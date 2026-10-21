declare const require: any;
const { readFileSync } = require('fs');

// S15P21E201-1871 — 확인 표는 칸을 누르면 그 단계로 갔다가 「조건 확인으로 돌아가기」로 바로 돌아온다(!1901).
// 안내가 「그 자리에서 고쳐요」라고 하면 동작과 어긋난다.
describe('확인 표 안내 문구', () => {
  const src = readFileSync(require.resolve('../../../app/(plan)/questions.tsx'), 'utf8');
  const tr = readFileSync(require.resolve('../../i18n/translations.ts'), 'utf8');
  it('동작대로 「고치고 바로 돌아와요」라고 말한다', () => {
    expect(src).toContain('칸을 누르면 고치고 바로 돌아와요.');
    expect(src).not.toContain('칸을 누르면 그 자리에서 고쳐요.');
  });
  it('번역표에도 새 문구가 있다', () => {
    expect(tr).toContain("'칸을 누르면 고치고 바로 돌아와요.'");
  });
});
