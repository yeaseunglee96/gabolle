// S15P21E201-1998 — 날씨·준비물 뒤로 가기, 알림 위 여백, 피드 정렬 설명 줄.
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

const read = (p: string): string => readFileSync(join(__dirname, '..', '..', p), 'utf8');

describe('S15P21E201-1998', () => {
  it('날씨·준비물로 바로 갈 때 replace 로 탭을 바꿔치기하지 않는다(뒤로 갈 곳이 남게)', () => {
    const src = read('app/(tabs)/trips.tsx');
    expect(src).not.toMatch(/router\.replace\(`\/\$\{prepareTarget\.tripId\}\/prepare`\)/);
    expect(src).toMatch(/router\.push\(`\/\$\{prepareTarget\.tripId\}\/prepare`\)/);
  });
  it('날씨·준비물 화면에 뒤로 단추가 있고 갈 곳이 없으면 홈으로 간다', () => {
    const src = read('app/(trip)/[id]/prepare.tsx');
    expect(src).toMatch(/<FieldBackButton fallback="\/home" \/>/);
  });
  it('뒤로 단추는 안드로이드 뒤로 버튼도 받는다', () => {
    expect(read('src/components/FieldBackButton.tsx')).toMatch(/hardwareBackPress/);
  });
  it('알림 화면 머리에 위 여백을 한 번 더 주지 않는다', () => {
    expect(read('app/notifications.tsx')).not.toMatch(/marginTop:\s*spacing\[6\]/);
  });
  it('피드 정렬 설명 줄을 보이지 않는다', () => {
    const src = read('app/(tabs)/feed.tsx');
    expect(src).not.toMatch(/tx\('좋아요 많은 순 · 같으면 최신순'/);
    expect(src).not.toMatch(/tx\('팔로우한 사람 먼저, 없으면 인기순'/);
  });
});
