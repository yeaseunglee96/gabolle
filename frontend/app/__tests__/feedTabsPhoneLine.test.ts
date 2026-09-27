declare const require: (id: string) => any; declare const __dirname: string; const { readFileSync } = require('fs');

// S15P21E201-1806 — 폰에서 피드 탭 밑줄이 「내 피드」 뒤에서 끊기지 않는다.
const src: string = readFileSync(`${__dirname}/../(tabs)/feed.tsx`, 'utf8');

describe('피드 탭 줄 — 폰에서 밑줄이 전체 폭', () => {
  it('폰(compact)일 때 탭 줄을 감싼 칸과 탭 줄이 전체 폭으로 펴진다', () => {
    expect(src).toMatch(/style=\{\[styles\.headerActions, compact && styles\.headerActionsPhone\]\}/);
    expect(src).toMatch(/accessibilityRole="tablist" style=\{\[styles\.tabs, compact && styles\.tabsPhone\]\}/);
    expect(src).toMatch(/headerActionsPhone: \{[^}]*width: '100%'/);
    expect(src).toMatch(/tabsPhone: \{[^}]*flexGrow: 1/);
  });
  it('넓은 화면의 원래 모양은 그대로 둔다', () => {
    expect(src).toMatch(/headerActions: \{\s*flexDirection: 'row'[\s\S]*?justifyContent: 'flex-end'/);
  });
});
